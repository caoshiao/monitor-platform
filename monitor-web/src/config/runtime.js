/** 浏览器运行时配置解析，统一 HTTP 与 WebSocket 的地址计算。 */

/**
 * 读取外置配置，配置缺失时明确报错，避免请求错误的默认后端。
 * @returns {Object} 部署目录中的运行时配置。
 */
function readConfig() {
  if (!window.MONITOR_CONFIG || typeof window.MONITOR_CONFIG.apiBase !== 'string') {
    throw new Error('运行时配置缺失或无效，请检查站点目录中的 config.js')
  }
  return window.MONITOR_CONFIG
}

/**
 * 解析接口根地址，相对路径以页面所在目录为基准，忽略哈希路由。
 * @returns {string} 不带末尾斜杠的 HTTP 接口前缀。
 */
export function getApiBase() {
  const address = new URL(readConfig().apiBase, window.location.href)
  if (!['http:', 'https:'].includes(address.protocol) || address.search || address.hash) {
    throw new Error('apiBase 必须为 HTTP 地址或站点路径，不能包含查询参数和哈希')
  }
  return address.href.replace(/\/+$/, '')
}

/**
 * 解析独立 WebSocket 配置，未指定时沿用接口前缀并自动切换协议。
 * @returns {string} 前端实时通道完整地址。
 */
export function getWebsocketUrl() {
  const configured = readConfig().websocketUrl
  const address = new URL(configured || `${getApiBase()}/ws/frontend`, window.location.href)
  if (address.protocol === 'https:') address.protocol = 'wss:'
  if (address.protocol === 'http:') address.protocol = 'ws:'
  if (!['ws:', 'wss:'].includes(address.protocol)) {
    throw new Error('websocketUrl 必须为 WebSocket 地址或站点路径')
  }
  return address.href
}
