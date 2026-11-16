/**
 * 运行时与应急页的「影响面摘要」提取（#1172）.
 *
 * <p>这些应急操作（清缓存 / 重置错误 / 强开熔断 / 强制恢复）一旦点错，代价是采集中断或
 * 指标丢失，而原来的确认气泡只有一句「确认…？」，操作者看不到影响面。这里把各接口返回的
 * 只读状态里能说出「影响多少」的字段摘出来，交给页面拼进确认文案。</p>
 *
 * <p>之所以做成纯函数而不是在组件里就地取值：这些字段来自四个不同组件的响应（缓存、
 * 错误处理、熔断、降级策略），字段名不统一（`size` vs `cacheSize`、`state` vs `currentState`），
 * 需要一层兼容与兜底；纯函数可以直接断言「字段缺失时返回 null，由页面退回通用文案」，
 * 不必把组件挂起来测。</p>
 *
 * <p>约束：只做「读」，任何函数都不修改入参；字段缺失或类型不符一律返回 `null`，
 * 绝不猜一个数字（确认气泡里出现错误的数字比没有数字更糟）。</p>
 */

/** 运行时快照：键名与 `MonitoringRuntime.vue` 里的 ref 一致，缺省允许为 undefined。 */
export interface RuntimeSnapshot {
  degradation?: Record<string, unknown> | null
  errorStats?: Record<string, unknown> | null
  cacheStats?: Record<string, unknown> | null
  pipelineBreakerStats?: Record<string, unknown> | null
  monitoringBreakerStats?: Record<string, unknown> | null
}

const readNumber = (source: Record<string, unknown> | null | undefined, key: string): number | null => {
  const value = source?.[key]
  return typeof value === 'number' && Number.isFinite(value) ? value : null
}

const readText = (source: Record<string, unknown> | null | undefined, key: string): string | null => {
  const value = source?.[key]
  return typeof value === 'string' && value.trim().length > 0 ? value : null
}

const countKeys = (value: unknown): number | null => {
  if (value === null || value === undefined || typeof value !== 'object' || Array.isArray(value)) {
    return null
  }
  return Object.keys(value as Record<string, unknown>).length
}

/**
 * 缓存里当前有多少条指标项（清缓存的直接影响面）。
 *
 * <p>后端 `CacheStats` 历史上用过 `size`，新版本用 `cacheSize`，两种都认；都没有时返回 `null`。
 */
export function readCacheEntries(stats: RuntimeSnapshot['cacheStats']): number | null {
  return readNumber(stats, 'cacheSize') ?? readNumber(stats, 'size')
}

/**
 * 错误统计里被记录的键数量（`errors` 或 `errorCounts` 的键数）。
 *
 * <p>不做「对象有几个字段」的兜底：`errorStats` 顶层还混有 `totalErrors` 之类的元数据字段，
 * 按顶层键数算会把它们算成「错误项」，宁可返回 `null` 让页面用通用文案。
 */
export function countErrorEntries(stats: RuntimeSnapshot['errorStats']): number | null {
  return countKeys(stats?.['errors']) ?? countKeys(stats?.['errorCounts'])
}

/** 熔断当前状态（`state` 或 `currentState`），用于「强开/强关」前展示现状。 */
export function readBreakerState(stats: RuntimeSnapshot['pipelineBreakerStats']): string | null {
  return readText(stats, 'state') ?? readText(stats, 'currentState')
}

/** 当前降级级别，用于「设置级别」与「强制恢复」前展示现状。 */
export function readDegradationLevel(stats: RuntimeSnapshot['degradation']): string | null {
  return readText(stats, 'level')
}
