# Dashboard 左侧导航栏专用表实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为 dashboard 页构建专用两级导航表 `nav_node`，配合 `device_deployment` 外键挂载真实设备，提供独立的 dashboard 导航/设备列表/图形监控页接口与前端页面，不改动旧表代码。

**Architecture:** 方案 A——新建独立 `DashboardNavController/Service`，设备列表查 `device_deployment`（JOIN `device_template`），图形监控页复用现有 `FuxaMonitorPanel`（fuxa 绑定表为空时优雅降级到默认模板视图）。所有新表与数据建在本地库，远程库只读。

**Tech Stack:** Spring Boot 4.0.7 / Java 21 / MyBatis / MySQL 8；Vue 3.5 / Element Plus 2.14 / Pinia 3 / Vue Router 4 / Vite 8 / axios。

**Spec:** `docs/superpowers/specs/2026-07-29-dashboard-nav-table-design.md`

---

## 文件结构总览

### 数据库脚本（本地执行，远程只读）
- Create: `backend/materials/dashboard_nav_init.sql` — 建表、加外键、预置分类、挂载设备、mock 占位的完整脚本

### 后端（新建，不碰旧表代码）
- Create: `backend/src/main/java/com/n4monitor/entity/NavNode.java` — 导航节点实体
- Create: `backend/src/main/java/com/n4monitor/mapper/NavNodeMapper.java` — MyBatis 接口
- Create: `backend/src/main/resources/mapper/NavNodeMapper.xml` — SQL 映射
- Create: `backend/src/main/java/com/n4monitor/dto/NavTreeNodeDto.java` — 导航树节点 DTO（含 children + deviceCount）
- Create: `backend/src/main/java/com/n4monitor/dto/DeviceDeployListDto.java` — 设备列表项 DTO
- Create: `backend/src/main/java/com/n4monitor/service/DashboardNavService.java` — 导航树构建 + 设备列表查询
- Create: `backend/src/main/java/com/n4monitor/controller/DashboardNavController.java` — 3 个接口

### 前端（新建 + 改造）
- Create: `frontend/src/api/dashboardNav.ts` — 新 API 模块
- Modify: `frontend/src/api/types.ts` — 新增 NavTreeNode / DeviceDeployItem 类型
- Modify: `frontend/src/router/index.ts` — 新增 2 条路由
- Create: `frontend/src/views/DashboardNavView.vue` — dashboard 主页面（导航+设备列表）
- Create: `frontend/src/components/DeviceListView.vue` — 设备列表组件
- Create: `frontend/src/views/DeviceMonitorView.vue` — 设备图形监控页（跳转目标）
- Modify: `frontend/src/components/LeftSidebar.vue` — 改造为两级导航树（仅 dashboard-nav 路由下用新数据源）

---

## Task 1: 导入远程新表结构与数据到本地库

**目标：** 把远程 `device_deployment`/`device_template`/`point_deployment`/`point_template` 四张表的结构+数据导入本地 `n4_monitor` 库（只读远程，不改动远程）。

**Files:**
- 无新建文件（命令行操作）

- [ ] **Step 1: 备份本地库**

```bash
"E:/MySQL/MySQL Server 8.0/bin/mysqldump.exe" -h localhost -P 3306 -u root -p123456 n4_monitor > "E:/Curzsu/N4-monitor/backend/materials/n4_monitor_backup_$(date +%Y%m%d_%H%M%S).sql"
```
Expected: 生成备份 SQL 文件，无报错。

- [ ] **Step 2: 从远程导出四张新表的结构+数据**

```bash
"E:/MySQL/MySQL Server 8.0/bin/mysqldump.exe" -h 47.121.30.136 -P 3306 -u root -p"Password@mysql" --default-character-set=utf8mb4 n4_monitor device_template device_deployment point_template point_deployment > "E:/Curzsu/N4-monitor/backend/materials/remote_new_tables.sql"
```
Expected: 生成 `remote_new_tables.sql`，含 4 张表 CREATE TABLE + INSERT。

- [ ] **Step 3: 导入本地库**

```bash
"E:/MySQL/MySQL Server 8.0/bin/mysql.exe" -h localhost -P 3306 -u root -p123456 n4_monitor < "E:/Curzsu/N4-monitor/backend/materials/remote_new_tables.sql"
```
Expected: 无报错。

- [ ] **Step 4: 验证本地四张表行数**

```bash
"E:/MySQL/MySQL Server 8.0/bin/mysql.exe" -h localhost -P 3306 -u root -p123456 -D n4_monitor -t -e "SELECT 'device_template' t, COUNT(*) c FROM device_template UNION ALL SELECT 'device_deployment', COUNT(*) FROM device_deployment UNION ALL SELECT 'point_template', COUNT(*) FROM point_template UNION ALL SELECT 'point_deployment', COUNT(*) FROM point_deployment;"
```
Expected: device_template=16, device_deployment=47, point_template=556, point_deployment=2476。

- [ ] **Step 5: Commit 脚本文件**

```bash
cd E:/Curzsu/N4-monitor
git add -f backend/materials/remote_new_tables.sql
git commit -m "chore(db): 导入远程新表结构与数据到本地(device_template/deployment/point_template/deployment)"
```

---

## Task 2: 编写并执行导航表初始化脚本

**目标：** 建 `nav_node` 表、给 `device_deployment` 加 `nav_node_id` 外键、预置 8 一级 + 21 二级节点、挂载 47 台真实设备、插入 mock 占位设备。

**Files:**
- Create: `backend/materials/dashboard_nav_init.sql`

- [ ] **Step 1: 编写完整初始化脚本**

写入 `backend/materials/dashboard_nav_init.sql`，完整内容如下：

```sql
-- ============================================================
-- Dashboard 左侧导航栏专用表初始化
-- 在本地 n4_monitor 库执行；远程库不改动
-- ============================================================

-- 1. 新建导航节点表（两级自关联）
CREATE TABLE IF NOT EXISTS `nav_node` (
  `node_id`      bigint       NOT NULL AUTO_INCREMENT COMMENT '导航节点ID',
  `parent_id`    bigint       DEFAULT NULL             COMMENT '父节点ID；一级节点为NULL，二级节点指向所属一级',
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

-- 2. device_deployment 加导航外键字段
ALTER TABLE `device_deployment`
  ADD COLUMN IF NOT EXISTS `nav_node_id` bigint DEFAULT NULL COMMENT '所属导航二级节点ID' AFTER `device_tmpl_id`,
  ADD KEY IF NOT EXISTS `idx_nav_node_id` (`nav_node_id`);

-- 3. 预置一级节点（8个）
INSERT INTO `nav_node` (`node_name`, `node_level`, `node_type`, `icon`, `sort_order`) VALUES
('房间/方舱类型',              1, 'group', 'Location',     1),
('电气系统图',                 1, 'group', 'Picture',      2),
('电气系统',                   1, 'group', 'OfficeBuilding',3),
('BA系统',                     1, 'group', 'Fan',          4),
('设备总览',                   1, 'group', 'Grid',         5),
('柴油发电机系统/油路系统',     1, 'group', 'Lightning',    6),
('消防系统',                   1, 'group', 'Warning',      7),
('自定义',                     1, 'group', 'EditPen',      8);

-- 4. 预置二级节点（21个）。parent_id 用子查询取一级节点id，避免硬编码。
-- 4.1 房间/方舱类型（5个，全mock）
INSERT INTO `nav_node` (`parent_id`, `node_name`, `node_level`, `sort_order`)
SELECT n.node_id, v.name, 2, v.sort
FROM `nav_node` n
JOIN (SELECT 'DataHall Room 25' name, 1 sort UNION ALL
      SELECT 'Public Area GF', 2 UNION ALL
      SELECT 'Public Area RF', 3 UNION ALL
      SELECT 'A1 PTU-A', 4 UNION ALL
      SELECT 'A1 PTU-B', 5) v
WHERE n.node_name='房间/方舱类型' AND n.node_level=1;

-- 4.2 电气系统图（2个，全mock）
INSERT INTO `nav_node` (`parent_id`, `node_name`, `node_level`, `sort_order`)
SELECT n.node_id, v.name, 2, v.sort
FROM `nav_node` n
JOIN (SELECT '电气系统图总览' name, 1 sort UNION ALL
      SELECT 'PTU单独系统图', 2) v
WHERE n.node_name='电气系统图' AND n.node_level=1;

-- 4.3 电气系统（5个，全真设备）
INSERT INTO `nav_node` (`parent_id`, `node_name`, `node_level`, `sort_order`)
SELECT n.node_id, v.name, 2, v.sort
FROM `nav_node` n
JOIN (SELECT '中压系统' name, 1 sort UNION ALL
      SELECT '低压系统', 2 UNION ALL
      SELECT 'UPS系统', 3 UNION ALL
      SELECT '直流系统', 4 UNION ALL
      SELECT '采集网关', 5) v
WHERE n.node_name='电气系统' AND n.node_level=1;

-- 4.4 BA系统（2个，风冷真设备，液冷mock）
INSERT INTO `nav_node` (`parent_id`, `node_name`, `node_level`, `sort_order`)
SELECT n.node_id, v.name, 2, v.sort
FROM `nav_node` n
JOIN (SELECT '风冷系统' name, 1 sort UNION ALL
      SELECT '液冷系统', 2) v
WHERE n.node_name='BA系统' AND n.node_level=1;

-- 4.5 设备总览（2个，全真设备）
INSERT INTO `nav_node` (`parent_id`, `node_name`, `node_level`, `sort_order`)
SELECT n.node_id, v.name, 2, v.sort
FROM `nav_node` n
JOIN (SELECT '温湿度' name, 1 sort UNION ALL
      SELECT '空调系统', 2) v
WHERE n.node_name='设备总览' AND n.node_level=1;

-- 4.6 柴油发电机系统/油路系统（2个，全mock）
INSERT INTO `nav_node` (`parent_id`, `node_name`, `node_level`, `sort_order`)
SELECT n.node_id, v.name, 2, v.sort
FROM `nav_node` n
JOIN (SELECT '柴油发电机系统' name, 1 sort UNION ALL
      SELECT '油路系统', 2) v
WHERE n.node_name='柴油发电机系统/油路系统' AND n.node_level=1;

-- 4.7 消防系统（2个，全真设备）
INSERT INTO `nav_node` (`parent_id`, `node_name`, `node_level`, `sort_order`)
SELECT n.node_id, v.name, 2, v.sort
FROM `nav_node` n
JOIN (SELECT '气体灭火/极早期' name, 1 sort UNION ALL
      SELECT '水消防/水压监测', 2) v
WHERE n.node_name='消防系统' AND n.node_level=1;

-- 4.8 自定义（1个，mock）
INSERT INTO `nav_node` (`parent_id`, `node_name`, `node_level`, `sort_order`)
SELECT n.node_id, '自定义分组/模板', 2, 1
FROM `nav_node` n
WHERE n.node_name='自定义' AND n.node_level=1;

-- 5. 挂载真实设备：按 device_type 更新 device_deployment.nav_node_id
UPDATE device_deployment dd
JOIN device_template dt ON dd.device_tmpl_id = dt.device_tmpl_id
JOIN nav_node nn ON nn.node_name = v_map.target AND nn.node_level = 2
SET dd.nav_node_id = nn.node_id
JOIN (
  SELECT 'DHG' dtype, '中压系统' target UNION ALL
  SELECT 'EM', '低压系统' UNION ALL
  SELECT 'ZB', '低压系统' UNION ALL
  SELECT 'UDB', 'UPS系统' UNION ALL
  SELECT 'EDB', 'UPS系统' UNION ALL
  SELECT 'DCP', '直流系统' UNION ALL
  SELECT 'Collector', '采集网关' UNION ALL
  SELECT 'IRC1', '风冷系统' UNION ALL
  SELECT 'IRC2', '风冷系统' UNION ALL
  SELECT 'DEHU', '空调系统' UNION ALL
  SELECT 'TH1', '温湿度' UNION ALL
  SELECT 'TH2', '温湿度' UNION ALL
  SELECT 'TH3', '温湿度' UNION ALL
  SELECT 'GAS1', '气体灭火/极早期' UNION ALL
  SELECT 'GAS2', '气体灭火/极早期' UNION ALL
  SELECT 'LD', '水消防/水压监测'
) v_map ON dt.device_type = v_map.dtype
WHERE dd.is_deleted = 0;
```

> 注意：上面 UPDATE 语句含两个 JOIN 别名冲突（v_map 既要 JOIN 又要在 SET 引用）。MySQL 不允许 UPDATE 同时 JOIN 派生表再用同一别名。下一步会修正为正确写法。

- [ ] **Step 2: 修正 UPDATE 语句为 MySQL 合法写法**

UPDATE 多表 JOIN 派生表时，派生表需独立 JOIN。把第 5 步的 UPDATE 替换为以下正确写法（用内联 CASE 而非派生表，避免语法问题）：

```sql
-- 5. 挂载真实设备：用 CASE 映射 device_type -> 二级节点名
UPDATE device_deployment dd
JOIN device_template dt ON dd.device_tmpl_id = dt.device_tmpl_id
JOIN nav_node nn ON nn.node_level = 2
SET dd.nav_node_id = nn.node_id
WHERE dd.is_deleted = 0
  AND nn.node_name = CASE dt.device_type
    WHEN 'DHG' THEN '中压系统'
    WHEN 'EM' THEN '低压系统'
    WHEN 'ZB' THEN '低压系统'
    WHEN 'UDB' THEN 'UPS系统'
    WHEN 'EDB' THEN 'UPS系统'
    WHEN 'DCP' THEN '直流系统'
    WHEN 'Collector' THEN '采集网关'
    WHEN 'IRC1' THEN '风冷系统'
    WHEN 'IRC2' THEN '风冷系统'
    WHEN 'DEHU' THEN '空调系统'
    WHEN 'TH1' THEN '温湿度'
    WHEN 'TH2' THEN '温湿度'
    WHEN 'TH3' THEN '温湿度'
    WHEN 'GAS1' THEN '气体灭火/极早期'
    WHEN 'GAS2' THEN '气体灭火/极早期'
    WHEN 'LD' THEN '水消防/水压监测'
  END;
```

- [ ] **Step 3: 追加 mock 占位设备插入语句**

在脚本末尾追加（为 11 个无真设备的二级节点各插 1 台 mock 设备，device_tmpl_id 用对应类型或 1，status=2 离线，device_deploy_name 带 MOCK_ 前缀）：

```sql
-- 6. 为无真设备的二级节点插入 mock 占位设备
-- 先确保每个目标二级节点至少有一条 device_deployment（device_tmpl_id 借用 1，实际展示由 is_mock 标记）
INSERT INTO device_deployment
  (device_tmpl_id, device_deploy_name, device_alias, n4_address, sys_sort_order, status, nav_node_id)
SELECT 1, CONCAT('MOCK_', nn.node_name), CONCAT('MOCK_', nn.node_name), CONCAT('mock://', nn.node_name), 0, 2, nn.node_id
FROM nav_node nn
WHERE nn.node_level = 2
  AND nn.is_deleted = 0
  AND NOT EXISTS (
    SELECT 1 FROM device_deployment dd
    WHERE dd.nav_node_id = nn.node_id AND dd.is_deleted = 0
  );
```

- [ ] **Step 4: 执行初始化脚本**

```bash
"E:/MySQL/MySQL Server 8.0/bin/mysql.exe" -h localhost -P 3306 -u root -p123456 --default-character-set=utf8mb4 n4_monitor < "E:/Curzsu/N4-monitor/backend/materials/dashboard_nav_init.sql"
```
Expected: 无报错。

- [ ] **Step 5: 验证导航数据**

```bash
"E:/MySQL/MySQL Server 8.0/bin/mysql.exe" -h localhost -P 3306 -u root -p123456 -D n4_monitor -t -e "
SELECT nn1.node_name AS 一级, nn2.node_name AS 二级, COUNT(dd.device_deploy_id) AS 设备数
FROM nav_node nn1
JOIN nav_node nn2 ON nn2.parent_id = nn1.node_id AND nn2.is_deleted=0
LEFT JOIN device_deployment dd ON dd.nav_node_id = nn2.node_id AND dd.is_deleted=0
WHERE nn1.node_level=1 AND nn1.is_deleted=0
GROUP BY nn1.node_id, nn2.node_id
ORDER BY nn1.sort_order, nn2.sort_order;"
```
Expected: 21 行结果。电气系统下中压=2、低压=32、UPS=2、直流=1、采集网关=1；BA风冷=2、液冷=1(mock)；设备总览温湿度=3、空调系统=1；消防气体灭火=2、水消防=1；其余 11 个二级各=1(mock)。

- [ ] **Step 6: Commit 脚本**

```bash
cd E:/Curzsu/N4-monitor
git add -f backend/materials/dashboard_nav_init.sql
git commit -m "feat(db): 新建nav_node导航表及两级分类数据，挂载真实设备+mock占位"
```

---

## Task 3: 后端实体与 Mapper

**目标：** 创建 NavNode 实体、NavNodeMapper 接口与 XML，提供导航树查询与设备列表查询。

**Files:**
- Create: `backend/src/main/java/com/n4monitor/entity/NavNode.java`
- Create: `backend/src/main/java/com/n4monitor/mapper/NavNodeMapper.java`
- Create: `backend/src/main/resources/mapper/NavNodeMapper.xml`

- [ ] **Step 1: 创建 NavNode 实体**

`backend/src/main/java/com/n4monitor/entity/NavNode.java`:

```java
package com.n4monitor.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class NavNode {
    private Long nodeId;
    private Long parentId;
    private String nodeName;
    private Integer nodeLevel;
    private String nodeType;
    private String icon;
    private Integer sortOrder;
    private Boolean isDeleted;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
```

- [ ] **Step 2: 创建 NavNodeMapper 接口**

`backend/src/main/java/com/n4monitor/mapper/NavNodeMapper.java`:

```java
package com.n4monitor.mapper;

import com.n4monitor.entity.NavNode;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;
import java.util.Map;

@Mapper
public interface NavNodeMapper {
    /** 查全部未删除节点，按 sort_order 排序 */
    List<NavNode> findAll();

    /** 统计各二级节点下的设备数（is_deleted=0），返回 nodeId -> count */
    List<Map<String, Object>> countDevicesByNode();

    /** 查某二级节点下的设备列表（JOIN device_template） */
    List<Map<String, Object>> findDevicesByNodeId(@Param("nodeId") Long nodeId);
}
```

- [ ] **Step 3: 创建 NavNodeMapper.xml**

`backend/src/main/resources/mapper/NavNodeMapper.xml`:

```xml
<?xml version="1.0" encoding="UTF-8" ?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN"
        "http://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="com.n4monitor.mapper.NavNodeMapper">

    <select id="findAll" resultType="com.n4monitor.entity.NavNode">
        SELECT node_id, parent_id, node_name, node_level, node_type, icon, sort_order,
               is_deleted, create_time, update_time
        FROM nav_node
        WHERE is_deleted = 0
        ORDER BY sort_order, node_id
    </select>

    <select id="countDevicesByNode" resultType="map">
        SELECT dd.nav_node_id AS nodeId, COUNT(*) AS deviceCount
        FROM device_deployment dd
        WHERE dd.is_deleted = 0 AND dd.nav_node_id IS NOT NULL
        GROUP BY dd.nav_node_id
    </select>

    <select id="findDevicesByNodeId" resultType="map">
        SELECT dd.device_deploy_id   AS deviceDeployId,
               dd.device_deploy_name AS deviceDeployName,
               dd.device_alias       AS deviceAlias,
               dd.status             AS status,
               dd.n4_address         AS n4Address,
               dt.device_type        AS deviceType,
               dt.manufacturer       AS manufacturer,
               dt.model              AS model,
               (dd.device_deploy_name LIKE 'MOCK_%') AS isMock
        FROM device_deployment dd
        LEFT JOIN device_template dt ON dd.device_tmpl_id = dt.device_tmpl_id
        WHERE dd.is_deleted = 0 AND dd.nav_node_id = #{nodeId}
        ORDER BY dd.device_deploy_id
    </select>

</mapper>
```

- [ ] **Step 4: 编译验证后端**

```bash
cd E:/Curzsu/N4-monitor/backend
./mvnw compile -q 2>&1 | tail -20
```
Expected: BUILD SUCCESS，无编译错误。

- [ ] **Step 5: Commit**

```bash
cd E:/Curzsu/N4-monitor
git add backend/src/main/java/com/n4monitor/entity/NavNode.java backend/src/main/java/com/n4monitor/mapper/NavNodeMapper.java backend/src/main/resources/mapper/NavNodeMapper.xml
git commit -m "feat(backend): 新增NavNode实体与Mapper(导航树查询/设备列表查询)"
```

---

## Task 4: 后端 DTO

**目标：** 创建导航树节点 DTO 和设备列表项 DTO。

**Files:**
- Create: `backend/src/main/java/com/n4monitor/dto/NavTreeNodeDto.java`
- Create: `backend/src/main/java/com/n4monitor/dto/DeviceDeployListDto.java`

- [ ] **Step 1: 创建 NavTreeNodeDto**

`backend/src/main/java/com/n4monitor/dto/NavTreeNodeDto.java`:

```java
package com.n4monitor.dto;

import lombok.Data;
import java.util.ArrayList;
import java.util.List;

@Data
public class NavTreeNodeDto {
    private Long nodeId;
    private String nodeName;
    private Integer nodeLevel;
    private String icon;
    private Integer sortOrder;
    /** 二级节点的设备数；一级节点为 null */
    private Integer deviceCount;
    private List<NavTreeNodeDto> children = new ArrayList<>();
}
```

- [ ] **Step 2: 创建 DeviceDeployListDto**

`backend/src/main/java/com/n4monitor/dto/DeviceDeployListDto.java`:

```java
package com.n4monitor.dto;

import lombok.Data;

@Data
public class DeviceDeployListDto {
    private Long deviceDeployId;
    private String deviceDeployName;
    private String deviceAlias;
    private Integer status;
    private String n4Address;
    private String deviceType;
    private String manufacturer;
    private String model;
    private Boolean isMock;
}
```

- [ ] **Step 3: Commit**

```bash
cd E:/Curzsu/N4-monitor
git add backend/src/main/java/com/n4monitor/dto/NavTreeNodeDto.java backend/src/main/java/com/n4monitor/dto/DeviceDeployListDto.java
git commit -m "feat(backend): 新增NavTreeNodeDto与DeviceDeployListDto"
```

---

## Task 5: 后端 Service 与 Controller

**目标：** 创建 DashboardNavService（构建两级树 + 设备列表查询）和 DashboardNavController（3 个接口）。

**Files:**
- Create: `backend/src/main/java/com/n4monitor/service/DashboardNavService.java`
- Create: `backend/src/main/java/com/n4monitor/controller/DashboardNavController.java`

- [ ] **Step 1: 创建 DashboardNavService**

`backend/src/main/java/com/n4monitor/service/DashboardNavService.java`:

```java
package com.n4monitor.service;

import com.n4monitor.dto.DeviceDeployListDto;
import com.n4monitor.dto.NavTreeNodeDto;
import com.n4monitor.entity.NavNode;
import com.n4monitor.mapper.NavNodeMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class DashboardNavService {

    private final NavNodeMapper navNodeMapper;

    public DashboardNavService(NavNodeMapper navNodeMapper) {
        this.navNodeMapper = navNodeMapper;
    }

    /** 构建两级导航树：一级节点 -> children(二级节点，带 deviceCount) */
    public List<NavTreeNodeDto> getNavTree() {
        List<NavNode> all = navNodeMapper.findAll();
        Map<Long, Integer> countMap = new HashMap<>();
        for (Map<String, Object> row : navNodeMapper.countDevicesByNode()) {
            Object id = row.get("nodeId");
            Object cnt = row.get("deviceCount");
            if (id != null) {
                countMap.put(((Number) id).longValue(), ((Number) cnt).intValue());
            }
        }

        Map<Long, NavTreeNodeDto> dtoMap = new HashMap<>();
        List<NavTreeNodeDto> level1 = new ArrayList<>();
        for (NavNode n : all) {
            NavTreeNodeDto dto = new NavTreeNodeDto();
            dto.setNodeId(n.getNodeId());
            dto.setNodeName(n.getNodeName());
            dto.setNodeLevel(n.getNodeLevel());
            dto.setIcon(n.getIcon());
            dto.setSortOrder(n.getSortOrder());
            if (n.getNodeLevel() == 2) {
                dto.setDeviceCount(countMap.getOrDefault(n.getNodeId(), 0));
            }
            dtoMap.put(n.getNodeId(), dto);
        }
        // 组装父子关系
        for (NavNode n : all) {
            NavTreeNodeDto dto = dtoMap.get(n.getNodeId());
            if (n.getNodeLevel() == 1) {
                level1.add(dto);
            } else if (n.getParentId() != null && dtoMap.containsKey(n.getParentId())) {
                dtoMap.get(n.getParentId()).getChildren().add(dto);
            }
        }
        return level1;
    }

    /** 查某二级节点下设备列表 */
    public List<DeviceDeployListDto> getDevicesByNode(Long nodeId) {
        List<Map<String, Object>> rows = navNodeMapper.findDevicesByNodeId(nodeId);
        return rows.stream().map(row -> {
            DeviceDeployListDto dto = new DeviceDeployListDto();
            dto.setDeviceDeployId(toLong(row.get("deviceDeployId")));
            dto.setDeviceDeployName((String) row.get("deviceDeployName"));
            dto.setDeviceAlias((String) row.get("deviceAlias"));
            dto.setStatus(toInt(row.get("status")));
            dto.setN4Address((String) row.get("n4Address"));
            dto.setDeviceType((String) row.get("deviceType"));
            dto.setManufacturer((String) row.get("manufacturer"));
            dto.setModel((String) row.get("model"));
            Object isMock = row.get("isMock");
            dto.setIsMock(isMock != null && ((Number) isMock).intValue() == 1);
            return dto;
        }).collect(Collectors.toList());
    }

    private static Long toLong(Object v) {
        return v == null ? null : ((Number) v).longValue();
    }

    private static Integer toInt(Object v) {
        return v == null ? null : ((Number) v).intValue();
    }
}
```

- [ ] **Step 2: 创建 DashboardNavController**

`backend/src/main/java/com/n4monitor/controller/DashboardNavController.java`:

```java
package com.n4monitor.controller;

import com.n4monitor.dto.ApiResponse;
import com.n4monitor.dto.DeviceDeployListDto;
import com.n4monitor.dto.NavTreeNodeDto;
import com.n4monitor.service.DashboardNavService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/dashboard/nav")
public class DashboardNavController {

    private final DashboardNavService navService;

    public DashboardNavController(DashboardNavService navService) {
        this.navService = navService;
    }

    /** 获取两级导航树 */
    @GetMapping("/tree")
    public ApiResponse<List<NavTreeNodeDto>> getNavTree() {
        return ApiResponse.success(navService.getNavTree());
    }

    /** 查某二级节点下设备列表 */
    @GetMapping("/nodes/{nodeId}/devices")
    public ApiResponse<List<DeviceDeployListDto>> getDevicesByNode(@PathVariable Long nodeId) {
        return ApiResponse.success(navService.getDevicesByNode(nodeId));
    }
}
```

> 说明：spec 第 5.2 节提到的第三个接口「设备图形页 fuxa 绑定」`GET /api/dashboard/nav/devices/{deviceDeployId}/fuxa`。由于本地 `fuxa_device_binding` 表为空（无任何绑定记录），且该表按旧 `device_id` 键控，本期该接口暂不实现——前端图形页直接复用现有 `FuxaMonitorPanel`，绑定查不到时它会优雅降级到默认模板视图（见 FuxaMonitorPanel.vue:112-120）。这样避免引入一个永远查不到数据的接口。后续 Node-RED 发设备后可再补。

- [ ] **Step 3: 编译验证**

```bash
cd E:/Curzsu/N4-monitor/backend
./mvnw compile -q 2>&1 | tail -20
```
Expected: BUILD SUCCESS。

- [ ] **Step 4: 启动后端并验证接口**

```bash
cd E:/Curzsu/N4-monitor/backend
./mvnw spring-boot:run -q 2>&1 | tail -30 &
sleep 30
curl -s "http://localhost:8089/api/dashboard/nav/tree" | head -c 500
echo ""
curl -s "http://localhost:8089/api/dashboard/nav/nodes/4/devices" | head -c 500
```
Expected: tree 接口返回 JSON 含 8 个一级节点及 children；devices 接口返回中压系统的 2 台设备。

- [ ] **Step 5: Commit**

```bash
cd E:/Curzsu/N4-monitor
git add backend/src/main/java/com/n4monitor/service/DashboardNavService.java backend/src/main/java/com/n4monitor/controller/DashboardNavController.java
git commit -m "feat(backend): 新增DashboardNavService/Controller(导航树+设备列表接口)"
```

---

## Task 6: 前端 API 与类型定义

**目标：** 新建 dashboardNav API 模块，types.ts 新增导航树与设备列表类型。

**Files:**
- Create: `frontend/src/api/dashboardNav.ts`
- Modify: `frontend/src/api/types.ts`

- [ ] **Step 1: types.ts 末尾追加新类型**

在 `frontend/src/api/types.ts` 末尾追加：

```typescript
// ===== Dashboard Nav Types =====

export interface NavTreeNode {
  nodeId: number
  nodeName: string
  nodeLevel: number
  icon?: string
  sortOrder: number
  deviceCount?: number
  children: NavTreeNode[]
}

export interface DeviceDeployItem {
  deviceDeployId: number
  deviceDeployName: string
  deviceAlias: string | null
  status: number
  n4Address: string
  deviceType: string | null
  manufacturer: string | null
  model: string | null
  isMock: boolean
}
```

- [ ] **Step 2: 创建 dashboardNav.ts**

`frontend/src/api/dashboardNav.ts`:

```typescript
import request from './request'
import type { NavTreeNode, DeviceDeployItem } from './types'

export const dashboardNavApi = {
  getNavTree(): Promise<NavTreeNode[]> {
    return request.get('/api/dashboard/nav/tree')
  },
  getDevicesByNode(nodeId: number): Promise<DeviceDeployItem[]> {
    return request.get(`/api/dashboard/nav/nodes/${nodeId}/devices`)
  },
}
```

- [ ] **Step 3: Commit**

```bash
cd E:/Curzsu/N4-monitor
git add frontend/src/api/dashboardNav.ts frontend/src/api/types.ts
git commit -m "feat(frontend): 新增dashboardNav API模块与导航/设备列表类型"
```

---

## Task 7: 前端设备列表组件

**目标：** 创建 DeviceListView 组件，接收设备数组，表格展示，mock 设备标记，查看监控按钮。

**Files:**
- Create: `frontend/src/components/DeviceListView.vue`

- [ ] **Step 1: 创建 DeviceListView.vue**

`frontend/src/components/DeviceListView.vue`:

```vue
<template>
  <div class="device-list-view">
    <div class="list-header">
      <h3>{{ title }}</h3>
      <span class="device-count">共 {{ devices.length }} 台设备</span>
    </div>
    <el-table :data="devices" style="width: 100%" v-loading="loading" empty-text="暂无设备">
      <el-table-column prop="deviceDeployName" label="设备名称" min-width="200">
        <template #default="{ row }">
          <span :class="{ 'mock-name': row.isMock }">{{ row.deviceDeployName }}</span>
          <el-tag v-if="row.isMock" size="small" type="info" class="mock-tag">示例数据</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="deviceAlias" label="别名" min-width="160" show-overflow-tooltip />
      <el-table-column prop="deviceType" label="设备类型" width="120" />
      <el-table-column prop="manufacturer" label="厂商" width="120" />
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <span class="status-dot" :class="getStatusClass(row.status)"></span>
          <span class="status-text">{{ getStatusText(row.status) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="120" fixed="right">
        <template #default="{ row }">
          <el-button type="primary" size="small" link @click="$emit('view-monitor', row)">
            查看监控
          </el-button>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script setup lang="ts">
import type { DeviceDeployItem } from '@/api/types'

defineProps<{
  devices: DeviceDeployItem[]
  title: string
  loading?: boolean
}>()

defineEmits<{
  (e: 'view-monitor', device: DeviceDeployItem): void
}>()

function getStatusClass(status: number): string {
  switch (status) {
    case 1: return 'online'
    case 2: return 'offline'
    case 3: return 'maintenance'
    case 4: return 'fault'
    default: return 'offline'
  }
}

function getStatusText(status: number): string {
  switch (status) {
    case 1: return '在线'
    case 2: return '离线'
    case 3: return '维护'
    case 4: return '故障'
    default: return '未知'
  }
}
</script>

<style scoped lang="scss">
.device-list-view {
  background-color: $bg-card;
  border: 1px solid $border-color;
  border-radius: 8px;
  padding: 16px;
}

.list-header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;

  h3 {
    margin: 0;
    font-size: 16px;
    color: $text-primary;
  }
  .device-count {
    font-size: 12px;
    color: $text-secondary;
  }
}

.mock-name {
  color: $text-secondary;
}
.mock-tag {
  margin-left: 8px;
}

.status-dot {
  display: inline-block;
  width: 7px;
  height: 7px;
  border-radius: 50%;
  margin-right: 6px;
  &.online { background-color: $accent-green; box-shadow: 0 0 6px rgba(0,230,118,0.6); }
  &.offline { background-color: $text-muted; }
  &.maintenance { background-color: $accent-yellow; box-shadow: 0 0 6px rgba(255,213,79,0.6); }
  &.fault { background-color: $accent-red; box-shadow: 0 0 6px rgba(255,82,82,0.6); }
}
.status-text {
  font-size: 12px;
  color: $text-secondary;
}
</style>
```

- [ ] **Step 2: Commit**

```bash
cd E:/Curzsu/N4-monitor
git add frontend/src/components/DeviceListView.vue
git commit -m "feat(frontend): 新增DeviceListView设备列表组件"
```

---

## Task 8: 前端 DashboardNavView 主页面

**目标：** 创建 dashboard 主页面，集成 LeftSidebar（两级导航）+ DeviceListView，处理节点点击与一级聚合。

**Files:**
- Create: `frontend/src/views/DashboardNavView.vue`

- [ ] **Step 1: 创建 DashboardNavView.vue**

`frontend/src/views/DashboardNavView.vue`:

```vue
<template>
  <div class="dashboard-nav-view">
    <LeftSidebar mode="nav" :nav-tree="navTree" :loading="navLoading"
                 @node-click="handleNodeClick" />
    <main class="main-area">
      <DeviceListView
        :devices="currentDevices"
        :title="currentTitle"
        :loading="devicesLoading"
        @view-monitor="goMonitor"
      />
    </main>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import LeftSidebar from '@/components/LeftSidebar.vue'
import DeviceListView from '@/components/DeviceListView.vue'
import { dashboardNavApi } from '@/api/dashboardNav'
import type { NavTreeNode, DeviceDeployItem } from '@/api/types'
import { ElMessage } from 'element-plus'

const router = useRouter()
const navTree = ref<NavTreeNode[]>([])
const navLoading = ref(false)
const currentDevices = ref<DeviceDeployItem[]>([])
const devicesLoading = ref(false)
const currentNode = ref<NavTreeNode | null>(null)

const currentTitle = computed(() => {
  if (!currentNode.value) return '设备列表'
  if (currentNode.value.nodeLevel === 1) {
    return `${currentNode.value.nodeName} - 全部设备`
  }
  return currentNode.value.nodeName
})

async function loadNavTree() {
  navLoading.value = true
  try {
    navTree.value = await dashboardNavApi.getNavTree()
  } catch (e) {
    console.error('加载导航树失败', e)
  } finally {
    navLoading.value = false
  }
}

async function handleNodeClick(node: NavTreeNode) {
  currentNode.value = node
  devicesLoading.value = true
  try {
    if (node.nodeLevel === 1) {
      // 一级节点：前端聚合所有二级 children 的设备
      const childIds = node.children.map(c => c.nodeId)
      const results = await Promise.all(
        childIds.map(id => dashboardNavApi.getDevicesByNode(id))
      )
      currentDevices.value = results.flat()
    } else {
      // 二级节点：直接查
      currentDevices.value = await dashboardNavApi.getDevicesByNode(node.nodeId)
    }
  } catch (e) {
    console.error('加载设备列表失败', e)
    currentDevices.value = []
    ElMessage.error('加载设备列表失败')
  } finally {
    devicesLoading.value = false
  }
}

function goMonitor(device: DeviceDeployItem) {
  router.push(`/dashboard-nav/device/${device.deviceDeployId}`)
}

onMounted(loadNavTree)
</script>

<style scoped lang="scss">
.dashboard-nav-view {
  display: flex;
  width: 100%;
  height: 100%;
}
.main-area {
  flex: 1;
  padding: 16px;
  overflow-y: auto;
}
</style>
```

- [ ] **Step 2: Commit**

```bash
cd E:/Curzsu/N4-monitor
git add frontend/src/views/DashboardNavView.vue
git commit -m "feat(frontend): 新增DashboardNavView主页面(导航+设备列表+一级聚合)"
```

---

## Task 9: 前端设备图形监控页（跳转目标）

**目标：** 创建 DeviceMonitorView，路由 `/dashboard-nav/device/:deviceDeployId`，复用 FuxaMonitorPanel + 告警列表，绑定查不到时优雅降级。

**Files:**
- Create: `frontend/src/views/DeviceMonitorView.vue`

- [ ] **Step 1: 创建 DeviceMonitorView.vue**

`frontend/src/views/DeviceMonitorView.vue`:

```vue
<template>
  <div class="device-monitor-view">
    <header class="monitor-header">
      <el-button :icon="ArrowLeft" link @click="$router.back()">返回</el-button>
      <h2>{{ deviceName }}</h2>
    </header>

    <FuxaMonitorPanel :device="fuxaDeviceProxy" />

    <section class="alarm-section">
      <h3>告警列表</h3>
      <p class="placeholder" v-if="!alarms.length">暂无告警</p>
      <el-table v-else :data="alarms" size="small">
        <el-table-column prop="time" label="时间" width="160" />
        <el-table-column prop="severity" label="级别" width="80" />
        <el-table-column prop="message" label="告警描述" />
      </el-table>
    </section>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ArrowLeft } from '@element-plus/icons-vue'
import FuxaMonitorPanel from '@/components/FuxaMonitorPanel.vue'
import type { DeviceTreeNode } from '@/api/types'

const route = useRoute()
const deviceDeployId = computed(() => Number(route.params.deviceDeployId))
const deviceName = ref(`设备 ${deviceDeployId.value}`)
const alarms = ref<any[]>([])

// FuxaMonitorPanel 期望 device: DeviceTreeNode，构造代理对象触发其加载绑定逻辑
// FuxaMonitorPanel 内部调用 deviceApi.getFuxaBinding(device.id)，绑定查不到会降级到默认视图
const fuxaDeviceProxy = computed<DeviceTreeNode | null>(() => ({
  id: deviceDeployId.value,
  label: deviceName.value,
  type: 'device',
  status: 1,
  children: [],
}))

onMounted(() => {
  // 设备名暂用 id 占位；后续可调设备详情接口补全。告警列表本期留空（占位）。
})
</script>

<style scoped lang="scss">
.device-monitor-view {
  height: 100%;
  overflow-y: auto;
  padding: 16px;
}
.monitor-header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
  h2 {
    margin: 0;
    font-size: 18px;
    color: $text-primary;
  }
}
.alarm-section {
  margin-top: 16px;
  h3 {
    font-size: 14px;
    color: $text-primary;
    margin: 0 0 8px;
  }
  .placeholder {
    color: $text-secondary;
    font-size: 13px;
  }
}
</style>
```

- [ ] **Step 2: Commit**

```bash
cd E:/Curzsu/N4-monitor
git add frontend/src/views/DeviceMonitorView.vue
git commit -m "feat(frontend): 新增DeviceMonitorView设备图形监控页(复用FuxaMonitorPanel)"
```

---

## Task 10: 改造 LeftSidebar 支持两级导航模式

**目标：** 给 LeftSidebar 增加 `mode="nav"` 模式，接收外部 navTree 数据，渲染两级树，点击 emit 事件；不影响原有 dashboard 的旧树模式。

**Files:**
- Modify: `frontend/src/components/LeftSidebar.vue`

- [ ] **Step 1: 改造 LeftSidebar 支持双模式**

在 `<script setup>` 顶部 props 部分增加模式与外部数据，并改造 `visibleTreeData` 与点击逻辑。具体修改点：

1. 增加 props 定义（替换原 `const store = useDashboardStore()` 单一来源逻辑）：

在 `import` 之后、`const route = useRoute()` 之前插入：

```typescript
const props = defineProps<{
  mode?: 'default' | 'nav'
  navTree?: import('@/api/types').NavTreeNode[]
  loading?: boolean
}>()

const emit = defineEmits<{
  (e: 'node-click', node: import('@/api/types').NavTreeNode): void
}>()
```

2. 改造 `visibleTreeData` computed，支持 nav 模式：

将原 `visibleTreeData` 函数体改为：

```typescript
const visibleTreeData = computed(() => {
  if (props.mode === 'nav') {
    return (props.navTree || []).map(n => toTreeNode(n))
  }
  if (route.path === '/device-template') {
    return [
      {
        id: 'root_tmpl',
        label: '全部设备模板 (数据库真实表)',
        type: 'site',
        status: 1,
        children: store.allProducts.map(p => ({
          id: p.productId || 0,
          label: p.productName,
          type: 'product',
          status: 1,
          children: []
        }))
      }
    ]
  }
  return store.deviceTree
})

// NavTreeNode 转 el-tree 节点
function toTreeNode(n: import('@/api/types').NavTreeNode): any {
  return {
    id: n.nodeId,
    label: n.nodeLevel === 2 && n.deviceCount != null
      ? `${n.nodeName} (${n.deviceCount})`
      : n.nodeName,
    type: n.nodeLevel === 1 ? 'system-group' : 'system-sub',
    status: 1,
    nodeLevel: n.nodeLevel,
    raw: n,
    children: (n.children || []).map(toTreeNode)
  }
}
```

3. 改造 `handleNodeClick`，nav 模式下 emit 原始节点：

将原 `handleNodeClick` 改为：

```typescript
function handleNodeClick(data: any) {
  if (props.mode === 'nav' && data.raw) {
    emit('node-click', data.raw)
    return
  }
  store.selectedNode = data
}
```

> 注意：`store` 仍需保留（default 模式用）。`const store = useDashboardStore()` 保留不动。

- [ ] **Step 2: 前端类型检查**

```bash
cd E:/Curzsu/N4-monitor/frontend
npx vue-tsc --noEmit 2>&1 | tail -20
```
Expected: 无类型错误（或仅有原有无关警告）。

- [ ] **Step 3: Commit**

```bash
cd E:/Curzsu/N4-monitor
git add frontend/src/components/LeftSidebar.vue
git commit -m "feat(frontend): LeftSidebar支持nav两级导航模式(保留default模式)"
```

---

## Task 11: 前端路由注册

**目标：** 在 router 注册 `/dashboard-nav` 和 `/dashboard-nav/device/:deviceDeployId` 两条路由。

**Files:**
- Modify: `frontend/src/router/index.ts`

- [ ] **Step 1: 追加路由**

在 `frontend/src/router/index.ts` 的 `routes` 数组末尾（`/device-deployment` 路由之后）追加：

```typescript
    {
      path: '/dashboard-nav',
      name: 'dashboard-nav',
      component: () => import('@/views/DashboardNavView.vue'),
    },
    {
      path: '/dashboard-nav/device/:deviceDeployId',
      name: 'dashboard-nav-device',
      component: () => import('@/views/DeviceMonitorView.vue'),
    },
```

- [ ] **Step 2: Commit**

```bash
cd E:/Curzsu/N4-monitor
git add frontend/src/router/index.ts
git commit -m "feat(frontend): 注册dashboard-nav与设备监控页路由"
```

---

## Task 12: 联调验证

**目标：** 启动前后端，端到端验证导航树、设备列表、图形页跳转。

**Files:**
- 无（验证步骤）

- [ ] **Step 1: 启动后端**

```bash
cd E:/Curzsu/N4-monitor/backend
./mvnw spring-boot:run -q 2>&1 | tail -30 &
sleep 30
curl -s "http://localhost:8089/api/dashboard/nav/tree" | head -c 300
```
Expected: 返回 8 个一级节点的 JSON。

- [ ] **Step 2: 启动前端**

```bash
cd E:/Curzsu/N4-monitor/frontend
npm run dev 2>&1 | tail -10 &
sleep 15
```
Expected: vite 启动，输出本地访问 URL（如 http://localhost:5173）。

- [ ] **Step 3: 浏览器验证导航树与设备列表**

用浏览器打开 `http://localhost:5173/dashboard-nav`：
- 左侧应显示 8 个一级节点，点击展开二级
- 二级节点带 `(数量)` 角标
- 点击「电气系统」一级，右侧应显示 38 台设备（中压2+低压32+UPS2+直流1+采集网关1）
- 点击「中压系统」二级，右侧只显示 2 台设备
- mock 节点（如液冷系统）显示 1 台 MOCK_ 设备，标"示例数据"

- [ ] **Step 4: 浏览器验证图形页跳转**

- 点击设备列表某行「查看监控」，应跳转到 `/dashboard-nav/device/:id`
- 图形监控页显示 FuxaMonitorPanel（因 fuxa 绑定表为空，会降级显示默认模板视图）
- 页面有「返回」按钮可返回列表页

- [ ] **Step 5: 验证旧页面不受影响**

访问 `http://localhost:5173/`（原 dashboard）和 `/device-deployment`：
- 原 dashboard 左侧导航树应正常显示（default 模式，旧 deviceTree）
- `/device-deployment` 页面应正常工作

- [ ] **Step 6: 验证远程库未被改动**

```bash
"E:/MySQL/MySQL Server 8.0/bin/mysql.exe" -h 47.121.30.136 -P 3306 -u root -p"Password@mysql" -D n4_monitor -e "SHOW TABLES;" 2>/dev/null
```
Expected: 远程库仍是 7 张原表（device_deployment/device_template/point_deployment/point_template/fuxa_device_binding/alarm_rule_enum/alarm_rule_numeric），无 nav_node 表，无 nav_node_id 字段。

- [ ] **Step 7: 最终 Commit（如有遗留改动）**

```bash
cd E:/Curzsu/N4-monitor
git add -A
git status
# 若有改动则提交，否则跳过
git commit -m "chore: dashboard-nav 联调验证完成" 2>&1 || echo "nothing to commit"
```

---

## 完成标准

1. 左侧导航栏显示两级树：8 个一级 + 21 个二级节点
2. 有真设备的 10 个二级节点显示真实设备数；11 个 mock 节点显示 MOCK_ 占位设备
3. 点击一级节点右侧聚合显示其下所有设备，点击二级节点只显示该节点设备
4. 点击「查看监控」跳转新页面，显示 FuxaMonitorPanel（绑定空则降级）+ 告警列表占位
5. 远程数据库未被修改
6. 现有旧页面（/、/device-deployment）功能不受影响
