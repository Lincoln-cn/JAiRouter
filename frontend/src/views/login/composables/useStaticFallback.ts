import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { useMediaQuery } from '@vueuse/core'

/**
 * 登录页的两条「静止」退路（计划 §7.0 A3）：
 *
 * 1. URL 带 `?static` → 强制静态海报（零动效，便于截图/低配机器/E2E 基线）；
 * 2. `prefers-reduced-motion: reduce` → 与 `?static` 完全等价（无障碍/晕动症）。
 *
 * 两者都走同一个 `isStatic`，因此静态海报只维护一条渲染路径。
 */
export function useStaticFallback() {
  const route = useRoute()
  // 跟随系统设置变化（用户中途改「减少动效」会即时生效）
  const reducedMotion = useMediaQuery('(prefers-reduced-motion: reduce)')
  // `?static`、`?static=1`、`?static=` 都算（只要参数在 query 里出现）
  const forcedStatic = computed(() => route.query.static !== undefined)
  const isStatic = computed(() => forcedStatic.value || reducedMotion.value)

  return { isStatic, forcedStatic, reducedMotion }
}
