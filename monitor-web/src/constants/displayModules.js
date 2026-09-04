/** 展示端监控模块定义，统一维护模块路由、标题和说明，避免组件内散落魔法值。 */
export const DISPLAY_MODULES = Object.freeze([
  {
    key: 'system',
    label: '系统指标',
    description: 'CPU、内存、磁盘、网络与负载',
    icon: 'el-icon-data-analysis',
    value: 'SYSTEM TELEMETRY'
  },
  {
    key: 'docker',
    label: 'Docker 容器',
    description: '容器运行状态与资源使用',
    icon: 'el-icon-box',
    value: 'CONTAINER STATUS'
  },
  {
    key: 'services',
    label: '微服务',
    description: '服务健康检查与响应耗时',
    icon: 'el-icon-s-grid',
    value: 'SERVICE HEALTH'
  },
  {
    key: 'highgo',
    label: '瀚高数据库',
    description: '连接、负载、审计与大表查询',
    icon: 'el-icon-connection',
    value: 'DATABASE AUDIT'
  },
  {
    key: 'alerts',
    label: '告警中心',
    description: '活动告警与历史告警记录',
    icon: 'el-icon-bell',
    value: 'ALERT EVENTS'
  },
  {
    key: 'nodes',
    label: '节点管理',
    description: '节点状态与展示配置',
    icon: 'el-icon-connection',
    value: 'NODE CONFIGURATION'
  }
])

export const DISPLAY_MODULE_META = Object.freeze({
  system: { title: '系统指标', description: '主机资源与系统负载实时数据' },
  docker: { title: 'Docker 容器', description: '容器运行状态与资源使用情况' },
  services: { title: '微服务', description: '微服务健康检查结果' },
  highgo: { title: '瀚高数据库', description: '瀚高数据库连接和审计指标' },
  alerts: { title: '告警中心', description: '查看当前正在发生的告警事件' }
})
