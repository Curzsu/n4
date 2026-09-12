# Permission Catalog Hierarchy Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rebuild the Permission catalog so existing route names and permission codes appear in a complete hierarchy, including `event`, with `system` as the system root, and synchronize both fresh and existing databases.

**Architecture:** Keep existing leaf permission codes as the compatibility contract and introduce one backend catalog definition as the source of truth for names, parent codes, routes, components, icons, and order. Make `RbacSchemaInitializer` run an idempotent catalog upsert for both new and existing RBAC schemas, then backfill only the ancestor permissions already implied by each role's existing assignments. The existing frontend recursive permission table and role tree remain the presentation layer.

**Tech Stack:** Java 21, Spring Boot `JdbcTemplate`, MySQL schema SQL, JUnit 5, Vue/Element Plus, Node test runner.

---

### Task 1: Lock the canonical permission catalog with a failing test

**Files:**
- Create: `monitor-backend/src/test/java/com/n4monitor/config/RbacPermissionCatalogTest.java`
- Create later: `monitor-backend/src/main/java/com/n4monitor/config/RbacPermissionCatalog.java`

- [ ] **Step 1: Write the failing catalog contract test**

Create `RbacPermissionCatalogTest.java` with a pure unit test that checks all roots, all current route permissions, exact existing labels, parent relationships, and the missing log-delete permission:

```java
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
```

- [ ] **Step 2: Run the focused test and verify it fails for the missing catalog**

Run from `E:\Curzsu\N4-monitor`:

```powershell
mvn -pl monitor-backend -am -Dtest=RbacPermissionCatalogTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected result: `FAIL` during test compilation because `RbacPermissionCatalog` does not exist yet. Do not change the test to make this failure disappear.

- [ ] **Step 3: Commit the red test only**

```powershell
git add -- monitor-backend/src/test/java/com/n4monitor/config/RbacPermissionCatalogTest.java
git commit -m "test(permission): 增加权限目录层级契约测试"
```

### Task 2: Add the canonical catalog and idempotent existing-database synchronization

**Files:**
- Create: `monitor-backend/src/main/java/com/n4monitor/config/RbacPermissionCatalog.java`
- Modify: `monitor-backend/src/main/java/com/n4monitor/config/RbacSchemaInitializer.java`
- Test: `monitor-backend/src/test/java/com/n4monitor/config/RbacPermissionCatalogTest.java`

- [ ] **Step 1: Add the canonical catalog definition**

Create `RbacPermissionCatalog.java` with no database or framework dependency. The catalog must be ordered by tree sort order and keep the existing leaf codes:

```java
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
            button("sys:log:delete", "Delete Logs", "sys:log:list", 1)
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
```

- [ ] **Step 2: Make `RbacSchemaInitializer` synchronize the catalog for every startup**

Keep the current schema-file selection for a brand-new database, but remove the existing skip behavior for an existing database. After the optional schema initialization, call a private `syncPermissionCatalog()` method. Resolve parent IDs by the catalog's parent code, upsert every definition by `perm_code`, restore canonical labels and active status, and then add ancestors only for permissions already assigned to a group:

```java
private void syncPermissionCatalog() {
    migrateLegacySystemRoot();

    Map<String, Long> permissionIds = new HashMap<>();
    for (RbacPermissionCatalog.PermissionDefinition definition : RbacPermissionCatalog.definitions()) {
        Long parentId = definition.parentCode() == null
                ? 0L
                : permissionIds.get(definition.parentCode());
        if (parentId == null) {
            parentId = findPermissionId(definition.parentCode());
        }
        if (parentId == null) {
            throw new IllegalStateException("Missing parent permission: " + definition.parentCode());
        }

        jdbcTemplate.update("""
                INSERT INTO sys_permission
                    (perm_name, perm_code, perm_type, parent_id, path, component, icon, sort_order, status, is_deleted)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, 1, 0)
                ON DUPLICATE KEY UPDATE
                    perm_name = VALUES(perm_name),
                    perm_type = VALUES(perm_type),
                    parent_id = VALUES(parent_id),
                    path = VALUES(path),
                    component = VALUES(component),
                    icon = VALUES(icon),
                    sort_order = VALUES(sort_order),
                    status = 1,
                    is_deleted = 0
                """,
                definition.name(), definition.code(), definition.type(), parentId,
                definition.path(), definition.component(), definition.icon(), definition.sortOrder());

        Long permissionId = findPermissionId(definition.code());
        if (permissionId == null) {
            throw new IllegalStateException("Permission upsert did not create a row: " + definition.code());
        }
        permissionIds.put(definition.code(), permissionId);
    }

    jdbcTemplate.update("""
            INSERT IGNORE INTO sys_group_permission (group_id, perm_id)
            SELECT group_record.group_id, permission_record.perm_id
            FROM sys_group group_record
            CROSS JOIN sys_permission permission_record
            WHERE group_record.group_code = 'SUPER_ADMIN'
              AND group_record.is_deleted = 0
              AND permission_record.is_deleted = 0
            """);

    for (int pass = 0; pass < RbacPermissionCatalog.definitions().size(); pass++) {
        int inserted = jdbcTemplate.update("""
                INSERT IGNORE INTO sys_group_permission (group_id, perm_id)
                SELECT DISTINCT group_permission.group_id, parent.perm_id
                FROM sys_group_permission group_permission
                JOIN sys_permission child ON child.perm_id = group_permission.perm_id
                JOIN sys_permission parent ON parent.perm_id = child.parent_id
                WHERE child.parent_id > 0
                  AND child.is_deleted = 0
                  AND parent.is_deleted = 0
                """);
        if (inserted == 0) {
            break;
        }
    }

    log.info("Canonical RBAC permission catalog synchronized: {} definitions.",
            RbacPermissionCatalog.definitions().size());
}

private Long findPermissionId(String code) {
    List<Long> ids = jdbcTemplate.query(
            "SELECT perm_id FROM sys_permission WHERE perm_code = ? AND is_deleted = 0",
            (resultSet, rowNum) -> resultSet.getLong("perm_id"), code);
    return ids.isEmpty() ? null : ids.get(0);
}

private void migrateLegacySystemRoot() {
    Integer canonicalCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM sys_permission WHERE perm_code = 'system'",
            Integer.class);
    if (canonicalCount != null && canonicalCount == 0) {
        jdbcTemplate.update("""
                UPDATE sys_permission
                SET perm_code = 'system', perm_name = 'system'
                WHERE perm_code = 'system:manage'
                """);
    } else {
        jdbcTemplate.update("""
                UPDATE sys_permission legacy
                JOIN sys_permission canonical ON canonical.perm_code = 'system'
                SET legacy.is_deleted = 1
                WHERE legacy.perm_code = 'system:manage'
                  AND legacy.perm_id <> canonical.perm_id
                """);
    }
}
```

Add the required `HashMap`, `List`, and `Map` imports. Change `run()` so it still initializes a missing schema, logs the existing-table case, and invokes `syncPermissionCatalog()` after either branch. Keep all new application logs in English.

- [ ] **Step 3: Run the focused test and verify the catalog contract passes**

```powershell
mvn -pl monitor-backend -am -Dtest=RbacPermissionCatalogTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected result: `PASS` for `RbacPermissionCatalogTest`.

- [ ] **Step 4: Commit the catalog and initializer implementation**

```powershell
git add -- monitor-backend/src/main/java/com/n4monitor/config/RbacPermissionCatalog.java monitor-backend/src/main/java/com/n4monitor/config/RbacSchemaInitializer.java
git commit -m "feat(permission): 增加统一权限目录和旧库同步"
```

### Task 3: Make fresh schema initialization produce the same hierarchy

**Files:**
- Modify: `monitor-backend/src/main/resources/schema_rbac.sql`
- Modify: `deploy/schema_rbac.sql`

- [ ] **Step 1: Replace the permission seed rows with the canonical hierarchy**

In both SQL files, replace the current permission seed section with the same rows. Preserve existing IDs `1` through `23`, change ID `10` to `system`, add roots `24` (`dashboard`), `25` (`alarm`), and `26` (`device`), and add ID `27` for `sys:log:delete`:

```sql
INSERT INTO `sys_permission` (`perm_id`, `perm_name`, `perm_code`, `perm_type`, `parent_id`, `path`, `component`, `icon`, `sort_order`, `status`) VALUES
(24, 'dashboard', 'dashboard', 1, 0, NULL, NULL, 'Monitor', 1, 1),
(1, 'Dashboard', 'dashboard:view', 1, 24, '/', 'DashboardView', 'Monitor', 1, 1),
(25, 'alarm', 'alarm', 1, 0, NULL, NULL, 'Bell', 2, 1),
(2, 'Alarms', 'alarm:view', 1, 25, '/alarm', 'AlarmView', 'Bell', 1, 1),
(3, 'Alarm Configuration', 'alarm:config:view', 1, 25, '/alarm/config', 'AlarmConfigView', 'Tools', 2, 1),
(4, 'Alarm History', 'alarm:history:view', 1, 25, '/alarm/history', 'AlarmHistoryView', 'Clock', 3, 1),
(5, 'Event', 'alarm:event:view', 1, 25, '/event', 'AlarmEventView', 'List', 4, 1),
(26, 'device', 'device', 1, 0, NULL, NULL, 'SetUp', 3, 1),
(6, 'Device Template', 'device:template:view', 1, 26, '/device-template', 'device-deploy/ProductManagerView', 'Coin', 1, 1),
(7, 'Device Deployment', 'device:deploy:view', 1, 26, '/device-deployment', 'SystemDeploymentView', 'SetUp', 2, 1),
(10, 'system', 'system', 1, 0, NULL, NULL, 'Setting', 4, 1),
(11, 'User Management', 'sys:user:list', 1, 10, '/system/users', 'system/UserManagementView', 'User', 1, 1),
(12, 'Add User', 'sys:user:add', 2, 11, NULL, NULL, NULL, 1, 1),
(13, 'Edit User', 'sys:user:edit', 2, 11, NULL, NULL, NULL, 2, 1),
(14, 'Delete User', 'sys:user:delete', 2, 11, NULL, NULL, NULL, 3, 1),
(15, 'Role Management', 'sys:group:list', 1, 10, '/system/groups', 'system/GroupManagementView', 'UserFilled', 2, 1),
(16, 'Add Role', 'sys:group:add', 2, 15, NULL, NULL, NULL, 1, 1),
(17, 'Edit Role', 'sys:group:edit', 2, 15, NULL, NULL, NULL, 2, 1),
(18, 'Delete Role', 'sys:group:delete', 2, 15, NULL, NULL, NULL, 3, 1),
(19, 'Permissions', 'sys:permission:list', 1, 10, '/system/permissions', 'system/PermissionManagementView', 'Lock', 3, 1),
(20, 'Add Permission', 'sys:permission:add', 2, 19, NULL, NULL, NULL, 1, 1),
(21, 'Edit Permission', 'sys:permission:edit', 2, 19, NULL, NULL, NULL, 2, 1),
(22, 'Delete Permission', 'sys:permission:delete', 2, 19, NULL, NULL, NULL, 3, 1),
(23, 'Audit Logs', 'sys:log:list', 1, 10, '/logs', 'LogView', 'Document', 4, 1),
(27, 'Delete Logs', 'sys:log:delete', 2, 23, NULL, NULL, NULL, 1, 1)
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
```

- [ ] **Step 2: Update fresh-database role mappings by permission code**

Replace the operator and viewer `perm_id IN (...)` filters with code-based filters so mappings remain correct even if auto-increment IDs differ:

```sql
INSERT INTO `sys_group_permission` (`group_id`, `perm_id`)
SELECT 2, `perm_id` FROM `sys_permission`
WHERE `perm_code` IN (
  'dashboard', 'dashboard:view',
  'alarm', 'alarm:view', 'alarm:config:view', 'alarm:history:view', 'alarm:event:view',
  'device', 'device:template:view', 'device:deploy:view'
)
ON DUPLICATE KEY UPDATE `group_id` = VALUES(`group_id`);

INSERT INTO `sys_group_permission` (`group_id`, `perm_id`)
SELECT 3, `perm_id` FROM `sys_permission`
WHERE `perm_code` IN (
  'dashboard', 'dashboard:view',
  'alarm', 'alarm:view', 'alarm:history:view', 'alarm:event:view'
)
ON DUPLICATE KEY UPDATE `group_id` = VALUES(`group_id`);
```

- [ ] **Step 3: Confirm both schema files are byte-equivalent**

```powershell
if ((Get-FileHash -Algorithm SHA256 -LiteralPath 'monitor-backend/src/main/resources/schema_rbac.sql').Hash -ne (Get-FileHash -Algorithm SHA256 -LiteralPath 'deploy/schema_rbac.sql').Hash) { throw 'RBAC schema files differ' }
```

Expected result: no output and exit code `0`.

- [ ] **Step 4: Commit both schema changes**

```powershell
git add -- monitor-backend/src/main/resources/schema_rbac.sql deploy/schema_rbac.sql
git commit -m "fix(permission): 统一初始化权限树和角色映射"
```

### Task 4: Verify the Permission page contract and application behavior

**Files:**
- Test existing: `frontend/tests/PermissionManagement.order.test.mjs`
- Verify existing: `frontend/src/views/system/PermissionManagementView.vue`
- Verify existing: `frontend/src/utils/permissionTree.ts`
- Verify existing: `frontend/src/router/index.ts`

- [ ] **Step 1: Run the frontend permission-tree regression test**

```powershell
Push-Location frontend
npm test -- tests/PermissionManagement.order.test.mjs
Pop-Location
```

Expected result: the recursive ordering, `default-expand-all`, and Permission page tree assertions pass. No frontend source change is needed because the page already renders the backend `children` hierarchy.

- [ ] **Step 2: Build the frontend**

```powershell
Push-Location frontend
npm run build
Pop-Location
```

Expected result: `vue-tsc` and Vite complete successfully.

- [ ] **Step 3: Run the backend test suite**

```powershell
mvn -pl monitor-backend -am test
```

Expected result: all backend tests pass, including `RbacPermissionCatalogTest`.

- [ ] **Step 4: Restart the project and verify the live permission tree**

Run the project launcher from the repository root:

```powershell
.\start-n4-monitor.bat
```

After the services are ready, use the existing admin account to request the permission tree without printing the token:

```powershell
$loginBody = @{ username = 'admin'; password = 'admin123' } | ConvertTo-Json
$loginResponse = Invoke-RestMethod -Method Post -Uri 'http://localhost:8188/api/auth/login' -ContentType 'application/json' -Body $loginBody
$headers = @{ Authorization = "Bearer $($loginResponse.data.token)" }
$tree = Invoke-RestMethod -Method Get -Uri 'http://localhost:8188/api/permissions/tree' -Headers $headers
$tree.data | ConvertTo-Json -Depth 10
```

Expected live data shape: roots named `dashboard`, `alarm`, `device`, `system`; `alarm` has `Alarms`, `Alarm Configuration`, `Alarm History`, `Event`; `system` has `User Management`, `Role Management`, `Permissions`, and `Audit Logs`; no active `system:manage` record remains.

- [ ] **Step 5: Confirm that only the task commits changed the repository**

```powershell
git status --short
```

Expected result: no output. The catalog, initializer, test, and both schema files have already been committed in the preceding task commits; do not create an empty commit or stage unrelated working-tree changes.
