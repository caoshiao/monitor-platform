<template>
  <section class="dashboard-home">
    <div class="dashboard-heading">
      <div class="dashboard-heading__line">
        <span class="dashboard-heading__ornament" /><span>监控中心</span
        ><span class="dashboard-heading__ornament dashboard-heading__ornament--right" />
      </div>
      <p>MONITOR PLATFORM / LIVE OVERVIEW</p>
    </div>
    <div class="dashboard-summary">
      <button
        v-for="item in summary"
        :key="item.label"
        class="dashboard-summary__item"
        :class="{ 'is-clickable': item.route }"
        type="button"
        @click="item.route && goToModule(item.route)"
      >
        <span>{{ item.label }}</span
        ><strong :class="item.tone">{{ item.value }}</strong
        ><small>{{ item.note }}</small>
        <i v-if="item.route" class="el-icon-arrow-right dashboard-summary__arrow" />
      </button>
    </div>
    <div class="module-overview-grid">
      <div class="module-overview-column module-overview-column--left">
        <button
          v-for="module in leftModules"
          :key="module.key"
          class="module-overview-card"
          type="button"
          @click="goToModule(module.key)"
        >
          <span class="module-overview-card__corner" /><span class="module-overview-card__icon"
            ><i :class="module.icon" /></span
          ><span class="module-overview-card__body"
            ><b>{{ module.label }}</b
            ><small>{{ module.description }}</small
            ><em>{{ module.value }}</em></span
          ><i class="el-icon-arrow-right module-overview-card__arrow" />
        </button>
      </div>
      <div class="module-overview-column module-overview-column--middle">
        <button class="latest-alert-panel" type="button" @click="goToModule('alerts')">
          <div class="latest-alert-panel__header">
            <span><i class="el-icon-bell" /> 最新告警</span><small>RECENT ALERTS</small>
          </div>
          <div class="latest-alert-panel__summary">
            <strong>{{ activeAlerts.length }}</strong><span>当前待处理告警</span>
            <el-tag size="mini" :type="activeAlerts.length ? 'danger' : 'success'">{{ activeAlerts.length ? '需要关注' : '运行正常' }}</el-tag>
          </div>
          <div v-if="activeAlerts.length" class="latest-alert-list">
            <div v-for="event in activeAlerts.slice(0, alertLimit)" :key="alertKey(event)" class="latest-alert-item" :class="{ 'is-critical': event.level === 'CRITICAL' }">
              <i class="el-icon-warning-outline" /><span>{{ event.message }}</span><em>{{ event.level }}</em>
            </div>
          </div>
          <div v-else class="latest-alert-empty"><i class="el-icon-success" /> 当前运行正常</div>
          <span class="latest-alert-panel__footer">查看全部告警 <i class="el-icon-arrow-right" /></span>
        </button>
      </div>
      <div v-if="rightModules.length" class="module-overview-column module-overview-column--right">
        <button
          v-for="module in rightModules"
          :key="module.key"
          class="module-overview-card"
          type="button"
          @click="goToModule(module.key)"
        >
          <span class="module-overview-card__corner" /><span class="module-overview-card__icon"
            ><i :class="module.icon" /></span
          ><span class="module-overview-card__body"
            ><b>{{ module.label }}</b
            ><small>{{ module.description }}</small
            ><em>{{ module.value }}</em></span
          ><i class="el-icon-arrow-right module-overview-card__arrow" />
        </button>
      </div>
    </div>
    
    <div class="dashboard-lower">
      <div class="dashboard-panel dashboard-panel--nodes">
        <div class="dashboard-panel__title">
          <span>节点运行状态</span><small>NODE STATUS / {{ clients.length }} NODES</small>
        </div>
        <div v-if="clients.length" class="node-status-list">
          <div v-for="client in clients" :key="client.clientId" class="node-status-row">
            <span class="node-status-dot" /><b>{{ client.hostname || client.clientId }}</b
            ><code>{{ client.clientId }}</code
            ><span class="node-status-metric">CPU {{ formatMetric(clientCpu(client)) }}%</span
            ><span class="node-status-metric">负载 {{ formatMetric(clientLoad(client)) }}</span
            ><el-tag size="mini" type="success">ONLINE</el-tag>
          </div>
        </div>
        <div v-else class="dashboard-panel__empty">暂无在线节点</div>
      </div>
    </div>
  </section>
</template>

<script>
import { getActiveAlerts, getPublicNodeConfigs, getSnapshot } from '../services/monitor'
import { DISPLAY_MODULES } from '../constants/displayModules'

const REFRESH_INTERVAL = 10000
const ALERT_LIMIT = 4

/** 展示端首页，展示监控模块入口、运行摘要及节点状态。 */
export default {
  data() {
    return {
      snapshot: { clientSnapshots: [] },
      activeAlerts: [],
      modules: DISPLAY_MODULES,
      nodeConfigs: [],
      alertLimit: ALERT_LIMIT,
      refreshTimer: null
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
    leftModules() {
      return this.modules.filter((module) => module.key === 'external-api')
    },
    rightModules() {
      return this.modules.filter((module) => module.key === 'highgo')
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
    totalNodes() {
      return this.nodeConfigs.filter((node) => node.enabled !== false).length || this.clients.length
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
        { label: '活动告警', value: this.activeAlerts.length, note: 'ACTIVE ALERTS', tone: 'red' },
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
    this.loadDashboardData()
    this.refreshTimer = setInterval(this.loadDashboardData, REFRESH_INTERVAL)
  },
  beforeDestroy() {
    clearInterval(this.refreshTimer)
  },
  methods: {
    loadDashboardData() {
      Promise.all([getSnapshot(), getActiveAlerts(), getPublicNodeConfigs()])
        .then(([snapshot, alerts, nodeConfigs]) => {
          this.snapshot = snapshot || { clientSnapshots: [] }
          this.activeAlerts = alerts || []
          this.nodeConfigs = nodeConfigs || []
        })
        .catch((error) => {
          this.$log && this.$log.warn('展示端首页数据加载失败', error)
        })
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
    formatMetric(value) {
      return Number(value || 0).toFixed(1)
    },
    alertKey(event) {
      return `${event.ruleId}-${event.clientId}`
    }
  }
}
</script>
