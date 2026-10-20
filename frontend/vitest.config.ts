import { defineConfig } from 'vitest/config'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
      // Element Plus 2.x 以 ESM 引入 @popperjs/core 的具名导出，而该包的
      // package.json 没有 exports 字段 ⇒ Vitest 会解析到 CJS 入口并报
      // "Named export 'placements' not found"。挂载布局组件（views/Layout.vue）的
      // 用例因此指向同包的 ESM 产物，行为与 Vite 构建时一致。
      '@popperjs/core': fileURLToPath(
        new URL('./node_modules/@popperjs/core/dist/esm/index.js', import.meta.url)
      )
    }
  },
  test: {
    environment: 'happy-dom',
    include: ['src/**/*.{test,spec}.ts'],
    globals: false,
    server: {
      deps: {
        // element-plus 必须由 Vite 处理：它从 @popperjs/core 取具名导出，
        // 交给 Node 直接 ESM 加载会撞上该包的 CJS 入口（见上方 alias 注释）
        inline: ['element-plus']
      }
    }
  }
})
