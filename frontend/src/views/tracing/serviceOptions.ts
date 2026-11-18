/**
 * 把 `GET /tracing/query/services` 的两种返回形态归一成下拉选项。
 *
 * 后端 `/services` 返回的是**对象数组**，服务名在 `name` 字段（`TracingQueryController`
 * 的 `serviceStatistics` 用 `service.get("name")` 也是这个口径）；而前端类型早期按
 * `serviceName` 写的。两者都收，按「先 `serviceName` 再 `name`」取，避免任何一侧改名
 * 就把下拉变成一排空选项（曾经的真实缺陷：下拉里两个空串，点了也选不中）。
 *
 * 第三种形态 `{ services: string[] }` 也兼容（`TracingServiceListResult`）。
 * 结果会去空、去重、按字典序排；**返回空数组表示「没有可用服务」**，由调用方决定兜底。
 */
export function normalizeServiceOptions(data: unknown): string[] {
  const names: string[] = []

  const pushName = (value: unknown): void => {
    if (typeof value === 'string' && value.trim()) {
      names.push(value.trim())
    }
  }

  if (Array.isArray(data)) {
    for (const item of data) {
      if (typeof item === 'string') {
        pushName(item)
        continue
      }
      if (item && typeof item === 'object') {
        const record = item as Record<string, unknown>
        pushName(record.serviceName ?? record.name)
      }
    }
  } else if (data && typeof data === 'object') {
    const services = (data as Record<string, unknown>).services
    if (Array.isArray(services)) {
      for (const service of services) {
        if (typeof service === 'string') {
          pushName(service)
        } else if (service && typeof service === 'object') {
          const record = service as Record<string, unknown>
          pushName(record.serviceName ?? record.name)
        }
      }
    }
  }

  return [...new Set(names)].sort((left, right) => left.localeCompare(right))
}

/** 取不到服务列表时的兜底：至少让值班的人能选中一个已知服务名。 */
export const FALLBACK_SERVICE_OPTIONS: readonly string[] = [
  'jairouter',
  'model-adapter-service',
  'load-balancer-service',
  'rate-limiter-service'
]
