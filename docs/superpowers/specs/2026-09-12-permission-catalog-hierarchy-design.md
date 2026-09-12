# Permission Catalog Hierarchy Design

## 背景

Permission 页面当前把多个同一业务域的菜单作为平级节点，`event` 目录不够完整，系统根节点还使用了 `System Management` 和 `system:manage`。这会让页面展示、角色勾选和实际路由命名不一致，也会让已有数据库在重启后继续保留旧结构。

## 目标

1. 使用现有系统中的权限编码和页面名称，建立可读的多级权限树。
2. 将 `alarm`、`device`、`system` 作为现有命名空间对应的一级目录，其中 `system` 必须是系统权限树的一级名称和编码。
3. 补齐当前后端实际校验的 `sys:log:delete` 权限，并确保 `event` 位于 `alarm` 目录下。
4. 同时兼容新数据库初始化和已有数据库升级，升级过程可重复执行且不改变叶子权限编码。

## 目录设计

```text
dashboard
  Dashboard                  dashboard:view
alarm
  Alarms                     alarm:view
  Alarm Configuration        alarm:config:view
  Alarm History              alarm:history:view
  Event                      alarm:event:view
device
  Device Template            device:template:view
  Device Deployment          device:deploy:view
system
  User Management            sys:user:list
    Add User                 sys:user:add
    Edit User                sys:user:edit
    Delete User              sys:user:delete
  Role Management            sys:group:list
    Add Role                 sys:group:add
    Edit Role                sys:group:edit
    Delete Role              sys:group:delete
  Permissions                sys:permission:list
    Add Permission           sys:permission:add
    Edit Permission          sys:permission:edit
    Delete Permission        sys:permission:delete
  Audit Logs                 sys:log:list
    Delete Logs               sys:log:delete
```

一级目录使用现有权限命名空间的小写形式，目录节点不绑定不存在的页面组件；实际可访问页面继续使用现有叶子权限编码。`Alarm Configuration`、`Event`、`Role Management`、`Permissions` 和 `Audit Logs` 使用导航及页面中已经存在的名称。

## 实现方案

新增唯一的后端权限目录定义，集中描述权限编码、名称、类型、父权限、路由、组件和排序。启动时无论是新建表还是检测到已有 RBAC 表，都执行幂等同步：

- 先把旧的 `system:manage` 根节点迁移为 `system`，保留原权限记录及关联关系。
- 按父权限编码解析父节点，更新或插入目录和叶子权限，恢复被逻辑删除的规范权限。
- 为已有角色补齐其已拥有权限的祖先节点，使角色树展示和菜单树不会出现断层；不向角色授予无关的叶子权限。
- 将新目录同步到 `schema_rbac.sql` 和 `deploy/schema_rbac.sql`，保证新库与升级后的旧库一致。

前端继续使用现有的递归树表格和角色权限树，不另造一套展示规则；后端返回的 `parent_id` 负责层级关系，页面显示名称直接来自同步后的权限目录。

## 兼容性与边界

- 不重命名现有路由使用的叶子权限编码，例如保留 `alarm:event:view`。
- 不新增当前代码没有使用的领域 API 权限。
- 保留现有权限 ID（通过按权限编码更新），新增节点使用数据库自增 ID。
- 同步失败必须记录英文错误日志，不阻止应用启动流程以外的现有行为；测试覆盖目录完整性和父子关系。

## 验证标准

1. 权限树的根节点包含 `dashboard`、`alarm`、`device`、`system`，不再出现 `System Management` 或 `system:manage`。
2. `alarm` 下包含四个页面节点，`Event` 的编码仍为 `alarm:event:view`，路径仍为 `/event`。
3. `system` 下的用户、角色、权限、审计日志及其操作权限均有正确父节点。
4. 使用已有角色登录时，角色权限树能展开上述层级；管理员访问 Permission 页面能看到完整目录。
5. 后端目录同步测试通过，前端现有权限树展示和相关测试通过。
