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

    <div v-if="!clients.length && !['alerts', 'nodes'].includes(module)" class="empty-state">
      <i class="el-icon-connection" />
      <h3>等待采集节点上线</h3>
      <p>请启动 monitor-client，数据将通过 WebSocket 自动出现在这里。</p>
    </div>

    <div v-if="module === 'alerts'" class="client-panel alert-history-panel">
      <div class="client-panel__head">
        <div>
          <b>告警记录</b><span class="alert-history-count">共 {{ alerts.length }} 条</span>
        </div>
        <el-button size="mini" icon="el-icon-refresh" :loading="alertsLoading" @click="loadAlerts">
          刷新
        </el-button>
      </div>
      <el-table
        :data="pagedAlerts"
        class="monitor-detail-table alert-detail-table"
        border
        empty-text="暂无告警记录"
      >
        <el-table-column label="规则" width="120" show-overflow-tooltip>
          <template slot-scope="scope">{{ ruleName(scope.row.ruleName) }}</template>
        </el-table-column>
        <el-table-column label="节点" width="110" show-overflow-tooltip>
          <template slot-scope="scope">{{ scope.row.nodeName || scope.row.clientId }}</template>
        </el-table-column>
        <el-table-column label="事件类型" width="135" show-overflow-tooltip>
          <template slot-scope="scope">{{ alertType(scope.row.metric) }}</template>
        </el-table-column>
        <el-table-column label="事件内容" min-width="190" show-overflow-tooltip>
          <template slot-scope="scope">
            <span class="alert-detail-content">{{ scope.row.message || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="级别" width="70" align="center">
          <template slot-scope="scope">
            <el-tag size="mini" :type="scope.row.level === 'CRITICAL' ? 'danger' : 'warning'">
              {{ alertLevel(scope.row.level) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="75" align="center">
          <template slot-scope="scope">{{ alertStatus(scope.row.status) }}</template>
        </el-table-column>
        <el-table-column label="开始时间" width="145" class-name="alert-detail-nowrap">
          <template slot-scope="scope">{{ formatAlertTime(scope.row.startedAt) }}</template>
        </el-table-column>
        <el-table-column label="恢复时间" width="145" class-name="alert-detail-nowrap">
          <template slot-scope="scope">
            {{ scope.row.resolvedAt ? formatAlertTime(scope.row.resolvedAt) : '持续中' }}
          </template>
        </el-table-column>
        <el-table-column label="持续时长" width="90" class-name="alert-detail-nowrap">
          <template slot-scope="scope">{{ alertDuration(scope.row.durationSeconds) }}</template>
        </el-table-column>
        <el-table-column label="确认操作" width="100" align="center" fixed="right">
          <template slot-scope="scope">
            <el-tag v-if="scope.row.acknowledged" size="mini" type="info">已确认</el-tag>
            <el-button
              v-else
              size="mini"
              type="primary"
              plain
              :loading="acknowledgingId === scope.row.id"
              @click="confirmAlert(scope.row)"
            >
              确认
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      <div v-if="alerts.length > alertPageSize" class="alert-pagination">
        <el-pagination
          background
          layout="prev, pager, next, total"
          :current-page.sync="alertCurrentPage"
          :page-size="alertPageSize"
          :total="alerts.length"
        />
      </div>
    </div>

    <div v-else-if="module === 'nodes'" class="client-panel">
      <div class="metrics-grid compact">
        <metric-card label="节点总数" :value="nodeTotal" unit="个" icon="el-icon-connection" />
        <metric-card
          label="在线节点"
          :value="clients.length"
          unit="个"
          tone="green"
          icon="el-icon-success"
        />
      </div>
      <data-table :columns="nodeColumns" :rows="nodeRows" />
    </div>

    <div v-for="client in clients" v-else :key="client.clientId" class="client-panel">
      <div
        class="client-panel__head"
        :class="{ 'client-panel__head--summary': ['docker', 'services'].includes(module) }"
      >
        <div class="client-panel__identity">
          <span class="node-dot" /><b>{{ client.hostname || client.clientId }}</b
          ><code>{{ client.ipAddress || '-' }}</code
          ><code>{{ client.clientId }}</code>
        </div>
        <div v-if="module === 'docker'" class="module-head-summary">
          <span
            >容器总数 <b>{{ docker(client).totalContainers || 0 }}</b></span
          >
          <span class="is-running"
            >运行中 <b>{{ docker(client).runningContainers || 0 }}</b></span
          >
          <span class="is-unhealthy"
            >异常 <b>{{ docker(client).unhealthyContainers || 0 }}</b></span
          >
          <el-button
            type="text"
            :icon="isDockerExpanded(client.clientId) ? 'el-icon-arrow-up' : 'el-icon-arrow-down'"
            @click="toggleDocker(client.clientId)"
          >
            {{ isDockerExpanded(client.clientId) ? '收起列表' : '展开列表' }}
          </el-button>
        </div>
        <div v-else-if="module === 'services'" class="module-head-summary">
          <span
            >服务总数 <b>{{ services(client).totalServices || 0 }}</b></span
          >
          <span class="is-running"
            >健康 <b>{{ services(client).healthyServices || 0 }}</b></span
          >
          <span class="is-unhealthy"
            >异常 <b>{{ services(client).unhealthyServices || 0 }}</b></span
          >
          <el-button
            type="text"
            :icon="isServiceExpanded(client.clientId) ? 'el-icon-arrow-up' : 'el-icon-arrow-down'"
            @click="toggleService(client.clientId)"
          >
            {{ isServiceExpanded(client.clientId) ? '收起列表' : '展开列表' }}
          </el-button>
        </div>
        <el-tag v-else size="mini" type="success">ONLINE</el-tag>
      </div>
      <div v-if="module === 'system'" class="metrics-grid">
        <metric-card v-for="item in systemMetrics(client)" :key="item.label" v-bind="item" />
      </div>
      <el-table
        v-if="module === 'docker' && isDockerExpanded(client.clientId)"
        :data="docker(client).containers || []"
        class="monitor-detail-table"
        stripe
        border
        empty-text="暂无容器数据"
      >
        <el-table-column prop="name" label="容器名称" min-width="150" show-overflow-tooltip />
        <el-table-column prop="containerId" label="容器 ID" min-width="145" show-overflow-tooltip />
        <el-table-column prop="image" label="镜像" min-width="180" show-overflow-tooltip />
        <el-table-column prop="status" label="状态" width="100" align="center">
          <template slot-scope="scope">
            <el-tag size="mini" :type="scope.row.status === 'running' ? 'success' : 'danger'">
              {{ scope.row.status || '未知' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="uptime" label="运行时长" min-width="130" show-overflow-tooltip />
        <el-table-column label="CPU 使用率" width="110" align="right">
          <template slot-scope="scope">{{ formatNumber(scope.row.cpuUsage) }}%</template>
        </el-table-column>
        <el-table-column label="内存使用（MB）" width="130" align="right">
          <template slot-scope="scope">{{ formatNumber(scope.row.memoryUsageMB) }}</template>
        </el-table-column>
        <el-table-column prop="ports" label="端口映射" min-width="160" show-overflow-tooltip />
      </el-table>
      <el-table
        v-else-if="module === 'services' && isServiceExpanded(client.clientId)"
        :data="services(client).services || []"
        class="monitor-detail-table"
        stripe
        border
        empty-text="暂无微服务数据"
      >
        <el-table-column
          prop="serviceName"
          label="服务名称"
          min-width="150"
          show-overflow-tooltip
        />
        <el-table-column
          prop="serviceUrl"
          label="健康检查地址"
          min-width="240"
          show-overflow-tooltip
        />
        <el-table-column prop="port" label="端口" width="90" align="right" />
        <el-table-column prop="healthStatus" label="状态" width="100" align="center">
          <template slot-scope="scope">
            <el-tag size="mini" :type="scope.row.healthStatus === 'UP' ? 'success' : 'danger'">
              {{ scope.row.healthStatus || '未知' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="响应时间" width="115" align="right">
          <template slot-scope="scope">{{ formatResponseTime(scope.row.responseTimeMs) }}</template>
        </el-table-column>
        <el-table-column prop="lastCheckTime" label="最后检查时间" min-width="165" />
        <el-table-column
          prop="errorMessage"
          label="错误信息"
          min-width="180"
          show-overflow-tooltip
        />
      </el-table>
      <template v-else-if="module === 'external-api'">
        <div class="metrics-grid compact external-summary-grid">
          <metric-card
            label="系统总数"
            :value="services(client).totalServices"
            unit="个"
            icon="el-icon-s-grid"
          />
          <metric-card
            label="完全可用"
            :value="services(client).healthyServices"
            unit="个"
            tone="green"
            icon="el-icon-success"
          />
          <metric-card
            label="部分异常"
            :value="services(client).degradedServices"
            unit="个"
            icon="el-icon-warning-outline"
          />
          <metric-card
            label="完全不可用"
            :value="unavailableServices(client)"
            unit="个"
            tone="red"
            icon="el-icon-error"
          />
        </div>
        <el-table
          :data="services(client).services || []"
          class="monitor-detail-table external-system-table"
          stripe
          border
          empty-text="暂无外部系统探测数据"
        >
          <el-table-column prop="serviceName" label="外部系统" min-width="165" show-overflow-tooltip />
          <el-table-column label="服务器与端口" min-width="155" show-overflow-tooltip>
            <template slot-scope="scope">{{ scope.row.host || '-' }}:{{ scope.row.port || '-' }}</template>
          </el-table-column>
          <el-table-column label="TCP 探测" width="105" align="center">
            <template slot-scope="scope">
              <el-tag size="mini" :type="probeTagType(scope.row.networkStatus)">
                {{ probeStatusLabel(scope.row.networkStatus) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="接口探测" width="105" align="center">
            <template slot-scope="scope">
              <el-tag size="mini" :type="probeTagType(scope.row.interfaceStatus)">
                {{ probeStatusLabel(scope.row.interfaceStatus) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="总体状态" width="110" align="center">
            <template slot-scope="scope">
              <el-tag size="mini" :type="probeTagType(scope.row.healthStatus)">
                {{ probeStatusLabel(scope.row.healthStatus) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="TCP 耗时" width="105" align="right">
            <template slot-scope="scope">{{ formatResponseTime(scope.row.networkResponseTimeMs) }}</template>
          </el-table-column>
          <el-table-column label="接口耗时" width="105" align="right">
            <template slot-scope="scope">{{ formatResponseTime(scope.row.responseTimeMs) }}</template>
          </el-table-column>
          <el-table-column label="HTTP 状态" width="105" align="center">
            <template slot-scope="scope">{{ httpStatusLabel(scope.row.httpStatusCode) }}</template>
          </el-table-column>
          <el-table-column prop="serviceUrl" label="接口地址" min-width="250" show-overflow-tooltip />
          <el-table-column prop="lastCheckTime" label="最后检查时间" min-width="165" />
          <el-table-column prop="errorMessage" label="异常原因" min-width="220" show-overflow-tooltip />
        </el-table>
      </template>
      <template v-else-if="module === 'highgo'">
        <div class="metrics-grid compact">
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
            :value="availableMetric(highgo(client).loginFailureCount)"
            unit="次"
            tone="red"
            icon="el-icon-warning"
          />
          <metric-card
            label="空闲连接"
            :value="highgo(client).idleConnectionCount"
            unit="个"
            icon="el-icon-minus"
          />
          <metric-card
            label="锁等待"
            :value="highgo(client).waitingSessionCount"
            unit="个"
            tone="red"
            icon="el-icon-warning"
          />
          <metric-card
            label="长事务"
            :value="highgo(client).longTransactionCount"
            unit="个"
            tone="yellow"
            icon="el-icon-time"
          />
          <metric-card
            :label="databaseSizeLabel(client)"
            :value="availableMetric(highgo(client).databaseSizeMB)"
            unit="MB"
            icon="el-icon-coin"
          />
          <metric-card
            label="缓存命中率"
            :value="highgo(client).cacheHitRatio"
            unit="%"
            tone="green"
            icon="el-icon-data-analysis"
          />
        </div>
        <data-table :columns="highgoColumns" :rows="highgo(client).repeatedLargeTableQueries" />
      </template>
    </div>
  </section>
</template>

<script>
import DataTable from '../components/DataTable.vue'
import MetricCard from '../components/MetricCard.vue'
import {
  acknowledgeAlert,
  getAlertHistory,
  getPublicNodeConfigs,
  getSnapshot,
  websocketUrl
} from '../services/monitor'
import {
  alertLevelLabel,
  alertRuleNameLabel,
  alertStatusLabel,
  alertTypeLabel,
  formatAlertDuration
} from '../constants/alertTypes'
import { DISPLAY_MODULE_META } from '../constants/displayModules'
import { formatServerTime } from '../utils/dateTime'

const ALERT_REFRESH_INTERVAL = 10000
const ALERT_PAGE_SIZE = 10

/** 展示端模块详情页，按路由模块展示节点实时数据。 */
export default {
  components: { DataTable, MetricCard },
  props: { module: { type: String, default: 'system' } },
  data() {
    return {
      snapshot: { clientSnapshots: [] },
      alerts: [],
      alertsLoading: false,
      acknowledgingId: null,
      alertRefreshTimer: null,
      alertCurrentPage: 1,
      alertPageSize: ALERT_PAGE_SIZE,
      nodeConfigs: [],
      expandedDockerClients: {},
      expandedServiceClients: {},
      socket: null,
      nodeColumns: [
        { key: 'hostname', label: '节点名称' },
        { key: 'ipAddress', label: 'IP 地址' },
        { key: 'clientId', label: '客户端 ID' },
        { key: 'environment', label: '环境' },
        { key: 'location', label: '位置' },
        { key: 'status', label: '状态', type: 'status' }
      ],
      highgoColumns: [
        { key: 'databaseName', label: '数据库' },
        { key: 'userName', label: '用户' },
        { key: 'clientAddress', label: '客户端' },
        { key: 'tableName', label: '表名' },
        { key: 'tableSizeMB', label: '表大小（MB）' },
        { key: 'query', label: 'SQL' },
        { key: 'occurrences', label: '窗口内次数' }
      ]
    }
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
    },
    nodeTotal() {
      return this.nodeConfigs.length || this.clients.length
    },
    nodeRows() {
      const onlineIds = new Set(this.clients.map((client) => client.clientId))
      const onlineClients = new Map(this.clients.map((client) => [client.clientId, client]))
      const configs = this.nodeConfigs.length
        ? this.nodeConfigs
        : this.clients.map((client) => ({ ...client, enabled: true }))
      return configs.map((node) => {
        const onlineClient = onlineClients.get(node.clientId) || {}
        return {
          ...node,
          hostname: onlineClient.hostname || node.hostname || node.displayName || node.clientId,
          ipAddress: onlineClient.ipAddress || node.ipAddress || '-',
          status: onlineIds.has(node.clientId) ? 'ONLINE' : 'OFFLINE'
        }
      })
    },
    pagedAlerts() {
      const startIndex = (this.alertCurrentPage - 1) * this.alertPageSize
      return this.alerts.slice(startIndex, startIndex + this.alertPageSize)
    }
  },
  mounted() {
    this.loadData()
    this.socket = new WebSocket(websocketUrl())
    this.socket.onmessage = this.handleSocketMessage
    if (this.module === 'alerts') {
      this.alertRefreshTimer = setInterval(this.loadAlerts, ALERT_REFRESH_INTERVAL)
    }
  },
  beforeDestroy() {
    clearInterval(this.alertRefreshTimer)
    if (this.socket) this.socket.close()
  },
  methods: {
    loadData() {
      getSnapshot()
        .then((data) => {
          this.snapshot = data || { clientSnapshots: [] }
        })
        .catch(() => {})
      if (this.module === 'alerts') {
        this.loadAlerts()
      }
      getPublicNodeConfigs()
        .then((data) => {
          this.nodeConfigs = data || []
        })
        .catch((error) => {
          this.$log && this.$log.warn('节点配置加载失败', error)
        })
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
    formatAlertTime(value) {
      return formatServerTime(value)
    },
    alertType(metric) {
      return alertTypeLabel(metric)
    },
    alertLevel(level) {
      return alertLevelLabel(level)
    },
    alertStatus(status) {
      return alertStatusLabel(status)
    },
    ruleName(name) {
      return alertRuleNameLabel(name)
    },
    alertDuration(seconds) {
      return formatAlertDuration(seconds)
    },
    loadAlerts() {
      this.alertsLoading = true
      return getAlertHistory()
        .then((data) => {
          this.alerts = data || []
          const totalPages = Math.max(1, Math.ceil(this.alerts.length / this.alertPageSize))
          if (this.alertCurrentPage > totalPages) {
            this.alertCurrentPage = totalPages
          }
        })
        .catch((error) => {
          this.$message.error('告警记录加载失败')
          this.$log && this.$log.warn('告警记录加载失败', error)
        })
        .finally(() => {
          this.alertsLoading = false
        })
    },
    confirmAlert(event) {
      if (!event || !event.id) return
      this.acknowledgingId = event.id
      acknowledgeAlert(event.id)
        .then(() => {
          this.$message.success('告警已确认，历史记录继续保留')
          return this.loadAlerts()
        })
        .catch((error) => {
          this.$message.error('告警确认失败')
          this.$log && this.$log.warn('告警确认失败', error)
        })
        .finally(() => {
          this.acknowledgingId = null
        })
    },
    docker(client) {
      return client.dockerMetrics || {}
    },
    toggleDocker(clientId) {
      this.$set(this.expandedDockerClients, clientId, !this.isDockerExpanded(clientId))
    },
    isDockerExpanded(clientId) {
      return this.expandedDockerClients[clientId] === true
    },
    toggleService(clientId) {
      this.$set(this.expandedServiceClients, clientId, !this.isServiceExpanded(clientId))
    },
    isServiceExpanded(clientId) {
      return this.expandedServiceClients[clientId] === true
    },
    formatNumber(value) {
      return Number(value || 0).toFixed(1)
    },
    formatResponseTime(value) {
      return Number(value) < 0 ? '失败' : `${Number(value || 0)} ms`
    },
    services(client) {
      return client.microserviceMetrics || {}
    },
    unavailableServices(client) {
      const metrics = this.services(client)
      return Math.max(
        0,
        Number(metrics.unhealthyServices || 0) - Number(metrics.degradedServices || 0)
      )
    },
    probeStatusLabel(status) {
      const labels = { UP: '正常', DEGRADED: '部分异常', DOWN: '异常' }
      return labels[status] || '未知'
    },
    probeTagType(status) {
      if (status === 'UP') return 'success'
      if (status === 'DEGRADED') return 'warning'
      return 'danger'
    },
    httpStatusLabel(statusCode) {
      return Number(statusCode) > 0 ? statusCode : '--'
    },
    highgo(client) {
      return client.highgoMetrics || {}
    },
    availableMetric(value) {
      return value === undefined || value === null || Number(value) < 0 ? '--' : value
    },
    databaseSizeLabel(client) {
      return this.highgo(client).databaseName === '全部数据库' ? '实例总容量' : '数据库大小'
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
