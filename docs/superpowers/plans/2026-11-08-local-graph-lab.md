# 本地隔离环境与第一版图谱实施计划
> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 在 Su/data-assistant 上建立独立本地数据副本和可验证资产图谱。
**Architecture:** Windows 前后端连接 Docker MySQL、Redis、Neo4j，仅暴露回环端口；远程只读备份；不部署 TDengine。
**Tech Stack:** Docker Compose、MySQL 8.4.5、Redis 7.4.10、Neo4j 5.26.29、Python、Spring Boot。

- [x] 添加 compose.local.yaml 和私有 .env.local，启动独立命名卷。
- [x] 先运行构图测试：枚举分组、空阈值、缺失设备和重复 MQTT 路径。
- [x] 一致性导出 MySQL 至忽略的备份目录，恢复本地并比较表内容摘要。
- [x] 只读复制 Redis alarm:* 键并记录范围和时间；只恢复本地。
- [x] 构图、导入 Neo4j 快照，通过校验后激活；失败快照不供查询。
- [x] 本地启动加载 .env.local；关闭 Flink/FUXA/TDengine 持久化及历史问数。
- [x] 运行自动化测试、数据核对、图查询和隔离启动验证，记录验收报告。
- [x] 只提交任务代码和中文文档，不提交凭据、备份，不推送。

## 验收与决策
以实际运行结果为准：容器健康、源目标数据摘要一致、图谱关系正确、历史功能禁用。本地端口仅 127.0.0.1。
用户明确要求当前分支，故不另建 worktree。Kafka/EMQX/FUXA 采集链路不属于静态构图首版；不复制无关 Gitea。
原 .env 保留为只读来源，启动优先采用 .env.local。图谱快照保留以支持复现，不覆盖用户已有图。

Ruling: Windows 保留了 13278–13377，实测 23306 可绑定，MySQL 本地端口改为 23306。

## 完成记录
已完成数据迁移、两次图谱快照导入、全量属性关系核对及无 TDengine 后端真实启动。Ask Data 图谱自动路由不属于此次首版构图范围，后续单独实现。
