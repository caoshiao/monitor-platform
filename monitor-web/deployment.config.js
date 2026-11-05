/**
 * 本地开发服务器配置，供 Vite 读取。
 * 生产环境的后端地址请修改 public/config.js，打包后直接修改 dist/config.js。
 */
export default {
  development: {
    port: 3000,
    host: '0.0.0.0',
    backendUrl: 'http://localhost:8080'
  }
}
