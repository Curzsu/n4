-- ============================================================
-- Dashboard 左侧导航栏专用表初始化
-- 在应用实际连接的 n4_monitor 库中执行
-- ============================================================

USE `n4_monitor`;

-- 1. 新建导航节点表（两级自关联）
CREATE TABLE IF NOT EXISTS `nav_node` (
  `node_id`      bigint       NOT NULL AUTO_INCREMENT COMMENT '导航节点ID',
  `parent_id`    bigint       DEFAULT NULL             COMMENT '父节点ID；一级节点为NULL，二级节点指向所属一级',
  `node_name`    varchar(100) NOT NULL                  COMMENT '节点显示名',
  `node_level`   tinyint      NOT NULL                  COMMENT '层级：1=一级分类，2=二级分类',
  `node_type`    varchar(30)  NOT NULL DEFAULT 'group'  COMMENT '节点类型：group=设备分组',
  `max_level`    tinyint      DEFAULT NULL              COMMENT '该一级节点允许的最大层级，NULL表示使用默认层级',
  `icon`         varchar(50)  DEFAULT NULL              COMMENT '前端图标名，一级节点用',
  `sort_order`   int          NOT NULL DEFAULT 0        COMMENT '同级排序序号',
  `is_deleted`   tinyint(1)   NOT NULL DEFAULT 0        COMMENT '逻辑删除',
  `create_time`  datetime     DEFAULT CURRENT_TIMESTAMP,
  `update_time`  datetime     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`node_id`),
  KEY `idx_parent` (`parent_id`),
  KEY `idx_level`  (`node_level`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Dashboard左侧导航节点表';

-- 2. device_deployment 加导航字段和索引（允许在部分迁移过的数据库上重复执行）
SET @add_nav_column = IF(
  EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'device_deployment'
      AND column_name = 'nav_node_id'
  ),
  'SELECT 1',
  'ALTER TABLE `device_deployment` ADD COLUMN `nav_node_id` bigint DEFAULT NULL COMMENT ''所属导航节点ID'' AFTER `device_tmpl_id`'
);
PREPARE add_nav_column_stmt FROM @add_nav_column;
EXECUTE add_nav_column_stmt;
DEALLOCATE PREPARE add_nav_column_stmt;

SET @add_nav_index = IF(
  EXISTS (
    SELECT 1 FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'device_deployment'
      AND index_name = 'idx_nav_node_id'
  ),
  'SELECT 1',
  'ALTER TABLE `device_deployment` ADD INDEX `idx_nav_node_id` (`nav_node_id`)'
);
PREPARE add_nav_index_stmt FROM @add_nav_index;
EXECUTE add_nav_index_stmt;
DEALLOCATE PREPARE add_nav_index_stmt;

-- 3. 预置一级节点（8个）
INSERT INTO `nav_node` (`node_name`, `node_level`, `node_type`, `icon`, `sort_order`) VALUES
('房间/方舱类型',              1, 'group', 'Location',       1),
('电气系统图',                 1, 'group', 'Picture',        2),
('电气系统',                   1, 'group', 'OfficeBuilding', 3),
('BA系统',                     1, 'group', 'Fan',            4),
('设备总览',                   1, 'group', 'Grid',           5),
('柴油发电机系统/油路系统',     1, 'group', 'Lightning',      6),
('消防系统',                   1, 'group', 'Warning',        7),
('自定义',                     1, 'group', 'EditPen',        8);

-- 4. 预置二级节点（21个）。parent_id 用子查询取一级节点id，避免硬编码。
-- 4.1 房间/方舱类型（5个，全mock）
INSERT INTO `nav_node` (`parent_id`, `node_name`, `node_level`, `sort_order`)
SELECT n.node_id, v.name, 2, v.sort
FROM `nav_node` n
JOIN (SELECT 'DataHall Room 25' name, 1 sort UNION ALL
      SELECT 'Public Area GF', 2 UNION ALL
      SELECT 'Public Area RF', 3 UNION ALL
      SELECT 'A1 PTU-A', 4 UNION ALL
      SELECT 'A1 PTU-B', 5) v
WHERE n.node_name='房间/方舱类型' AND n.node_level=1;

-- 4.2 电气系统图（2个，全mock）
INSERT INTO `nav_node` (`parent_id`, `node_name`, `node_level`, `sort_order`)
SELECT n.node_id, v.name, 2, v.sort
FROM `nav_node` n
JOIN (SELECT '电气系统图总览' name, 1 sort UNION ALL
      SELECT 'PTU单独系统图', 2) v
WHERE n.node_name='电气系统图' AND n.node_level=1;

-- 4.3 电气系统（5个，全真设备）
INSERT INTO `nav_node` (`parent_id`, `node_name`, `node_level`, `sort_order`)
SELECT n.node_id, v.name, 2, v.sort
FROM `nav_node` n
JOIN (SELECT '中压系统' name, 1 sort UNION ALL
      SELECT '低压系统', 2 UNION ALL
      SELECT 'UPS系统', 3 UNION ALL
      SELECT '直流系统', 4 UNION ALL
      SELECT '采集网关', 5) v
WHERE n.node_name='电气系统' AND n.node_level=1;

-- 4.4 BA系统（2个，风冷真设备，液冷mock）
INSERT INTO `nav_node` (`parent_id`, `node_name`, `node_level`, `sort_order`)
SELECT n.node_id, v.name, 2, v.sort
FROM `nav_node` n
JOIN (SELECT '风冷系统' name, 1 sort UNION ALL
      SELECT '液冷系统', 2) v
WHERE n.node_name='BA系统' AND n.node_level=1;

-- 4.5 设备总览（2个，全真设备）
INSERT INTO `nav_node` (`parent_id`, `node_name`, `node_level`, `sort_order`)
SELECT n.node_id, v.name, 2, v.sort
FROM `nav_node` n
JOIN (SELECT '温湿度' name, 1 sort UNION ALL
      SELECT '空调系统', 2) v
WHERE n.node_name='设备总览' AND n.node_level=1;

-- 4.6 柴油发电机系统/油路系统（2个，全mock）
INSERT INTO `nav_node` (`parent_id`, `node_name`, `node_level`, `sort_order`)
SELECT n.node_id, v.name, 2, v.sort
FROM `nav_node` n
JOIN (SELECT '柴油发电机系统' name, 1 sort UNION ALL
      SELECT '油路系统', 2) v
WHERE n.node_name='柴油发电机系统/油路系统' AND n.node_level=1;

-- 4.7 消防系统（2个，全真设备）
INSERT INTO `nav_node` (`parent_id`, `node_name`, `node_level`, `sort_order`)
SELECT n.node_id, v.name, 2, v.sort
FROM `nav_node` n
JOIN (SELECT '气体灭火/极早期' name, 1 sort UNION ALL
      SELECT '水消防/水压监测', 2) v
WHERE n.node_name='消防系统' AND n.node_level=1;

-- 4.8 自定义（1个，mock）
INSERT INTO `nav_node` (`parent_id`, `node_name`, `node_level`, `sort_order`)
SELECT n.node_id, '自定义分组/模板', 2, 1
FROM `nav_node` n
WHERE n.node_name='自定义' AND n.node_level=1;

-- 5. 挂载真实设备：用 CASE 映射 device_type -> 二级节点名
UPDATE device_deployment dd
JOIN device_template dt ON dd.device_tmpl_id = dt.device_tmpl_id
JOIN nav_node nn ON nn.node_level = 2
SET dd.nav_node_id = nn.node_id
WHERE dd.is_deleted = 0
  AND nn.node_name = CASE dt.device_type
    WHEN 'DHG' THEN '中压系统'
    WHEN 'EM' THEN '低压系统'
    WHEN 'ZB' THEN '低压系统'
    WHEN 'UDB' THEN 'UPS系统'
    WHEN 'EDB' THEN 'UPS系统'
    WHEN 'DCP' THEN '直流系统'
    WHEN 'Collector' THEN '采集网关'
    WHEN 'IRC1' THEN '风冷系统'
    WHEN 'IRC2' THEN '风冷系统'
    WHEN 'DEHU' THEN '空调系统'
    WHEN 'TH1' THEN '温湿度'
    WHEN 'TH2' THEN '温湿度'
    WHEN 'TH3' THEN '温湿度'
    WHEN 'GAS1' THEN '气体灭火/极早期'
    WHEN 'GAS2' THEN '气体灭火/极早期'
    WHEN 'LD' THEN '水消防/水压监测'
  END;

-- 6. 为无真设备的二级节点插入 mock 占位设备
INSERT INTO device_deployment
  (device_tmpl_id, device_deploy_name, device_alias, n4_address, sys_sort_order, status, nav_node_id)
SELECT 1, CONCAT('MOCK_', nn.node_name), CONCAT('MOCK_', nn.node_name), CONCAT('mock://', nn.node_name), 0, 2, nn.node_id
FROM nav_node nn
WHERE nn.node_level = 2
  AND nn.is_deleted = 0
  AND NOT EXISTS (
    SELECT 1 FROM device_deployment dd
    WHERE dd.nav_node_id = nn.node_id AND dd.is_deleted = 0
  );
