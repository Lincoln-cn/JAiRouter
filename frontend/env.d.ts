/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_API_BASE_URL: string
  readonly VITE_WS_BASE_URL: string
  readonly VITE_APP_TITLE: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}

// Element Plus 图标类型定义
declare module '@element-plus/icons-vue' {
  import { DefineComponent } from 'vue'
  const icons: Record<string, DefineComponent>
  export default icons
}