/**
 * Issue #130：组件卸载后的 WebSocket 重连僵尸循环。
 *
 * 症状：disconnectWebSocket() 清掉定时器并 close()，但 close 事件随后触发
 * onclose，又无条件排了一个 3s 后的 connectWebSocket()。组件已销毁仍会新建
 * socket，每次进页面多一条永久重连链。
 *
 * 本用例：挂载后卸载，快进定时器，断言 WebSocket 只被构造一次。
 */
import { describe, expect, it, vi, beforeEach, afterEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'

vi.mock('element-plus', () => ({
  ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn(), info: vi.fn() }
}))

vi.mock('@element-plus/icons-vue', () => ({
  Download: { template: '<i />' },
  Box: { template: '<i />' },
  InfoFilled: { template: '<i />' }
}))

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: vi.fn() })
}))

vi.mock('@/utils/request', () => ({
  default: {
    get: vi.fn().mockImplementation((url: string) => {
      if (String(url).endsWith('/status')) {
        return Promise.resolve({
          data: {
            enabled: true,
            paused: false,
            sampleRate: 0.1,
            historySize: 1000,
            totalSampledCount: 0,
            pausedServices: []
          }
        })
      }
      return Promise.resolve({ data: {} })
    }),
    post: vi.fn().mockResolvedValue({ data: {} }),
    put: vi.fn().mockResolvedValue({ data: {} }),
    delete: vi.fn().mockResolvedValue({ data: {} })
  }
}))

import Monitoring from '@/views/load-balancer/Monitoring.vue'

const i18n = createI18n({
  legacy: false,
  locale: 'zh-CN',
  missingWarn: false,
  fallbackWarn: false,
  messages: { 'zh-CN': {} }
})

const elStubs = [
  'el-button', 'el-card', 'el-col', 'el-dropdown', 'el-dropdown-item', 'el-dropdown-menu',
  'el-icon', 'el-input-number', 'el-option', 'el-progress', 'el-row', 'el-select',
  'el-slider', 'el-table', 'el-table-column', 'el-tag', 'el-tooltip'
]
const stubs = Object.fromEntries([
  ...elStubs.map(c => [c, true]),
  ['PageSkeleton', { template: '<div><slot name="actions" /><slot /></div>' }]
])

/** 模拟浏览器 WebSocket：close() 后异步派发 close 事件 */
class MockWebSocket {
  static instances: MockWebSocket[] = []
  onopen: (() => void) | null = null
  onmessage: ((e: { data: string }) => void) | null = null
  onclose: (() => void) | null = null
  onerror: ((e: unknown) => void) | null = null
  readonly url: string

  constructor(url: string) {
    this.url = url
    MockWebSocket.instances.push(this)
  }

  close() {
    setTimeout(() => {
      this.onclose?.()
    }, 0)
  }
}

describe('load-balancer Monitoring WebSocket 生命周期（issue #130）', () => {
  beforeEach(() => {
    MockWebSocket.instances = []
    vi.useFakeTimers()
    vi.stubGlobal('WebSocket', MockWebSocket as unknown as typeof WebSocket)
  })

  afterEach(() => {
    vi.useRealTimers()
    vi.unstubAllGlobals()
  })

  it('卸载后不应再重建 WebSocket（无僵尸重连）', () => {
    const wrapper = mount(Monitoring, { global: { plugins: [i18n], stubs } })
    expect(MockWebSocket.instances.length).toBe(1)

    wrapper.unmount()

    // close 事件（0ms）+ 原 bug 会排的 3s 重连窗口
    vi.advanceTimersByTime(0)
    vi.advanceTimersByTime(3000)
    vi.advanceTimersByTime(3000)

    expect(MockWebSocket.instances.length).toBe(1)
  })
})
