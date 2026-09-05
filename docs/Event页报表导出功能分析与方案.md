# Event 页报表导出功能分析与实施方案

> 撰写日期：2026-09-05。本文回答四个问题：主流工业软件的报表导出是怎么做的、需要哪些信息、导出形式（Excel 内部结构）是什么样、以及当前 TDengine 字段是否够用；最后给出本项目的落地实施方案。

---

## 1. 结论摘要（TL;DR)

1. **业界主流做法**：工业软件（SCADA/HMI/实时库平台）的报表导出以 **Excel (.xlsx) 为绝对主流**，CSV 用于系统间数据交换，PDF 用于正式交付文档。触发方式分两类——**"所见即所得"的即时导出**（当前查询结果直接落 Excel，Ignition / WinCC / PI Vision 均是如此）与**"模板渲染"的周期性报表**（日报/班报/月报，力控、中控 InPlant 等国产平台常见）。
2. **Excel 里包含各种报表吗？** 是的，工业惯例是**一个工作簿多个 Sheet**：Summary（统计汇总 + KPI）/ Detail（明细行）/ 按维度分组的透视表。报警类报表的内容口径有国际标准可依——**ISA-18.2**（报警系统性能 KPI：平均报警率、报警洪水、陈旧报警、确认时长、Top 10 报警源、优先级分布）。
3. **字段充足性**：当前 `alarm_event` / `alarm_realtime` 超表字段**足以支撑"明细清单导出"（一期）**，只需在查询层增加时间范围与过滤参数；**"统计汇总报表（二期）"大部分可算出来**，但有三个可推导的弱项（恢复时刻、确认时刻、报警时长均为字符串/无独立列）与两个需关联 MySQL 补齐的维度（单位 unit、设备显示名/责任人）。**不需要动 TDengine 表结构**即可开工。
4. **一个必须注意的现状**：`alarm_event` 存的是**全部处理帧（含正常帧）**，不只是报警帧（`monitor-flink-job/.../AlarmProcessFunction.java` 中 normal 分支也调用 `publishPersistenceStream`）。因此导出接口**必须强制时间范围**并默认只导报警帧，否则是无界导出（7 天窗口实测 68 万行量级）。
5. **技术选型**：项目已有 **Apache POI 5.2.5** 依赖（`TemplateService` 模板导入在用），导出直接用其 **SXSSF 流式写**（滑动窗口写磁盘临时文件，内存恒定），**无需引入新依赖**；分页循环读 TDengine + 分批写 Excel + 直接写响应流，50 万行以内同步导出没问题。

---

## 2. 业界调研：主流工业软件怎么做报表导出

### 2.1 代表性平台的实现方式

| 平台 | 实现方式 | 输出形式 |
|---|---|---|
| **AVEVA PI System** | Event Frames（事件帧）封装"事件时段 + 资产数据"，通过 PI Vision 自定义符号导出、CSV 导出或 BI 工具（Excel/Power BI）集成取数 | CSV / Excel / BI 报表 |
| **Ignition SCADA** | 内置 Reporting Module 拖拽设计报表，脚本 `system.dataset.exportExcel` 导出多 Sheet Excel；社区惯用 Python 脚本 + 定时任务邮件发送 | Excel（多 Sheet）/ PDF |
| **Siemens WinCC / PCS 7** | Runtime 侧 VBScript 自动化导出 CSV/Excel；企业级方案用 Information Server + Process Historian 的 Web 报表模板 | CSV / Excel / Web 报表 |
| **力控 / 中控 InPlant / 台达 DIAView（国产）** | 平台自带"报表套件/Excel 模式报表"：Excel 模板导入 → 数据源绑定（实时库 + 关系库）→ 渲染 → 导出/定时生成，典型场景即污水处理、电力监控的**日报、班报、值班报表** | Excel（模板渲染）/ 打印 |

共性规律：

- **即时导出**（把当前表格/查询结果交给用户）是所有平台的标配，格式就是用户看到的列；
- **周期性统计报表**走"模板 + 定时"路线，数据源同时接**实时库（时序数据）与关系库（元数据）**——这正是本系统 TDengine + MySQL 的双库结构；
- 没有哪家把"导出"做成全内存一次性生成，都是**分页/游标拉取 + 流式写出**。

### 2.2 报警报表的内容口径：ISA-18.2

报警/事件类报表的行业基准是 ISA-18.2（EEMUA 191 同源），其定义的 KPI 正是"汇总 Sheet"该放什么：

| 指标 | 参考目标 |
|---|---|
| 平均报警率（每操作员） | ≤ ~150 条/天（约 6 条/小时） |
| 报警洪水（>10 条/10 分钟）的时间占比 | ≤ 1% |
| 陈旧报警（持续 >24h）占比 | ≤ 5% |
| 抖动报警（1 分钟内 ≥3 次状态变化） | Top 10 清单人工治理 |
| 优先级分布 | 低/中/高 ≈ 80% / 15% / 5% |
| 平均确认时长、Top 10 报警源、峰值时段分布 | 常规展示项 |

明细清单的典型列（业界通用）：**报警时间、恢复时间、持续时间、位号/点位、报警值、限值、级别、严重度、报警消息、位置/系统、确认状态、确认人、确认时间**。

### 2.3 导出形式小结

- **xlsx 明细**：与页面表格同列，可冻结表头、自动列宽、按 severity 着色；
- **xlsx 统计报表**：多 Sheet 工作簿（Summary + Detail + 分组透视），值班/日/月报为模板渲染式；
- **CSV**：大数据量交换、二次分析用，无样式；
- **PDF**：正式交付件，SCADA 圈少见（Ignition Reporting 有），优先级最低。

---

## 3. 项目现状盘点

### 3.1 Event 页与数据链路

- 前端 `frontend/src/views/AlarmEventView.vue`：展示 `alarm_event` 数据，列为 Time / Value / Stage / Severity / Source / Message / Location / Status / Duration / Ack. By；**滚动分页**（50/页，进入页面时锁定 snapshot `topTs`），**当前没有任何过滤条件**（无时间范围/级别/来源筛选），仅一个 Refresh 按钮。
- 接口 `GET /api/alarms/events`（`AlarmController.getAlarmEvents`）→ `TdengineSyncService.queryAlarmEvents(topTs, offset, limit)`：按写入时间 `ts DESC` 取数，`query-window-days: 7` 限定最近 7 天窗口（`application.yaml`），单页上限 500 行。同构接口 `GET /api/alarms/cleared` 读 `alarm_realtime`。
- **数据范围**：`alarm_event` 是"全量帧历史"——Flink 对每个处理过的数据帧（正常帧、无规则帧、string 类型帧均包括，见 `AlarmProcessFunction.java:243-276`）都发往 `alarm:stream:persist` 并落 TDengine；报警行带 `alarm_level`/`alarm_severity`/`alarm_stage`，正常行这些列为 NULL。`alarm_realtime` 只存报警生命周期事件（start / continue / 升级 / end / ack）。
- **确认（Ack）流转**：`AlarmService.acknowledgeAlarms` 会把确认事件作为一条 `stageMessage='Ack'`、`data_timestamp=确认时刻`、`status='Ack'` 的记录**追加写入两张超表**（`AlarmService.java:203-210`）——即**确认时刻在 TDengine 里是可推导的**，只是没有独立列。

### 3.2 TDengine 超表结构（两表同构，`TdengineSyncService.initSupertable`）

| 列 | 类型 | 报表可用性 |
|---|---|---|
| ts | TIMESTAMP（主键，写入时间，有索引） | ✅ 时间范围过滤、排序、分页 |
| data_timestamp | TIMESTAMP（数据时间） | ✅ 业务时间；End 行 = 恢复时刻，Ack 行 = 确认时刻 |
| point_value | NCHAR(64) | ✅ 报警值 |
| point_type | NCHAR(32) | ✅ |
| alarm_level | NCHAR(32) | ✅ 阈值级别（High/Low…），NULL=正常帧，可作"只导报警"过滤 |
| alarm_severity | NCHAR(32) | ✅ 分级统计 |
| alarm_message | NCHAR(512) | ✅ |
| time_delay / deadband | NCHAR | ✅ 规则参数（字符串，展示够用） |
| ack_by | NCHAR(32) | ✅ 确认人 |
| alarm_status | NCHAR(32) | ✅ Unack/Ack |
| alarm_source / alarm_location | NCHAR(256/128) | ✅ 由 path 派生的来源/位置，分组维度 |
| alarm_stage / alarm_stage_message | NCHAR | ✅ 生命周期阶段 |
| alarm_duration | **NCHAR(32)（秒数字符串）** | ⚠️ 展示可用；聚合需解析或重算 |
| alarm_start_time | TIMESTAMP | ✅ 报警起始时刻，配对键 |
| TAG: path | NCHAR(256) | ✅ 点位唯一键，PARTITION BY 维度 |

数据库保留 3 年（`KEEP 1095`），查询窗口 7 天（`query-window-days`，仅作用于现有历史查询）。

### 3.3 可用的既有资产

- **POI 5.2.5 已在 `monitor-backend/pom.xml`**（模板导入读取用）→ 导出零新增依赖；
- MySQL 侧元数据可富化报表：`device`（device_name、department、responsible_person、loc_path）、`monitor_point`（**unit**、point_name）、`point_deployment`（n4_address ↔ path 的桥接）；TDengine 瘦身时移除了 unit/设备名列，**这些维度要回 MySQL join**；
- 统计端点 `/summary`、`/by-severity`、`/trend`、`/by-hour`、`/by-system`、`/top-sources` 已存在，但都基于 **Redis 当前活跃快照**，不是历史统计——历史统计报表的数据要从 TDengine 现算。

---

## 4. 字段充足性评估

### 4.1 结论：一期够用，二期有两个可控缺口

| 报表内容 | 所需数据 | 现状 |
|---|---|---|
| 明细清单导出（所见即所得） | 全部列均已在 `alarm_event` | ✅ **完全够用**，只缺查询层的过滤参数 |
| 只导报警帧 | `alarm_level IS NOT NULL` | ✅ 可过滤（实现时以特征测试钉死 End/Ack 行的 level 取值） |
| 报警持续时间统计 | duration 或 起止时刻 | ⚠️ duration 是秒字符串；End 行可重算：`data_timestamp - alarm_start_time` |
| 确认时长 KPI | 确认时刻 | ⚠️ 无独立列；Ack 行的 `data_timestamp` 即确认时刻，与 Start 行按 `path + alarm_start_time` 关联可算 |
| 按严重度/来源/位置/小时分布 | severity / source / location / ts | ✅ TDengine `GROUP BY` / `interval()` 直接算 |
| 报警洪水、陈旧报警、抖动 | 逐条时刻序列 | ✅ `alarm_realtime` 逐条时刻齐全，Java 侧滑窗统计 |
| 单位（unit）、设备显示名、责任人 | MySQL 元数据 | ⚠️ 需按 `path → point_deployment.n4_address → device/monitor_point` 关联补齐 |
| 报警限值（阈值）原文 | 规则表 | ⚠️ TDengine 未存触发阈值，需回 MySQL `alarm_rule_numeric/enum` 按 `alarm_name` 取（Redis `alarm:rules:*` 也有） |

### 4.2 不建议现在改表的三个理由

1. "恢复时刻/确认时刻/时长"都能从现有行**推导**，改表（加 `alarm_end_time`、`ack_timestamp`、`duration` 改 BIGINT）要同时动 Flink 写入、`TdengineSql`、两张超表 DDL、历史数据不回填，成本远超收益；
2. 唯一强诉求的元数据（unit、设备名）本就属于关系库，业界惯例也是"时序库存事实、关系库存维度"，导出时 join 是正解；
3. 若未来确认时长 KPI 成为高频需求，再加 `ack_timestamp TIMESTAMP` 一列即可（TDengine 加列 `ALTER STABLE ... ADD COLUMN` 对现有数据无损），届时 Flink ack 路径与后端 ack 路径各补一个字段写入。

---

## 5. 实施方案

### 5.1 总体规划

| 阶段 | 交付物 | 规模 |
|---|---|---|
| **一期** | Event 页筛选器 + "所见即所得" xlsx 导出（可含报警帧过滤） | 1 个接口 + 1 个 Service + 前端筛选区/导出按钮 |
| **二期** | 报警统计报表（多 Sheet 工作簿，ISA-18.2 裁剪版 KPI），入口放在 Event 页或独立的 Report 菜单 | 1 个聚合查询集 + 报表装配器 |
| **远期** | 定时日报（`@Scheduled` 生成并落盘/邮件）、异步导出任务中心（百万行级再做） | 另立专项 |

### 5.2 一期：Event 页明细导出

**后端**

- 新增 `GET /api/alarms/events/export`，参数：

  | 参数 | 说明 |
  |---|---|
  | `start` / `end` | **必填**，epoch millis，按写入时间 `ts` 过滤（走主键索引，性能可控；UI 标注"Record Time"）。同时钳制在 `KEEP` 范围内 |
  | `severity` | 可选，`Critical/Major/Minor/Warning`（可多值） |
  | `source` / `location` / `keyword` | 可选，`LIKE` 匹配 source / location / message |
  | `alarmOnly` | 可选，默认 `true`：追加 `alarm_level IS NOT NULL`，只导报警相关帧 |
  | `format` | 可选，`xlsx`（默认）/ `csv`，CSV 供大数据量/二次分析 |

- `TdengineSyncService` 新增流式导出查询：**时间正序（ASC）+ LIMIT/OFFSET 分页循环**（每页 5000，复用"同毫秒多行用 OFFSET 防跳页"的既有经验），每页回调写入 Excel，内存恒定；不复用 `queryWindowDays` 限制（导出有显式范围）。
- 导出前先 `SELECT COUNT(*)` 预检：超过 `alarm.export.max-rows`（建议默认 50,000，可配）直接返回 400 + 总数提示，让用户缩小范围，不做静默截断。
- Excel 生成用 **`SXSSFWorkbook`**（窗口 100~200 行）：单 Sheet `Events`，冻结首行、`trackColumnForAutoSizing` 自动列宽、severity 单元格按项目色板着色；列 = 页面列 + `Level` + `Alarm Start Time` + `Duration (s)`（原始秒数列，便于 Excel 透视）；文件名 `n4_events_20260905_153000.xlsx`，`Content-Type: application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`，直接写响应流（不落盘、不进字节数组）。
- 响应是文件流而非 `ApiResponse` JSON：`GlobalExceptionHandler`/前端拦截器需放行 blob（前端 `request.get(..., { responseType: 'blob' })`，错误时按 JSON 解析展示）。

**前端（AlarmEventView.vue）**

- 头部加筛选区：`el-date-picker`（范围，默认最近 24h）、Severity 多选、Source 输入、"Alarm only" 开关；筛选条件**同时作用于页面查询**（后端 `/events` 接口补同名可选参数，向后兼容）与导出；
- `Export` 按钮：带 loading 的 blob 下载（`URL.createObjectURL` + `<a download>`），成功提示导出行数。

### 5.3 二期：报警统计报表（多 Sheet 工作簿）

新增 `GET /api/alarms/report/export?start=&end=`，生成的工作簿结构（对齐 2.2 节口径，按需裁剪）：

| Sheet | 内容 | 数据来源 |
|---|---|---|
| `Summary` | 报表周期、报警总次数（start 行计数）、按严重度分布与占比、按位置/系统分布、按小时分布（找峰值时段）、平均/最长持续时长、平均确认时长、未确认占比、ISA-18.2 参考线对照（报警率、洪水次数、陈旧报警占比） | TDengine 聚合 SQL（`GROUP BY alarm_severity/alarm_source`、`interval(1h)`）+ Java 侧滑窗（洪水/陈旧） |
| `Alarm Detail` | 报警生命周期明细：Start 时刻、End 时刻、持续、Level、Severity、Source、Location、Message、Status、Ack By、确认时长 | `alarm_realtime` 按 `path + alarm_start_time` 配对 Start/End/Ack 行（Java 侧归并） |
| `Top Sources` | 按来源的次数、累计持续、平均持续 Top N 排序 | TDengine `GROUP BY alarm_source ORDER BY count DESC` |
| `Rules Reference`（可选） | 触发阈值/死区/延迟原文 | MySQL `alarm_rule_numeric` / `alarm_rule_enum` |

维度富化（设备名、unit、责任人）统一在 Java 侧做一次 `path → point_deployment → device/monitor_point` 批量映射后带入各 Sheet；POI 画原生图表成本高，一期/二期统计 Sheet 用数字表格呈现即可（前端 ECharts 已有同类图，报表以表格为主符合值班打印场景）。

### 5.4 关键实现注意点

1. **`alarm_level IS NOT NULL` 过滤**需特征测试钉死 End/Ack 行的 level 写入行为（均为报警期间写入，level 应非空），避免"只导报警"漏行；
2. 同毫秒批量写入的翻页问题沿用现有结论——**OFFSET 分页**而非 `ts <` 游标（`TdengineSyncService.queryAlarmEvents` 注释已论证）；
3. SXSSF 自动列宽只能对跟踪列生效，列数少（≤14）直接 `trackColumnForAutoSizing` 全开，内存可忽略；
4. 导出接口要挂与查询一致的超时/并发保护：同一时刻限 1 个导出任务（简单信号量即可），防止多用户同时拉大范围；
5. 时间语义在 UI 上明确标注"Record Time（写入时间）"；断链回放场景写入时间 ≠ 数据时间，若需要按数据时间导出，二期在 `ts` 范围裁剪之上再叠加 `data_timestamp` 精确过滤即可（代价可忽略）。

---

## 6. 决策点（需要用户确认）

1. **表头语言**：项目 UI 与代码均为英文，报表表头默认跟随英文；若最终用户是中文运维人员，可在列头映射层一次性切换（建议留常量表）。
2. **一期导出范围语义**：默认 `alarmOnly=true`（只导报警相关帧）还是包含正常数据帧？建议默认报警帧、开关可切。
3. **CSV 是否一期就要**：若只是运维临时分析，Excel 足够，CSV 可延后。

---

## 7. 参考资料

- [ISA InTech: From alarm floods to highly protected status](https://www.isa.org/intech-home/2021/august-2021/features/from-alarm-floods-to-highly-protected-status)
- [Alarm System Performance Metrics (ISA Ireland, Kim Van Camp)](https://isa.ie/wp-content/uploads/2016/06/Alarm_System_Performance_Metrics_Kim_Van_camp.pdf)
- [Alarm Management by the Numbers - Chemical Engineering](https://www.chemengonline.com/alarm-management-numbers/)
- [Alarm Management in SCADA: ISA-18.2 Implementation Guide](https://ifactoryapp.com/blog/alarm-management-scada-isa-18-2)
- [Quality vs. quantity of alarm messages - ScienceDirect](https://www.sciencedirect.com/science/article/pii/S0263876221002653)
- [AVEVA: Understand event frames in PI AF](https://docs.aveva.com/bundle/pi-server-l-af-pse/page/1021923.html) / [Export data from a display (PI Vision)](https://docs.aveva.com/bundle/pi-vision/page/1010132.html)
- [Ignition 论坛: Export data into excel（多 Sheet 场景）](https://forum.inductiveautomation.com/t/export-data-into-excel/5317) / [Export Multiple Tables to Separate Excel Sheets](https://industrialmonitordirect.com/blogs/knowledgebase/export-multiple-tables-to-separate-excel-sheets-in-ignition)
- [WinCC Excel Export: VBScript Data Reporting Tutorial](https://industrialmonitordirect.com/blogs/knowledgebase/wincc-excel-export-vbscript-data-reporting-tutorial) / [Siemens Information Server 报表模板](https://support.industry.siemens.com/cs/document/64906050/)
- [力控科技：国产化 SCADA 报表套件在污水处理系统中的应用](http://www.sunwayland.com/answer-info/509.html) / [中控 InPlant SCADA 报表应用案例](https://zhuanlan.zhihu.com/p/696187602) / [台达 DIAView SCADA](https://www.delta-china.com.cn/zh-CN/products/DIAView-SCADA-System/4464)
- [Apache POI SXSSF 流式写出 - Baeldung](https://www.baeldung.com/jdbc-resultset-excel) / [超大规模数据导出 Excel 的实现方法（分页 + 流式 + 异步）](https://yanglei.ltd/archives/gEZtR6Qw)
