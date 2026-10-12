<template>
  <section class="dashboard-home">
    <div class="dashboard-heading">
      <div class="dashboard-heading__line">
        <span class="dashboard-heading__ornament" /><span>监控中心</span
        ><span class="dashboard-heading__ornament dashboard-heading__ornament--right" />
      </div>
    </div>

    <el-row class="summary-row">
      <el-col v-for="item in summary" :key="item.label" :span="6">
        <el-card
          class="home-summary-card"
          :class="{ 'is-clickable': item.route }"
          shadow="hover"
          @click.native="item.route && goToModule(item.route)"
        >
          <span class="home-summary-card__label">{{ item.label }}</span>
          <strong :class="item.tone">{{ item.value }}</strong>
          <small>{{ item.note }}</small>
          <i v-if="item.route" class="el-icon-arrow-right home-summary-card__arrow" />
        </el-card>
      </el-col>
    </el-row>

    <el-row class="main-module-row">
      <el-col :span="8">
        <el-card
          class="module-card module-card--link"
          shadow="hover"
          @click.native="goToModule('external-api')"
        >
          <div slot="header" class="module-card__header">
            <span><i class="el-icon-link" /> 外部接口状态</span><i class="el-icon-arrow-right" />
          </div>
          <div v-if="totalServices" class="external-status-summary">
            <div>
              <span>系统总数</span><strong>{{ totalServices }}</strong>
            </div>
            <div>
              <span>可用</span><strong class="is-success">{{ healthyServices }}</strong>
            </div>
            <div>
              <span>异常</span><strong class="is-danger">{{ unhealthyServices }}</strong>
            </div>
          </div>
          <div v-else class="module-card__empty">暂无外部系统探测数据</div>
        </el-card>
      </el-col>

      <el-col :span="8">
        <el-card
          class="module-card module-card--alerts"
          shadow="hover"
          @click.native="goToModule('alerts')"
        >
          <div slot="header" class="module-card__header">
            <span><i class="el-icon-bell" /> 最新告警</span>
            <el-tag size="mini" :type="activeAlerts.length ? 'danger' : 'success'">
              {{ activeAlerts.length ? `${activeAlerts.length} 条` : '运行正常' }}
            </el-tag>
          </div>
          <div v-if="activeAlerts.length" class="latest-alert-list">
            <div
              v-for="event in activeAlerts.slice(0, alertLimit)"
              :key="alertKey(event)"
              class="latest-alert-item"
              :class="{ 'is-critical': event.level === 'CRITICAL' }"
            >
              <span>{{ event.nodeName || event.clientId }}</span>
              <em>{{ alertType(event.metric) }}</em>
              <time>{{ formatAlertTime(event.startedAt) }}</time>
            </div>
          </div>
          <div v-else class="module-card__empty module-card__empty--success">
            <i class="el-icon-success" /> 当前没有活动告警
          </div>
        </el-card>
      </el-col>

      <el-col :span="8">
        <el-card
          class="module-card module-card--database"
          shadow="hover"
          @click.native="goToModule('highgo')"
        >
          <div slot="header" class="module-card__header">
            <span><i class="el-icon-connection" /> 瀚高数据库</span
            ><i class="el-icon-arrow-right" />
          </div>
          <el-row v-if="highgoSummary.available" class="database-metrics">
            <el-col v-for="metric in highgoMetrics" :key="metric.label" :span="12">
              <div class="database-metric">
                <span>{{ metric.label }}</span
                ><strong>{{ metric.value }}</strong>
              </div>
            </el-col>
          </el-row>
          <div v-else class="module-card__empty">暂无瀚高数据库实时数据</div>
        </el-card>
      </el-col>
    </el-row>

    <el-card class="node-card" shadow="never">
      <div slot="header" class="module-card__header">
        <span>节点运行状态</span><small>NODE STATUS / {{ clients.length }} NODES</small>
      </div>
      <el-table :data="nodeRows" height="100%" stripe class="node-table" empty-text="暂无在线节点">
        <!-- <el-table-column label="状态" width="72" align="center">
          <template slot-scope="scope"
            ><span class="node-status-dot" :title="scope.row.status"
          /></template>
        </el-table-column> -->
        <el-table-column prop="hostname" label="节点名称" min-width="140" show-overflow-tooltip />
        <el-table-column prop="ipAddress" label="IP 地址" min-width="130" show-overflow-tooltip />
        <el-table-column prop="clientId" label="客户端 ID" min-width="150" show-overflow-tooltip />
        <el-table-column label="CPU 使用率" min-width="110" align="right">
          <template slot-scope="scope">
            <span :class="metricAlertClass(scope.row.clientId, 'CPU')">{{
              scope.row.cpuUsage
            }}</span>
          </template>
        </el-table-column>
        <el-table-column label="内存使用率" min-width="110" align="right">
          <template slot-scope="scope">
            <span :class="metricAlertClass(scope.row.clientId, 'MEMORY')">{{
              scope.row.memoryUsage
            }}</span>
          </template>
        </el-table-column>
        <el-table-column label="磁盘使用率" min-width="110" align="right">
          <template slot-scope="scope">
            <span :class="metricAlertClass(scope.row.clientId, 'DISK')">{{
              scope.row.diskUsage
            }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="loadAverage" label="系统负载" min-width="100" align="right" />
        <el-table-column label="连接状态" width="100" align="center">
          <template><el-tag size="mini" type="success">在线</el-tag></template>
        </el-table-column>
      </el-table>
    </el-card>
  </section>
</template>

<script>
import { getActiveAlerts, getPublicNodeConfigs, websocketUrl } from '../services/monitor'
import { alertTypeLabel } from '../constants/alertTypes'
import { formatServerTime } from '../utils/dateTime'

const ALERT_LIMIT = 4
const STATIC_REFRESH_INTERVAL = 30000
const RECONNECT_INTERVAL = 3000

/** 展示端首页，展示监控模块入口、运行摘要及节点状态。 */
export default {
  data() {
    return {
      snapshot: { clientSnapshots: [] },
      activeAlerts: [],
      metricAlerts: [],
      nodeConfigs: [],
      alertLimit: ALERT_LIMIT,
      refreshTimer: null,
      reconnectTimer: null,
      socket: null,
      destroyed: false
    }
  },
  computed: {
    clients() {
      const snapshots = this.snapshot.clientSnapshots || []
      if (!this.nodeConfigs.length) {
        return snapshots
      }
      const enabledIds = new Set(
        this.nodeConfigs.filter((node) => node.enabled !== false).map((node) => node.clientId)
      )
      return snapshots.filter((client) => enabledIds.has(client.clientId))
    },
    totalRunning() {
      return this.clients.reduce(
        (total, client) => total + Number((client.dockerMetrics || {}).runningContainers || 0),
        0
      )
    },
    healthyServices() {
      return this.clients.reduce(
        (total, client) => total + Number((client.microserviceMetrics || {}).healthyServices || 0),
        0
      )
    },
    totalServices() {
      return this.clients.reduce(
        (total, client) => total + Number((client.microserviceMetrics || {}).totalServices || 0),
        0
      )
    },
    unhealthyServices() {
      return this.clients.reduce(
        (total, client) => total + Number((client.microserviceMetrics || {}).unhealthyServices || 0),
        0
      )
    },
    totalNodes() {
      return this.nodeConfigs.filter((node) => node.enabled !== false).length || this.clients.length
    },
    highgoSummary() {
      const metrics = this.clients
        .map((client) => client.highgoMetrics)
        .filter((metrics) => metrics && Number(metrics.connectionCount) >= 0)
      const total = (key) => metrics.reduce((sum, item) => sum + Number(item[key] || 0), 0)
      const availableTotal = (key) => {
        const values = metrics.map((item) => Number(item[key])).filter((value) => value >= 0)
        return values.length ? values.reduce((sum, value) => sum + value, 0) : null
      }
      const loads = metrics.filter((item) => Number(item.load) >= 0)
      const averageLoad = loads.length
        ? loads.reduce((sum, item) => sum + Number(item.load || 0), 0) / loads.length
        : 0
      return {
        connectionCount: total('connectionCount'),
        activeConnectionCount: total('activeConnectionCount'),
        load: averageLoad.toFixed(1),
        waitingSessionCount: total('waitingSessionCount'),
        loginFailureCount: availableTotal('loginFailureCount'),
        databaseSizeMB: availableTotal('databaseSizeMB'),
        available: metrics.length > 0
      }
    },
    highgoMetrics() {
      return [
        { label: '连接数', value: this.highgoSummary.connectionCount },
        { label: '活跃连接', value: this.highgoSummary.activeConnectionCount },
        { label: '连接负载', value: `${this.highgoSummary.load}%` },
        {
          label: '登录失败',
          value: this.formatAvailableMetric(this.highgoSummary.loginFailureCount)
        },
        {
          label: '实例总容量',
          value: this.formatAvailableMetric(this.highgoSummary.databaseSizeMB, ' MB', 1)
        },
        { label: '锁等待', value: this.highgoSummary.waitingSessionCount }
      ]
    },
    nodeRows() {
      return this.clients.map((client) => ({
        clientId: client.clientId,
        hostname: client.hostname || client.clientId,
        ipAddress: client.ipAddress || '-',
        cpuUsage: `${this.formatMetric(this.clientCpu(client))}%`,
        memoryUsage: `${this.formatMetric(this.clientMemory(client))}%`,
        diskUsage: `${this.formatMetric(this.clientDisk(client))}%`,
        loadAverage: this.formatMetric(this.clientLoad(client)),
        status: '在线'
      }))
    },
    summary() {
      return [
        {
          label: '在线节点',
          value: `${this.clients.length} / ${this.totalNodes}`,
          note: '在线 / 总节点，点击查看详情',
          tone: 'blue',
          route: 'nodes'
        },
        {
          label: '活动告警',
          value: this.activeAlerts.length,
          note: '未确认告警，点击查看详情',
          tone: 'red',
          route: 'alerts'
        },
        {
          label: '运行容器',
          value: this.totalRunning,
          note: 'RUNNING CONTAINERS，点击查看详情',
          tone: 'green',
          route: 'docker'
        },
        {
          label: '健康微服务',
          value: `${this.healthyServices} / ${this.totalServices}`,
          note: '健康 / 总服务，点击查看详情',
          tone: 'cyan',
          route: 'services'
        }
      ]
    }
  },
  mounted() {
    this.destroyed = false
    this.loadInitialData()
    this.connectWebSocket()
    this.refreshTimer = setInterval(this.loadNodeConfigs, STATIC_REFRESH_INTERVAL)
  },
  beforeDestroy() {
    this.destroyed = true
    clearInterval(this.refreshTimer)
    clearTimeout(this.reconnectTimer)
    if (this.socket) this.socket.close()
  },
  methods: {
    loadInitialData() {
      Promise.all([getActiveAlerts(), getPublicNodeConfigs()])
        .then(([alerts, nodeConfigs]) => {
          this.activeAlerts = alerts || []
          this.nodeConfigs = nodeConfigs || []
        })
        .catch((error) => {
          this.$log && this.$log.warn('展示端首页数据加载失败', error)
        })
    },
    loadNodeConfigs() {
      getPublicNodeConfigs()
        .then((nodeConfigs) => {
          this.nodeConfigs = nodeConfigs || []
        })
        .catch((error) => {
          this.$log && this.$log.warn('节点配置加载失败', error)
        })
    },
    connectWebSocket() {
      if (this.destroyed) return
      this.socket = new WebSocket(websocketUrl())
      this.socket.onmessage = (event) => {
        try {
          const message = JSON.parse(event.data) || { clientSnapshots: [] }
          this.snapshot = message
          if (Array.isArray(message.activeAlerts)) {
            this.activeAlerts = message.activeAlerts
          }
          if (Array.isArray(message.metricAlerts)) {
            this.metricAlerts = message.metricAlerts
          }
        } catch (error) {
          this.$log && this.$log.warn('实时监控数据解析失败', error)
        }
      }
      this.socket.onerror = (error) => {
        this.$log && this.$log.warn('节点 WebSocket 连接异常', error)
      }
      this.socket.onclose = () => {
        this.socket = null
        if (!this.destroyed) {
          this.reconnectTimer = setTimeout(() => this.connectWebSocket(), RECONNECT_INTERVAL)
        }
      }
    },
    goToModule(moduleKey) {
      window.location.hash = `/${moduleKey}`
    },
    clientCpu(client) {
      return client.systemMetrics && client.systemMetrics.cpuUsage
    },
    clientLoad(client) {
      return client.systemMetrics && client.systemMetrics.loadAverage
    },
    clientMemory(client) {
      return client.systemMetrics && client.systemMetrics.memoryUsage
    },
    clientDisk(client) {
      return client.systemMetrics && client.systemMetrics.diskUsage
    },
    formatMetric(value) {
      return Number(value || 0).toFixed(1)
    },
    formatAvailableMetric(value, unit = '', precision = 0) {
      return value === null || value === undefined || Number(value) < 0
        ? '--'
        : `${Number(value).toFixed(precision)}${unit}`
    },
    alertKey(event) {
      return event.id || `${event.ruleId}-${event.clientId}`
    },
    formatAlertTime(value) {
      return formatServerTime(value)
    },
    alertType(metric) {
      return alertTypeLabel(metric)
    },
    metricAlertClass(clientId, metric) {
      const alert = this.metricAlerts.find(
        (item) => item.clientId === clientId && item.metric === metric
      )
      if (!alert) return ''
      return alert.level === 'CRITICAL' ? 'node-metric--critical' : 'node-metric--warning'
    }
  }
}
</script>
