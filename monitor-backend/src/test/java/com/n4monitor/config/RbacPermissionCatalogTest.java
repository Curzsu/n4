package com.n4monitor.config;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class RbacPermissionCatalogTest {
    @Test
    void exposesCanonicalPermissionHierarchy() {
        List<RbacPermissionCatalog.PermissionDefinition> catalog =
                RbacPermissionCatalog.definitions();
        Map<String, RbacPermissionCatalog.PermissionDefinition> definitions =
                catalog.stream()
                        .collect(Collectors.toMap(
                                RbacPermissionCatalog.PermissionDefinition::code,
                                Function.identity()));

        assertEquals(List.of(
                        "dashboard:view",
                        "alarm",
                        "alarm:event:view",
                        "trend",
                        "report",
                        "system"),
                catalog.stream()
                        .filter(definition -> definition.parentCode() == null)
                        .map(RbacPermissionCatalog.PermissionDefinition::code)
                        .toList());
        assertNull(definitions.get("dashboard"));
        assertNull(definitions.get("device"));
        assertNull(definitions.get("system:manage"));

        assertParent(definitions, "dashboard:view", null, "Dashboard", "/", "DashboardView");
        assertParent(definitions, "alarm", null, "Alarms", null, null);
        assertParent(definitions, "alarm:view", "alarm", "Active Alarms", "/alarm", "AlarmView");
        assertParent(definitions, "alarm:history:view", "alarm", "Alarm History", "/alarm/history", "AlarmHistoryView");
        assertParent(definitions, "alarm:event:view", null, "Event", "/event", "AlarmEventView");
        assertParent(definitions, "trend", null, "Trend", null, null);
        assertParent(definitions, "report", null, "Report", null, null);
        assertParent(definitions, "system", null, "System", null, null);

        assertParent(definitions, "device:template:view", "system", "Device Template", "/device-template", "device-deploy/ProductManagerView");
        assertParent(definitions, "device:deploy:view", "system", "Device Deployment", "/device-deployment", "SystemDeploymentView");
        assertParent(definitions, "alarm:config:view", "system", "Alarm Configuration", "/alarm/config", "AlarmConfigView");
        assertParent(definitions, "system:authority", "system", "Authority Management", null, null);

        assertParent(definitions, "sys:user:list", "system:authority", "User Management", "/system/users", "system/UserManagementView");
        assertParent(definitions, "sys:group:list", "system:authority", "Role Management", "/system/groups", "system/GroupManagementView");
        assertParent(definitions, "sys:permission:list", "system:authority", "Permissions", "/system/permissions", "system/PermissionManagementView");
        assertParent(definitions, "sys:log:list", "system:authority", "Audit Logs", "/logs", "LogView");
        assertParent(definitions, "auth:password:change", "system:authority", "Change Password", null, null);
        assertParent(definitions, "auth:logout", "system:authority", "Logout", null, null);
        assertEquals(RbacPermissionCatalog.BUTTON, definitions.get("auth:password:change").type());
        assertEquals(RbacPermissionCatalog.BUTTON, definitions.get("auth:logout").type());

        assertParent(definitions, "sys:user:add", "sys:user:list", "Add User", null, null);
        assertParent(definitions, "sys:user:edit", "sys:user:list", "Edit User", null, null);
        assertParent(definitions, "sys:user:delete", "sys:user:list", "Delete User", null, null);
        assertParent(definitions, "sys:group:add", "sys:group:list", "Add Role", null, null);
        assertParent(definitions, "sys:group:edit", "sys:group:list", "Edit Role", null, null);
        assertParent(definitions, "sys:group:delete", "sys:group:list", "Delete Role", null, null);
        assertParent(definitions, "sys:permission:add", "sys:permission:list", "Add Permission", null, null);
        assertParent(definitions, "sys:permission:edit", "sys:permission:list", "Edit Permission", null, null);
        assertParent(definitions, "sys:permission:delete", "sys:permission:list", "Delete Permission", null, null);
        assertParent(definitions, "sys:log:delete", "sys:log:list", "Delete Logs", null, null);
    }

    private static void assertParent(
            Map<String, RbacPermissionCatalog.PermissionDefinition> definitions,
            String code,
            String parentCode,
            String name,
            String path,
            String component) {
        RbacPermissionCatalog.PermissionDefinition definition = definitions.get(code);
        assertNotNull(definition, "Missing permission: " + code);
        assertEquals(parentCode, definition.parentCode(), code);
        assertEquals(name, definition.name(), code);
        assertEquals(path, definition.path(), code);
        assertEquals(component, definition.component(), code);
        assertFalse(definition.code().isBlank());
    }
}
