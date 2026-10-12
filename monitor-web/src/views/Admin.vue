<template>
  <section>
    <div class="page-heading">
      <div>
        <h1>后台管理</h1>
        <p class="subtext">统一管理节点展示策略和告警规则。</p>
      </div>
      <el-button icon="el-icon-refresh" :loading="loading" @click="refreshAll">刷新数据</el-button>
    </div>
    <div class="admin-tabs">
      <button :class="{ active: activeModule === 'nodes' }" @click="activeModule = 'nodes'">
        <i class="el-icon-s-platform" /> 节点管理</button
      ><button :class="{ active: activeModule === 'alerts' }" @click="activeModule = 'alerts'">
        <i class="el-icon-bell" /> 告警管理
        <el-badge v-if="activeAlerts.length" :value="activeAlerts.length" />
      </button>
    </div>

    <div v-if="activeModule === 'nodes'">
      <div class="module-toolbar">
        <div>
          <b>节点配置</b><span class="toolbar-hint">配置节点名称、环境、位置及展示指标</span>
        </div>
        <el-button type="primary" icon="el-icon-plus" @click="openNodeDialog()">新增节点</el-button>
      </div>
      <div class="admin-card admin-table-card">
        <el-table :data="nodes" size="small" empty-text="暂无节点配置"
          ><el-table-column prop="clientId" label="节点标识" min-width="150" /><el-table-column
            prop="hostname"
            label="主机名"
            min-width="135"
          /><el-table-column prop="displayName" label="展示名称" min-width="130" /><el-table-column
            prop="environment"
            label="环境"
            width="100"
          /><el-table-column prop="location" label="位置" width="120" /><el-table-column
            label="状态"
            width="100"
            ><template slot-scope="scope"
              ><el-tag size="mini" :type="scope.row.online ? 'success' : 'info'">{{
                scope.row.online ? '在线' : '离线'
              }}</el-tag></template
            ></el-table-column
          ><el-table-column label="展示模块" min-width="180"
            ><template slot-scope="scope"
              ><span class="module-chips"
                ><i v-if="scope.row.displaySystem">系统</i><i v-if="scope.row.displayDocker">容器</i
                ><i v-if="scope.row.displayMicroservice">微服务</i></span
              ></template
            ></el-table-column
          ><el-table-column label="启用" width="75"
            ><template slot-scope="scope"
              ><el-switch
                v-model="scope.row.enabled"
                @change="saveNode(scope.row)" /></template></el-table-column
          ><el-table-column label="操作" width="135" fixed="right"
            ><template slot-scope="scope"
              ><el-button type="text" size="mini" @click="openNodeDialog(scope.row)">编辑</el-button
              ><el-button type="text" size="mini" class="danger-text" @click="removeNode(scope.row)"
                >删除</el-button
              ></template
            ></el-table-column
          ></el-table
        >
      </div>
    </div>

    <div v-else>
      <div class="module-toolbar">
        <div><b>告警规则</b><span class="toolbar-hint">按节点和指标配置预警、严重阈值</span></div>
        <el-button type="primary" icon="el-icon-plus" @click="openAlertDialog()"
          >新增规则</el-button
        >
      </div>
      <div class="alert-overview">
        <div class="alert-overview__icon"><i class="el-icon-warning" /></div>
        <div>
          <strong>{{ activeAlerts.length }}</strong
          ><span>未确认告警</span>
        </div>
        <div class="alert-overview__split" />
        <div>
          <strong class="warning-color">{{ warningCount }}</strong
          ><span>预警</span>
        </div>
        <div>
          <strong class="critical-color">{{ criticalCount }}</strong
          ><span>严重</span>
        </div>
        <el-button size="mini" icon="el-icon-refresh" @click="loadActiveAlerts">刷新状态</el-button>
      </div>
      <div class="admin-card admin-table-card">
        <el-table :data="rules" size="small" empty-text="暂无告警规则"
          ><el-table-column label="规则名称" min-width="150"
            ><template slot-scope="scope">{{
              ruleNameLabel(scope.row.name)
            }}</template></el-table-column
          ><el-table-column prop="metric" label="指标" min-width="180"
            ><template slot-scope="scope"
              ><el-tag size="mini">{{ metricLabel(scope.row.metric) }}</el-tag></template
            ></el-table-column
          ><el-table-column label="作用节点" min-width="145"
            ><template slot-scope="scope">{{
              nodeLabel(scope.row.nodeClientId)
            }}</template></el-table-column
          ><el-table-column label="阈值" width="155"
            ><template slot-scope="scope"
              ><span class="warning-color"
                >{{ scope.row.warningThreshold }}{{ metricUnit(scope.row.metric) }}</span
              >
              /
              <span class="critical-color"
                >{{ scope.row.criticalThreshold }}{{ metricUnit(scope.row.metric) }}</span
              ></template
            ></el-table-column
          ><el-table-column label="启用" width="75"
            ><template slot-scope="scope"
              ><el-switch
                v-model="scope.row.enabled"
                @change="saveAlert(scope.row)" /></template></el-table-column
          ><el-table-column label="操作" width="135"
            ><template slot-scope="scope"
              ><el-button type="text" size="mini" @click="openAlertDialog(scope.row)"
                >编辑</el-button
              ><el-button
                type="text"
                size="mini"
                class="danger-text"
                @click="removeAlert(scope.row)"
                >删除</el-button
              ></template
            ></el-table-column
          ></el-table
        >
      </div>
      <div class="admin-card">
        <div class="card-title">
          <span>未确认告警</span
          ><el-tag size="mini" :type="activeAlerts.length ? 'danger' : 'success'">{{
            activeAlerts.length ? '需要关注' : '运行正常'
          }}</el-tag>
        </div>
        <div v-if="activeAlerts.length" class="active-alert-list">
          <div
            v-for="event in activeAlerts"
            :key="`${event.ruleId}-${event.clientId}`"
            class="active-alert"
            :class="event.level === 'CRITICAL' ? 'active-alert--critical' : ''"
          >
            <i class="el-icon-warning-outline" />
            <div>
              <b>{{ event.message }}</b
              ><small
                >告警时间 {{ formatTime(event.startedAt) }} · {{ ruleNameLabel(event.ruleName) }} ·
                阈值 {{ event.threshold }}{{ metricUnit(event.metric) }} · 持续
                {{ formatDuration(event.durationSeconds) }} · {{ levelLabel(event.level) }}</small
              >
            </div>
          </div>
        </div>
        <div v-else class="panel-empty">当前没有未确认告警</div>
      </div>
    </div>

    <el-dialog
      :title="editingNode ? '编辑节点配置' : '新增节点配置'"
      :visible.sync="nodeDialogVisible"
      width="560px"
      ><el-form :model="nodeForm" label-width="95px" size="small"
        ><el-form-item label="节点标识" required
          ><el-input
            v-model="nodeForm.clientId"
            :disabled="!!editingNode"
            placeholder="请输入采集端标识" /></el-form-item
        ><el-form-item label="展示名称"
          ><el-input
            v-model="nodeForm.displayName"
            placeholder="例如：生产服务器 01" /></el-form-item
        ><el-form-item label="环境"
          ><el-select v-model="nodeForm.environment" placeholder="请选择" clearable
            ><el-option label="生产" value="production" /><el-option
              label="测试"
              value="testing" /><el-option
              label="开发"
              value="development" /></el-select></el-form-item
        ><el-form-item label="位置"
          ><el-input v-model="nodeForm.location" placeholder="例如：上海机房 A 区" /></el-form-item
        ><el-form-item label="描述"
          ><el-input v-model="nodeForm.description" type="textarea" :rows="2" /></el-form-item
        ><el-form-item label="展示指标"
          ><el-checkbox v-model="nodeForm.displaySystem">系统指标</el-checkbox
          ><el-checkbox v-model="nodeForm.displayDocker">容器</el-checkbox
          ><el-checkbox v-model="nodeForm.displayMicroservice">微服务</el-checkbox></el-form-item
        ><el-form-item label="节点启用"
          ><el-switch v-model="nodeForm.enabled" /></el-form-item></el-form
      ><span slot="footer"
        ><el-button @click="nodeDialogVisible = false">取消</el-button
        ><el-button type="primary" :loading="saving" @click="submitNode"
          >保存到数据库</el-button
        ></span
      ></el-dialog
    >
    <el-dialog
      :title="editingAlert ? '编辑告警规则' : '新增告警规则'"
      :visible.sync="alertDialogVisible"
      width="520px"
      ><el-form :model="alertForm" label-width="105px" size="small"
        ><el-form-item label="规则名称" required
          ><el-input
            v-model="alertForm.name"
            placeholder="例如：生产服务器处理器使用率过高" /></el-form-item
        ><el-form-item label="监控指标" required
          ><el-select v-model="alertForm.metric" @change="handleMetricChange"
            ><el-option
              v-for="item in metricOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value" /></el-select></el-form-item
        ><el-form-item label="作用节点"
          ><el-select v-model="alertForm.nodeClientId" clearable placeholder="全部节点"
            ><el-option
              v-for="node in nodes"
              :key="node.clientId"
              :label="nodeLabel(node.clientId)"
              :value="node.clientId" /></el-select></el-form-item
        ><el-form-item label="预警阈值"
          ><el-input-number
            v-model="alertForm.warningThreshold"
            :min="0"
            :max="metricThresholdMax"
            :precision="metricThresholdPrecision"
          />
          {{ metricUnit(alertForm.metric) }}</el-form-item
        ><el-form-item label="严重阈值"
          ><el-input-number
            v-model="alertForm.criticalThreshold"
            :min="1"
            :max="metricThresholdMax"
            :precision="metricThresholdPrecision"
          />
          {{ metricUnit(alertForm.metric) }}</el-form-item
        ><el-form-item label="规则启用"
          ><el-switch v-model="alertForm.enabled" /></el-form-item></el-form
      ><span slot="footer"
        ><el-button @click="alertDialogVisible = false">取消</el-button
        ><el-button type="primary" :loading="saving" @click="submitAlert"
          >保存到数据库</el-button
        ></span
      ></el-dialog
    >
  </section>
</template>
<script>
import {
  getNodeConfigs,
  createNodeConfig,
  updateNodeConfig,
  deleteNodeConfig,
  getAlertRules,
  createAlertRule,
  updateAlertRule,
  deleteAlertRule,
  getActiveAlerts
} from '../services/monitor'
import { alertLevelLabel, alertRuleNameLabel, formatAlertDuration } from '../constants/alertTypes'
import { formatServerTime } from '../utils/dateTime'
const emptyNode = () => ({
  clientId: '',
  displayName: '',
  description: '',
  environment: '',
  location: '',
  enabled: true,
  displaySystem: true,
  displayDocker: true,
  displayMicroservice: true
})
const emptyAlert = () => ({
  name: '',
  nodeClientId: '',
  metric: 'CPU',
  warningThreshold: 70,
  criticalThreshold: 90,
  enabled: true
})
export default {
  data: () => ({
    activeModule: 'nodes',
    nodes: [],
    rules: [],
    activeAlerts: [],
    loading: false,
    saving: false,
    nodeDialogVisible: false,
    alertDialogVisible: false,
    editingNode: null,
    editingAlert: null,
    nodeForm: emptyNode(),
    alertForm: emptyAlert(),
    metricOptions: [
      { value: 'CPU', label: '处理器使用率' },
      { value: 'MEMORY', label: '内存使用率' },
      { value: 'DISK', label: '磁盘使用率' },
      { value: 'JVM', label: '虚拟机堆内存使用率' },
      { value: 'LARGE_TABLE_QUERY', label: '瀚高大表全表查询次数' },
      { value: 'LOGIN_FAILURE', label: '瀚高数据库登录失败次数' }
    ]
  }),
  computed: {
    warningCount() {
      return this.activeAlerts.filter((a) => a.level === 'WARNING').length
    },
    criticalCount() {
      return this.activeAlerts.filter((a) => a.level === 'CRITICAL').length
    },
    metricThresholdMax() {
      return ['LARGE_TABLE_QUERY', 'LOGIN_FAILURE'].includes(this.alertForm.metric) ? 10000 : 100
    },
    metricThresholdPrecision() {
      return ['LARGE_TABLE_QUERY', 'LOGIN_FAILURE'].includes(this.alertForm.metric) ? 0 : 1
    }
  },
  mounted() {
    this.refreshAll()
  },
  methods: {
    refreshAll() {
      this.loading = true
      Promise.all([this.loadNodes(), this.loadRules(), this.loadActiveAlerts()]).finally(() => {
        this.loading = false
      })
    },
    loadNodes() {
      return getNodeConfigs()
        .then((data) => {
          this.nodes = data || []
        })
        .catch(() => {})
    },
    loadRules() {
      return getAlertRules()
        .then((data) => {
          this.rules = data || []
        })
        .catch(() => {})
    },
    loadActiveAlerts() {
      return getActiveAlerts()
        .then((data) => {
          this.activeAlerts = data || []
        })
        .catch(() => {})
    },
    nodeLabel(id) {
      if (!id) return '全部节点'
      const node = this.nodes.find((n) => n.clientId === id)
      return node ? `${node.displayName || node.hostname} (${id})` : id
    },
    metricLabel(metric) {
      const item = this.metricOptions.find((i) => i.value === metric)
      return item ? item.label : '未知指标'
    },
    metricUnit(metric) {
      return ['LARGE_TABLE_QUERY', 'LOGIN_FAILURE'].includes(metric) ? '次' : '%'
    },
    /**
     * 将告警级别转换为中文展示名称。
     *
     * @param {string} level 告警级别
     * @returns {string} 中文级别名称
     */
    levelLabel(level) {
      return alertLevelLabel(level)
    },
    /**
     * 将已有规则名称中的英文技术缩写转换为中文。
     *
     * @param {string} ruleName 规则名称
     * @returns {string} 中文规则名称
     */
    ruleNameLabel(ruleName) {
      return alertRuleNameLabel(ruleName)
    },
    handleMetricChange(metric) {
      if (metric === 'LARGE_TABLE_QUERY') {
        this.alertForm.warningThreshold = 1
        this.alertForm.criticalThreshold = 5
        return
      }
      this.alertForm.warningThreshold = 70
      this.alertForm.criticalThreshold = 90
    },
    formatTime(value) {
      return formatServerTime(value)
    },
    formatDuration(seconds) {
      return formatAlertDuration(seconds)
    },
    openNodeDialog(node) {
      this.editingNode = node || null
      this.nodeForm = node
        ? {
            clientId: node.clientId,
            displayName: node.displayName,
            description: node.description,
            environment: node.environment,
            location: node.location,
            enabled: node.enabled,
            displaySystem: node.displaySystem,
            displayDocker: node.displayDocker,
            displayMicroservice: node.displayMicroservice
          }
        : emptyNode()
      this.nodeDialogVisible = true
    },
    submitNode() {
      if (!this.nodeForm.clientId) return this.$message.warning('请输入节点标识')
      this.saving = true
      const req =
        this.editingNode && this.editingNode.id
          ? updateNodeConfig(this.editingNode.id, this.nodeForm)
          : createNodeConfig(this.nodeForm)
      req
        .then(() => {
          this.$message.success('节点配置已保存')
          this.nodeDialogVisible = false
          return this.loadNodes()
        })
        .catch(this.showError)
        .finally(() => {
          this.saving = false
        })
    },
    saveNode(node) {
      const req = node.id ? updateNodeConfig(node.id, node) : createNodeConfig(node)
      req
        .then(() => {
          this.$message.success('节点状态已保存')
          this.loadNodes()
        })
        .catch(this.showError)
    },
    removeNode(node) {
      if (!node.id) return this.$message.info('该在线节点尚未保存配置，无需删除')
      this.$confirm(`确定删除节点配置“${node.displayName || node.clientId}”吗？`, '提示', {
        type: 'warning'
      })
        .then(() => deleteNodeConfig(node.id))
        .then(() => {
          this.$message.success('已删除')
          this.loadNodes()
        })
        .catch(() => {})
    },
    openAlertDialog(rule) {
      this.editingAlert = rule || null
      this.alertForm = rule
        ? {
            name: rule.name,
            nodeClientId: rule.nodeClientId || '',
            metric: rule.metric,
            warningThreshold: Number(rule.warningThreshold),
            criticalThreshold: Number(rule.criticalThreshold),
            enabled: rule.enabled
          }
        : emptyAlert()
      this.alertDialogVisible = true
    },
    submitAlert() {
      if (!this.alertForm.name) return this.$message.warning('请输入规则名称')
      if (this.alertForm.warningThreshold >= this.alertForm.criticalThreshold)
        return this.$message.warning('预警阈值必须小于严重阈值')
      this.saving = true
      const req = this.editingAlert
        ? updateAlertRule(this.editingAlert.id, this.alertForm)
        : createAlertRule(this.alertForm)
      req
        .then(() => {
          this.$message.success('告警规则已保存')
          this.alertDialogVisible = false
          return Promise.all([this.loadRules(), this.loadActiveAlerts()])
        })
        .catch(this.showError)
        .finally(() => {
          this.saving = false
        })
    },
    saveAlert(rule) {
      updateAlertRule(rule.id, rule)
        .then(() => this.loadActiveAlerts())
        .catch(this.showError)
    },
    removeAlert(rule) {
      this.$confirm(`确定删除规则“${rule.name}”吗？`, '提示', {
        type: 'warning'
      })
        .then(() => deleteAlertRule(rule.id))
        .then(() => {
          this.$message.success('已删除')
          return Promise.all([this.loadRules(), this.loadActiveAlerts()])
        })
        .catch(() => {})
    },
    showError(err) {
      this.$message.error(
        (err && err.response && err.response.data && err.response.data.message) ||
          '保存失败，请检查服务端和数据库连接'
      )
    }
  }
}
</script>
