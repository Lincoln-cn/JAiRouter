import { watch, onBeforeUnmount } from 'vue'
import { useI18n } from 'vue-i18n'
import { useTheme } from './useTheme'

/**
 * 图表语言 / 主题自动重绘 composable（v2.10.4-2）
 *
 * 页面在图表初始化完成（数据已渲染、实例已创建）后注册一次：
 *   const rebuildAll = () => { rebuildEveryChartFromCurrentRefs() }
 *   useChartAutoRefresh(rebuildAll)
 *
 * 内部 watch 两个全局响应式源，任一变化即触发 refresh()：
 *   1. vue-i18n `locale` —— tooltip / legend / axis / series 中 t() 文案随新语言重取；
 *   2. `useTheme` 导出的 `isDark` ref —— 模块级单例 ref，主题切换（Layout.toggleTheme /
 *      main.ts initTheme）都会改它并同步 html.dark 类，故直接 watch ref 即可，
 *      无需 MutationObserver 监听 class。
 *
 * 约定：
 * - flush:'post'：在 DOM / 新语言渲染之后执行，避免重建时读到旧文案；
 * - refresh() 内禁止发起网络请求，只读取已加载的 refs 重绘；
 * - 图表实例尚未创建时应自行安全跳过（refresh 内部空值保护）。
 * - 生命周期绑定调用方组件：watch 自动随组件卸载停止（返回 stop 亦可手动停止）。
 */
export function useChartAutoRefresh(refresh: () => void) {
  const { locale } = useI18n()
  const { isDark } = useTheme()

  const stop = watch(
    [locale, isDark],
    () => {
      refresh()
    },
    { flush: 'post' }
  )

  // 组件卸载时兜底清理（watch 本身随实例 effect scope 自动停止）
  onBeforeUnmount(() => {
    stop()
  })

  return { stop }
}
