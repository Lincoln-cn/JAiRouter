/**
 * 通用类型定义
 * 用于减少 any 的使用，提高类型安全
 */

// 通用 API 错误响应
export interface ApiError {
  message: string
  code?: string
  details?: Record<string, unknown>
}

// 分页参数
export interface PaginationParams {
  page: number
  size: number
  sort?: string
  order?: 'asc' | 'desc'
}

// 分页响应
export interface PaginationResponse<T> {
  content: T[]
  total: number
  page: number
  size: number
  totalPages: number
}

// 服务实例类型
export interface ServiceInstance {
  instanceId: string
  name: string
  type: string
  adapter: string
  baseUrl: string
  weight: number
  headers?: Record<string, string>
  health?: boolean
  metadata?: Record<string, unknown>
}

// 服务配置类型
export interface ServiceConfig {
  type: string
  adapter: string
  loadBalanceType: string
  rateLimitType: string
  instances: ServiceInstance[]
  metadata?: Record<string, unknown>
}

// 监控数据类型
export interface MonitoringData {
  serviceType: string
  totalInstances: number
  healthyInstances: number
  healthRate: number
  requests?: {
    total: number
    success: number
    failed: number
    avgResponseTime: number
  }
}

// 追踪数据类型
export interface TraceData {
  traceId: string
  spanId: string
  operation: string
  duration: number
  status: 'success' | 'error'
  timestamp: string
  tags?: Record<string, string>
}

// 审计日志类型
export interface AuditLog {
  eventId: string
  eventType: string
  username: string
  action: string
  resource: string
  result: 'success' | 'failure'
  timestamp: string
  ipAddress?: string
  details?: Record<string, unknown>
}

// JWT Token 类型
export interface JwtPayload {
  sub: string
  roles: string[]
  permissions?: string[]
  exp: number
  iat: number
  iss: string
}

// 配置版本类型
export interface ConfigVersion {
  version: number
  configKey: string
  configValue: Record<string, unknown>
  isLatest: boolean
  createdAt: string
  updatedAt: string
}

// 通用表单字段类型
export interface FormField {
  key: string
  value: string
  label?: string
  required?: boolean
  disabled?: boolean
}

// 通用选项类型
export interface SelectOption {
  label: string
  value: string | number
  disabled?: boolean
}

// 表格列定义
export interface TableColumn {
  prop: string
  label: string
  width?: string | number
  sortable?: boolean
  formatter?: (row: Record<string, unknown>) => string
}
