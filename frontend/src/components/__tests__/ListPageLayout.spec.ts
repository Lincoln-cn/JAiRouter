import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import { defineComponent, h } from 'vue'
import ListPageLayout from '@/components/ListPageLayout.vue'

const ElButtonStub = defineComponent({
  name: 'ElButton',
  props: { type: { type: String, default: '' } },
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

const mountLayout = (
  props: Record<string, unknown> = {},
  slots: Record<string, unknown> = {}
) =>
  mount(ListPageLayout, {
    props,
    slots: { default: () => h('div', { class: 'table-slot' }, '表格'), ...slots },
    global: { components: globalComponents }
  })

describe('ListPageLayout', () => {
  it('默认渲染内容插槽', () => {
    const wrapper = mountLayout()

    expect(wrapper.find('.table-slot').text()).toBe('表格')
    expect(wrapper.find('.page-skeleton').exists()).toBe(false)
  })

  it('loading 时显示 PageSkeleton', () => {
    const wrapper = mountLayout({ loading: true })

    expect(wrapper.find('.page-skeleton').exists()).toBe(true)
    expect(wrapper.find('.table-slot').exists()).toBe(false)
  })

  it('loading 优先于 empty', () => {
    const wrapper = mountLayout({
      loading: true,
      empty: true,
      emptyTitle: '暂无实例'
    })

    expect(wrapper.find('.page-skeleton').exists()).toBe(true)
    expect(wrapper.find('.empty-state').exists()).toBe(false)
  })

  it('empty 时渲染 EmptyState 并透传文案', () => {
    const wrapper = mountLayout({
      empty: true,
      emptyTitle: '暂无实例',
      emptyDescription: '当前筛选条件下没有匹配项'
    })

    expect(wrapper.find('.empty-state').exists()).toBe(true)
    expect(wrapper.find('.empty-state__title').text()).toBe('暂无实例')
    expect(wrapper.find('.empty-state__description').text()).toBe('当前筛选条件下没有匹配项')
    expect(wrapper.find('.table-slot').exists()).toBe(false)
  })

  it('emptyActionText 渲染主行动按钮并 emit empty-action', async () => {
    const wrapper = mountLayout({
      empty: true,
      emptyTitle: '暂无实例',
      emptyActionText: '新建实例'
    })

    const button = wrapper.find('.empty-state__actions .el-button-stub')
    expect(button.text()).toBe('新建实例')

    await button.trigger('click')
    expect(wrapper.emitted('empty-action')).toHaveLength(1)
  })

  it('无 emptyActionText 时 empty 态不渲染按钮', () => {
    const wrapper = mountLayout({ empty: true, emptyTitle: '暂无实例' })
    expect(wrapper.find('.el-button-stub').exists()).toBe(false)
  })

  it('empty-actions 插槽优先于 emptyActionText', () => {
    const wrapper = mountLayout(
      { empty: true, emptyTitle: '暂无实例', emptyActionText: '新建实例' },
      { 'empty-actions': () => h('a', { class: 'custom-empty-action' }, '自定义') }
    )

    expect(wrapper.find('.custom-empty-action').text()).toBe('自定义')
    expect(wrapper.find('.el-button-stub').exists()).toBe(false)
  })

  it('按需渲染 header / actions / filters / toolbar / footer 插槽', () => {
    const wrapper = mountLayout(
      {},
      {
        header: () => h('h1', { class: 'header-slot' }, '实例'),
        actions: () => h('button', { class: 'actions-slot' }, '刷新'),
        filters: () => h('div', { class: 'filters-slot' }, '筛选'),
        toolbar: () => h('div', { class: 'toolbar-slot' }, '工具'),
        footer: () => h('div', { class: 'footer-slot' }, '分页')
      }
    )

    expect(wrapper.find('.list-page-layout__header .header-slot').exists()).toBe(true)
    expect(wrapper.find('.list-page-layout__actions .actions-slot').exists()).toBe(true)
    expect(wrapper.find('.list-page-layout__filters .filters-slot').exists()).toBe(true)
    expect(wrapper.find('.list-page-layout__toolbar .toolbar-slot').exists()).toBe(true)
    expect(wrapper.find('.list-page-layout__footer .footer-slot').exists()).toBe(true)
  })

  it('不传可选插槽时不渲染对应容器', () => {
    const wrapper = mountLayout()

    expect(wrapper.find('.list-page-layout__header').exists()).toBe(false)
    expect(wrapper.find('.list-page-layout__filters').exists()).toBe(false)
    expect(wrapper.find('.list-page-layout__toolbar').exists()).toBe(false)
    expect(wrapper.find('.list-page-layout__footer').exists()).toBe(false)
  })

  it('drawer 插槽原样透传', () => {
    const wrapper = mountLayout({}, { drawer: () => h('aside', { class: 'drawer-slot' }) })
    expect(wrapper.find('.drawer-slot').exists()).toBe(true)
  })
})
