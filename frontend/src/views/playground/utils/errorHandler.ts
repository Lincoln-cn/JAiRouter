/**
 * Playground 错误处理工具
 * 提供友好的错误提示信息
 */

/**
 * 状态码错误提示
 */
const STATUS_CODE_HINTS: Record<number, string> = {
  401: '实例未配置认证，请前往实例管理添加 Authorization 或 X-API-Key 认证头',
  403: '权限不足，请检查实例的访问权限配置',
  404: '接口不存在，请检查实例地址和端口是否正确',
  429: '请求频率超限，请稍后重试或调整限流配置',
  500: '服务内部错误，请查看实例日志排查问题',
  502: '实例不可达，请检查实例是否正常运行',
  503: '服务不可用，实例可能正在重启或已熔断',
  504: '请求超时，请检查实例性能或调整超时配置'
}

/**
 * 错误关键词映射
 */
const ERROR_KEYWORDS: Record<string, string> = {
  'connection refused': '无法连接到实例，请检查实例是否启动',
  'timeout': '请求超时，实例响应时间过长',
  'unknown model': '模型不存在，请检查模型名称是否正确',
  'not found': '资源不存在，请检查配置',
  'unauthorized': '认证失败，请检查实例的认证配置',
  'invalid api key': 'API Key 无效，请检查实例的认证信息',
  'not supported': '参数不支持，请检查请求参数',
  'Service is currently degraded': '服务熔断中，请稍后重试或重置熔断器',
  'failed to': '操作失败，请检查配置'
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
    return STATUS_CODE_HINTS[error.status]
  }

  // 3. 检查错误消息关键词
  const errorMsg = error.message || error.statusText || String(error)
  for (const [keyword, hint] of Object.entries(ERROR_KEYWORDS)) {
    if (errorMsg.toLowerCase().includes(keyword.toLowerCase())) {
      return hint
    }
  }

  // 4. 返回默认错误信息
  if (errorMsg && errorMsg !== '[object Object]') {
    return `${context || '操作'}失败: ${errorMsg}`
  }

  return `${context || '操作'}失败，请检查配置后重试`
}

/**
 * 增强错误信息，添加提示
 */
function enhanceErrorMessage(message: string, status?: number): string {
  // 如果是 401 错误，添加认证提示
  if (status === 401) {
    return `${message}\n\n💡 提示: 实例未配置认证，请在实例管理中添加认证头（如 Authorization 或 X-API-Key）`
  }

  // 检查是否匹配已知错误模式
  for (const [keyword, hint] of Object.entries(ERROR_KEYWORDS)) {
    if (message.toLowerCase().includes(keyword.toLowerCase())) {
      return `${message}\n\n💡 提示: ${hint}`
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
      return '前往实例管理 → 选择实例 → 点击"编辑" → 添加认证头配置'
    case 404:
      return '检查实例地址、端口是否正确，或确认模型是否已加载'
    case 429:
      return '稍后重试，或在实例管理中调整限流配置'
    case 500:
    case 502:
    case 503:
      return '检查实例日志，确认服务状态正常'
    default:
      return null
  }
}
