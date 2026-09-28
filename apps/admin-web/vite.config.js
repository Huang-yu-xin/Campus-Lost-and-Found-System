import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// 管理后台开发服务器；/api 代理到后端 /api/v1。
export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true
      }
    }
  }
})
