"""Verify restored data and the active graph against the frozen local source."""
import json
from common import PRIVATE, canonical, digest, graph, load_env, mysql, save_json
from graph_model import build_graph
from sync_data import quote

cfg = load_env(".env.local")
directory = PRIVATE / (PRIVATE / "latest-backup.txt").read_text(encoding="utf-8").strip()
source = json.loads((directory / "mysql.json").read_text(encoding="utf-8"))
manifest = json.loads((directory / "mysql-manifest.json").read_text(encoding="utf-8"))
connection = mysql(cfg, destination=True)
verified, changed_system = [], []
with connection.cursor() as cursor:
    for table, expected in manifest.items():
        cursor.execute("SELECT * FROM " + quote(table))
        rows = cursor.fetchall()
        if len(rows) == expected["rows"] and digest(rows) == expected["sha256"]:
            verified.append(table)
        elif table.startswith("sys_"):
            changed_system.append(table)
        else:
            raise ValueError("Source and local business table differ: " + table)
connection.close()
active = graph(cfg, "MATCH (s:N4Snapshot {current:true,status:'verified'}) RETURN s.id AS id")
if len(active) != 1:
    raise ValueError("Expected exactly one active verified graph snapshot")
snapshot = active[0]["id"]
nodes, edges = build_graph(source)
actual_nodes = graph(cfg, "MATCH (n:N4Entity {snapshot:$id}) RETURN n.uid AS uid,properties(n) AS props", {"id":snapshot})
expected = {n["uid"]: {k:v for k,v in n["props"].items() if v is not None} for n in nodes}
actual = {n["uid"]: {k:v for k,v in n["props"].items() if k not in {"key","uid","snapshot"}} for n in actual_nodes}
if expected != actual:
    raise ValueError("Active graph node properties do not match the source")
actual_edges = graph(cfg, "MATCH (a:N4Entity {snapshot:$id})-[r]->(b:N4Entity {snapshot:$id}) "
    "RETURN a.uid AS source,type(r) AS type,b.uid AS target", {"id":snapshot})
if sorted(map(canonical, actual_edges)) != sorted(map(canonical, edges)):
    raise ValueError("Active graph relations do not match the source")
report = {"verified_mysql_tables": len(verified), "runtime_changed_system_tables": changed_system,
          "snapshot": snapshot, "nodes_verified": len(nodes), "relationships_verified": len(edges),
          "verified": True}
save_json(PRIVATE / "verification-report.json", report)
print(canonical(report))
