# N4-monitor Linux 服务器部署指南

本文档介绍如何将 N4-monitor 应用（后端 Spring Boot + 前端 Vue）通过 Docker Compose 部署到 Linux 服务器。

## 一、部署架构

```
Linux 服务器 (47.121.30.136)
├── 已运行的外部组件（部署前确认已启动）:
│     ├── MySQL    :3306    (业务库 n4_monitor)
│     ├── Redis    :6379
│     ├── Kafka    :9092
│     ├── TDengine :6041    (REST 模式, 库 n4)
│     └── FUXA     :1881    (已用 Docker 运行)
│
└── 本次部署 (Docker Compose 编排两个容器):
      ├── n4-monitor-backend   Spring Boot fat jar + 内嵌 Flink   端口 8089
      └── n4-monitor-frontend  Nginx 托管 Vue dist + 反代         对外端口 80

可选：`docker compose --profile flink up -d` 另起独立 Flink 集群运行告警作业
（flink-jobmanager + flink-taskmanager）。与后端内嵌作业互斥——两者消费同一
Kafka topic，同时运行会瓜分分区导致告警随机丢失；切换顺序见 docker-compose.yml 注释。
```

- 后端容器通过 `47.121.30.136` 访问已运行的中间件。
- 前端 Nginx 容器对外暴露 80 端口，将 `/api`、`/ws` 反向代理到后端容器（容器间用 compose 服务名 `n4-monitor-backend:8089` 通信）。
- FUXA 图形监控 iframe 由浏览器直接访问 `http://47.121.30.136:1881/...`，Nginx 不代理 FUXA。

## 二、前置条件

在服务器上确认以下环境：

```bash
# 1. Docker 已安装 (建议 24+)
docker --version

# 2. Docker Compose v2 已安装 (Docker 20.10+ 自带 compose 插件)
docker compose version

# 3. 端口 80、8089 未被占用
ss -tlnp | grep -E ':80|:8089'

# 4. 外部组件连通性自检（口令见 .env，勿写入文档或命令历史）
mysql -h 47.121.30.136 -P 3306 -uroot -p -e "SELECT 1"          # MySQL
redis-cli -h 47.121.30.136 -p 6379 -a "$REDIS_PASSWORD" ping    # Redis 应返回 PONG
curl http://47.121.30.136:1881                                   # FUXA
curl http://47.121.30.136:6041/rest/login/root/$TDENGINE_PASSWORD  # TDengine REST
# Kafka 可用 kafkacat: kcat -b 47.121.30.136:9092 -L
```

若 `docker` 未安装，参考官方一键脚本：
```bash
curl -fsSL https://get.docker.com | sh
systemctl enable --now docker
```

## 三、传输代码到服务器

将项目代码传到服务器（二选一）：

**方式 A：Git 克隆（推荐）**
```bash
cd /opt
git clone <你的仓库地址> N4-monitor
cd N4-monitor
```

**方式 B：本地打包上传**
```bash
# 本地（项目根目录）打包，排除不需要的大目录
# tools/backups 与 outputs 含数 GB 的 FUXA 备份 JSON，务必排除，否则包会巨大
tar --exclude='node_modules' --exclude='target' --exclude='.git' \
    --exclude='材料' --exclude='构建数据' --exclude='logs' \
    --exclude='tools' --exclude='outputs' --exclude='旧脚本备份' \
    -czf n4-monitor.tar.gz .

# 上传到服务器
scp n4-monitor.tar.gz root@47.121.30.136:/opt/

# 服务器上解压
ssh root@47.121.30.136
mkdir -p /opt/N4-monitor && cd /opt/N4-monitor
tar -xzf /opt/n4-monitor.tar.gz
```

## 四、配置调整（必做）

中间件地址与口令不再写死在仓库里。部署机上执行：

```bash
cp .env.example .env
vi .env   # 填入真实的中间件地址与口令
```

`docker compose` 与后端 `application.yaml` 均从根目录 `.env` 读取（当前 `.env` 随仓库入库，部署机拉取代码即得；若仓库转为公开或扩大共享范围，请先移除 `.env` 并轮换口令）：

| 环境变量 | 说明 |
|---------|------|
| `MYSQL_URL` / `MYSQL_USERNAME` / `MYSQL_PASSWORD` | MySQL JDBC 连接串与账号 |
| `KAFKA_BOOTSTRAP_SERVERS` | Kafka broker 地址 |
| `REDIS_HOST` / `REDIS_PORT` / `REDIS_PASSWORD` | Redis 地址与口令 |
| `TDENGINE_URL` / `TDENGINE_USERNAME` / `TDENGINE_PASSWORD` | TDengine REST 连接与口令 |
| `FUXA_BASE_URL` | FUXA 服务地址（后端调模板 provisioning） |
| `JAVA_OPTS` | JVM 参数（在 docker-compose.yml 中调整） |
| `ALARM_FLINK_ENABLED` | 是否启动内嵌 Flink 告警作业（docker-compose.yml 中调整） |

前端 FUXA iframe 构建地址取自同一 `FUXA_BASE_URL`（`n4-monitor-frontend.build.args.VITE_FUXA_URL` 引用）。

## 五、构建并启动

```bash
cd /opt/N4-monitor

# 准备环境变量文件（中间件地址与口令，见"四、配置调整"）
cp .env.example .env && vi .env

# 构建镜像（首次较慢，后端会下载 Maven 依赖，前端会 npm ci）
docker compose build

# 后台启动
docker compose up -d

# 查看运行状态
docker compose ps
```

首次构建约 5-15 分钟（取决于网络和机器性能），后续改动代码后重新 `docker compose build` 会利用缓存加快。

## 六、验证部署

```bash
# 1. 容器状态应为 Up
docker compose ps

# 2. 后端启动日志（观察 Spring Boot + Flink 作业）
docker compose logs -f n4-monitor-backend
# 关键日志: "Started N4MonitorApplication" 和 "N4-Monitor Alarm Processing Job"

# 3. 前端 Nginx 日志
docker compose logs -f n4-monitor-frontend

# 4. 容器内时区检查（应为 CST / Asia/Shanghai）
docker exec n4-monitor-backend date
docker exec n4-monitor-frontend date

# 5. 健康检查
curl http://47.121.30.136/api/monitor/nav      # 后端 API (路径按实际接口为准)
curl -I http://47.121.30.136/                   # 前端页面应返回 200
```

浏览器访问 `http://47.121.30.136/` 即可使用监控系统。

## 七、常用运维命令

```bash
# 查看实时日志
docker compose logs -f
docker compose logs -f n4-monitor-backend     # 仅后端

# 重启服务
docker compose restart
docker compose restart n4-monitor-backend

# 停止并移除容器（保留镜像和数据）
docker compose down

# 更新代码后重新部署
git pull                                       # 或重新上传代码
docker compose build                           # 重新构建
docker compose up -d                           # 滚动更新

# 进入容器排查
docker exec -it n4-monitor-backend sh
```

## 八、常见问题排查

### 1. 后端启动失败：连接中间件超时
后端容器通过 `47.121.30.136` 访问中间件。若中间件绑定的是 `127.0.0.1` 或有防火墙限制，容器无法访问。
```bash
# 进入后端容器测试连通性
docker exec -it n4-monitor-backend sh
# 容器内
apt-get update && apt-get install -y telnet
telnet 47.121.30.136 3306
telnet 47.121.30.136 6379
telnet 47.121.30.136 9092
```
若不通，需将中间件改为监听 `0.0.0.0` 或在防火墙放行端口。

### 2. 前端页面空白 / 404
- 检查 Nginx 是否正常：`docker compose logs n4-monitor-frontend`
- 确认 80 端口未冲突：`ss -tlnp | grep :80`

### 3. FUXA 图形监控 iframe 不显示
- 确认 FUXA 容器运行中：`docker ps | grep fuxa`
- 确认浏览器能直接访问 `http://47.121.30.136:1881`
- 若 FUXA 地址变更，修改 `.env` 中 `FUXA_BASE_URL` 后重新 `docker compose build n4-monitor-frontend`

### 4. TDengine 时区问题（时间戳偏移）
容器已设 `TZ=Asia/Shanghai`，且 JDBC 连接串带 `serverTimezone=Asia/Shanghai`。若仍有偏移：
```bash
docker exec n4-monitor-backend date     # 确认容器时间为 CST
docker exec n4-monitor-backend env | grep TZ
```
确认 `application.yaml` 中 `tdengine.url` 使用 `jdbc:TAOS-RS://`（REST 模式，无本地客户端时区依赖）。

### 5. Kafka 消费者组冲突
本地开发与服务器共用同一 Kafka `group-id`（内嵌作业取 `application.yaml` 的 `alarm.flink.group-id`，默认 `n4-flink-alarm-processor`；独立集群取 compose 中 flink command 的 `--group-id`，当前为 `n4-flink-alarm-processor-prod`）会导致消息被抢占。
解决：部署前停掉本地后端，或修改上述 group-id 保证两侧不同。

### 6. 内存不足（OOM）
后端默认 `-Xmx1024m`，加上 Flink 嵌入式执行开销，建议服务器至少 2GB 可用内存。如不足，调整 `JAVA_OPTS: "-Xms256m -Xmx512m ..."`。
```bash
free -h                  # 查看可用内存
docker stats             # 查看容器实时资源占用
```

## 九、端口与防火墙放行清单

确保服务器防火墙 / 安全组放行以下端口（供外部访问）：

| 端口 | 用途 |
|------|------|
| 80   | N4-monitor 前端（对外） |
| 8089 | 后端 API。compose 当前绑定所有网卡（`8089:8089`）；若无需直连后端调试，可改为 `127.0.0.1:8089:8089` 收敛为仅本机访问，常规流量一律走前端 Nginx 反代 |
| 1881 | FUXA 图形监控（浏览器需直接访问 iframe） |

中间件端口（3306/6379/9092/6041）只需对后端容器可达，**不建议对公网开放**。
