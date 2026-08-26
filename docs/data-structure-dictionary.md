# N4 Monitor 数据结构字典

> 更新日期：2026-08-17。与当前代码对齐：Boolean 规则已并入 Enum（字符串匹配）、
> 数值规则由按级别的 hysteresis 列改为单一 `deadband` + `time_delay`、工程为
> Maven 多模块（monitor-common / monitor-flink-job / monitor-backend）。
> Redis 侧契约以 `monitor-flink-job` 的特征测试（44 例）为准。

## 1. MySQL 规则表

### 1.1 `alarm_rule_numeric`

来源：`n4_monitor_schema_latest.sql`、`monitor-common/src/main/java/com/n4monitor/entity/AlarmRuleNumericEntity.java`、`monitor-backend/src/main/resources/mapper/AlarmRuleNumericMapper.xml`

| 字段名 | 类型 | 可空 | 默认值 | 说明 |
|---|---|---:|---|---|
| id | bigint | 否 | 自增 | 主键 ID |
| alarm_name | varchar(200) | 否 | - | 规则名称，格式为 `点位名_序号`（如 `temp1_00`），大小写敏感 |
| point_type | varchar(20) | 是 | numeric | 点位类型 |
| time_delay | int | 是 | 0 | 延迟确认秒数，每点位一个值；越限持续该时长后才确认告警 |
| deadband | varchar(50) | 是 | 0 | 死区/迟滞（数值或字符串），每点位一个值；恢复边界 = 阈值 ± deadband |
| high_high_value | varchar(50) | 是 | NULL | High-High 阈值，`>=` 触发 |
| high_high_severity | varchar(20) | 是 | NULL | High-High 级别 |
| high_high_message | varchar(500) | 是 | NULL | High-High 提示信息 |
| high_value | varchar(50) | 是 | NULL | High 阈值，`>=` 触发 |
| high_severity | varchar(20) | 是 | NULL | High 级别 |
| high_message | varchar(500) | 是 | NULL | High 提示信息 |
| low_value | varchar(50) | 是 | NULL | Low 阈值，`<=` 触发 |
| low_severity | varchar(20) | 是 | NULL | Low 级别 |
| low_message | varchar(500) | 是 | NULL | Low 提示信息 |
| low_low_value | varchar(50) | 是 | NULL | Low-Low 阈值，`<=` 触发 |
| low_low_severity | varchar(20) | 是 | NULL | Low-Low 级别 |
| low_low_message | varchar(500) | 是 | NULL | Low-Low 提示信息 |
| create_time | datetime | 否 | CURRENT_TIMESTAMP | 创建时间 |
| update_time | datetime | 否 | CURRENT_TIMESTAMP ON UPDATE | 更新时间 |
| is_deleted | tinyint(1) | 否 | 0 | 逻辑删除标记 |

补充约束：`alarm_name` 唯一索引 `uk_alarm_name`。评估优先级 High-High > High > Low-Low > Low；
阈值非法（非数字）时跳过该级别并记录 warn 日志。

### 1.2 `alarm_rule_enum`

来源：`n4_monitor_schema_latest.sql`、`monitor-common/src/main/java/com/n4monitor/entity/AlarmRuleEnumEntity.java`、`monitor-backend/src/main/resources/mapper/AlarmRuleEnumMapper.xml`

| 字段名 | 类型 | 可空 | 默认值 | 说明 |
|---|---|---:|---|---|
| id | bigint | 否 | 自增 | 主键 ID |
| alarm_name | varchar(200) | 否 | - | 规则名称，同一点位的所有枚举行共用 |
| point_type | varchar(20) | 是 | enum | 点位类型 |
| time_delay | int | 是 | 0 | 延迟确认秒数，同一点位各行共用 |
| enum_value | varchar(50) | 否 | - | 枚举触发值（字符串，如 `0`/`1`/`true`/`Normal`），匹配不区分大小写 |
| enum_meaning | varchar(200) | 是 | NULL | 枚举值含义 |
| severity | varchar(20) | 是 | NULL | 级别，取值 `Critical`/`Major`/`Minor`/`Warning`；**留空（NULL/空串）表示该值是显式正常状态，不触发告警** |
| message | varchar(500) | 是 | NULL | 提示信息 |
| create_time | datetime | 否 | CURRENT_TIMESTAMP | 创建时间 |
| update_time | datetime | 否 | CURRENT_TIMESTAMP ON UPDATE | 更新时间 |
| is_deleted | tinyint(1) | 否 | 0 | 逻辑删除标记 |

补充约束：联合唯一索引 `uk_alarm_name_enum(alarm_name, enum_value)`。
Boolean 类型点位复用本表（`true`/`false` 作为字符串枚举值匹配）；历史上的独立
`alarm_rule_boolean` 表已废弃。

## 2. Redis 数据结构

### 2.1 `alarm:point:rule`（点位元数据映射）

来源：`monitor-backend` `alarm/service/AlarmRuleSyncService.java`（写入），`monitor-flink-job` `PointMetaParser`（读取）

| 项目 | 说明 |
|---|---|
| 类型 | Hash |
| key | `alarm:point:rule`（配置项 `alarm.redis.point-rule-map-key`） |
| field | 点位 mqttPath（对应 `point_deployment.n4_address`） |
| value | `{"type":"numeric","alarm_code":"temp1_00"}` |

`type` 驱动评估路由（`numeric` / `enum` / `boolean` / `string`），`alarm_code`
选中规则；两者来自同一 `point_deployment` 行。

### 2.2 `alarm:rules:numeric`

来源：`AlarmRuleSyncService`（写入），`RuleEvaluator.evaluateNumeric`（读取）

| 项目 | 说明 |
|---|---|
| 类型 | Hash |
| key | `alarm:rules:numeric` |
| field | `alarm_name` |
| value | `AlarmRuleNumericEntity` 的 JSON（驼峰字段，与 1.1 表字段一一对应） |

### 2.3 `alarm:rules:enum`

来源：`AlarmRuleSyncService`（写入），`RuleEvaluator.evaluateEnum`（读取）

| 项目 | 说明 |
|---|---|
| 类型 | Hash |
| key | `alarm:rules:enum` |
| field | `alarm_name` |
| value | 同一 `alarm_name` 的 `AlarmRuleEnumEntity` **列表** JSON |

### 2.4 `alarm:latest`（最新值）

来源：`monitor-flink-job` `RedisStateStore.writeLatestState`（写入），`monitor-backend` `AlarmService.updateLatestOnAck`（ack 确认覆盖读取）

| 项目 | 说明 |
|---|---|
| 类型 | Hash |
| key | `alarm:latest` |
| field | 点位 mqttPath |
| value | `PointAlarmState` JSON（字段见下） |

`PointAlarmState` JSON 字段：

| 字段名 | 类型 | 说明 |
|---|---|---|
| clientId | string | 来源客户端标识 |
| mqttPath | string | 点位唯一键（= field 名） |
| path | string | 遗留 slot 路径，仅参考 |
| value | string | 点位值 |
| type | string | 解析后的点位类型 |
| timestamp | number | 数据时间戳（epoch 毫秒） |
| level | string / null | 告警等级（数值为级别名，枚举为 `Enum:<值>`；正常为 null） |
| severity | string / null | Critical / Major / Minor / Warning |
| message | string / null | 告警提示 |
| timeDelay | number / null | 规则延迟确认秒数（正常态也携带，供死区判定） |
| deadband | string / null | 规则死区（正常态也携带） |
| ackBy | string / null | 确认人（`-` 表示未确认） |
| status | string / null | `Unack` / `Ack` |
| source | string / null | 从 mqttPath 解析的来源（倒数两段） |
| location | string / null | 从 mqttPath 解析的位置（首段） |
| duration | string / null | 持续秒数 |
| stage | string / null | `start` / `continue` / `end` |
| stageMessage | string / null | 阶段说明（升级时为 `Start - High to High-High` 等） |
| alarmStartTime | number / null | 告警起始时间（epoch 毫秒；延迟确认场景为确认时刻） |

### 2.5 `alarm:active`（活跃/冻结告警）

来源：`RedisStateStore.writeActiveState / readActive / removeActive`（作业侧），`AlarmService`（查询/确认）

| 项目 | 说明 |
|---|---|
| 类型 | Hash |
| key | `alarm:active` |
| field | 点位 mqttPath |
| value | `PointAlarmState` JSON，字段同 2.4 |

写入口径：序列化时 `timestamp` 恒被覆盖为 `alarmStartTime`（继续/结束记录保留
起始时间戳）。恢复且已 Ack 的记录从 hash 中删除；未 Ack 的恢复记录以 `stage=end`
冻结保留。

### 2.6 `alarm:notifications`（前端通知通道）

| 项目 | 说明 |
|---|---|
| 类型 | Pub/Sub Channel |
| 发布方 | Flink 作业（start / 升级 / end 事件） |
| 消息 | `PointAlarmState` JSON，`timestamp` 覆盖为 `alarmStartTime` |
| 订阅方 | `monitor-backend` `alarm/listener/AlarmNotificationListener`：读取 `alarm:active` 聚合后经 WebSocket 广播前端 |

### 2.7 `alarm:realtime`（TDengine 持久化通道）

| 项目 | 说明 |
|---|---|
| 类型 | Pub/Sub Channel |
| 发布方 | Flink 作业（每个生命周期事件，含升级时先 End 后 Start 两条） |
| 消息 | `PointAlarmState` JSON，`timestamp` 覆盖为当次数据时间戳 |
| 订阅方 | `alarm/listener/AlarmRealtimeListener`：异步写入 TDengine `alarm_realtime` 超表 |

## 3. 配置入口

| 文件/位置 | 说明 |
|---|---|
| `monitor-backend/src/main/resources/application.yaml` | 主配置：中间件连接、Redis 键名/通道名、Flink 作业参数 |
| 根目录 `.env`（模板 `.env.example`） | 环境相关地址与口令；Spring Boot 与 docker compose 共同读取 |
| `monitor-backend .../alarm/service/AlarmRuleSyncService.java` | MySQL 规则表 → Redis 三个 hash 的同步 |
| `monitor-flink-job .../flink/AlarmProcessFunction.java` 及协作者 | 作业侧全部 Redis 读写（经 `RuleEvaluator` / `RedisStateStore`） |
| `monitor-backend .../tdengine/TdengineSyncService.java` | TDengine REST 连接管理、`alarm_event` 批量写入、`alarm_realtime` 实时写入 |
| `monitor-backend .../config/RedisPubSubConfig.java` | 三个 Pub/Sub 通道（notifications / realtime / persist）的监听注册 |

## 4. 核验方式

Redis 契约（2.1–2.7）由 `monitor-flink-job` 的 50 个特征测试
（`NumericRuleEvaluationTest`、`EnumRuleEvaluationTest`、`AlarmLifecycleTest`、
`PendingAlarmDelayTest`、`SkipPathsTest`、`PersistenceChannelTest`）钉死：
`mvn -pl monitor-flink-job test`。
表结构变更时以 `n4_monitor_schema_latest.sql` 为准并同步更新本文档。
