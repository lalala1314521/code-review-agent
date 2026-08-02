import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// Vite 配置：开发服务器代理后端 API
// 前端跑 5173，后端跑 8080，/api 请求代理到后端
export default defineConfig({
  plugins: [vue()],
  server: {
    // host: true 监听 0.0.0.0（IPv4 + IPv6），否则默认只绑 IPv6 ::1，部分工具走 IPv4 连不上
    host: true,
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
})
