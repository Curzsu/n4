"""Build and verify a new immutable graph snapshot before activating it."""
from collections import Counter
from datetime import datetime, timezone
import json
import time
import uuid
from common import PRIVATE, canonical, graph, load_env, save_json
from graph_model import build_graph

def batches(values, size=500):
    for start in range(0, len(values), size):
        yield values[start:start + size]

def main():
    started = time.monotonic()
    directory = PRIVATE / (PRIVATE / "latest-backup.txt").read_text(encoding="utf-8").strip()
    source = json.loads((directory / "mysql.json").read_text(encoding="utf-8"))
    nodes, edges = build_graph(source)
    cfg = load_env(".env.local")
    snapshot = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ") + "-" + uuid.uuid4().hex[:8]
    graph(cfg, "CREATE CONSTRAINT n4_entity_key IF NOT EXISTS FOR (n:N4Entity) REQUIRE n.key IS UNIQUE")
    graph(cfg, "CREATE CONSTRAINT n4_snapshot_id IF NOT EXISTS FOR (n:N4Snapshot) REQUIRE n.id IS UNIQUE")
    graph(cfg, "CREATE INDEX n4_snapshot_lookup IF NOT EXISTS FOR (n:N4Entity) ON (n.snapshot)")
    graph(cfg, "CREATE (s:N4Snapshot {id:$id, current:false, status:'building', sourceBackup:$backup})",
          {"id": snapshot, "backup": directory.name})
    for label in sorted({n["label"] for n in nodes}):
        selected = [n for n in nodes if n["label"] == label]
        for batch in batches(selected):
            result = graph(cfg, "UNWIND $rows AS row CREATE (n:N4Entity:" + label + ") "
                "SET n=row.props, n.key=$id+':' + row.uid, n.uid=row.uid, n.snapshot=$id RETURN count(n) AS added",
                {"id": snapshot, "rows": batch})
            if result[0]["added"] != len(batch):
                raise ValueError("Node import count mismatch")
    for relation in sorted({e["type"] for e in edges}):
        selected = [e for e in edges if e["type"] == relation]
        for batch in batches(selected):
            result = graph(cfg, "UNWIND $rows AS row MATCH (a:N4Entity {key:$id+':' + row.source}), "
                "(b:N4Entity {key:$id+':' + row.target}) CREATE (a)-[r:" + relation + " {snapshot:$id}]->(b) "
                "RETURN count(r) AS added", {"id": snapshot, "rows": batch})
            if result[0]["added"] != len(batch):
                raise ValueError("Edge import count mismatch")
    stored = graph(cfg, "MATCH (n:N4Entity {snapshot:$id}) RETURN n.uid AS uid, properties(n) AS props", {"id": snapshot})
    expected = {n["uid"]: {k:v for k,v in n["props"].items() if v is not None} for n in nodes}
    actual = {n["uid"]: {k:v for k,v in n["props"].items() if k not in {"key","uid","snapshot"}} for n in stored}
    if actual != expected:
        raise ValueError("Stored graph node properties differ from the source projection")
    stored_edges = graph(cfg, "MATCH (a:N4Entity {snapshot:$id})-[r]->(b:N4Entity {snapshot:$id}) "
        "RETURN a.uid AS source,type(r) AS type,b.uid AS target", {"id": snapshot})
    if sorted(map(canonical, edges)) != sorted(map(canonical, stored_edges)):
        raise ValueError("Stored graph edges differ from the source projection")
    # One transaction activates the verified snapshot; an interrupted import never replaces the current graph.
    graph(cfg, "MATCH (old:N4Snapshot) WHERE old.current=true SET old.current=false "
        "WITH count(old) AS previous MATCH (fresh:N4Snapshot {id:$id}) "
        "SET fresh.current=true,fresh.status='verified',fresh.verifiedAt=datetime() RETURN fresh.id AS current",
        {"id": snapshot})
    examples = graph(cfg, "MATCH (s:N4Snapshot {current:true}) "
        "MATCH (d:Device {snapshot:s.id,deviceDeployId:43})-[:HAS_POINT]->(p:Point)-[:USES_POLICY]->(r:AlarmPolicy) "
        "OPTIONAL MATCH (r)-[:HAS_CONDITION]->(c:TriggerCondition) "
        "RETURN d.name AS device,p.name AS point,p.unit AS unit,r.name AS policy,"
        "collect(c{.level,.threshold,.operator,.severity,.enumValue,.meaning}) AS conditions ORDER BY point LIMIT 8")
    report = {"snapshot": snapshot, "source_backup": directory.name, "verified": True,
        "nodes": dict(Counter(n["label"] for n in nodes)),
        "relationships": dict(Counter(e["type"] for e in edges)),
        "node_total": len(nodes), "relationship_total": len(edges),
        "verification": "all node properties and all edge triples equal source projection",
        "examples": examples, "seconds": round(time.monotonic() - started, 2)}
    save_json(PRIVATE / "graph-report.json", report)
    save_json(directory / ("graph-" + snapshot + ".json"), {"nodes": nodes, "edges": edges})
    print(canonical(report))

if __name__ == "__main__":
    main()
