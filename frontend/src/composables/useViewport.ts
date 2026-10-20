import { onBeforeUnmount, ref, type Ref } from 'vue'

/**
 * 视口断点（P1-3 表格窄屏卡片化）。
 *
 * - narrow：≤ 767px，表格改为卡片列表
 * - compact：768–1024px，隐藏次要列、不允许横向滚动
 * - 其余（≥ 1025px）视为 wide，保持原有表格
 *
 * narrow 与 compact 互斥：compact 的媒体查询本身带 `min-width: 768px`，
 * 同时再用 narrow 结果做一次兜底，避免浏览器在临界值上同时命中两条查询。
 *
 * SSR / 单元测试中没有 `window.matchMedia` 时返回保守默认值（宽屏），
 * 页面会按现有表格渲染，不会因为缺少浏览器 API 而报错。
 */
const NARROW_QUERY = '(max-width: 767px)'
const COMPACT_QUERY = '(min-width: 768px) and (max-width: 1024px)'

export interface ViewportState {
  /** ≤ 767px */
  isNarrow: Ref<boolean>
  /** 768–1024px（与 isNarrow 互斥） */
  isCompact: Ref<boolean>
}

const addChangeListener = (query: MediaQueryList, handler: () => void): void => {
  if (typeof query.addEventListener === 'function') {
    query.addEventListener('change', handler)
  } else if (typeof query.addListener === 'function') {
    // 兼容旧版 Safari；addEventListener 不存在时才走这里
    query.addListener(handler)
  }
}

const removeChangeListener = (query: MediaQueryList, handler: () => void): void => {
  if (typeof query.removeEventListener === 'function') {
    query.removeEventListener('change', handler)
  } else if (typeof query.removeListener === 'function') {
    query.removeListener(handler)
  }
}

export function useViewport(): ViewportState {
  const isNarrow = ref(false)
  const isCompact = ref(false)

  if (typeof window === 'undefined' || typeof window.matchMedia !== 'function') {
    return { isNarrow, isCompact }
  }

  const narrowQuery = window.matchMedia(NARROW_QUERY)
  const compactQuery = window.matchMedia(COMPACT_QUERY)

  const sync = (): void => {
    isNarrow.value = narrowQuery.matches
    isCompact.value = !narrowQuery.matches && compactQuery.matches
  }

  sync()
  addChangeListener(narrowQuery, sync)
  addChangeListener(compactQuery, sync)

  onBeforeUnmount(() => {
    removeChangeListener(narrowQuery, sync)
    removeChangeListener(compactQuery, sync)
  })

  return { isNarrow, isCompact }
}
