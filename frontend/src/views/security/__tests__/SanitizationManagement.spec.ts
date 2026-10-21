/**
 * SanitizationManagement 组件表单同步单测
 */
import { describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'

/**
 * 组件实例的对外形状（#525：替代 `wrapper.vm as any`）。
 * 只声明本文件用例真正访问的成员；字段名写错会在编译期报错。
 */
interface SanitizationPageVm {
  form: {
    request: { enabled: boolean; maskingChar: string; piiPatternsText: string }
    response: { enabled: boolean; maskingChar: string }
  }
}

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
      preserveJsonStructure: true,
      scope: {
        mode: 'include',
        patterns: ['/v1/**'],
        usingBuiltInDefaults: false,
        warning: ''
      }
    },
    ruleCount: 2,
    scopeSource: 'configuration'
  }),
  getSanitizationRules: vi.fn().mockResolvedValue([
    { ruleId: 'r1', type: 'PII_PATTERN', pattern: '\\d{11}', enabled: true }
  ]),
  updateSanitizationConfig: vi.fn().mockResolvedValue({ ruleCount: 2 }),
  testSanitization: vi.fn(),
  previewSanitizationScope: vi.fn().mockResolvedValue({
    path: '/v1/chat/completions',
    mode: 'include',
    shouldSanitize: true,
    matchedPattern: '/v1/**',
    usingBuiltInDefaults: false,
    warning: ''
  })
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
        loadFailed: 'load fail',
        scopeTitle: '响应脱敏生效范围',
        scopeModeExclude: '排除模式',
        scopeModeInclude: '包含模式',
        scopeSourceBuiltin: '内置默认',
        scopeSourceConfig: '配置指定',
        scopeDesc: 'scope desc',
        scopePatternsLabel: '路径规则',
        scopeEmptyPatterns: '未配置',
        scopePreviewPlaceholder: '输入路径',
        scopePreviewButton: '预览',
        scopePreviewSanitized: '会脱敏',
        scopePreviewSkipped: '跳过脱敏',
        scopePreviewMatched: '命中规则',
        scopeNotConfigurableHint: '只读'
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
  'el-tag': { template: '<span><slot /></span>' },
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
    const vm = wrapper.vm as unknown as SanitizationPageVm
    expect(vm.form.request.enabled).toBe(true)
    expect(vm.form.request.maskingChar).toBe('*')
    expect(vm.form.request.piiPatternsText).toContain('\\d{11}')
    expect(vm.form.response.enabled).toBe(false)
    expect(vm.form.response.maskingChar).toBe('#')
  })

  it('回显响应脱敏生效范围（模式/来源/规则）', async () => {
    const wrapper = mount(SanitizationManagement, {
      global: {
        plugins: [i18n],
        stubs
      }
    })

    await flushPromises()
    const text = wrapper.text()
    expect(text).toContain('响应脱敏生效范围')
    expect(text).toContain('包含模式')
    expect(text).toContain('配置指定')
    expect(text).toContain('/v1/**')
    expect(text).toContain('只读')
  })
})
