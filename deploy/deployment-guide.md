# 监控平台部署指南

## 一、架构概览

```
                          ┌─────────────────────────────────┐
                          │     浏览器仪表盘                  │
                          │  http://<server>:8080            │
                          └──────────────┬──────────────────┘
                                         │ ws:///ws/frontend
                          ┌──────────────▼──────────────────┐
                          │  Monitor Server (宿主机 systemd)  │
                          │  • 配置外挂 ./config/            │
                          │  • 日志输出 ./logs/              │
                          └──────────────▲──────────────────┘
                                         │ ws:///ws/client
          ┌──────────────────────────────┼──────────────────────┐
          │                              │                      │
┌─────────▼─────────┐  ┌────────────────▼──────┐  ┌───────────▼───────────┐
│ Monitor Client    │  │ Monitor Client         │  │ Monitor Client        │
│ Server-01 (systemd)│  │ Server-02 (systemd)    │  │ Server-03 (systemd)   │
│ • 开机自启         │  │ • 开机自启              │  │ • 开机自启             │
│ • 自动重连         │  │ • 自动重连              │  │ • 自动重连             │
└───────────────────┘  └───────────────────────┘  └───────────────────────┘
```

---

## 二、环境要求

| 组件 | 要求 | 说明 |
|------|------|------|
| JDK | 11+ | Server + Client 运行环境 |
| systemd | 任意版本 | 服务管理 + 开机自启 |
| 端口 8080 | 放行 | Server HTTP + WebSocket |

---

## 三、项目构建

在项目根目录 `monitor-platform/` 执行：

```bash
mvn clean package -DskipTests
```

构建产物：
```
monitor-server/target/monitor-server-1.0.0-SNAPSHOT.jar   (~18 MB)
monitor-client/target/monitor-client-1.0.0-SNAPSHOT.jar   (~39 MB)
```

---

## 四、服务端部署（宿主机）

### 4.1 部署文件结构

```
deploy/server/
├── monitor-server.service      # systemd 服务文件
├── scripts/
│   └── install-server.sh       # 一键安装脚本
├── config/
│   └── application.yml         # ★ 外挂配置（修改后 restart 生效）
└── logs/                       # ★ 日志目录（部署后自动生成）
```

### 4.2 一键安装

```bash
cd deploy/server/scripts

# 复制 JAR
cp ../../../monitor-server/target/monitor-server-1.0.0-SNAPSHOT.jar .

# 执行安装
chmod +x install-server.sh
bash install-server.sh
```

### 4.3 手动部署

```bash
# 1. 创建目录
mkdir -p /usr/local/monitor-platform/monitor-server/config
mkdir -p /usr/local/monitor-platform/monitor-server/logs

# 2. 部署文件
cp monitor-server-1.0.0-SNAPSHOT.jar /usr/local/monitor-platform/monitor-server/
cp config/application.yml /usr/local/monitor-platform/monitor-server/config/

# 3. 注册服务
cp monitor-server.service /etc/systemd/system/
systemctl daemon-reload
systemctl enable monitor-server
systemctl start monitor-server
```

### 4.4 配置修改

外挂配置文件路径：`/usr/local/monitor-platform/monitor-server/config/application.yml`

```bash
vim /usr/local/monitor-platform/monitor-server/config/application.yml
systemctl restart monitor-server
```

### 4.5 systemd 常用操作

```bash
systemctl status monitor-server          # 查看状态
systemctl start monitor-server           # 启动
systemctl stop monitor-server            # 停止
systemctl restart monitor-server         # 重启
systemctl enable monitor-server          # 开机自启
systemctl disable monitor-server         # 取消自启
journalctl -u monitor-server -f          # 实时日志
journalctl -u monitor-server -n 100      # 最近100条
```

---

## 五、客户端部署（宿主机 systemd）

### 5.1 部署文件结构

```
deploy/client/
├── monitor-client.service       # systemd 服务文件
├── config/
│   └── application.yml          # ★ 客户端配置（部署前修改 server-url）
└── scripts/
    └── install-client.sh        # 一键安装脚本
```

### 5.2 一键安装

```bash
cd deploy/client/scripts

# 复制 JAR
cp ../../../monitor-client/target/monitor-client-1.0.0-SNAPSHOT.jar .

# ★ 编辑配置
vim ../config/application.yml

# 执行安装
chmod +x install-client.sh
bash install-client.sh
```

### 5.3 手动部署

```bash
mkdir -p /opt/monitor-platform/monitor-client/config
mkdir -p /opt/monitor-platform/logs

cp monitor-client-1.0.0-SNAPSHOT.jar /opt/monitor-platform/monitor-client/
cp config/application.yml /opt/monitor-platform/monitor-client/config/

cp monitor-client.service /etc/systemd/system/
systemctl daemon-reload
systemctl enable monitor-client
systemctl start monitor-client
```

### 5.4 客户端配置说明

编辑 `/opt/monitor-platform/monitor-client/config/application.yml`：

```yaml
monitor:
  client:
    client-id:                     # 留空自动取主机名
    server-url: ws://10.1.0.228:8080/ws/client  # ★ 指向 Server 地址

    system-enabled: true
    docker-enabled: true           # 需要时开启
    docker-host: unix:///var/run/docker.sock
    microservice-enabled: true     # 需要时开启
    microservices:
      - name: my-service
        health-url: http://localhost:8081/actuator/health
        port: 8081
```

### 5.5 systemd 常用操作

```bash
systemctl status monitor-client
systemctl restart monitor-client
journalctl -u monitor-client -f
```

---

## 六、防火墙配置

```bash
# firewalld
firewall-cmd --add-port=8080/tcp --permanent
firewall-cmd --reload

# iptables
iptables -A INPUT -p tcp --dport 8080 -j ACCEPT
```

---

## 七、验证部署

### 7.1 验证服务端

```bash
systemctl status monitor-server          # active (running)
curl http://localhost:8080               # 返回 HTML
```

### 7.2 验证客户端

```bash
systemctl status monitor-client          # active (running)
journalctl -u monitor-client -f          # 查看连接日志
```

### 7.3 浏览器访问

打开 `http://<server-ip>:8080`，观察仪表盘是否显示在线节点和数据。

---

## 八、更新升级

```bash
# 1. 构建
cd monitor-platform && mvn clean package -DskipTests

# 2. 更新 Server
systemctl stop monitor-server
cp monitor-server/target/monitor-server-1.0.0-SNAPSHOT.jar \
   /usr/local/monitor-platform/monitor-server/
systemctl start monitor-server

# 3. 更新 Client（在目标服务器上）
systemctl stop monitor-client
cp monitor-client/target/monitor-client-1.0.0-SNAPSHOT.jar \
   /opt/monitor-platform/monitor-client/
systemctl start monitor-client
```

---

## 九、双机部署快速参考

假设 Server 部署在 `10.1.0.228`，以下是在各节点上的操作汇总：

| 节点 | 部署角色 | 关键操作 |
|------|----------|----------|
| 10.1.0.228 | Server | `bash install-server.sh` → 访问 `http://10.1.0.228:8080` |
| 其他服务器 | Client | 编辑 `config/application.yml` 中 `server-url` → `bash install-client.sh` |

Client 的 `server-url` 配置：
```yaml
monitor:
  client:
    server-url: ws://10.1.0.228:8080/ws/client
```

---

## 十、故障排查

### 服务启动失败

```bash
journalctl -u monitor-server -n 50 --no-pager
journalctl -u monitor-client -n 50 --no-pager
```

### 客户端无法连接

```bash
# 检查 Server 可达性
curl http://<server-ip>:8080

# 查看 Client 日志
journalctl -u monitor-client -f
```

### 仪表盘无数据

1. 确认 Server 运行：`systemctl status monitor-server`
2. 确认 Client 运行：`systemctl status monitor-client`
3. 浏览器 F12 → Network → WS → 查看 `/ws/frontend` 连接状态

### 端口被占用

```bash
# 查看 8080 端口占用
netstat -tlnp | grep 8080

# 修改 Server 端口
vim /usr/local/monitor-platform/monitor-server/config/application.yml
# 改 server.port 为其他端口
systemctl restart monitor-server
```

---

## 附：Docker 部署（备选）

如需容器化部署 Server，参考 `deploy/server/` 下的 Dockerfile 和 docker-compose.yml：

```bash
cd deploy/server
cp ../../monitor-server/target/monitor-server-1.0.0-SNAPSHOT.jar .
docker-compose up -d --build
```
