"""Deterministic graph projection of a frozen MySQL snapshot."""
from collections import defaultdict

def build_graph(source):
    nodes, edges, ids, mqtt_paths = [], [], set(), set()
    def add(label, uid, table, source_id, **props):
        if uid in ids:
            raise ValueError("Duplicate graph identifier: " + uid)
        ids.add(uid)
        nodes.append({"label": label, "uid": uid,
                      "props": dict(props, source_table=table, source_id=str(source_id))})
    def link(kind, origin, target):
        if origin not in ids or target not in ids:
            raise ValueError("Missing graph endpoint: " + origin + " -> " + target)
        edges.append({"type": kind, "source": origin, "target": target})
    def active(table):
        return [x for x in source.get(table, []) if not x.get("is_deleted", 0)]
    deployments = active("system_deployment")
    devices = active("device_deployment")
    points = active("point_deployment")
    referenced_templates = {x.get("device_tmpl_id") for x in devices}
    referenced_points = {x.get("point_tmpl_id") for x in points}
    for x in deployments:
        add("DeploymentNode", f"deployment:{x['node_id']}", "system_deployment", x["node_id"],
            name=x.get("node_name"), level=x.get("node_level"))
    for x in source.get("device_template", []):
        if not x.get("is_deleted", 0) or x["device_tmpl_id"] in referenced_templates:
            add("DeviceTemplate", f"device-template:{x['device_tmpl_id']}", "device_template", x["device_tmpl_id"],
                name=x.get("device_tmpl_name"), deviceType=x.get("device_type"), model=x.get("model"),
                purpose=x.get("purpose"), manufacturer=x.get("manufacturer"), deleted=bool(x.get("is_deleted")))
    for x in source.get("point_template", []):
        if not x.get("is_deleted", 0) or x["point_tmpl_id"] in referenced_points:
            add("PointTemplate", f"point-template:{x['point_tmpl_id']}", "point_template", x["point_tmpl_id"],
                name=x.get("point_name"), pointType=x.get("type"), unit=x.get("unit"),
                deleted=bool(x.get("is_deleted")))
    policies = {}
    for x in active("alarm_rule_numeric"):
        key = ("numeric", x["alarm_name"])
        uid = "policy:numeric:" + x["alarm_name"]
        policies[key] = uid
        add("AlarmPolicy", uid, "alarm_rule_numeric", x["id"], name=x["alarm_name"], kind="numeric",
            timeDelay=x.get("time_delay"), deadband=x.get("deadband"), updatedAt=str(x.get("update_time", "")))
        # Match RuleEvaluator's inclusive thresholds and priority order.
        for prefix, priority, operator in [("high_high", 1, ">="), ("high", 2, ">="),
                                            ("low_low", 3, "<="), ("low", 4, "<=")]:
            value = x.get(prefix + "_value")
            if value is not None and str(value).strip():
                condition = f"condition:numeric:{x['id']}:{prefix}"
                add("TriggerCondition", condition, "alarm_rule_numeric", x["id"], kind="numeric",
                    level=prefix, threshold=str(value), operator=operator, priority=priority,
                    severity=x.get(prefix + "_severity"), message=x.get(prefix + "_message"))
                link("HAS_CONDITION", uid, condition)
    grouped = defaultdict(list)
    for x in active("alarm_rule_enum"):
        grouped[x["alarm_name"]].append(x)
    for name, values in grouped.items():
        uid = "policy:enum:" + name
        policies[("enum", name)] = uid
        add("AlarmPolicy", uid, "alarm_rule_enum", name, name=name, kind="enum",
            timeDelay=values[0].get("time_delay"))
        for x in values:
            condition = f"condition:enum:{x['id']}"
            add("TriggerCondition", condition, "alarm_rule_enum", x["id"], kind="enum",
                enumValue=x.get("enum_value"), meaning=x.get("enum_meaning"),
                severity=x.get("severity"), message=x.get("message"))
            link("HAS_CONDITION", uid, condition)
    for x in devices:
        uid = f"device:{x['device_deploy_id']}"
        add("Device", uid, "device_deployment", x["device_deploy_id"],
            deviceDeployId=x["device_deploy_id"], name=x.get("device_deploy_name"),
            alias=x.get("device_alias"), location=x.get("location"), systemName=x.get("system_name"))
        if x.get("nav_node_id") is not None:
            link("DEPLOYED_IN", uid, f"deployment:{x['nav_node_id']}")
        link("INSTANCE_OF", uid, f"device-template:{x['device_tmpl_id']}")
    for x in deployments:
        if x.get("parent_id") is not None:
            link("CHILD_OF", f"deployment:{x['node_id']}", f"deployment:{x['parent_id']}")
    for x in points:
        path = x.get("n4_address")
        if not path or path in mqtt_paths:
            raise ValueError("Missing or duplicate MQTT path")
        mqtt_paths.add(path)
        uid = f"point:{x['point_deploy_id']}"
        add("Point", uid, "point_deployment", x["point_deploy_id"],
            pointDeployId=x["point_deploy_id"], name=x.get("point_name"),
            mqttPath=path, pointType=x.get("type"), unit=x.get("unit"))
        link("HAS_POINT", f"device:{x['device_deploy_id']}", uid)
        if x.get("point_tmpl_id") is not None:
            link("INSTANCE_OF", uid, f"point-template:{x['point_tmpl_id']}")
        if x.get("alarm_code") and x["alarm_code"].strip():
            kind = "numeric" if x.get("type", "").lower() == "numeric" else "enum"
            policy = policies.get((kind, x["alarm_code"]))
            if policy is None:
                raise ValueError("Missing alarm policy for " + uid)
            link("USES_POLICY", uid, policy)
    return nodes, edges
