/**
 * 菜单配置（2026-11-18「一页一事」重构：8 组 / 49 项 / 49 个页面）
 *
 * 组的顺序：
 * - 概览(1)：仪表板
 * - 运行观测(12)：调用记录 / 调用统计 / 慢调用 / 链路搜索 / 链路统计 /
 *   异常列表 / 异常统计 / 慢 SQL / 审计日志 / 操作记录 / Token 用量 / 配额使用
 * - 流量治理(9)：负载均衡监控 / 限流监控 / 熔断监控 / 熔断历史 /
 *   路由规则 / 负载均衡策略 / 限流配置 / 熔断配置 / 资源池
 * - 模型与实例(4)：适配器 / 服务 / 实例 / 版本
 * - 安全(6)：API 密钥 / JWT 令牌 / 黑名单 / PII 脱敏 / 记录治理 / 登录保护
 * - 访问与身份(3)：账户 / 权限 / SLA 接入
 * - 平台设置(8)：监控配置 / 监控运行时 / 追踪配置 / 响应缓存 / 配额运行时 /
 *   状态持久化 / 集群设置 / 健康探针设置
 * - 开发工具(6)：对话测试 / 向量生成 / 重排序 / 语音服务 / 图像服务 / 客户端接入
 *
 * **一个菜单项 = 一个页面（单 tab）**：不再用 tab 把「看」与「改」多种形态塞进同一项。
 * 每组内部按用途用 `section` 分节（观测类成节、策略/配置类成节），节标题不参与点击，
 * 由 `Layout.vue` 按 `children[i-1].section !== children[i].section` 渲染。
 *
 * **URL 一律不变**：平铺只是把原来藏在 tab 里的页面提升为独立菜单项，
 * 路由、权限码、后端接口都没动。
 *
 * 权限语义：
 * - `permission` 缺省 = 所有已登录用户可见；
 * - 数组 = **或**（任一命中即显示）；
 * - `roles` 字段保留（与 `permission` 是**且**关系，必须与路由守卫的 `meta.roles` 同口径），
 *   但**当前没有任何菜单项声明它**：授权边界统一交给权限码 + 路由 `meta.permissions`；
 * - 权限码以 `PermissionCodes.java` 的 `ALL_PERMISSION_CODES` 为权威全集（当前 76 个）。
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
  /** tab 级角色限制；缺省时沿用菜单项的 roles */
  roles?: string[]
}

export interface MenuItem {
  /** 菜单项主路由（= tabs[0].to），也是 el-menu-item 的 index */
  path: string
  /** 菜单标题（i18n key，如 menu.callHistory） */
  title: string
  /** 权限码；数组表示「任一命中即显示」。缺省表示所有已登录用户可见 */
  permission?: string | string[]
  /**
   * 允许访问的角色名（任一命中即可）；缺省表示不限制。
   *
   * 与 `permission` 是**且**关系，且必须与路由守卫的 `meta.roles` 同口径：
   * 菜单只按权限码过滤、路由按角色拦截时，会造出「看得见但点不进」的死入口。
   *
   * **当前没有任何菜单项使用它**：`/security`、`/system` 父级的 `meta.roles` 已撤掉，
   * 授权边界统一交给权限码（否则会反过来出现「后端放行、前端藏入口」）。
   */
  roles?: string[]
  /** 菜单项图标名（Element Plus 图标 kebab-case 名，可选） */
  icon?: string
  /**
   * 组内小节标题（i18n key，如 menu.sections.access）。
   *
   * 只用于「项太多、需要组内再分节」的组（当前仅平台设置组：访问与身份 / 运行时配置 /
   * 部署与基础设施 / 审计）。渲染规则见 `Layout.vue` 的 `showSectionHeader`：同一个小节
   * 只出现一次标题。缺省表示不分组。
   */
  section?: string
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
            labelKey: 'menu.dashboardMain',
            to: '/dashboard/main'
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
        title: 'menu.callRecords',
        permission: 'callhistory:read',
        section: 'menu.sections.calls',
        tabs: [
          {
            key: 'records',
            labelKey: 'menu.callRecords',
            to: '/call-history/list'
          }
        ]
      },
      {
        path: '/call-history/dashboard',
        title: 'menu.callStats',
        permission: 'callhistory:read',
        section: 'menu.sections.calls',
        tabs: [
          {
            key: 'dashboard',
            labelKey: 'menu.callStats',
            to: '/call-history/dashboard'
          }
        ]
      },
      {
        path: '/call-history/slow-calls',
        title: 'menu.slowCalls',
        permission: 'monitoring:slowquery:read',
        section: 'menu.sections.calls',
        tabs: [
          {
            key: 'slow-calls',
            labelKey: 'menu.slowCalls',
            to: '/call-history/slow-calls'
          }
        ]
      },
      {
        path: '/tracing/search',
        title: 'menu.tracingSearch',
        permission: 'tracing:search:read',
        section: 'menu.sections.tracing',
        tabs: [
          {
            key: 'search',
            labelKey: 'menu.tracingSearch',
            to: '/tracing/search'
          }
        ]
      },
      {
        path: '/tracing/dashboard',
        title: 'menu.tracingStats',
        permission: 'tracing:dashboard:read',
        section: 'menu.sections.tracing',
        tabs: [
          {
            key: 'dashboard',
            labelKey: 'menu.tracingStats',
            to: '/tracing/dashboard'
          }
        ]
      },
      {
        path: '/exceptions/list',
        title: 'menu.exceptionList',
        permission: 'monitoring:exceptions:read',
        section: 'menu.sections.health',
        tabs: [
          {
            key: 'list',
            labelKey: 'menu.exceptionList',
            to: '/exceptions/list'
          }
        ]
      },
      {
        path: '/exceptions/statistics',
        title: 'menu.exceptionStats',
        permission: 'monitoring:exceptions:read',
        section: 'menu.sections.health',
        tabs: [
          {
            key: 'statistics',
            labelKey: 'menu.exceptionStats',
            to: '/exceptions/statistics'
          }
        ]
      },
      {
        path: '/monitoring/slow-queries',
        title: 'menu.slowSql',
        permission: 'monitoring:slowquery:read',
        section: 'menu.sections.health',
        tabs: [
          {
            key: 'slow-sql',
            labelKey: 'menu.slowSql',
            to: '/monitoring/slow-queries'
          }
        ]
      },
      {
        path: '/security/audit-logs',
        title: 'menu.auditLogs',
        permission: 'security:audit:read',
        section: 'menu.sections.health',
        tabs: [
          {
            key: 'audit-logs',
            labelKey: 'menu.auditLogs',
            to: '/security/audit-logs'
          }
        ]
      },
      {
        path: '/system/console-operations',
        title: 'menu.consoleOperations',
        permission: ['monitoring:console:read'],
        section: 'menu.sections.health',
        tabs: [
          {
            key: 'console-operations',
            labelKey: 'menu.consoleOperations',
            to: '/system/console-operations'
          }
        ]
      },
      {
        path: '/call-history/token-usage',
        title: 'menu.tokenUsage',
        permission: 'monitoring:tokenusage:read',
        section: 'menu.sections.usage',
        tabs: [
          {
            key: 'token-usage',
            labelKey: 'menu.tokenUsage',
            to: '/call-history/token-usage'
          }
        ]
      },
      {
        path: '/monitoring/quota',
        title: 'menu.quotaUsage',
        permission: 'monitoring:quota:read',
        section: 'menu.sections.usage',
        tabs: [
          {
            key: 'quota',
            labelKey: 'menu.quotaUsage',
            to: '/monitoring/quota'
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
        path: '/load-balancers/monitoring',
        title: 'menu.lbMonitoring',
        permission: 'lb:monitoring:read',
        section: 'menu.sections.monitor',
        tabs: [
          {
            key: 'monitoring',
            labelKey: 'menu.lbMonitoring',
            to: '/load-balancers/monitoring'
          }
        ]
      },
      {
        path: '/rate-limiters/monitoring',
        title: 'menu.rlMonitoring',
        permission: 'rl:monitoring:read',
        section: 'menu.sections.monitor',
        tabs: [
          {
            key: 'monitoring',
            labelKey: 'menu.rlMonitoring',
            to: '/rate-limiters/monitoring'
          }
        ]
      },
      {
        path: '/circuit-breakers/monitoring',
        title: 'menu.cbMonitoring',
        permission: 'cb:monitoring:read',
        section: 'menu.sections.monitor',
        tabs: [
          {
            key: 'monitoring',
            labelKey: 'menu.cbMonitoring',
            to: '/circuit-breakers/monitoring'
          }
        ]
      },
      {
        path: '/circuit-breakers/history',
        title: 'menu.cbHistory',
        permission: 'cb:history:read',
        section: 'menu.sections.monitor',
        tabs: [
          {
            key: 'history',
            labelKey: 'menu.cbHistory',
            to: '/circuit-breakers/history'
          }
        ]
      },
      {
        path: '/config/rules',
        title: 'menu.configRules',
        permission: 'config:rules:read',
        section: 'menu.sections.policy',
        tabs: [
          {
            key: 'rules',
            labelKey: 'menu.configRules',
            to: '/config/rules'
          }
        ]
      },
      {
        path: '/load-balancers/strategy-config',
        title: 'menu.lbStrategy',
        permission: ['lb:config:read', 'lb:config:write'],
        section: 'menu.sections.policy',
        tabs: [
          {
            key: 'strategy',
            labelKey: 'menu.lbStrategy',
            to: '/load-balancers/strategy-config'
          }
        ]
      },
      {
        path: '/rate-limiters/config',
        title: 'menu.rateLimitingConfig',
        permission: 'config:ratelimit:read',
        section: 'menu.sections.policy',
        tabs: [
          {
            key: 'config',
            labelKey: 'menu.rateLimitingConfig',
            to: '/rate-limiters/config'
          }
        ]
      },
      {
        path: '/circuit-breakers/global-config',
        title: 'menu.cbConfig',
        permission: 'config:circuitbreaker:read',
        section: 'menu.sections.policy',
        tabs: [
          {
            key: 'global-config',
            labelKey: 'menu.cbConfig',
            to: '/circuit-breakers/global-config'
          }
        ]
      },
      {
        path: '/config/pools',
        title: 'menu.configPools',
        permission: 'config:pools:read',
        section: 'menu.sections.policy',
        tabs: [
          {
            key: 'pools',
            labelKey: 'menu.configPools',
            to: '/config/pools'
          }
        ]
      }
    ]
  },
  {
    index: 'model-services',
    title: 'menu.modelServices',
    icon: 'hourse',
    children: [
      {
        path: '/config/adapters',
        title: 'menu.configAdapters',
        permission: 'config:adapters:read',
        tabs: [
          {
            key: 'adapters',
            labelKey: 'menu.configAdapters',
            to: '/config/adapters'
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
            to: '/config/services'
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
            to: '/config/instances'
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
            to: '/config/versions'
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
            to: '/security/api-keys'
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
            to: '/security/jwt-tokens'
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
            to: '/security/blacklist'
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
            to: '/security/sanitization'
          }
        ]
      },
      {
        path: '/security/record-governance',
        title: 'menu.recordGovernance',
        permission: ['config:callhistory:read', 'config:callhistory:write'],
        tabs: [
          {
            key: 'record-governance',
            labelKey: 'menu.recordGovernance',
            to: '/security/record-governance'
          }
        ]
      },
      {
        path: '/security/login-guard',
        title: 'menu.loginGuard',
        permission: ['security:loginguard:read', 'security:loginguard:manage'],
        tabs: [
          {
            key: 'login-guard',
            labelKey: 'menu.loginGuard',
            to: '/security/login-guard'
          }
        ]
      }
    ]
  },
  {
    index: 'access',
    title: 'menu.access',
    icon: 'key',
    children: [
      {
        path: '/system/accounts',
        title: 'menu.accounts',
        permission: ['system:accounts:read', 'system:accounts:manage'],
        tabs: [
          {
            key: 'accounts',
            labelKey: 'menu.accounts',
            to: '/system/accounts'
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
            to: '/system/permissions'
          }
        ]
      },
      {
        path: '/system/sla',
        title: 'menu.slaAccess',
        permission: ['system:sla:read', 'system:sla:manage'],
        tabs: [
          {
            key: 'sla-access',
            labelKey: 'menu.slaAccess',
            to: '/system/sla'
          }
        ]
      }
    ]
  },
  {
    index: 'platform',
    title: 'menu.platform',
    icon: 'setting',
    children: [
      {
        path: '/monitoring/config',
        title: 'menu.monitoringConfig',
        permission: ['monitoring:config:read'],
        section: 'menu.sections.runtime',
        tabs: [
          {
            key: 'monitoring-config',
            labelKey: 'menu.monitoringConfig',
            to: '/monitoring/config'
          }
        ]
      },
      {
        path: '/monitoring/config/runtime',
        title: 'menu.monitoringRuntime',
        permission: ['monitoring:config:read'],
        section: 'menu.sections.runtime',
        tabs: [
          {
            key: 'monitoring-runtime',
            labelKey: 'menu.monitoringRuntime',
            to: '/monitoring/config/runtime'
          }
        ]
      },
      {
        path: '/tracing/management',
        title: 'menu.tracingManagement',
        permission: ['tracing:config:read', 'tracing:config:manage'],
        section: 'menu.sections.runtime',
        tabs: [
          {
            key: 'management',
            labelKey: 'menu.tracingManagement',
            to: '/tracing/management'
          }
        ]
      },
      {
        path: '/config/cache',
        title: 'menu.responseCache',
        permission: ['config:cache:read', 'config:cache:write'],
        section: 'menu.sections.runtime',
        tabs: [
          {
            key: 'cache',
            labelKey: 'menu.responseCache',
            to: '/config/cache'
          }
        ]
      },
      {
        path: '/config/quota',
        title: 'menu.quotaConfig',
        permission: 'config:quota:read',
        section: 'menu.sections.runtime',
        tabs: [
          {
            key: 'quota',
            labelKey: 'menu.quotaConfig',
            to: '/config/quota'
          }
        ]
      },
      {
        path: '/config/state-persistence',
        title: 'menu.statePersistence',
        permission: 'config:persistence:read',
        section: 'menu.sections.deployment',
        tabs: [
          {
            key: 'state-persistence',
            labelKey: 'menu.statePersistence',
            to: '/config/state-persistence'
          }
        ]
      },
      {
        path: '/system/cluster',
        title: 'menu.clusterConfig',
        permission: ['config:cluster:read', 'config:cluster:write'],
        section: 'menu.sections.deployment',
        tabs: [
          {
            key: 'cluster-config',
            labelKey: 'menu.clusterConfig',
            to: '/system/cluster'
          }
        ]
      },
      {
        path: '/system/health',
        title: 'menu.healthConfig',
        permission: ['config:health:read', 'config:health:write'],
        section: 'menu.sections.deployment',
        tabs: [
          {
            key: 'health-config',
            labelKey: 'menu.healthConfig',
            to: '/system/health'
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
        title: 'menu.playgroundChat',
        permission: 'ai:playground:use',
        section: 'menu.sections.playground',
        tabs: [
          {
            key: 'chat',
            labelKey: 'menu.playgroundChat',
            to: '/playground/chat'
          }
        ]
      },
      {
        path: '/playground/embedding',
        title: 'menu.playgroundEmbedding',
        permission: 'ai:playground:use',
        section: 'menu.sections.playground',
        tabs: [
          {
            key: 'embedding',
            labelKey: 'menu.playgroundEmbedding',
            to: '/playground/embedding'
          }
        ]
      },
      {
        path: '/playground/rerank',
        title: 'menu.playgroundRerank',
        permission: 'ai:playground:use',
        section: 'menu.sections.playground',
        tabs: [
          {
            key: 'rerank',
            labelKey: 'menu.playgroundRerank',
            to: '/playground/rerank'
          }
        ]
      },
      {
        path: '/playground/audio',
        title: 'menu.playgroundAudio',
        permission: 'ai:playground:use',
        section: 'menu.sections.playground',
        tabs: [
          {
            key: 'audio',
            labelKey: 'menu.playgroundAudio',
            to: '/playground/audio'
          }
        ]
      },
      {
        path: '/playground/image',
        title: 'menu.playgroundImage',
        permission: 'ai:playground:use',
        section: 'menu.sections.playground',
        tabs: [
          {
            key: 'image',
            labelKey: 'menu.playgroundImage',
            to: '/playground/image'
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
