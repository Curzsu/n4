# Dashboard 左侧导航栏专用数据库表设计

- 日期：2026-07-29
- 状态：已确认，待生成实现计划
- 分支：Su

## 1. 背景与目标

为 dashboard 页构建一个专用的左侧导航栏。导航栏固定两级：

- **第一级**：业务系统分类（电气系统、BA系统、消防系统、设备总览、房间/方舱类型、电气系统图、柴油发电机系统/油路系统、自定义）
- **第二级**：子系统/设备分组（如电气系统下的中压/低压/UPS/直流/采集网关）

交互模型：

- 点击一级节点：展开/收起二级 + 右侧以列表形式展示该一级下所有设备
- 点击二级节点：右侧只展示该二级下设备
- 点击右侧设备列表某行：**跳转新页面**到该设备的图形监控页（fuxa 视图 + 下方告警列表，与现有系统图形监控界面一致）
- 第三级（设备图形监控页）不在左侧导航栏直接呈现，是"点进去后右侧看到的内容"

### 为什么需要新建导航表

远程数据库 `device_deployment` 表中所有 47 台设备的 `location`/`department` 字段全为 `NULL`，`device_type` 仅为型号缩写（EM/ZB/DHG/Collector 等），**没有任何字段能表示设备属于哪个业务系统分类**。`A1_MVP_A` / `A1_MVP_A1` 这类路径段也是从 `device_deploy_name` / `n4_address` 拆出来的，并非独立存储。因此必须新建专用导航表承载两级业务分类，并在设备表上加外键表达归属。

## 2. 现状分析

### 2.1 数据库现状

| | 本地 `n4_monitor`（旧） | 远程 `n4_monitor`（新） |
|---|---|---|
| 设备模板 | 无独立表，靠 `product` + `device` 表塞 `TEMPLATE_` 假设备模拟 | `device_template`（16 条） |
| 设备部署 | `device`（92 条，带 sys_path 层级字段） | `device_deployment`（47 条，扁平无层级） |
| 点位模板 | 无 | `point_template`（556 条） |
| 点位部署 | `monitor_point`（1826 条） | `point_deployment`（2476 条） |
| 产品/位置/字典 | `product`/`location`/`dict_value` | 全部废弃 |
| FUXA 绑定 | `fuxa_device_binding`（0 条） | `fuxa_device_binding`（0 条） |
| 告警规则 | `alarm_rule_boolean`(旧) + `numeric`/`enum`(新) | `alarm_rule_numeric`(42) + `alarm_rule_enum`(590)，无 boolean |

本地仅有旧表，远程是新表结构。远程所有设备的 `location`/`department` 均为 NULL。

### 2.2 后端代码现状

后端代码 100% 基于旧表（device/product/monitor_point），全文检索 `device_template`/`device_deployment`/`tmpl_id`/`deploy_id` 零命中。现有 `DeviceController`/`DeviceService`/`DeviceMapper`/`ProductMapper` 等全部操作旧表。本地直接连远程新库会全部报错。

### 2.3 前端现状

- 路由已有 `/device-template`、`/device-deployment`（对应 `ProductManagerView.vue`、`DeviceDeploymentView.vue`），但调用的还是旧 `deviceApi`/`productApi`。
- `LeftSidebar.vue` 用 `el-tree` 渲染 `store.deviceTree`（后端 `device.sys_path` 多级树）。
- 图形监控页机制已存在：`FuxaMonitorPanel.vue` + `GET /api/fuxa/devices/{deviceId}` 查 fuxa 绑定渲染 iframe + 告警列表。

## 3. 方案选择

采用**方案 A：导航表 + 全新独立 dashboard 接口**。

- 新建导航表 + 新建独立的 `DashboardNavController/Service`，只服务 dashboard 页
- 设备列表直接查 `device_deployment`（通过 nav_node_id 外键）
- 图形监控页跳转复用现有 fuxa 绑定机制（新建 dashboard 专用查询接口，不改旧接口）
- **不动现有旧表代码**（DeviceMapper/ProductMapper 等保持原样），零回归风险

不选方案 B（全面迁移后端到新表）：工作量过大，dashboard 被拖在巨大重构后面。
不选方案 C（前端 mock 优先）：不符合"用远程真实设备数据"的要求。

## 4. 数据库设计

### 4.1 新建导航表 `nav_node`（两级自关联）

```sql
CREATE TABLE `nav_node` (
  `node_id`      bigint       NOT NULL AUTO_INCREMENT COMMENT '导航节点ID',
  `parent_id`    bigint       DEFAULT NULL             COMMENT '父节点ID；一级节点为NULL，二级节点指向所属一级',
  `node_name`    varchar(100) NOT NULL                  COMMENT '节点显示名（如「电气系统」「中压系统」）',
  `node_level`   tinyint      NOT NULL                  COMMENT '层级：1=一级分类，2=二级分类',
  `node_type`    varchar(30)  NOT NULL DEFAULT 'group'  COMMENT '节点类型：group=设备分组（本期唯一类型，预留扩展）',
  `icon`         varchar(50)  DEFAULT NULL              COMMENT '前端图标名（如 Monitor/Cpu），一级节点用',
  `sort_order`   int          NOT NULL DEFAULT 0        COMMENT '同级排序序号',
  `is_deleted`   tinyint(1)   NOT NULL DEFAULT 0        COMMENT '逻辑删除',
  `create_time`  datetime     DEFAULT CURRENT_TIMESTAMP,
  `update_time`  datetime     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`node_id`),
  KEY `idx_parent` (`parent_id`),
  KEY `idx_level`  (`node_level`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Dashboard左侧导航节点表';
```

### 4.2 `device_deployment` 表加外键字段

```sql
ALTER TABLE `device_deployment`
  ADD COLUMN `nav_node_id` bigint DEFAULT NULL COMMENT '所属导航二级节点ID（外键nav_node.node_id）' AFTER `device_tmpl_id`,
  ADD KEY `idx_nav_node_id` (`nav_node_id`);
```

### 4.3 预置两级分类 + 设备挂载方案

一级节点（8 个，按指定顺序）+ 二级节点。远程 47 台真实设备挂到对应二级下，无真设备的二级用 mock 占位设备。

| # | 一级 | 二级 | 真实设备（device_type:数量） | mock占位 |
|---|---|---|---|---|
| 1 | 房间/方舱类型 | DataHall Room 25 | 无 | 是 |
| 1 | 房间/方舱类型 | Public Area GF | 无 | 是 |
| 1 | 房间/方舱类型 | Public Area RF | 无 | 是 |
| 1 | 房间/方舱类型 | A1 PTU-A | 无 | 是 |
| 1 | 房间/方舱类型 | A1 PTU-B | 无 | 是 |
| 2 | 电气系统图 | 电气系统图总览 | 无 | 是 |
| 2 | 电气系统图 | PTU单独系统图 | 无 | 是 |
| 3 | 电气系统 | 中压系统 | DHG(2) | 否 |
| 3 | 电气系统 | 低压系统 | EM(17)+ZB(15) | 否 |
| 3 | 电气系统 | UPS系统 | UDB(1)+EDB(1) | 否 |
| 3 | 电气系统 | 直流系统 | DCP(1) | 否 |
| 3 | 电气系统 | 采集网关 | Collector(1) | 否 |
| 4 | BA系统 | 风冷系统 | IRC1(1)+IRC2(1) | 否 |
| 4 | BA系统 | 液冷系统 | 无 | 是 |
| 5 | 设备总览 | 温湿度 | TH1+TH2+TH3(3) | 否 |
| 5 | 设备总览 | 空调系统 | DEHU(1) | 否 |
| 6 | 柴油发电机系统/油路系统 | 柴油发电机系统 | 无 | 是 |
| 6 | 柴油发电机系统/油路系统 | 油路系统 | 无 | 是 |
| 7 | 消防系统 | 气体灭火/极早期 | GAS1(1)+GAS2(1) | 否 |
| 7 | 消防系统 | 水消防/水压监测 | LD(1) | 否 |
| 8 | 自定义 | 自定义分组/模板 | 无 | 是 |

**DEHU 决策**：挂「设备总览 -> 空调系统」。除湿器属环境调节设备，与空调同类，让该节点有真实设备。BA系统下严格只有风冷+液冷。

**设备挂载统计**：47 台真实设备全部挂载到 10 个有真设备的二级节点；其余 11 个二级节点用 mock 占位设备（每节点 1-2 台虚拟设备，status=离线，设备名带 `MOCK_` 前缀）。二级节点总数 21 个（5+2+5+2+2+2+2+1）。

**设备类型 -> 导航节点映射规则**（用于初始化 UPDATE 语句）：

| device_type | 二级节点名 |
|---|---|
| DHG | 中压系统 |
| EM, ZB | 低压系统 |
| UDB, EDB | UPS系统 |
| DCP | 直流系统 |
| Collector | 采集网关 |
| IRC1, IRC2 | 风冷系统 |
| DEHU | 空调系统（设备总览下） |
| TH1, TH2, TH3 | 温湿度 |
| GAS1, GAS2 | 气体灭火/极早期 |
| LD | 水消防/水压监测 |

### 4.4 数据库目标

所有新表和数据建在**本地** `n4_monitor` 库（localhost）。远程数据库（47.121.30.136）只读参考，绝不改动。

前置条件：本地目前还没有 `device_deployment`/`device_template`/`point_deployment`/`point_template` 四张新表（本地只有旧表）。需先从远程导出这四张表的结构+数据导入本地，dashboard 才有设备数据可查。此导入属本期工作一部分。

## 5. 后端设计

采用方案 A，新建独立 dashboard 接口，不碰旧表代码。

### 5.1 新增文件

```
backend/src/main/java/com/n4monitor/
├── entity/NavNode.java
├── mapper/NavNodeMapper.java
├── service/DashboardNavService.java
├── controller/DashboardNavController.java
├── dto/NavTreeNodeDto.java
└── dto/DeviceDeployListDto.java
backend/src/main/resources/mapper/NavNodeMapper.xml
```

### 5.2 接口定义

| 接口 | 路径 | 作用 |
|---|---|---|
| 获取导航树 | `GET /api/dashboard/nav/tree` | 返回两级树：一级节点 -> children(二级节点)。二级节点带 `deviceCount`（该分组下未删除设备数） |
| 查节点下设备列表 | `GET /api/dashboard/nav/nodes/{nodeId}/devices` | 返回该二级节点下所有设备（`device_deployment` JOIN `device_template`）。返回字段：deviceDeployId, deviceDeployName, deviceAlias, deviceType, status, manufacturer, isMock |
| 设备图形页 fuxa 绑定 | `GET /api/dashboard/nav/devices/{deviceDeployId}/fuxa` | 查 `fuxa_device_binding` 表返回 fuxa 绑定信息，供图形监控页渲染。独立于旧 `GET /api/fuxa/devices/{deviceId}` 接口 |

### 5.3 导航树返回结构示例

```json
[
  {
    "nodeId": 3,
    "nodeName": "电气系统",
    "nodeLevel": 1,
    "icon": "OfficeBuilding",
    "sortOrder": 3,
    "children": [
      {"nodeId": 4, "nodeName": "中压系统", "nodeLevel": 2, "deviceCount": 2, "children": []},
      {"nodeId": 5, "nodeName": "低压系统", "nodeLevel": 2, "deviceCount": 32, "children": []},
      {"nodeId": 6, "nodeName": "UPS系统", "nodeLevel": 2, "deviceCount": 2, "children": []}
    ]
  }
]
```

### 5.4 点击一级节点聚合逻辑

点击一级节点时右侧展示其下所有设备，由**前端聚合**：前端遍历该一级节点的所有二级 children，逐个调用 `GET /nodes/{nodeId}/devices` 拼接列表。后端接口只负责查单节点，保持简单。

## 6. 前端设计

### 6.1 路由与页面

```
路由: /dashboard-nav  ->  views/DashboardNavView.vue
设备图形页路由: /dashboard-nav/device/:deviceDeployId  ->  DeviceMonitorView.vue
```

页面布局沿用现有 dashboard 三栏式骨架（LeftSidebar + 右侧主区域）。

### 6.2 LeftSidebar 改造（两级树）

- 数据源从 `store.deviceTree`（旧多级树）改为新 `navTree`（两级），通过 `GET /api/dashboard/nav/tree` 获取
- 一级节点 `nodeLevel=1` 可展开/收起，二级节点 `nodeLevel=2` 是叶子（点击触发右侧设备列表）
- 点击行为：
  - 点一级节点 -> 展开/收起二级 + 右侧显示该一级下所有设备（前端聚合所有 children）
  - 点二级节点 -> 右侧只显示该二级下设备
- 二级节点带 `deviceCount` 角标（如 `中压系统 (2)`）
- 保留现有搜索框、折叠功能

### 6.3 右侧设备列表

新增 `DeviceListView.vue` 组件：

- 表格列：设备名 / 别名 / 设备类型 / 厂商 / 状态(带状态点) / 操作
- 操作列：「查看监控」按钮 -> 跳转 `/dashboard-nav/device/:deviceDeployId`
- 状态点复用现有 LeftSidebar 的 status-dot 样式
- mock 占位设备：状态标"离线"，设备名带 `MOCK_` 前缀或备注列标"示例数据"，便于区分

### 6.4 设备图形监控页（第三级，跳转新页面）

`DeviceMonitorView.vue`：

- 调 `GET /api/dashboard/nav/devices/{deviceDeployId}/fuxa` 拿 fuxa 绑定
- 渲染 `FuxaMonitorPanel.vue`（fuxa iframe 视图）+ 下方告警列表
- 与现有系统图形监控界面一致

### 6.5 新增前端文件

```
frontend/src/
├── api/dashboardNav.ts                    # 新 API 模块
├── views/DashboardNavView.vue             # dashboard 主页面
├── views/DeviceMonitorView.vue            # 设备图形监控页
├── components/DeviceListView.vue          # 设备列表组件
└── (改造) components/LeftSidebar.vue      # 两级导航树
└── (改造) api/types.ts                    # 新增 NavTreeNode/DeviceDeployItem 类型
└── (改造) router/index.ts                 # 新增路由
```

## 7. 实现范围与边界

### 7.1 IN（本期做）

1. 数据库：新建 `nav_node` 表 + `device_deployment` 加 `nav_node_id` 外键 + 预置两级分类数据 + 真实设备挂载 + mock 占位设备
2. 从远程导出 `device_deployment`/`device_template`/`point_deployment`/`point_template` 四张表结构+数据到本地（只读远程）
3. 后端：NavNode 实体 / NavNodeMapper / DashboardNavService / DashboardNavController + DTO + fuxa 绑定查询接口
4. 前端：DashboardNavView + DeviceMonitorView + DeviceListView + LeftSidebar 改造 + api/types/router 更新
5. 数据来源：远程真实设备数据（47 台）+ mock 占位

### 7.2 OUT（本期不做）

1. 不动旧表代码（DeviceMapper/ProductMapper/MonitorPointMapper 等保持原样）
2. 不做导航节点的增删改管理界面（本期数据用 SQL 预置）
3. 不迁移旧 device/product 页面到新表
4. 不做"电气系统图总览1~8""PTU单独系统图"等视图页的实际渲染（只保留为空分组 + mock 占位）
5. 不改远程数据库任何数据（只读远程做参考）

### 7.3 实现顺序（粗略）

1. 从远程导出新表结构+数据到本地（只读远程）
2. 本地建 `nav_node` 表 + 预置分类数据
3. 本地 `device_deployment` 加 `nav_node_id` + 更新真实设备挂载 + 插 mock 占位设备
4. 后端实体/Mapper/Service/Controller
5. 前端页面/组件/路由/API
6. 联调验证

## 8. 验证标准

1. 左侧导航栏显示固定两级树，含 8 个一级节点和 21 个二级节点
2. 有真设备的二级节点显示真实设备数，无真设备的显示 mock 占位设备
3. 点击一级节点右侧显示其下所有设备，点击二级节点显示该二级设备
4. 点击设备「查看监控」跳转新页面，显示 fuxa 图形视图 + 告警列表
5. 远程数据库未被修改
6. 现有旧页面（/device-deployment 等）功能不受影响
