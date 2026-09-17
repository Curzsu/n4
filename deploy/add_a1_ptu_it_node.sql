-- Add the A1_PTU_IT cabin node under the A1_PTU category.
-- Hierarchy follows existing level-2 cabins (A1_PTU_A1 sort 1, A1_PTU_BS1 sort 2,
-- A1_PTU_IT sort 3). The node shows as a leaf group with device count (0) until
-- devices are deployed under it.
-- The frontend cabinImageUrl mapping binds it to /cabin-images/A1-PTU-IT.png.
-- Applied on the test server MySQL (n4_monitor) on 2026-09-17.

INSERT INTO system_deployment
  (node_name, parent_id, node_level, node_type, max_level, icon, sort_order, mqtt_server_addresses)
SELECT 'A1_PTU_IT', node_id, 2, 'group', NULL, NULL, 3, NULL
FROM system_deployment
WHERE node_name = 'A1_PTU' AND node_level = 1 AND is_deleted = 0
LIMIT 1;
