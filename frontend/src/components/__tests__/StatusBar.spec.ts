/**
 * StatusBar 单测。
 *
 * 覆盖：OPEN 计数与配色、24h 异常数、接口失败整条隐藏、
 * 60s 轮询、页面隐藏暂停 / 恢复立即刷新、卸载 abort、窄屏不显示文字。
 */
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'

const mocks = vi.hoisted(() => ({
  get: vi.fn(),
  push: vi.fn(),
  narrow: false
}))

vi.mock('@/utils/request', () => ({
  default: { get: mocks.get }
}))

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: mocks.push })
}))

vi.mock('@/composables/useViewport', () => ({
  useViewport: () => ({ isNarrow: mocks.narrow, isCompact: false })
}))

vi.mock('@element-plus/icons-vue', () => ({
  SwitchButton: { template: '<i />' },
  Warning: { template: '<i />' }
}))

import StatusBar from '@/components/StatusBar.vue'

const i18n = createI18n({
  legacy: false,
  locale: 'zh-CN',
  messages: {
    'zh-CN': {
      statusBar: {
        label: '系统状态',
        openBreakers: '开放熔断',
        openBreakersAria: '开放熔断 {count} 个',
        exceptions24h: '24h 异常',
        exceptions24hAria: '24 小时异常 {count} 条'
      }
    }
  }
})

const ok = <T>(data: T) => Promise.resolve({ data })

const mountBar = () =>
  mount(StatusBar, {
    global: { plugins: [i18n], stubs: { 'el-icon': true } }
  })

const instances = (states: string[]) => ok(states.map(state => ({ state })))
const statistics = (totalEvents: number) => ok({ success: true, data: { totalEvents } })

describe('StatusBar', () => {
  let wrapper: ReturnType<typeof mountBar> | undefined
  let hidden = false

  beforeEach(() => {
    vi.useFakeTimers()
    mocks.get.mockReset()
    mocks.push.mockReset()
    mocks.narrow = false
    hidden = false
    // StatusBar 只在存在登录 token 时才发请求（避免 401 触发登录跳转）
    localStorage.setItem('admin_token', 'test-token')
    Object.defineProperty(document, 'hidden', {
      configurable: true,
      get: () => hidden
    })
  })

  afterEach(() => {
    wrapper?.unmount()
    wrapper = undefined
    vi.useRealTimers()
    localStorage.removeItem('admin_token')
    document.body.innerHTML = ''
  })

  const ready = async (): Promise<void> => {
    await flushPromises()
  }

  it('统计 OPEN 条数并把两个胶囊都显示出来', async () => {
    mocks.get.mockImplementation((url: string) => {
      if (url === '/v1/circuit-breaker-monitor/instances') {
        return instances(['OPEN', 'CLOSED', 'open', 'HALF_OPEN'])
      }
      return statistics(12)
    })

    wrapper = mountBar()
    await ready()

    const breaker = wrapper.find('.status-bar__pill--breakers')
    const exception = wrapper.find('.status-bar__pill--exceptions')
    expect(breaker.text()).toContain('2')
    expect(breaker.classes()).toContain('is-danger')
    expect(exception.text()).toContain('12')
    expect(exception.classes()).toContain('is-warning')
    // 统计窗口是最近 24 小时，且时间不带时区后缀
    expect(mocks.get).toHaveBeenCalledWith(
      '/exceptions/statistics',
      expect.objectContaining({
        params: {
          startTime: expect.stringMatching(/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}$/),
          endTime: expect.stringMatching(/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}$/)
        }
      })
    )
  })

  it('计数为 0 时不加告警配色', async () => {
    mocks.get.mockImplementation((url: string) =>
      url === '/v1/circuit-breaker-monitor/instances' ? instances(['CLOSED']) : statistics(0)
    )

    wrapper = mountBar()
    await ready()

    const breaker = wrapper.find('.status-bar__pill--breakers')
    expect(breaker.classes()).not.toContain('is-danger')
    expect(wrapper.find('.status-bar__pill--exceptions').classes()).not.toContain('is-warning')
  })

  it('点击胶囊跳转到对应页面', async () => {
    mocks.get.mockImplementation((url: string) =>
      url === '/v1/circuit-breaker-monitor/instances' ? instances(['OPEN']) : statistics(3)
    )

    wrapper = mountBar()
    await ready()

    await wrapper.find('.status-bar__pill--breakers').trigger('click')
    await wrapper.find('.status-bar__pill--exceptions').trigger('click')
    expect(mocks.push).toHaveBeenNthCalledWith(1, '/circuit-breakers/monitoring')
    expect(mocks.push).toHaveBeenNthCalledWith(2, '/exceptions/list')
  })

  it('单个接口失败只隐藏对应胶囊', async () => {
    mocks.get.mockImplementation((url: string) =>
      url === '/v1/circuit-breaker-monitor/instances'
        ? Promise.reject(new Error('breaker down'))
        : statistics(5)
    )

    wrapper = mountBar()
    await ready()

    expect(wrapper.find('.status-bar__pill--breakers').exists()).toBe(false)
    expect(wrapper.find('.status-bar__pill--exceptions').exists()).toBe(true)
  })

  it('两个接口都失败时整个容器不渲染', async () => {
    mocks.get.mockRejectedValue(new Error('offline'))

    wrapper = mountBar()
    await ready()

    expect(wrapper.find('.status-bar').exists()).toBe(false)
  })

  it('每 60s 轮询一次', async () => {
    mocks.get.mockImplementation((url: string) =>
      url === '/v1/circuit-breaker-monitor/instances' ? instances(['CLOSED']) : statistics(1)
    )

    wrapper = mountBar()
    await ready()
    const calls = mocks.get.mock.calls.length

    vi.advanceTimersByTime(60_000)
    await ready()
    expect(mocks.get.mock.calls.length).toBe(calls + 2)
  })

  it('页面隐藏时暂停轮询与请求，恢复可见立即刷新', async () => {
    mocks.get.mockImplementation((url: string) =>
      url === '/v1/circuit-breaker-monitor/instances' ? instances(['CLOSED']) : statistics(1)
    )

    wrapper = mountBar()
    await ready()
    const calls = mocks.get.mock.calls.length

    hidden = true
    document.dispatchEvent(new Event('visibilitychange'))
    vi.advanceTimersByTime(120_000)
    await ready()
    expect(mocks.get.mock.calls.length).toBe(calls)

    hidden = false
    document.dispatchEvent(new Event('visibilitychange'))
    await ready()
    expect(mocks.get.mock.calls.length).toBe(calls + 2)
  })

  it('卸载时中止在途请求并停止轮询', async () => {
    mocks.get.mockImplementation((url: string) =>
      url === '/v1/circuit-breaker-monitor/instances' ? instances(['CLOSED']) : statistics(1)
    )

    wrapper = mountBar()
    await ready()

    const signal = mocks.get.mock.calls[0][1].signal as AbortSignal
    expect(signal.aborted).toBe(false)

    wrapper.unmount()
    wrapper = undefined
    expect(signal.aborted).toBe(true)

    const calls = mocks.get.mock.calls.length
    vi.advanceTimersByTime(120_000)
    await ready()
    expect(mocks.get.mock.calls.length).toBe(calls)
  })

  it('窄屏只显示图标与数字，不显示文字标签', async () => {
    mocks.narrow = true
    mocks.get.mockImplementation((url: string) =>
      url === '/v1/circuit-breaker-monitor/instances' ? instances(['OPEN']) : statistics(2)
    )

    wrapper = mountBar()
    await ready()

    expect(wrapper.find('.status-bar__label').exists()).toBe(false)
    expect(wrapper.find('.status-bar__pill--breakers').text()).toContain('1')
  })
})
