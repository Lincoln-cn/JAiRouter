import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { resolve } from 'path'
import AutoImport from 'unplugin-auto-import/vite'
import Components from 'unplugin-vue-components/vite'
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers'

// https://vitejs.dev/config/
export default defineConfig({
  plugins: [
    vue(),
    AutoImport({
      resolvers: [ElementPlusResolver()],
    }),
    Components({
      resolvers: [ElementPlusResolver()],
    }),
  ],
  base: '/admin/',
  build: {
    outDir: 'dist',
    assetsDir: 'assets',
    sourcemap: false,
    minify: 'terser',
    chunkSizeWarningLimit: 1000, // 增加块大小警告限制
    rollupOptions: {
      output: {
        manualChunks: {
          // 核心框架
          vue: ['vue'],
          'vue-router': ['vue-router'],
          pinia: ['pinia'],
          
          // UI框架
          'element-plus': ['element-plus'],
          'element-plus-icons': ['@element-plus/icons-vue'],
          
          // 图表库
          echarts: ['echarts'],
          'vue-echarts': ['vue-echarts'],
          
          // 网络请求
          axios: ['axios'],
          
          // 国际化
          'vue-i18n': ['vue-i18n']
        }
      }
    }
  },
  resolve: {
    alias: {
      '@': resolve(__dirname, 'src')
    }
  },
  server: {
    port: 3000,
    proxy: {
      '/admin/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        secure: false
      },
      '/admin/ws': {
        target: 'ws://localhost:8080',
        ws: true,
        changeOrigin: true
      }
    }
  },
  define: {
    __VUE_OPTIONS_API__: true,
    __VUE_PROD_DEVTOOLS__: false
  }
})