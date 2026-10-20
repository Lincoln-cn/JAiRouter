import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import { defineComponent, h, markRaw } from 'vue'
import EmptyState from '@/components/EmptyState.vue'

const ElIconStub = defineComponent({
  name: 'ElIcon',
  setup(_props, { slots }) {
    return () => h('span', { class: 'el-icon-stub' }, slots.default?.())
  }
})

const globalComponents = { ElIcon: ElIconStub }

describe('EmptyState', () => {
  it('渲染调用方传入的 title / description', () => {
    const wrapper = mount(EmptyState, {
      props: { title: '暂无实例', description: '调整筛选条件后重试' },
      global: { components: globalComponents }
    })

    expect(wrapper.find('.empty-state__title').text()).toBe('暂无实例')
    expect(wrapper.find('.empty-state__description').text()).toBe('调整筛选条件后重试')
  })

  it('未传文案时不渲染任何默认文案（组件内不写死字符串）', () => {
    const wrapper = mount(EmptyState, {
      global: { components: globalComponents }
    })

    expect(wrapper.find('.empty-state__title').exists()).toBe(false)
    expect(wrapper.find('.empty-state__description').exists()).toBe(false)
    expect(wrapper.text()).toBe('')
  })

  it('渲染 actions 插槽内容', () => {
    const wrapper = mount(EmptyState, {
      props: { title: '暂无数据' },
      slots: {
        actions: () => [
          h('button', { class: 'action-primary' }, '新建'),
          h('button', { class: 'action-secondary' }, '刷新')
        ]
      },
      global: { components: globalComponents }
    })

    expect(wrapper.find('.empty-state__actions .action-primary').text()).toBe('新建')
    expect(wrapper.find('.empty-state__actions .action-secondary').text()).toBe('刷新')
  })

  it('无 actions 插槽时不渲染操作区', () => {
    const wrapper = mount(EmptyState, {
      props: { title: '暂无数据' },
      global: { components: globalComponents }
    })

    expect(wrapper.find('.empty-state__actions').exists()).toBe(false)
  })

  it('icon 支持组件形式并包在 el-icon 内', () => {
    const CustomIcon = defineComponent({
      name: 'CustomIcon',
      setup() {
        return () => h('i', { class: 'custom-icon' })
      }
    })

    const wrapper = mount(EmptyState, {
      props: { title: '暂无数据', icon: markRaw(CustomIcon) },
      global: { components: globalComponents }
    })

    expect(wrapper.find('.empty-state__icon .el-icon-stub .custom-icon').exists()).toBe(true)
  })

  it('图标容器对辅助技术隐藏', () => {
    const CustomIcon = defineComponent({
      name: 'CustomIcon2',
      setup() {
        return () => h('i')
      }
    })

    const wrapper = mount(EmptyState, {
      props: { title: '暂无数据', icon: markRaw(CustomIcon) },
      global: { components: globalComponents }
    })

    expect(wrapper.find('.empty-state__icon').attributes('aria-hidden')).toBe('true')
  })
})
