import { computed } from 'vue'
import type { ComputedRef } from 'vue'
import { useChartTheme } from './useChartTheme'
import type { EChartsColorPalette } from './useChartTheme'

/**
 * ECharts 统一皮肤（P1-2 第二步的公共底座）。
 *
 * 设计纪律：分类色板全部落在「主色相 + 中性阶」内（主色 → 三级浅阶 → 中性两级），
 * 语义色只通过 `emphasis` 提供给「失败 / 告警」系列，不参与分类着色；
 * `--ja-accent`（青色）不进入图表皮肤。
 *
 * 复用 `useChartTheme()` 的 MutationObserver：`html.dark` 切换时 `chartTheme` 更新，
 * 本文件的 `skin` 随之重算，页面无需再监听主题。
 */
export interface ChartSkin {
  /** 分类色板，长度 6：主色 + 主色浅阶 + 中性（全部为主色相或中性） */
  palette: string[]
  /** 语义色，仅用于「失败 / 告警」系列 */
  emphasis: string
  text: string
  textMuted: string
  border: string
  surface: string
  grid: Record<string, unknown>
  categoryAxis: Record<string, unknown>
  valueAxis: Record<string, unknown>
  tooltip: Record<string, unknown>
  legend: Record<string, unknown>
}

/**
 * 读不到 CSS 令牌时的兜底分类色。
 *
 * 十六进制只允许出现在本文件的常量里（模板与样式一律走 `--ja-*` 令牌）。
 */
const FALLBACK_PALETTE = ['#409eff', '#79bbff', '#a0cfff', '#c6e2ff', '#8b93a1', '#e6e6e6'] as const

/** 语义色兜底：只在「失败 / 告警」系列使用。 */
const FALLBACK_EMPHASIS = '#f56c6c'

/** 提示框底色兜底：tokens.css 恒定义 `--ja-paper`，这里仅在无令牌环境（测试）生效。 */
const FALLBACK_SURFACE = '#ffffff'

function cssVar(name: string, fallback: string): string {
  if (typeof document === 'undefined') {
    return fallback
  }
  const value = getComputedStyle(document.documentElement).getPropertyValue(name).trim()
  return value || fallback
}

function buildSkin(theme: EChartsColorPalette): ChartSkin {
  const primary = cssVar('--ja-primary', FALLBACK_PALETTE[0])
  const primaryLight3 = cssVar('--ja-primary-light-3', FALLBACK_PALETTE[1])
  const primaryLight5 = cssVar('--ja-primary-light-5', FALLBACK_PALETTE[2])
  const primaryLight7 = cssVar('--ja-primary-light-7', FALLBACK_PALETTE[3])
  const ink3 = cssVar('--ja-ink-3', FALLBACK_PALETTE[4])
  const border = cssVar('--ja-border', FALLBACK_PALETTE[5])
  const emphasis = cssVar('--ja-danger', FALLBACK_EMPHASIS)
  // --ja-ink 缺失时退回 useChartTheme 已解析的正文色，避免图表文字不可见
  const text = cssVar('--ja-ink', theme.textColor)
  const surface = cssVar('--ja-paper', FALLBACK_SURFACE)

  return {
    palette: [primary, primaryLight3, primaryLight5, primaryLight7, ink3, border],
    emphasis,
    text,
    textMuted: ink3,
    border,
    surface,
    grid: { left: 8, right: 16, top: 32, bottom: 8, containLabel: true },
    categoryAxis: {
      type: 'category',
      axisLine: { lineStyle: { color: border } },
      axisTick: { show: false },
      axisLabel: { color: ink3, fontSize: 12 }
    },
    valueAxis: {
      type: 'value',
      axisLine: { show: false },
      axisTick: { show: false },
      axisLabel: { color: ink3, fontSize: 12 },
      splitLine: { lineStyle: { color: border, type: 'dashed' } }
    },
    tooltip: {
      trigger: 'axis',
      backgroundColor: surface,
      borderColor: border,
      borderWidth: 1,
      textStyle: { color: text, fontSize: 12 },
      axisPointer: { type: 'line', lineStyle: { color: border } }
    },
    legend: {
      top: 0,
      right: 0,
      icon: 'circle',
      itemWidth: 8,
      itemHeight: 8,
      textStyle: { color: ink3, fontSize: 12 }
    }
  }
}

/**
 * 读取统一图表皮肤。
 *
 * 必须在组件 `setup` 内调用（内部 `useChartTheme()` 注册了 `onBeforeUnmount`）。
 */
export function useChartSkin(): { skin: ComputedRef<ChartSkin> } {
  const { chartTheme } = useChartTheme()
  const skin = computed<ChartSkin>(() => buildSkin(chartTheme.value))

  return { skin }
}
