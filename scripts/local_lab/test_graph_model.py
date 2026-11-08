import unittest
from graph_model import build_graph

class GraphModelTest(unittest.TestCase):
    def source(self):
        return {
            "system_deployment": [{"node_id": 1, "parent_id": None}],
            "device_template": [{"device_tmpl_id": 2}],
            "point_template": [{"point_tmpl_id": 3, "device_tmpl_id": 2}],
            "device_deployment": [{"device_deploy_id": 4, "nav_node_id": 1, "device_tmpl_id": 2}],
            "point_deployment": [{"point_deploy_id": 5, "device_deploy_id": 4, "point_tmpl_id": 3,
                                  "n4_address": "local/device/value", "type": "enum", "alarm_code": "status"}],
            "alarm_rule_numeric": [],
            "alarm_rule_enum": [{"id": 6, "alarm_name": "status", "enum_value": "Normal"},
                                {"id": 7, "alarm_name": "status", "enum_value": "Alarm", "severity": "Minor"}]}
    def test_enum_rows_form_one_policy_and_two_conditions(self):
        nodes, edges = build_graph(self.source())
        self.assertEqual(1, sum(n["label"] == "AlarmPolicy" for n in nodes))
        self.assertEqual(2, sum(n["label"] == "TriggerCondition" for n in nodes))
        bindings = [e for e in edges if e["type"] == "USES_POLICY"]
        self.assertEqual(1, len(bindings))
        self.assertEqual("point:5", bindings[0]["source"])
    def test_unassigned_device_is_kept_without_inventing_location(self):
        source = self.source()
        source["device_deployment"][0]["nav_node_id"] = None
        nodes, edges = build_graph(source)
        self.assertTrue(any(n["uid"] == "device:4" for n in nodes))
        self.assertFalse(any(e["type"] == "DEPLOYED_IN" for e in edges))

    def test_missing_device_is_rejected(self):
        source = self.source()
        source["device_deployment"] = []
        with self.assertRaisesRegex(ValueError, "device"):
            build_graph(source)
    def test_duplicate_mqtt_path_is_rejected(self):
        source = self.source()
        source["point_deployment"].append(dict(source["point_deployment"][0], point_deploy_id=8))
        with self.assertRaisesRegex(ValueError, "MQTT"):
            build_graph(source)
    def test_numeric_conditions_only_include_configured_limits(self):
        source = self.source()
        source["point_deployment"][0].update(type="numeric", alarm_code="gas")
        source["alarm_rule_numeric"] = [{"id": 8, "alarm_name": "gas", "high_value": "20", "low_value": None}]
        nodes, edges = build_graph(source)
        conditions = [n for n in nodes if n["label"] == "TriggerCondition" and n["props"].get("kind") == "numeric"]
        self.assertEqual(1, len(conditions))
        self.assertEqual("20", conditions[0]["props"]["threshold"])
if __name__ == "__main__":
    unittest.main()
