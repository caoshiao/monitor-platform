import { defineConfig } from 'vite'
import vue2 from '@vitejs/plugin-vue2'
import { resolve } from 'node:path'
import deployment from './deployment.config.js'

export default defineConfig(({ mode }) => ({
  base: mode === 'production' ? './' : '/',
  plugins: [vue2()],
  server: {
    port: deployment.development.port,
    host: deployment.development.host,
    proxy: {
      '/api': { target: deployment.development.backendUrl, changeOrigin: true },
      '/ws': {
        target: deployment.development.backendUrl,
        ws: true,
        changeOrigin: true,
        configure(proxy) {
          proxy.on('error', (error, request) => {
            console.warn(`[WebSocket代理] ${request.url} 连接异常: ${error.message}`)
          })
        }
      }
    }
  },
  build: {
    outDir: 'dist',
    sourcemap: true,
    rollupOptions: {
      input: {
        display: resolve(__dirname, 'index.html'),
        admin: resolve(__dirname, 'admin.html')
      }
    }
  }
}))
