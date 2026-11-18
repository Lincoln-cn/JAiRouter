import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { reportPageView } from '@/api/uiAccess'
import { menuGroups } from '@/config/menu'
import type { Audience } from '@/config/menu'
import { usePermission } from '@/composables/usePermission'
import { i18n } from '@/i18n'

/**
 * 路由 meta 的具名声明（#521）。
 *
 * 此前守卫里写 `(to.meta as any).roles` / `(to.meta as any).permissions` —— 等于放弃类型检查：
 * 键名拼错、值形状写错都不会有人发现。这里把路由表实际用到的 5 个键声明出来，
 * 守卫改为直接读 `to.meta.roles`，写错键名会在编译期报错。
 */
declare module 'vue-router' {
  interface RouteMeta {
    /** 是否需要登录（未声明时按需要处理） */
    requiresAuth?: boolean
    /** i18n 标题键（如 `route.dashboard`） */
    titleKey?: string
    /** 侧栏图标名 */
    icon?: string
    /** 允许访问的角色名（任一命中即可） */
    roles?: string[]
    /** 需要的权限码（任一命中即可；ADMIN 恒通过） */
    permissions?: string[]
    /**
     * 页面级 audience（与菜单项同一套取值）。
     *
     * 目前只有「角色专属首页」声明它：概览组的四个首页互斥，路由层必须与菜单层同口径，
     * 否则用户手输别人的首页地址仍能进去（菜单藏了、路由放行 = 假门控）。
     */
    audience?: Audience[]
  }
}

// 添加JWT解码函数
function isTokenExpired(token: string): boolean {
  try {
    // 解码JWT token
    const payload = JSON.parse(atob(token.split('.')[1]))
    // 获取过期时间（以秒为单位）
    const exp = payload.exp
    // 获取当前时间（以秒为单位）
    const currentTime = Math.floor(Date.now() / 1000)
    // 检查是否过期（提前1分钟过期以确保安全）
    return exp - currentTime < 60
  } catch (error) {
    // 如果解码失败，认为token无效
    return true
  }
}

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/login',
      name: 'login',
      component: () => import('../views/Login.vue'),
      meta: { requiresAuth: false }
    },
    {
      path: '/',
      name: 'home',
      component: () => import('../views/Layout.vue'),
      redirect: '/dashboard',
      meta: { requiresAuth: true },
      children: [
        // 未匹配路径的兜底：此前没有 catch-all，手输或收藏一个失效地址会得到整页空白
        // （连侧边栏都不渲染，页面标题仍是默认的 "JAiRouter"），看起来像服务挂了。
        // 挂在 Layout 下，用户仍可通过菜单离开；静态路由优先于通配，不会吞掉具名路由。
        {
          path: ':pathMatch(.*)*',
          name: 'not-found',
          component: () => import('../views/NotFound.vue'),
          meta: { titleKey: 'route.notFound' }
        }
      ]
    },
    // 概览
    {
      path: '/dashboard',
      name: 'dashboard',
      component: () => import('../views/Layout.vue'),
      redirect: '/dashboard/main',
      meta: { requiresAuth: true, titleKey: 'route.dashboard' },
      children: [
        {
          path: 'main',
          name: 'dashboard-main',
          component: () => import('../views/Dashboard.vue'),
          meta: { titleKey: 'route.dashboardMain', icon: 'house', permissions: ['overview:dashboard:read'], audience: ['admin'] }
        },
        {
          path: 'operations',
          name: 'dashboard-operations',
          component: () => import('../views/dashboard/OperationsBoard.vue'),
          meta: { titleKey: 'route.dashboardOps', icon: 'data-analysis', permissions: ['overview:dashboard:read'], audience: ['maintainer'] }
        },
        {
          path: 'mine',
          name: 'dashboard-mine',
          component: () => import('../views/dashboard/MyUsage.vue'),
          meta: {
            titleKey: 'route.dashboardMine',
            icon: 'user',
            permissions: ['callhistory:self:read', 'callhistory:read'],
            audience: ['user']
          }
        },
        {
          path: 'trends',
          name: 'dashboard-trends',
          component: () => import('../views/dashboard/TrendsReport.vue'),
          meta: { titleKey: 'route.dashboardTrends', icon: 'data-line', permissions: ['overview:dashboard:read'], audience: ['observer'] }
        }
      ]
    },
    // 配置管理
    {
      path: '/config',
      name: 'config',
      component: () => import('../views/Layout.vue'),
      meta: { requiresAuth: true, titleKey: 'route.config' },
      redirect: '/config/services',
      children: [
        {
          path: 'services',
          name: 'service-management',
          component: () => import('../views/config/ServiceManagement.vue'),
          meta: { titleKey: 'route.serviceManagement', icon: 'setting', permissions: ['config:services:read'] }
        },
        {
          path: 'instances',
          name: 'instance-management',
          component: () => import('../views/config/InstanceManagement.vue'),
          meta: { titleKey: 'route.instanceManagement', icon: 'cpu', permissions: ['config:instances:read'] }
        },
        {
          path: 'versions',
          name: 'version-management',
          component: () => import('../views/config/VersionManagement.vue'),
          meta: { titleKey: 'route.versionManagement', icon: 'document', permissions: ['config:versions:read'] }
        },
        {
          path: 'state-persistence',
          name: 'state-persistence-config',
          component: () => import('../views/config/StatePersistenceManagement.vue'),
          meta: { titleKey: 'route.statePersistenceConfig', icon: 'folder-opened', permissions: ['config:persistence:read'] }
        },
        {
          path: 'adapters',
          name: 'adapter-management',
          component: () => import('../views/config/AdapterManagement.vue'),
          meta: { titleKey: 'route.adapterManagement', icon: 'connection', permissions: ['config:adapters:read'] }
        },
        {
          path: 'rules',
          name: 'rule-management',
          component: () => import('../views/config/rules/RuleManagement.vue'),
          meta: { titleKey: 'route.ruleManagement', icon: 'set-up', permissions: ['config:rules:read'] }
        },
        {
          path: 'pools',
          name: 'pool-management',
          component: () => import('../views/config/pools/PoolManagement.vue'),
          meta: { titleKey: 'route.poolManagement', icon: 'box', permissions: ['config:pools:read'] }
        },
        {
          path: 'cache',
          name: 'response-cache-management',
          component: () => import('../views/config/ResponseCacheManagement.vue'),
          meta: { titleKey: 'route.responseCacheManagement', icon: 'coin', permissions: ['config:cache:read', 'config:cache:write'] }
        },
        {
          path: 'quota',
          name: 'quota-config-management',
          component: () => import('../views/config/QuotaConfigManagement.vue'),
          meta: { titleKey: 'route.quotaConfigManagement', icon: 'coin', permissions: ['config:quota:read'] }
        }
      ]
    },
    // 负载均衡器管理
    {
      path: '/load-balancers',
      name: 'load-balancers',
      component: () => import('../views/Layout.vue'),
      redirect: '/load-balancers/monitoring',
      meta: { requiresAuth: true, titleKey: 'route.loadBalancers' },
      children: [
        {
          path: 'monitoring',
          name: 'load-balancer-monitoring',
          component: () => import('../views/load-balancer/Monitoring.vue'),
          meta: { titleKey: 'route.loadBalancerMonitoring', icon: 'monitor', permissions: ['lb:monitoring:read', 'lb:config:read', 'lb:config:write'] }
        },
        {
          path: 'strategy-config',
          name: 'load-balancer-strategy-config',
          component: () => import('../views/load-balancer/StrategyConfig.vue'),
          meta: { titleKey: 'route.loadBalancerStrategyConfig', icon: 'setting', permissions: ['lb:config:read', 'lb:config:write'] }
        }
      ]
    },

    // 熔断器管理
    {
      path: '/circuit-breakers',
      name: 'circuit-breakers',
      component: () => import('../views/Layout.vue'),
      redirect: '/circuit-breakers/monitoring',
      meta: { requiresAuth: true, titleKey: 'route.circuitBreakers' },
      children: [
        {
          path: 'monitoring',
          name: 'circuit-breaker-monitoring',
          component: () => import('../views/circuit-breaker/Monitoring.vue'),
          meta: { titleKey: 'route.circuitBreakerMonitoring', icon: 'monitor', permissions: ['cb:monitoring:read', 'cb:history:read', 'config:circuitbreaker:read'] }
        },
        {
          path: 'history',
          name: 'circuit-breaker-history',
          component: () => import('../views/circuit-breaker/History.vue'),
          meta: { titleKey: 'route.circuitBreakerHistory', icon: 'document', permissions: ['cb:history:read'] }
        },
        {
          path: 'global-config',
          name: 'circuit-breaker-global-config',
          component: () => import('../views/circuit-breaker/GlobalConfig.vue'),
          meta: { titleKey: 'route.circuitBreakerGlobalConfig', icon: 'setting', permissions: ['config:circuitbreaker:read'] }
        }
      ]
    },

    // 安全管理
    {
      path: '/security',
      name: 'security',
      component: () => import('../views/Layout.vue'),
      meta: { requiresAuth: true, titleKey: 'route.security' },
      redirect: '/security/api-keys',
      children: [
        {
          path: 'api-keys',
          name: 'api-key-management',
          component: () => import('../views/security/ApiKeyManagement.vue'),
          meta: { titleKey: 'route.apiKeyManagement', icon: 'key', permissions: ['security:apikeys:read', 'security:apikeys:manage'] }
        },
        {
          path: 'sanitization',
          name: 'sanitization-management',
          component: () => import('../views/security/SanitizationManagement.vue'),
          meta: { titleKey: 'route.sanitizationManagement', icon: 'lock', permissions: ['security:sanitization:read', 'security:sanitization:manage'] }
        },
        {
          path: 'jwt-tokens',
          name: 'jwt-token-management',
          component: () => import('../views/security/JwtTokenManagement.vue'),
          meta: { titleKey: 'route.jwtTokenManagement', icon: 'lock', permissions: ['security:jwttokens:read', 'security:jwttokens:manage'] }
        },
        {
          path: 'blacklist',
          name: 'blacklist-management',
          component: () => import('../views/security/BlacklistManagement.vue'),
          meta: { titleKey: 'route.blacklistManagement', icon: 'warning', permissions: ['security:blacklist:read', 'security:blacklist:manage'] }
        },
        {
          path: 'audit-logs',
          name: 'audit-log-management',
          component: () => import('../views/security/AuditLogManagement.vue'),
          meta: { titleKey: 'route.auditLogManagement', icon: 'document-checked', permissions: ['security:audit:read'] }
        },
        {
          path: 'record-governance',
          name: 'record-governance',
          component: () => import('../views/security/RecordGovernance.vue'),
          meta: {
            titleKey: 'route.recordGovernance',
            icon: 'lock',
            permissions: ['config:callhistory:read', 'config:callhistory:write']
          }
        },
        {
          path: 'login-guard',
          name: 'login-guard',
          component: () => import('../views/security/LoginGuardManagement.vue'),
          meta: {
            titleKey: 'route.loginGuard',
            icon: 'key',
            permissions: ['security:loginguard:read', 'security:loginguard:manage']
          }
        }
      ]
    },
    // 系统管理
    {
      path: '/system',
      name: 'system',
      component: () => import('../views/Layout.vue'),
      meta: { requiresAuth: true, titleKey: 'route.system' },
      redirect: '/system/accounts',
      children: [
        {
          path: 'accounts',
          name: 'account-management',
          component: () => import('../views/security/JwtAccountManagement.vue'),
          meta: { titleKey: 'route.accountManagement', icon: 'user', permissions: ['system:accounts:read', 'system:accounts:manage'] }
        },
        {
          path: 'permissions',
          name: 'permission-management',
          component: () => import('../views/system/PermissionManagement.vue'),
          meta: { titleKey: 'route.permissionManagement', icon: 'key', permissions: ['system:permissions:read', 'system:permissions:manage'] }
        },
        {
          path: 'cluster',
          name: 'cluster-config',
          component: () => import('../views/system/ClusterConfig.vue'),
          meta: { titleKey: 'route.clusterConfig', icon: 'connection', permissions: ['config:cluster:read', 'config:cluster:write'] }
        },
        {
          path: 'health',
          name: 'health-config',
          component: () => import('../views/system/HealthProbeConfig.vue'),
          meta: { titleKey: 'route.healthConfig', icon: 'first-aid-kit', permissions: ['config:health:read', 'config:health:write'] }
        },
        {
          path: 'console-operations',
          name: 'console-operations',
          component: () => import('../views/system/ConsoleOperations.vue'),
          meta: { titleKey: 'route.consoleOperations', icon: 'document', permissions: ['monitoring:console:read'] }
        },
        {
          path: 'sla',
          name: 'sla-access',
          component: () => import('../views/system/SlaAccessManagement.vue'),
          // 看账号列表只需读码；签发/吊销/删除按钮各自挂 system:sla:manage。
          meta: { titleKey: 'route.slaAccess', icon: 'link', permissions: ['system:sla:read', 'system:sla:manage'] }
        },
        {
          // B4（2026-11-18）：页面访问热度（只读集合页）。
          // 数据是「别人的访问记录」，因此和审计日志同一把门：security:audit:read。
          path: 'page-heat',
          name: 'page-access-heat',
          component: () => import('../views/system/PageAccessHeat.vue'),
          meta: { titleKey: 'route.pageHeat', icon: 'data-line', permissions: ['security:audit:read'] }
        }
      ]
    },
    // 追踪管理 - 重构后的结构
    {
      path: '/tracing',
      name: 'tracing',
      component: () => import('../views/Layout.vue'),
      redirect: '/tracing/dashboard',
      meta: { requiresAuth: true, titleKey: 'route.tracing' },
      children: [
        {
          path: 'dashboard',
          name: 'tracing-dashboard',
          component: () => import('../views/tracing/Dashboard.vue'),
          meta: { titleKey: 'route.tracingDashboard', icon: 'connection', permissions: ['tracing:dashboard:read'] }
        },
        {
          path: 'search',
          name: 'tracing-search',
          component: () => import('../views/tracing/Search.vue'),
          meta: { titleKey: 'route.tracingSearch', icon: 'search', permissions: ['tracing:search:read'] }
        },
        {
          path: 'management',
          name: 'tracing-management',
          component: () => import('../views/tracing/Management.vue'),
          meta: { titleKey: 'route.tracingManagement', icon: 'setting', permissions: ['tracing:config:read', 'tracing:config:manage'] }
        }
      ]
    },

    // AI 试验场 - 各服务作为独立子路由
    {
      path: '/playground',
      name: 'playground',
      component: () => import('../views/Layout.vue'),
      redirect: '/playground/chat',
      meta: { requiresAuth: true, titleKey: 'route.playground' },
      children: [
        {
          path: 'chat',
          name: 'playground-chat',
          component: () => import('../views/playground/components/chat/ChatContainer.vue'),
          meta: { titleKey: 'route.playgroundChat', icon: 'chat-dot-round', permissions: ['ai:playground:use'] }
        },
        {
          path: 'embedding',
          name: 'playground-embedding',
          component: () => import('../views/playground/components/embedding/EmbeddingContainer.vue'),
          meta: { titleKey: 'route.playgroundEmbedding', icon: 'data-line', permissions: ['ai:playground:use'] }
        },
        {
          path: 'rerank',
          name: 'playground-rerank',
          component: () => import('../views/playground/components/rerank/RerankContainer.vue'),
          meta: { titleKey: 'route.playgroundRerank', icon: 'sort', permissions: ['ai:playground:use'] }
        },
        {
          path: 'audio',
          name: 'playground-audio',
          component: () => import('../views/playground/components/audio/AudioContainer.vue'),
          meta: { titleKey: 'route.playgroundAudio', icon: 'headset', permissions: ['ai:playground:use'] }
        },
        {
          path: 'image',
          name: 'playground-image',
          component: () => import('../views/playground/components/image/ImageContainer.vue'),
          meta: { titleKey: 'route.playgroundImage', icon: 'picture', permissions: ['ai:playground:use'] }
        }
      ]
    },

    // 异常管理
    {
      path: '/exceptions',
      name: 'exceptions',
      component: () => import('../views/Layout.vue'),
      redirect: '/exceptions/list',
      meta: { requiresAuth: true, titleKey: 'route.exceptions' },
      children: [
        {
          path: 'list',
          name: 'exception-list',
          component: () => import('../views/exception/ExceptionManagement.vue'),
          meta: { titleKey: 'route.exceptionList', icon: 'warning', permissions: ['monitoring:exceptions:read'] }
        },
        {
          path: 'detail/:id',
          name: 'exception-detail',
          component: () => import('../views/exception/ExceptionDetail.vue'),
          meta: { titleKey: 'route.exceptionDetail', icon: 'document-checked', permissions: ['monitoring:exceptions:read'] }
        },
        {
          path: 'statistics',
          name: 'exception-statistics',
          component: () => import('../views/exception/ExceptionStatistics.vue'),
          meta: { titleKey: 'route.exceptionStatistics', icon: 'data-analysis', permissions: ['monitoring:exceptions:read'] }
        }
      ]
    },
    // 限流器管理
    {
      path: '/rate-limiters',
      name: 'rate-limiters',
      component: () => import('../views/Layout.vue'),
      redirect: '/rate-limiters/monitoring',
      meta: { requiresAuth: true, titleKey: 'route.rateLimiters' },
      children: [
        {
          path: 'monitoring',
          name: 'rate-limiter-monitoring',
          component: () => import('../views/rate-limiter/Monitoring.vue'),
          meta: { titleKey: 'route.rateLimiterMonitoring', icon: 'monitor', permissions: ['rl:monitoring:read'] }
        },
        {
          path: 'config',
          name: 'rate-limiter-config',
          component: () => import('../views/rate-limiter/Config.vue'),
          meta: { titleKey: 'route.rateLimiterConfig', icon: 'setting', permissions: ['config:ratelimit:read'] }
        }
      ]
    },

    // 监控分析
    {
      path: '/monitoring',
      name: 'monitoring',
      component: () => import('../views/Layout.vue'),
      meta: { requiresAuth: true, titleKey: 'route.monitoring' },
      redirect: '/monitoring/slow-queries',
      children: [
        {
          path: 'slow-queries',
          name: 'slow-query-analysis',
          component: () => import('../views/monitoring/SlowQueryAnalysis.vue'),
          meta: { titleKey: 'route.slowQueryAnalysis', icon: 'timer', permissions: ['monitoring:slowquery:read'] }
        },
        {
          path: 'quota',
          name: 'quota-usage-monitoring',
          component: () => import('../views/monitoring/QuotaUsageMonitoring.vue'),
          meta: { titleKey: 'route.quotaUsageMonitoring', icon: 'data-analysis', permissions: ['monitoring:quota:read'] }
        },
        {
          // 监控配置：系统组里的一项两 tab（决策 D1）；路径沿用 /monitoring 前缀。
          // 权限门槛用 monitoring:config:read（#1173 新增）：页面可见与能否改配置是两件事。
      // 本页与「运行时与应急」页的写按钮各自挂 monitoring:config:write，
          // 而这个码正好是「运营/管理」面才有的：ADMIN + OPERATOR 有，USER/VIEWER 无。
          path: 'config',
          name: 'monitoring-config',
          component: () => import('../views/monitoring/MonitoringConfig.vue'),
          meta: { titleKey: 'route.monitoringConfig', icon: 'setting', permissions: ['monitoring:config:read'] }
        },
        {
          path: 'config/runtime',
          name: 'monitoring-runtime',
          component: () => import('../views/monitoring/MonitoringRuntime.vue'),
          meta: { titleKey: 'route.monitoringRuntime', icon: 'setting', permissions: ['monitoring:config:read'] }
        }
      ]
    },
    // API 调用历史路由
    {
      path: '/call-history',
      name: 'call-history',
      component: () => import('../views/Layout.vue'),
      redirect: '/call-history/dashboard',
      meta: { requiresAuth: true, titleKey: 'route.callHistory' },
      children: [
        {
          path: 'dashboard',
          name: 'call-history-dashboard',
          component: () => import('../views/callHistory/Dashboard.vue'),
          meta: { titleKey: 'route.callHistoryDashboard', icon: 'data-analysis', permissions: ['callhistory:read', 'callhistory:self:read'] }
        },
        {
          path: 'list',
          name: 'call-history-list',
          component: () => import('../views/callHistory/CallHistoryList.vue'),
          meta: { titleKey: 'route.callHistoryList', icon: 'list', permissions: ['callhistory:read', 'callhistory:self:read'] }
        },
        {
          path: 'slow-calls',
          name: 'call-history-slow-calls',
          component: () => import('../views/callHistory/CallHistorySlowCalls.vue'),
          meta: { titleKey: 'route.callHistorySlowCalls', icon: 'timer', permissions: ['monitoring:slowquery:read'] }
        },
        {
          path: 'token-usage',
          name: 'call-history-token-usage',
          component: () => import('../views/callHistory/TokenUsageStatistics.vue'),
          meta: { titleKey: 'route.callHistoryTokenUsage', icon: 'DataAnalysis', permissions: ['monitoring:tokenusage:read', 'monitoring:quota:read'] }
        }
      ]
    },

    // 开发者工具
    {
      path: '/tools',
      name: 'tools',
      component: () => import('../views/Layout.vue'),
      meta: { requiresAuth: true, titleKey: 'route.tools' },
      redirect: '/tools/client-access',
      children: [
        {
          path: 'client-access',
          name: 'client-access-guide',
          component: () => import('../views/tools/ClientAccessGuide.vue'),
          meta: { titleKey: 'route.clientAccessGuide', icon: 'link' }
        }
      ]
    }
  ]
})

// 路由守卫
router.beforeEach((to, from, next) => {
  const userStore = useUserStore()

  /**
   * 无权限时的统一出口
   *
   * 落地页 `dashboard-main` 自身也受权限门控（它声明了 meta.permissions），
   * 若用户连落地页的权限码都没有，再跳 `dashboard-main` 就会被同一个守卫反复拦截，
   * vue-router 会抛「Infinite redirect in navigation guard」而整页白屏。
   * 所以已经在落地页上时直接放行，由页面自身呈现（数据请求失败也可降级展示）。
   */
  /**
   * 拿不到权限时的去处：本角色第一个可访问页面。
   *
   * 五个角色各有首页（ADMIN 治理指挥台 / OPERATOR 运营看板 / USER 我的用量 /
   * VIEWER 趋势报表 / AUDITOR 审计），固定回 dashboard-main 会让后三者停在一个
   * 自己没有权限的页面上（旧实现靠 `to.name === 'dashboard-main'` 时放行兜住，
   * 代价是 USER 也能看到治理指挥台）。
   */
  const denyToLanding = (): void => {
    const landing = landingPath()
    if (to.path === landing) {
      next()
      return
    }
    next({ path: landing })
  }

  const landingPath = (): string => {
    const { filterMenuByPermission, resolveLandingPath } = usePermission()
    const groups = filterMenuByPermission(menuGroups)
    for (const group of groups) {
      const first = group.children[0]
      if (first) {
        return resolveLandingPath(first)
      }
    }
    return '/dashboard/main'
  }

  // 特殊处理根路径的重定向
  if (to.path === '/') {
    next({ name: 'dashboard-main' })
    return
  }

  // 检查是否需要认证
  if (to.meta.requiresAuth !== false) {
    // 检查是否有token
    if (!userStore.isAuthenticated()) {
      next({ name: 'login' })
      return
    }

    // 检查token是否过期
    const token = localStorage.getItem('admin_token')
    if (token && isTokenExpired(token)) {
      userStore.clearToken()
      next({ name: 'login' })
      return
    }

    // 检查角色权限
    const requiredRoles = to.meta.roles
    if (requiredRoles && requiredRoles.length > 0) {
      const userRoles = userStore.userInfo?.roles || []
      const hasRole = requiredRoles.some(role => userRoles.includes(role))
      if (!hasRole) {
        // 无权限，跳转到仪表板
        denyToLanding()
        return
      }
    }

    // 页面级 audience（仅角色专属首页声明）：与菜单层同口径，
    // 否则用户手输别人的首页地址仍能进去（菜单藏了、路由放行 = 假门控）
    const requiredAudience = to.meta.audience
    if (requiredAudience && requiredAudience.length > 0) {
      const { hasAudience } = usePermission()
      if (!hasAudience(requiredAudience)) {
        denyToLanding()
        return
      }
    }

    // v2.9.8 RBAC: 检查权限码（meta.permissions 为「或」：任一命中即放行，与菜单/按钮的 v-permission 数组语义一致；ADMIN 恒通过）
    const requiredPermissions = to.meta.permissions
    if (requiredPermissions && requiredPermissions.length > 0) {
      const hasAnyPermission = requiredPermissions.some(code => userStore.hasPermission(code))
      if (!hasAnyPermission) {
        // 无权限，跳转到仪表板
        denyToLanding()
        return
      }
    }
  }

  // 已登录用户访问登录页的处理
  if (to.name === 'login' && userStore.isAuthenticated()) {
    const token = localStorage.getItem('admin_token')
    if (token && isTokenExpired(token)) {
      userStore.clearToken()
      next({ name: 'login' })
    } else {
      next({ name: 'dashboard-main' })
    }
    return
  }

  next()
})

// v2.10.3 双语版: 依据路由 meta.titleKey 同步标签页标题（语言切换后重新导航即更新）
// t 经最小接口断言，规避 vue-i18n 消息泛型导致的深层实例化
const { t: tTitle } = i18n.global as unknown as { t: (key: string) => string }
router.afterEach((to) => {
  const titleKey = to.meta.titleKey as string | undefined
  document.title = titleKey ? `${tTitle(titleKey)} · JAiRouter` : 'JAiRouter'

  // B4（2026-11-18）：页面访问统计——回答「哪些页面真的有人用、哪些角色的入口形同虚设」。
  // 只上报已认证后的页面：登录页由 userStore.isLoggedIn() 判掉。
  // 节流（同路径 60s 一次）与失败静默都在 api 层做，这里不 await、不处理结果。
  if (to.name !== 'login') {
    const userStore = useUserStore()
    const roles = userStore.userInfo?.roles ?? []
    // 只上报「已认证后」的页面：登录页与未拿到角色时一律跳过，
    // 避免把登录过程或异常态混进热度表
    if (roles.length > 0) {
      reportPageView(to.fullPath, roles[0])
    }
  }
})

export default router