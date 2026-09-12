# Permission Catalog Navigation Hierarchy Correction Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让 Permission 页面严格镜像顶部导航、System 底部菜单、Authority Management 左侧菜单及页面操作的真实层级和顺序。

**Architecture:** 继续以 `RbacPermissionCatalog` 作为权限目录唯一来源，保留所有已被路由和后端鉴权使用的叶子权限编码，仅调整父子关系和显示名称。启动同步负责幂等迁移已有数据库，并明确停用上一版错误新增的 `dashboard`、`device` 目录；两份初始化 SQL 与目录定义保持一致。

**Tech Stack:** Java 21、Spring Boot `JdbcTemplate`、MySQL、JUnit 5、Vue 3、Element Plus、Node test runner。

---

### Task 1: 用失败测试锁定真实导航层级

**Files:**
- Modify: `monitor-backend/src/test/java/com/n4monitor/config/RbacPermissionCatalogTest.java`
- Test: `monitor-backend/src/test/java/com/n4monitor/config/RbacPermissionCatalogTest.java`

- [ ] **Step 1: 将目录契约改为真实导航结构**

测试必须断言六个一级节点依次为 Dashboard、Alarms、Event、Trend、Report、System，并断言 System 和 Authority Management 的完整子树：

```java
assertRoots(definitions,
        "dashboard:view", "alarm", "alarm:event:view", "trend", "report", "system");

assertParent(definitions, "alarm:view", "alarm", "Active Alarms", "/alarm", "AlarmView");
assertParent(definitions, "alarm:history:view", "alarm", "Alarm History", "/alarm/history", "AlarmHistoryView");

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
```

同时断言 `dashboard`、`device`、`system:manage` 不再属于规范目录。

- [ ] **Step 2: 运行测试并确认因旧层级失败**

```powershell
mvn.cmd -pl monitor-backend -Dtest=RbacPermissionCatalogTest -DfailIfNoTests=false test
```

Expected: `FAIL`，失败信息应显示根节点、父节点或名称仍是旧结构。

- [ ] **Step 3: 提交失败测试**

```powershell
git add -- monitor-backend/src/test/java/com/n4monitor/config/RbacPermissionCatalogTest.java
git commit -m "test(permission): 锁定真实导航权限层级"
```

### Task 2: 调整规范权限目录并迁移旧目录

**Files:**
- Modify: `monitor-backend/src/main/java/com/n4monitor/config/RbacPermissionCatalog.java`
- Modify: `monitor-backend/src/main/java/com/n4monitor/config/RbacSchemaInitializer.java`
- Test: `monitor-backend/src/test/java/com/n4monitor/config/RbacPermissionCatalogTest.java`

- [ ] **Step 1: 按导航顺序重写目录定义**

```java
menu("dashboard:view", "Dashboard", null, "/", "DashboardView", "Monitor", 1),
menu("alarm", "Alarms", null, null, null, "Bell", 2),
menu("alarm:view", "Active Alarms", "alarm", "/alarm", "AlarmView", "Bell", 1),
menu("alarm:history:view", "Alarm History", "alarm", "/alarm/history", "AlarmHistoryView", "Clock", 2),
menu("alarm:event:view", "Event", null, "/event", "AlarmEventView", "List", 3),
menu("trend", "Trend", null, null, null, "TrendCharts", 4),
menu("report", "Report", null, null, null, "Document", 5),
menu("system", "System", null, null, null, "Setting", 6),
menu("device:template:view", "Device Template", "system", "/device-template", "device-deploy/ProductManagerView", "Coin", 1),
menu("device:deploy:view", "Device Deployment", "system", "/device-deployment", "SystemDeploymentView", "SetUp", 2),
menu("alarm:config:view", "Alarm Configuration", "system", "/alarm/config", "AlarmConfigView", "Tools", 3),
menu("system:authority", "Authority Management", "system", null, null, "Lock", 4),
```

Authority Management 下依次放置四个现有管理页面和两个现有侧边栏操作。四个页面的按钮权限继续挂在各自页面节点下；Change Password 和 Logout 使用 `button(...)`，不绑定路径和组件。

- [ ] **Step 2: 停用上一版错误目录**

在目录 upsert 完成后调用：

```java
private void retireObsoleteCatalogNodes() {
    jdbcTemplate.update("""
            UPDATE sys_permission
            SET status = 0, is_deleted = 1
            WHERE perm_code IN ('dashboard', 'device')
            """);
}
```

只处理上一版新增的两个明确编码，不批量删除用户自建权限。既有叶子权限会先被重新挂到正确父节点，角色已有叶子授权保持不变；后续祖先补齐会为角色补上新的 System 或 Authority Management 父节点。

- [ ] **Step 3: 运行目录契约测试并确认通过**

```powershell
mvn.cmd -pl monitor-backend -Dtest=RbacPermissionCatalogTest -DfailIfNoTests=false test
```

Expected: `PASS`，1 test、0 failures、0 errors。

- [ ] **Step 4: 提交目录和迁移实现**

```powershell
git add -- monitor-backend/src/main/java/com/n4monitor/config/RbacPermissionCatalog.java monitor-backend/src/main/java/com/n4monitor/config/RbacSchemaInitializer.java
git commit -m "fix(permission): 按真实导航重排权限目录"
```

### Task 3: 对齐新数据库初始化 SQL

**Files:**
- Modify: `monitor-backend/src/main/resources/schema_rbac.sql`
- Modify: `deploy/schema_rbac.sql`

- [ ] **Step 1: 将两份权限种子改为相同层级**

保留既有页面和按钮权限 ID；使用 ID 25 作为 Alarms，新增 Trend、Report、Authority Management、Change Password 和 Logout 节点。Dashboard 和 Event 页面权限直接作为根节点，Device Template、Device Deployment、Alarm Configuration 的父节点改为 System，四个管理页面的父节点改为 Authority Management。

- [ ] **Step 2: 更新默认角色映射**

Operator 使用以下代码：

```sql
'dashboard:view',
'alarm', 'alarm:view', 'alarm:history:view', 'alarm:event:view',
'system', 'device:template:view', 'device:deploy:view', 'alarm:config:view'
```

Viewer 使用以下代码：

```sql
'dashboard:view',
'alarm', 'alarm:view', 'alarm:history:view', 'alarm:event:view'
```

Super Administrator 继续获得所有活动权限。

- [ ] **Step 3: 确认两份 SQL 完全一致**

```powershell
if ((Get-FileHash -Algorithm SHA256 -LiteralPath 'monitor-backend/src/main/resources/schema_rbac.sql').Hash -ne (Get-FileHash -Algorithm SHA256 -LiteralPath 'deploy/schema_rbac.sql').Hash) { throw 'RBAC schema files differ' }
```

Expected: exit code `0`，无错误输出。

- [ ] **Step 4: 提交初始化数据**

```powershell
git add -- monitor-backend/src/main/resources/schema_rbac.sql deploy/schema_rbac.sql
git commit -m "fix(permission): 对齐导航权限初始化数据"
```

### Task 4: 全量验证和实际权限树检查

**Files:**
- Test: `frontend/tests/PermissionManagement.order.test.mjs`
- Verify: `frontend/src/views/system/PermissionManagementView.vue`
- Verify: `monitor-backend/src/main/java/com/n4monitor/service/SysPermissionService.java`

- [ ] **Step 1: 运行前端权限树测试**

```powershell
Push-Location frontend
npm.cmd test -- tests/PermissionManagement.order.test.mjs
Pop-Location
```

Expected: 1 pass、0 fail。

- [ ] **Step 2: 运行后台测试**

```powershell
mvn.cmd -pl monitor-backend -am test
```

Expected: 所有测试 0 failures、0 errors。

- [ ] **Step 3: 重启项目并检查实际接口**

```powershell
cmd.exe /c ".\start-n4-monitor.bat"
```

登录后请求 `/api/permissions/tree`，确认根节点依次为 Dashboard、Alarms、Event、Trend、Report、System；System 和 Authority Management 子节点名称及顺序与设计一致，且活动树中没有 `dashboard`、`device`、`system:manage`。

- [ ] **Step 4: 确认工作区并提交所有任务相关改动**

```powershell
git status --short
```

Expected: 无未提交的任务相关文件；不推送远程。
