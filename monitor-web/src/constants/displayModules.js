/** 展示端监控模块定义，统一维护模块路由、标题和说明，避免组件内散落魔法值。 */
export const DISPLAY_MODULES = Object.freeze([
  {
    key: 'external-api',
    label: '外部接口状态',
    description: '外部接口健康检查与响应耗时',
    icon: 'el-icon-link',
    value: 'EXTERNAL API STATUS'
  },
  {
    key: 'highgo',
    label: '数据库监控',
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
  }
])

export const DISPLAY_MODULE_META = Object.freeze({
  middleware: {
    title: '中间件监控',
    description: '按节点查看中间件实例、探测方式、可用状态与响应耗时'
  },
  docker: { title: 'Docker 容器', description: '容器运行状态与资源使用情况' },
  services: { title: '微服务', description: '微服务健康检查结果' },
  'external-api': { title: '外部接口状态', description: '外部接口健康检查结果' },
  highgo: { title: '数据库监控', description: 'PostgreSQL / 瀚高实例运行指标与日志事件' },
  alerts: { title: '告警中心', description: '查看全部告警记录并确认待处理事件' },
  nodes: { title: '在线节点', description: '查看节点总数及在线状态' }
})
