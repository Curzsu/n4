# N4-Monitor Flink 报警处理全流程开发指南

> 面向新开发者的深度业务逻辑梳理：从设备数据接入、规则评估、告警生命周期，
> 到前端实时展示与历史持久化的完整链路，以及每一步涉及的中间件及其具体作用。
>
> 相关阅读：`docs/系统架构设计文档.md`（整体架构概览）、`deploy/部署文档.md`（部署细节）、
> `docs/data-structure-dictionary.md`（数据结构字典）。

---

## 一、端到端数据链路总览

真实生产环境的数据链路如下（开发调试时另有 `test-producer` 测试脚本绕过设备/EMQX 直发 Kafka）：

```
现场设备
  │  MQTT 上报（topic: niagara_send/#）
  ▼
EMQX（MQTT Broker 6.2.1）
  │  规则引擎（rule: mqtt_to_kafka）
  │   + Kafka 桥接（action: push_kafka, connector: n4-kafka-producer）
  ▼
Kafka（topic: mqtt-push-kafka）
  │  Flink 消费（SimpleStringSchema，group: n4-flink-alarm-processor）
  ▼
Flink（AlarmProcessingJob，内嵌于 Spring Boot 或独立集群）
  │  ParsePointFunction → keyBy(mqttPath) → AlarmProcessFunction（规则评估 + 状态机）
  ▼
Redis
  ├─ Hash: alarm:latest   实时值（每帧覆盖）
  ├─ Hash: alarm:active   当前告警记录 + ack 状态
  └─ Pub/Sub: alarm:notifications / alarm:realtime / alarm:persist
        │
        ├─ alarm:notifications → Spring 监听 → WebSocket(/ws/alarm) → 前端大屏
        ├─ alarm:realtime     → Spring 监听 → TDengine alarm_realtime（告警生命周期事件）
        └─ alarm:persist      → Spring 监听 → 缓冲批量 → TDengine alarm_event（每帧全量明细）
```

**各中间件的定位（一句话）：**

| 中间件 | 在报警链路中的具体作用 |
|--------|----------------------|
| EMQX | 设备 MQTT 接入端点；规则引擎把 `niagara_send/#` 的消息通过 Kafka 桥接转发到 Kafka topic |
| Kafka | 消息缓冲与解耦：削峰、多消费者复用同一份数据、Flink 与后端解耦 |
| Flink | 实时规则评估与告警生命周期状态机（**唯一的规则判定者**） |
| Redis | 规则缓存（MySQL 同步而来）、实时状态（latest/active）、跨进程 Pub/Sub 通信 |
| MySQL | 规则与点位元数据的持久化存储（`alarm_rule_numeric` / `alarm_rule_enum` / `point_deployment`） |
| TDengine | 告警生命周期历史（`alarm_realtime`）与每帧全量事件明细（`alarm_event`）的时序持久化 |
| Spring Boot 后端 | 启动时同步规则到 Redis、内嵌启动 Flink、消费 Redis 通知、推送 WebSocket、提供 REST API、ack 操作 |

---

## 二、Kafka 消息格式（输入）

Flink 从 Kafka topic `mqtt-push-kafka` 消费的每条消息是**纯 JSON 字符串**（Kafka 仅取 value），
格式固定为五部分：

```json
{
  "clientId": "clientId_1001",
  "mqttPath": "A1_MVP_A1/MVP_A1/MVP_A1_02_IRC_00/Dry_Mode",
  "path": "slot:/Drivers/A1_MVP_A1/MVP_A1/points/MqttNetwork/MVP_A1_02_IRC_00/Dry_Mode",
  "timestamp": 1730000000000,
  "value": "25.6"
}
```

- **`mqttPath`**：点位的唯一标识，贯穿整个报警链路——Flink `keyBy` 分区键、
  Redis `alarm:latest` / `alarm:active` 的 hash 字段、TDengine 的 subtable 命名。
  它对应 MySQL `point_deployment.n4_address`。
- **`timestamp`**：毫秒时间戳，由上游在发送时打上（约等于 wall clock）。
- **`type` 不再由消息携带**：点位类型（numeric/enum/boolean/string）由 Flink 在运行时
  从 Redis `alarm:point:rule` 元数据映射解析（该映射来自 MySQL `point_deployment`）。

> **开发者注意**：`SimpleStringSchema` 只读 value，Kafka 记录的 key 不被 Flink 使用。
> 分区（partitioning）由下游 `keyBy(mqttPath)` 完成，而非依赖 Kafka 分区。

---

## 三、规则从哪来：MySQL → Redis 同步

**Flink 本身不读 MySQL**。规则与点位元数据由 Spring Boot 后端从 MySQL 读取后
同步到 Redis，Flink 每次处理消息时从 Redis 读取。这是 Flink（独立运行）与
Spring（依赖注入）解耦的关键设计。

### 3.1 同步时机

- **应用启动时**：`AlarmRuleInitializer`（`@Order(0)`）先于 Flink 启动（`@Order(20)`）
  执行 `syncAllRulesToRedis()` + `syncPointRuleMapToRedis()`，重试 3 次（间隔 3s），
  保证 Flink 打开时规则已就绪。若最终失败会打 ERROR 日志（规则缺失时报警链路静默，必须显式暴露）。
- **规则 CRUD 后**：`AlarmRuleCrudService` 在同一事务内重新同步对应规则类型。
- **手动**：`POST /api/alarm-rules/reload` 强制全量同步。

### 3.2 Redis 中的三张规则 Hash

| Redis Key | 类型 | field | value |
|-----------|------|-------|-------|
| `alarm:rules:numeric` | Hash | alarm_name | `AlarmRuleNumericEntity` JSON（一条） |
| `alarm:rules:enum` | Hash | alarm_name | `AlarmRuleEnumEntity` JSON 数组（一组，布尔规则已并入枚举） |
| `alarm:point:rule` | Hash | 点位 mqttPath | `{"type":"numeric|enum|boolean|string","alarm_code":"..."}` |

同步采用**临时 key + `RENAME` 原子替换**（`atomicReplaceHash`）：读者（Flink）永远看到
完整的旧映射或完整的新映射，避免出现删了再写的空窗。

### 3.3 点位元数据映射（alarm:point:rule）

- 数据来源：`SELECT point_deploy_id, n4_address, alarm_code, type FROM point_deployment WHERE is_deleted = 0`。
- `n4_address` = mqttPath；`alarm_code` 是规则在两张规则表里的名称；
  `alarm_code` 为空 = 该点位**无规则**，Flink 会跳过评估（不报警）。
- `type` 与 `alarm_code` 取自同一行，保证一致性。

### 3.4 Flink 规则加载的本地缓存（TtlCache）

Flink **不直接读 MySQL**，也不每帧都查 Redis：规则与点位元数据在 Redis 前面还有一层 **subtask 本地 TTL 缓存**，由 `TtlCache`（`monitor-flink-job`）实现。完整链路是：

```
MySQL ──启动/CRUD时同步──> Redis hash ──TTL 到期回源──> subtask 本地 TtlCache ──每帧查询──> 规则评估
```

**每个 subtask 独立持有三个缓存实例**（在 `open()` 中创建，随 task 生命周期）：

| 缓存 | 所属类 | 缓存内容 | 回源 Redis Key |
|------|--------|---------|---------------|
| `pointMetaCache` | `AlarmProcessFunction` | 点位元数据（type + alarm_code） | `alarm:point:rule` |
| `numericRuleCache` | `RuleEvaluator` | 数值规则对象 | `alarm:rules:numeric` |
| `enumRuleCache` | `RuleEvaluator` | 枚举规则列表 | `alarm:rules:enum` |

**TTL（缓存过期时间）为可配置项**：通过 `alarm.flink.point-meta-cache-ttl-seconds`（点位元数据）与 `alarm.flink.rule-cache-ttl-seconds`（规则对象）配置——内嵌模式走 `application.yaml` / 环境变量，独立集群模式走 `--point-meta-cache-ttl-seconds` / `--rule-cache-ttl-seconds` 命令行参数，默认均为 60 秒。下文统一以"TTL 窗口"指代该时长，具体数值以实际配置为准。

**工作机制：**

- 命中：条目存在且未过期，直接返回，零 Redis 访问。
- 回源：条目缺失或过期时，通过加载函数执行一次 `HGET`，结果写入缓存并重置 TTL 窗口。
- **负缓存**：查不到的规则/路径，`null` 也会被缓存（包装为哨兵对象存入 `ConcurrentHashMap`）。探测一个未知 alarm_code 或未部署的点位，每个 TTL 窗口只花费一次 Redis 往返，而不是每帧一次。
- **分片效应**：流按 `keyBy(mqttPath)` 分区，一个 subtask 只会加载路由到它的那些点位，所以 `pointMetaCache` 天然是 Redis 映射的"分片副本"，容量上界 = 总点位数 / 并行度。规则缓存本身很小（几百条），无需淘汰策略。
- **线程模型**：Flink 的 `processElement` 与 `onTimer` 由同一 mailbox 线程调用，缓存按单线程设计；底层仍用 `ConcurrentHashMap` 兜底（防御性并发，无额外成本）。

**对规则变更的影响（运维要点）：**

- 规则编辑后端会即时同步 Redis（原子替换），但 Flink subtask 仍持有最长一个 TTL 窗口的旧缓存 → **规则变更生效延迟最多一个 TTL 窗口（当前默认 60s），无需重启 Flink**。
- 需要立即生效时调用 `POST /api/alarm-rules/reload` 重新同步 Redis，再等当前 TTL 窗口耗尽（或重启作业）。
- 注意：TTL 从**加载时刻**起算，不是全局对齐的时钟，不同 subtask/不同 key 的过期时刻各不相同。

---

## 四、Flink 作业拓扑（Job Topology）

Flink 作业入口为 `com.n4monitor.flink.AlarmProcessingJob`，有**两种启动方式**：

| 模式 | 启动方式 | 配置来源 |
|------|---------|---------|
| **内嵌模式**（默认部署） | `FlinkJobRunner`（`@Order(20)` CommandLineRunner）在 Spring Boot 启动时于后台守护线程 `flink-alarm-thread` 调用 `AlarmProcessingJob.run(...)` | `application.yaml` / 环境变量 |
| **独立集群模式** | `flink run -c com.n4monitor.flink.AlarmProcessingJob monitor-flink-job-*-standalone.jar --kafka-broker ... --kafka-topic ...` | 命令行参数（`ParameterTool`） |

两种模式**互斥**：它们消费同一个 topic、同一个消费组，同时运行会把分区瓜分导致
告警随机丢失。docker-compose 用 `--profile flink` 守卫独立集群，默认只跑内嵌模式。

### 4.1 拓扑的四个阶段

```
KafkaSource<String>                    # 从 mqtt-push-kafka 消费，OffsetsInitializer.latest()
   │  WatermarkStrategy.forBoundedOutOfOrderness(3s)
   ▼
flatMap(ParsePointFunction)            # 阶段1：JSON → PointData；畸形消息丢弃
   ▼
keyBy(PointData::getMqttPath)          # 阶段2：按点位路径分区，同点位路由到同一 task
   ▼
process(AlarmProcessFunction)          # 阶段3：规则查找 + 生命周期状态机 + 延迟定时器
   ▼
print()                                # 阶段4：仅输出 stdout 日志；真正的输出是 Redis 副作用写入
```

### 4.2 关键配置（`monitor-backend/src/main/resources/application.yaml`）

```yaml
alarm:
  flink:
    enabled: ${ALARM_FLINK_ENABLED:true} # 报警作业总开关；本地开发不碰报警链路可在 .env 置 false（不连 Kafka、不做评估）
    topic: ${ALARM_FLINK_TOPIC:mqtt-push-kafka} # 消费 topic；联调可覆盖为 kafka-test，独立集群 --kafka-topic 同读此变量自动保持一致
    group-id: n4-flink-alarm-processor
    parallelism: ${ALARM_FLINK_PARALLELISM:4} # 必须 ≤ min(CPU 核数, Kafka topic 分区数)；内嵌与独立集群 --parallelism 同读此变量
    point-meta-cache-ttl-seconds: 60 # 点位元数据本地缓存 TTL
    rule-cache-ttl-seconds: 60       # 数值/枚举规则本地缓存 TTL
  redis:
    rules-numeric-key: alarm:rules:numeric
    rules-enum-key: alarm:rules:enum
    latest-key: alarm:latest
    active-key: alarm:active
    notification-channel: alarm:notifications
    realtime-channel: alarm:realtime
    persistence-channel: alarm:persist
    point-rule-map-key: alarm:point:rule
```

> **并行度正确性**：Kafka 源并行度受 topic 分区数约束；下游 `keyBy(path)` 保证同一
> 点位的所有消息落到同一 task，因此 `alarm:active` 上的读-改-写在该 key 内是串行的，
> 不会并发竞争。Kafka topic 分区数应 ≥ 配置的并行度，否则部分 task 闲置。

---

## 五、阶段 1：数据解析（ParsePointFunction）

- 无状态 `RichFlatMapFunction<String, PointData>`，完全可并行。
- 用 `flatMap`（而非 `map`）：JSON 解析失败或 `mqttPath` 为空的畸形消息被**静默丢弃**，
  不产出哨兵值，保证下游永远看不到坏记录。
- 解析失败打 warn 日志，消息计数仍继续。

---

## 六、阶段 3：规则评估（RuleEvaluator）

`AlarmProcessFunction` 在处理每条消息时，先解析点位元数据（走 per-subtask 的本地 TTL 缓存，详见 3.4），再按类型分发到 `RuleEvaluator`。

### 6.1 点位类型分发与过滤

| 场景 | 处理 |
|------|------|
| `string` 类型 | 无报警规则，只写 latest + persist，跳过评估 |
| `alarm_code` 为空 | 无规则，写正常状态，跳过评估（不报警） |
| 未知类型 | 写正常状态，跳过评估 |
| 在 Redis 中找不到规则 | 跳过评估 |
| 枚举值未匹配任何规则（`skipped=true`） | **只刷新 latest，不触碰 active，不进状态机**——否则未知值会被当作正常值，错误地恢复进行中的告警 |

### 6.2 数值型规则评估（evaluateNumeric）

`alarm:rules:numeric` 中一条规则最多四档阈值，**按优先级从上到下，先命中即返回**：

```
High-High  ≥ 高高限   → 报警级别 High-High
High       ≥ 高限     → 报警级别 High
Low-Low    ≤ 低低限   → 报警级别 Low-Low
Low        ≤ 低限     → 报警级别 Low
均未命中               → 正常（但保留 timeDelay / deadband 供迟滞判断）
```

- 每档阈值携带独立的 `severity`（Critical / Major / Minor / Warning）与 `message`。
- 值用 `Double.parseDouble` 解析；无法解析按正常处理。
- 未配置或格式错误的阈值档会被跳过并打 warn 日志（不再静默失败）。

### 6.3 枚举型规则评估（evaluateEnum）

- 消息值与 `alarm:rules:enum` 中该 alarm_name 的一组枚举值做**大小写不敏感**匹配。
- 命中且 severity 非空 → 报警，**级别合成 `"Enum:" + 枚举值`**（例如 `Enum:0`、`Enum:2`），
  使不同枚举值在生命周期状态机里是不同的报警级别（否则级别相同会被误判为 continue）。
- 命中但 severity 为空 → 显式正常（该枚举值是正常状态）。
- 无匹配 → `skipped=true`，只刷新 latest（见 6.1）。

### 6.4 死区 / 迟滞（Deadband / Hysteresis）

仅对**数值型**点位生效，且仅在「当前值不再触发原始阈值（isAlarm=false）、但告警仍激活」
时启用。作用：防止阈值边界抖动导致告警反复恢复/触发。

```
高侧（High / High-High）：值 ≥ 阈值 - deadband 时告警保持，值 < 阈值 - deadband 才恢复
低侧（Low / Low-Low）：  值 ≤ 阈值 + deadband 时告警保持，值 > 阈值 + deadband 才恢复
```

- deadband 为空 / 为 0 / 无法解析 → 不启用迟滞（向后兼容）。
- 处于死区内时，告警按历史级别/severity/message 保持，`isAlarm` 被强制置回 true。

---

## 七、阶段 3：告警生命周期状态机（AlarmProcessFunction）

这是整个报警链路的核心。`KeyedProcessFunction<String, PointData, String>`，每个
`mqttPath` 一份状态。生命周期基于 Redis `alarm:active` 中的历史记录（`history`）演进。

### 7.1 状态机总览

```
                     ┌───────────────────────────────────────────────┐
                     │                                               │
                     ▼                                               │
   ┌─────────┐  isAlarm  ┌──────────┐  isAlarm(同级别)  ┌───────────┐│
   │(无记录)  │ ───────▶ │  start   │ ───────────────▶ │ continue  ││
   └─────────┘           └────┬─────┘                  └─────┬─────┘│
                              │                              │      │
              isAlarm(升级,如 High→High-High)                │      │
              关闭旧阶段(end) + 开启新阶段(start)             │      │
                              │                              │      │
                              │                    恢复正常  │      │
                              │                     │        │      │
                              │                     ▼        │      │
                              │              ┌─────────┐     │      │
                              └────────────▶ │   end   │─────┘      │
                                             └─────────┘            │
                                             已恢复                │
                                             再次 isAlarm ──────────┘ (重新 start)
```

### 7.2 各分支的详细行为（processMessage 328–421 行）

| 历史状态 | 当前判定 | 行为 |
|---------|---------|------|
| 无记录 / 历史级别为空 | isAlarm 且 timeDelay>0 | 进入 pending 延迟确认（见 7.4） |
| 无记录 / 历史级别为空 | isAlarm 且无延迟 | `markAlarmStart`（stage=start, status=Unack, duration=0）+ 写 active + 发 notification + 发 realtime |
| 历史为 `end`（冻结恢复记录） | isAlarm | 恢复后再次报警 = **新的一轮 start**，丢弃冻结记录（同样走延迟规则） |
| 进行中（start/continue） | 同级别 | **continue**：保留 ack 状态/ackBy，duration= 到当前帧的秒数，沿用原始 alarmStartTime |
| 进行中 | 不同级别 | **升级**：关闭旧阶段（end，stageMessage=`End - High to High-High`，时间戳=当前帧）发 realtime+persist；再对新级别 `markAlarmStart` 发 notification+realtime |
| 进行中 | 已恢复 且 已 Ack | 从 active 移除 + 发 notification（清除信号）+ realtime + persist |
| 进行中 | 已恢复 但 未 Ack | **冻结**：stage=end 写回 active（仍显示）+ 发三个通道 |

### 7.3 记录写入时间戳规则（重要）

- **`alarm:active`**：序列化时**强制 timestamp = alarmStartTime**（历史开始时间），
  所以 continue / end 记录都保留告警开始的时刻。
- **`alarm:notifications`**：同样强制为 alarmStartTime，前端始终显示告警何时开始。
- **`alarm:realtime` / `alarm:persist`**：使用状态自身的时间戳；调用方（如 end 记录）
  需自行把观测时间戳写到 state 上再发布，保证两条通道时间一致。

### 7.4 延迟确认（Pending Alarm / 定时器）

当规则配置了 `timeDelay > 0`（告警触发后持续超阈值 N 秒才确认），Flink 不立即报警，
而是进入 pending 状态：

```
首帧超阈值 → 存 PendingAlarm 到 keyed state + 注册 processing-time 定时器
           （target = firstEventTime + timeDelay*1000，若已过去则取当前处理时间）
延迟窗口内：
  - 同级别继续来 → 刷新 value/timestamp，保留定时器，latest 仍写正常态（无级别）
  - 不同级别      → 取消旧定时器，重新计延迟
  - 恢复正常      → 取消定时器，清空 pending
定时器触发（onTimer）：
  → 确认报警：markAlarmStart（用确认时刻作为 alarmStartTime）
  → 一次性完成 5 次写入，时间戳统一为确认时刻：
     writeActiveState + notification + realtime + persist + latest
```

> **设计意图**：延迟窗口锚定在数据自身时间戳（事件时间）上，但**定时器用处理时间**
> （wall clock）——即使延迟窗口内不再有新数据到达，也假设值保持不变，到点即报警
> （符合规格「若无新数据，视为值保持，延迟后报警」）。

### 7.5 每帧必做：写 latest + persist

无论是否报警，`processMessage` 末尾（第 7 步）都会：
- `writeLatestState(jedis, current)` → 写 `alarm:latest`
- `publishPersistenceChannel(jedis, current)` → 发 `alarm:persist`（TDengine 全量明细）

---

## 八、输出落点（Flink 的「输出」）

Flink 作业**没有 Kafka sink**。所有结果通过 `RedisStateStore` 以 Redis 副作用写入：

| 目标 | 类型 | 内容 | 下游 |
|------|------|------|------|
| `alarm:latest` | Hash | 每帧覆盖的点位实时值 + 评估结果 | 实时显示、ack 覆盖 |
| `alarm:active` | Hash | 当前告警记录 + ack 状态（start/continue/frozen-end） | 前端活动告警表、聚合图表 |
| `alarm:notifications` | Pub/Sub | 告警生命周期事件（start/continue/升级/end/ack） | Spring → WebSocket → 前端 |
| `alarm:realtime` | Pub/Sub | 告警生命周期事件（同上） | Spring → TDengine `alarm_realtime` |
| `alarm:persist` | Pub/Sub | **每一帧**（无论是否报警） | Spring → 缓冲 → TDengine `alarm_event` |
| stdout | print() | 彩色控制台日志（ConsoleFormatter） | 运维排查 |

### 8.1 Flink 的 Redis 连接方式

- 使用 **Jedis 连接池**（maxTotal=8, testOnBorrow, testWhileIdle, 超时 5s），
  而不是裸连接——裸连接的 2s 默认超时对公网远程 Redis 太紧，且坏连接会被复用导致级联失败。
- 空密码规范化为 `null`：Jedis 7.x 对任何非空密码都会发 AUTH，在无密码 Redis 上会报错。
- **每个 subtask 维护规则/点位元数据的本地 TTL 缓存**（TTL 可配置，默认 60s，含负缓存）：
  规则编辑后无需重启 Flink，一个 TTL 窗口内生效（详见 3.4）。
- `processElement` 永不抛异常逃逸（否则会杀死整个 job）：catch 后打日志并输出 `[SKIP]`。

---

## 九、后端如何消费 Flink 的输出（Redis 监听 → 存储/推送）

Spring Boot 通过 `RedisMessageListenerContainer`（`RedisPubSubConfig`）订阅三个通道，
每个通道一个 `MessageListener`。**后端不消费 Kafka**。

### 9.1 alarm:notifications → WebSocket → 前端

`AlarmNotificationListener.onMessage`：

1. 解析触发告警的 `PointAlarmState` JSON（提取 mqttPath、stage 用于日志）。
2. 从共享快照 `ActiveAlarmStore.snapshot()` 聚合 summary / severityData / hourlyData。
3. 组装 JSON 信封（`type: "alarm_update"`），`AlarmWebSocketHandler.broadcast()` 广播到
   `/ws/alarm` 的所有连接。

```json
{
  "type": "alarm_update",
  "alarm": { "...PointAlarmState..." },
  "summary": {"critical": 2, "major": 1, "minor": 0, "warning": 3},
  "severityData": {"total": 6, "items": [...]},
  "hourlyData": {"hours": [...], "counts": [...]}
}
```

- 信封里的 `alarm` 字段**始终携带**（即使 active 为空——清除信号在记录已从 Redis 移除后才发）。
- 前端 `useAlarmWebSocket` composable 处理 `alarm_update`，更新共享 ref 并通知监听者；
  `AlarmTable.vue` 据此增/改/删行。
- 无连接客户端时打 warn（广播被跳过），便于区分「无订阅者」与「上游丢失」。

### 9.2 alarm:realtime → TDengine（即时写）

`AlarmRealtimeListener` → `TdengineSyncService.writeRealtimeAlarm(state)`：
- 走**独立 realtime 通道**（专属单线程 + 专属长连接）写 `alarm_realtime` 超表，**立即**
  写入（不经批量）；与 9.3 的批量事件通道完全隔离，大批 flush 再慢也不挤占报警写入。
- 只含告警生命周期事件（start/continue/升级/end/ack）。

### 9.3 alarm:persist → TDengine（批量写）

`AlarmPersistenceListener` → `TdengineSyncService.enqueuePersistence(state)`：
- 入**有界缓冲**（容量 5000，满则丢弃并 warn）。
- `flushPersistenceBatch()`（`@Scheduled` 每秒）提交给**独立 event 通道**（专属单线程 +
  专属长连接，等待队列只有 2——丢帧的缓冲是 persistenceBuffer 本身，不是排队任务）：
  连接就绪后才 `drainTo`，由 `TdengineSql` 按点位路径分组、拼成**多条多表多行 INSERT**
  （每条最多 300 行 / 250K 字符，组内 `VALUES (a)(b)` 元组并列）一次提交，
  `alarm_event` 的写入从「每帧一次 HTTP 往返」变为「每秒几次往返」，突发流量下
  写吞吐提升 1~2 个数量级。某条语句失败时仅对该分片回退为逐行写，定位坏行不丢整批。
- 每帧都写，`alarm_event` 是点位全量明细日志。

### 9.4 订阅健康检查

`RedisPubSubConfig.checkSubscriptionHealth()` 每 60s 用 `PUBSUB NUMSUB` 检查三个通道
是否有存活订阅者；任一为 0 则强制重启 listener container（覆盖「半开连接」静默失效
这种 keepalive 也测不出的场景）。

---

## 十、TDengine 存储结构

**驱动**：默认 REST 模式（`jdbc:TAOS-RS`，走 taosAdapter 6041 端口，无需 native 客户端库）。
对服务器实测（3000 行 / 30 条分片语句写入 + 200 行窗口查询）：REST 写入 320ms vs WS 442ms、
查询 34ms vs 158ms——WS 的 fetch 协议每次查询多出往返（跨公网 RTT 被放大），而项目走
`Statement` 文本 SQL，享受不到 WS 二进制绑定的收益，故 REST 更优。可选 WS：把
`TDENGINE_URL` 改成 `jdbc:TAOS-WS://` 即可，建议与 STMT2 参数绑定改造一起再评估。

**建库建表（引导式）**：`TdengineSyncService` 进程内首次写入前会执行一次 schema 引导——
先用**不带库名的 URL** 建连接，以全限定名（`n4.alarm_realtime` / `n4.alarm_event`）执行
幂等 DDL（`CREATE DATABASE IF NOT EXISTS n4 KEEP 1095 DURATION 10 BUFFER 256` + 两张
`CREATE STABLE IF NOT EXISTS`），再用完整 URL 正式连接。原因：**URL 里的库不存在时，
WS 模式连接直接失败（auth failure），REST 模式也无法通过该连接完成建库**——旧版
"先连 URL 再 CREATE + USE"的全新安装流程在两种驱动下都是坏的，引导式建库是修复。
两个超表**共享同一套 schema**，仅名字与 subtable 后缀不同。
注意 `IF NOT EXISTS` 只对**不存在的库**生效：线上已建的库要用 `ALTER DATABASE n4 BUFFER 256`
在线调整（实测线上库曾为 BUFFER 32 / VGROUPS 4，实际写内存池 = vgroup 数 × BUFFER）。

**写通道模型**：`realtime` 与 `event` 两条独立通道，各自专属单线程 + 专属长连接
（JDBC 连接非线程安全）；event 通道的等待队列只有 2，突发流量的缓冲是 persistenceBuffer
本身；任一通道连接故障互不影响，查询失败也不会关闭写连接。

| 超表 | subtable 后缀 | 内容 | 写入频率 |
|------|--------------|------|---------|
| `alarm_realtime` | `_rt` | 告警生命周期事件 | 事件发生时即时 |
| `alarm_event` | `_ev` | 每帧全量明细 | 每秒批量 |

**Schema**（TAG `path` + 17 列）：`ts, data_timestamp, point_value, point_type,
alarm_level, alarm_severity, alarm_message, time_delay, deadband, ack_by,
alarm_status, alarm_source, alarm_location, alarm_stage, alarm_stage_message,
alarm_duration, alarm_start_time`。

- subtable 名 = `sanitizeTableName(path) + 后缀`（非字母数字替换为下划线）。
- 时间戳以**原始毫秒数值**写入 SQL 文本（早期版本用 setLong 绑定，语义相同）：
  不经过本地时区的 datetime 字符串序列化，TDengine 按绝对 UTC 时刻解析，时区安全。
  `alarm_start_time` 对非告警行为 NULL，避免显示成 1970-01-01。
- 写入 SQL 由 `TdengineSql` 客户端侧拼接：字符串字面量做转义（单引号/反斜杠/控制字符），
  taos-jdbcdriver 的 WS/REST 模式走 `Statement` 文本 SQL，均无服务端参数绑定。
- 查询走 `openReadConnection()` 从**专用 HikariCP 读连接池**（`tdengine-read`，最多 4 连接）
  借连接，绝不共享写通道的长连接（JDBC 非线程安全）。
- 历史查询按**写入时间戳排序**（`ORDER BY ts DESC, data_timestamp DESC`，ts 是唯一有索引的
  主键时间戳），并带 `WHERE ts > NOW() - N d` 窗口裁剪（`tdengine.query-window-days`，默认 7，
  0 为关闭），避免对全量 `alarm_event`（KEEP 1095）做全表扫描。
- event 历史接口（`/api/alarms/events`）支持**滚动分页**：`topTs`（**进入页面 / 点击 Refresh 那一刻的
  系统时间**，作为本次浏览会话的快照上界——首屏与后续页统一按 `ts <= topTs` 查询，翻页期间
  新写入的行不参与排序，页序稳定）+ `offset`（已加载行数）+ `limit`（每页行数，默认 50、上限 500）。
  Refresh / 重新进入页面会重置快照。不用 `ts <` 游标是因为批量写入会让同一毫秒挤进几十行，
  ts 游标会在页边界静默丢行；实测 OFFSET 方案在 68 万行 / 2700 子表上代价平坦（OFFSET 10000 ≈ 0.3s）。
  前端 `AlarmEventView` 首屏渲染 50 行，滚动到底自动追加更早记录（表尾 #append 提示加载状态）。
  注意 Time 列显示的是**观测时间**（设备消息时间戳）：不同点位/设备的数据滞后不均时，
  显示时间会随翻页非单调跳变，属正常现象。

---

## 十一、前端实时与历史两条通道

### 11.1 实时通道（WebSocket）

```
Kafka → Flink 评估 → alarm:notifications → AlarmNotificationListener
     → AlarmWebSocketHandler.broadcast() → /ws/alarm → useAlarmWebSocket → 大屏
```

- 前端断线重连采用指数退避（1s→30s），重连后通过 REST `getAlarmSummary()` 重新播种汇总数据。

### 11.2 历史通道（REST API）

| 端点 | 数据源 | 说明 |
|------|--------|------|
| `GET /api/alarms/active?tab=active\|unacknowledged\|acknowledged\|cleared` | Redis `alarm:active`（经 `ActiveAlarmStore` 800ms TTL 快照） | 当前/未确认/已确认/已清除 |
| `GET /api/alarms/cleared` | TDengine `alarm_realtime` | 告警生命周期历史，最新 200 条 |
| `GET /api/alarms/events?topTs&offset&limit` | TDengine `alarm_event` | 每帧全量明细，**滚动分页**（进入/刷新时刻为快照上界，每页默认 50，前端滚动到底自动加载更早记录，见十章） |
| `GET /api/alarms/summary` | 快照聚合 | 严重级别计数 |
| `GET /api/alarms/by-severity` | 快照聚合 | 严重级别分布 |
| `GET /api/alarms/trend` | 快照聚合 | 24h 趋势 |
| `GET /api/alarms/by-hour` | 快照聚合 | 按小时分布 |
| `GET /api/alarms/by-system` | 快照聚合 | 按系统（路径第 3 段）分布 |
| `GET /api/alarms/top-sources` | 快照聚合 | 报警源 Top |
| `POST /api/alarms/ack` | Redis `alarm:active`（写） | 确认告警 |

> **性能设计**：`ActiveAlarmStore` 是共享的短 TTL（800ms）读穿快照，一次拉取整个
> `alarm:active` hash 供所有端点与 WebSocket 聚合复用，避免每个请求/每个事件都全量拉取+重聚合。

---

## 十二、确认（Ack）如何回写状态机

`POST /api/alarms/ack`（body: `{"paths": [...], "ackBy": "..."}`）→ `AlarmService.acknowledgeAlarms`：

- 读 `alarm:active`，把命中的记录 `status=Ack`、`ackBy=ackBy`、`stageMessage="Ack"`。
- **duration 规则**：
  - 进行中（start/continue）：重算为 `(ackMoment - alarmStart)/1000`。
  - 已恢复（end）：**保留**恢复时冻结的 duration，不重算。
- **已恢复 + 确认** → 从 `alarm:active` 移除（防止刷新后复活），但仍广播清除信号。
- **进行中 + 确认** → 记录保留在 active（状态 Ack）继续展示。
- 每个确认记录重新发布到 `alarm:notifications`（前端立即更新），并写 TDengine
  `alarm_realtime` + 入 `alarm_event` 缓冲（时间戳 = 确认时刻）。
- `alarm:latest` 仅在**进行中**更新（已恢复则 latest 已是正常态，ack 不得触碰）。
- 最后 `activeAlarmStore.invalidate()` 使快照失效。

> **边界说明**：后端是唯一回写 Flink 所拥有的 `alarm:active` 的地方（ack），
> 这是一个刻意的跨边界写入，需保持与 Flink 状态机的时间戳/字段约定一致。

---

## 十三、内嵌模式 vs 独立集群（配置与运维要点）

| 维度 | 内嵌模式（默认） | 独立集群（`--profile flink`） |
|------|----------------|------------------------------|
| 运行位置 | Spring Boot 进程内守护线程 | 独立 Flink JobManager/TaskManager 容器 |
| 启动 | `FlinkJobRunner` 启动时自动 | `standalone-job --job-classname ...` |
| 关闭 | 随 Spring Boot 关闭 | `flink cancel` / 停止容器 |
| 依赖 | 后端 fat jar 捆绑 Flink 运行时（compile scope） | Flink 运行时 `provided`（用集群的），`flink-connector-kafka` + `jedis` 打进 jar |
| 配置 | `application.yaml` / 环境变量 | `--kafka-broker --kafka-topic --group-id --redis-host ...` |
| checkpoint | 无 | 启用：`execution.checkpointing.interval: 60s`、EXACTLY_ONCE、fixed-delay 重启 |
| 消费组 | `n4-flink-alarm-processor` | `n4-flink-alarm-processor-prod` |
| 互斥性 | 两者消费同一 topic/组，**不能同时运行** | 同左 |

---

## 十四、常见问题排查指引

| 现象 | 排查方向 |
|------|---------|
| 没有报警产生 | ① `alarm.flink.enabled` 是否 true；② 启动日志是否「规则已同步到 Redis」；③ 该点位 `alarm_code` 是否为空；④ `alarm:point:rule` 是否有该 mqttPath 条目 |
| 规则改了不生效 | Flink per-subtask 本地 TTL 缓存（默认 60s，可配置），等 TTL 过期或 `POST /api/alarm-rules/reload` |
| 报警延迟不确认 | 检查规则 `timeDelay`；pending 期间 `alarm:latest` 应为正常态（无级别） |
| 前端没实时刷新 | 检查 `alarm:notifications` 订阅是否存活（`PUBSUB NUMSUB`）、WebSocket 是否有客户端连接、Nginx 是否代理 `/ws` |
| TDengine 时间错 8 小时 | 确认写入走 setLong 毫秒绑定；检查 TDengine 与 JVM 时区配置 |
| 重启后消费行为 | Kafka `OffsetsInitializer.latest()`：新消费组从最新开始，会跳过已有历史消息 |
| 内嵌与独立集群同跑 | 两套并行会瓜分分区随机丢告警——同一 topic 同一组只能有一种模式 |

---

## 十五、关键代码文件索引

| 模块 | 文件 | 职责 |
|------|------|------|
| monitor-flink-job | `AlarmProcessingJob.java` | 作业入口、拓扑、Kafka 源、两种启动模式 |
| monitor-flink-job | `AlarmProcessFunction.java` | 状态机编排、延迟确认、pending 定时器 |
| monitor-flink-job | `RuleEvaluator.java` | 数值/枚举规则评估、死区迟滞 |
| monitor-flink-job | `ParsePointFunction.java` | JSON 解析（丢弃畸形消息） |
| monitor-flink-job | `RedisStateStore.java` | 全部 Redis 读写/发布（时间戳覆盖规则） |
| monitor-flink-job | `PointMetaParser.java` | 点位元数据解析、source/location 提取 |
| monitor-flink-job | `PendingAlarm.java` | 延迟确认的 in-flight 状态 |
| monitor-backend | `FlinkJobRunner.java` | 内嵌启动 Flink 作业 |
| monitor-backend | `AlarmRuleInitializer.java` / `AlarmRuleSyncService.java` | MySQL→Redis 规则同步 |
| monitor-backend | `AlarmNotificationListener.java` | 通知→WebSocket |
| monitor-backend | `AlarmRealtimeListener.java` / `AlarmPersistenceListener.java` | 通知→TDengine |
| monitor-backend | `RedisPubSubConfig.java` | Pub/Sub 订阅 + 健康检查 |
| monitor-backend | `TdengineSyncService.java` | TDengine 建表/写/查 |
| monitor-backend | `AlarmService.java` | REST 查询 + ack 回写 |
| monitor-backend | `ActiveAlarmStore.java` / `AlarmAggregator.java` | 快照缓存 + 聚合 |
| monitor-common | `alarm/model/*`（PointData, PointAlarmState, AlarmSeverity, AlarmStage, AlarmStatus） | 共享数据模型与取值词表 |
| frontend | `useAlarmWebSocket.ts` / `api/alarm.ts` / `AlarmTable.vue` 等 | 前端实时与历史展示 |
