import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { i18n } from '@/i18n'

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
      children: []
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
          meta: { titleKey: 'route.dashboardMain', icon: 'house' }
        }
      ]
    },
    // 配置管理
    {
      path: '/config',
      name: 'config',
      component: () => import('../views/Layout.vue'),
      meta: { requiresAuth: true, titleKey: 'route.config' },
      children: [
        {
          path: 'services',
          name: 'service-management',
          component: () => import('../views/config/ServiceManagement.vue'),
          meta: { titleKey: 'route.serviceManagement', icon: 'setting' }
        },
        {
          path: 'instances',
          name: 'instance-management',
          component: () => import('../views/config/InstanceManagement.vue'),
          meta: { titleKey: 'route.instanceManagement', icon: 'cpu' }
        },
        {
          path: 'versions',
          name: 'version-management',
          component: () => import('../views/config/VersionManagement.vue'),
          meta: { titleKey: 'route.versionManagement', icon: 'document' }
        },
        {
          path: 'state-persistence',
          name: 'state-persistence-config',
          component: () => import('../views/config/StatePersistenceManagement.vue'),
          meta: { titleKey: 'route.statePersistenceConfig', icon: 'folder-opened' }
        },
        {
          path: 'adapters',
          name: 'adapter-management',
          component: () => import('../views/config/AdapterManagement.vue'),
          meta: { titleKey: 'route.adapterManagement', icon: 'connection' }
        },
        {
          path: 'rules',
          name: 'rule-management',
          component: () => import('../views/config/rules/RuleManagement.vue'),
          meta: { titleKey: 'route.ruleManagement', icon: 'set-up' }
        },
        {
          path: 'pools',
          name: 'pool-management',
          component: () => import('../views/config/pools/PoolManagement.vue'),
          meta: { titleKey: 'route.poolManagement', icon: 'box' }
        },
        {
          path: 'cache',
          name: 'response-cache-management',
          component: () => import('../views/config/ResponseCacheManagement.vue'),
          meta: { titleKey: 'route.responseCacheManagement', icon: 'coin', permissions: ['config:cache:write'] }
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
          meta: { titleKey: 'route.loadBalancerMonitoring', icon: 'monitor' }
        },
        {
          path: 'strategy-config',
          name: 'load-balancer-strategy-config',
          component: () => import('../views/load-balancer/StrategyConfig.vue'),
          meta: { titleKey: 'route.loadBalancerStrategyConfig', icon: 'setting' }
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
          meta: { titleKey: 'route.circuitBreakerMonitoring', icon: 'monitor' }
        },
        {
          path: 'history',
          name: 'circuit-breaker-history',
          component: () => import('../views/circuit-breaker/History.vue'),
          meta: { titleKey: 'route.circuitBreakerHistory', icon: 'document' }
        },
        {
          path: 'global-config',
          name: 'circuit-breaker-global-config',
          component: () => import('../views/circuit-breaker/GlobalConfig.vue'),
          meta: { titleKey: 'route.circuitBreakerGlobalConfig', icon: 'setting' }
        }
      ]
    },

    // 安全管理
    {
      path: '/security',
      name: 'security',
      component: () => import('../views/Layout.vue'),
      meta: { requiresAuth: true, roles: ['ADMIN'], titleKey: 'route.security' },
      children: [
        {
          path: 'api-keys',
          name: 'api-key-management',
          component: () => import('../views/security/ApiKeyManagement.vue'),
          meta: { titleKey: 'route.apiKeyManagement', icon: 'key', permissions: ['security:apikeys:manage'] }
        },
        {
          path: 'jwt-tokens',
          name: 'jwt-token-management',
          component: () => import('../views/security/JwtTokenManagement.vue'),
          meta: { titleKey: 'route.jwtTokenManagement', icon: 'lock', permissions: ['security:jwttokens:manage'] }
        },
        {
          path: 'blacklist',
          name: 'blacklist-management',
          component: () => import('../views/security/BlacklistManagement.vue'),
          meta: { titleKey: 'route.blacklistManagement', icon: 'warning', permissions: ['security:blacklist:manage'] }
        },
        {
          path: 'audit-logs',
          name: 'audit-log-management',
          component: () => import('../views/security/AuditLogManagement.vue'),
          meta: { titleKey: 'route.auditLogManagement', icon: 'document-checked', permissions: ['security:audit:read'] }
        }
      ]
    },
    // 系统管理
    {
      path: '/system',
      name: 'system',
      component: () => import('../views/Layout.vue'),
      meta: { requiresAuth: true, roles: ['ADMIN'], titleKey: 'route.system' },
      children: [
        {
          path: 'accounts',
          name: 'account-management',
          component: () => import('../views/security/JwtAccountManagement.vue'),
          meta: { titleKey: 'route.accountManagement', icon: 'user', permissions: ['system:accounts:manage'] }
        },
        {
          path: 'permissions',
          name: 'permission-management',
          component: () => import('../views/system/PermissionManagement.vue'),
          meta: { titleKey: 'route.permissionManagement', icon: 'key', permissions: ['system:permissions:manage'] }
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
          meta: { titleKey: 'route.tracingDashboard', icon: 'connection' }
        },
        {
          path: 'search',
          name: 'tracing-search',
          component: () => import('../views/tracing/Search.vue'),
          meta: { titleKey: 'route.tracingSearch', icon: 'search' }
        },
        {
          path: 'management',
          name: 'tracing-management',
          component: () => import('../views/tracing/Management.vue'),
          meta: { titleKey: 'route.tracingManagement', icon: 'setting' }
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
          meta: { titleKey: 'route.playgroundChat', icon: 'chat-dot-round' }
        },
        {
          path: 'embedding',
          name: 'playground-embedding',
          component: () => import('../views/playground/components/embedding/EmbeddingContainer.vue'),
          meta: { titleKey: 'route.playgroundEmbedding', icon: 'data-line' }
        },
        {
          path: 'rerank',
          name: 'playground-rerank',
          component: () => import('../views/playground/components/rerank/RerankContainer.vue'),
          meta: { titleKey: 'route.playgroundRerank', icon: 'sort' }
        },
        {
          path: 'audio',
          name: 'playground-audio',
          component: () => import('../views/playground/components/audio/AudioContainer.vue'),
          meta: { titleKey: 'route.playgroundAudio', icon: 'headset' }
        },
        {
          path: 'image',
          name: 'playground-image',
          component: () => import('../views/playground/components/image/ImageContainer.vue'),
          meta: { titleKey: 'route.playgroundImage', icon: 'picture' }
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
          meta: { titleKey: 'route.exceptionList', icon: 'warning' }
        },
        {
          path: 'detail/:id',
          name: 'exception-detail',
          component: () => import('../views/exception/ExceptionDetail.vue'),
          meta: { titleKey: 'route.exceptionDetail', icon: 'document-checked' }
        },
        {
          path: 'statistics',
          name: 'exception-statistics',
          component: () => import('../views/exception/ExceptionStatistics.vue'),
          meta: { titleKey: 'route.exceptionStatistics', icon: 'data-analysis' }
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
          meta: { titleKey: 'route.rateLimiterMonitoring', icon: 'monitor' }
        }
      ]
    },

    // 监控分析
    {
      path: '/monitoring',
      name: 'monitoring',
      component: () => import('../views/Layout.vue'),
      meta: { requiresAuth: true, titleKey: 'route.monitoring' },
      children: [
        {
          path: 'slow-queries',
          name: 'slow-query-analysis',
          component: () => import('../views/monitoring/SlowQueryAnalysis.vue'),
          meta: { titleKey: 'route.slowQueryAnalysis', icon: 'timer', permissions: ['monitoring:slowquery:read'] }
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
          meta: { titleKey: 'route.callHistoryDashboard', icon: 'data-analysis' }
        },
        {
          path: 'list',
          name: 'call-history-list',
          component: () => import('../views/callHistory/CallHistoryList.vue'),
          meta: { titleKey: 'route.callHistoryList', icon: 'list' }
        },
        {
          path: 'slow-calls',
          name: 'call-history-slow-calls',
          component: () => import('../views/callHistory/CallHistorySlowCalls.vue'),
          meta: { titleKey: 'route.callHistorySlowCalls', icon: 'timer' }
        },
        {
          path: 'token-usage',
          name: 'call-history-token-usage',
          component: () => import('../views/callHistory/TokenUsageStatistics.vue'),
          meta: { titleKey: 'route.callHistoryTokenUsage', icon: 'DataAnalysis' }
        }
      ]
    }
  ]
})

// 路由守卫
router.beforeEach((to, from, next) => {
  const userStore = useUserStore()

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
    const requiredRoles = (to.meta as any).roles as string[] | undefined
    if (requiredRoles && requiredRoles.length > 0) {
      const userRoles = userStore.userInfo?.roles || []
      const hasRole = requiredRoles.some(role => userRoles.includes(role))
      if (!hasRole) {
        // 无权限，跳转到仪表板
        next({ name: 'dashboard-main' })
        return
      }
    }

    // v2.9.8 RBAC: 检查权限码（meta.permissions，需全部拥有；ADMIN 恒通过）
    const requiredPermissions = (to.meta as any).permissions as string[] | undefined
    if (requiredPermissions && requiredPermissions.length > 0) {
      const hasAllPermissions = requiredPermissions.every(code => userStore.hasPermission(code))
      if (!hasAllPermissions) {
        // 无权限，跳转到仪表板
        next({ name: 'dashboard-main' })
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
})

export default router