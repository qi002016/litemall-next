import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'

// https://vitejs.dev/config/
export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    port: 5173,
    host: '0.0.0.0',
    proxy: {
      // C 端接口统一代理到微服务网关。
      // 必须写 127.0.0.1 而非 localhost：Windows 上 localhost 先解析到 ::1，而网关只监听 IPv4，会 502。
      '/app': { target: 'http://127.0.0.1:8000', changeOrigin: true },
      '/agent': { target: 'http://127.0.0.1:8100', changeOrigin: true },
    },
  },
})