import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import { defineComponent, h, nextTick } from 'vue'
import FilterBar from '@/components/FilterBar.vue'

const ElButtonStub = defineComponent({
  name: 'ElButton',
  emits: ['click'],
  setup(_props, { slots, emit }) {
    return () =>
      h(
        'button',
        { class: 'el-button-stub', onClick: () => emit('click') },
        slots.default?.()
      )
  }
})

const ElIconStub = defineComponent({
  name: 'ElIcon',
  setup(_props, { slots }) {
    return () => h('span', { class: 'el-icon-stub' }, slots.default?.())
  }
})

const globalComponents = { ElButton: ElButtonStub, ElIcon: ElIconStub }

const mountFilter = (props: Record<string, unknown> = {}) =>
  mount(FilterBar, {
    props,
    slots: {
      default: () => [
        h('span', { class: 'filter-field' }, '状态'),
        h('span', { class: 'filter-field' }, '实例'),
        h('span', { class: 'filter-field' }, '关键字')
      ]
    },
    global: { components: globalComponents }
  })

const defineOffsetTop = (element: Element, offsetTop: number): void => {
  Object.defineProperty(element, 'offsetTop', { value: offsetTop, configurable: true })
}

describe('FilterBar', () => {
  it('渲染默认插槽内容', () => {
    const wrapper = mountFilter()

    expect(wrapper.findAll('.filter-bar__content > .filter-field')).toHaveLength(3)
    expect(wrapper.find('.filter-bar__content').text()).toContain('状态')
  })

  it('collapsible=false 时不渲染展开/收起按钮', () => {
    const wrapper = mountFilter({ collapsible: false })
    expect(wrapper.find('.filter-bar__toggle').exists()).toBe(false)
  })

  it('内容不超出行数时不渲染展开/收起按钮', async () => {
    const wrapper = mountFilter({ maxRows: 1 })
    await nextTick()
    expect(wrapper.find('.filter-bar__toggle').exists()).toBe(false)
  })

  it('超出 maxRows 时出现展开/收起，收起态给出行高', async () => {
    const wrapper = mountFilter({ collapsible: false, maxRows: 1 })
    const fields = wrapper.findAll('.filter-bar__content > .filter-field')
    defineOffsetTop(fields[0]!.element, 0)
    defineOffsetTop(fields[1]!.element, 0)
    defineOffsetTop(fields[2]!.element, 40)

    await wrapper.setProps({ collapsible: true })
    await nextTick()
    await nextTick()

    const toggle = wrapper.find('.filter-bar__toggle-button')
    expect(toggle.exists()).toBe(true)
    expect(toggle.attributes('aria-expanded')).toBe('false')

    const viewport = wrapper.find('.filter-bar__viewport')
    expect(viewport.classes()).toContain('is-collapsed')
    expect(viewport.attributes('style') ?? '').toContain('max-height: 40px')

    await toggle.trigger('click')
    await nextTick()

    expect(toggle.attributes('aria-expanded')).toBe('true')
    expect(wrapper.find('.filter-bar__viewport').classes()).not.toContain('is-collapsed')
  })

  it('defaultExpanded=true 时初始为展开态', async () => {
    const wrapper = mountFilter({ collapsible: false, defaultExpanded: true })
    const fields = wrapper.findAll('.filter-bar__content > .filter-field')
    defineOffsetTop(fields[0]!.element, 0)
    defineOffsetTop(fields[1]!.element, 40)

    await wrapper.setProps({ collapsible: true })
    await nextTick()
    await nextTick()

    const toggle = wrapper.find('.filter-bar__toggle-button')
    expect(wrapper.find('.filter-bar__viewport').classes()).not.toContain('is-collapsed')
    expect(toggle.attributes('aria-expanded')).toBe('true')
  })
})
