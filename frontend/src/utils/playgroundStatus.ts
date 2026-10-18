// Playground 状态检查工具
//
// 本文件只写两个「挂到 window 上供控制台调试用」的全局量（#521：替代 `(window as any)`）。
// 用一个本地的 Window 扩展把这两处收成具名类型，避免任何处都能往 window 上塞东西。
declare global {
  interface Window {
    /** 由调试插件/宿主页挂上的 Vue 应用实例；本文件只判断存在性，不读其形状 */
    __VUE_APP__?: unknown
    /** 供浏览器控制台调用的状态检查入口 */
    checkPlaygroundStatus?: () => void
  }
}

export const checkPlaygroundStatus = () => {
  console.log('=== Playground 状态检查 ===')

  // 检查 DOM 元素
  const chatTab = document.querySelector('[name="chat"]')
  const instanceSelect = document.querySelector('el-select[placeholder*="对话服务实例"]')

  console.log('DOM 元素:', {
    chatTab: !!chatTab,
    instanceSelect: !!instanceSelect
  })

  // 检查 Vue 组件实例（如果可能）
  const app = window.__VUE_APP__
  if (app) {
    console.log('Vue 应用实例存在')
  }

  console.log('=== 状态检查完成 ===')
}

// 在浏览器控制台中可以调用这个函数
if (typeof window !== 'undefined') {
  window.checkPlaygroundStatus = checkPlaygroundStatus
}
