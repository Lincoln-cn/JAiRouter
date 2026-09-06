/**
 * Playground 错误处理工具
 * 提供友好的错误提示信息
 */

import { i18n } from '@/i18n'

// i18n.global.t 轻量包装（消息 schema 为动态合并，运行期按 key 查找字符串）
const gt = (key: string, named?: Record<string, unknown>): string =>
  (i18n.global as unknown as { t: (key: string, named?: Record<string, unknown>) => string }).t(key, named)

/**
 * 状态码错误提示（值为 i18n key，文案见各语言 playgroundErrors.json）
 */
const STATUS_CODE_HINTS: Record<number, string> = {
  401: 'playgroundErrors.status.401',
  403: 'playgroundErrors.status.403',
  404: 'playgroundErrors.status.404',
  429: 'playgroundErrors.status.429',
  500: 'playgroundErrors.status.500',
  502: 'playgroundErrors.status.502',
  503: 'playgroundErrors.status.503',
  504: 'playgroundErrors.status.504'
}

/**
 * 错误关键词映射（值为 i18n key）
 */
const ERROR_KEYWORDS: Record<string, string> = {
  'connection refused': 'playgroundErrors.keyword.connectionRefused',
  'timeout': 'playgroundErrors.keyword.timeout',
  'unknown model': 'playgroundErrors.keyword.unknownModel',
  'not found': 'playgroundErrors.keyword.notFound',
  'unauthorized': 'playgroundErrors.keyword.unauthorized',
  'invalid api key': 'playgroundErrors.keyword.invalidApiKey',
  'not supported': 'playgroundErrors.keyword.notSupported',
  'Service is currently degraded': 'playgroundErrors.keyword.serviceDegraded',
  'failed to': 'playgroundErrors.keyword.failedTo'
}

/**
 * 解析错误信息并返回友好的提示
 */
export function parseErrorMessage(error: any, context?: string): string {
  // 1. 检查是否有响应数据
  if (error.data) {
    const errorData = error.data

    // OpenAI 格式错误
    if (errorData.error?.message) {
      return enhanceErrorMessage(errorData.error.message, error.status)
    }

    // 直接字符串错误
    if (typeof errorData === 'string') {
      return enhanceErrorMessage(errorData, error.status)
    }

    // message 字段
    if (errorData.message) {
      return enhanceErrorMessage(errorData.message, error.status)
    }
  }

  // 2. 检查 HTTP 状态码
  if (error.status && STATUS_CODE_HINTS[error.status]) {
    return gt(STATUS_CODE_HINTS[error.status])
  }

  // 3. 检查错误消息关键词
  const errorMsg = error.message || error.statusText || String(error)
  for (const [keyword, key] of Object.entries(ERROR_KEYWORDS)) {
    if (errorMsg.toLowerCase().includes(keyword.toLowerCase())) {
      return gt(key)
    }
  }

  // 4. 返回默认错误信息
  const op = context || gt('playgroundErrors.defaultContext')
  if (errorMsg && errorMsg !== '[object Object]') {
    return gt('playgroundErrors.failedDetail', { context: op, message: errorMsg })
  }

  return gt('playgroundErrors.failedCheckConfig', { context: op })
}

/**
 * 增强错误信息，添加提示
 */
function enhanceErrorMessage(message: string, status?: number): string {
  // 如果是 401 错误，添加认证提示
  if (status === 401) {
    return gt('playgroundErrors.enhance401', { message })
  }

  // 检查是否匹配已知错误模式
  for (const [keyword, key] of Object.entries(ERROR_KEYWORDS)) {
    if (message.toLowerCase().includes(keyword.toLowerCase())) {
      return gt('playgroundErrors.enhanceKeyword', { message, hint: gt(key) })
    }
  }

  return message
}

/**
 * 获取错误操作建议
 */
export function getErrorSuggestion(error: any): string | null {
  if (!error?.status) return null

  switch (error.status) {
    case 401:
      return gt('playgroundErrors.suggestion.401')
    case 404:
      return gt('playgroundErrors.suggestion.404')
    case 429:
      return gt('playgroundErrors.suggestion.429')
    case 500:
    case 502:
    case 503:
      return gt('playgroundErrors.suggestion.5xx')
    default:
      return null
  }
}
