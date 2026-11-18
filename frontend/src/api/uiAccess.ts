import request from '@/utils/request'
import type { RouterResponse } from '@/types'

/**
 * 控制台页面访问统计（B4，2026-11-18）。
 *
 * 后端两个端点：
 * - `POST /api/monitoring/ui-access`           任何已认证用户上报「我看了哪一页」
 * - `GET  /api/monitoring/ui-access/summary`   需要 `security:audit:read`，按页面 × 角色聚合
 *
 * 上报的三条纪律：
 * 1. **只报路径**：不带查询串、不带 hash —— 查询串里可能带敏感参数
 * 2. **按路径节流**：同一页面 60 秒内只报一次，避免在页面内点几下就写满审计表
 * 3. **永不影响用户**：任何失败静默吞掉（上报是观测行为，不是业务动作）
 */

/** 视角切换/表格翻页之类的同页交互不应重复计数 */
const THROTTLE_MS = 60_000

/** 每个角色只保留一条汇总行 */
export interface UiPageViewSummary {
  path: string
  role: string
  views: number
  users: number
}

const lastSentAt = new Map<string, number>()

/** 上报节流用的纯函数：返回 true 表示这次应当上报 */
export function shouldReport(path: string, now: number = Date.now()): boolean {
  const last = lastSentAt.get(path) ?? 0
  if (now - last < THROTTLE_MS) {
    return false
  }
  lastSentAt.set(path, now)
  return true
}

/** 测试用：清空节流记录 */
export function resetReportThrottle(): void {
  lastSentAt.clear()
}

/**
 * 把路由地址归一成可上报的页面标识。
 *
 * - 去掉查询串与 hash
 * - 去掉末尾多余的 `/`（`/call-history/list/` 与 `/call-history/list` 是同一页）
 * - 认证页本身不统计（它是登录过程，不是「看了哪一页」）
 */
export function normalizeReportPath(fullPath: string | undefined | null): string | null {
  if (!fullPath || !fullPath.startsWith('/')) {
    return null
  }
  const path = fullPath.split('?')[0].split('#')[0]
  if (path === '/login' || path === '/') {
    return null
  }
  return path.length > 1 && path.endsWith('/') ? path.slice(0, -1) : path
}

/**
 * 上报一次页面访问（节流 + 静默失败）。
 *
 * @param fullPath 路由的 fullPath（含查询串也没关系，内部会清洗）
 * @param role     当前角色名（后端存进审计 details，用于按角色聚合）
 */
export function reportPageView(fullPath: string | undefined | null, role: string | null | undefined): void {
  const path = normalizeReportPath(fullPath)
  if (!path || !shouldReport(path)) {
    return
  }
  void request
    .post<RouterResponse<void>>('/monitoring/ui-access', { path, role: role ?? null })
    .catch(() => {
      // 静默：上报失败不能打扰使用者
    })
}

/**
 * 页面访问热度聚合。
 *
 * @param days  统计窗口（天，后端夹到 1..90）
 * @param limit 返回行数上限（后端夹到 1..100）
 */
export async function getPageViewSummary(
  days = 7,
  limit = 50
): Promise<UiPageViewSummary[]> {
  const response = await request.get<RouterResponse<UiPageViewSummary[]>>(
    '/monitoring/ui-access/summary',
    { params: { days, limit } }
  )
  return response.data.data ?? []
}
