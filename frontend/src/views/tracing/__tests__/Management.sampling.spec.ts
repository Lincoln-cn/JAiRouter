/**
 * issue #224（P5）：采样策略选择器 + 保存不再清空未暴露的采样字段。
 *
 * 两件事一起钉住：
 * 1. `sampling.strategy`（#234 起后端真正接线）现在能在页面上选，且会随保存提交；
 * 2. 保存用的是「服务端原始 sampling 段 + 覆盖本页可编辑字段」，因此本页没暴露的
 *    `rules` 与 `adaptive.targetSpansPerSecond` / `minRatio` / `maxRatio` 不会被清空
 *    —— 后端是整体替换 sampling（TracingController#updateRuntimeConfiguration），
 *    不是逐字段合并，所以旧的「只发部分对象」写法会把它们覆盖成默认值。
 */
import { describe, expect, it, vi, beforeEach } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'

vi.mock('element-plus', () => ({
  ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn() },
  ElMessageBox: { confirm: vi.fn().mockResolvedValue('confirm'), prompt: vi.fn() }
}))

const updateTracingConfig = vi.fn().mockResolvedValue({})
const refreshSamplingStrategy = vi.fn().mockResolvedValue({})

const serverSampling = vi.hoisted(() => ({
  strategy: 'rule',
  ratio: 0.2,
  serviceRatios: { chat: 0.3 },
  alwaysSample: ['/v1/chat'],
  neverSample: ['/health'],
  rules: [{ condition: 'service.type == "chat"', ratio: 0.5 }],
  adaptive: { enabled: true, targetSpansPerSecond: 500, minRatio: 0.01, maxRatio: 0.5 }
}))

vi.mock('@/api/tracing', () => ({
  getTracingStatus: vi.fn().mockResolvedValue({
    data: { data: { serviceName: 'jairouter', enabled: true, sampling: { globalRatio: 0.2 } } }
  }),
  getTracingHealth: vi.fn().mockResolvedValue({ data: { data: { status: 'UP' } } }),
  getTracingConfig: vi.fn().mockResolvedValue({
    data: {
      data: {
        sampling: serverSampling,
        exporter: { type: 'logging', logging: { enabled: true } },
        performance: { asyncProcessing: true }
      }
    }
  }),
  updateTracingConfig: (...a: unknown[]) => updateTracingConfig(...a),
  refreshSamplingStrategy: (...a: unknown[]) => refreshSamplingStrategy(...a),
  enableTracing: vi.fn(),
  disableTracing: vi.fn(),
  refreshTracingData: vi.fn(),
  cleanupExpiredTraces: vi.fn()
}))

import Management from '@/views/tracing/Management.vue'

const i18n = createI18n({
  legacy: false,
  locale: 'zh-CN',
  missingWarn: false,
  fallbackWarn: false,
  messages: { 'zh-CN': {} }
})

const elStubs = [
  'el-button', 'el-card', 'el-col', 'el-descriptions', 'el-descriptions-item', 'el-divider',
  'el-form', 'el-form-item', 'el-input', 'el-input-number', 'el-option', 'el-progress', 'el-row',
  'el-select', 'el-slider', 'el-switch', 'el-table', 'el-table-column', 'el-tab-pane', 'el-tabs',
  'el-tag'
]
const stubs = Object.fromEntries([
  ...elStubs.map(c => [c, true]),
  ['PageSkeleton', true]
])

const mountPage = () => mount(Management, { global: { plugins: [i18n], stubs } })

describe('tracing Management 采样配置（issue #224 P5）', () => {
  beforeEach(() => {
    updateTracingConfig.mockClear()
    refreshSamplingStrategy.mockClear()
  })

  it('加载时应把服务端的 strategy 回显到选择器', async () => {
    const wrapper = mountPage()
    await flushPromises()

    expect((wrapper.vm as any).samplingConfig.strategy).toBe('rule')
  })

  it('保存时应提交页面选中的 strategy 与服务端原本的 rules / adaptive 字段', async () => {
    const wrapper = mountPage()
    await flushPromises()
    const vm = wrapper.vm as any

    vm.samplingConfig.strategy = 'adaptive'
    await vm.handleSaveConfig()
    await flushPromises()

    expect(updateTracingConfig).toHaveBeenCalledTimes(1)
    const sampling = updateTracingConfig.mock.calls[0][0].sampling

    expect(sampling.strategy).toBe('adaptive')
    expect(sampling.ratio).toBeCloseTo(0.2, 5)
    // 本页没暴露的字段必须原样保留
    expect(sampling.rules).toEqual(serverSampling.rules)
    expect(sampling.adaptive).toEqual({
      enabled: true,
      targetSpansPerSecond: 500,
      minRatio: 0.01,
      maxRatio: 0.5
    })
    expect(refreshSamplingStrategy).toHaveBeenCalled()
  })

  it('关闭自适应开关只改 adaptive.enabled，不丢其余 adaptive 字段', async () => {
    const wrapper = mountPage()
    await flushPromises()
    const vm = wrapper.vm as any

    vm.samplingConfig.adaptiveSampling = false
    await vm.handleSaveConfig()
    await flushPromises()

    const adaptive = updateTracingConfig.mock.calls[0][0].sampling.adaptive
    expect(adaptive.enabled).toBe(false)
    expect(adaptive.targetSpansPerSecond).toBe(500)
    expect(adaptive.minRatio).toBe(0.01)
    expect(adaptive.maxRatio).toBe(0.5)
  })
})
