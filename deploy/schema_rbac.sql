-- ==========================================================
-- N4 Monitor RBAC & Permission System Schema
-- ==========================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for sys_user
-- ----------------------------
DROP TABLE IF EXISTS `sys_user`;
CREATE TABLE `sys_user` (
  `user_id` bigint NOT NULL AUTO_INCREMENT COMMENT 'User ID',
  `username` varchar(50) NOT NULL COMMENT 'Login username',
  `password` varchar(255) NOT NULL COMMENT 'Encrypted password',
  `real_name` varchar(50) DEFAULT NULL COMMENT 'Real name / display name',
  `email` varchar(100) DEFAULT NULL COMMENT 'Email address',
  `phone` varchar(20) DEFAULT NULL COMMENT 'Phone number',
  `status` tinyint DEFAULT 1 COMMENT 'Status: 1-Active, 0-Disabled',
  `avatar` varchar(255) DEFAULT NULL COMMENT 'Avatar URL',
  `last_login_time` datetime DEFAULT NULL COMMENT 'Last login timestamp',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT 'Created timestamp',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Updated timestamp',
  `is_deleted` tinyint(1) DEFAULT 0 COMMENT 'Soft delete flag: 0-No, 1-Yes',
  PRIMARY KEY (`user_id`),
  UNIQUE KEY `uk_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='System User Table';

-- ----------------------------
-- Table structure for sys_group
-- ----------------------------
DROP TABLE IF EXISTS `sys_group`;
CREATE TABLE `sys_group` (
  `group_id` bigint NOT NULL AUTO_INCREMENT COMMENT 'Group/Role ID',
  `group_name` varchar(50) NOT NULL COMMENT 'Group name',
  `group_code` varchar(50) NOT NULL COMMENT 'Group code',
  `description` varchar(255) DEFAULT NULL COMMENT 'Description',
  `status` tinyint DEFAULT 1 COMMENT 'Status: 1-Active, 0-Disabled',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT 'Created timestamp',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Updated timestamp',
  `is_deleted` tinyint(1) DEFAULT 0 COMMENT 'Soft delete flag: 0-No, 1-Yes',
  PRIMARY KEY (`group_id`),
  UNIQUE KEY `uk_group_name` (`group_name`),
  UNIQUE KEY `uk_group_code` (`group_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='System User Group / Role Table';

-- ----------------------------
-- Table structure for sys_user_group
-- ----------------------------
DROP TABLE IF EXISTS `sys_user_group`;
CREATE TABLE `sys_user_group` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL COMMENT 'User ID',
  `group_id` bigint NOT NULL COMMENT 'Group ID',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_group` (`user_id`, `group_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='User-Group Mapping Table';

-- ----------------------------
-- Table structure for sys_permission
-- ----------------------------
DROP TABLE IF EXISTS `sys_permission`;
CREATE TABLE `sys_permission` (
  `perm_id` bigint NOT NULL AUTO_INCREMENT COMMENT 'Permission ID',
  `perm_name` varchar(50) NOT NULL COMMENT 'Permission name',
  `perm_code` varchar(100) NOT NULL COMMENT 'Permission unique code',
  `perm_type` tinyint NOT NULL COMMENT 'Type: 1-Menu, 2-Button, 3-API',
  `parent_id` bigint DEFAULT 0 COMMENT 'Parent permission ID (0 for root)',
  `path` varchar(255) DEFAULT NULL COMMENT 'Frontend route path',
  `component` varchar(255) DEFAULT NULL COMMENT 'Frontend component path',
  `icon` varchar(50) DEFAULT NULL COMMENT 'Icon name',
  `sort_order` int DEFAULT 0 COMMENT 'Display sort order',
  `status` tinyint DEFAULT 1 COMMENT 'Status: 1-Active, 0-Disabled',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT 'Created timestamp',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Updated timestamp',
  `is_deleted` tinyint(1) DEFAULT 0 COMMENT 'Soft delete flag: 0-No, 1-Yes',
  PRIMARY KEY (`perm_id`),
  UNIQUE KEY `uk_perm_code` (`perm_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Permission Resource Table';

-- ----------------------------
-- Table structure for sys_group_permission
-- ----------------------------
DROP TABLE IF EXISTS `sys_group_permission`;
CREATE TABLE `sys_group_permission` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `group_id` bigint NOT NULL COMMENT 'Group ID',
  `perm_id` bigint NOT NULL COMMENT 'Permission ID',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_group_perm` (`group_id`, `perm_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Group-Permission Mapping Table';

-- ----------------------------
-- Table structure for sys_data_scope
-- ----------------------------
DROP TABLE IF EXISTS `sys_data_scope`;
CREATE TABLE `sys_data_scope` (
  `scope_id` bigint NOT NULL AUTO_INCREMENT,
  `group_id` bigint NOT NULL COMMENT 'Group ID',
  `scope_type` varchar(50) NOT NULL COMMENT 'Scope type: location / system',
  `scope_value` varchar(255) NOT NULL COMMENT 'Scope target value (ID or path prefix)',
  PRIMARY KEY (`scope_id`),
  UNIQUE KEY `uk_group_type_value` (`group_id`, `scope_type`, `scope_value`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Data Scope Configuration Table';

-- ----------------------------
-- Table structure for sys_operation_log
-- ----------------------------
DROP TABLE IF EXISTS `sys_operation_log`;
CREATE TABLE `sys_operation_log` (
  `log_id` bigint NOT NULL AUTO_INCREMENT COMMENT 'Log ID',
  `operator_id` bigint DEFAULT NULL COMMENT 'Operator user ID',
  `operator_name` varchar(50) DEFAULT NULL COMMENT 'Operator username',
  `module` varchar(50) DEFAULT NULL COMMENT 'Module name',
  `operation` varchar(50) DEFAULT NULL COMMENT 'Operation summary',
  `business_type` int DEFAULT NULL COMMENT 'Business type (1-Create, 2-Update, 3-Delete, 4-Grant)',
  `method` varchar(255) DEFAULT NULL COMMENT 'Java method name',
  `request_method` varchar(10) DEFAULT NULL COMMENT 'HTTP request method (GET, POST, etc.)',
  `request_uri` varchar(255) DEFAULT NULL COMMENT 'Request URI',
  `request_params` text DEFAULT NULL COMMENT 'Request parameters (JSON)',
  `status` int DEFAULT 1 COMMENT 'Execution status: 1-Success, 0-Failed',
  `error_msg` text DEFAULT NULL COMMENT 'Error message',
  `cost_time` bigint DEFAULT 0 COMMENT 'Cost time in ms',
  `operator_ip` varchar(50) DEFAULT NULL COMMENT 'Operator IP address',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT 'Log timestamp',
  PRIMARY KEY (`log_id`),
  KEY `idx_log_operator` (`operator_name`),
  KEY `idx_log_module` (`module`),
  KEY `idx_log_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Operation Audit Log Table';

-- ----------------------------
-- Seed Data: Initial Groups / Roles
-- ----------------------------
INSERT INTO `sys_group` (`group_id`, `group_name`, `group_code`, `description`, `status`) VALUES
(1, 'Super Administrator', 'SUPER_ADMIN', 'Full system access and administrative privileges', 1),
(2, 'Operator', 'OPERATOR', 'Monitoring, device maintenance and basic operations', 1),
(3, 'Viewer', 'VIEWER', 'Read-only access to dashboard and alarm data', 1)
ON DUPLICATE KEY UPDATE `group_name` = VALUES(`group_name`);

-- ----------------------------
-- Seed Data: Permissions Tree
-- ----------------------------
INSERT INTO `sys_permission` (`perm_id`, `perm_name`, `perm_code`, `perm_type`, `parent_id`, `path`, `component`, `icon`, `sort_order`, `status`) VALUES
(1, 'Dashboard', 'dashboard:view', 1, 0, '/', 'DashboardView', 'Monitor', 1, 1),
(25, 'Alarms', 'alarm', 1, 0, NULL, NULL, 'Bell', 2, 1),
(2, 'Active Alarms', 'alarm:view', 1, 25, '/alarm', 'AlarmView', 'Bell', 1, 1),
(4, 'Alarm History', 'alarm:history:view', 1, 25, '/alarm/history', 'AlarmHistoryView', 'Clock', 2, 1),
(5, 'Event', 'alarm:event:view', 1, 0, '/event', 'AlarmEventView', 'List', 3, 1),
(28, 'Trend', 'trend', 1, 0, NULL, NULL, 'TrendCharts', 4, 1),
(29, 'Report', 'report', 1, 0, NULL, NULL, 'Document', 5, 1),
(10, 'System', 'system', 1, 0, NULL, NULL, 'Setting', 6, 1),
(6, 'Device Template', 'device:template:view', 1, 10, '/device-template', 'device-deploy/ProductManagerView', 'Cpu', 1, 1),
(33, 'Add Template', 'device:template:add', 2, 6, NULL, NULL, NULL, 1, 1),
(34, 'Edit Template', 'device:template:edit', 2, 6, NULL, NULL, NULL, 2, 1),
(35, 'Delete Template', 'device:template:delete', 2, 6, NULL, NULL, NULL, 3, 1),
(36, 'Import', 'device:template:import', 2, 6, NULL, NULL, NULL, 4, 1),
(37, 'Export', 'device:template:export', 2, 6, NULL, NULL, NULL, 5, 1),
(38, 'Add Point', 'device:template:point:add', 2, 6, NULL, NULL, NULL, 6, 1),
(39, 'Edit Point', 'device:template:point:edit', 2, 6, NULL, NULL, NULL, 7, 1),
(40, 'Delete Point', 'device:template:point:delete', 2, 6, NULL, NULL, NULL, 8, 1),
(41, 'Set Faceplate Panel', 'device:template:faceplate:view', 2, 6, NULL, NULL, NULL, 9, 1),
(7, 'Device Deployment', 'device:deploy:view', 1, 10, '/device-deployment', 'SystemDeploymentView', 'Operation', 2, 1),
(42, 'Add Group', 'device:deploy:group:add', 2, 7, NULL, NULL, NULL, 1, 1),
(43, 'Edit Group', 'device:deploy:group:edit', 2, 7, NULL, NULL, NULL, 2, 1),
(44, 'Delete Group', 'device:deploy:group:delete', 2, 7, NULL, NULL, NULL, 3, 1),
(45, 'Deploy Device', 'device:deploy:add', 2, 7, NULL, NULL, NULL, 4, 1),
(46, 'Edit Device', 'device:deploy:edit', 2, 7, NULL, NULL, NULL, 5, 1),
(47, 'Delete Device', 'device:deploy:delete', 2, 7, NULL, NULL, NULL, 6, 1),
(48, 'Mount Device', 'device:deploy:mount', 2, 7, NULL, NULL, NULL, 7, 1),
(49, 'Unmount Device', 'device:deploy:unmount', 2, 7, NULL, NULL, NULL, 8, 1),
(50, 'Import', 'device:deploy:import', 2, 7, NULL, NULL, NULL, 9, 1),
(51, 'Export', 'device:deploy:export', 2, 7, NULL, NULL, NULL, 10, 1),
(52, 'Set Device Alarm', 'device:deploy:alarm:edit', 2, 7, NULL, NULL, NULL, 11, 1),
(3, 'Alarm Configuration', 'alarm:config:view', 1, 10, '/alarm/config', 'AlarmConfigView', 'Setting', 3, 1),
(30, 'Authority Management', 'system:authority', 1, 10, NULL, NULL, 'Lock', 4, 1),
(11, 'User Management', 'sys:user:list', 1, 30, '/system/users', 'system/UserManagementView', 'User', 1, 1),
(12, 'Add User', 'sys:user:add', 2, 11, NULL, NULL, NULL, 1, 1),
(13, 'Edit User', 'sys:user:edit', 2, 11, NULL, NULL, NULL, 2, 1),
(14, 'Delete User', 'sys:user:delete', 2, 11, NULL, NULL, NULL, 3, 1),
(15, 'Role Management', 'sys:group:list', 1, 30, '/system/groups', 'system/GroupManagementView', 'Key', 2, 1),
(16, 'Add Role', 'sys:group:add', 2, 15, NULL, NULL, NULL, 1, 1),
(17, 'Edit Role', 'sys:group:edit', 2, 15, NULL, NULL, NULL, 2, 1),
(18, 'Delete Role', 'sys:group:delete', 2, 15, NULL, NULL, NULL, 3, 1),
(19, 'Permissions', 'sys:permission:list', 1, 30, '/system/permissions', 'system/PermissionManagementView', 'Lock', 3, 1),
(20, 'Add Permission', 'sys:permission:add', 2, 19, NULL, NULL, NULL, 1, 1),
(21, 'Edit Permission', 'sys:permission:edit', 2, 19, NULL, NULL, NULL, 2, 1),
(22, 'Delete Permission', 'sys:permission:delete', 2, 19, NULL, NULL, NULL, 3, 1),
(23, 'Audit Logs', 'sys:log:list', 1, 30, '/logs', 'LogView', 'Tickets', 4, 1),
(27, 'Delete Logs', 'sys:log:delete', 2, 23, NULL, NULL, NULL, 1, 1),
(31, 'Change Password', 'auth:password:change', 2, 30, NULL, NULL, 'EditPen', 5, 1),
(32, 'Logout', 'auth:logout', 2, 30, NULL, NULL, 'SwitchButton', 6, 1)
ON DUPLICATE KEY UPDATE
  `perm_name` = VALUES(`perm_name`),
  `perm_type` = VALUES(`perm_type`),
  `parent_id` = VALUES(`parent_id`),
  `path` = VALUES(`path`),
  `component` = VALUES(`component`),
  `icon` = VALUES(`icon`),
  `sort_order` = VALUES(`sort_order`),
  `status` = VALUES(`status`),
  `is_deleted` = 0;

UPDATE `sys_permission`
SET `status` = 0, `is_deleted` = 1
WHERE `perm_code` IN ('dashboard', 'device');

-- ----------------------------
-- Seed Data: Super Admin Group Permission Mapping (All permissions)
-- ----------------------------
INSERT INTO `sys_group_permission` (`group_id`, `perm_id`)
SELECT 1, `perm_id` FROM `sys_permission`
ON DUPLICATE KEY UPDATE `group_id` = VALUES(`group_id`);

-- ----------------------------
-- Seed Data: Operator Group Permission Mapping (Dashboard, Alarms, Deployments)
-- ----------------------------
INSERT INTO `sys_group_permission` (`group_id`, `perm_id`)
SELECT 2, `perm_id` FROM `sys_permission`
WHERE `perm_code` IN (
  'dashboard:view',
  'alarm', 'alarm:view', 'alarm:history:view', 'alarm:event:view',
  'system',
  'device:template:view', 'device:template:add', 'device:template:edit',
  'device:template:import', 'device:template:export',
  'device:template:point:add', 'device:template:point:edit',
  'device:template:faceplate:view',
  'device:deploy:view', 'device:deploy:group:add', 'device:deploy:group:edit',
  'device:deploy:add', 'device:deploy:edit', 'device:deploy:mount',
  'device:deploy:unmount', 'device:deploy:import', 'device:deploy:export',
  'device:deploy:alarm:edit',
  'alarm:config:view'
)
ON DUPLICATE KEY UPDATE `group_id` = VALUES(`group_id`);

-- ----------------------------
-- Seed Data: Viewer Group Permission Mapping (Dashboard, Alarms, History, Events)
-- ----------------------------
INSERT INTO `sys_group_permission` (`group_id`, `perm_id`)
SELECT 3, `perm_id` FROM `sys_permission`
WHERE `perm_code` IN (
  'dashboard:view',
  'alarm', 'alarm:view', 'alarm:history:view', 'alarm:event:view'
)
ON DUPLICATE KEY UPDATE `group_id` = VALUES(`group_id`);

-- ----------------------------
-- Seed Data: Default Admin User (Password BCrypt of 'admin123' is $2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2)
-- ----------------------------
INSERT INTO `sys_user` (`user_id`, `username`, `password`, `real_name`, `email`, `phone`, `status`) VALUES
(1, 'admin', '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2', 'n4-dhu', 'admin@n4-monitor.local', '13800000000', 1)
ON DUPLICATE KEY UPDATE `username` = VALUES(`username`);

-- Super Admin User-Group Mapping
INSERT INTO `sys_user_group` (`user_id`, `group_id`) VALUES (1, 1)
ON DUPLICATE KEY UPDATE `user_id` = VALUES(`user_id`);

SET FOREIGN_KEY_CHECKS = 1;
