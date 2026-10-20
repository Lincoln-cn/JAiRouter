import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import MetricStrip from '@/components/MetricStrip.vue'
import type { MetricStripItem } from '@/components/MetricStrip.vue'

const baseItems: MetricStripItem[] = [
  { key: 'requests', label: '请求数', value: 1024 },
  { key: 'failures', label: '失败数', value: 12, tone: 'danger' },
  { key: 'latency', label: '平均延迟', value: '128 ms', hint: '近 24 小时' }
]

const mountStrip = (props: Record<string, unknown> = {}) =>
  mount(MetricStrip, { props: { items: baseItems, ...props } })

describe('MetricStrip', () => {
  it('按传入顺序渲染每个指标的标签与值', () => {
    const wrapper = mountStrip()
    const metrics = wrapper.findAll('.metric')

    expect(metrics).toHaveLength(3)
    expect(metrics.map(metric => metric.find('.metric__value').text())).toEqual([
      '1024',
      '12',
      '128 ms'
    ])
    expect(metrics.map(metric => metric.find('.metric__label').text())).toEqual([
      '请求数',
      '失败数',
      '平均延迟'
    ])
  })

  it('tone 为 danger 时只给对应的值加语义类名', () => {
    const wrapper = mountStrip()
    const values = wrapper.findAll('.metric__value')

    expect(values[1].classes()).toContain('metric__value--danger')
    expect(wrapper.findAll('.metric__value--danger')).toHaveLength(1)
  })

  it('tone 缺省或为 default 时不加任何语义类名', () => {
    const wrapper = mount(MetricStrip, {
      props: {
        items: [
          { key: 'a', label: '甲', value: 1 },
          { key: 'b', label: '乙', value: 2, tone: 'default' }
        ] satisfies MetricStripItem[]
      }
    })

    for (const value of wrapper.findAll('.metric__value')) {
      expect(value.classes().some(name => name.startsWith('metric__value--'))).toBe(false)
    }
  })

  it('success / warning 同样落到语义类名上', () => {
    const wrapper = mount(MetricStrip, {
      props: {
        items: [
          { key: 'ok', label: '成功', value: 9, tone: 'success' },
          { key: 'warn', label: '告警', value: 3, tone: 'warning' }
        ] satisfies MetricStripItem[]
      }
    })

    expect(wrapper.find('.metric__value--success').text()).toBe('9')
    expect(wrapper.find('.metric__value--warning').text()).toBe('3')
  })

  it('hint 有值才渲染', () => {
    const wrapper = mountStrip()

    expect(wrapper.findAll('.metric__hint')).toHaveLength(1)
    expect(wrapper.find('.metric__hint').text()).toBe('近 24 小时')
  })

  it('loading 时按 columns 渲染骨架且不渲染真实数字', () => {
    const wrapper = mountStrip({ loading: true, columns: 3 })

    expect(wrapper.findAll('.metric--skeleton')).toHaveLength(3)
    expect(wrapper.findAll('.metric__value')).toHaveLength(0)
    expect(wrapper.text()).toBe('')
  })

  it('aria-busy 跟随 loading 状态', () => {
    expect(wrapperBusy(false)).toBe('false')
    expect(wrapperBusy(true)).toBe('true')
  })

  it('columns 写入网格列数的 CSS 自定义属性', () => {
    const wrapper = mountStrip({ columns: 2 })
    const element = wrapper.element as HTMLElement

    expect(element.style.getPropertyValue('--metric-columns')).toBe('2')
  })
})

function wrapperBusy(loading: boolean): string | undefined {
  return mountStrip({ loading }).attributes('aria-busy')
}
