import { describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { defineComponent, h } from 'vue'
import ConfirmAction from '@/components/ConfirmAction.vue'

const ElPopconfirmStub = defineComponent({
  name: 'ElPopconfirm',
  props: {
    title: { type: String, default: '' },
    confirmButtonText: { type: String, default: '' },
    cancelButtonText: { type: String, default: '' },
    confirmButtonType: { type: String, default: '' }
  },
  emits: ['confirm'],
  setup(_props, { slots, emit }) {
    return () =>
      h('div', { class: 'el-popconfirm-stub' }, [
        slots.reference?.(),
        h(
          'button',
          { class: 'stub-confirm', onClick: () => emit('confirm') },
          'confirm'
        )
      ])
  }
})

const globalComponents = { ElPopconfirm: ElPopconfirmStub }

const mountAction = (props: Record<string, unknown> = {}) =>
  mount(ConfirmAction, {
    props: { title: '确认删除？', ...props },
    slots: { default: () => h('button', { class: 'trigger-button' }, '删除') },
    global: { components: globalComponents }
  })

describe('ConfirmAction', () => {
  it('渲染默认插槽触发元素，并把文案传给 el-popconfirm', () => {
    const wrapper = mountAction({
      confirmText: '删除',
      cancelText: '取消',
      type: 'danger'
    })

    expect(wrapper.find('.trigger-button').text()).toBe('删除')

    const popconfirm = wrapper.findComponent(ElPopconfirmStub)
    expect(popconfirm.props('title')).toBe('确认删除？')
    expect(popconfirm.props('confirmButtonText')).toBe('删除')
    expect(popconfirm.props('cancelButtonText')).toBe('取消')
    expect(popconfirm.props('confirmButtonType')).toBe('danger')
  })

  it('在弹层里确认后 emit confirm', async () => {
    const wrapper = mountAction()

    await wrapper.find('.stub-confirm').trigger('click')

    expect(wrapper.emitted('confirm')).toHaveLength(1)
  })

  it('disabled 时不渲染二次确认层，也不会 emit', async () => {
    const wrapper = mountAction({ disabled: true })

    expect(wrapper.find('.el-popconfirm-stub').exists()).toBe(false)
    expect(wrapper.find('.confirm-action__trigger').attributes('aria-disabled')).toBe('true')

    await wrapper.find('.trigger-button').trigger('click')
    expect(wrapper.emitted('confirm')).toBeUndefined()
  })

  it('loading 时不可重复触发：确认层不渲染，点击/回车都不 emit', async () => {
    const wrapper = mountAction({ loading: true })
    const trigger = wrapper.find('.confirm-action__trigger')

    expect(wrapper.find('.el-popconfirm-stub').exists()).toBe(false)

    await trigger.trigger('click')
    expect(wrapper.emitted('confirm')).toBeUndefined()

    // 键盘不会把 Enter 转发成 click（转发逻辑被 interactive 守卫拦住）
    const clickSpy = vi.fn()
    trigger.element.addEventListener('click', clickSpy)
    await trigger.trigger('keydown', { key: 'Enter' })

    expect(clickSpy).not.toHaveBeenCalled()
    expect(wrapper.emitted('confirm')).toBeUndefined()
  })

  it('触发器自身可键盘操作：Enter / Space 转发为一次 click', async () => {
    const wrapper = mountAction()
    const trigger = wrapper.find('.confirm-action__trigger')
    const clickSpy = vi.fn()
    trigger.element.addEventListener('click', clickSpy)

    await trigger.trigger('keydown', { key: 'Enter' })
    expect(clickSpy).toHaveBeenCalledTimes(1)

    await trigger.trigger('keydown', { key: ' ' })
    expect(clickSpy).toHaveBeenCalledTimes(2)

    await trigger.trigger('keydown', { key: 'a' })
    expect(clickSpy).toHaveBeenCalledTimes(2)
  })

  it('默认 type 为 primary', () => {
    const wrapper = mountAction()
    expect(wrapper.findComponent(ElPopconfirmStub).props('confirmButtonType')).toBe('primary')
  })
})
