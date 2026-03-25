/**
 * 统一错误处理工具
 * 提供标准化的错误类型、错误码和错误信息格式化
 */

// 错误码枚举
export enum ErrorCode {
  // 通用错误 (1xxx)
  UNKNOWN_ERROR = '1000',
  NETWORK_ERROR = '1001',
  TIMEOUT_ERROR = '1002',
  REQUEST_CANCELLED = '1003',

  // 认证错误 (2xxx)
  AUTH_REQUIRED = '2000',
  AUTH_FAILED = '2001',
  TOKEN_EXPIRED = '2002',
  TOKEN_INVALID = '2003',
  PERMISSION_DENIED = '2004',

  // 参数错误 (3xxx)
  INVALID_PARAMS = '3000',
  MISSING_PARAMS = '3001',
  PARAMS_TYPE_ERROR = '3002',

  // 业务错误 (4xxx)
  RESOURCE_NOT_FOUND = '4000',
  RESOURCE_ALREADY_EXISTS = '4001',
  OPERATION_FAILED = '4002',
  DATA_CONFLICT = '4003',

  // 系统错误 (5xxx)
  SYSTEM_ERROR = '5000',
  SERVICE_UNAVAILABLE = '5001',
  DATABASE_ERROR = '5002'
}

// 错误信息映射
const ERROR_MESSAGES: Record<ErrorCode, string> = {
  // 通用错误
  [ErrorCode.UNKNOWN_ERROR]: '未知错误',
  [ErrorCode.NETWORK_ERROR]: '网络连接失败，请检查网络',
  [ErrorCode.TIMEOUT_ERROR]: '请求超时，请重试',
  [ErrorCode.REQUEST_CANCELLED]: '请求已取消',

  // 认证错误
  [ErrorCode.AUTH_REQUIRED]: '请先登录',
  [ErrorCode.AUTH_FAILED]: '认证失败，请检查用户名和密码',
  [ErrorCode.TOKEN_EXPIRED]: '登录已过期，请重新登录',
  [ErrorCode.TOKEN_INVALID]: '令牌无效，请重新登录',
  [ErrorCode.PERMISSION_DENIED]: '权限不足',

  // 参数错误
  [ErrorCode.INVALID_PARAMS]: '参数无效',
  [ErrorCode.MISSING_PARAMS]: '缺少必要参数',
  [ErrorCode.PARAMS_TYPE_ERROR]: '参数类型错误',

  // 业务错误
  [ErrorCode.RESOURCE_NOT_FOUND]: '资源不存在',
  [ErrorCode.RESOURCE_ALREADY_EXISTS]: '资源已存在',
  [ErrorCode.OPERATION_FAILED]: '操作失败',
  [ErrorCode.DATA_CONFLICT]: '数据冲突',

  // 系统错误
  [ErrorCode.SYSTEM_ERROR]: '系统错误',
  [ErrorCode.SERVICE_UNAVAILABLE]: '服务不可用',
  [ErrorCode.DATABASE_ERROR]: '数据库错误'
}

// 标准错误接口
export interface AppError {
  code: ErrorCode
  message: string
  details?: Record<string, unknown>
  cause?: Error
  timestamp: string
}

// HTTP 状态码到错误码映射
const HTTP_STATUS_TO_ERROR: Record<number, ErrorCode> = {
  400: ErrorCode.INVALID_PARAMS,
  401: ErrorCode.AUTH_FAILED,
  403: ErrorCode.PERMISSION_DENIED,
  404: ErrorCode.RESOURCE_NOT_FOUND,
  408: ErrorCode.TIMEOUT_ERROR,
  409: ErrorCode.DATA_CONFLICT,
  500: ErrorCode.SYSTEM_ERROR,
  502: ErrorCode.SERVICE_UNAVAILABLE,
  503: ErrorCode.SERVICE_UNAVAILABLE,
  504: ErrorCode.TIMEOUT_ERROR
}

/**
 * 创建标准错误对象
 */
export function createError(
  code: ErrorCode,
  message?: string,
  details?: Record<string, unknown>,
  cause?: Error
): AppError {
  return {
    code,
    message: message || ERROR_MESSAGES[code],
    details,
    cause,
    timestamp: new Date().toISOString()
  }
}

/**
 * 从 HTTP 响应创建错误
 */
export function createHttpError(
  status: number,
  statusText?: string,
  data?: any
): AppError {
  const errorCode = HTTP_STATUS_TO_ERROR[status] || ErrorCode.UNKNOWN_ERROR
  const defaultMessage = ERROR_MESSAGES[errorCode]
  
  // 优先使用响应中的错误信息
  let message = defaultMessage
  if (data?.message) {
    message = data.message
  } else if (statusText) {
    message = `${status} ${statusText}`
  }

  return createError(errorCode, message, {
    status,
    statusText,
    responseData: data
  })
}

/**
 * 从任意错误创建标准错误
 */
export function toAppError(error: unknown): AppError {
  // 如果已经是 AppError，直接返回
  if (isAppError(error)) {
    return error
  }

  // 处理 Axios 错误
  if (isAxiosError(error)) {
    const status = error.response?.status
    if (status) {
      return createHttpError(status, error.response?.statusText, error.response?.data)
    }
    
    // 网络错误
    if (error.code === 'ECONNABORTED') {
      return createError(ErrorCode.TIMEOUT_ERROR, '请求超时', undefined, error)
    }
    
    if (error.code === 'ERR_CANCELED') {
      return createError(ErrorCode.REQUEST_CANCELLED, '请求已取消', undefined, error)
    }

    return createError(ErrorCode.NETWORK_ERROR, error.message, undefined, error)
  }

  // 处理普通 Error
  if (error instanceof Error) {
    return createError(ErrorCode.UNKNOWN_ERROR, error.message, undefined, error)
  }

  // 处理字符串
  if (typeof error === 'string') {
    return createError(ErrorCode.UNKNOWN_ERROR, error)
  }

  // 未知错误
  return createError(ErrorCode.UNKNOWN_ERROR, '发生未知错误')
}

/**
 * 判断是否为 AppError
 */
export function isAppError(error: unknown): error is AppError {
  return (
    typeof error === 'object' &&
    error !== null &&
    'code' in error &&
    'message' in error &&
    'timestamp' in error
  )
}

/**
 * 判断是否为 Axios 错误
 */
function isAxiosError(error: unknown): error is any {
  return (
    typeof error === 'object' &&
    error !== null &&
    'isAxiosError' in error &&
    (error as any).isAxiosError === true
  )
}

/**
 * 格式化错误信息用于显示
 */
export function formatErrorMessage(error: AppError | string): string {
  if (typeof error === 'string') {
    return error
  }
  
  // 开发环境显示详细信息
  if (import.meta.env.DEV) {
    return `[${error.code}] ${error.message}`
  }
  
  // 生产环境显示友好信息
  return error.message
}

/**
 * 记录错误日志
 */
export function logError(
  error: AppError,
  context?: {
    action?: string
    component?: string
    userId?: string
  }
): void {
  const logData = {
    timestamp: error.timestamp,
    code: error.code,
    message: error.message,
    details: error.details,
    context,
    stack: error.cause?.stack
  }

  // 生产环境只记录错误
  if (import.meta.env.PROD) {
    console.error('[AppError]', logData)
  } else {
    // 开发环境记录详细日志
    console.error('[AppError]', logData)
    console.error('Stack:', error.cause?.stack)
  }
}

/**
 * 错误处理类
 * 提供链式错误处理能力
 */
export class ErrorHandler {
  private error: AppError

  constructor(error: unknown) {
    this.error = toAppError(error)
  }

  /**
   * 添加上下文信息
   */
  withContext(context: {
    action?: string
    component?: string
    userId?: string
  }): this {
    logError(this.error, context)
    return this
  }

  /**
   * 添加详细信息
   */
  withDetails(details: Record<string, unknown>): this {
    this.error.details = {
      ...this.error.details,
      ...details
    }
    return this
  }

  /**
   * 获取错误对象
   */
  getError(): AppError {
    return this.error
  }

  /**
   * 获取错误信息
   */
  getMessage(): string {
    return formatErrorMessage(this.error)
  }

  /**
   * 抛出错误
   */
  throw(): never {
    throw this.error
  }

  /**
   * 静默处理（只记录日志）
   */
  silent(): void {
    logError(this.error)
  }

  /**
   * 显示错误提示
   */
  async show(): Promise<void> {
    const message = this.getMessage()
    
    // 动态导入 ElMessage 避免循环依赖
    const { ElMessage } = await import('element-plus')
    ElMessage.error({
      message,
      duration: 3000,
      showClose: true
    })
  }
}

/**
 * 异步错误处理包装器
 */
export async function tryCatch<T>(
  promise: Promise<T>,
  fallback?: (error: AppError) => T | Promise<T>
): Promise<T | AppError> {
  try {
    return await promise
  } catch (error) {
    const appError = toAppError(error)
    
    if (fallback) {
      return fallback(appError)
    }
    
    return appError
  }
}
