# Dashboard 左侧导航栏专用数据库表设计 - 工作汇报

## 一、任务背景

项目原有 dashboard 首页左侧导航栏基于旧设备表（`device`）的 `sys_path` 字段在内存中拼装多级树，存在两个核心问题：

1. **层级结构耦合在设备表里**：设备表同时承担"设备信息"和"导航层级"两个职责，职责不清。
2. **无法表达业务分类**：远程新数据库 `device_deployment` 表是扁平结构，`location`、`department` 字段全为 NULL，`device_type` 仅为型号缩写（如 EM/ZB/DHG），没有任何字段能表示"这台设备属于电气系统还是消防系统"。

老师要求：构建一个 dashboard 页，左侧导航栏固定两级，数据来自数据库表。第一级是业务系统分类（电气系统、BA系统、消防系统等），第二级是子系统/设备分组（如电气系统下的中压/低压/UPS）。点击节点后右侧以列表形式展示设备，点击设备可跳转到图形监控页面。

## 二、设计思路

### 2.1 核心决策：新建专用导航表

经过调研发现，`A1_MVP_A` / `A1_MVP_A1` 这类路径段在远程数据库中并非独立存储，而是从 `device_deploy_name` 和 `n4_address` 字段中拆分得到的。远程库所有 47 台设备的 `location`/`department` 均为 NULL，没有任何字段能表示业务系统归属。

因此决定**新建一张专用的导航节点表 `nav_node`**，承载两级业务分类，并在设备部署表上加外键字段表达设备归属。导航分类本身是一个独立的业务概念，不应硬塞进设备表。

### 2.2 两级自关联结构

导航表采用 `parent_id` 自关联实现两级：

- **一级节点**（`node_level=1`，`parent_id=NULL`）：业务系统分类，如"电气系统""BA系统""消防系统"
- **二级节点**（`node_level=2`，`parent_id=一级节点ID`）：子系统/设备分组，如"中压系统""低压系统""风冷系统"

选择自关联而非分两张表的原因：结构最简、可扩展（将来加第三级也容易）、查询方便。

### 2.3 设备归属：外键挂载

在 `device_deployment` 表上新增 `nav_node_id` 外键字段，指向所属的二级导航节点。这样设备与导航分类解耦清晰：导航表只管分类，设备表只管设备，两者通过外键关联。

### 2.4 方案选择：独立接口，不动旧代码

后端采用"独立 Dashboard 接口"方案：新建 `DashboardNavController/Service`，设备列表直接查 `device_deployment`（通过 `nav_node_id` 外键），图形监控页跳转复用现有 FUXA 绑定机制。**不改动任何现有旧表代码**（DeviceMapper/ProductMapper 等），零回归风险，dashboard 自成闭环。

## 三、数据库设计

### 3.1 新建导航表 `nav_node`

```sql
CREATE TABLE `nav_node` (
  `node_id`      bigint       NOT NULL AUTO_INCREMENT COMMENT '导航节点ID',
  `parent_id`    bigint       DEFAULT NULL             COMMENT '父节点ID；一级为NULL，二级指向所属一级',
  `node_name`    varchar(100) NOT NULL                  COMMENT '节点显示名',
  `node_level`   tinyint      NOT NULL                  COMMENT '层级：1=一级分类，2=二级分类',
  `node_type`    varchar(30)  NOT NULL DEFAULT 'group'  COMMENT '节点类型：group=设备分组',
  `icon`         varchar(50)  DEFAULT NULL              COMMENT '前端图标名，一级节点用',
  `sort_order`   int          NOT NULL DEFAULT 0        COMMENT '同级排序序号',
  `is_deleted`   tinyint(1)   NOT NULL DEFAULT 0        COMMENT '逻辑删除',
  `create_time`  datetime     DEFAULT CURRENT_TIMESTAMP,
  `update_time`  datetime     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`node_id`),
  KEY `idx_parent` (`parent_id`),
  KEY `idx_level`  (`node_level`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Dashboard左侧导航节点表';
```

### 3.2 设备部署表加外键字段

```sql
ALTER TABLE `device_deployment`
  ADD COLUMN `nav_node_id` bigint DEFAULT NULL COMMENT '所属导航二级节点ID' AFTER `device_tmpl_id`,
  ADD INDEX `idx_nav_node_id` (`nav_node_id`);
```

### 3.3 预置两级分类数据

**8 个一级节点**（按指定顺序）：

| 序号 | 一级分类 | 图标 |
|---|---|---|
| 1 | 房间/方舱类型 | Location |
| 2 | 电气系统图 | Picture |
| 3 | 电气系统 | OfficeBuilding |
| 4 | BA系统 | Fan |
| 5 | 设备总览 | Grid |
| 6 | 柴油发电机系统/油路系统 | Lightning |
| 7 | 消防系统 | Warning |
| 8 | 自定义 | EditPen |

**21 个二级节点**，分布如下：

| 一级 | 二级节点 |
|---|---|
| 房间/方舱类型 | DataHall Room 25、Public Area GF、Public Area RF、A1 PTU-A、A1 PTU-B |
| 电气系统图 | 电气系统图总览、PTU单独系统图 |
| 电气系统 | 中压系统、低压系统、UPS系统、直流系统、采集网关 |
| BA系统 | 风冷系统、液冷系统 |
| 设备总览 | 温湿度、空调系统 |
| 柴油发电机系统/油路系统 | 柴油发电机系统、油路系统 |
| 消防系统 | 气体灭火/极早期、水消防/水压监测 |
| 自定义 | 自定义分组/模板 |

### 3.4 真实设备挂载

从远程数据库导入 47 台真实设备，根据设备类型（`device_type`）映射到对应的二级节点：

| 设备类型 | 数量 | 归属二级节点 | 说明 |
|---|---|---|---|
| EM | 17 | 低压系统 | 电表，位于 MVSG 低压配电柜 |
| ZB | 15 | 低压系统 | 断路器/转换器，位于 MVSG 低压配电柜 |
| DHG | 2 | 中压系统 | |
| UDB | 1 | UPS系统 | 蓄电池 |
| EDB | 1 | UPS系统 | 蓄电池 |
| DCP | 1 | 直流系统 | 直流屏 |
| Collector | 1 | 采集网关 | 采集器 |
| IRC1 | 1 | 风冷系统 | 空调 |
| IRC2 | 1 | 风冷系统 | 空调 |
| DEHU | 1 | 空调系统 | 除湿器 |
| TH1 | 1 | 温湿度 | 温湿度传感器 |
| TH2 | 1 | 温湿度 | 温湿度传感器 |
| TH3 | 1 | 温湿度 | 温湿度传感器 |
| GAS1 | 1 | 气体灭火/极早期 | 氢气探测器 |
| GAS2 | 1 | 气体灭火/极早期 | 氢气探测器 |
| LD | 1 | 水消防/水压监测 | 漏水报警器 |

> 设备命名规范：`A1(楼号)_MVP(方仓类型)_A1(方仓编号)_MVSG(柜体名)_A1(第几排)_01(柜体编号)_EM(设备类型)`，最后一段即设备类型。

### 3.5 Mock 占位设备

对于远程数据库中没有对应真实设备的 11 个二级节点（如液冷系统、柴油发电机系统、油路系统等），各插入 1 台 Mock 占位设备（设备名带 `MOCK_` 前缀，状态设为离线），保证每个分类节点都有内容展示，后续有真实设备时替换即可。

### 3.6 数据库最终状态

本地 `n4_monitor` 库共 14 张表，新旧共存：

| 类别 | 表名 | 来源 |
|---|---|---|
| 旧表（原有，未改动） | device、product、monitor_point、dict_value、location、alarm_rule_boolean | 原有 |
| 远程导入的新表 | device_template、device_deployment、point_template、point_deployment | 从远程库复制 |
| 本次新建 | **nav_node** | 新建导航表 |
| 本次改动 | device_deployment（加 nav_node_id 字段） | 在远程导入基础上加外键 |

远程数据库（47.121.30.136）**全程只读，未做任何改动**。

## 四、后端实现

采用独立接口方案，不改动现有旧表代码。

### 4.1 新增文件

| 文件 | 职责 |
|---|---|
| `entity/NavNode.java` | 导航节点实体 |
| `mapper/NavNodeMapper.java` + `NavNodeMapper.xml` | 导航树查询、设备数统计、设备列表查询、按ID查设备 |
| `dto/NavTreeNodeDto.java` | 导航树节点（含 children + deviceCount） |
| `dto/DeviceDeployListDto.java` | 设备列表项 |
| `service/DashboardNavService.java` | 两级树构建 + 设备列表查询 |
| `controller/DashboardNavController.java` | REST 接口 |

### 4.2 接口设计

| 接口 | 方法 | 作用 |
|---|---|---|
| `/api/dashboard/nav/tree` | GET | 返回两级导航树，二级节点带 deviceCount |
| `/api/dashboard/nav/nodes/{nodeId}/devices` | GET | 查某二级节点下设备列表（JOIN device_template） |
| `/api/dashboard/nav/devices/{deviceDeployId}` | GET | 按设备ID查单个设备详情（供图形页获取设备名） |

设备列表查询 SQL 核心：`device_deployment` JOIN `device_template`，通过 `nav_node_id` 外键过滤，并用 `device_deploy_name LIKE 'MOCK_%'` 标记占位设备。

### 4.3 导航树构建逻辑

1. 查全部未删除节点（`findAll`），按 `sort_order` 排序
2. 查各二级节点设备数（`countDevicesByNode`），构建 nodeId -> count 映射
3. 将节点转为 DTO，二级节点设置 `deviceCount`
4. 按 `parent_id` 组装父子关系，返回一级节点列表（各自带 children）

## 五、前端实现

### 5.1 页面结构

```
首页（/）
├── 左侧：LeftSidebar（两级导航树，nav 模式）
│   ├── 数据总览（虚拟节点，点击回首页数据图）
│   ├── 房间/方舱类型 ▸
│   ├── 电气系统 ▸
│   │   ├── 中压系统 (2)
│   │   ├── 低压系统 (32)
│   │   └── ...
│   └── ...
└── 右侧：
    ├── 未选节点 → KPI卡片 + 建筑占位图 + 趋势图（原首页数据图）
    └── 选中节点 → 设备列表（DeviceListView）
        └── 点击"查看监控" → 跳转图形监控页（/dashboard-nav/device/:id）
            ├── FuxaMonitorPanel（FUXA 图形视图）
            └── AlarmTable（告警列表，按设备名过滤）
```

### 5.2 交互逻辑

- **点击"数据总览"**：清空选中，右侧显示首页数据图（KPI/建筑图/趋势图）
- **点击一级节点**：右侧聚合显示该一级下所有二级节点的设备（前端 Promise.all 并发查询后合并）
- **点击二级节点**：右侧只显示该二级节点下设备
- **点击设备"查看监控"**：跳转新页面，显示图形监控视图 + 告警列表

### 5.3 关键组件

| 组件 | 职责 |
|---|---|
| `LeftSidebar.vue` | 改造支持 `mode="nav"` 两级导航模式，保留原 default 模式 |
| `DeviceListView.vue` | 设备列表表格（设备ID/名称/类型/厂商/状态/操作），Mock 设备标"示例数据" |
| `DeviceMonitorView.vue` | 设备图形监控页，复用 FuxaMonitorPanel + AlarmTable |
| `stores/dashboard.ts` | 导航树状态、设备列表、节点选择逻辑下沉到 Pinia store |

### 5.4 深色主题适配

在 `global.scss` 中新增 Element Plus `el-table` 深色主题覆盖（表头、行、边框、空数据等统一深色），与项目黑色主题一致。

## 六、技术要点

### 6.1 新旧表共存，互不干扰

本地数据库同时存在旧表（device/product 等）和新表（nav_node/device_deployment 等），但：
- 旧页面（/device-deployment 等）只用旧表
- 新 dashboard 导航栏只用新表
- 两套互不干扰，零回归风险

### 6.2 远程数据库保护

远程数据库全程只读，所有新表和数据建在本地。验证确认远程库仍为原 7 张表，无 `nav_node` 表，`device_deployment` 无 `nav_node_id` 字段。

### 6.3 设备命名规范的应用

设备名最后一段为设备类型（如 `A1_MVP_A1_MVSG_A1_02_EM` 中 `EM` 为电表），以此作为设备归属导航节点的映射依据。设备名中的柜体名（如 MVSG = 低压配电柜）也辅助判断归属。

## 七、待确认事项

设备类型到导航分类的映射规则（如 ZB 归低压系统、DHG 归中压系统等）是根据设备类型语义和命名规范推断的，远程数据库中无业务分类字段可循。此映射规则待老师确认后可随时调整（只需修改 `dashboard_nav_init.sql` 中的 UPDATE 语句重新挂载）。

## 八、文件清单

### 数据库脚本
- `backend/materials/remote_new_tables.sql` - 从远程导出的4张新表
- `backend/materials/dashboard_nav_init.sql` - 导航表建表+预置数据+设备挂载

### 后端（7个新文件）
- `entity/NavNode.java`
- `mapper/NavNodeMapper.java` + `resources/mapper/NavNodeMapper.xml`
- `dto/NavTreeNodeDto.java`、`dto/DeviceDeployListDto.java`
- `service/DashboardNavService.java`
- `controller/DashboardNavController.java`

### 前端（4个新文件 + 5个改造文件）
- 新建：`api/dashboardNav.ts`、`components/DeviceListView.vue`、`views/DeviceMonitorView.vue`
- 改造：`components/LeftSidebar.vue`、`views/DashboardView.vue`、`stores/dashboard.ts`、`router/index.ts`、`styles/global.scss`

### 设计文档
- `docs/superpowers/specs/2026-07-29-dashboard-nav-table-design.md` - 设计文档
- `docs/superpowers/plans/2026-07-29-dashboard-nav-table.md` - 实现计划
