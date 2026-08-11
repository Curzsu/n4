## 目标
把本地左侧导航栏重建为**三级方舱树**（方舱组→方舱→采集器），挂载远程 443 台真实设备；废弃并删除旧中文分组、旧 A1_PTU/MVP 树、TestSystem 测试节点。只改数据库，前端首页交互已支持（自动聚合），前端/后端代码不动。

## 三级树结构（由 system_name = `方舱/采集器` 解析）
- **L1 方舱组**（3）：`A1_MVP` / `A1_PTU` / `A1_SLP`（= 方舱名去掉最后一段）
- **L2 方舱**（7）：挂到对应组
  - A1_MVP_A1、A1_MVP_B1 → A1_MVP
  - A1_PTU_A1、A1_PTU_BS1 → A1_PTU
  - A1_SLP_A2C2、A1_SLP_BS1A、A1_SLP_BS1BC → A1_SLP
- **L3 采集器**（13，叶子挂设备）：= system_name 第二段（采集器名，全局唯一）
  - MVP_A1、MVP_B1、PTU_A1、PTUBAM_A1、PTU_BS1、SLP_A2、SLP_C2、SLPBAM_A2C2、SLP_BS1A、SLPBAM_BS1A、SLP_BS1B、SLP_BS1C、SLPBAM_BS1BC

## 执行步骤

### 阶段1：备份
- 用 mysqldump 备份当前 `nav_node` + `device_deployment`（生成回滚 SQL，可恢复到本次改动前）。

### 阶段2：清空重建 nav_node
- 清空 `nav_node` 全部节点（删除旧中文分组、旧树、测试节点）。
- 插入三级树（node_type='group'，L1 设 icon、max_level=3，L2/L3 按序 sort_order）：
  - 3 个 L1 组 → 7 个 L2 方舱（parent 指向组）→ 13 个 L3 采集器（parent 指向方舱）。

### 阶段3：挂载设备（只挂真实数据，不按 device_type）
- 按 `system_name` 第二段（采集器名）匹配 L3 采集器，把 443 台设备 `nav_node_id` 挂到对应采集器叶子：
  ```sql
  UPDATE device_deployment dd
  JOIN nav_node nn ON nn.node_name = SUBSTRING_INDEX(dd.system_name,'/',-1) AND nn.node_level=3
  SET dd.nav_node_id = nn.node_id
  WHERE dd.is_deleted=0;
  ```

### 阶段4：验证
- nav_node 树结构：3 组 / 7 方舱 / 13 采集器。
- 设备全部挂载（总 443，未挂载 0）。
- 首页 `/api/dashboard/nav/tree` 返回三级树；抽查 `nodes/{采集器id}/devices` 返回本采集器设备。
- 前端交互（点击二级显示合并设备、点击三级显示本组设备）由现有 `collectLeafGroupIds` 实现，已验证无需改动。

## 交互说明
- 点击**三级采集器** → 显示该采集器下设备（前端叶子逻辑）。
- 点击**二级方舱** → 前端 `collectLeafGroupIds` 收集其下所有采集器叶子并合并设备列表（已支持，无需改）。

## 风险与回滚
- 只动本地 `nav_node` 和 `device_deployment.nav_node_id`，可回滚（阶段1备份）。
- 不改远程、不改前端/后端代码、不删除 device_deployment 数据。
- 前端首页交互已满足，部署管理页不动。