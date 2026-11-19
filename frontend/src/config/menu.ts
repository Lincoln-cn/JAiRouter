/**
 * 控制台菜单结构（单一数据源）。
 *
 * 设计规则（2026-11-18 信息架构治理）：
 * - 一个菜单项 = 一个对象；同一对象的不同视图用页内分段（放在内容区顶部），URL 各自独立；
 * - 每组 ≤8 项（menu.spec.ts 有预算测试守住）；
 * - audience 与 permission 是「且」关系：角色不对或权限码不足都不显示；
 * - audience 取值 admin / maintainer / user / observer / auditor，与后端角色模板一一对应；
 * - 新增能力时先问：是新对象（加项，需过预算）还是新视图（加分段，不加项）。
 *
 * 权限语义：
 * - permission 缺省 = 已登录可见；数组 = 任一命中即可；
 * - tab.permission 缺省沿用菜单项；
 * - 角色（roles）与路由守卫同口径，见 usePermission.hasRouteRoles。
 */

export type Audience = 'admin' | 'maintainer' | 'user' | 'observer' | 'auditor'

export interface MenuTab {
  key: string
  labelKey: string
  to: string
  permission?: string | string[]
  roles?: string[]
  /** 视图级 audience（缺省沿用菜单项）。 */
  audience?: Audience[]
}

export interface MenuItem {
  path: string
  title: string
  icon?: string
  permission?: string | string[]
  roles?: string[]
  /** 哪些 audience 可见（与 permission 取交集）。 */
  audience?: Audience[]
  /** 对象标识，用于文档与访问统计归档。 */
  object?: string
  /** 组内小节标题（项多到需要分节的组才用）。 */
  section?: string
  /** 视图列表：长度 1 即单页，>1 时页面内用分段控件切换。 */
  tabs: MenuTab[]
}

export interface MenuGroup {
  index: string
  title: string
  icon: string
  children: MenuItem[]
}

export const menuGroups: MenuGroup[] = [
  {
    index: 'dashboard',
    title: 'menu.dashboard',
    icon: 'house',
    children: [
      {
        // 五个角色各有自己的落地页：同一组里互斥出现，任何角色只看到一项。
        // AUDITOR 不在这里 —— 它的首页是「运行观测 → 审计」，不给概览位。
        path: '/dashboard/main',
        title: 'menu.dashboardMain',
        object: 'overview',
        permission: 'overview:dashboard:read',
        audience: ['admin'],
        tabs: [
          { key: 'dashboard-main', labelKey: 'menu.dashboardMain', to: '/dashboard/main' },
        ],
      },
      {
        path: '/dashboard/operations',
        title: 'menu.dashboardOps',
        object: 'overview',
        permission: 'overview:dashboard:read',
        audience: ['maintainer'],
        tabs: [
          { key: 'dashboard-operations', labelKey: 'menu.dashboardOps', to: '/dashboard/operations' },
        ],
      },
      {
        path: '/dashboard/mine',
        title: 'menu.dashboardMine',
        object: 'overview',
        permission: ['callhistory:self:read', 'callhistory:read'],
        audience: ['user'],
        tabs: [
          { key: 'dashboard-mine', labelKey: 'menu.dashboardMine', to: '/dashboard/mine' },
        ],
      },
      {
        path: '/dashboard/trends',
        title: 'menu.dashboardTrends',
        object: 'overview',
        permission: 'overview:dashboard:read',
        audience: ['observer'],
        tabs: [
          { key: 'dashboard-trends', labelKey: 'menu.dashboardTrends', to: '/dashboard/trends' },
        ],
      },
    ],
  },
  {
    index: 'observability',
    title: 'menu.observability',
    icon: 'data-analysis',
    children: [
      {
        path: '/call-history/list',
        title: 'menu.callRecords',
        object: 'call',
        section: 'menu.sections.calls',
        // 或语义：运维/观察者持全局读码，普通用户持本人读码（服务端按 userId 与密钥并集过滤）
        permission: ['callhistory:read', 'callhistory:self:read'],
        audience: ['admin', 'maintainer', 'user', 'observer'],
        tabs: [
          { key: 'call-history-list', labelKey: 'menu.callRecords', to: '/call-history/list' },
          { key: 'call-history-dashboard', labelKey: 'menu.callStats', to: '/call-history/dashboard' },
        ],
      },
      {
        path: '/call-history/slow-calls',
        title: 'menu.slowRequests',
        object: 'slow',
        section: 'menu.sections.calls',
        permission: 'monitoring:slowquery:read',
        audience: ['admin', 'maintainer', 'user', 'observer'],
        tabs: [
          { key: 'call-history-slow-calls', labelKey: 'menu.slowCalls', to: '/call-history/slow-calls' },
          { key: 'monitoring-slow-queries', labelKey: 'menu.slowSql', to: '/monitoring/slow-queries' },
        ],
      },
      {
        path: '/tracing/search',
        title: 'menu.tracing',
        object: 'trace',
        section: 'menu.sections.tracing',
        permission: 'tracing:search:read',
        audience: ['admin', 'maintainer', 'user', 'observer'],
        tabs: [
          { key: 'tracing-search', labelKey: 'menu.tracingSearch', to: '/tracing/search' },
          { key: 'tracing-dashboard', labelKey: 'menu.tracingStats', to: '/tracing/dashboard', permission: 'tracing:dashboard:read' },
        ],
      },
      {
        path: '/exceptions/list',
        title: 'menu.exceptions',
        object: 'exception',
        section: 'menu.sections.health',
        // #1189 之后异常事件带归属两列（user_id / api_key_id），
        // 持「本人读」码的账号也能进，后端按 DataScope 只返回自己的异常。
        permission: ['monitoring:exceptions:read', 'monitoring:exceptions:self:read'],
        audience: ['admin', 'maintainer', 'observer', 'user'],
        tabs: [
          { key: 'exceptions-list', labelKey: 'menu.exceptionList', to: '/exceptions/list' },
          { key: 'exceptions-statistics', labelKey: 'menu.exceptionStats', to: '/exceptions/statistics' },
        ],
      },
      {
        path: '/security/audit-logs',
        title: 'menu.audit',
        object: 'audit',
        section: 'menu.sections.health',
        permission: 'security:audit:read',
        audience: ['admin', 'maintainer', 'observer', 'auditor'],
        tabs: [
          { key: 'security-audit-logs', labelKey: 'menu.auditLogs', to: '/security/audit-logs' },
          { key: 'system-console-operations', labelKey: 'menu.consoleOperations', to: '/system/console-operations', permission: ['monitoring:console:read'] },
        ],
      },
      {
        path: '/call-history/token-usage',
        title: 'menu.usage',
        object: 'usage',
        section: 'menu.sections.usage',
        // 全局码看全量，本人读码只看自己（后端按 DataScope 过滤，见 #1185）
        permission: ['monitoring:tokenusage:read', 'monitoring:tokenusage:self:read'],
        audience: ['admin', 'maintainer', 'user', 'observer'],
        tabs: [
          { key: 'call-history-token-usage', labelKey: 'menu.tokenUsage', to: '/call-history/token-usage' },
          { key: 'monitoring-quota', labelKey: 'menu.quotaUsage', to: '/monitoring/quota', permission: 'monitoring:quota:read' },
        ],
      },
    ],
  },
  {
    index: 'traffic',
    title: 'menu.traffic',
    icon: 'connection',
    children: [
      {
        path: '/load-balancers/monitoring',
        title: 'menu.loadBalancing',
        object: 'lb',
        section: 'menu.sections.monitor',
        permission: 'lb:monitoring:read',
        audience: ['admin', 'maintainer', 'user', 'observer'],
        tabs: [
          { key: 'load-balancers-monitoring', labelKey: 'menu.lbMonitoring', to: '/load-balancers/monitoring' },
          { key: 'load-balancers-strategy-config', labelKey: 'menu.lbStrategy', to: '/load-balancers/strategy-config', permission: ['lb:config:read', 'lb:config:write'] },
        ],
      },
      {
        path: '/rate-limiters/monitoring',
        title: 'menu.rateLimiting',
        object: 'ratelimit',
        section: 'menu.sections.monitor',
        permission: 'rl:monitoring:read',
        audience: ['admin', 'maintainer', 'user', 'observer'],
        tabs: [
          { key: 'rate-limiters-monitoring', labelKey: 'menu.rlMonitoring', to: '/rate-limiters/monitoring' },
          { key: 'rate-limiters-config', labelKey: 'menu.rateLimitingConfig', to: '/rate-limiters/config', permission: 'config:ratelimit:read' },
        ],
      },
      {
        path: '/circuit-breakers/monitoring',
        title: 'menu.circuitBreaking',
        object: 'circuitbreaker',
        section: 'menu.sections.monitor',
        permission: 'cb:monitoring:read',
        audience: ['admin', 'maintainer', 'user', 'observer'],
        tabs: [
          { key: 'circuit-breakers-monitoring', labelKey: 'menu.cbMonitoring', to: '/circuit-breakers/monitoring' },
          { key: 'circuit-breakers-history', labelKey: 'menu.cbHistory', to: '/circuit-breakers/history', permission: 'cb:history:read' },
          { key: 'circuit-breakers-global-config', labelKey: 'menu.cbConfig', to: '/circuit-breakers/global-config', permission: 'config:circuitbreaker:read' },
        ],
      },
      {
        path: '/config/rules',
        title: 'menu.configRules',
        object: 'rule',
        section: 'menu.sections.policy',
        permission: 'config:rules:read',
        audience: ['admin', 'maintainer'],
        tabs: [
          { key: 'config-rules', labelKey: 'menu.configRules', to: '/config/rules' },
        ],
      },
      {
        path: '/config/pools',
        title: 'menu.configPools',
        object: 'pool',
        section: 'menu.sections.policy',
        permission: 'config:pools:read',
        audience: ['admin', 'maintainer'],
        tabs: [
          { key: 'config-pools', labelKey: 'menu.configPools', to: '/config/pools' },
        ],
      },
    ],
  },
  {
    index: 'model-services',
    title: 'menu.modelServices',
    icon: 'cpu',
    children: [
      {
        path: '/config/adapters',
        title: 'menu.configAdapters',
        object: 'adapter',
        permission: 'config:adapters:read',
        audience: ['admin', 'maintainer'],
        tabs: [
          { key: 'config-adapters', labelKey: 'menu.configAdapters', to: '/config/adapters' },
        ],
      },
      {
        path: '/config/services',
        title: 'menu.configServices',
        object: 'service',
        permission: 'config:services:read',
        audience: ['admin', 'maintainer'],
        tabs: [
          { key: 'config-services', labelKey: 'menu.configServices', to: '/config/services' },
          { key: 'config-versions', labelKey: 'menu.configVersions', to: '/config/versions', permission: 'config:versions:read' },
        ],
      },
      {
        path: '/config/instances',
        title: 'menu.configInstances',
        object: 'instance',
        permission: 'config:instances:read',
        audience: ['admin', 'maintainer'],
        tabs: [
          { key: 'config-instances', labelKey: 'menu.configInstances', to: '/config/instances' },
        ],
      },
    ],
  },
  {
    index: 'security',
    title: 'menu.security',
    icon: 'lock',
    children: [
      {
        path: '/security/api-keys',
        title: 'menu.apiKeys',
        object: 'apikey',
        permission: ['security:apikeys:read', 'security:apikeys:manage'],
        audience: ['admin', 'maintainer'],
        tabs: [
          { key: 'security-api-keys', labelKey: 'menu.apiKeys', to: '/security/api-keys' },
        ],
      },
      {
        path: '/security/jwt-tokens',
        title: 'menu.jwtTokens',
        object: 'jwttoken',
        permission: ['security:jwttokens:read', 'security:jwttokens:manage'],
        audience: ['admin', 'maintainer'],
        tabs: [
          { key: 'security-jwt-tokens', labelKey: 'menu.jwtTokens', to: '/security/jwt-tokens' },
        ],
      },
      {
        path: '/security/blacklist',
        title: 'menu.blacklist',
        object: 'blacklist',
        permission: ['security:blacklist:read', 'security:blacklist:manage'],
        audience: ['admin', 'maintainer'],
        tabs: [
          { key: 'security-blacklist', labelKey: 'menu.blacklist', to: '/security/blacklist' },
        ],
      },
      {
        path: '/security/sanitization',
        title: 'menu.sanitization',
        object: 'sanitization',
        permission: ['security:sanitization:read', 'security:sanitization:manage'],
        audience: ['admin', 'maintainer'],
        tabs: [
          { key: 'security-sanitization', labelKey: 'menu.sanitization', to: '/security/sanitization' },
        ],
      },
      {
        path: '/security/record-governance',
        title: 'menu.recordGovernance',
        object: 'recordgovernance',
        permission: ['config:callhistory:read', 'config:callhistory:write'],
        audience: ['admin', 'maintainer'],
        tabs: [
          { key: 'security-record-governance', labelKey: 'menu.recordGovernance', to: '/security/record-governance' },
        ],
      },
      {
        path: '/security/login-guard',
        title: 'menu.loginGuard',
        object: 'loginguard',
        permission: ['security:loginguard:read', 'security:loginguard:manage'],
        audience: ['admin'],
        tabs: [
          { key: 'security-login-guard', labelKey: 'menu.loginGuard', to: '/security/login-guard' },
        ],
      },
    ],
  },
  {
    index: 'access',
    title: 'menu.access',
    icon: 'key',
    children: [
      {
        path: '/system/accounts',
        title: 'menu.accounts',
        object: 'account',
        permission: ['system:accounts:read', 'system:accounts:manage'],
        audience: ['admin'],
        tabs: [
          { key: 'system-accounts', labelKey: 'menu.accounts', to: '/system/accounts' },
        ],
      },
      {
        path: '/system/permissions',
        title: 'menu.permissions',
        object: 'permission',
        permission: ['system:permissions:read', 'system:permissions:manage'],
        audience: ['admin'],
        tabs: [
          { key: 'system-permissions', labelKey: 'menu.permissions', to: '/system/permissions' },
        ],
      },
      {
        path: '/system/sla',
        title: 'menu.slaAccess',
        object: 'sla',
        permission: ['system:sla:read', 'system:sla:manage'],
        audience: ['admin'],
        tabs: [
          { key: 'system-sla', labelKey: 'menu.slaAccess', to: '/system/sla' },
        ],
      },
      {
        // B4（2026-11-18）：页面访问热度。与「权限管理」同组相邻，因为它们是同一个问题的两面
        // ——权限管理决定「谁应该看到什么」，访问热度回答「谁实际在用」。
        // 只读、无写动作；审计员与管理员可见（权限码 security:audit:read）。
        path: '/system/page-heat',
        title: 'menu.pageHeat',
        object: 'page-access',
        permission: ['security:audit:read'],
        audience: ['admin', 'auditor'],
        tabs: [
          { key: 'system-page-heat', labelKey: 'menu.pageHeat', to: '/system/page-heat' },
        ],
      },
    ],
  },
  {
    index: 'platform',
    title: 'menu.platform',
    icon: 'setting',
    children: [
      {
        path: '/monitoring/config',
        title: 'menu.monitoringConfig',
        object: 'monitoring',
        section: 'menu.sections.runtime',
        permission: ['monitoring:config:read'],
        audience: ['admin', 'maintainer'],
        tabs: [
          { key: 'monitoring-config', labelKey: 'menu.monitoringConfig', to: '/monitoring/config' },
          { key: 'monitoring-config-runtime', labelKey: 'menu.monitoringRuntime', to: '/monitoring/config/runtime' },
        ],
      },
      {
        path: '/tracing/management',
        title: 'menu.tracingManagement',
        object: 'tracingconfig',
        section: 'menu.sections.runtime',
        permission: ['tracing:config:read', 'tracing:config:manage'],
        audience: ['admin', 'maintainer'],
        tabs: [
          { key: 'tracing-management', labelKey: 'menu.tracingManagement', to: '/tracing/management' },
        ],
      },
      {
        path: '/config/cache',
        title: 'menu.responseCache',
        object: 'cache',
        section: 'menu.sections.runtime',
        permission: ['config:cache:read', 'config:cache:write'],
        audience: ['admin', 'maintainer'],
        tabs: [
          { key: 'config-cache', labelKey: 'menu.responseCache', to: '/config/cache' },
        ],
      },
      {
        path: '/config/quota',
        title: 'menu.quotaConfig',
        object: 'quotaruntime',
        section: 'menu.sections.runtime',
        permission: 'config:quota:read',
        audience: ['admin', 'maintainer'],
        tabs: [
          { key: 'config-quota', labelKey: 'menu.quotaConfig', to: '/config/quota' },
        ],
      },
      {
        path: '/config/state-persistence',
        title: 'menu.deployment',
        object: 'deployment',
        section: 'menu.sections.deployment',
        permission: 'config:persistence:read',
        audience: ['admin', 'maintainer'],
        tabs: [
          { key: 'config-state-persistence', labelKey: 'menu.statePersistence', to: '/config/state-persistence' },
          { key: 'system-cluster', labelKey: 'menu.clusterConfig', to: '/system/cluster', permission: ['config:cluster:read', 'config:cluster:write'] },
          { key: 'system-health', labelKey: 'menu.healthConfig', to: '/system/health', permission: ['config:health:read', 'config:health:write'] },
        ],
      },
    ],
  },
  {
    index: 'dev-tools',
    title: 'menu.devTools',
    icon: 'tools',
    children: [
      // AI 试验场：5 个并列工具，不是一个对象的 5 个视图。
      // 合并成一项时头部要挤 5 连段（与右侧操作区抢空间），且用户「想直接点图像服务」的诉求落空，
      // 因此拆回独立菜单项；组内 6 项仍在「每组 ≤8 项」预算内。
      {
        path: '/playground/chat',
        title: 'menu.playgroundChat',
        object: 'playground-chat',
        permission: 'ai:playground:use',
        audience: ['admin', 'maintainer', 'user'],
        tabs: [
          { key: 'playground-chat', labelKey: 'menu.playgroundChat', to: '/playground/chat' },
        ],
      },
      {
        path: '/playground/embedding',
        title: 'menu.playgroundEmbedding',
        object: 'playground-embedding',
        permission: 'ai:playground:use',
        audience: ['admin', 'maintainer', 'user'],
        tabs: [
          { key: 'playground-embedding', labelKey: 'menu.playgroundEmbedding', to: '/playground/embedding' },
        ],
      },
      {
        path: '/playground/rerank',
        title: 'menu.playgroundRerank',
        object: 'playground-rerank',
        permission: 'ai:playground:use',
        audience: ['admin', 'maintainer', 'user'],
        tabs: [
          { key: 'playground-rerank', labelKey: 'menu.playgroundRerank', to: '/playground/rerank' },
        ],
      },
      {
        path: '/playground/audio',
        title: 'menu.playgroundAudio',
        object: 'playground-audio',
        permission: 'ai:playground:use',
        audience: ['admin', 'maintainer', 'user'],
        tabs: [
          { key: 'playground-audio', labelKey: 'menu.playgroundAudio', to: '/playground/audio' },
        ],
      },
      {
        path: '/playground/image',
        title: 'menu.playgroundImage',
        object: 'playground-image',
        permission: 'ai:playground:use',
        audience: ['admin', 'maintainer', 'user'],
        tabs: [
          { key: 'playground-image', labelKey: 'menu.playgroundImage', to: '/playground/image' },
        ],
      },
      {
        path: '/tools/client-access',
        title: 'menu.clientAccess',
        object: 'guide',
        audience: ['admin', 'maintainer', 'user', 'observer', 'auditor'],
        tabs: [
          { key: 'tools-client-access', labelKey: 'menu.clientAccess', to: '/tools/client-access' },
        ],
      },
    ],
  },
]

export const ROUTE_TO_MENU: Record<string, string> = Object.fromEntries(
  menuGroups.flatMap(group => group.children.flatMap(item => item.tabs.map(tab => [tab.to, item.path])))
) as Record<string, string>

/**
 * 带动态段的详情路由 → 所属菜单项。
 *
 * 详情页不属于任何 tab（面包屑末段单独渲染 `#id`），但侧栏高亮、默认展开组
 * 仍要落到它的列表页。
 */
const DETAIL_ROUTE_TO_MENU: Record<string, string> = {
  '/exceptions/detail': '/exceptions/list'
}

/**
 * 路由 → 菜单项主路径。
 *
 * 未命中一律回退 `route.path`（保证未登记的旧页面/未来页面仍能高亮自身）。
 */
export function resolveMenuPath(path: string): string {
  const exact = ROUTE_TO_MENU[path]
  if (exact) {
    return exact
  }
  for (const [prefix, menuPath] of Object.entries(DETAIL_ROUTE_TO_MENU)) {
    if (path === prefix || path.startsWith(`${prefix}/`)) {
      return menuPath
    }
  }
  return path
}

/** 按路由（含详情页）反查所属菜单项与组 */
export function findMenuEntry(path: string): { group: MenuGroup; item: MenuItem } | undefined {
  const menuPath = resolveMenuPath(path)
  for (const group of menuGroups) {
    for (const item of group.children) {
      if (item.path === menuPath) {
        return { group, item }
      }
    }
  }
  return undefined
}

/** 精确匹配当前路由对应的 tab（详情页等无 tab 的页面返回 undefined） */
/**
 * 给定的路由是不是该菜单项的某个视图。
 *
 * 点侧栏菜单项时用它判断「要不要跳」：用户已经停在这一项的某个视图上（例如「AI 试验场 / 图像服务」）
 * 就不该被打回第一个视图——那种「点了菜单却又回到别处」的体验会被读成「菜单点不动」。
 */
export function isMenuItemView(item: MenuItem, path: string): boolean {
  return item.tabs.some(tab => tab.to === path)
}

export function findMenuTab(path: string): MenuTab | undefined {
  for (const group of menuGroups) {
    for (const item of group.children) {
      const tab = item.tabs.find(candidate => candidate.to === path)
      if (tab) {
        return tab
      }
    }
  }
  return undefined
}
