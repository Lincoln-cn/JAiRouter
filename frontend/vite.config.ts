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
  base: '/admin/',
  build: {
    outDir: 'dist',
    assetsDir: 'assets',
    sourcemap: false,
    minify: 'terser',
    chunkSizeWarningLimit: 1000, // 增加块大小警告限制
    rollupOptions: {
      output: {
        // Vite v8 requires manualChunks to be a function
        manualChunks: (id) => {
          // 核心框架
          if (id.includes('node_modules/vue/dist/') || id.includes('node_modules\\vue\\dist\\')) {
            return 'vue'
          }
          if (id.includes('node_modules/vue-router/dist/') || id.includes('node_modules\\vue-router\\dist\\')) {
            return 'vue-router'
          }
          if (id.includes('node_modules/pinia/dist/') || id.includes('node_modules\\pinia\\dist\\')) {
            return 'pinia'
          }
          // UI框架
          if (id.includes('node_modules/element-plus/') || id.includes('node_modules\\element-plus\\')) {
            return 'element-plus'
          }
          if (id.includes('node_modules/@element-plus/icons-vue/') || id.includes('node_modules\\@element-plus\\icons-vue\\')) {
            return 'element-plus-icons'
          }
          // 图表库
          if (id.includes('node_modules/echarts/') || id.includes('node_modules\\echarts\\')) {
            return 'echarts'
          }
          if (id.includes('node_modules/vue-echarts/') || id.includes('node_modules\\vue-echarts\\')) {
            return 'vue-echarts'
          }
          // 网络请求
          if (id.includes('node_modules/axios/') || id.includes('node_modules\\axios\\')) {
            return 'axios'
          }
          // 国际化
          if (id.includes('node_modules/vue-i18n/') || id.includes('node_modules\\vue-i18n\\')) {
            return 'vue-i18n'
          }
          // 其他 node_modules 放入 vendor
          if (id.includes('node_modules/')) {
            return 'vendor'
          }
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
        target: process.env.VITE_API_URL || 'http://127.0.0.1:9900',
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