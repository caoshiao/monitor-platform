# monitor-web

独立的监控平台前端，使用 Vue 2、Element UI 与 Vite。

## 开发

```bash
npm install
npm run dev
```

Vite 会将 `/api` 和 `/ws` 代理到 Spring Boot 服务的 `8080` 端口。两个前端完全独立：

- 展示端：http://localhost:3000/index.html
- 管理端：http://localhost:3000/admin.html

## 生产构建

```bash
npm run build
npm run preview
```

通过 `VITE_API_BASE` 指定后端地址，例如 `VITE_API_BASE=https://monitor.example.com`。后端保留采集端 `/ws/client` 与前端实时通道 `/ws/frontend`，前端不再依赖服务端静态资源目录。
