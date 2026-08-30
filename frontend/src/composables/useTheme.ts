import { ref, watch, onMounted } from 'vue'

const STORAGE_KEY = 'ja-theme'
const isDark = ref(false)

/**
 * 主题切换 composable
 * - 从 localStorage 恢复用户偏好
 * - 切换 html.dark 类名
 * - 持久化到 localStorage
 */
export function useTheme() {
  // 初始化：从 localStorage 读取
  const initTheme = () => {
    const saved = localStorage.getItem(STORAGE_KEY)
    if (saved === 'dark') {
      isDark.value = true
    } else if (saved === 'light') {
      isDark.value = false
    } else {
      // 未设置过则跟随系统偏好
      isDark.value = window.matchMedia('(prefers-color-scheme: dark)').matches
    }
    applyTheme()
  }

  // 应用到 DOM
  const applyTheme = () => {
    if (isDark.value) {
      document.documentElement.classList.add('dark')
    } else {
      document.documentElement.classList.remove('dark')
    }
  }

  // 切换主题
  const toggleTheme = () => {
    isDark.value = !isDark.value
  }

  // 监听变化并持久化
  watch(isDark, () => {
    applyTheme()
    localStorage.setItem(STORAGE_KEY, isDark.value ? 'dark' : 'light')
  })

  return {
    isDark,
    initTheme,
    toggleTheme
  }
}
