/**
 * SanitizationManagement 组件表单同步单测
 */
import { describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'

vi.mock('element-plus', () => ({
  ElMessage: {
    success: vi.fn(),
    error: vi.fn(),
    warning: vi.fn()
  }
}))

vi.mock('@element-plus/icons-vue', () => ({
  Refresh: { template: '<i />' },
  Check: { template: '<i />' }
}))

vi.mock('@/api/sanitization', () => ({
  getSanitizationConfig: vi.fn().mockResolvedValue({
    request: {
      enabled: true,
      piiPatterns: ['\\d{11}'],
      sensitiveWords: ['password'],
      maskingChar: '*',
      logSanitization: true,
      failOnError: false,
      whitelistUsers: []
    },
    response: {
      enabled: false,
      piiPatterns: [],
      sensitiveWords: [],
      maskingChar: '#',
      logSanitization: false,
      failOnError: false,
      preserveJsonStructure: true
    },
    ruleCount: 2
  }),
  getSanitizationRules: vi.fn().mockResolvedValue([
    { ruleId: 'r1', type: 'PII_PATTERN', pattern: '\\d{11}', enabled: true }
  ]),
  updateSanitizationConfig: vi.fn().mockResolvedValue({ ruleCount: 2 }),
  testSanitization: vi.fn()
}))

import SanitizationManagement from '@/views/security/SanitizationManagement.vue'

const i18n = createI18n({
  legacy: false,
  locale: 'zh-CN',
  messages: {
    'zh-CN': {
      common: { enabled: '已启用', disabled: '已禁用' },
      sanitization: {
        pageTitle: 'PII 脱敏管理',
        primaryUseCaseHint: 'hint',
        refresh: '刷新',
        ruleCountLabel: '生效规则数',
        requestEnabledLabel: '请求侧',
        responseEnabledLabel: '响应侧',
        recordNote: '记录侧',
        recordNoteValue: 'SUMMARY',
        requestSection: '请求',
        responseSection: '响应',
        enabledLabel: '启用',
        piiPatternsLabel: 'PII',
        piiPatternsPlaceholder: 'p',
        sensitiveWordsLabel: '词',
        sensitiveWordsPlaceholder: 'w',
        maskingCharLabel: '掩码',
        logSanitizationLabel: '日志',
        failOnErrorLabel: '中断',
        preserveJsonLabel: 'JSON',
        whitelistUsersLabel: '白名单',
        whitelistUsersPlaceholder: 'u',
        save: '保存',
        reset: '重置',
        saveSuccess: 'ok',
        saveFailed: 'fail',
        rulesTitle: '规则',
        ruleIdColumn: 'ID',
        typeColumn: '类型',
        patternColumn: '模式',
        enabledColumn: '启用',
        testTitle: '试脱敏',
        testSampleLabel: '样例',
        testSamplePlaceholder: 'p',
        runTest: '试跑',
        beforeLabel: '前',
        afterLabel: '后',
        matchedLabel: '命中',
        loadFailed: 'load fail'
      }
    }
  }
})

const stubs = {
  PageSkeleton: { template: '<div><slot /><slot name="stats" /><slot name="actions" /></div>' },
  StatCard: true,
  'el-alert': true,
  'el-row': true,
  'el-col': true,
  'el-card': { template: '<div><slot /><slot name="header" /></div>' },
  'el-form': true,
  'el-form-item': true,
  'el-switch': true,
  'el-input': true,
  'el-table': true,
  'el-table-column': true,
  'el-tag': true,
  'el-button': true,
  'el-icon': true
}

describe('SanitizationManagement.vue', () => {
  it('挂载后根据配置同步表单（开关与掩码）', async () => {
    const wrapper = mount(SanitizationManagement, {
      global: {
        plugins: [i18n],
        stubs
      }
    })

    await flushPromises()
    const vm = wrapper.vm as any
    expect(vm.form.request.enabled).toBe(true)
    expect(vm.form.request.maskingChar).toBe('*')
    expect(vm.form.request.piiPatternsText).toContain('\\d{11}')
    expect(vm.form.response.enabled).toBe(false)
    expect(vm.form.response.maskingChar).toBe('#')
  })
})
