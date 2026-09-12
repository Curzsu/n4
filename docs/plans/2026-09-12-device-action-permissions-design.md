# 设备模板与设备部署操作权限设计

## 目标

在现有 `System` 权限层级下，为 `Device Template` 和 `Device Deployment` 补齐与页面真实操作一致的下级权限，并让权限同时作用于前端按钮和已有后端写接口。修复权限树将未知图标误显示为锁图标的问题。

## 权限层级

### Device Template

父权限为 `device:template:view`，新增以下操作权限：

| 权限名称 | 权限代码 | 对应页面操作 |
| --- | --- | --- |
| Add Template | `device:template:add` | Add Template |
| Edit Template | `device:template:edit` | Edit Template |
| Delete Template | `device:template:delete` | Delete Template、模板批量删除 |
| Import | `device:template:import` | Import、Preview、下载导入模板、Confirm |
| Export | `device:template:export` | Export |
| Add Point | `device:template:point:add` | Add Point |
| Edit Point | `device:template:point:edit` | Edit Point，以及保存点位告警绑定 |
| Delete Point | `device:template:point:delete` | Delete Point、点位批量删除 |
| Set Faceplate Panel | `device:template:faceplate:view` | Set Faceplate Panel、Diagram |

`Batch Manage` 只是进入批量删除模式，复用 Delete 权限，不新增重复权限。

### Device Deployment

父权限为 `device:deploy:view`，新增以下操作权限：

| 权限名称 | 权限代码 | 对应页面操作 |
| --- | --- | --- |
| Add Group | `device:deploy:group:add` | Add Root Node、Add Child、Add Child Group |
| Edit Group | `device:deploy:group:edit` | Edit Group |
| Delete Group | `device:deploy:group:delete` | Delete Group |
| Deploy Device | `device:deploy:add` | Deploy Device |
| Edit Device | `device:deploy:edit` | Edit Device、移动设备、更新部署点位 |
| Delete Device | `device:deploy:delete` | Delete Device、设备批量删除 |
| Mount Device | `device:deploy:mount` | Mount Device |
| Unmount Device | `device:deploy:unmount` | Unmount |
| Import | `device:deploy:import` | Import、Preview、下载导入模板、Confirm |
| Export | `device:deploy:export` | Export |
| Set Device Alarm | `device:deploy:alarm:edit` | Set Device Alarm |

`Batch Manage` 复用 Delete Device 权限。搜索、刷新、返回、缩放和查看详情属于基础查看能力，不增加操作权限。

## 前后端约束

- 前端使用现有 `v-permission` 指令控制按钮和入口显示。
- 模板、点位、部署设备、分组、挂载、导入导出的后端写接口使用对应权限校验，避免只隐藏按钮却仍可直接调用接口。
- `Set Faceplate Panel` 目前只浏览静态图片，`Set Device Alarm` 目前只显示说明提示，因此暂时仅控制前端入口；未来接入保存接口时沿用同一权限代码。
- 读取接口继续沿用页面级查看权限和现有认证规则，避免影响 Dashboard 对部署树和设备详情的复用。

## 默认角色

- `SUPER_ADMIN`：自动拥有全部新增权限。
- `OPERATOR`：拥有模板和部署的新增、编辑、导入、导出、挂载、卸载及告警设置能力，但不拥有任何删除权限。
- `VIEWER`：不授予新增操作权限，保持只读。

## 图标修复

权限目录已经使用 `Cpu`、`Operation`、`TrendCharts`、`Key`、`Tickets` 等图标，但权限页面没有注册全部图标，导致未知图标统一回退成 `Lock`。页面将注册目录中使用的全部图标，并在遇到未知图标时不再显示锁，防止把图标缺失误解为权限被锁定。

## 验证

- 目录测试验证新增权限的名称、代码、父子关系和默认角色集合。
- 控制器测试或源码契约测试验证写接口使用正确权限。
- 前端测试验证所有真实操作入口绑定相应权限，并验证目录图标均可解析。
- 运行后端测试、前端测试和前端构建。
