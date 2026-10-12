# 监控平台部署文档

## 一、环境要求

| 组件 | 版本要求 | 说明 |
|------|----------|------|
| JDK | 11+ | 编译与运行环境 |
| Maven | 3.6+ | 项目构建工具 |
| Docker | 20.10+ (可选) | 仅 Docker 监控功能需要 |
| 操作系统 | Linux / Windows | 客户端支持跨平台部署 |

---

## 二、项目构建

### 2.1 获取源码

```bash
cd monitor-platform
```

### 2.2 编译打包

```bash
# 编译（只编译不打包）
mvn clean compile -Dmaven.repo.local=D:\code\apache-maven-3.8.8\repository

# 打包（生成可执行 JAR，跳过测试）
mvn clean package -DskipTests -Dmaven.repo.local=D:\code\apache-maven-3.8.8\repository
```

构建产物位置：

| 模块 | JAR 路径 |
|------|----------|
| 公共模块 | `monitor-common/target/monitor-common-1.0.0-SNAPSHOT.jar` |
| 采集客户端 | `monitor-client/target/monitor-client-1.0.0-SNAPSHOT.jar` |
| 汇总服务端 | `monitor-server/target/monitor-server-1.0.0-SNAPSHOT.jar` |

---

## 三、服务端部署

服务端负责接收各客户端上报的指标数据，并通过 REST/WebSocket 向独立前端提供数据。

### 3.1 启动服务端

```bash
cd monitor-server
java -jar target/monitor-server-1.0.0-SNAPSHOT.jar
```

默认监听端口 **8080**。

### 3.2 自定义配置

```bash
# 修改端口
java -jar target/monitor-server-1.0.0-SNAPSHOT.jar --server.port=9090

# 或通过外部配置文件
java -jar target/monitor-server-1.0.0-SNAPSHOT.jar --spring.config.location=/path/to/application.yml
```

### 3.3 验证服务端

启动后可通过以下地址确认服务正常运行：

- **REST 快照**：`http://<server-ip>:8080/api/monitor/snapshot`
- **REST 健康检查**：`http://<server-ip>:8080/api/monitor/health`
- **WebSocket 客户端端点**：`ws://<server-ip>:8080/ws/client`
- **WebSocket 前端端点**：`ws://<server-ip>:8080/ws/frontend`

服务端不再内置前端页面，前端请按下文“前端启动”单独运行。

### 3.4 PostgreSQL 12 数据库

节点配置和告警规则使用 MyBatis-Plus 持久化到 PostgreSQL 12。连接信息请通过部署环境注入：

```yaml
MONITOR_DB_URL=jdbc:postgresql://192.168.222.128:55432/monitor_platform
MONITOR_DB_USERNAME=szh
MONITOR_DB_PASSWORD=请通过部署环境注入
```

首次部署请在 PostgreSQL 上创建数据库（已有同名数据库可跳过）：

```sql
CREATE DATABASE monitor_platform OWNER szh;
```

也可以通过环境变量覆盖连接信息：`MONITOR_DB_URL`、`MONITOR_DB_NAME`、`MONITOR_DB_USERNAME`、`MONITOR_DB_PASSWORD`。服务启动时会自动执行 `monitor-server/src/main/resources/schema.sql`，创建 `monitor_node`、`monitor_alert_rule` 和 `monitor_alert_event` 表。

瀚高数据库监控按安全版 V4.5 实现。运行期只使用长期账号 `szh`，不读取二进制 `hgaudit` 文件、不调用 `hgaudit_imp`，也不保存 `sysdba`、`syssao`、`syssso` 的密码。

登录失败从普通数据库文本日志增量采集。客户端为每个日志文件持久化读取偏移量，每轮最多读取 `highgo-log-max-read-bytes-per-cycle` 字节；大文件会分多轮读取，不会全量载入内存。数据库每天创建新日志文件时，新文件自动从头读取，旧文件未读完的内容仍会继续补读。客户端首次接管已有目录时从现有文件末尾建立基线，避免把数 GB 历史日志重新统计。

普通日志必须包含认证失败信息。先由 DBA 确认日志目录和日志参数：

```sql
SHOW log_directory;
SHOW log_filename;
SHOW logging_collector;
SHOW log_connections;
```

客户端配置实际目录；该目录不是 `$PGDATA/hgaudit`：

```yaml
highgo-url: jdbc:highgo://192.168.140.128:5866/kcc_main
highgo-username: szh
node-ip-address: 192.168.140.128
highgo-server-log-directory: /实际数据目录/log
highgo-server-log-pattern: "*.{log,csv}"
highgo-login-failure-window-hours: 24
highgo-large-query-window-minutes: 60
highgo-log-max-read-bytes-per-cycle: 1048576
highgo-collector-state-path: /opt/monitor-platform/data/highgo-collector.state
```

容器部署客户端时，需要把普通日志目录只读挂载进容器，并把状态目录持久化：

```yaml
volumes:
  - /实际数据目录/log:/var/log/highgo:ro
  - /opt/monitor-platform/data:/opt/monitor-platform/data
environment:
  HIGHGO_SERVER_LOG_DIRECTORY: /var/log/highgo
```

登录失败和大表全表查询统一从瀚高 `csvlog` 增量读取。登录失败按 SQLSTATE `28P01/28000` 及配置的认证失败文本识别；大表查询匹配 `SELECT * FROM ...`，再连接对应数据库查询表的实际大小。由于日志可能持续增长，客户端按文件偏移、轮转和单轮最大字节数处理，不会一次性加载整个文件。

大表查询日志采集要求 `log_statement=all`，并建议在专用监控窗口验证后评估日志量、磁盘空间和敏感 SQL 暴露风险。`pg_stat_statements` 不再是大表查询告警的必需依赖。

历史版本曾使用 `pg_stat_statements` 累计快照采集大表查询；当前版本已切换为 CSV 日志方案，因此不再要求预加载该扩展。若现场仍保留旧扩展，不影响当前日志采集。下面的内容仅用于兼容检查，不是当前大表告警的前置条件：

```sql
SHOW shared_preload_libraries;
```

若仍需要使用旧版统计方案，才需要包含 `pg_stat_statements`，并由 DBA 修改参数后重启实例。当前日志方案不需要执行下面的扩展安装脚本：

```bash
HIGHGO_EXECUTION_USER=sysdba \
PSQL_BIN=/opt/highgo/hgdb-4.5/bin/psql \
bash deploy/client/scripts/install-highgo-pg-stat-statements.sh szh kcc_main
```

日志方案中的客户端地址、用户、数据库和 SQL 来自 CSV 日志；页面不会伪造日志中不存在的字段。

原来的审计表函数和安装脚本已从项目中移除。数据库中已部署的旧函数不会被程序调用；切换完成后可由 DBA 删除。`hg_audit` 是否继续开启由安全审计制度决定，不影响本监控方案。

`highgo-url` 中的数据库仅作为实例发现入口，不代表监控范围。客户端按照 `highgo-collect-interval-ms` 持续定时采集，默认每 5 秒刷新一次：连接数、容量、锁等待和缓存命中率等实例指标每周期查询一次；登录失败和大表查询均增量读取本机 CSV 日志。

告警事件会在首次超过预警阈值时创建，持续期间更新 `last_seen_at` 和 `duration_seconds`，指标恢复后写入 `resolved_at` 并将状态标记为 `RESOLVED`。即使没有浏览器打开，服务端也会每 5 秒后台评估一次。

大表查询属于离散安全事件。客户端会在 `highgo-large-query-window-minutes` 配置窗口内持续上报最近事件，服务端通过 `monitor.server.discrete-alert-hold-minutes` 保证活动告警不会被下一轮空采集立即恢复。告警会持久化到 `monitor_alert_event` 表，其中 `started_at` 记录告警时间、`metric` 记录事件类型、`message` 记录数据库、表名、表大小及 SQL 等事件内容。

### 3.5 服务端配置参考

```yaml
# application.yml
server:
  port: 8080                      # 监听端口

monitor:
  server:
    push-interval: 3000           # 推送到前端的间隔（毫秒）
    client-timeout: 30000         # 客户端超时判定（毫秒），超时未收到数据则判定离线
```

### 3.5 使用 systemd 托管（Linux）

创建服务文件 `/etc/systemd/system/monitor-server.service`：

```ini
[Unit]
Description=Monitor Server
After=network.target

[Service]
Type=simple
User=root
WorkingDirectory=/opt/monitor-platform/monitor-server
ExecStart=/usr/bin/java -jar /opt/monitor-platform/monitor-server/target/monitor-server-1.0.0-SNAPSHOT.jar
Restart=on-failure
RestartSec=10

[Install]
WantedBy=multi-user.target
```

```bash
# 启用并启动
systemctl daemon-reload
systemctl enable monitor-server
systemctl start monitor-server

# 查看状态
systemctl status monitor-server

# 查看日志
journalctl -u monitor-server -f
```

---

## 四、客户端部署

将客户端 JAR 部署到每一台需要监控的服务器上。客户端支持三种监控模式，可按需启停。

### 4.1 启动客户端（基础模式）

```bash
cd monitor-client
java -jar target/monitor-client-1.0.0-SNAPSHOT.jar \
  --monitor.client.server-url=ws://<server-ip>:8080/ws/client
```

### 4.2 启用全部监控功能

```bash
java -jar target/monitor-client-1.0.0-SNAPSHOT.jar \
  --monitor.client.client-id=prod-server-01 \
  --monitor.client.server-url=ws://192.168.1.100:8080/ws/client \
  --monitor.client.system-enabled=true \
  --monitor.client.docker-enabled=true \
  --monitor.client.docker-host=unix:///var/run/docker.sock \
  --monitor.client.microservice-enabled=true \
  --monitor.client.microservices[0].name=user-service \
  --monitor.client.microservices[0].health-url=http://localhost:8081/actuator/health \
  --monitor.client.microservices[0].port=8081 \
  --monitor.client.microservices[1].name=order-service \
  --monitor.client.microservices[1].health-url=http://localhost:8082/actuator/health \
  --monitor.client.microservices[1].port=8082
```

### 4.3 客户端完整配置参考

```yaml
# application.yml
monitor:
  client:
    # ----- 基础配置 -----
    client-id:                     # 客户端唯一标识（留空自动取主机名）
    server-url: ws://localhost:8080/ws/client  # 服务端 WebSocket 地址

    # ----- 系统监控 -----
    system-enabled: true           # 启用 CPU/内存/磁盘/网络 采集

    # ----- Docker 监控 -----
    docker-enabled: false          # 启用 Docker 容器状态采集
    docker-host: unix:///var/run/docker.sock   # Docker 守护进程地址
    # Linux 默认: unix:///var/run/docker.sock
    # Windows 需开启 TCP: tcp://localhost:2375

    # ----- 微服务监控 -----
    microservice-enabled: false    # 启用微服务健康检查
    microservices:                 # 需要监控的微服务列表
      - name: user-service
        health-url: http://localhost:8081/actuator/health
        port: 8081
      - name: order-service
        health-url: http://localhost:8082/actuator/health
        port: 8082
      - name: gateway-service
        health-url: http://localhost:8080/actuator/health
        port: 8080
```

### 4.4 使用 systemd 托管客户端（Linux）

创建 `/etc/systemd/system/monitor-client.service`：

```ini
[Unit]
Description=Monitor Client
After=network.target

[Service]
Type=simple
User=root
WorkingDirectory=/opt/monitor-platform/monitor-client
ExecStart=/usr/bin/java -jar /opt/monitor-platform/monitor-client/target/monitor-client-1.0.0-SNAPSHOT.jar \
  --monitor.client.server-url=ws://192.168.1.100:8080/ws/client \
  --monitor.client.docker-enabled=true \
  --monitor.client.microservice-enabled=true
Restart=always
RestartSec=15

[Install]
WantedBy=multi-user.target
```

```bash
systemctl daemon-reload
systemctl enable monitor-client
systemctl start monitor-client
```

---

## 五、Docker 监控配置详解

### 5.1 Linux 环境

客户端默认通过 Unix Socket 与 Docker 守护进程通信：

```bash
# 确保 Java 进程有权限访问 Docker Socket
usermod -aG docker <运行用户>

# 验证 Docker 连接
docker info
```

配置中使用 `unix:///var/run/docker.sock`（默认值）。

### 5.2 Windows 环境

需先开启 Docker Desktop 的 TCP 端口：

1. Docker Desktop → Settings → General
2. 勾选 **"Expose daemon on tcp://localhost:2375 without TLS"**
3. 重启 Docker Desktop

客户端配置：

```bash
java -jar monitor-client-1.0.0-SNAPSHOT.jar \
  --monitor.client.docker-enabled=true \
  --monitor.client.docker-host=tcp://localhost:2375
```

### 5.3 远程 Docker 主机

```bash
java -jar monitor-client-1.0.0-SNAPSHOT.jar \
  --monitor.client.docker-enabled=true \
  --monitor.client.docker-host=tcp://192.168.1.50:2375
```

> ⚠️ 生产环境中建议使用 TLS 保护 Docker API 通信。

---

## 六、微服务健康检查配置

### 6.1 Spring Boot 微服务

确保目标微服务引入了 Actuator：

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
```

并暴露健康端点（`application.yml`）：

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health
  endpoint:
    health:
      show-details: always
```

### 6.2 配置健康检查地址

```yaml
monitor:
  client:
    microservice-enabled: true
    microservices:
      - name: ai-chat-service           # 服务名称（自定义）
        health-url: http://localhost:8081/actuator/health
        port: 8081
      - name: ai-file-service
        health-url: http://localhost:8082/actuator/health
        port: 8082
```

### 6.3 非 Spring Boot 服务

对于没有 Actuator 的服务，可以配置任意 HTTP GET 端点，只要返回 2xx/3xx 即判定为健康：

```yaml
microservices:
  - name: nginx-gateway
    health-url: http://localhost:80/health
    port: 80
  - name: node-api
    health-url: http://localhost:3000/api/health
    port: 3000
```

---

## 七、独立前端启动与使用

前端代码位于 `monitor-web`，技术栈为 Vue 2 + Element UI + Vite + Node.js。

```bash
cd monitor-web
npm install
npm run dev
```

开发模式默认访问 `http://localhost:3000`，Vite 会将 `/api`、`/ws` 代理到 `http://localhost:8080`。生产构建：

```bash
npm run build
npm run preview
```

生产环境可使用 `VITE_API_BASE=https://monitor.example.com npm run build` 指定后端地址，再将 `monitor-web/dist` 部署到 Nginx、Node.js 静态服务器或 CDN。

前端包含两个入口：

| 页面 | 路由 | 内容 |
|------|------|------|
| 实时总览 | `/index.html` | 独立展示入口；节点在线状态、系统指标、Docker 容器、微服务健康检查，WebSocket 实时刷新 |
| 后台管理 | `/admin.html` | 独立管理入口；节点展示名称/可见性、告警阈值、刷新间隔、服务端健康检查 |

## 八、前端仪表盘功能

服务端与前端均启动后，浏览器访问 `http://<frontend-host>:3000`（开发模式）或前端部署地址即可打开监控仪表盘。

### 仪表盘功能

| 区域 | 内容 |
|------|------|
| 顶部状态栏 | WebSocket 连接状态、在线节点数 |
| 系统指标 Tab | CPU/内存/磁盘/网络/JVM 使用率（含进度条） |
| Docker Tab | 容器总数/运行/停止/异常 + 容器详情表格 |
| 微服务 Tab | 服务总数/健康/异常 + 响应时间表格 |

### 连接状态说明

| 状态 | 含义 |
|------|------|
| 🟢 已连接 | WebSocket 连接正常 |
| 🔴 已断开 | 连接中断（3 秒自动重连） |

---

## 九、防火墙与网络

### 9.1 端口清单

| 端口 | 方向 | 用途 |
|------|------|------|
| 8080 | 入站 | 服务端 HTTP + WebSocket |
| 2375 | 本机 | Docker API（仅 Windows 需开启） |

### 9.2 Linux 防火墙放行

```bash
# firewalld
firewall-cmd --add-port=8080/tcp --permanent
firewall-cmd --reload

# iptables
iptables -A INPUT -p tcp --dport 8080 -j ACCEPT
```

---

## 十、数据采集频率说明

| 指标类型 | 采集间隔 | 配置位置 |
|----------|----------|----------|
| 系统指标 | 5 秒 | `CollectScheduler.java` @Scheduled(fixedDelay=5000) |
| Docker 指标 | 15 秒 | `CollectScheduler.java` @Scheduled(fixedDelay=15000) |
| 微服务指标 | 10 秒 | `CollectScheduler.java` @Scheduled(fixedDelay=10000) |
| 前端推送 | 3 秒 | `PushScheduler.java` @Scheduled(fixedDelay=3000) |

> 可根据实际需求修改 `CollectScheduler` 和 `PushScheduler` 中的 `@Scheduled` 注解参数。

---

## 十一、故障排查

### 11.1 客户端无法连接服务端

```bash
# 检查服务端是否启动
curl http://<server-ip>:8080/api/monitor/health

# 检查 WebSocket 端点
# 浏览器控制台执行：
# new WebSocket('ws://<server-ip>:8080/ws/client')
```

### 11.2 系统指标采集为空

- 确认 `system-enabled: true`
- 客户端日志查看是否有 OSHI 相关错误

### 11.3 Docker 采集失败

- Linux：确认用户有 Docker Socket 读取权限（`docker ps` 可正常执行）
- Windows：确认已开启 TCP 端口（参考 5.2 节）
- 查看服务端日志：采集失败时 `totalContainers` 返回 `-1`

### 11.4 微服务健康检查失败

- 确认健康检查 URL 可从客户端服务器直接访问
- 测试命令：`curl http://localhost:8081/actuator/health`
- 检查超时设置（默认 5 秒连接超时 + 5 秒读取超时）

### 11.5 前端页面显示「暂无在线节点」

1. 确认服务端已启动
2. 确认至少有一个客户端已连接并上报数据
3. 打开浏览器开发者工具控制台，查看 WebSocket 连接状态
4. 检查是否有防火墙阻拦

### 11.6 查看日志

```bash
# 客户端/服务端启动时添加日志参数
java -jar monitor-client-1.0.0-SNAPSHOT.jar --logging.level.com.monitor=DEBUG

# systemd 方式
journalctl -u monitor-client -f
journalctl -u monitor-server -f
```

---

## 十二、典型部署拓扑

```
                          ┌─────────────────┐
                          │   浏览器前端      │
                          │  http://:3000    │
                          └────────┬────────┘
                                   │ ws:///ws/frontend
                          ┌────────▼────────┐
                          │  Monitor Server  │
                          │    :8080         │
                          └────────▲────────┘
                                   │ ws:///ws/client
          ┌────────────────────────┼────────────────────┐
          │                        │                    │
┌─────────▼──────┐   ┌─────────────▼──────┐   ┌────────▼──────┐
│ Monitor Client │   │  Monitor Client    │   │Monitor Client │
│  Server-01     │   │  Server-02         │   │  Server-03    │
│ ┌───────────┐  │   │ ┌───────────┐      │   │ ┌───────────┐ │
│ │ 系统指标   │  │   │ │ 系统指标   │      │   │ │ 系统指标   │ │
│ │ Docker     │  │   │ │ Docker     │      │   │ │ 微服务健康 │ │
│ │ 微服务健康  │  │   │ │ 微服务健康  │      │   │ └───────────┘ │
│ └───────────┘  │   │ └───────────┘      │   └──────────────┘
└────────────────┘   └────────────────────┘
```

---

## 十三、快速启动脚本

### 服务端启动脚本 `start-server.sh`

```bash
#!/bin/bash
APP_DIR=$(cd "$(dirname "$0")" && pwd)
JAR_FILE="$APP_DIR/../monitor-server/target/monitor-server-1.0.0-SNAPSHOT.jar"
CONFIG="$APP_DIR/../monitor-server/src/main/resources/application.yml"

if [ ! -f "$JAR_FILE" ]; then
    echo "未找到 JAR 文件，请先执行 mvn clean package -DskipTests"
    exit 1
fi

echo "启动 Monitor Server..."
java -jar "$JAR_FILE" --spring.config.location="$CONFIG"
```

### 客户端启动脚本 `start-client.sh`

```bash
#!/bin/bash
APP_DIR=$(cd "$(dirname "$0")" && pwd)
JAR_FILE="$APP_DIR/../monitor-client/target/monitor-client-1.0.0-SNAPSHOT.jar"
SERVER_URL="${MONITOR_SERVER_URL:-ws://localhost:8080/ws/client}"
CLIENT_ID="${MONITOR_CLIENT_ID:-$(hostname)}"

if [ ! -f "$JAR_FILE" ]; then
    echo "未找到 JAR 文件，请先执行 mvn clean package -DskipTests"
    exit 1
fi

echo "启动 Monitor Client [$CLIENT_ID] -> $SERVER_URL"
java -jar "$JAR_FILE" \
  --monitor.client.client-id="$CLIENT_ID" \
  --monitor.client.server-url="$SERVER_URL"
```
