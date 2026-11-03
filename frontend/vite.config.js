import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import path from 'path'

// 约束文档 1.2：Vue 3 + Vite + Element Plus
// 风险文档 #47：开发环境通过 Vite proxy 解决跨域
export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, 'src')
    }
  },
  server: {
    port: 5173,
    host: true,
    // 风险文档 #47：开发环境代理到后端，解决 CORS
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true
      },
      // 头像等上传静态资源：后端 Spring Boot context-path 是 /api，所以 /uploads 直连根
      '/uploads': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        rewrite: (p) => '/api' + p   // 后端 context-path=/api，必须加前缀
      }
    }
  },
  build: {
    outDir: 'dist',
    sourcemap: false,
    chunkSizeWarningLimit: 1200, // echarts 全量引入约 820KB，允许合理上限
    rollupOptions: {
      output: {
        manualChunks: (id) => {
          // 按 node_modules 路径切分包 —— 精准命中，不依赖包名枚举
          if (id.includes('node_modules')) {
            if (id.includes('element-plus') || id.includes('@element-plus')) return 'element-plus'
            if (id.includes('echarts'))                                   return 'echarts'
            if (id.includes('vue') || id.includes('vue-router')
             || id.includes('pinia'))                                    return 'vue-vendor'
            if (id.includes('axios'))                                     return 'axios'
            return 'vendor' // 其他第三方库兜底
          }
        },
        // chunk 命名：保持清晰可读（rollup 默认 hash 仍会追加）
        entryFileNames: 'assets/[name]-[hash].js',
        chunkFileNames: 'assets/[name]-[hash].js',
        assetFileNames: 'assets/[name]-[hash][extname]'
      }
    }
  }
})
