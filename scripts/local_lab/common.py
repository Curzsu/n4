"""Local-lab connections. Destination writes are restricted to loopback hosts."""
import base64
import hashlib
import json
import socket
from pathlib import Path
from urllib.parse import urlparse, unquote

import pymysql
import requests

ROOT = Path(__file__).resolve().parents[2]
PRIVATE = ROOT / ".local-lab"

def load_env(name):
    result = {}
    for line in (ROOT / name).read_text(encoding="utf-8-sig").splitlines():
        line = line.strip()
        if line and not line.startswith("#") and "=" in line:
            key, value = line.split("=", 1)
            result[key.strip()] = value.strip().strip(chr(34)).strip(chr(39))
    return result

def require_local(host):
    if host not in {"localhost", "127.0.0.1", "::1"}:
        raise ValueError("Destination must be a loopback host")

def mysql(cfg, destination=False):
    uri = urlparse(cfg["MYSQL_URL"].removeprefix("jdbc:"))
    if destination:
        require_local(uri.hostname)
        if uri.port != 23306 or uri.path != "/n4_monitor":
            raise ValueError("Destination must use the dedicated lab MySQL port")
    return pymysql.connect(host=uri.hostname, port=uri.port or 3306,
        user=cfg["MYSQL_USERNAME"], password=cfg["MYSQL_PASSWORD"],
        database=unquote(uri.path.lstrip("/")), charset="utf8mb4",
        cursorclass=pymysql.cursors.DictCursor, connect_timeout=10,
        read_timeout=120, write_timeout=120, autocommit=False)

def canonical(value):
    return json.dumps(value, sort_keys=True, ensure_ascii=False, default=str, separators=(",", ":"))

def digest(rows):
    hashes = sorted(hashlib.sha256(canonical(row).encode()).hexdigest() for row in rows)
    return hashlib.sha256("".join(hashes).encode()).hexdigest()

def save_json(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2, ensure_ascii=False, default=str), encoding="utf-8")

class Redis:
    def __init__(self, cfg, destination=False):
        if destination:
            require_local(cfg["REDIS_HOST"])
            if int(cfg["REDIS_PORT"]) != 16379:
                raise ValueError("Destination must use the dedicated lab Redis port")
        self.sock = socket.create_connection((cfg["REDIS_HOST"], int(cfg["REDIS_PORT"])), timeout=20)
        self.file = self.sock.makefile("rwb")
        if cfg.get("REDIS_PASSWORD"):
            self.command("AUTH", cfg["REDIS_PASSWORD"])

    def read(self):
        line = self.file.readline()
        if not line:
            raise ConnectionError("Redis connection closed")
        kind, value = line[:1], line[1:].rstrip(b"\r\n")
        if kind == b"+": return value
        if kind == b"-": raise RuntimeError("Redis: " + value.decode())
        if kind == b":": return int(value)
        if kind == b"$":
            size = int(value)
            if size < 0: return None
            data = self.file.read(size)
            self.file.read(2)
            return data
        if kind == b"*":
            size = int(value)
            return None if size < 0 else [self.read() for _ in range(size)]
        raise RuntimeError("Invalid Redis response")

    def command(self, *parts):
        payload = b"*" + str(len(parts)).encode() + b"\r\n"
        for part in parts:
            item = part if isinstance(part, bytes) else str(part).encode()
            payload += b"$" + str(len(item)).encode() + b"\r\n" + item + b"\r\n"
        self.file.write(payload)
        self.file.flush()
        return self.read()

    def close(self):
        self.file.close()
        self.sock.close()

def graph(cfg, statement, parameters=None):
    uri = urlparse(cfg["NEO4J_HTTP_URL"])
    require_local(uri.hostname)
    if uri.port != 17474:
        raise ValueError("Destination must use the dedicated lab Neo4j port")
    with requests.Session() as session:
        session.trust_env = False
        response = session.post(cfg["NEO4J_HTTP_URL"] + "/db/neo4j/tx/commit",
            auth=(cfg["NEO4J_USERNAME"], cfg["NEO4J_PASSWORD"]),
            json={"statements": [{"statement": statement, "parameters": parameters or {}}]},
            timeout=(10, 120))
        response.raise_for_status()
        result = response.json()
    if result.get("errors"):
        raise RuntimeError(canonical(result["errors"]))
    return [dict(zip(result["results"][0]["columns"], row["row"])) for row in result["results"][0]["data"]]
