package com.n4monitor.config;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
        assertButton(definitions, "device:template:add", "device:template:view", "Add Template", 1);
        assertButton(definitions, "device:template:edit", "device:template:view", "Edit Template", 2);
        assertButton(definitions, "device:template:delete", "device:template:view", "Delete Template", 3);
        assertButton(definitions, "device:template:import", "device:template:view", "Import", 4);
        assertButton(definitions, "device:template:export", "device:template:view", "Export", 5);
        assertButton(definitions, "device:template:point:add", "device:template:view", "Add Point", 6);
        assertButton(definitions, "device:template:point:edit", "device:template:view", "Edit Point", 7);
        assertButton(definitions, "device:template:point:delete", "device:template:view", "Delete Point", 8);
        assertButton(definitions, "device:template:faceplate:view", "device:template:view", "Set Faceplate Panel", 9);
        assertParent(definitions, "device:deploy:view", "system", "Device Deployment", "/device-deployment", "SystemDeploymentView");
        assertButton(definitions, "device:deploy:group:add", "device:deploy:view", "Add Group", 1);
        assertButton(definitions, "device:deploy:group:edit", "device:deploy:view", "Edit Group", 2);
        assertButton(definitions, "device:deploy:group:delete", "device:deploy:view", "Delete Group", 3);
        assertButton(definitions, "device:deploy:add", "device:deploy:view", "Deploy Device", 4);
        assertButton(definitions, "device:deploy:edit", "device:deploy:view", "Edit Device", 5);
        assertButton(definitions, "device:deploy:delete", "device:deploy:view", "Delete Device", 6);
        assertButton(definitions, "device:deploy:mount", "device:deploy:view", "Mount Device", 7);
        assertButton(definitions, "device:deploy:unmount", "device:deploy:view", "Unmount Device", 8);
        assertButton(definitions, "device:deploy:import", "device:deploy:view", "Import", 9);
        assertButton(definitions, "device:deploy:export", "device:deploy:view", "Export", 10);
        assertButton(definitions, "device:deploy:alarm:edit", "device:deploy:view", "Set Device Alarm", 11);
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

    @Test
    void operatorCanMaintainDevicesWithoutDeletingThem() {
        Set<String> operatorPermissions = RbacPermissionCatalog.operatorPermissionCodes();

        assertTrue(operatorPermissions.containsAll(Set.of(
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
                "device:deploy:add",
                "device:deploy:edit",
                "device:deploy:mount",
                "device:deploy:unmount",
                "device:deploy:import",
                "device:deploy:export",
                "device:deploy:alarm:edit")));
        assertFalse(operatorPermissions.contains("device:template:delete"));
        assertFalse(operatorPermissions.contains("device:template:point:delete"));
        assertFalse(operatorPermissions.contains("device:deploy:group:delete"));
        assertFalse(operatorPermissions.contains("device:deploy:delete"));
    }

    private static void assertButton(
            Map<String, RbacPermissionCatalog.PermissionDefinition> definitions,
            String code,
            String parentCode,
            String name,
            int sortOrder) {
        assertParent(definitions, code, parentCode, name, null, null);
        assertEquals(RbacPermissionCatalog.BUTTON, definitions.get(code).type(), code);
        assertEquals(sortOrder, definitions.get(code).sortOrder(), code);
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
