"""Export a complete verified snapshot into an offline interactive graph viewer."""
from collections import Counter
from datetime import datetime, timezone
import json
from pathlib import Path
import shutil

from common import ROOT, canonical, graph, load_env

LABELS = {"DeploymentNode", "Device", "DeviceTemplate", "Point", "PointTemplate", "AlarmPolicy", "TriggerCondition"}
PROPERTIES = {"name", "alias", "location", "systemName", "deviceType", "model", "purpose",
    "manufacturer", "deleted", "level", "deviceDeployId", "pointDeployId", "pointType",
    "mqttPath", "unit", "kind", "timeDelay", "deadband", "updatedAt", "threshold",
    "operator", "priority", "severity", "message", "enumValue", "meaning", "source_table", "source_id"}

def build_payload(node_rows, edge_rows, snapshot):
    nodes, ids = [], set()
    for row in node_rows:
        labels = set(row["labels"]) & LABELS
        if len(labels) != 1 or row["props"].get("snapshot") != snapshot:
            raise ValueError("Unexpected label or cross-snapshot node")
        if row["uid"] in ids:
            raise ValueError("Duplicate graph node")
        ids.add(row["uid"])
        nodes.append({"id": row["uid"], "kind": next(iter(labels)),
                      "props": {k:v for k,v in row["props"].items() if k in PROPERTIES}})
    edges = []
    for edge in edge_rows:
        if edge["source"] not in ids or edge["target"] not in ids:
            raise ValueError("Dangling graph edge")
        edges.append(dict(edge))
    return {"snapshot": snapshot, "nodes": nodes, "edges": edges,
            "counts": dict(Counter(n["kind"] for n in nodes)),
            "relationshipCounts": dict(Counter(e["type"] for e in edges)),
            "exportedAt": datetime.now(timezone.utc).isoformat()}

def main():
    cfg = load_env(".env.local")
    active = graph(cfg, "MATCH (s:N4Snapshot {current:true,status:'verified'}) RETURN s.id AS id")
    if len(active) != 1:
        raise ValueError("Exactly one verified current snapshot is required")
    snapshot = active[0]["id"]
    nodes = graph(cfg, "MATCH (n:N4Entity {snapshot:$id}) "
        "RETURN n.uid AS uid,labels(n) AS labels,properties(n) AS props", {"id": snapshot})
    edges = graph(cfg, "MATCH (a:N4Entity {snapshot:$id})-[r]->(b:N4Entity {snapshot:$id}) "
        "RETURN a.uid AS source,type(r) AS type,b.uid AS target", {"id":snapshot})
    payload = build_payload(nodes, edges, snapshot)
    assets = Path(__file__).parent / "viewer"
    target = ROOT / "outputs" / "n4-graph-viewer"
    target.mkdir(parents=True, exist_ok=True)
    echarts = ROOT / "frontend/node_modules/echarts/dist/echarts.min.js"
    if not echarts.is_file():
        raise FileNotFoundError("Run npm ci in frontend before exporting the viewer")
    html = (assets / "index.html").read_text(encoding="utf-8")
    language = json.loads((assets / "labels.zh.json").read_text(encoding="utf-8"))
    for token, value in [("__GRAPH_JSON__", payload), ("__LANG_JSON__", language)]:
        html = html.replace(token, json.dumps(value, ensure_ascii=False).replace("</", "<" + chr(92) + "/"))
    (target / "index.html").write_text(html, encoding="utf-8")
    shutil.copyfile(echarts, target / "echarts.min.js")
    shutil.copyfile(assets / "viewer.js", target / "viewer.js")
    shutil.copyfile(assets / "style.css", target / "style.css")
    print(canonical({"path":str(target / "index.html"), "snapshot":snapshot,
                     "nodes":len(nodes), "relationships":len(edges)}))

if __name__ == "__main__":
    main()
