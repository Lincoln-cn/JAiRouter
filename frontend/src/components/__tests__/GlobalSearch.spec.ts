/**
 * GlobalSearch（⌘K 命令面板）单测。
 *
 * 覆盖：打开时聚焦 / 关闭还焦、250ms 防抖、分组截断与「共 N 条」、
 * TraceID 组的 query 跳转、↑↓ + Enter 导航、Esc 关闭。
 * 请求层整体 mock，缓存与 abort 细节由 composable 自己保证（另有类型检查兜底）。
 */
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'

const mocks = vi.hoisted(() => ({
  get: vi.fn(),
  push: vi.fn()
}))

vi.mock('@/utils/request', () => ({
  default: { get: mocks.get }
}))

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: mocks.push })
}))

// 菜单权限与落地路径与本组件无关，统一放行并直接用 item.path
vi.mock('@/composables/usePermission', () => ({
  usePermission: () => ({
    filterMenuByPermission: (groups: unknown) => groups,
    resolveLandingPath: (item: { path: string }) => item.path
  })
}))

vi.mock('@element-plus/icons-vue', () => ({
  Search: { template: '<i />' }
}))

import GlobalSearch from '@/components/GlobalSearch.vue'

const i18n = createI18n({
  legacy: false,
  locale: 'zh-CN',
  missingWarn: false,
  fallbackWarn: false,
  messages: {
    'zh-CN': {
      globalSearch: {
        dialogLabel: '全局搜索',
        inputLabel: '搜索菜单、服务类型、实例、API Key、TraceID 或异常',
        placeholder: '搜索…',
        resultsLabel: '搜索结果',
        groupMenu: '菜单',
        groupServices: '服务类型',
        groupInstances: '实例',
        groupApiKeys: 'API Key',
        groupTrace: '链路追踪',
        groupExceptions: '异常',
        total: '共 {count} 条',
        empty: '没有匹配结果',
        hint: '↑↓ 选择 · Enter 打开 · Esc 关闭',
        traceSearchItem: '在链路搜索中查找 {id}',
        traceSearchHint: '按 TraceID 搜索',
        goServices: '服务类型管理'
      }
    }
  }
})

interface GlobalSearchVm {
  open: () => Promise<void>
  close: () => void
}

const ok = <T>(data: T) => Promise.resolve({ data: { success: true, data } })

const apiKey = (index: number) => ({
  keyId: `key-${index}`,
  description: `prod-key-${index}`,
  permissions: [],
  enabled: true,
  expired: false,
  createdAt: '2026-01-01T00:00:00',
  totalRequests: 0
})

const mountSearch = () =>
  mount(GlobalSearch, {
    attachTo: document.body,
    global: {
      plugins: [i18n],
      stubs: { 'el-icon': true }
    }
  })

type SearchWrapper = ReturnType<typeof mountSearch>

const openPanel = async (wrapper: SearchWrapper): Promise<void> => {
  await (wrapper.vm as unknown as GlobalSearchVm).open()
  await flushPromises()
}

describe('GlobalSearch', () => {
  let wrapper: SearchWrapper | undefined

  beforeEach(() => {
    vi.useFakeTimers()
    mocks.get.mockReset()
    mocks.push.mockReset()
    mocks.get.mockImplementation((url: string) => {
      if (url === '/config/type/services') {
        return ok(['chat', 'embedding'])
      }
      if (url.startsWith('/config/instance/')) {
        return ok([{ instanceId: 'i1', name: 'chat-a', baseUrl: 'http://a' }])
      }
      if (url === '/auth/api-keys') {
        return ok({ items: Array.from({ length: 7 }, (_, index) => apiKey(index + 1)) })
      }
      if (url === '/exceptions') {
        return ok({
          content: [
            {
              eventId: 'e1',
              exceptionType: 'TimeoutException',
              exceptionMessage: 'read timeout'
            }
          ]
        })
      }
      return Promise.reject(new Error(`unexpected url: ${url}`))
    })
  })

  afterEach(() => {
    wrapper?.unmount()
    wrapper = undefined
    vi.useRealTimers()
    document.body.innerHTML = ''
  })

  it('打开后聚焦输入框，关闭后把焦点还给触发元素', async () => {
    const trigger = document.createElement('button')
    document.body.appendChild(trigger)
    trigger.focus()

    wrapper = mountSearch()
    await openPanel(wrapper)

    expect(wrapper.find('[role="dialog"]').attributes('aria-modal')).toBe('true')
    const input = wrapper.find('input')
    expect(document.activeElement).toBe(input.element)

    ;(wrapper.vm as unknown as GlobalSearchVm).close()
    await flushPromises()

    expect(wrapper.find('[role="dialog"]').exists()).toBe(false)
    expect(document.activeElement).toBe(trigger)
  })

  it('打开时加载静态清单，空查询给菜单与服务类型两组默认建议', async () => {
    wrapper = mountSearch()
    await openPanel(wrapper)

    expect(mocks.get).toHaveBeenCalledWith('/config/type/services', expect.anything())
    expect(mocks.get).toHaveBeenCalledWith('/auth/api-keys', expect.anything())
    expect(mocks.get).toHaveBeenCalledWith('/config/instance/chat', expect.anything())
    // 面板打开即有内容可点，不要求用户先盲打关键词
    expect(wrapper.findAll('.global-search__group').length).toBeGreaterThan(0)
    expect(wrapper.findAll('.global-search__item').length).toBeGreaterThan(0)
  })

  it('输入 250ms 防抖后才查询异常组', async () => {
    wrapper = mountSearch()
    await openPanel(wrapper)

    await wrapper.find('input').setValue('timeout')
    vi.advanceTimersByTime(249)
    await flushPromises()
    expect(mocks.get).not.toHaveBeenCalledWith('/exceptions', expect.anything())

    vi.advanceTimersByTime(1)
    await flushPromises()
    expect(mocks.get).toHaveBeenCalledWith(
      '/exceptions',
      expect.objectContaining({ params: expect.objectContaining({ exceptionType: 'timeout' }) })
    )
    expect(wrapper.text()).toContain('TimeoutException')
  })

  it('每组最多 5 条并显示「共 N 条」', async () => {
    wrapper = mountSearch()
    await openPanel(wrapper)

    await wrapper.find('input').setValue('prod-key')
    vi.advanceTimersByTime(250)
    await flushPromises()

    const items = wrapper.findAll('.global-search__item')
    expect(items).toHaveLength(5)
    expect(wrapper.text()).toContain('共 7 条')
  })

  it('32 位 hex 命中 TraceID 组，点击按 query 跳转链路搜索', async () => {
    wrapper = mountSearch()
    await openPanel(wrapper)

    const traceId = 'abcdef0123456789abcdef0123456789'
    await wrapper.find('input').setValue(traceId)
    vi.advanceTimersByTime(250)
    await flushPromises()

    expect(wrapper.text()).toContain('链路追踪')
    const traceItem = wrapper
      .findAll('.global-search__item')
      .find(item => item.text().includes(traceId))
    expect(traceItem).toBeTruthy()

    await traceItem!.trigger('click')
    expect(mocks.push).toHaveBeenCalledWith({
      path: '/tracing/search',
      query: { traceId }
    })
  })

  it('↑↓ 切换高亮，Enter 打开当前项', async () => {
    wrapper = mountSearch()
    await openPanel(wrapper)

    // 该 hex 同时命中 API Key（描述相同）与 TraceID，后者排在后面
    const traceId = 'abcdef0123456789'
    mocks.get.mockImplementation((url: string) => {
      if (url === '/config/type/services') {
        return ok([])
      }
      if (url === '/auth/api-keys') {
        return ok({ items: [{ ...apiKey(1), description: traceId }] })
      }
      if (url === '/exceptions') {
        return ok({ content: [] })
      }
      return Promise.reject(new Error(`unexpected url: ${url}`))
    })

    await wrapper.find('input').setValue(traceId)
    vi.advanceTimersByTime(250)
    await flushPromises()

    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowDown', bubbles: true }))
    await flushPromises()
    expect(wrapper.find('.global-search__item.is-active').text()).toContain('在链路搜索中查找')

    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', bubbles: true }))
    await flushPromises()
    expect(mocks.push).toHaveBeenCalledWith({
      path: '/tracing/search',
      query: { traceId }
    })
  })

  it('Esc 关闭面板', async () => {
    wrapper = mountSearch()
    await openPanel(wrapper)

    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }))
    await flushPromises()

    expect(wrapper.find('[role="dialog"]').exists()).toBe(false)
  })
})
