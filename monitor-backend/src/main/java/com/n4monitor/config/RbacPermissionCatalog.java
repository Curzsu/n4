package com.n4monitor.config;

import java.util.List;
import java.util.Set;

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
            button("device:template:add", "Add Template", "device:template:view", 1),
            button("device:template:edit", "Edit Template", "device:template:view", 2),
            button("device:template:delete", "Delete Template", "device:template:view", 3),
            button("device:template:import", "Import", "device:template:view", 4),
            button("device:template:export", "Export", "device:template:view", 5),
            button("device:template:point:add", "Add Point", "device:template:view", 6),
            button("device:template:point:edit", "Edit Point", "device:template:view", 7),
            button("device:template:point:delete", "Delete Point", "device:template:view", 8),
            button("device:template:faceplate:view", "Set Faceplate Panel", "device:template:view", 9),
            menu("device:deploy:view", "Device Deployment", "system", "/device-deployment", "SystemDeploymentView", "Operation", 2),
            button("device:deploy:group:add", "Add Group", "device:deploy:view", 1),
            button("device:deploy:group:edit", "Edit Group", "device:deploy:view", 2),
            button("device:deploy:group:delete", "Delete Group", "device:deploy:view", 3),
            button("device:deploy:group:batch-move", "Batch Move Groups", "device:deploy:view", 3),
            button("device:deploy:add", "Deploy Device", "device:deploy:view", 4),
            button("device:deploy:edit", "Edit Device", "device:deploy:view", 5),
            button("device:deploy:delete", "Delete Device", "device:deploy:view", 6),
            button("device:deploy:mount", "Mount Device", "device:deploy:view", 7),
            button("device:deploy:unmount", "Unmount Device", "device:deploy:view", 8),
            button("device:deploy:import", "Import", "device:deploy:view", 9),
            button("device:deploy:export", "Export", "device:deploy:view", 10),
            button("device:deploy:alarm:edit", "Set Device Alarm", "device:deploy:view", 11),
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
            button("auth:logout", "Logout", "system:authority", 6),

            menu("data-assistant:view", "Ask Data", null, "/ask-data", "DataAssistantView", "TrendCharts", 7)
    );

    private static final Set<String> OPERATOR_PERMISSION_CODES = Set.of(
            "dashboard:view",
            "alarm",
            "alarm:view",
            "alarm:history:view",
            "alarm:event:view",
            "system",
            "device:template:view",
            "device:template:add",
            "device:template:edit",
            "device:template:import",
            "device:template:export",
            "device:template:point:add",
            "device:template:point:edit",
            "device:template:faceplate:view",
            "device:deploy:view",
            "device:deploy:group:add",
            "device:deploy:group:edit",
            "device:deploy:group:batch-move",
            "device:deploy:add",
            "device:deploy:edit",
            "device:deploy:mount",
            "device:deploy:unmount",
            "device:deploy:import",
            "device:deploy:export",
            "device:deploy:alarm:edit",
            "alarm:config:view");

    private RbacPermissionCatalog() {
    }

    public static List<PermissionDefinition> definitions() {
        return DEFINITIONS;
    }

    public static Set<String> operatorPermissionCodes() {
        return OPERATOR_PERMISSION_CODES;
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
