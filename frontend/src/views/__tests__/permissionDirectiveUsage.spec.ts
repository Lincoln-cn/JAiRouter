import { existsSync, readdirSync, readFileSync } from 'node:fs'
import { join, resolve } from 'node:path'
import { describe, expect, it } from 'vitest'
import { KNOWN_PERMISSION_CODES } from '@/config/__tests__/knownPermissions'

/**
 * 页面按钮权限门控的静态守卫（2026-11-16）
 *
 * 背景：`v-permission` 指令的单元测试只能证明「指令本身会按码隐藏元素」，证明不了
 * 「写操作按钮上到底有没有挂这条指令」，也证明不了「挂的码对不对」。当时的现状是
 * `ServiceManagement.vue` 5 处按钮挂成了 `config:instances:write`（实例页的写码），
 * 而响应缓存 / 熔断全局配置 / 负载均衡策略三个页面一个门控都没有。
 *
 * 这里不起 DOM 直接扫源码，成本低且能覆盖全部页面：
 *  - 所有 `v-permission` 里写的权限码必须存在于白名单（拼错码 = 按钮永远不显示）；
 *  - 关键写操作页面必须带上自己的写权限码（回归清单，逐条列出）。
 *
 * 局限：静态扫描判断不了「这个码在语义上属于这个页面」。语义错配由下面的具名断言
 * （例如服务管理页不得出现实例写码）兜住；新增页面仍建议在页面级测试里断言按钮可见性。
 *
 * 路径定位用 `process.cwd()`：happy-dom 环境下 `import.meta.url` 的 scheme 不是 `file`，
 * `fileURLToPath` 会抛「The URL must be of scheme file」。
 */
const frontendRoot = [process.cwd(), resolve(process.cwd(), 'frontend')].find(candidate =>
  existsSync(join(candidate, 'src', 'views'))
)
if (!frontendRoot) {
  throw new Error('无法定位 frontend 根目录：请在 frontend/ 目录下运行测试')
}
const viewsDir = join(frontendRoot, 'src', 'views')

function collectVueFiles(dir: string): string[] {
  return readdirSync(dir, { withFileTypes: true }).flatMap(entry => {
    const full = join(dir, entry.name)
    if (entry.isDirectory()) return collectVueFiles(full)
    return entry.name.endsWith('.vue') ? [full] : []
  })
}

/** 从 `v-permission="'a'"` / `v-permission="['a', 'b']"` 里取出字面量权限码 */
function collectDeclaredCodes(source: string): string[] {
  const codes: string[] = []
  for (const directive of source.matchAll(/v-permission\s*=\s*"([^"]*)"/g)) {
    const expression = directive[1]
    for (const literal of expression.matchAll(/'([^']+)'/g)) {
      codes.push(literal[1])
    }
  }
  return codes
}

const pages = collectVueFiles(viewsDir).map(file => ({
  file,
  name: file.slice(viewsDir.length + 1).replace(/\\/g, '/'),
  source: readFileSync(file, 'utf-8')
}))

const countOccurrences = (source: string, needle: string): number =>
  source.split(needle).length - 1

/**
 * 从 `src/api/*.ts` 里认出「会发写请求」的导出函数名。
 *
 * 页面上的写操作有两类调用形态：`import { updateXxx } from '@/api/xxx'` 与直接
 * `request.delete(...)`。前者要靠这一步把函数名映射回「是不是写」，否则只能靠人记。
 */
function collectWriteApiNames(): Set<string> {  const names = new Set<string>()
  const apiDir = join(frontendRoot!, 'src', 'api')
  for (const file of readdirSync(apiDir).filter(name => name.endsWith('.ts'))) {
    const source = readFileSync(join(apiDir, file), 'utf-8')
    const matcher = /\nexport\s+(?:async\s+)?(?:const|function)\s+([A-Za-z0-9_]+)([\s\S]*?)(?=\nexport\s|$)/g
    for (const match of source.matchAll(matcher)) {
      const body = match[2] ?? ''
      // 泛型写法（`request.post<RouterResponse<void>>(...)`）里方法名后面不是 `(`，
      // 所以这里只要求方法名整体出现，不能再紧跟 `\s*\(`
      if (/\.(post|put|delete|patch)\b|method:\s*['"](POST|PUT|DELETE|PATCH)/.test(body)) {
        names.add(match[1])
      }
    }
  }
  return names
}

/**
 * POST 也常用于「查询 / 导出 / 试跑」这类只读语义（如 queryAuditEventsAdvanced 用 POST 传查询体），
 * 只看 HTTP 方法会误报，所以按命名前缀把只读语义摘出去。
 */
const QUERY_LIKE_PREFIX = /^(query|search|fetch|export|test|preview|validate|get)/i

/** 弹窗/向导/面板这类嵌套组件由已门控的父页面打开，自身不重复门控 */
const NESTED_COMPONENTS = new Set([
  'config/adapter/AdapterTestPanel.vue',
  'config/adapter/AdapterWizard.vue',
  'config/rules/RuleFormDialog.vue',
  'config/rules/RuleTemplateDialog.vue',
  'security/apikeys/ImportConfigDialog.vue'
])

/**
 * 原先这里列着 `/security` 下的四页，理由是父路由 `meta.roles: ['ADMIN']` 挡住非 ADMIN、
 * 而 ADMIN 恒持 manage 码，所以按钮门控没有实际差别。
 * 2026-11-16 第五轮撤掉了父路由角色门控（权限码成为唯一门控）⇒ 该理由不再成立，
 * 这些页的写按钮必须自己挂码；白名单清空后仍保留结构，方便将来再有同类豁免时登记。
 */
const ADMIN_ONLY_PAGES = new Set<string>([])

describe('页面按钮权限门控', () => {
  it('扫描面自证：views 下确实扫到了 .vue 与 v-permission 用法（防扫描目录失效后空转）', () => {
    expect(pages.length).toBeGreaterThan(40)
    const totalUsage = pages.reduce((total, page) => total + countOccurrences(page.source, 'v-permission'), 0)
    expect(totalUsage).toBeGreaterThan(10)
  })

  it('v-permission 里写的权限码全部在白名单内', () => {
    const unknown = pages.flatMap(page =>
      collectDeclaredCodes(page.source)
        .filter(code => !KNOWN_PERMISSION_CODES.includes(code))
        .map(code => `${page.name}: ${code}`)
    )
    expect(unknown).toEqual([])
  })

  it('服务管理页用服务写码，不得再出现实例写码（2026-11-16 错码回归）', () => {
    const page = pages.find(candidate => candidate.name === 'config/ServiceManagement.vue')
    expect(page).toBeTruthy()
    expect(countOccurrences(page!.source, 'config:instances:write')).toBe(0)
    expect(countOccurrences(page!.source, 'config:services:write')).toBeGreaterThanOrEqual(5)
  })

  it('响应缓存管理页的四个写操作都挂了 config:cache:write', () => {
    const page = pages.find(candidate => candidate.name === 'config/ResponseCacheManagement.vue')
    expect(page).toBeTruthy()
    expect(countOccurrences(page!.source, 'config:cache:write')).toBeGreaterThanOrEqual(4)
  })

  it('熔断全局配置页的保存与恢复默认挂了 config:circuitbreaker:write', () => {
    const page = pages.find(candidate => candidate.name === 'circuit-breaker/GlobalConfig.vue')
    expect(page).toBeTruthy()
    expect(countOccurrences(page!.source, 'config:circuitbreaker:write')).toBeGreaterThanOrEqual(2)
  })

  it('负载均衡策略配置页的配置入口与保存挂了 lb:config:write', () => {
    const page = pages.find(candidate => candidate.name === 'load-balancer/StrategyConfig.vue')
    expect(page).toBeTruthy()
    expect(countOccurrences(page!.source, 'lb:config:write')).toBeGreaterThanOrEqual(2)
  })

  it('其它已门控页面保持门控（回归清单）', () => {
    const expected: Array<[string, string]> = [
      ['config/InstanceManagement.vue', 'config:instances:write'],
      ['config/AdapterManagement.vue', 'config:adapters:write'],
      ['config/StatePersistenceManagement.vue', 'config:persistence:write'],
      ['config/rules/RuleManagement.vue', 'config:rules:write'],
      ['config/pools/PoolManagement.vue', 'config:pools:write'],
      ['config/VersionManagement.vue', 'config:versions:write'],
      ['config/QuotaConfigManagement.vue', 'config:quota:write'],
      ['system/HealthProbeConfig.vue', 'config:health:write'],
      ['system/ClusterConfig.vue', 'config:cluster:write'],
      ['rate-limiter/Config.vue', 'config:ratelimit:write'],
      ['exception/ExceptionManagement.vue', 'monitoring:exceptions:write'],
      // 2026-11-16 第二轮补充：这些页的写操作原先对只读角色也可见，点击才 403
      ['circuit-breaker/History.vue', 'config:circuitbreaker:write'],
      ['tracing/Management.vue', 'tracing:config:manage'],
      ['system/PermissionManagement.vue', 'system:permissions:manage'],
      // 用 computed canWrite 收敛，不走 v-permission：写码出现一次即可
      ['load-balancer/Monitoring.vue', 'lb:config:write'],
      // 2026-11-16 第三轮：写 API 扫描发现这两页会调用 updateCallHistoryConfig，
      // 但保存按钮对只持 read 的角色（USER/OPERATOR/VIEWER 都能进记录治理页）仍可见
      ['security/RecordGovernance.vue', 'config:callhistory:write'],
      ['callHistory/CallHistoryList.vue', 'config:callhistory:write'],
      // 2026-11-16 第四轮：撤掉 /security、/system 父路由的角色门控后，这两组页面对
      // 「只勾了 read 码的自定义角色」可达，写按钮必须自己挂码，否则点击才 403
      ['security/ApiKeyManagement.vue', 'security:apikeys:manage'],
      ['security/apikeys/ApiKeyTable.vue', 'security:apikeys:manage'],
      ['security/apikeys/ApiKeyCards.vue', 'security:apikeys:manage'],
      ['security/apikeys/QuotaDrawer.vue', 'security:apikeys:manage'],
      ['security/SanitizationManagement.vue', 'security:sanitization:manage'],
      ['security/JwtTokenManagement.vue', 'security:jwttokens:manage'],
      ['security/BlacklistManagement.vue', 'security:blacklist:manage'],
      ['security/LoginGuardManagement.vue', 'security:loginguard:manage'],
      ['security/JwtAccountManagement.vue', 'system:accounts:manage']
    ]
    const missing = expected
      .filter(([name, code]) => {
        const page = pages.find(candidate => candidate.name === name)
        return !page || countOccurrences(page.source, code) === 0
      })
      .map(([name, code]) => `${name} 缺少 ${code}`)
    expect(missing).toEqual([])
  })

  it('调用写接口的页面必须挂门控（含直接 request.delete 这类写法）', () => {
    // POST 也常用于查询/导出/试跑这三类只读语义（如 queryAuditEventsAdvanced 用 POST 传查询体），
    // 只看 HTTP 方法会误报，所以按命名前缀把只读语义摘出去
    const writeApis = [...collectWriteApiNames()].filter(name => !QUERY_LIKE_PREFIX.test(name))
    expect(writeApis.length, '写接口集合异常小：api 模块解析可能失效').toBeGreaterThan(5)

    // 白名单自身要能对上真实文件，否则改了名字以后白名单会静默失效
    const whitelisted = [...NESTED_COMPONENTS, ...ADMIN_ONLY_PAGES]
    expect(whitelisted.filter(name => !pages.some(page => page.name === name))).toEqual([])

    const GATED = /v-permission|hasPermission|canWrite|canEdit|canManage|canReset/
    const DIRECT_WRITE = /request\.(post|put|delete|patch)\b/

    const ungated = pages
      .filter(page => !NESTED_COMPONENTS.has(page.name) && !ADMIN_ONLY_PAGES.has(page.name))
      .filter(page => {
        const callsWriteApi = writeApis.some(name => new RegExp(`\\b${name}\\s*\\(`).test(page.source))
        return callsWriteApi || DIRECT_WRITE.test(page.source)
      })
      .filter(page => !GATED.test(page.source))
      .map(page => page.name)

    expect(
      ungated,
      '这些页面会发写请求却没有门控：只读角色能看到按钮，点了才 403'
    ).toEqual([])
  })
})
