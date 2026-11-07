import { describe, expect, it, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'

const push = vi.fn()
vi.mock('vue-router', () => ({ useRouter: () => ({ push }) }))
vi.mock('vue-i18n', () => ({
  useI18n: () => ({ t: (key: string, arg?: unknown) => (arg ? `${key}:${JSON.stringify(arg)}` : key) }),
}))

const getServiceStats = vi.fn()
const getApiKeys = vi.fn()
vi.mock('@/api/dashboard', () => ({ getServiceStats: () => getServiceStats() }))
vi.mock('@/api/apiKey', () => ({ getApiKeys: () => getApiKeys() }))

import GettingStartedCard from '../GettingStartedCard.vue'

function mountCard() {
  return mount(GettingStartedCard, {
    global: {
      stubs: {
        'el-card': { template: '<div><slot /></div>' },
        'el-button': { template: '<button @click="$emit(\'click\')"><slot /></button>' },
        'el-tag': { template: '<span><slot /></span>' },
      },
    },
  })
}

async function flush() {
  await Promise.resolve()
  await Promise.resolve()
  await new Promise((r) => setTimeout(r, 0))
}

describe('GettingStartedCard', () => {
  beforeEach(() => {
    push.mockClear()
    getServiceStats.mockReset()
    getApiKeys.mockReset()
  })

  it('实例与密钥都没有时，两步都标为待办', async () => {
    getServiceStats.mockResolvedValue({ data: { totalInstances: 0 } })
    getApiKeys.mockResolvedValue({ data: { keys: [] } })
    const wrapper = mountCard()
    await flush()
    expect(wrapper.text()).toContain('dashboard.gsStepInstance')
    expect(wrapper.text()).toContain('dashboard.gsActionConfig')
    expect(wrapper.text()).toContain('dashboard.gsActionApiKey')
    // 未完成时不出现接入示例
    expect(wrapper.text()).not.toContain('dashboard.gsCurlHint')
  })

  it('实例与密钥齐备时显示「可以接入」与 curl 示例', async () => {
    getServiceStats.mockResolvedValue({ data: { totalInstances: 3 } })
    getApiKeys.mockResolvedValue({ data: { keys: [{ id: 'k1' }] } })
    const wrapper = mountCard()
    await flush()
    expect(wrapper.text()).toContain('dashboard.gsAllDone')
    expect(wrapper.text()).toContain('dashboard.gsCurlHint')
    expect(wrapper.text()).toContain('/v1/chat/completions')
    // 完成态下不再出现跳转动作
    expect(wrapper.text()).not.toContain('dashboard.gsActionConfig')
  })

  it('接口失败时按「未配置」处理，不抛错', async () => {
    getServiceStats.mockRejectedValue(new Error('boom'))
    getApiKeys.mockRejectedValue(new Error('boom'))
    const wrapper = mountCard()
    await flush()
    expect(wrapper.text()).toContain('dashboard.gsStepInstanceFail')
    expect(wrapper.text()).toContain('dashboard.gsStepKeyFail')
  })
})
