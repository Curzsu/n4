"""Read-only remote export and guarded import into the dedicated local lab."""
import argparse
import base64
from datetime import datetime, timezone
import re
import time
from common import PRIVATE, Redis, canonical, digest, load_env, mysql, save_json

def quote(name):
    if not re.fullmatch(r"[A-Za-z0-9_]+", name):
        raise ValueError("Unsupported SQL identifier")
    return chr(96) + name + chr(96)

def export_mysql(remote, directory):
    connection = mysql(remote)
    source, schemas = {}, {}
    with connection.cursor() as cursor:
        cursor.execute("SET TRANSACTION ISOLATION LEVEL REPEATABLE READ")
        cursor.execute("START TRANSACTION WITH CONSISTENT SNAPSHOT, READ ONLY")
        cursor.execute("SHOW TABLES")
        tables = [next(iter(row.values())) for row in cursor.fetchall()]
        with (directory / "n4_monitor.sql").open("w", encoding="utf-8") as output:
            output.write("SET NAMES utf8mb4;\nSET FOREIGN_KEY_CHECKS=0;\n")
            for table in tables:
                cursor.execute("SHOW CREATE TABLE " + quote(table))
                schemas[table] = cursor.fetchone()["Create Table"]
                cursor.execute("SELECT * FROM " + quote(table))
                rows = cursor.fetchall()
                source[table] = rows
                output.write(schemas[table] + ";\n")
                if rows:
                    columns = list(rows[0])
                    statement = "INSERT INTO " + quote(table) + " (" + ",".join(map(quote, columns)) + ") VALUES (" + ",".join(["%s"] * len(columns)) + ");\n"
                    for row in rows:
                        output.write(cursor.mogrify(statement, tuple(row[x] for x in columns)))
        connection.rollback()
    connection.close()
    save_json(directory / "mysql.json", source)
    save_json(directory / "schemas.json", schemas)
    manifest = {name: {"rows": len(rows), "sha256": digest(rows)} for name, rows in source.items()}
    save_json(directory / "mysql-manifest.json", manifest)
    return source, schemas, manifest

def restore_mysql(local, source, schemas, manifest):
    connection = mysql(local, destination=True)
    with connection.cursor() as cursor:
        cursor.execute("SHOW TABLES")
        if cursor.fetchall():
            raise ValueError("Local lab MySQL must be empty before the first restore")
        cursor.execute("SET FOREIGN_KEY_CHECKS=0")
        for table, ddl in schemas.items():
            cursor.execute(ddl)
            rows = source[table]
            if rows:
                columns = list(rows[0])
                sql = "INSERT INTO " + quote(table) + " (" + ",".join(map(quote, columns)) + ") VALUES (" + ",".join(["%s"] * len(columns)) + ")"
                for start in range(0, len(rows), 500):
                    cursor.executemany(sql, [tuple(row[x] for x in columns) for row in rows[start:start + 500]])
        cursor.execute("SET FOREIGN_KEY_CHECKS=1")
        connection.commit()
        for table in source:
            cursor.execute("SELECT * FROM " + quote(table))
            rows = cursor.fetchall()
            if len(rows) != manifest[table]["rows"] or digest(rows) != manifest[table]["sha256"]:
                raise ValueError("Local MySQL verification failed: " + table)
    connection.close()

def sync_redis(remote, local, directory):
    origin, destination = Redis(remote), Redis(local, destination=True)
    cursor, names = b"0", set()
    while True:
        cursor, batch = origin.command("SCAN", cursor, "MATCH", "alarm:*", "COUNT", 1000)
        names.update(batch)
        if cursor == b"0": break
    snapshot, report = [], []
    for name in sorted(names):
        if name.startswith(b"alarm:stream:"):
            report.append({"key": name.decode(), "status": "excluded-persistence-stream"})
            continue
        kind = origin.command("TYPE", name)
        ttl = origin.command("PTTL", name)
        if ttl == -2: continue
        deadline = int(time.time() * 1000) + ttl if ttl > 0 else None
        if kind == b"hash":
            values = origin.command("HGETALL", name)
            pairs = {values[i].hex(): values[i + 1].hex() for i in range(0, len(values), 2)}
        elif kind == b"string":
            values = [origin.command("GET", name)]
            pairs = {"value": values[0].hex()}
        else:
            raise ValueError("Unexpected alarm key type: " + kind.decode())
        snapshot.append({"key": name.decode(), "type": kind.decode(), "deadline_ms": deadline,
                         "values_base64": [base64.b64encode(v).decode() for v in values]})
        remaining = deadline - int(time.time() * 1000) if deadline else None
        if remaining is not None and remaining <= 0:
            report.append({"key": name.decode(), "status": "expired-before-restore"})
            continue
        destination.command("DEL", name)
        if kind == b"hash":
            for start in range(0, len(values), 1000):
                destination.command("HSET", name, *values[start:start + 1000])
            restored = destination.command("HGETALL", name)
            actual = {restored[i].hex(): restored[i + 1].hex() for i in range(0, len(restored), 2)}
        else:
            destination.command("SET", name, values[0])
            actual = {"value": destination.command("GET", name).hex()}
        if digest([pairs]) != digest([actual]):
            raise ValueError("Redis verification failed: " + name.decode())
        if remaining:
            destination.command("PEXPIRE", name, max(1, deadline - int(time.time() * 1000)))
        report.append({"key": name.decode(), "status": "verified", "fields": len(pairs), "sha256": digest([pairs])})
    save_json(directory / "redis.json", snapshot)
    save_json(directory / "redis-manifest.json", report)
    origin.close(); destination.close()
    return report

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source-env", default=".env")
    args = parser.parse_args()
    remote, local = load_env(args.source_env), load_env(".env.local")
    directory = PRIVATE / ("backup-" + datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ"))
    directory.mkdir(parents=True, exist_ok=False)
    started = time.monotonic()
    source, schemas, manifest = export_mysql(remote, directory)
    print("Remote MySQL snapshot exported; restoring only to the guarded local destination.", flush=True)
    restore_mysql(local, source, schemas, manifest)
    print("All MySQL table counts and content hashes verified.", flush=True)
    redis = sync_redis(remote, local, directory)
    save_json(directory / "migration-report.json", {
        "captured_at_utc": datetime.now(timezone.utc).isoformat(),
        "mysql_tables": manifest, "redis_keys": redis,
        "mysql_consistency": "single-repeatable-read-snapshot",
        "redis_consistency": "per-key read; expiring keys retain remaining TTL",
        "excluded": ["TDengine", "Kafka messages", "Redis persistence streams", "FUXA", "EMQX", "Gitea"],
        "seconds": round(time.monotonic() - started, 2)})
    (PRIVATE / "latest-backup.txt").write_text(directory.name, encoding="utf-8")
    print(canonical({"backup": str(directory), "mysql_tables": len(manifest),
                     "mysql_rows": sum(x["rows"] for x in manifest.values()),
                     "redis_verified": sum(x["status"] == "verified" for x in redis),
                     "seconds": round(time.monotonic() - started, 2)}))

if __name__ == "__main__":
    main()
