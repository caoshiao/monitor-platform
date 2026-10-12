const ALERT_TYPE_LABELS = Object.freeze({
  CPU: '处理器使用率',
  MEMORY: '内存使用率',
  DISK: '磁盘使用率',
  JVM: '虚拟机堆内存使用率',
  LARGE_TABLE_QUERY: '数据库全表查询',
  LOGIN_FAILURE: '数据库登录失败'
})

const ALERT_LEVEL_LABELS = Object.freeze({ WARNING: '预警', CRITICAL: '严重' })
const ALERT_STATUS_LABELS = Object.freeze({
  ACTIVE: '告警中',
  RESOLVED: '已恢复',
  ACKNOWLEDGED: '已确认'
})

/**
 * 获取告警指标对应的中文类型名称。
 *
 * @param {string} metric 告警指标编码
 * @returns {string} 告警类型名称
 */
export function alertTypeLabel(metric) {
  return ALERT_TYPE_LABELS[metric] || '未知告警'
}

/**
 * 获取告警级别对应的中文名称。
 *
 * @param {string} level 告警级别编码
 * @returns {string} 中文级别名称
 */
export function alertLevelLabel(level) {
  return ALERT_LEVEL_LABELS[level] || '未知'
}

/**
 * 获取告警状态对应的中文名称。
 *
 * @param {string} status 告警状态编码
 * @returns {string} 中文状态名称
 */
export function alertStatusLabel(status) {
  return ALERT_STATUS_LABELS[status] || '未知'
}

/**
 * 将已有规则名称中的英文技术缩写转换为中文。
 *
 * @param {string} ruleName 规则名称
 * @returns {string} 中文规则名称
 */
export function alertRuleNameLabel(ruleName) {
  return String(ruleName || '-')
    .replace(/CPU/gi, '处理器')
    .replace(/JVM/gi, '虚拟机')
    .replace(/Docker/gi, '容器')
}

/**
 * 将告警持续秒数转换为中文时长。
 *
 * @param {number} seconds 持续秒数
 * @returns {string} 中文持续时长
 */
export function formatAlertDuration(seconds) {
  const total = Number(seconds || 0)
  if (total < 60) return `${total} 秒`
  const minutes = Math.floor(total / 60)
  const hours = Math.floor(minutes / 60)
  const days = Math.floor(hours / 24)
  if (days) return `${days} 天 ${hours % 24} 小时`
  if (hours) return `${hours} 小时 ${minutes % 60} 分钟`
  return `${minutes} 分钟`
}
