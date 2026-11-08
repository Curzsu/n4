import unittest
from export_viewer import build_payload

class ViewerExportTest(unittest.TestCase):
    def nodes(self):
        return [
            {"uid": "device:1", "labels": ["N4Entity", "Device"],
             "props": {"name": "Device A", "snapshot": "s1", "deviceDeployId": 1, "password": "not-public"}},
            {"uid": "point:2", "labels": ["N4Entity", "Point"],
             "props": {"name": "Temperature", "snapshot": "s1", "unit": "C"}}]
    def test_export_is_complete_and_only_contains_public_graph_properties(self):
        payload = build_payload(self.nodes(), [{"source":"device:1","target":"point:2","type":"HAS_POINT"}], "s1")
        self.assertIn("nodes", payload)
        self.assertEqual(2, len(payload["nodes"]))
        self.assertEqual(1, len(payload["edges"]))
        self.assertNotIn("password", payload["nodes"][0]["props"])
    def test_cross_snapshot_data_is_rejected(self):
        nodes = self.nodes()
        nodes[1]["props"]["snapshot"] = "s2"
        with self.assertRaises(ValueError):
            build_payload(nodes, [], "s1")
    def test_dangling_edges_are_rejected(self):
        with self.assertRaises(ValueError):
            build_payload(self.nodes(), [{"source":"device:1","target":"missing","type":"HAS_POINT"}], "s1")
    def test_duplicate_nodes_are_rejected(self):
        with self.assertRaises(ValueError):
            build_payload(self.nodes() * 2, [], "s1")

if __name__ == "__main__":
    unittest.main()
