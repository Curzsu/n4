# System Account Navigation 设计说明

## 目标

移除主页右上角的管理员身份展示和账户下拉入口，将账户相关操作统一放到 `System` 二级菜单中。现有告警数量、搜索入口和顶层导航保持不变。

## 方案

顶部导航栏 `TopNavbar` 不再渲染用户名、用户图标、下拉箭头或账户下拉菜单，也不再承载修改密码、审计日志和退出登录的处理逻辑。

现有底部二级菜单 `BottomToolbar` 在进入 `System` 路由时增加两个操作：

- `Change Password`：打开已有的 `ChangePasswordDialog`，继续调用现有密码修改 API。
- `Logout`：沿用现有确认弹窗、登出 API、提示消息和登录页跳转行为。

`Audit Logs` 已经位于 `System` 菜单中，保持不变。为了满足“主页右上角不显示管理员信息”，不新增管理员名称展示；账户操作只在用户进入 `System` 后可见。

## 影响范围

- `frontend/src/components/TopNavbar.vue`：删除账户展示、下拉菜单和相关状态/处理函数。
- `frontend/src/components/BottomToolbar.vue`：增加密码修改与退出操作，挂载已有密码对话框并复用登出逻辑。
- `frontend/tests/TopNavbar.system-account.test.mjs`：增加静态回归测试，约束顶部不再有账户入口，并确认 System 菜单包含账户操作。

不修改后端接口、路由权限、密码校验逻辑和审计日志页面。

## 验证标准

1. `TopNavbar` 源码不再包含账户下拉菜单、用户名展示和 `ChangePasswordDialog`。
2. `BottomToolbar` 的 System 菜单包含 `Change Password` 和 `Logout`，并继续包含 `Audit Logs`。
3. 密码修改仍通过已有对话框和 `authApi.changePassword` 完成。
4. 退出登录仍需二次确认，成功后清理登录状态并跳转到 `/login`。
5. 前端回归测试和生产构建通过。
