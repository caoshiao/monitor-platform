<template>
  <section>
    <div class="page-heading">
      <div>
        <p class="eyebrow">MODULE / {{ title }}</p>
        <h1>{{ title }}</h1>
        <p class="subtext">{{ description }}</p>
      </div>
      <el-button plain icon="el-icon-back" @click="goHome">返回总览</el-button>
    </div>

    <div v-if="!clients.length && module !== 'alerts'" class="empty-state">
      <i class="el-icon-connection" />
      <h3>等待采集节点上线</h3>
      <p>请启动 monitor-client，数据将通过 WebSocket 自动出现在这里。</p>
    </div>

    <div v-if="module === 'alerts'" class="client-panel">
      <div class="display-alert-list">
        <div
          v-for="event in alerts"
          :key="alertKey(event)"
          class="display-alert"
          :class="{ 'display-alert--critical': event.level === 'CRITICAL' }"
        >
          <i class="el-icon-warning-outline" />
          <span>{{ event.message }}</span>
          <small>{{ event.level }}</small>
        </div>
        <div v-if="!alerts.length" class="panel-empty">当前没有活动告警</div>
      </div>
    </div>

    <div v-for="client in clients" v-else :key="client.clientId" class="client-panel">
      <div class="client-panel__head">
        <div>
          <span class="node-dot" /><b>{{ client.hostname || client.clientId }}</b
          ><code>{{ client.clientId }}</code>
        </div>
        <el-tag size="mini" type="success">ONLINE</el-tag>
      </div>
      <div v-if="module === 'system'" class="metrics-grid">
        <metric-card v-for="item in systemMetrics(client)" :key="item.label" v-bind="item" />
      </div>
      <div v-else-if="module === 'docker'" class="metrics-grid compact">
        <metric-card
          label="容器总数"
          :value="docker(client).totalContainers"
          unit="个"
          icon="el-icon-box"
        />
        <metric-card
          label="运行中"
          :value="docker(client).runningContainers"
          unit="个"
          tone="green"
          icon="el-icon-video-play"
        />
        <metric-card
          label="异常"
          :value="docker(client).unhealthyContainers"
          unit="个"
          tone="red"
          icon="el-icon-warning"
        />
      </div>
      <div v-else-if="module === 'services'" class="metrics-grid compact">
        <metric-card
          label="服务总数"
          :value="services(client).totalServices"
          unit="个"
          icon="el-icon-s-grid"
        />
        <metric-card
          label="健康"
          :value="services(client).healthyServices"
          unit="个"
          tone="green"
          icon="el-icon-success"
        />
        <metric-card
          label="异常"
          :value="services(client).unhealthyServices"
          unit="个"
          tone="red"
          icon="el-icon-warning"
        />
      </div>
      <div v-else-if="module === 'highgo'" class="metrics-grid compact">
        <metric-card
          label="连接数"
          :value="highgo(client).connectionCount"
          unit="个"
          icon="el-icon-connection"
        />
        <metric-card
          label="活跃连接"
          :value="highgo(client).activeConnectionCount"
          unit="个"
          tone="green"
          icon="el-icon-link"
        />
        <metric-card
          label="连接负载"
          :value="highgo(client).load"
          unit="%"
          :progress="highgo(client).load"
          icon="el-icon-odometer"
        />
        <metric-card
          label="登录失败"
          :value="highgo(client).loginFailureCount"
          unit="次"
          tone="red"
          icon="el-icon-warning"
        />
      </div>
    </div>
  </section>
</template>

<script>
import MetricCard from '../components/MetricCard.vue'
import { getActiveAlerts, getSnapshot, websocketUrl } from '../services/monitor'
import { DISPLAY_MODULE_META } from '../constants/displayModules'

/** 展示端模块详情页，按路由模块展示节点实时数据。 */
export default {
  components: { MetricCard },
  props: { module: { type: String, default: 'system' } },
  data() {
    return { snapshot: { clientSnapshots: [] }, alerts: [], socket: null }
  },
  computed: {
    clients() {
      return this.snapshot.clientSnapshots || []
    },
    moduleMeta() {
      return (
        DISPLAY_MODULE_META[this.module] || {
          title: '监控模块',
          description: ''
        }
      )
    },
    title() {
      return this.moduleMeta.title
    },
    description() {
      return this.moduleMeta.description
    }
  },
  mounted() {
    this.loadData()
    this.socket = new WebSocket(websocketUrl())
    this.socket.onmessage = this.handleSocketMessage
  },
  beforeDestroy() {
    if (this.socket) this.socket.close()
  },
  methods: {
    loadData() {
      getSnapshot()
        .then((data) => {
          this.snapshot = data || { clientSnapshots: [] }
        })
        .catch(() => {})
      getActiveAlerts()
        .then((data) => {
          this.alerts = data || []
        })
        .catch(() => {})
    },
    handleSocketMessage(event) {
      try {
        this.snapshot = JSON.parse(event.data)
      } catch (error) {
        this.$log && this.$log.warn('实时数据解析失败', error)
      }
    },
    goHome() {
      window.location.hash = '/'
    },
    alertKey(event) {
      return `${event.ruleId}-${event.clientId}`
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
    systemMetrics(client) {
      const metrics = client.systemMetrics || {}
      return [
        this.percentMetric('CPU 使用率', metrics.cpuUsage, 'el-icon-cpu'),
        this.percentMetric('内存使用率', metrics.memoryUsage, 'el-icon-odometer'),
        this.percentMetric('磁盘使用率', metrics.diskUsage, 'el-icon-files'),
        {
          label: '系统负载',
          value: Number(metrics.loadAverage || 0).toFixed(2),
          unit: '',
          icon: 'el-icon-data-line'
        },
        {
          label: '网络接收',
          value: Number(metrics.networkRxKBs || 0).toFixed(1),
          unit: 'KB/s',
          icon: 'el-icon-download'
        },
        {
          label: '网络发送',
          value: Number(metrics.networkTxKBs || 0).toFixed(1),
          unit: 'KB/s',
          icon: 'el-icon-upload'
        }
      ]
    },
    percentMetric(label, value, icon) {
      return {
        label,
        value: Number(value || 0).toFixed(1),
        unit: '%',
        progress: Number(value || 0),
        icon
      }
    }
  }
}
</script>
