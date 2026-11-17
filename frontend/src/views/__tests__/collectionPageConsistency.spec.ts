import { describe, expect, it } from 'vitest'
import { existsSync, readFileSync, readdirSync, statSync } from 'node:fs'
import { join } from 'node:path'

/**
 * 集合页一致性守卫（列表 + 增删改查）。
 *
 * <p>背景：2026-11-18 用户反馈「/admin/system/sla 和 /admin/system/accounts 风格都不一样」。
 * 当时 `system/SlaAccessManagement.vue` 是手写表格 + 两个 `el-dialog`，而
 * `security/JwtAccountManagement.vue` 已经是 `PageSkeleton` + `MetricStrip` + `EmptyState`
 * + 抽屉的形态。规范见 `innerdoc/02-架构与设计/列表页与混排页规范-执行版.md`。</p>
 *
 * <p>这是**源码级**断言：判定「集合页」的口径是「模板里有 `<el-table>` 且有 `#toolbar` 插槽」，
 * 配置页/观测页/子组件通过下面的白名单排除。它能拦住「新增列表页时忘了空态/外壳」这类回退，
 * 不校验像素。</p>
 */

const frontendRoot = ((): string => {
  const cwd = process.cwd()
  if (existsSync(join(cwd, 'src', 'views'))) {
    return cwd
  }
  return join(cwd, 'frontend')
})()

const viewsRoot = join(frontendRoot, 'src', 'views')

const walk = (dir: string, out: string[] = []): string[] => {
  for (const name of readdirSync(dir)) {
    const full = join(dir, name)
    if (statSync(full).isDirectory()) {
      if (name === '__tests__') {
        continue
      }
      walk(full, out)
      continue
    }
    if (name.endsWith('.vue')) {
      out.push(full)
    }
  }
  return out
}

const relative = (full: string): string => full.slice(viewsRoot.length + 1).split('\\').join('/')

const sources = walk(viewsRoot).map((full) => ({ path: relative(full), source: readFileSync(full, 'utf8') }))

/**
 * 不是集合页的例外：配置页里的只读表格、抽屉/表格类子组件、单页说明页。
 * 追加条目必须写明理由，否则下次重构会失去约束力。
 */
const NON_COLLECTION_PAGES = new Set([
  // 配置页（主体是参数，表格只是只读明细）
  'config/StatePersistenceManagement.vue',
  'security/SanitizationManagement.vue',
  'security/LoginGuardManagement.vue',
  'security/RecordGovernance.vue',
  'tracing/Management.vue',
  'system/ClusterConfig.vue',
  'system/HealthProbeConfig.vue',
  'load-balancer/StrategyConfig.vue',
  // 观测页（没有新增/编辑语义，走观测页要求）
  'callHistory/Dashboard.vue',
  'callHistory/TokenUsageStatistics.vue',
  'Dashboard.vue',
  'exception/ExceptionStatistics.vue',
  'tracing/Dashboard.vue',
  'tracing/Search.vue',
  'monitoring/QuotaUsageMonitoring.vue',
  'monitoring/SlowQueryAnalysis.vue',
  'rate-limiter/Monitoring.vue',
  'load-balancer/Monitoring.vue',
  'circuit-breaker/Monitoring.vue',
  'circuit-breaker/History.vue',
  // 子组件（被父页渲染，自身不是独立页面）
  'security/apikeys/ApiKeyTable.vue',
  'security/apikeys/QuotaAlertsCard.vue',
  'security/apikeys/ImportConfigDialog.vue',
  'tracing/components/TraceDetail.vue',
  'tools/ClientAccessGuide.vue',
])

const collectionPages = sources.filter(
  (page) =>
    /<el-table\b/.test(page.source) && /#toolbar/.test(page.source) && !NON_COLLECTION_PAGES.has(page.path),
)

/**
 * 允许保留 `el-dialog` 的集合页（每条都要写理由，否则下次重构会失去约束力）。
 *
 * <ul>
 *   <li>`security/BlacklistManagement.vue`：添加黑名单的「设备选择器」——
 *       它本身已经在一个编辑抽屉里，再叠一层右侧抽屉会互相遮挡，只能用居中弹窗。</li>
 * </ul>
 */
const DIALOG_ALLOWED = new Set(['security/BlacklistManagement.vue'])

describe('集合页一致性（列表页 8 条口径）', () => {
  it('扫描到的集合页数量合理（防止解析失效后静默通过）', () => {
    expect(sources.length).toBeGreaterThan(40)
    expect(collectionPages.length).toBeGreaterThanOrEqual(8)
  })

  it('每个集合页都用 PageSkeleton 作为外壳', () => {
    const offenders = collectionPages.filter((page) => !/<PageSkeleton\b/.test(page.source)).map((page) => page.path)
    expect(offenders).toEqual([])
  })

  it('每个集合页都用 MetricStrip 给统计卡', () => {
    const offenders = collectionPages.filter((page) => !/<MetricStrip\b/.test(page.source)).map((page) => page.path)
    expect(offenders).toEqual([])
  })

  it('每个集合页的表格空态都用 EmptyState（禁止裸 el-empty / 自绘空态）', () => {
    const offenders = collectionPages.filter((page) => !/<EmptyState\b/.test(page.source)).map((page) => page.path)
    expect(offenders).toEqual([])
  })

  it('集合页不得用 ElMessageBox 做确认（统一 ConfirmAction）', () => {
    const offenders = collectionPages.filter((page) => /ElMessageBox/.test(page.source)).map((page) => page.path)
    expect(offenders).toEqual([])
  })

  it('集合页的详情不得用 el-dialog（统一 DetailDrawer）', () => {
    // 一次性结果展示（如 API Key 明文）允许保留 el-dialog，但必须写注释说明；
    // 这里只拦「既没有 DetailDrawer 又直接开 el-dialog」的页面。
    const offenders = collectionPages
      .filter((page) => /<el-dialog\b/.test(page.source) && !/<DetailDrawer\b/.test(page.source))
      .filter((page) => !DIALOG_ALLOWED.has(page.path))
      .map((page) => page.path)
    expect(offenders).toEqual([])
  })
})
