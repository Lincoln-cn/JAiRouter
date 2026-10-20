import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { nextTick } from 'vue'
import TimeText from '@/components/TimeText.vue'

const BASE = new Date('2026-10-20T12:00:00+08:00')
const BASE_MS = BASE.getTime()
const HOUR = 3_600_000
const DAY = 86_400_000

const rtf = new Intl.RelativeTimeFormat('zh-CN', { numeric: 'always' })
const absolute = (date: Date, withSeconds = false): string =>
  new Intl.DateTimeFormat('zh-CN', {
    dateStyle: 'medium',
    timeStyle: withSeconds ? 'medium' : 'short'
  }).format(date)
const full = (date: Date): string =>
  new Intl.DateTimeFormat('zh-CN', { dateStyle: 'medium', timeStyle: 'medium' }).format(date)

const mountTime = (
  value: string | number | Date | null | undefined,
  props: Record<string, unknown> = {}
) => mount(TimeText, { props: { value, ...props } })

beforeEach(() => {
  vi.useFakeTimers()
  vi.setSystemTime(BASE)
})

afterEach(() => {
  vi.useRealTimers()
})

describe('TimeText', () => {
  it('null / undefined / 空串渲染为空字符串，不产生 Invalid Date', () => {
    for (const value of [null, undefined, '']) {
      const wrapper = mountTime(value)
      expect(wrapper.text()).toBe('')
      expect(wrapper.attributes('title')).toBe('')
      wrapper.unmount()
    }
  })

  it('无法解析的时间同样渲染为空（防御性分支）', () => {
    const wrapper = mountTime('not-a-date')
    expect(wrapper.text()).toBe('')
    expect(wrapper.attributes('title')).toBe('')
  })

  it('30 秒前：minute 精度显示「刚刚」，second 精度显示「30秒前」', () => {
    const value = BASE_MS - 30_000

    expect(mountTime(value).text()).toBe('刚刚')
    expect(mountTime(value, { precision: 'second' }).text()).toBe(
      rtf.format(-30, 'second')
    )
  })

  it('2 小时前显示「2小时前」，title 为完整时间', () => {
    const value = new Date(BASE_MS - 2 * HOUR)
    const wrapper = mountTime(value)

    expect(wrapper.text()).toBe('2小时前')
    expect(wrapper.text()).toBe(rtf.format(-2, 'hour'))
    expect(wrapper.attributes('title')).toBe(full(value))
  })

  it('2 天前显示「2天前」', () => {
    const wrapper = mountTime(new Date(BASE_MS - 2 * DAY))
    expect(wrapper.text()).toBe('2天前')
  })

  it('超过 30 天回落绝对日期', () => {
    const value = new Date(BASE_MS - 40 * DAY)
    const wrapper = mountTime(value)

    expect(wrapper.text()).toBe(absolute(value, true))
    expect(wrapper.attributes('title')).toBe(full(value))
  })

  it('absolute 模式按 precision 输出固定格式', () => {
    const value = new Date('2026-10-20T09:30:15+08:00')

    const minute = mountTime(value, { mode: 'absolute' })
    expect(minute.text()).toBe(absolute(value, false))
    expect(minute.attributes('title')).toBe(full(value))

    const second = mountTime(value, { mode: 'absolute', precision: 'second' })
    expect(second.text()).toBe(absolute(value, true))
  })

  it('挂载后每 30s 刷新相对时间', async () => {
    const value = BASE_MS - 59_000
    const wrapper = mountTime(value, { precision: 'second' })

    expect(wrapper.text()).toBe(rtf.format(-59, 'second'))

    vi.advanceTimersByTime(30_000)
    await nextTick()

    expect(wrapper.text()).toBe('1分钟前')
  })

  it('卸载后清理定时器', () => {
    const wrapper = mountTime(BASE_MS - 30_000)

    expect(vi.getTimerCount()).toBe(1)
    wrapper.unmount()
    expect(vi.getTimerCount()).toBe(0)
  })
})
