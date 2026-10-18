/**
 * 链路追踪（tracing）接口的响应载荷类型（#520）。
 *
 * 来源：这些形状不是从后端 DTO 抄来的，而是**逐字段取自消费方的实际读取**
 * （`views/tracing/Dashboard.vue`、`views/tracing/Management.vue`、`Search.vue`、`TraceDetail.vue`）。
 * 只声明前端真正读的字段，其余一律不写；后端新增字段不影响这里。
 */

/** `GET /tracing/query/statistics` 概览卡片用到的四个数 */
export interface TracingStatistics {
  totalTraces?: number
  errorTraces?: number
  avgDuration?: number
  samplingRate?: number
}

/** `GET /tracing/actuator/status` */
export interface TracingStatus {
  enabled?: boolean
  serviceName?: string
  serviceVersion?: string
  openTelemetry?: {
    enabled?: boolean
  }
  exporter?: {
    type?: string
  }
  sampling?: {
    globalRatio?: number
  }
}

/** `GET /tracing/actuator/health` */
export interface TracingHealth {
  status?: string
  /** 0..1 的比例，页面乘以 100 显示为百分比 */
  memoryUsage?: number
  activeTraces?: number
  /** 0..1 的比例，页面乘以 100 显示为百分比 */
  bufferUsage?: number
}

/** `GET/PUT /tracing/actuator/config` 的采样段 */
export interface TracingSamplingConfig {
  strategy?: string
  /** 全局采样率 0..1 */
  ratio?: number
  adaptive?: {
    enabled?: boolean
  }
  alwaysSample?: string[]
  neverSample?: string[]
  /** 服务名 → 采样率（0..1） */
  serviceRatios?: Record<string, number>
  /** 页面会把这一整段暂存起来原样回传，故允许其余字段透传 */
  [key: string]: unknown
}

/** `GET/PUT /tracing/actuator/config` 的导出段 */
export interface TracingExporterConfig {
  type?: string
  logging?: {
    enabled?: boolean
  }
}

/** `GET/PUT /tracing/actuator/config` 的性能段 */
export interface TracingPerformanceConfig {
  asyncProcessing?: boolean
  threadPool?: {
    coreSize?: number
  }
  buffer?: {
    size?: number
  }
  memory?: {
    memoryLimitMb?: number
  }
}

/** `GET/PUT /tracing/actuator/config` 的整份配置 */
export interface TracingConfig {
  sampling?: TracingSamplingConfig
  exporter?: TracingExporterConfig
  performance?: TracingPerformanceConfig
}

/** 单条链路的详情节点（页面按 span 树渲染，结构随 OpenTelemetry 数据变化） */
export interface TraceChainNode {
  traceId?: string
  spanId?: string
  parentSpanId?: string
  name?: string
  duration?: number
  status?: string
  [key: string]: unknown
}

/** `GET /tracing/query/services` 的单个服务统计（页面只读服务名与计数类字段） */
export interface TracingServiceStat {
  serviceName?: string
  traceCount?: number
  errorCount?: number
  [key: string]: unknown
}

/**
 * `GET /tracing/query/services` 的另一种返回形态：`{ services: string[] }`。
 * `Search.vue` 的「可用服务」下拉对两种形态都做了兼容，故这里显式声明（#520）。
 */
export interface TracingServiceListResult {
  services?: string[]
}

/**
 * `GET /tracing/query/search` 的分页形态（页面同时兼容「裸数组」旧格式，见 searchTraces 的返回联合类型）。
 */
export interface TraceSearchResult {
  traces?: TraceChainNode[]
  total?: number
  totalPages?: number
}

/** `POST /tracing/query/cleanup` 的返回：清理了多少条 */
export interface TracingCleanupResult {
  removedCount?: number
  [key: string]: unknown
}

/**
 * `POST /tracing/query/export` 的入参（取自 `Search.vue` 实际下发的键）。
 * 它是「按当前查询条件导出」而不是「按 traceId 列表导出」，故没有 traceIds。
 */
export interface TraceExportRequest {
  /** ISO 时间；未选时间范围时页面传 null */
  startTime: string | null
  endTime: string | null
  /** 导出格式（当前只有 json） */
  format: string
  /** 最多导出多少条（页面传当前查询命中的总数） */
  maxRecords: number
}
