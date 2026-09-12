# Permission Catalog Navigation Hierarchy Design

## 背景

Permission 页面当前按权限编码命名空间分组，没有反映真实导航关系。例如 Device Template、Device Deployment 和 Alarm Configuration 被拆到独立业务目录，而真实界面中它们都属于顶部 System 导航；User Management 等权限又被直接挂在 System 下，缺少 Authority Management 这一层。

## 目标

1. Permission 树严格镜像顶部导航、底部菜单、Authority Management 左侧菜单和页面按钮的真实层级与顺序。
2. 保留现有路由权限编码，不因层级变化修改路由守卫或后端鉴权编码。
3. 补齐 Authority Management、Change Password、Logout，以及顶部已有但尚未绑定页面的 Trend、Report 节点。
4. 同时兼容新数据库初始化和已有数据库升级，清理上一版错误产生的 `dashboard`、`device` 目录节点。

## 目录设计

```text
Dashboard                       dashboard:view
Alarms                          alarm
  Active Alarms                 alarm:view
  Alarm History                 alarm:history:view
Event                           alarm:event:view
Trend                           trend
Report                          report
System                          system
  Device Template               device:template:view
  Device Deployment             device:deploy:view
  Alarm Configuration           alarm:config:view
  Authority Management          system:authority
    User Management             sys:user:list
      Add User                  sys:user:add
      Edit User                 sys:user:edit
      Delete User               sys:user:delete
    Role Management             sys:group:list
      Add Role                  sys:group:add
      Edit Role                 sys:group:edit
      Delete Role               sys:group:delete
    Permissions                 sys:permission:list
      Add Permission            sys:permission:add
      Edit Permission           sys:permission:edit
      Delete Permission         sys:permission:delete
    Audit Logs                  sys:log:list
      Delete Logs               sys:log:delete
    Change Password             auth:password:change
    Logout                      auth:logout
```

名称和顺序直接取自 `TopNavbar.vue`、`BottomToolbar.vue` 和 `LeftSidebar.vue`。Trend、Report 当前没有路由，因此只作为目录节点存在；Authority Management 也是承载下级菜单的目录。Change Password 和 Logout 是现有侧边栏操作，没有页面路径和组件。

## 实现方案

新增唯一的后端权限目录定义，集中描述权限编码、名称、类型、父权限、路由、组件和排序。启动时无论是新建表还是检测到已有 RBAC 表，都执行幂等同步：

- 先把旧的 `system:manage` 根节点迁移为 `system`，保留原权限记录及关联关系。
- 按父权限编码解析父节点，更新或插入目录和叶子权限，恢复被逻辑删除的规范权限。
- 将上一版错误生成的 `dashboard`、`device` 目录标记为已删除；对应页面权限会被重新挂到真实导航位置。
- 为已有角色补齐其已拥有权限的祖先节点，使角色树展示和菜单树不会出现断层；不向角色授予无关的叶子权限。
- 将新目录同步到 `schema_rbac.sql` 和 `deploy/schema_rbac.sql`，保证新库与升级后的旧库一致。

前端继续使用现有的递归树表格和角色权限树，不另造一套展示规则；后端返回的 `parent_id` 负责层级关系，页面显示名称直接来自同步后的权限目录。

## 兼容性与边界

- 不重命名现有路由使用的叶子权限编码，例如保留 `alarm:event:view`。
- 新增的 Trend、Report、Authority Management 仅为导航目录；Change Password 和 Logout 仅描述现有侧边栏操作，不改变现有接口鉴权行为。
- 保留现有权限 ID（通过按权限编码更新），新增节点使用数据库自增 ID。
- 同步失败必须记录英文错误日志，不阻止应用启动流程以外的现有行为；测试覆盖目录完整性和父子关系。

## 验证标准

1. 一级顺序为 Dashboard、Alarms、Event、Trend、Report、System。
2. Alarms 下只有 Active Alarms 和 Alarm History；Event 保持一级节点。
3. System 下依次为 Device Template、Device Deployment、Alarm Configuration、Authority Management。
4. Authority Management 下依次为 User Management、Role Management、Permissions、Audit Logs、Change Password、Logout；页面操作权限继续位于对应页面节点下。
5. 不再出现活动状态的 `dashboard`、`device`、`system:manage` 错误目录。
6. 后端目录同步测试、前端权限树展示测试和实际接口树均通过。
