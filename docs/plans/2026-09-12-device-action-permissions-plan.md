# 设备操作权限实施计划

> 目标：在不改变现有导航层级的前提下，补齐 `Device Template` 与 `Device Deployment` 的操作权限，并确保权限目录、页面按钮、后端接口和默认角色一致。

## 任务一：用测试锁定权限目录和默认角色

涉及文件：

- `monitor-backend/src/test/java/com/n4monitor/config/RbacPermissionCatalogTest.java`
- `frontend/tests/PermissionManagement.order.test.mjs`

步骤：

1. 增加两组设备操作权限的父子关系、显示名称、类型和排序断言。
2. 增加 `OPERATOR` 拥有维护权限但没有删除权限的断言。
3. 增加权限页面图标目录完整性断言。
4. 先运行测试并确认因功能尚未实现而失败。

## 任务二：扩展规范权限目录和默认授权

涉及文件：

- `monitor-backend/src/main/java/com/n4monitor/config/RbacPermissionCatalog.java`
- `monitor-backend/src/main/java/com/n4monitor/config/RbacSchemaInitializer.java`
- `deploy/schema_rbac.sql`
- `monitor-backend/src/main/resources/schema_rbac.sql`

步骤：

1. 在两个页面权限下加入操作权限节点。
2. 在目录中维护 `OPERATOR` 的规范默认权限集合。
3. 初始化时为 `SUPER_ADMIN` 补齐全部权限，为 `OPERATOR` 补齐允许的非删除操作，并为已授权子节点补齐父节点。
4. 同步两份初始化 SQL，确保全新数据库与已有数据库结果一致。
5. 运行目录测试并确认通过。

## 任务三：修复权限树图标

涉及文件：

- `frontend/src/views/system/PermissionManagementView.vue`
- `frontend/tests/PermissionManagement.order.test.mjs`

步骤：

1. 注册规范目录中实际使用的全部 Element Plus 图标。
2. 删除未知图标回退为锁图标的行为，未知图标不显示。
3. 运行前端权限页面测试。

## 任务四：接入 Device Template 页面与接口

涉及文件：

- `frontend/src/views/device-deploy/ProductManagerView.vue`
- `monitor-backend/src/main/java/com/n4monitor/template/controller/TemplateController.java`
- 对应测试文件

步骤：

1. 先增加页面权限绑定和控制器权限契约测试，确认失败。
2. 为模板、点位、导入、导出和面板入口绑定对应权限。
3. 为已有后端写接口增加 `@PreAuthorize`，导入模板下载复用 Import 权限。
4. 运行相关测试。

## 任务五：接入 Device Deployment 页面与接口

涉及文件：

- `frontend/src/views/SystemDeploymentView.vue`
- `monitor-backend/src/main/java/com/n4monitor/deployment/controller/DeploymentController.java`
- `monitor-backend/src/main/java/com/n4monitor/dashboard/controller/DashboardNavController.java`
- 对应测试文件

步骤：

1. 先增加页面权限绑定和控制器权限契约测试，确认失败。
2. 为分组、设备、挂载、卸载、导入、导出和设备告警入口绑定对应权限。
3. 为部署设备、分组、挂载、卸载和传输写接口增加 `@PreAuthorize`。
4. 保持 Dashboard 复用的读取接口不变。
5. 运行相关测试。

## 任务六：全量验证和本地提交

步骤：

1. 运行后端完整测试套件。
2. 运行前端完整测试和生产构建。
3. 检查源代码没有新增中文字符，检查只修改本任务文件。
4. 启动或重启前后端，验证权限树接口包含新增层级。
5. 按任务相关文件创建本地提交，不推送远端。
