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
      ├── n4-monitor-backend   Spring Boot fat jar + 内嵌 Flink   端口 ${SERVER_PORT:-8088}
      └── n4-monitor-frontend  Nginx 托管 Vue dist + 反代         对外端口 80

可选：`docker compose --profile flink up -d` 另起独立 Flink 集群运行告警作业
（flink-jobmanager + flink-taskmanager）。与后端内嵌作业互斥——两者消费同一
Kafka topic，同时运行会瓜分分区导致告警随机丢失；切换顺序见 docker-compose.yml 注释。
```

- 后端容器通过 `47.121.30.136` 访问已运行的中间件。
- **后端与前端均使用 host 网络模式**（`network_mode: host`）：容器共享宿主机网络栈，访问同机 Redis/中间件走回环而非 Docker 网桥，延迟更低。前端 Nginx 直接监听宿主机 `:80`，将 `/api`、`/ws` 通过 `127.0.0.1:${SERVER_PORT}`（默认 8088）反向代理到后端。host 模式下容器间无 compose 服务名 DNS，故 nginx upstream 用回环地址。
- **后端端口全局只改一处**：在 `.env` 里改 `SERVER_PORT`（默认 `8088`），compose 端口映射、nginx 反代（模板经 envsubst 渲染）会全部跟随，无需改代码或配置。
- FUXA 图形监控 iframe 由浏览器直接访问 `http://47.121.30.136:1881/...`，Nginx 不代理 FUXA。

## 二、前置条件

在服务器上确认以下环境：

```bash
# 1. Docker 已安装 (建议 24+)
docker --version

# 2. Docker Compose v2 已安装 (Docker 20.10+ 自带 compose 插件)
docker compose version

# 3. 端口 80、${SERVER_PORT:-8088} 未被占用
ss -tlnp | grep -E ":80|:${SERVER_PORT:-8088}"

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

## 七、一键迁移到测试环境

开发机（`/opt` 下的中间件 + N4-monitor）要复制一份到测试机作为测试环境，用 `deploy/pack.sh`（开发机打包） + `deploy/unpack.sh`（测试机还原）两个脚本完成。

### 测试环境准备：安装 Docker 官方版

测试机必须装 **Docker 官方版（Engine + Compose v2 插件）**。脚本依赖 `docker compose`（v2 语法）、`docker load`、host 网络、HEALTHCHECK，官方最新版最稳。**不要用 `apt install podman-docker` 或 `docker.io`**：podman-docker 只是把 podman 伪装成 docker 的兼容层（compose 兼容差）；docker.io 是 Ubuntu 打包的老版 Moby，不带 compose v2 插件。

**若误装了 `podman-docker`，先回退**（若安装还在进行，先 `Ctrl+C` 中断）：

```bash
# 卸载 podman-docker 及其依赖（不影响系统）
sudo apt-get remove -y podman-docker podman
sudo apt-get autoremove -y
docker -v        # 应提示 command not found（正常）
```

**安装 Docker 官方版（Ubuntu/Debian）**：

```bash
# 1. 卸载可能存在的旧 docker 包
sudo apt-get remove -y docker docker-engine docker.io containerd runc

# 2. 依赖
sudo apt-get update
sudo apt-get install -y ca-certificates curl gnupg

# 3. 添加 Docker 官方 GPG key + 仓库（Debian 请把 URL 的 ubuntu 换成 debian）
sudo install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg | sudo gpg --dearmor -o /etc/apt/keyrings/docker.gpg
sudo chmod a+r /etc/apt/keyrings/docker.gpg
echo \
  "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/ubuntu \
  $(. /etc/os-release && echo "$VERSION_CODENAME") stable" | \
  sudo tee /etc/apt/sources.list.d/docker.list > /dev/null

# 4. 安装 Engine + Compose 插件
sudo apt-get update
sudo apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin

# 5. 验证（两个都应有输出）
sudo docker --version
sudo docker compose version
```

**国内服务器（如阿里云）下载慢时，用阿里云镜像源**，把第 3、4 步的 `download.docker.com` 换成 `mirrors.aliyun.com/docker-ce`：

```bash
curl -fsSL https://mirrors.aliyun.com/docker-ce/linux/ubuntu/gpg | sudo gpg --dearmor -o /etc/apt/keyrings/docker.gpg
sudo chmod a+r /etc/apt/keyrings/docker.gpg
echo \
  "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://mirrors.aliyun.com/docker-ce/linux/ubuntu \
  $(. /etc/os-release && echo "$VERSION_CODENAME") stable" | \
  sudo tee /etc/apt/sources.list.d/docker.list > /dev/null
sudo apt-get update
sudo apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
```

装完建议确认系统是 Ubuntu 还是 Debian（`cat /etc/os-release`），源地址要对应。

**Maven 阿里云镜像**：N4-monitor 的 Docker 构建已内置阿里云 Maven 镜像（`monitor-backend/Dockerfile` 使用 `monitor-backend/maven-settings-aliyun.xml`，`mirrorOf=*` 全量镜像中央仓库），`docker compose build` 自动走国内源，无需额外配置。若在宿主机直接跑 `mvn`（非 Docker），可手动配一次镜像：

```bash
# 宿主机直接 mvn 时配置 Maven 阿里云镜像（可选，Docker 构建已内置）
mkdir -p ~/.m2
cp /opt/N4-monitor/monitor-backend/maven-settings-aliyun.xml ~/.m2/settings.xml
```

### 开发机：一键打包

**迁移范围（设计约定）**：
- **迁移**：代码、compose 配置、N4-monitor 自建镜像、`.env` 配置、中间件**配置文件/配置目录**（如 emqx `./etc`）、FUXA 等自定义镜像、以及**数据挂载目录的结构**（空目录，保证容器启动时挂载点存在）。
- **不迁移（业务数据）**：MySQL 表数据、TDengine 时序数据、Redis 缓存、Kafka 消息、以及 EMQX **数据目录**（`./data` / named volume）里保存的运行时配置（Dashboard 里改的认证、规则、桥接等）。测试机以空库/默认状态启动，可用种子数据或 SQL 脚本初始化。

```bash
cd /opt/N4-monitor/deploy
sudo ./pack.sh                 # 生成 n4-monitor-pack-<时间戳>.tar.gz
```

脚本会：
1. `docker compose build` 重建 N4-monitor 镜像（确保打包的是最新构建）；
2. `docker save` 导出镜像：N4-monitor 自建镜像（`backend/frontend/flink`）+ 无法从 registry 拉取的自定义镜像（默认含 FUXA `frangoteam/fuxa:1.3.3`，`EXTRA_IMAGES` 可增删）+ **中间件官方镜像**（emqx/kafka/mysql/redis/tdengine，`MIDDLEWARE_IMAGES`，避免测试机从 Docker Hub 拉取超时）；
3. 收集 N4-monitor 代码仓库（git 跟踪文件 + `.env`）；
4. 收集 `/opt` 下各中间件目录（emqx/fuxa/kafka/mysql/redis/tdengine）的 compose 定义 + `.env`，并**解析 compose 里的 bind-mount 源路径**：
   - 配置类目录（如 `./etc`、`./log`、nginx conf）→ **完整复制**；
   - 数据类目录（`./data`、`mysql-data` 等，见脚本 `DATA_DIRS`）→ **只建空目录结构**，不打包内容；
5. 打包成单个 `.tar.gz`。

### 测试机：一键还原

**步骤 0：把 tar.gz 传到测试机**（方式任选，详见上节"传输"）：

```bash
# 测试机上执行（从开发机拉取，最简单；也可在开发机 scp 推送）
cd /opt
rsync -avP root@47.121.30.136:/opt/N4-monitor/n4-monitor-pack-<时间戳>.tar.gz ./
# 确认传到了
ls -lh /opt/n4-monitor-pack-<时间戳>.tar.gz
```

**步骤 1：完整解压包**（unpack.sh 在包里，必须先解压才能拿到它）：

```bash
cd /opt
sudo tar -xzf n4-monitor-pack-<时间戳>.tar.gz
# 解压后生成 N4-monitor/ 与 middleware/（若已存在会合并/覆盖）
ls -la /opt/N4-monitor/deploy/          # 应能看到 unpack.sh、pack.sh
```

> 若提示文件已存在或想彻底干净，可先 `sudo rm -rf /opt/N4-monitor /opt/middleware` 再解压（谨慎：会清掉已装的 N4-monitor）。

**步骤 2：给脚本加执行权限**（git 检出可能丢失执行位，务必执行）：

```bash
sudo chmod +x /opt/N4-monitor/deploy/unpack.sh /opt/N4-monitor/deploy/pack.sh
```

**步骤 3：运行还原**（从 deploy 目录运行，包用绝对路径）：

```bash
cd /opt/N4-monitor/deploy
sudo ./unpack.sh /opt/n4-monitor-pack-<时间戳>.tar.gz
```

脚本会：
1. `docker load` 导入全部随包镜像：N4-monitor 自建镜像 + 自定义镜像（FUXA）+ **中间件官方镜像**（emqx/kafka/mysql/redis/tdengine）——测试机无需从 Docker Hub 拉取，离线可用；
2. 把 N4-monitor 代码 + `.env` 放到 `/opt/N4-monitor`；
3. 把各中间件的 compose 定义 + 配置目录 + 空数据目录放到 `/opt/<name>`（bind-mount 挂载点已就绪）；
4. 逐个 `docker compose up -d` 启动中间件（镜像已本地加载；数据卷全新/空目录初始化）；
5. 启动 N4-monitor：镜像已从包加载则**直接 `docker compose up -d`**（不重新构建，避免测试机 Maven/npm 下载慢或超时）；仅当镜像缺失时才 `docker compose build`。

### 各中间件迁移策略（基于开发机实际 compose 核实）

pack.sh 解析 compose 的 bind-mount（支持相对路径 `./x` 与绝对路径 `/opt/<name>/x`，自动跳过 named volume 与宿主机系统路径如 `/etc/localtime`），按 `DATA_DIRS` 分类决定"完整复制"或"只建空结构"。当前实测结果：

| 中间件 | 挂载路径 | 处理 | 说明 |
|---|---|---|---|
| emqx | `/opt/emqx/data`、`/opt/emqx/log`（绝对） | 空结构 | 运行时数据 + 日志；Dashboard 里配的认证/规则/桥接**不迁移**，测试机需重配 |
| fuxa | `./appdata`（相对） | **完整复制** | FUXA 组态项目（project.fuxap.db）+ 用户，手工搭建不可再生 |
| fuxa | `./db`、`./logs` | 空结构 | DAQ 历史采集 + 日志 |
| fuxa | `./images` | 完整复制 | 基本为空 |
| kafka | named volume `kafka_data` | 不处理 | `docker compose up` 自动建空卷，消息不迁移 |
| mysql | `./conf`（相对） | **完整复制** | MySQL 配置（utf8mb4 等） |
| mysql | `./data`、`./log` | 空结构 | 数据库文件 + 日志；测试机空库，用种子 SQL 初始化 |
| redis | `/opt/redis/conf/redis.conf`（绝对） | **完整复制** | Redis 配置 |
| redis | `/opt/redis/data` | 空结构 | RDB/AOF，空库启动 |
| tdengine | `/opt/tdengine/data`、`/log`（绝对） | 空结构 | 时序数据 + 日志，空库启动 |

> 若测试机需要**保留 FUXA 组态**（通常是需要的，否则画面全丢），当前策略已默认完整复制 `./appdata`；若连 MySQL/TDengine 数据也要，需手动把这些目录改纳入完整复制（即调整迁移范围，属于"带业务数据"）。

### 后续增量更新（测试机同步最新开发成果）

```bash
# 开发机：改完代码后
cd /opt/N4-monitor
git pull                          # 或直接在开发机改
docker compose build              # 重建镜像
sudo ./deploy/pack.sh             # 重新打包（含最新镜像+代码）

# 测试机：把新包传到 /opt 后
cd /opt
sudo tar -xzf n4-monitor-pack-<时间戳>.tar.gz      # 重新解压（覆盖旧 N4-monitor/ 与 middleware/）
sudo chmod +x /opt/N4-monitor/deploy/unpack.sh
cd /opt/N4-monitor/deploy
sudo ./unpack.sh /opt/n4-monitor-pack-<时间戳>.tar.gz   # 重新还原
```

注意：重复 `unpack.sh` 会**覆盖** `/opt/N4-monitor` 和中间件 compose 定义，但**不会清掉已存在的数据卷**（`docker compose up` 对已存在卷直接复用）。所以：
- 测试机若有需要保留的数据，unpack 不会误删；
- 若想彻底重置测试库，先手动 `docker compose down -v`（会清空该中间件的卷，谨慎）。

### 测试机 IP 不同时的调整

若测试机 IP 与开发机不同，还原后需更新地址相关配置（N4-monitor 与各中间件的 `.env` 里的 IP），再 `docker compose up -d` 重建。若中间件与 N4-monitor 都在同一台测试机且用 host 网络，可统一改为 `127.0.0.1`（见"网络模式"说明，FUXA 例外：前端 iframe 需用可访问地址）。

### 关于数据挂载目录 / FUXA 镜像 / EMQX 配置

- **数据挂载目录**：pack.sh 会解析各中间件 compose 的 bind-mount（以 `./` 或 `/` 开头的挂载源），把**配置类目录完整打包**、**数据类目录（`./data` 等）打包为空结构**。unpack.sh 还原时这些目录会重建，保证容器启动时挂载点存在。若实际中间件的数据目录名不在脚本 `DATA_DIRS` 列表里，会按配置目录被**完整复制**——若不想复制其内容，在脚本里补一个目录名即可。另外注意 bind-mount 空目录的**权限**问题（容器内以非 root 用户写数据时，需 `chown` 目录属主与容器一致，否则启动报权限错误）。
- **FUXA（自定义镜像）**：`frangoteam/fuxa:1.3.3` 无法从公共 registry 拉取，pack.sh 已把 `EXTRA_IMAGES` 里的 FUXA 一并 `docker save` 导出，unpack.sh `docker load` 导入，测试机无需联网拉取。若以后有别的自定义镜像，加到 `pack.sh` 的 `EXTRA_IMAGES` 即可。
- **EMQX 配置**：EMQX 6.x 的配置分两类——**静态配置文件**（`./etc` 下的 `emqx.conf` 等，随配置目录迁移）和**运行时配置**（Dashboard/API 里改的认证、规则引擎、桥接、ACL 等，存储在 `./data` 或 named volume）。**运行时配置不会迁移**，测试机的 EMQX 以全新默认状态启动，需要重新在测试机配置认证/规则/桥接。若希望连运行时配置一起迁，需把 EMQX 的 `./data`（或对应 named volume）也纳入打包（但这就属于"带业务数据"，需调整迁移范围）。

## 八、常用运维命令

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
| ${SERVER_PORT:-8088} | 后端 API（由 .env 的 `SERVER_PORT` 统一控制，默认 8088）。compose 绑定所有网卡（`${SERVER_PORT:-8088}:${SERVER_PORT:-8088}`）；若无需直连后端调试，可改为 `127.0.0.1:${SERVER_PORT:-8088}:${SERVER_PORT:-8088}` 收敛为仅本机访问，常规流量一律走前端 Nginx 反代 |
| 1881 | FUXA 图形监控（浏览器需直接访问 iframe） |

中间件端口（3306/6379/9092/6041）只需对后端容器可达，**不建议对公网开放**。
