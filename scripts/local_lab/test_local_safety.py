import unittest
from common import graph, mysql, require_local

class LocalSafetyTest(unittest.TestCase):
    def test_remote_destination_is_rejected_before_connecting(self):
        with self.assertRaisesRegex(ValueError, "loopback"):
            require_local("192.0.2.1")
    def test_regular_local_mysql_is_not_a_lab_destination(self):
        with self.assertRaisesRegex(ValueError, "dedicated"):
            mysql({"MYSQL_URL": "jdbc:mysql://127.0.0.1:3306/n4_monitor"}, destination=True)
    def test_other_local_database_is_not_a_lab_destination(self):
        with self.assertRaisesRegex(ValueError, "dedicated"):
            mysql({"MYSQL_URL": "jdbc:mysql://127.0.0.1:23306/other"}, destination=True)
    def test_remote_graph_is_rejected_before_connecting(self):
        with self.assertRaisesRegex(ValueError, "loopback"):
            graph({"NEO4J_HTTP_URL": "http://192.0.2.1:17474"}, "RETURN 1")

if __name__ == "__main__":
    unittest.main()
