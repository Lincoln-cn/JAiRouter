import {defineConfig} from 'vite'
import vue from '@vitejs/plugin-vue'
import {resolve} from 'path'
import AutoImport from 'unplugin-auto-import/vite'
import Components from 'unplugin-vue-components/vite'
import {ElementPlusResolver} from 'unplugin-vue-components/resolvers'

// https://vitejs.dev/config/
export default defineConfig({
  plugins: [
    vue(),
    AutoImport({
      resolvers: [ElementPlusResolver()],
      imports: ['vue', 'vue-router', 'pinia'],
      dirs: ['src/stores'],
      dts: 'auto-imports.d.ts'
    }),
    Components({
      resolvers: [ElementPlusResolver()],
      dirs: ['src/components'],
      dts: 'components.d.ts'
    }),
  ],
  base: '/',
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
    host: '0.0.0.0',
    port: 3000,
    proxy: {
      '/api': {
        target: process.env.VITE_API_URL || 'http://127.0.0.1:8080',
        changeOrigin: true,
        secure: false,
        configure: (proxy, options) => {
          // 添加请求日志
          proxy.on('proxyReq', (proxyReq, req, res) => {
            console.log(`[Vite Proxy] Request: ${req.method} ${req.url} -> ${options.target}${req.url}`)
          });
          // 添加响应日志
          proxy.on('proxyRes', (proxyRes, req, res) => {
            console.log(`[Vite Proxy] Response: ${req.method} ${req.url} -> Status: ${proxyRes.statusCode}`)
          });
          // 添加错误日志
          proxy.on('error', (err, req, res) => {
            console.error(`[Vite Proxy] Error: ${req.method} ${req.url} ->`, err.message)
          });
        }
      }
    }
  },
  define: {
    __VUE_OPTIONS_API__: true,
    __VUE_PROD_DEVTOOLS__: false
  }
})