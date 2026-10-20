import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import { defineComponent, h } from 'vue'
import DetailDrawer from '@/components/DetailDrawer.vue'

const ElDrawerStub = defineComponent({
  name: 'ElDrawer',
  props: {
    modelValue: { type: Boolean, default: false },
    title: { type: String, default: '' },
    size: { type: [Number, String], default: '' }
  },
  emits: ['update:modelValue'],
  setup(props, { slots, emit }) {
    return () =>
      h(
        'section',
        {
          class: 'el-drawer-stub',
          'data-open': String(props.modelValue),
          'data-size': String(props.size)
        },
        [
          h('header', { class: 'drawer-title' }, props.title),
          h(
            'button',
            { class: 'drawer-close', onClick: () => emit('update:modelValue', false) },
            'close'
          ),
          slots.default?.(),
          slots.footer ? h('footer', { class: 'drawer-footer' }, slots.footer()) : null
        ]
      )
  }
})

const ElSkeletonStub = defineComponent({
  name: 'ElSkeleton',
  setup() {
    return () => h('div', { class: 'el-skeleton-stub' })
  }
})

const globalComponents = { ElDrawer: ElDrawerStub, ElSkeleton: ElSkeletonStub }

const mountDrawer = (props: Record<string, unknown> = {}, slots: Record<string, unknown> = {}) =>
  mount(DetailDrawer, {
    props: { modelValue: true, title: '实例详情', ...props },
    slots: { default: () => h('p', { class: 'drawer-body' }, '内容'), ...slots },
    global: { components: globalComponents }
  })

describe('DetailDrawer', () => {
  it('把 modelValue / title / width 传给 el-drawer，width 默认 480', () => {
    const wrapper = mountDrawer()
    const drawer = wrapper.findComponent(ElDrawerStub)

    expect(drawer.props('modelValue')).toBe(true)
    expect(drawer.props('title')).toBe('实例详情')
    expect(drawer.props('size')).toBe(480)
    expect(wrapper.attributes('data-open')).toBe('true')
  })

  it('width 支持字符串（如百分比）', () => {
    const wrapper = mountDrawer({ width: '60%' })
    expect(wrapper.findComponent(ElDrawerStub).props('size')).toBe('60%')
  })

  it('关闭时 emit update:modelValue(false)', async () => {
    const wrapper = mountDrawer()

    await wrapper.find('.drawer-close').trigger('click')

    expect(wrapper.emitted('update:modelValue')).toEqual([[false]])
  })

  it('内部开启时 emit update:modelValue(true)', () => {
    const wrapper = mountDrawer({ modelValue: false })
    wrapper.findComponent(ElDrawerStub).vm.$emit('update:modelValue', true)

    expect(wrapper.emitted('update:modelValue')).toEqual([[true]])
  })

  it('默认插槽渲染内容', () => {
    const wrapper = mountDrawer()
    expect(wrapper.find('.drawer-body').text()).toBe('内容')
  })

  it('loading 时显示骨架并替换默认内容', () => {
    const wrapper = mountDrawer({ loading: true })

    expect(wrapper.find('.el-skeleton-stub').exists()).toBe(true)
    expect(wrapper.find('.drawer-body').exists()).toBe(false)
  })

  it('footer 插槽存在时才渲染底栏', () => {
    const withoutFooter = mountDrawer()
    expect(withoutFooter.find('.drawer-footer').exists()).toBe(false)

    const withFooter = mountDrawer(
      {},
      { footer: () => h('button', { class: 'footer-action' }, '保存') }
    )
    expect(withFooter.find('.drawer-footer .footer-action').text()).toBe('保存')
  })

  it('loading 时 footer 仍可用（便于加载期间取消/关闭）', () => {
    const wrapper = mountDrawer(
      { loading: true },
      { footer: () => h('button', { class: 'footer-action' }, '取消') }
    )

    expect(wrapper.find('.el-skeleton-stub').exists()).toBe(true)
    expect(wrapper.find('.drawer-footer .footer-action').exists()).toBe(true)
  })
})
