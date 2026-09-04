<template>
  <section>
    <div class="page-heading">
      <div>
        <p class="eyebrow">LIVE TELEMETRY / {{ formatTime(snapshot.timestamp) }}</p>
        <h1>实时运行总览</h1>
        <p class="subtext">聚合查看所有采集节点的系统、容器、微服务与数据库状态。</p>
      </div>
      <div class="connection" :class="connected ? 'is-online' : 'is-offline'">
        <span class="pulse" />{{ connected ? '实时连接中' : '连接断开，自动重试' }}
      </div>
    </div>
    <div class="summary-grid">
      <div class="summary-card summary-card--accent">
        <span>在线节点</span><strong>{{ clients.length }}</strong
        ><small>当前有数据上报的客户端</small>
      </div>
      <div class="summary-card">
        <span>运行容器</span><strong>{{ totalRunning }}</strong
        ><small>来自 {{ clientsWithDocker }} 个节点</small>
      </div>
      <div class="summary-card">
        <span>健康微服务</span><strong>{{ healthyServices }}</strong
        ><small>共 {{ totalServices }} 个检查项</small>
      </div>
      <div class="summary-card">
        <span>数据刷新</span><strong>{{ lastUpdateLabel }}</strong
        ><small>WebSocket / 3 秒推送</small>
      </div>
    </div>
    <div class="display-alerts" :class="{ 'display-alerts--normal': !activeAlerts.length }">
      <div class="display-alerts__title">
        <span><i class="el-icon-bell" /> 告警状态</span
        ><el-tag size="mini" :type="activeAlerts.length ? 'danger' : 'success'">{{
          activeAlerts.length ? `${activeAlerts.length} 条待处理` : '运行正常'
        }}</el-tag>
      </div>
      <div v-if="activeAlerts.length" class="display-alert-list">
        <div
          v-for="event in activeAlerts.slice(0, alertDisplayLimit)"
          :key="alertKey(event)"
          class="display-alert"
          :class="{ 'display-alert--critical': event.level === 'CRITICAL' }"
        >
          <i class="el-icon-warning-outline" /><span>{{ event.message }}</span
          ><small>{{ event.level }}</small>
        </div>
        <div v-if="activeAlerts.length > alertDisplayLimit" class="display-alert-more">
          还有
          {{ activeAlerts.length - alertDisplayLimit }}
          条告警，请前往后台管理查看
        </div>
      </div>
      <div v-else class="display-alert-normal">当前没有触发的告警规则</div>
    </div>
    <div v-if="!clients.length" class="empty-state">
      <i class="el-icon-connection" />
      <h3>等待采集节点上线</h3>
      <p>请启动 monitor-client，数据将通过 WebSocket 自动出现在这里。</p>
      <el-button type="primary" plain @click="loadSnapshot">立即刷新</el-button>
    </div>
    <div v-for="client in clients" :key="client.clientId" class="client-panel">
      <div class="client-panel__head">
        <div>
          <span class="node-dot" /><b>{{ displayName(client) }}</b
          ><code>{{ client.clientId }}</code>
        </div>
        <el-tag size="mini" type="success">ONLINE</el-tag>
      </div>
      <div class="tabs">
        <button
          v-for="tab in visibleTabs(client)"
          :key="tab.key"
          :class="{ active: activeTabs[client.clientId] === tab.key }"
          type="button"
          @click="selectTab(client.clientId, tab.key)"
        >
          {{ tab.label }}
        </button>
      </div>
      <div v-if="activeTabs[client.clientId] === 'system'" class="metrics-grid">
        <metric-card v-for="item in systemMetrics(client)" :key="item.label" v-bind="item" />
      </div>
      <div v-else-if="activeTabs[client.clientId] === 'docker'">
        <div v-if="!hasDocker(client)" class="panel-empty">Docker 未启用或暂无采集数据</div>
        <template v-else
          ><div class="metrics-grid compact">
            <metric-card
              label="容器总数"
              :value="docker(client).totalContainers"
              unit="个"
              icon="el-icon-box"
            /><metric-card
              label="运行中"
              :value="docker(client).runningContainers"
              unit="个"
              tone="green"
              icon="el-icon-video-play"
            /><metric-card
              label="异常"
              :value="docker(client).unhealthyContainers"
              unit="个"
              tone="red"
              icon="el-icon-warning"
            />
          </div>
          <data-table :columns="dockerColumns" :rows="docker(client).containers"
        /></template>
      </div>
      <div v-else-if="activeTabs[client.clientId] === 'services'">
        <div v-if="!hasServices(client)" class="panel-empty">微服务监控未启用或暂无采集数据</div>
        <template v-else
          ><div class="metrics-grid compact">
            <metric-card
              label="服务总数"
              :value="services(client).totalServices"
              unit="个"
              icon="el-icon-s-grid"
            /><metric-card
              label="健康"
              :value="services(client).healthyServices"
              unit="个"
              tone="green"
              icon="el-icon-success"
            /><metric-card
              label="异常"
              :value="services(client).unhealthyServices"
              unit="个"
              tone="red"
              icon="el-icon-warning"
            />
          </div>
          <data-table :columns="serviceColumns" :rows="services(client).services"
        /></template>
      </div>
      <div v-else>
        <div v-if="!hasHighgo(client)" class="panel-empty">瀚高数据库监控未启用或暂无采集数据</div>
        <template v-else
          ><div class="metrics-grid compact">
            <metric-card
              label="连接数"
              :value="highgo(client).connectionCount"
              unit="个"
              icon="el-icon-connection"
            /><metric-card
              label="活跃连接"
              :value="highgo(client).activeConnectionCount"
              unit="个"
              tone="green"
              icon="el-icon-link"
            /><metric-card
              label="连接负载"
              :value="num(highgo(client).load)"
              unit="%"
              :progress="highgo(client).load"
              :tone="tone(highgo(client).load)"
              icon="el-icon-odometer"
            /><metric-card
              label="登录失败"
              :value="highgo(client).loginFailureCount"
              unit="次"
              tone="red"
              icon="el-icon-warning"
            />
          </div>
          <data-table :columns="highgoColumns" :rows="highgo(client).repeatedLargeTableQueries"
        /></template>
      </div>
    </div>
  </section>
</template>

<script>
import DataTable from '../components/DataTable.vue'
import MetricCard from '../components/MetricCard.vue'
import {
  getActiveAlerts,
  getPublicNodeConfigs,
  getSnapshot,
  websocketUrl
} from '../services/monitor'

const ALERT_REFRESH_INTERVAL = 5000
const RECONNECT_INTERVAL = 3000
const ALERT_DISPLAY_LIMIT = 4
const WARNING_THRESHOLD = 70
const CRITICAL_THRESHOLD = 90

/** 兼容旧版总览页面，按节点展示系统、容器、微服务和数据库指标。 */
export default {
  components: { DataTable, MetricCard },
  data() {
    return {
      snapshot: { clientSnapshots: [], onlineClients: [] },
      activeAlerts: [],
      connected: false,
      socket: null,
      reconnectTimer: null,
      alertTimer: null,
      lastUpdate: null,
      activeTabs: {},
      nodeConfigs: {},
      alertDisplayLimit: ALERT_DISPLAY_LIMIT,
      settings: { warning: WARNING_THRESHOLD, critical: CRITICAL_THRESHOLD },
      tabs: [
        { key: 'system', label: '系统指标' },
        { key: 'docker', label: 'Docker 容器' },
        { key: 'services', label: '微服务' },
        { key: 'highgo', label: '瀚高数据库' }
      ],
      dockerColumns: [
        { key: 'name', label: '容器' },
        { key: 'image', label: '镜像' },
        { key: 'status', label: '状态', type: 'status' },
        { key: 'cpuUsage', label: 'CPU %' },
        { key: 'memoryUsageMB', label: '内存 MB' },
        { key: 'ports', label: '端口' }
      ],
      serviceColumns: [
        { key: 'serviceName', label: '服务' },
        { key: 'serviceUrl', label: '检查地址' },
        { key: 'healthStatus', label: '状态', type: 'status' },
        { key: 'responseTimeMs', label: '响应 ms' },
        { key: 'errorMessage', label: '错误信息' }
      ],
      highgoColumns: [
        { key: 'userName', label: '用户' },
        { key: 'clientAddress', label: '客户端' },
        { key: 'query', label: 'SQL' },
        { key: 'occurrences', label: '执行次数' }
      ]
    }
  },
  computed: {
    clients() {
      return (this.snapshot.clientSnapshots || []).filter(
        (client) =>
          !this.nodeConfigs[client.clientId] || this.nodeConfigs[client.clientId].enabled !== false
      )
    },
    totalRunning() {
      return this.clients.reduce(
        (total, client) => total + Number(this.docker(client).runningContainers || 0),
        0
      )
    },
    clientsWithDocker() {
      return this.clients.filter((client) => this.hasDocker(client)).length
    },
    healthyServices() {
      return this.clients.reduce(
        (total, client) => total + Number(this.services(client).healthyServices || 0),
        0
      )
    },
    totalServices() {
      return this.clients.reduce(
        (total, client) => total + Number(this.services(client).totalServices || 0),
        0
      )
    },
    lastUpdateLabel() {
      return this.lastUpdate
        ? `${Math.max(0, Math.round((Date.now() - this.lastUpdate) / 1000))}s 前`
        : '-'
    }
  },
  mounted() {
    this.loadNodeConfigs()
    this.loadSnapshot()
    this.loadActiveAlerts()
    this.alertTimer = setInterval(this.loadActiveAlerts, ALERT_REFRESH_INTERVAL)
    this.connect()
  },
  beforeDestroy() {
    clearTimeout(this.reconnectTimer)
    clearInterval(this.alertTimer)
    if (this.socket) this.socket.close()
  },
  methods: {
    loadNodeConfigs() {
      getPublicNodeConfigs()
        .then((rows) => {
          const configMap = {}
          ;(rows || []).forEach((row) => {
            configMap[row.clientId] = row
          })
          this.nodeConfigs = configMap
        })
        .catch(() => {})
    },
    loadActiveAlerts() {
      getActiveAlerts()
        .then((data) => {
          this.activeAlerts = data || []
        })
        .catch(() => {})
    },
    loadSnapshot() {
      getSnapshot()
        .then(this.applySnapshot)
        .catch(() => {})
    },
    applySnapshot(data) {
      this.snapshot = data || { clientSnapshots: [] }
      this.lastUpdate = Date.now()
      this.clients.forEach((client) => {
        if (!this.activeTabs[client.clientId])
          this.$set(this.activeTabs, client.clientId, this.defaultTab(client))
      })
    },
    connect() {
      try {
        this.socket = new WebSocket(websocketUrl())
        this.socket.onopen = () => {
          this.connected = true
        }
        this.socket.onmessage = (event) => {
          try {
            this.applySnapshot(JSON.parse(event.data))
          } catch (_) {}
        }
        this.socket.onclose = () => {
          this.connected = false
          this.reconnectTimer = setTimeout(() => this.connect(), RECONNECT_INTERVAL)
        }
        this.socket.onerror = () => this.socket.close()
      } catch (_) {
        this.connected = false
        this.reconnectTimer = setTimeout(() => this.connect(), RECONNECT_INTERVAL)
      }
    },
    selectTab(clientId, tabKey) {
      this.$set(this.activeTabs, clientId, tabKey)
    },
    displayName(client) {
      return (
        (this.nodeConfigs[client.clientId] && this.nodeConfigs[client.clientId].displayName) ||
        client.hostname ||
        client.clientId
      )
    },
    visibleTabs(client) {
      const config = this.nodeConfigs[client.clientId]
      if (!config) return this.tabs
      return this.tabs.filter((tab) =>
        tab.key === 'system'
          ? config.displaySystem !== false
          : tab.key === 'docker'
            ? config.displayDocker !== false
            : tab.key === 'services'
              ? config.displayMicroservice !== false
              : true
      )
    },
    defaultTab(client) {
      const tabs = this.visibleTabs(client)
      return tabs.length ? tabs[0].key : 'system'
    },
    docker(client) {
      return client.dockerMetrics || {}
    },
    services(client) {
      return client.microserviceMetrics || {}
    },
    highgo(client) {
      return client.highgoMetrics || {}
    },
    hasDocker(client) {
      return (
        this.docker(client).totalContainers !== undefined &&
        this.docker(client).totalContainers >= 0
      )
    },
    hasServices(client) {
      return (
        this.services(client).totalServices !== undefined &&
        this.services(client).totalServices >= 0
      )
    },
    hasHighgo(client) {
      return (
        this.highgo(client).connectionCount !== undefined &&
        this.highgo(client).connectionCount >= 0
      )
    },
    formatTime(value) {
      return value ? String(value).replace('T', ' ') : '--'
    },
    num(value) {
      return Number(value || 0).toFixed(1)
    },
    tone(value) {
      return value > this.settings.critical
        ? 'red'
        : value > this.settings.warning
          ? 'yellow'
          : 'green'
    },
    alertKey(event) {
      return `${event.ruleId}-${event.clientId}`
    },
    systemMetrics(client) {
      const metrics = client.systemMetrics || {}
      return [
        {
          label: 'CPU 使用率',
          value: this.num(metrics.cpuUsage),
          unit: '%',
          progress: metrics.cpuUsage || 0,
          tone: this.tone(metrics.cpuUsage),
          icon: 'el-icon-cpu'
        },
        {
          label: '内存使用率',
          value: this.num(metrics.memoryUsage),
          unit: '%',
          progress: metrics.memoryUsage || 0,
          tone: this.tone(metrics.memoryUsage),
          icon: 'el-icon-odometer'
        },
        {
          label: '磁盘使用率',
          value: this.num(metrics.diskUsage),
          unit: '%',
          progress: metrics.diskUsage || 0,
          tone: this.tone(metrics.diskUsage),
          icon: 'el-icon-files'
        },
        {
          label: 'JVM 堆使用',
          value: this.num(metrics.jvmHeapUsage),
          unit: '%',
          progress: metrics.jvmHeapUsage || 0,
          tone: this.tone(metrics.jvmHeapUsage),
          icon: 'el-icon-data-line'
        },
        {
          label: '网络接收',
          value: this.num(metrics.networkRxKBs),
          unit: 'KB/s',
          icon: 'el-icon-download'
        },
        {
          label: '网络发送',
          value: this.num(metrics.networkTxKBs),
          unit: 'KB/s',
          icon: 'el-icon-upload'
        }
      ]
    }
  }
}
</script>
