-- Free the n4_address unique-key space held by unmounted devices.
-- Invariant: a device with nav_node_id IS NULL must not hold a live address,
-- otherwise moving another device into the same path collides with
-- point_deployment.uk_n4_address. Mounting recomputes addresses from the new
-- node path, so rewriting these to tombstones is safe.
-- Applied on the test server MySQL (n4_monitor) on 2026-09-21.

UPDATE device_deployment
SET n4_address = CONCAT('unmounted/', device_deploy_id)
WHERE nav_node_id IS NULL AND is_deleted = 0
  AND n4_address IS NOT NULL AND n4_address NOT LIKE 'unmounted/%';

UPDATE point_deployment pd
INNER JOIN device_deployment dd ON dd.device_deploy_id = pd.device_deploy_id
SET pd.n4_address = CONCAT('unmounted/', pd.point_deploy_id, '/', LEFT(pd.point_name, 40)),
    pd.update_time = NOW()
WHERE dd.nav_node_id IS NULL AND dd.is_deleted = 0 AND pd.is_deleted = 0
  AND pd.n4_address NOT LIKE 'unmounted/%';
