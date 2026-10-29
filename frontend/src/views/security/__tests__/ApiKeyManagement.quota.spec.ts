/**
 * Issue #84 复现：创建 API Key 时设置的配额未完整提交给后端。
 *
 * 症状：在创建对话框里设置了「每日 Token 上限 / 每分钟速率 / 告警阈值」，
 * 但保存后抽屉/列表显示为 0（不限制）与 0.8，即设置的配额“没有正常显示”。
 * 根因：handleSave 构造的 payload 只带了 dailyRequestLimit 与 rotationPeriodDays。
 */
import { describe, expect, it, vi, beforeEach } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'

/**
 * 组件实例的对外形状（#525：替代 `wrapper.vm as any`）。
 * 只声明本文件用例真正访问的成员；键名/返回值写错会在编译期报错。
 */
interface QuotaPageVm {
  handleCreateApiKey: () => void
  handleEdit: (row: Record<string, unknown>) => void
  handleSave: () => Promise<void>
  form: {
    keyId: string
    description: string
    permissions: string[]
    dailyRequestLimit: number
    dailyTokenLimit: number
    rateLimitPerMinute: number
    quotaAlertThreshold: number
    rotationPeriodDays: number
    allowedIpAddresses: string[]
  }
}

vi.mock('element-plus', () => ({
  ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn() },
  ElMessageBox: { confirm: vi.fn().mockResolvedValue('confirm') }
}))

vi.mock('@element-plus/icons-vue', () => ({
  Key: { template: '<i />' },
  CircleCheck: { template: '<i />' },
  CircleClose: { template: '<i />' },
  Warning: { template: '<i />' },
  RefreshRight: { template: '<i />' },
  Download: { template: '<i />' },
  Upload: { template: '<i />' },
  UploadFilled: { template: '<i />' }
}))

const createApiKey = vi.fn().mockResolvedValue({ keyId: 'k1', keyValue: 'sk-x' })
const updateApiKey = vi.fn().mockResolvedValue({ keyId: 'k1' })

vi.mock('@/api/apiKey', () => ({
  createApiKey: (...a: unknown[]) => createApiKey(...a),
  updateApiKey: (...a: unknown[]) => updateApiKey(...a),
  getApiKeys: vi.fn().mockResolvedValue({
    items: [], total: 0, enabledCount: 0, disabledCount: 0, expiredCount: 0,
    summary: { todayTotalRequests: 0, todaySuccessfulRequests: 0, todayFailedRequests: 0 }
  }),
  deleteApiKey: vi.fn(),
  disableApiKey: vi.fn(),
  enableApiKey: vi.fn(),
  resetApiKey: vi.fn(),
  rotateApiKey: vi.fn(),
  exportApiKeys: vi.fn(),
  importApiKeys: vi.fn(),
  resetApiKeyQuota: vi.fn(),
  getQuotaOverview: vi.fn().mockResolvedValue([]),
  getQuotaAlerts: vi.fn().mockResolvedValue([]),
  getApiKeyQuota: vi.fn().mockResolvedValue({})
}))

vi.mock('@/api/apiKeyQuotaOps', () => ({
  updateApiKeyQuota: vi.fn().mockResolvedValue({})
}))

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: vi.fn() })
}))

import ApiKeyManagement from '@/views/security/ApiKeyManagement.vue'

const i18n = createI18n({
  legacy: false,
  locale: 'zh-CN',
  missingWarn: false,
  fallbackWarn: false,
  messages: { 'zh-CN': {} }
})

// 全部 Element Plus 组件替换为空组件：本用例只驱动 setup 内的提交逻辑，
// 不依赖任何 DOM 渲染，也避免作用域插槽（如 el-table-column 的 scope.row）被误渲染。
const elStubs = [
  'el-alert', 'el-badge', 'el-button', 'el-card', 'el-checkbox', 'el-checkbox-group',
  'el-col', 'el-date-picker', 'el-descriptions', 'el-descriptions-item', 'el-dialog',
  'el-drawer', 'el-form', 'el-form-item', 'el-icon', 'el-input', 'el-input-number',
  'el-option', 'el-pagination', 'el-radio', 'el-radio-group', 'el-row', 'el-select',
  'el-slider', 'el-switch', 'el-table', 'el-table-column', 'el-tag', 'el-tooltip', 'el-upload'
]
const stubs = Object.fromEntries([
  ...elStubs.map(c => [c, true]),
  ['PageSkeleton', true],
  ['StatCard', true]
])

const mountPage = () => mount(ApiKeyManagement, { global: { plugins: [i18n], stubs } })

describe('ApiKeyManagement 配额提交（issue #84）', () => {
  beforeEach(() => {
    createApiKey.mockClear()
    updateApiKey.mockClear()
  })

  it('创建 API Key 时应提交全部 4 个配额字段', async () => {
    const wrapper = mountPage()
    await flushPromises()
    const vm = wrapper.vm as unknown as QuotaPageVm

    vm.handleCreateApiKey()
    vm.form.keyId = 'k1'
    vm.form.description = 'quota round trip'
    vm.form.permissions = ['chat']
    vm.form.dailyRequestLimit = 1000
    vm.form.dailyTokenLimit = 50000
    vm.form.rateLimitPerMinute = 60
    vm.form.quotaAlertThreshold = 0.5
    vm.form.rotationPeriodDays = 30

    await vm.handleSave()

    expect(createApiKey).toHaveBeenCalledTimes(1)
    const payload = createApiKey.mock.calls[0][0]
    expect(payload.dailyRequestLimit).toBe(1000)
    expect(payload.dailyTokenLimit).toBe(50000)
    expect(payload.rateLimitPerMinute).toBe(60)
    expect(payload.quotaAlertThreshold).toBe(0.5)
    expect(payload.rotationPeriodDays).toBe(30)
  })

  it('编辑 API Key 时应提交全部 4 个配额字段', async () => {
    const wrapper = mountPage()
    await flushPromises()
    const vm = wrapper.vm as unknown as QuotaPageVm

    vm.handleEdit({
      keyId: 'k1', description: 'd', enabled: true, permissions: ['chat'],
      dailyRequestLimit: 200, dailyTokenLimit: 9000, rateLimitPerMinute: 30,
      quotaAlertThreshold: 0.7, rotationPeriodDays: 10
    })
    await vm.handleSave()

    expect(updateApiKey).toHaveBeenCalledTimes(1)
    const payload = updateApiKey.mock.calls[0][1]
    expect(payload.dailyRequestLimit).toBe(200)
    expect(payload.dailyTokenLimit).toBe(9000)
    expect(payload.rateLimitPerMinute).toBe(30)
    expect(payload.quotaAlertThreshold).toBe(0.7)
    expect(payload.rotationPeriodDays).toBe(10)
  })

  // issue #777：后端此前不回显 allowedIpAddresses，编辑对话框写死 [] 把它抹掉了。
  it('编辑 API Key 时应回填并提交 IP 白名单', async () => {
    const wrapper = mountPage()
    await flushPromises()
    const vm = wrapper.vm as unknown as QuotaPageVm

    vm.handleEdit({
      keyId: 'k1', description: 'd', enabled: true, permissions: ['chat'],
      allowedIpAddresses: ['10.0.0.1', '192.168.0.0/16'],
      dailyRequestLimit: 0, dailyTokenLimit: 0, rateLimitPerMinute: 0,
      quotaAlertThreshold: 0.8, rotationPeriodDays: 0
    })

    expect(vm.form.allowedIpAddresses).toEqual(['10.0.0.1', '192.168.0.0/16'])

    await vm.handleSave()
    const payload = updateApiKey.mock.calls[0][1]
    expect(payload.allowedIpAddresses).toEqual(['10.0.0.1', '192.168.0.0/16'])
  })
})
