import request from '@/utils/request'
import type { RouterResponse } from '@/types'

/**
 * PII / 脱敏配置管理 API
 * - GET  /api/config/sanitization        配置快照
 * - PUT  /api/config/sanitization        热改 request/response
 * - GET  /api/config/sanitization/rules  当前规则
 * - POST /api/config/sanitization/test   试脱敏
 */

export interface SanitizationSubConfig {
  enabled: boolean
  piiPatterns: string[]
  sensitiveWords: string[]
  maskingChar: string
  logSanitization: boolean
  failOnError: boolean
  preserveJsonStructure?: boolean
  whitelistUsers?: string[]
  scope?: SanitizationScope
}

/** 响应脱敏的路径生效范围（issue #560 可配置化 A 档，只读回显） */
export interface SanitizationScope {
  mode: 'exclude' | 'include'
  patterns: string[]
  usingBuiltInDefaults: boolean
  warning: string
}

export interface SanitizationConfig {
  request: SanitizationSubConfig
  response: SanitizationSubConfig
  ruleCount: number
  primaryUseCase?: string
  gatewayResponseFilter?: string
  scopeSource?: string
}

export interface SanitizationScopePreview {
  path: string
  mode: 'exclude' | 'include'
  shouldSanitize: boolean
  matchedPattern: string | null
  usingBuiltInDefaults: boolean
  warning: string
}

export interface SanitizationRuleItem {
  ruleId: string
  name?: string
  description?: string
  type?: string
  pattern?: string
  strategy?: string
  enabled: boolean
  priority?: number
  replacementChar?: string
}

export interface SanitizationTestResult {
  before: string
  after: string
  matchedRuleIds: string[]
  contentType: string
}

export const getSanitizationConfig = async (): Promise<SanitizationConfig | null> => {
  const response = await request.get<RouterResponse<SanitizationConfig>>('/config/sanitization')
  return (response.data.data as SanitizationConfig) ?? null
}

export const updateSanitizationConfig = async (payload: {
  request?: Partial<SanitizationSubConfig>
  response?: Partial<SanitizationSubConfig>
}): Promise<SanitizationConfig> => {
  const response = await request.put<RouterResponse<SanitizationConfig>>('/config/sanitization', payload)
  return response.data.data as SanitizationConfig
}

export const getSanitizationRules = async (): Promise<SanitizationRuleItem[]> => {
  const response = await request.get<RouterResponse<SanitizationRuleItem[]>>('/config/sanitization/rules')
  return response.data.data || []
}

export const testSanitization = async (sample: string, contentType = 'text/plain'): Promise<SanitizationTestResult> => {
  const response = await request.post<RouterResponse<SanitizationTestResult>>('/config/sanitization/test', {
    sample,
    contentType
  })
  return response.data.data as SanitizationTestResult
}

/** 预览某个路径是否落在脱敏生效范围内（与服务端同一套 Ant/前缀匹配） */
export const previewSanitizationScope = async (path: string): Promise<SanitizationScopePreview> => {
  const response = await request.post<RouterResponse<SanitizationScopePreview>>(
    '/config/sanitization/scope/preview',
    { path }
  )
  return response.data.data as SanitizationScopePreview
}
