# Device Deployment 导入预览优化设计

## 目标

完成 Device Deployment 导入流程的三项调整：

1. 新设备携带的点位统一按 `new` 统计，即使数据库中存在相同 `mqttPath` 的点位，也不计入 `reused`。
2. 将导入确认按钮文案从 `Confirm Import` 改为 `Confirm`。
3. 将 Preview 结果改为有层次的展示，让导入概览、资源统计、设备明细和校验问题能够被快速区分。

## 方案

### 导入统计

后端继续使用现有设备业务键和点位 `mqttPath` 进行持久化复用。新增点位统计以所属 `DevicePlan` 的设备动作作为优先条件：设备动作是 `CREATE` 时，该设备下的每个有效导入点位都计入 `pointDeploymentCreated`；仅当设备动作不是 `CREATE` 时，才按现有点位是否存在区分 `created` 和 `reused`。这样只调整统计口径，不引入重复点位或改变已有导入写入路径。

### Preview 展示

保留现有 API 响应字段，在前端将平铺的 `deploymentPreviews` 转换为按 `systemName` 分组的树形数据：系统路径作为父行，设备作为子行，默认展开。概览信息、统计信息、设备树和问题列表使用独立的视觉分区；统计数字继续使用 `new` / `reused` 语义，设备子行继续展示模板、点位数和导入动作。

### 文案和兼容性

只修改按钮显示文本，不修改确认请求、权限、接口参数或提交逻辑。已有文件选择、Preview、Confirm、导航刷新和校验流程保持不变。

## 测试

- 后端增加回归测试：当设备被判定为新设备且导入点位的 `mqttPath` 已存在时，点位统计为 created，设备预览动作仍为 `CREATE`。
- 前端增加源码契约测试：确认按钮使用 `Confirm`，不再出现 `Confirm Import`；Preview 包含树形表格所需的分组数据和树形配置。
- 执行相关前端 Node 测试、后端 `DeploymentTransferServiceTest` 和前端构建。

## 范围

本次只涉及 Device Deployment 导入统计、确认按钮文案和 Preview UI，不修改数据库结构、导入接口契约或其他部署页面。
