/**
 * 浏览器运行时配置，打包后原样输出到 dist/config.js。
 * 部署后修改此文件并刷新页面即可生效，无需重新打包；请勿在此保存密码或令牌。
 */
window.MONITOR_CONFIG = {
  // . 表示以当前页面所在目录作为接口前缀，自动适配 / 和 /monitor-platform/。
  // 也可填写 '/monitor-platform' 或完整后端地址 'https://example.com'，不包含 /api。
  apiBase: '.',
  // 留空时从 apiBase 推导 /ws/frontend，HTTPS 页面自动使用 WSS。
  // 单独部署 WebSocket 时，可填写完整地址或同域绝对路径。
  websocketUrl: ''
}
