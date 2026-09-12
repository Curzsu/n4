package com.n4monitor.config;

import java.util.List;

public final class RbacPermissionCatalog {
    public static final int MENU = 1;
    public static final int BUTTON = 2;

    private static final List<PermissionDefinition> DEFINITIONS = List.of(
            menu("dashboard", "dashboard", null, null, null, "Monitor", 1),
            menu("dashboard:view", "Dashboard", "dashboard", "/", "DashboardView", "Monitor", 1),

            menu("alarm", "alarm", null, null, null, "Bell", 2),
            menu("alarm:view", "Alarms", "alarm", "/alarm", "AlarmView", "Bell", 1),
            menu("alarm:config:view", "Alarm Configuration", "alarm", "/alarm/config", "AlarmConfigView", "Tools", 2),
            menu("alarm:history:view", "Alarm History", "alarm", "/alarm/history", "AlarmHistoryView", "Clock", 3),
            menu("alarm:event:view", "Event", "alarm", "/event", "AlarmEventView", "List", 4),

            menu("device", "device", null, null, null, "SetUp", 3),
            menu("device:template:view", "Device Template", "device", "/device-template", "device-deploy/ProductManagerView", "Coin", 1),
            menu("device:deploy:view", "Device Deployment", "device", "/device-deployment", "SystemDeploymentView", "SetUp", 2),

            menu("system", "system", null, null, null, "Setting", 4),
            menu("sys:user:list", "User Management", "system", "/system/users", "system/UserManagementView", "User", 1),
            button("sys:user:add", "Add User", "sys:user:list", 1),
            button("sys:user:edit", "Edit User", "sys:user:list", 2),
            button("sys:user:delete", "Delete User", "sys:user:list", 3),
            menu("sys:group:list", "Role Management", "system", "/system/groups", "system/GroupManagementView", "UserFilled", 2),
            button("sys:group:add", "Add Role", "sys:group:list", 1),
            button("sys:group:edit", "Edit Role", "sys:group:list", 2),
            button("sys:group:delete", "Delete Role", "sys:group:list", 3),
            menu("sys:permission:list", "Permissions", "system", "/system/permissions", "system/PermissionManagementView", "Lock", 3),
            button("sys:permission:add", "Add Permission", "sys:permission:list", 1),
            button("sys:permission:edit", "Edit Permission", "sys:permission:list", 2),
            button("sys:permission:delete", "Delete Permission", "sys:permission:list", 3),
            menu("sys:log:list", "Audit Logs", "system", "/logs", "LogView", "Document", 4),
            button("sys:log:delete", "Delete Audit Log", "sys:log:list", 1)
    );

    private RbacPermissionCatalog() {
    }

    public static List<PermissionDefinition> definitions() {
        return DEFINITIONS;
    }

    private static PermissionDefinition menu(
            String code,
            String name,
            String parentCode,
            String path,
            String component,
            String icon,
            int sortOrder) {
        return new PermissionDefinition(code, name, MENU, parentCode, path, component, icon, sortOrder);
    }

    private static PermissionDefinition button(String code, String name, String parentCode, int sortOrder) {
        return new PermissionDefinition(code, name, BUTTON, parentCode, null, null, null, sortOrder);
    }

    public record PermissionDefinition(
            String code,
            String name,
            int type,
            String parentCode,
            String path,
            String component,
            String icon,
            int sortOrder) {
    }
}
