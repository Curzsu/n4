"""Read the current verified snapshot by device ID or exact device name."""
import argparse
from common import canonical, graph, load_env
parser = argparse.ArgumentParser(description=__doc__)
group = parser.add_mutually_exclusive_group(required=True)
group.add_argument("--device-id", type=int)
group.add_argument("--device-name")
args = parser.parse_args()
rows = graph(load_env(".env.local"),
    "MATCH (s:N4Snapshot {current:true,status:'verified'}) "
    "MATCH (d:Device {snapshot:s.id}) WHERE ($deviceId IS NOT NULL AND d.deviceDeployId=$deviceId) "
    "OR ($name IS NOT NULL AND d.name=$name) "
    "OPTIONAL MATCH (d)-[:DEPLOYED_IN]->(location:DeploymentNode) "
    "OPTIONAL MATCH (d)-[:HAS_POINT]->(p:Point) "
    "OPTIONAL MATCH (p)-[:USES_POLICY]->(policy:AlarmPolicy) "
    "OPTIONAL MATCH (policy)-[:HAS_CONDITION]->(condition:TriggerCondition) "
    "RETURN d.deviceDeployId AS deviceId,d.name AS device,location.name AS location,"
    "p.name AS point,p.mqttPath AS mqttPath,p.unit AS unit,policy.name AS policy,"
    "collect(condition{.level,.threshold,.operator,.enumValue,.meaning,.severity,.message}) AS conditions "
    "ORDER BY point LIMIT 200", {"deviceId": args.device_id, "name": args.device_name})
print(canonical(rows))
