/**
 * 菜单配置（P0-3 信息架构：9 组 40 项 → 7 组 28 项）
 *
 * 组的顺序（设计文档 §3.1）：
 * - 概览(1)：概览
 * - 运行观测(5)：调用历史 / 链路追踪 / 异常 / 慢请求 / 用量
 * - 流量治理(7)：路由规则 / 负载均衡 / 限流 / 熔断 / 响应缓存 / 配额策略 / 连接池
 * - 模型与实例(4)：服务 / 实例 / 版本 / 适配器
 * - 安全(5)：API Key / 响应脱敏 / JWT 令牌 / 黑名单 / 审计日志
 * - 系统(4)：账号 / 权限 / 链路追踪配置 / 状态持久化
 * - 开发工具(2)：AI 试验场 / 客户端接入
 *
 * **URL 一律不变**：40 → 28 只是把 12 个页面收进同组的 `tabs`，
 * 被收起的路由仍可直达（深链、收藏、文档里的路径都继续有效），只是不再单独占侧栏一行。
 * 哪个 tab 是「落地页」由 Layout.vue 按 `permission` 现算（第一个当前用户有权限的 tab）。
 *
 * 权限语义（设计文档 §3.3）：
 * - `permission` 缺省 = 所有已登录用户可见；
 * - 数组 = **或**（任一命中即显示），用于「聚合了不同权限码页面」的菜单项；
 * - tab 必须再单独门控：菜单可见 ≠ 每个 tab 可用（例：只有 `cb:history:read` 的用户
 *   看得到「熔断」，但落地页是「历史」而不是「状态与重置」）。
 * - 权限码以 `PermissionCodes.java` 的 `ALL_PERMISSION_CODES` 为权威全集（当前 63 个）。
 *   设计文档 §3.1 表里的 `security:jwt:manage` / `system:users:manage` / `system:roles:manage`
 *   在代码里不存在，已分别替换为 `security:jwttokens:manage` / `system:accounts:manage` /
 *   `system:permissions:manage`。
 *
 * `icon` 为 Element Plus 图标 kebab-case 名称，Layout.vue 中映射为图标组件。
 */

/** 菜单项内的 tab（一个 tab = 一条真实路由；URL 与单独成项时完全一致） */
export interface MenuTab {
  /** tab 标识（同一菜单项内唯一，供高亮/面包屑判定与测试使用） */
  key: string
  /** tab 标题（i18n key；单页 tab 直接复用菜单项 key，面包屑会自动去重） */
  labelKey: string
  /** tab 对应路由（必须是 router/index.ts 里存在的路径） */
  to: string
  /** tab 级权限码；缺省时沿用菜单项的 permission。数组同样为「或」 */
  permission?: string | string[]
}

export interface MenuItem {
  /** 菜单项主路由（= tabs[0].to），也是 el-menu-item 的 index */
  path: string
  /** 菜单标题（i18n key，如 menu.callHistory） */
  title: string
  /** 权限码；数组表示「任一命中即显示」。缺省表示所有已登录用户可见 */
  permission?: string | string[]
  /** 菜单项图标名（Element Plus 图标 kebab-case 名，可选） */
  icon?: string
  /** tab 列表，顺序即「tab 顺序」；第一个是默认落地页（无权限时取第一个有权限的） */
  tabs: MenuTab[]
}

export interface MenuGroup {
  /** 组标识（el-sub-menu index，defaultOpeneds 引用） */
  index: string
  /** 组标题（i18n key，如 menu.observability） */
  title: string
  /** 组图标名（Element Plus 图标 kebab-case 名） */
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
        path: '/dashboard/main',
        title: 'menu.dashboardMain',
        permission: 'overview:dashboard:read',
        tabs: [
          {
            key: 'main',
            labelKey: 'tabs.dashboard',
            to: '/dashboard/main',
            permission: 'overview:dashboard:read'
          }
        ]
      }
    ]
  },
  {
    index: 'observability',
    title: 'menu.observability',
    icon: 'data-analysis',
    children: [
      {
        path: '/call-history/list',
        title: 'menu.callHistory',
        permission: 'callhistory:view',
        tabs: [
          {
            key: 'records',
            labelKey: 'tabs.callHistoryRecords',
            to: '/call-history/list',
            permission: 'callhistory:view'
          },
          {
            key: 'dashboard',
            labelKey: 'tabs.callHistoryDashboard',
            to: '/call-history/dashboard',
            permission: 'callhistory:view'
          }
        ]
      },
      {
        path: '/tracing/search',
        title: 'menu.tracing',
        permission: ['tracing:search:read', 'tracing:dashboard:read'],
        tabs: [
          {
            key: 'search',
            labelKey: 'tabs.tracingSearch',
            to: '/tracing/search',
            permission: 'tracing:search:read'
          },
          {
            key: 'dashboard',
            labelKey: 'tabs.tracingDashboard',
            to: '/tracing/dashboard',
            permission: 'tracing:dashboard:read'
          }
        ]
      },
      {
        path: '/exceptions/list',
        title: 'menu.exceptions',
        permission: 'monitoring:exceptions:read',
        tabs: [
          {
            key: 'list',
            labelKey: 'tabs.exceptionList',
            to: '/exceptions/list',
            permission: 'monitoring:exceptions:read'
          },
          {
            key: 'statistics',
            labelKey: 'tabs.exceptionStatistics',
            to: '/exceptions/statistics',
            permission: 'monitoring:exceptions:read'
          }
        ]
      },
      {
        path: '/call-history/slow-calls',
        title: 'menu.slowRequests',
        permission: 'monitoring:slowquery:read',
        tabs: [
          {
            key: 'slow-calls',
            labelKey: 'tabs.slowCalls',
            to: '/call-history/slow-calls',
            permission: 'monitoring:slowquery:read'
          },
          {
            key: 'slow-sql',
            labelKey: 'tabs.slowSql',
            to: '/monitoring/slow-queries',
            permission: 'monitoring:slowquery:read'
          }
        ]
      },
      {
        path: '/call-history/token-usage',
        title: 'menu.usage',
        permission: ['monitoring:tokenusage:read', 'monitoring:quota:read'],
        tabs: [
          {
            key: 'token-usage',
            labelKey: 'tabs.tokenUsage',
            to: '/call-history/token-usage',
            permission: 'monitoring:tokenusage:read'
          },
          {
            key: 'quota',
            labelKey: 'tabs.quotaUsage',
            to: '/monitoring/quota',
            permission: 'monitoring:quota:read'
          }
        ]
      }
    ]
  },
  {
    index: 'traffic',
    title: 'menu.traffic',
    icon: 'connection',
    children: [
      {
        path: '/config/rules',
        title: 'menu.configRules',
        permission: 'config:rules:read',
        tabs: [
          {
            key: 'rules',
            labelKey: 'menu.configRules',
            to: '/config/rules',
            permission: 'config:rules:read'
          }
        ]
      },
      {
        path: '/load-balancers/monitoring',
        title: 'menu.loadBalancing',
        permission: ['lb:monitoring:read', 'lb:config:write', 'lb:config:read'],
        tabs: [
          {
            key: 'monitoring',
            labelKey: 'tabs.lbMonitoring',
            to: '/load-balancers/monitoring',
            permission: 'lb:monitoring:read'
          },
          {
            key: 'strategy',
            labelKey: 'tabs.lbStrategy',
            to: '/load-balancers/strategy-config',
            permission: ['lb:config:read', 'lb:config:write']
          }
        ]
      },
      {
        path: '/rate-limiters/monitoring',
        title: 'menu.rateLimiting',
        permission: 'rl:monitoring:read',
        tabs: [
          {
            key: 'monitoring',
            labelKey: 'menu.rateLimiting',
            to: '/rate-limiters/monitoring',
            permission: 'rl:monitoring:read'
          }
        ]
      },
      {
        path: '/circuit-breakers/monitoring',
        title: 'menu.circuitBreaking',
        permission: ['cb:monitoring:read', 'cb:history:read', 'config:circuitbreaker:read'],
        tabs: [
          {
            key: 'monitoring',
            labelKey: 'tabs.cbMonitoring',
            to: '/circuit-breakers/monitoring',
            permission: 'cb:monitoring:read'
          },
          {
            key: 'history',
            labelKey: 'tabs.cbHistory',
            to: '/circuit-breakers/history',
            permission: 'cb:history:read'
          },
          {
            key: 'global-config',
            labelKey: 'tabs.cbGlobalConfig',
            to: '/circuit-breakers/global-config',
            permission: 'config:circuitbreaker:read'
          }
        ]
      },
      {
        path: '/config/cache',
        title: 'menu.responseCache',
        permission: ['config:cache:read', 'config:cache:write'],
        tabs: [
          {
            key: 'cache',
            labelKey: 'menu.responseCache',
            to: '/config/cache',
            permission: ['config:cache:read', 'config:cache:write']
          }
        ]
      },
      {
        path: '/config/quota',
        title: 'menu.quotaConfig',
        permission: 'config:quota:read',
        tabs: [
          {
            key: 'quota',
            labelKey: 'menu.quotaConfig',
            to: '/config/quota',
            permission: 'config:quota:read'
          }
        ]
      },
      {
        path: '/config/pools',
        title: 'menu.configPools',
        permission: 'config:pools:read',
        tabs: [
          {
            key: 'pools',
            labelKey: 'menu.configPools',
            to: '/config/pools',
            permission: 'config:pools:read'
          }
        ]
      }
    ]
  },
  {
    index: 'model-services',
    title: 'menu.modelServices',
    icon: 'setting',
    children: [
      // 顺序 = 实例管理页引导条的顺序（配置适配器 → 创建服务 → 添加实例 → 版本）
      {
        path: '/config/adapters',
        title: 'menu.configAdapters',
        permission: 'config:adapters:read',
        tabs: [
          {
            key: 'adapters',
            labelKey: 'menu.configAdapters',
            to: '/config/adapters',
            permission: 'config:adapters:read'
          }
        ]
      },
      {
        path: '/config/services',
        title: 'menu.configServices',
        permission: 'config:services:read',
        tabs: [
          {
            key: 'services',
            labelKey: 'menu.configServices',
            to: '/config/services',
            permission: 'config:services:read'
          }
        ]
      },
      {
        path: '/config/instances',
        title: 'menu.configInstances',
        permission: 'config:instances:read',
        tabs: [
          {
            key: 'instances',
            labelKey: 'menu.configInstances',
            to: '/config/instances',
            permission: 'config:instances:read'
          }
        ]
      },
      {
        path: '/config/versions',
        title: 'menu.configVersions',
        permission: 'config:versions:read',
        tabs: [
          {
            key: 'versions',
            labelKey: 'menu.configVersions',
            to: '/config/versions',
            permission: 'config:versions:read'
          }
        ]
      }
    ]
  },
  {
    index: 'security',
    title: 'menu.security',
    icon: 'lock',
    children: [
      {
        path: '/security/api-keys',
        title: 'menu.apiKeys',
        permission: ['security:apikeys:read', 'security:apikeys:manage'],
        tabs: [
          {
            key: 'api-keys',
            labelKey: 'menu.apiKeys',
            to: '/security/api-keys',
            permission: ['security:apikeys:read', 'security:apikeys:manage']
          }
        ]
      },
      {
        path: '/security/sanitization',
        title: 'menu.sanitization',
        permission: ['security:sanitization:read', 'security:sanitization:manage'],
        tabs: [
          {
            key: 'sanitization',
            labelKey: 'menu.sanitization',
            to: '/security/sanitization',
            permission: ['security:sanitization:read', 'security:sanitization:manage']
          }
        ]
      },
      {
        path: '/security/jwt-tokens',
        title: 'menu.jwtTokens',
        permission: ['security:jwttokens:read', 'security:jwttokens:manage'],
        tabs: [
          {
            key: 'jwt-tokens',
            labelKey: 'menu.jwtTokens',
            to: '/security/jwt-tokens',
            permission: ['security:jwttokens:read', 'security:jwttokens:manage']
          }
        ]
      },
      {
        path: '/security/blacklist',
        title: 'menu.blacklist',
        permission: ['security:blacklist:read', 'security:blacklist:manage'],
        tabs: [
          {
            key: 'blacklist',
            labelKey: 'menu.blacklist',
            to: '/security/blacklist',
            permission: ['security:blacklist:read', 'security:blacklist:manage']
          }
        ]
      },
      {
        path: '/security/audit-logs',
        title: 'menu.auditLogs',
        permission: 'security:audit:read',
        tabs: [
          {
            key: 'audit-logs',
            labelKey: 'menu.auditLogs',
            to: '/security/audit-logs',
            permission: 'security:audit:read'
          }
        ]
      }
    ]
  },
  {
    index: 'system',
    title: 'menu.system',
    icon: 'user',
    children: [
      {
        path: '/system/accounts',
        title: 'menu.accounts',
        permission: ['system:accounts:read', 'system:accounts:manage'],
        tabs: [
          {
            key: 'accounts',
            labelKey: 'menu.accounts',
            to: '/system/accounts',
            permission: ['system:accounts:read', 'system:accounts:manage']
          }
        ]
      },
      {
        path: '/system/permissions',
        title: 'menu.permissions',
        permission: ['system:permissions:read', 'system:permissions:manage'],
        tabs: [
          {
            key: 'permissions',
            labelKey: 'menu.permissions',
            to: '/system/permissions',
            permission: ['system:permissions:read', 'system:permissions:manage']
          }
        ]
      },
      {
        path: '/tracing/management',
        title: 'menu.tracingManagement',
        permission: ['tracing:config:read', 'tracing:config:manage'],
        tabs: [
          {
            key: 'management',
            labelKey: 'menu.tracingManagement',
            to: '/tracing/management',
            permission: ['tracing:config:read', 'tracing:config:manage']
          }
        ]
      },
      {
        path: '/config/state-persistence',
        title: 'menu.statePersistence',
        permission: 'config:persistence:read',
        tabs: [
          {
            key: 'state-persistence',
            labelKey: 'menu.statePersistence',
            to: '/config/state-persistence',
            permission: 'config:persistence:read'
          }
        ]
      }
    ]
  },
  {
    index: 'dev-tools',
    title: 'menu.devTools',
    icon: 'tools',
    children: [
      {
        path: '/playground/chat',
        title: 'menu.playground',
        permission: 'ai:playground:use',
        tabs: [
          {
            key: 'chat',
            labelKey: 'tabs.playgroundChat',
            to: '/playground/chat',
            permission: 'ai:playground:use'
          },
          {
            key: 'embedding',
            labelKey: 'tabs.playgroundEmbedding',
            to: '/playground/embedding',
            permission: 'ai:playground:use'
          },
          {
            key: 'rerank',
            labelKey: 'tabs.playgroundRerank',
            to: '/playground/rerank',
            permission: 'ai:playground:use'
          },
          {
            key: 'audio',
            labelKey: 'tabs.playgroundAudio',
            to: '/playground/audio',
            permission: 'ai:playground:use'
          },
          {
            key: 'image',
            labelKey: 'tabs.playgroundImage',
            to: '/playground/image',
            permission: 'ai:playground:use'
          }
        ]
      },
      {
        path: '/tools/client-access',
        title: 'menu.clientAccess',
        tabs: [
          {
            key: 'client-access',
            labelKey: 'menu.clientAccess',
            to: '/tools/client-access'
          }
        ]
      }
    ]
  }
]

/**
 * 深链 → 菜单项 的反查表（设计文档 §3.4）。
 *
 * 由 `menuGroups` 的 tabs 派生，**不手写**：新增 tab 即自动获得高亮，不会出现
 * 「配置里加了页面、高亮表忘加」的漂移。键是真实路由，值是菜单项主路径。
 */
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
