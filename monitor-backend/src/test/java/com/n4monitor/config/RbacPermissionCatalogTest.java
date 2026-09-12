package com.n4monitor.config;

import org.junit.jupiter.api.Test;

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
        Map<String, RbacPermissionCatalog.PermissionDefinition> definitions =
                RbacPermissionCatalog.definitions().stream()
                        .collect(Collectors.toMap(
                                RbacPermissionCatalog.PermissionDefinition::code,
                                Function.identity()));

        assertEquals(4, definitions.values().stream()
                .filter(definition -> definition.parentCode() == null)
                .count());
        assertEquals("dashboard", definitions.get("dashboard").name());
        assertEquals("alarm", definitions.get("alarm").name());
        assertEquals("device", definitions.get("device").name());
        assertEquals("system", definitions.get("system").name());
        assertNull(definitions.get("system:manage"));

        assertParent(definitions, "dashboard:view", "dashboard", "Dashboard", "/", "DashboardView");
        assertParent(definitions, "alarm:view", "alarm", "Alarms", "/alarm", "AlarmView");
        assertParent(definitions, "alarm:config:view", "alarm", "Alarm Configuration", "/alarm/config", "AlarmConfigView");
        assertParent(definitions, "alarm:history:view", "alarm", "Alarm History", "/alarm/history", "AlarmHistoryView");
        assertParent(definitions, "alarm:event:view", "alarm", "Event", "/event", "AlarmEventView");
        assertParent(definitions, "device:template:view", "device", "Device Template", "/device-template", "device-deploy/ProductManagerView");
        assertParent(definitions, "device:deploy:view", "device", "Device Deployment", "/device-deployment", "SystemDeploymentView");

        assertParent(definitions, "sys:user:list", "system", "User Management", "/system/users", "system/UserManagementView");
        assertParent(definitions, "sys:group:list", "system", "Role Management", "/system/groups", "system/GroupManagementView");
        assertParent(definitions, "sys:permission:list", "system", "Permissions", "/system/permissions", "system/PermissionManagementView");
        assertParent(definitions, "sys:log:list", "system", "Audit Logs", "/logs", "LogView");

        assertParent(definitions, "sys:user:add", "sys:user:list", "Add User", null, null);
        assertParent(definitions, "sys:user:edit", "sys:user:list", "Edit User", null, null);
        assertParent(definitions, "sys:user:delete", "sys:user:list", "Delete User", null, null);
        assertParent(definitions, "sys:group:add", "sys:group:list", "Add Role", null, null);
        assertParent(definitions, "sys:group:edit", "sys:group:list", "Edit Role", null, null);
        assertParent(definitions, "sys:group:delete", "sys:group:list", "Delete Role", null, null);
        assertParent(definitions, "sys:permission:add", "sys:permission:list", "Add Permission", null, null);
        assertParent(definitions, "sys:permission:edit", "sys:permission:list", "Edit Permission", null, null);
        assertParent(definitions, "sys:permission:delete", "sys:permission:list", "Delete Permission", null, null);
        assertParent(definitions, "sys:log:delete", "sys:log:list", "Delete Audit Log", null, null);
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
