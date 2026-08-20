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
mvn clean compile

# 打包（生成可执行 JAR，跳过测试）
mvn clean package -DskipTests
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

节点配置和告警规则使用 MyBatis-Plus 持久化到 PostgreSQL 12。默认连接配置为：

```yaml
url: jdbc:postgresql://192.168.222.128:55432/monitor_platform
username: szh
password: Szh,111111
```

首次部署请在 PostgreSQL 上创建数据库（已有同名数据库可跳过）：

```sql
CREATE DATABASE monitor_platform OWNER szh;
```

也可以通过环境变量覆盖连接信息：`MONITOR_DB_URL`、`MONITOR_DB_NAME`、`MONITOR_DB_USERNAME`、`MONITOR_DB_PASSWORD`。服务启动时会自动执行 `monitor-server/src/main/resources/schema.sql`，创建 `monitor_node`、`monitor_alert_rule` 和 `monitor_alert_event` 表。

告警事件会在首次超过预警阈值时创建，持续期间更新 `last_seen_at` 和 `duration_seconds`，指标恢复后写入 `resolved_at` 并将状态标记为 `RESOLVED`。即使没有浏览器打开，服务端也会每 5 秒后台评估一次。

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
