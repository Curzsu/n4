# N4 Monitor

面向 N4（Niagara）设备的监控与告警平台：Spring Boot 后端（内嵌 Flink 告警
流处理）+ Vue 3 监控大屏。数据链路：设备 → EMQX(MQTT) → Kafka → Flink 规则评估
（阈值/死区/延迟确认/级别升降级）→ Redis 实时状态 + TDengine 时序存储 + MySQL 业务库。

## 模块结构

| 目录 | 说明 |
|---|---|
| `monitor-backend` | Spring Boot 后端（Web API + 内嵌 Flink 作业） |
| `monitor-flink-job` | Flink 告警流处理作业（可独立部署到 Flink 集群） |
| `monitor-common` | 跨模块共享模型 |
| `frontend` | Vue 3 + Element Plus 监控大屏 |
| `deploy` | 服务器部署文档 |
| `test-producer` | 开发调试用 Kafka 测试脚本（绕过设备/EMQX 直接发消息） |

## 快速开始

- 本地开发：仓库根目录执行 `start-n4-monitor.bat`（需 Java 21 / Maven / Node.js）
- 连接配置：`cp .env.example .env` 后按环境修改（后端与 docker compose 均读取该文件）
- 自动化回归测试：执行 `scripts\run_regression_test.bat` 或 `python scripts/regression_test.py`
- 服务器部署：见 [deploy/README.md](deploy/README.md)

## 问数

登录后，从顶部 **Ask Data** 进入独立页面 `/ask-data`。首次启动会将问数权限
`data-assistant:view` 同步给超级管理员。其他角色需要同时拥有问数权限和对应的数据页面权限。

在本地 `.env` 中设置 `GEMINI_API_KEY` 后重启后端。Docker Compose 和
`start-n4-monitor.bat` 会读取该文件。直接启动 Spring Boot 时，通过进程环境变量传入密钥。
问数需要后端能够访问 `generativelanguage.googleapis.com`。
如果后端需要代理，可设置 `GEMINI_PROXY` 为 HTTP 代理地址。未设置时使用环境变量
`HTTPS_PROXY` 或 `https_proxy`。代理只用于 Gemini 请求，地址需能从后端进程所在环境访问。

可以询问实时告警、历史告警帧和已部署设备。页面支持计数、分组和明细查询，可用同一页面内最近三条成功的
提问作为追问上下文。历史查询按上海时区统计，单次覆盖最多 30 个自然日。历史记录按帧统计，同一告警可能
对应多帧。若明细或分组需要读取超过 2,000 条历史帧，请缩小日期范围或指定告警级别。

Gemini 3.5 Flash-Lite 负责理解问题和生成摘要。后端校验权限及查询条件，然后通过现有服务读取数据。
发送给 Gemini 的内容包括问题、追问上下文和用于生成摘要的查询结果，每次最多发送 30 行明细或分组。

## ⚠️ 安全约定：.env 不入库

根目录 `.env`（含 MySQL / Redis / TDengine 的真实地址与口令）**不纳入版本控制**：`.gitignore` 已忽略 `*.env`（仅放行 `.env.example`），git 历史也已通过 filter-repo 重写清除。每台部署机需自行 `cp .env.example .env` 并按环境填写真实口令。

**请勿把 `.env` 加入 git 跟踪或推送**——口令一旦进入 git 历史，仅删除文件无法作废，必须到服务端逐一改密轮换。若误提交，应通过 filter-repo 重写历史并轮换受影响口令。

以上约定同样记录在 `.gitignore` 与 `.env.example` 的注释中，以注释为准。
