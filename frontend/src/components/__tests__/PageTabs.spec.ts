import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { defineComponent, h, nextTick } from 'vue'
import PageTabs from '@/components/PageTabs.vue'

const mocks = vi.hoisted(() => ({
  hasPermission: vi.fn(),
  push: vi.fn(),
  route: { path: '/overview' }
}))

vi.mock('@/composables/usePermission', () => ({
  usePermission: () => ({
    hasPermission: mocks.hasPermission,
    filterMenuByPermission: (groups: unknown) => groups
  })
}))

vi.mock('vue-router', () => ({
  useRoute: () => mocks.route,
  useRouter: () => ({ push: mocks.push })
}))

const ElTabsStub = defineComponent({
  name: 'ElTabs',
  props: {
    modelValue: { type: [String, Number], default: '' }
  },
  emits: ['tab-change'],
  setup(_props, { slots }) {
    return () => h('div', { class: 'el-tabs-stub' }, slots.default?.())
  }
})

const ElTabPaneStub = defineComponent({
  name: 'ElTabPane',
  props: {
    label: { type: String, default: '' },
    name: { type: [String, Number], default: '' }
  },
  setup(props) {
    return () =>
      h(
        'div',
        { class: 'el-tab-pane-stub', 'data-name': String(props.name) },
        props.label
      )
  }
})

const globalComponents = { ElTabs: ElTabsStub, ElTabPane: ElTabPaneStub }

const tabs = [
  { key: 'overview', label: '概览', to: '/overview' },
  { key: 'alerts', label: '告警', to: '/alerts', permission: 'monitoring:alert:read' },
  { key: 'logs', label: '日志', to: '/logs', permission: ['log:read', 'system:log:read'] }
]

const mountTabs = (props: Record<string, unknown> = {}) =>
  mount(PageTabs, {
    props: { tabs, ...props },
    global: { components: globalComponents }
  })

beforeEach(() => {
  mocks.hasPermission.mockReset()
  mocks.hasPermission.mockReturnValue(true)
  mocks.push.mockReset()
  mocks.route.path = '/overview'
})

describe('PageTabs', () => {
  it('过滤掉无权限的 tab，只渲染有权限的项', () => {
    mocks.hasPermission.mockImplementation((code?: string) => code === 'monitoring:alert:read')

    const wrapper = mountTabs()
    const panes = wrapper.findAll('.el-tab-pane-stub')

    expect(panes).toHaveLength(2)
    expect(panes.map(pane => pane.attributes('data-name'))).toEqual(['overview', 'alerts'])
    expect(wrapper.text()).not.toContain('日志')
  })

  it('permission 为数组时任一权限码命中即可见', () => {
    mocks.hasPermission.mockImplementation((code?: string) => code === 'system:log:read')

    const wrapper = mountTabs()
    const names = wrapper
      .findAll('.el-tab-pane-stub')
      .map(pane => pane.attributes('data-name'))

    expect(names).toEqual(['overview', 'logs'])
  })

  it('可见 tab ≤ 1 时整体隐藏', () => {
    mocks.hasPermission.mockReturnValue(false)

    const wrapper = mountTabs()
    expect(wrapper.find('.el-tabs-stub').exists()).toBe(false)
  })

  it('切换 tab 时 push 到对应 to', async () => {
    const wrapper = mountTabs()
    wrapper.findComponent(ElTabsStub).vm.$emit('tab-change', 'alerts')
    await nextTick()

    expect(mocks.push).toHaveBeenCalledTimes(1)
    expect(mocks.push).toHaveBeenCalledWith('/alerts')
  })

  it('同路径重复点击不 push', async () => {
    const wrapper = mountTabs()
    wrapper.findComponent(ElTabsStub).vm.$emit('tab-change', 'overview')
    await nextTick()

    expect(mocks.push).not.toHaveBeenCalled()
  })

  it('active 缺省时按 route.path 反查选中项', () => {
    mocks.route.path = '/alerts'

    const wrapper = mountTabs()
    expect(wrapper.findComponent(ElTabsStub).props('modelValue')).toBe('alerts')
  })

  it('显式 active 优先于 route.path，且接受 to 路径', () => {
    mocks.route.path = '/alerts'

    const byKey = mountTabs({ active: 'overview' })
    expect(byKey.findComponent(ElTabsStub).props('modelValue')).toBe('overview')

    const byPath = mountTabs({ active: '/logs' })
    expect(byPath.findComponent(ElTabsStub).props('modelValue')).toBe('logs')
  })

  it('未知 tab 名不触发导航', async () => {
    const wrapper = mountTabs()
    wrapper.findComponent(ElTabsStub).vm.$emit('tab-change', 'missing')
    await nextTick()

    expect(mocks.push).not.toHaveBeenCalled()
  })
})
