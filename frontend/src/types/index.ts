// Common type definitions for the admin interface

export interface ApiResponse<T = any> {
  success: boolean
  data?: T
  message?: string
  error?: string
}

export interface RouterResponse<T = any> {
  success: boolean
  message: string
  data?: T
  errorCode?: string
  timestamp: string
}

export interface SystemStatus {
  uptime: number
  version: string
  environment: string
}

export interface DashboardOverview {
  systemStatus: SystemStatus
  serviceStats: {
    totalServices: number
    activeServices: number
    failedServices: number
  }
  requestStats: {
    totalRequests: number
    requestsPerSecond: number
    averageResponseTime: number
    errorRate: number
  }
  resourceUsage: {
    cpuUsage: number
    memoryUsage: number
    diskUsage: number
  }
}

// Config Merge Management Types
export interface VersionFile {
  version: number
  filePath: string
}

export interface Statistics {
  totalVersionFiles?: number
  oldestVersion?: number | null
  newestVersion?: number | null
  previewAvailable?: boolean
}

export interface ServiceStatus {
  availableVersionFiles?: number
  configDirectory?: string
  serviceReady?: boolean
}

export interface MergePreviewData {
  mergedConfig?: Record<string, any>
  mergeStatistics?: {
    sourceVersionCount?: number
    totalServiceTypes?: number
    totalSourceInstances?: number
    mergedInstances?: number
    instanceReduction?: number
    mergedServiceTypes?: number
  }
}

export interface MergeResult {
  conflicts?: string[]
  warnings?: string[]
  success: boolean
  message: string
}

// API Key Management Types
export interface ApiKeyInfo {
  keyId: string
  description: string
  createdAt: string
  expiresAt: string
  enabled: boolean
  permissions: string[]
  expired?: boolean
}

export interface ApiKeyCreationResponse {
  keyId: string
  keyValue: string
  description: string
  createdAt: string
}

// API Key Management Request Types
export interface CreateApiKeyRequest {
  keyId?: string
  description: string
  permissions: string[]
  enabled?: boolean
  expiresAt?: string
}

export interface UpdateApiKeyRequest {
  description?: string
  permissions?: string[]
  enabled?: boolean
  expiresAt?: string
}
