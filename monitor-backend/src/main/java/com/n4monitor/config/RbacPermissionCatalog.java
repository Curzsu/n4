package com.n4monitor.config;

import java.util.List;

public final class RbacPermissionCatalog {
    public static final int MENU = 1;
    public static final int BUTTON = 2;

    private static final List<PermissionDefinition> DEFINITIONS = List.of(
            menu("dashboard:view", "Dashboard", null, "/", "DashboardView", "Monitor", 1),

            menu("alarm", "Alarms", null, null, null, "Bell", 2),
            menu("alarm:view", "Active Alarms", "alarm", "/alarm", "AlarmView", "Bell", 1),
            menu("alarm:history:view", "Alarm History", "alarm", "/alarm/history", "AlarmHistoryView", "Clock", 2),

            menu("alarm:event:view", "Event", null, "/event", "AlarmEventView", "List", 3),
            menu("trend", "Trend", null, null, null, "TrendCharts", 4),
            menu("report", "Report", null, null, null, "Document", 5),

            menu("system", "System", null, null, null, "Setting", 6),
            menu("device:template:view", "Device Template", "system", "/device-template", "device-deploy/ProductManagerView", "Cpu", 1),
            menu("device:deploy:view", "Device Deployment", "system", "/device-deployment", "SystemDeploymentView", "Operation", 2),
            menu("alarm:config:view", "Alarm Configuration", "system", "/alarm/config", "AlarmConfigView", "Setting", 3),
            menu("system:authority", "Authority Management", "system", null, null, "Lock", 4),

            menu("sys:user:list", "User Management", "system:authority", "/system/users", "system/UserManagementView", "User", 1),
            button("sys:user:add", "Add User", "sys:user:list", 1),
            button("sys:user:edit", "Edit User", "sys:user:list", 2),
            button("sys:user:delete", "Delete User", "sys:user:list", 3),
            menu("sys:group:list", "Role Management", "system:authority", "/system/groups", "system/GroupManagementView", "Key", 2),
            button("sys:group:add", "Add Role", "sys:group:list", 1),
            button("sys:group:edit", "Edit Role", "sys:group:list", 2),
            button("sys:group:delete", "Delete Role", "sys:group:list", 3),
            menu("sys:permission:list", "Permissions", "system:authority", "/system/permissions", "system/PermissionManagementView", "Lock", 3),
            button("sys:permission:add", "Add Permission", "sys:permission:list", 1),
            button("sys:permission:edit", "Edit Permission", "sys:permission:list", 2),
            button("sys:permission:delete", "Delete Permission", "sys:permission:list", 3),
            menu("sys:log:list", "Audit Logs", "system:authority", "/logs", "LogView", "Tickets", 4),
            button("sys:log:delete", "Delete Logs", "sys:log:list", 1),
            button("auth:password:change", "Change Password", "system:authority", 5),
            button("auth:logout", "Logout", "system:authority", 6)
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
