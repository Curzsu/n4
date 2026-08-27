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
- 服务器部署：见 [deploy/README.md](deploy/README.md)

## ⚠️ 安全约定：仓库公开前必须先处理 .env

本仓库当前托管在**自建私有 Gitea** 上，根目录 `.env`（含 MySQL / Redis /
TDengine 的真实地址与口令）**随仓库入库**，仅为私有场景下的部署便利。

**如果后续仓库要公开、或共享给更大范围的人员，必须先完成以下步骤，缺一不可：**

1. 删除 `.gitignore` 中的 `!.env` 放行行（恢复 `*.env` 对它的忽略）
2. 执行 `git rm --cached .env` 将其移出版本控制
3. **轮换 `.env` 中出现过的所有口令**（MySQL / Redis / TDengine）——这些口令
   已进入 git 历史，仅删除文件不等于作废，必须到服务端逐一改密
4. 同步更新各部署机上的本地 `.env`

以上步骤同样记录在 `.gitignore` 与 `.env.example` 的注释中，以注释为准。
