import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import { defineComponent, h } from 'vue'
import type { ComputedRef } from 'vue'
import { useChartSkin } from '@/composables/useChartSkin'
import type { ChartSkin } from '@/composables/useChartSkin'

/**
 * 在真实组件 setup 里取 skin：useChartSkin 内部的 useChartTheme 会注册
 * onBeforeUnmount，脱离组件实例调用会产生 Vue 警告。
 */
function readSkin(): ComputedRef<ChartSkin> {
  let skin: ComputedRef<ChartSkin> | undefined

  mount(
    defineComponent({
      setup() {
        skin = useChartSkin().skin
        return () => h('div')
      }
    })
  )

  if (!skin) {
    throw new Error('useChartSkin 未返回 skin')
  }
  return skin
}

describe('useChartSkin', () => {
  it('grid 使用统一内边距并给标题留出空间', () => {
    expect(readSkin().value.grid).toEqual({
      left: 8,
      right: 16,
      top: 32,
      bottom: 8,
      containLabel: true
    })
  })

  it('legend 固定在右上角并使用中性小圆点', () => {
    const skin = readSkin().value

    expect(skin.legend).toEqual({
      top: 0,
      right: 0,
      icon: 'circle',
      itemWidth: 8,
      itemHeight: 8,
      textStyle: { color: skin.textMuted, fontSize: 12 }
    })
  })

  it('tooltip 跟随令牌取底色、边框与文字色', () => {
    const skin = readSkin().value

    expect(skin.tooltip).toEqual({
      trigger: 'axis',
      backgroundColor: skin.surface,
      borderColor: skin.border,
      borderWidth: 1,
      textStyle: { color: skin.text, fontSize: 12 },
      axisPointer: { type: 'line', lineStyle: { color: skin.border } }
    })
  })

  it('categoryAxis / valueAxis 只用中性色与虚线分隔', () => {
    const skin = readSkin().value

    expect(skin.categoryAxis).toEqual({
      type: 'category',
      axisLine: { lineStyle: { color: skin.border } },
      axisTick: { show: false },
      axisLabel: { color: skin.textMuted, fontSize: 12 }
    })
    expect(skin.valueAxis).toEqual({
      type: 'value',
      axisLine: { show: false },
      axisTick: { show: false },
      axisLabel: { color: skin.textMuted, fontSize: 12 },
      splitLine: { lineStyle: { color: skin.border, type: 'dashed' } }
    })
  })

  it('palette 长度为 6 且无重复', () => {
    const palette = readSkin().value.palette

    expect(palette).toHaveLength(6)
    expect(new Set(palette).size).toBe(6)
  })

  it('emphasis 等于 danger 令牌值（无令牌环境回落到 #f56c6c）', () => {
    expect(readSkin().value.emphasis).toBe('#f56c6c')
  })
})
