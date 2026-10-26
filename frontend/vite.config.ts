import vue from '@vitejs/plugin-vue'
import { defineConfig } from 'vite'

/**
 * Phase 1 开发配置
 * - 开发服务器：5173
 * - API 代理：/api → http://localhost:8080（Spring Boot）
 * - Vite 原生代码分片 + LCP ≤ 1.5s（NFR-P1）
 */
export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
  build: {
    target: 'es2020',
    sourcemap: false,
    chunkSizeWarningLimit: 2000,
  },
})
