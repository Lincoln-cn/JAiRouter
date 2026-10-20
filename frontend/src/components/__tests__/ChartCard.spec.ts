import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import { defineComponent, h } from 'vue'
import ChartCard from '@/components/ChartCard.vue'

/** 默认槽里的「图表容器」：模拟页面用 ref 挂 echarts 实例的节点。 */
const chartContent = () => h('div', { class: 'chart-content' }, '图表容器')

/** EmptyState 的图标容器依赖 Element Plus 全局注册，测试里用桩替代。 */
const ElIconStub = defineComponent({
  name: 'ElIcon',
  setup(_props, { slots }) {
    return () => h('span', { class: 'el-icon-stub' }, slots.default?.())
  }
})

const mountCard = (props: Record<string, unknown> = {}, slots: Record<string, () => unknown> = {}) =>
  mount(ChartCard, {
    props: { title: '请求趋势', ...props },
    slots: { default: chartContent, ...slots },
    global: { components: { ElIcon: ElIconStub } }
  })

describe('ChartCard', () => {
  it('渲染标题', () => {
    const wrapper = mountCard()

    expect(wrapper.element.tagName).toBe('SECTION')
    expect(wrapper.find('.chart-card__title').text()).toBe('请求趋势')
  })

  it('无 actions 插槽时不渲染工具区', () => {
    expect(mountCard().find('.chart-card__actions').exists()).toBe(false)
  })

  it('有 actions 插槽时渲染工具区', () => {
    const wrapper = mountCard({}, { actions: () => h('button', { class: 'tool-refresh' }, '刷新') })

    expect(wrapper.find('.chart-card__actions .tool-refresh').text()).toBe('刷新')
  })

  it('empty 时渲染空态覆盖层，默认槽内容仍然存在', () => {
    const wrapper = mountCard({ empty: true })

    expect(wrapper.find('.chart-card__empty').exists()).toBe(true)
    expect(wrapper.find('.chart-card__empty .empty-state').exists()).toBe(true)
    // 图表容器不能被卸载，否则页面里的 echarts 实例会随 ref 丢失
    expect(wrapper.find('.chart-content').exists()).toBe(true)
  })

  it('emptyTitle / emptyDescription 透传给 EmptyState', () => {
    const wrapper = mountCard({
      empty: true,
      emptyTitle: '暂无数据',
      emptyDescription: '调整筛选条件或时间范围后重试'
    })

    expect(wrapper.find('.empty-state__title').text()).toBe('暂无数据')
    expect(wrapper.find('.empty-state__description').text()).toBe('调整筛选条件或时间范围后重试')
  })

  it('empty-action 槽转发到 EmptyState 的 actions 区', () => {
    const wrapper = mountCard(
      { empty: true },
      { 'empty-action': () => h('button', { class: 'empty-cta' }, '清除筛选') }
    )

    expect(wrapper.find('.empty-state__actions .empty-cta').text()).toBe('清除筛选')
  })

  it('loading 时渲染骨架覆盖层，默认槽内容仍然存在', () => {
    const wrapper = mountCard({ loading: true })

    expect(wrapper.find('.chart-card__skeleton').exists()).toBe(true)
    expect(wrapper.find('.chart-card__skeleton').attributes('aria-busy')).toBe('true')
    expect(wrapper.find('.chart-content').exists()).toBe(true)
  })

  it('height 应用到 chart-card__body 的内联样式', () => {
    const wrapper = mountCard({ height: 240 })

    expect(wrapper.find('.chart-card__body').attributes('style')).toContain('height: 240px')
  })

  it('empty 时 body 仍然保留 height（覆盖层不改变布局高度）', () => {
    const wrapper = mountCard({ empty: true, height: 200 })

    expect(wrapper.find('.chart-card__body').attributes('style')).toContain('height: 200px')
  })

  it('字符串高度原样应用', () => {
    const wrapper = mountCard({ height: '50vh' })

    expect(wrapper.find('.chart-card__body').attributes('style')).toContain('height: 50vh')
  })
})
