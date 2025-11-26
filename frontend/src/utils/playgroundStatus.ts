// Playground 状态检查工具
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
  const app = (window as any).__VUE_APP__
  if (app) {
    console.log('Vue 应用实例存在')
  }
  
  console.log('=== 状态检查完成 ===')
}

// 在浏览器控制台中可以调用这个函数
if (typeof window !== 'undefined') {
  (window as any).checkPlaygroundStatus = checkPlaygroundStatus
}