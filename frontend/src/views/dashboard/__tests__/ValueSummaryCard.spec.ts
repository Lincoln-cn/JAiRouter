import { describe, expect, it, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'

vi.mock('vue-i18n', () => ({
  useI18n: () => ({ t: (key: string, arg?: unknown) => (arg ? `${key}:${JSON.stringify(arg)}` : key) }),
}))

const getValueSummary = vi.fn()
vi.mock('@/api/dashboard', () => ({
  getValueSummary: () => getValueSummary(),
}))

import ValueSummaryCard from '../ValueSummaryCard.vue'

function mountCard() {
  return mount(ValueSummaryCard, {
    global: {
      stubs: {
        'el-card': { template: '<div><slot /></div>' },
      },
    },
  })
}

async function flush() {
  await Promise.resolve()
  await Promise.resolve()
  await new Promise((r) => setTimeout(r, 0))
}

const fullPayload = {
  semanticCacheHits: 30,
  semanticCacheMisses: 70,
  semanticCacheHitRate: 0.3,
  savedUpstreamCalls: 30,
  guardRedactions: 12,
  guardAborts: 2,
  backendP95: { 'chat-a:8080': 120.4, 'chat-b:8080': 350.6 },
}

describe('ValueSummaryCard', () => {
  beforeEach(() => {
    getValueSummary.mockReset()
  })

  it('四个数字都来自端点：命中率转百分比、护栏显示 脱敏/阻断 两个数、最慢实例取 max', async () => {
    getValueSummary.mockResolvedValue({ data: { data: fullPayload } })
    const wrapper = mountCard()
    await flush()
    expect(wrapper.text()).toContain('30.0%')
    expect(wrapper.text()).toContain('dashboard.valueSavedCalls')
    expect(wrapper.text()).toContain('12 / 2')
    // backendP95 取耗时最大的那条（350.6ms 的 chat-b），并四舍五入
    expect(wrapper.text()).toContain('chat-b:8080 · 351ms')
    expect(wrapper.text()).toContain('dashboard.valueBackendSlowest')
  })

  it('零流量时命中率显示 0.0% 而不是 NaN', async () => {
    getValueSummary.mockResolvedValue({
      data: {
        data: {
          ...fullPayload,
          semanticCacheHits: 0,
          semanticCacheMisses: 0,
          semanticCacheHitRate: 0,
          savedUpstreamCalls: 0,
        },
      },
    })
    const wrapper = mountCard()
    await flush()
    expect(wrapper.text()).toContain('0.0%')
    expect(wrapper.text()).not.toContain('NaN')
  })

  it('没有出站流量时如实显示「暂无数据」，不编造实例名', async () => {
    getValueSummary.mockResolvedValue({ data: { data: { ...fullPayload, backendP95: {} } } })
    const wrapper = mountCard()
    await flush()
    expect(wrapper.text()).toContain('dashboard.valueEmpty')
    expect(wrapper.text()).not.toContain('ms')
  })

  it('接口失败时显示「数据暂不可用」与占位符，不抛错', async () => {
    getValueSummary.mockRejectedValue(new Error('boom'))
    const wrapper = mountCard()
    await flush()
    expect(wrapper.text()).toContain('dashboard.valueUnavailable')
    expect(wrapper.text()).toContain('--')
    expect(wrapper.text()).not.toContain('%')
  })
})
