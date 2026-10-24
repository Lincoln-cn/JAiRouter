/**
 * 记录治理页（安全组）四件事：
 * ① 只读区如实显示「配置的密钥来源」与「实际生效的密钥来源」；
 * ② 回退发生时给出警告条（这是 3.5.1 修复的配置漂移可见性）；
 * ③ 切到 FULL 必须二次确认，取消后草稿回到当前级别；
 * ④ 保存只提交 recordLevel，并把后端返回值回填。
 */
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent } from 'vue'
import { flushPromises, mount } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import zh from '@/locales/zh-CN/recordGovernance.json'

const apiMock = vi.hoisted(() => ({
  getCallHistoryConfig: vi.fn(),
  updateCallHistoryConfig: vi.fn()
}))
const messageMock = vi.hoisted(() => ({
  success: vi.fn(),
  error: vi.fn(),
  warning: vi.fn()
}))
const messageBoxMock = vi.hoisted(() => ({ confirm: vi.fn() }))
const routerMock = vi.hoisted(() => ({ push: vi.fn() }))

vi.mock('@/api/callHistory', () => apiMock)
vi.mock('element-plus', () => ({ ElMessage: messageMock, ElMessageBox: messageBoxMock }))
vi.mock('vue-router', () => ({ useRouter: () => routerMock }))

import RecordGovernance from '../RecordGovernance.vue'

const RadioGroupStub = defineComponent({
  props: { modelValue: { type: String, default: '' } },
  emits: ['update:modelValue', 'change'],
  template: '<div class="radio-group"><slot /></div>'
})

const ButtonStub = defineComponent({
  inheritAttrs: false,
  template: '<button v-bind="$attrs"><slot /></button>'
})

const PassthroughStub = defineComponent({
  template: '<div><slot name="header" /><slot /></div>'
})

const i18n = createI18n({
  legacy: false,
  locale: 'zh-CN',
  messages: {
    'zh-CN': {
      ...zh,
      common: { enabled: '已启用', disabled: '已禁用', confirm: '确认', cancel: '取消' }
    }
  }
})

const stubs = {
  PageSkeleton: PassthroughStub,
  MetricStrip: true,
  'el-card': PassthroughStub,
  'el-alert': defineComponent({
    props: { title: { type: String, default: '' }, description: { type: String, default: '' } },
    template: '<div class="alert">{{ title }}{{ description }}</div>'
  }),
  'el-radio-group': RadioGroupStub,
  'el-radio-button': defineComponent({
    props: { value: { type: String, default: '' } },
    template: '<span class="radio">{{ value }}</span>'
  }),
  'el-button': ButtonStub,
  'el-icon': defineComponent({ template: '<i><slot /></i>' }),
  'el-tag': defineComponent({ template: '<span class="tag"><slot /></span>' }),
  'el-descriptions': defineComponent({ template: '<div><slot /></div>' }),
  'el-descriptions-item': defineComponent({
    props: { label: { type: String, default: '' } },
    template: '<div class="desc"><span class="label">{{ label }}</span><span class="value"><slot /></span></div>'
  })
}

const mountPage = () =>
  mount(RecordGovernance, { global: { plugins: [i18n], stubs } })

const configPayload = (overrides: Record<string, unknown> = {}) => ({
  recordLevel: 'SUMMARY',
  maxContentLength: 65536,
  retentionDays: 30,
  maxRecords: 1000000,
  enabled: true,
  requestBodySummaryEnabled: true,
  responseBodySummaryEnabled: true,
  slowCallThresholdMs: 1000,
  encryptionKeySource: 'env',
  encryptionKeySourceRequested: 'env',
  encryptionKeySourceEffective: 'auto',
  encryptionKeySourceHonored: false,
  environmentKeyPresent: false,
  encryptionAlgorithm: 'AES-256-GCM',
  encryptionKeyFingerprint: 'a1b2c3d4',
  contentStoredEncrypted: false,
  ...overrides
})

describe('RecordGovernance 记录治理页', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    apiMock.getCallHistoryConfig.mockResolvedValue(configPayload())
    apiMock.updateCallHistoryConfig.mockImplementation(async (payload: { recordLevel: string }) =>
      configPayload({
        recordLevel: payload.recordLevel,
        contentStoredEncrypted: payload.recordLevel === 'FULL'
      })
    )
    messageBoxMock.confirm.mockResolvedValue('confirm')
  })

  it('加载后展示配置来源与实际生效来源、密钥指纹与保留期', async () => {
    const wrapper = mountPage()
    await flushPromises()

    expect(apiMock.getCallHistoryConfig).toHaveBeenCalledTimes(1)
    expect(wrapper.find('[data-test="source-requested"]').text()).toBe('env')
    expect(wrapper.find('[data-test="source-effective"]').text()).toBe('auto')
    expect(wrapper.find('[data-test="key-fingerprint"]').text()).toBe('a1b2c3d4')
    expect(wrapper.text()).toContain('30 天')
    expect(wrapper.text()).toContain('64 KB')
    expect(wrapper.text()).toContain('1000000')
  })

  it('配置还没加载完时不渲染回退警告（避免把默认空值当成漂移）', async () => {
    apiMock.getCallHistoryConfig.mockRejectedValue(new Error('network'))
    const wrapper = mountPage()
    await flushPromises()

    expect(wrapper.find('[data-test="fallback-warning"]').exists()).toBe(false)
    expect(messageMock.error).toHaveBeenCalled()
  })

  it('回退时显示警告条，并说明配置请求的来源未生效', async () => {
    const wrapper = mountPage()
    await flushPromises()

    const warning = wrapper.find('[data-test="fallback-warning"]')
    expect(warning.exists()).toBe(true)
    expect(warning.text()).toContain('配置请求的密钥来源未生效')
    expect(warning.text()).toContain('JAIR_CALL_HISTORY_KEY')
  })

  it('来源被采纳（honored）时不显示警告条', async () => {
    apiMock.getCallHistoryConfig.mockResolvedValue(
      configPayload({
        recordLevel: 'FULL',
        encryptionKeySourceRequested: 'env',
        encryptionKeySourceEffective: 'env',
        encryptionKeySourceHonored: true,
        environmentKeyPresent: true,
        contentStoredEncrypted: true
      })
    )
    const wrapper = mountPage()
    await flushPromises()

    expect(wrapper.find('[data-test="fallback-warning"]').exists()).toBe(false)
    expect(wrapper.find('[data-test="env-key-present"]').text()).toBe('可用')
    expect(wrapper.text()).toContain('内容加密存储')
  })

  it('切到 FULL 需要确认；确认后保存只提交 recordLevel', async () => {
    const wrapper = mountPage()
    await flushPromises()

    wrapper.findComponent(RadioGroupStub).vm.$emit('update:modelValue', 'FULL')
    wrapper.findComponent(RadioGroupStub).vm.$emit('change', 'FULL')
    await flushPromises()
    expect(messageBoxMock.confirm).toHaveBeenCalledTimes(1)

    await wrapper.find('[data-test="save"]').trigger('click')
    await flushPromises()

    expect(apiMock.updateCallHistoryConfig).toHaveBeenCalledWith({ recordLevel: 'FULL' })
    expect(messageMock.success).toHaveBeenCalled()
    // 保存后草稿与后端一致，保存按钮回到禁用态
    expect(wrapper.find('[data-test="save"]').attributes('disabled')).toBeDefined()
  })

  it('取消 FULL 确认后草稿回到当前级别，且不会保存', async () => {
    messageBoxMock.confirm.mockRejectedValue('cancel')
    const wrapper = mountPage()
    await flushPromises()

    const group = wrapper.findComponent(RadioGroupStub)
    group.vm.$emit('update:modelValue', 'FULL')
    group.vm.$emit('change', 'FULL')
    await flushPromises()

    expect(group.props('modelValue')).toBe('SUMMARY')
    expect(apiMock.updateCallHistoryConfig).not.toHaveBeenCalled()
  })

  it('切到非 FULL 级别不弹确认，直接可保存', async () => {
    const wrapper = mountPage()
    await flushPromises()

    const group = wrapper.findComponent(RadioGroupStub)
    group.vm.$emit('update:modelValue', 'METADATA_ONLY')
    group.vm.$emit('change', 'METADATA_ONLY')
    await flushPromises()

    expect(messageBoxMock.confirm).not.toHaveBeenCalled()
    await wrapper.find('[data-test="save"]').trigger('click')
    await flushPromises()
    expect(apiMock.updateCallHistoryConfig).toHaveBeenCalledWith({ recordLevel: 'METADATA_ONLY' })
  })

  it('解密审计卡提供跳转到审计日志的入口', async () => {
    const wrapper = mountPage()
    await flushPromises()

    await wrapper.find('[data-test="open-audit"]').trigger('click')
    expect(routerMock.push).toHaveBeenCalledWith('/security/audit-logs')
  })
})
