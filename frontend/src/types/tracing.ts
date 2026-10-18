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

/**
 * 单条链路的 span（#522：形状取自 `TraceDetail.vue` / `Search.vue` 的实际读取 ——
 * `startTime` 是 ISO 字符串、`operationName` 是展示名、`error` 决定甘特条颜色）。
 * 结构随 OpenTelemetry 数据变化，故保留索引签名。
 */
export interface TraceSpan {
  traceId?: string
  spanId?: string
  parentSpanId?: string
  /** 展示用操作名（如 `HTTP GET /v1/chat/completions`） */
  operationName?: string
  serviceName?: string
  /** ISO 时间字符串 */
  startTime?: string
  /** 毫秒 */
  duration?: number
  error?: boolean
  /** 列表行的是否错误标记（`/tracing/query/search` 的 `hasError`） */
  hasError?: boolean
  /** 列表行的 span 数（详情头部在拿不到 stats 时回落到它） */
  spanCount?: number
  status?: string
  /** span 标签 / 属性，值类型不定，展示时再格式化 */
  attributes?: Record<string, unknown>
  tags?: Record<string, unknown>
  [key: string]: unknown
}

/** 链路详情里由后端算好的汇总（`Search.vue` 的详情头部直接展示这四个数） */
export interface TraceChainStats {
  totalDuration?: number
  totalSpans?: number
  errorCount?: number
  avgDuration?: number
  maxDuration?: number
}

/**
 * `GET /tracing/query/trace/{traceId}` 的载荷（#522 修正）。
 *
 * ⚠️ `#520` 里把它声明成了「span 数组」，但两个消费方都读 `data.spans` / `data.stats`
 * ⇒ 真实形状是对象。这里改正，否则类型对真实字段毫无约束（正是本轴要消除的情况）。
 */
export interface TraceChainResponse {
  traceId?: string
  /** 链路所属服务名（详情头部展示） */
  serviceName?: string
  /** 链路开始时间（ISO 字符串） */
  startTime?: string
  spans?: TraceSpan[]
  stats?: TraceChainStats
}

/** `GET /tracing/query/services` 的单个服务统计（页面只读服务名与计数类字段） */
export interface TracingServiceStat {
  serviceName?: string
  traceCount?: number
  errorCount?: number
  /** 错误率（百分比，>5 视为异常） */
  errorRate?: number
  /** 平均耗时（毫秒，>1000 视为慢） */
  avgDuration?: number
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
  traces?: TraceSpan[]
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
