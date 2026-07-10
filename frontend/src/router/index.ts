import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '@/stores/user'

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
      meta: { requiresAuth: true },
      children: [
        {
          path: 'main',
          name: 'dashboard-main',
          component: () => import('../views/Dashboard.vue'),
          meta: { title: '仪表板', icon: 'house' }
        }
      ]
    },
    // 配置管理
    {
      path: '/config',
      name: 'config',
      component: () => import('../views/Layout.vue'),
      meta: { requiresAuth: true },
      children: [
        {
          path: 'services',
          name: 'service-management',
          component: () => import('../views/config/ServiceManagement.vue'),
          meta: { title: '服务管理', icon: 'setting' }
        },
        {
          path: 'instances',
          name: 'instance-management',
          component: () => import('../views/config/InstanceManagement.vue'),
          meta: { title: '实例管理', icon: 'cpu' }
        },
        {
          path: 'versions',
          name: 'version-management',
          component: () => import('../views/config/VersionManagement.vue'),
          meta: { title: '版本管理', icon: 'document' }
        },
        {
          path: 'state-persistence',
          name: 'state-persistence-config',
          component: () => import('../views/config/StatePersistenceManagement.vue'),
          meta: { title: '状态持久化', icon: 'folder-opened' }
        }
      ]
    },
    // 负载均衡器管理
    {
      path: '/load-balancers',
      name: 'load-balancers',
      component: () => import('../views/Layout.vue'),
      redirect: '/load-balancers/monitoring',
      meta: { requiresAuth: true },
      children: [
        {
          path: 'monitoring',
          name: 'load-balancer-monitoring',
          component: () => import('../views/load-balancer/Monitoring.vue'),
          meta: { title: '实时监控', icon: 'monitor' }
        },
        {
          path: 'strategy-config',
          name: 'load-balancer-strategy-config',
          component: () => import('../views/load-balancer/StrategyConfig.vue'),
          meta: { title: '策略配置', icon: 'setting' }
        }
      ]
    },
    // 兼容旧路由
    {
      path: '/load-balancers/management',
      redirect: '/load-balancers/monitoring'
    },
    // 熔断器管理
    {
      path: '/circuit-breakers',
      name: 'circuit-breakers',
      component: () => import('../views/Layout.vue'),
      redirect: '/circuit-breakers/monitoring',
      meta: { requiresAuth: true },
      children: [
        {
          path: 'monitoring',
          name: 'circuit-breaker-monitoring',
          component: () => import('../views/circuit-breaker/Monitoring.vue'),
          meta: { title: '实时监控', icon: 'monitor' }
        },
        {
          path: 'history',
          name: 'circuit-breaker-history',
          component: () => import('../views/circuit-breaker/History.vue'),
          meta: { title: '历史记录', icon: 'document' }
        },
        {
          path: 'global-config',
          name: 'circuit-breaker-global-config',
          component: () => import('../views/circuit-breaker/GlobalConfig.vue'),
          meta: { title: '全局配置', icon: 'setting' }
        }
      ]
    },
    // 兼容旧路由
    {
      path: '/circuit-breakers/management',
      redirect: '/circuit-breakers/monitoring'
    },
    // 安全管理
    {
      path: '/security',
      name: 'security',
      component: () => import('../views/Layout.vue'),
      meta: { requiresAuth: true },
      children: [
        {
          path: 'api-keys',
          name: 'api-key-management',
          component: () => import('../views/security/ApiKeyManagement.vue'),
          meta: { title: 'API密钥管理', icon: 'key' }
        },
        {
          path: 'jwt-tokens',
          name: 'jwt-token-management',
          component: () => import('../views/security/JwtTokenManagement.vue'),
          meta: { title: 'JWT令牌管理', icon: 'lock' }
        },
        {
          path: 'blacklist',
          name: 'blacklist-management',
          component: () => import('../views/security/BlacklistManagement.vue'),
          meta: { title: '安全黑名单', icon: 'warning' }
        },
        {
          path: 'audit-logs',
          name: 'audit-log-management',
          component: () => import('../views/security/AuditLogManagement.vue'),
          meta: { title: '审计日志', icon: 'document-checked' }
        }
      ]
    },
    // 系统管理
    {
      path: '/system',
      name: 'system',
      component: () => import('../views/Layout.vue'),
      meta: { requiresAuth: true },
      children: [
        {
          path: 'accounts',
          name: 'account-management',
          component: () => import('../views/security/JwtAccountManagement.vue'),
          meta: { title: '账户管理', icon: 'user' }
        }
      ]
    },
    // 追踪管理 - 重构后的结构
    {
      path: '/tracing',
      name: 'tracing',
      component: () => import('../views/Layout.vue'),
      redirect: '/tracing/dashboard',
      meta: { requiresAuth: true },
      children: [
        {
          path: 'dashboard',
          name: 'tracing-dashboard',
          component: () => import('../views/tracing/Dashboard.vue'),
          meta: { title: '追踪仪表盘', icon: 'connection' }
        },
        {
          path: 'search',
          name: 'tracing-search',
          component: () => import('../views/tracing/Search.vue'),
          meta: { title: '链路追踪', icon: 'search' }
        },
        {
          path: 'management',
          name: 'tracing-management',
          component: () => import('../views/tracing/Management.vue'),
          meta: { title: '追踪配置', icon: 'setting' }
        }
      ]
    },
    // 兼容旧路由
    {
      path: '/tracing/overview',
      redirect: '/tracing/dashboard'
    },
    {
      path: '/tracing/performance',
      redirect: '/tracing/dashboard'
    },
    // 兼容 /admin/admin/tracing 的错误路径
    {
      path: '/admin/tracing/:pathMatch(.*)*',
      redirect: to => `/tracing/${to.params.pathMatch || 'dashboard'}`
    },
    // AI 试验场 - 各服务作为独立子路由
    {
      path: '/playground',
      name: 'playground',
      component: () => import('../views/Layout.vue'),
      redirect: '/playground/chat',
      meta: { requiresAuth: true },
      children: [
        {
          path: 'chat',
          name: 'playground-chat',
          component: () => import('../views/playground/components/chat/ChatContainer.vue'),
          meta: { title: '对话测试', icon: 'chat-dot-round' }
        },
        {
          path: 'embedding',
          name: 'playground-embedding',
          component: () => import('../views/playground/components/embedding/EmbeddingContainer.vue'),
          meta: { title: '向量生成', icon: 'data-line' }
        },
        {
          path: 'rerank',
          name: 'playground-rerank',
          component: () => import('../views/playground/components/rerank/RerankContainer.vue'),
          meta: { title: '重排序', icon: 'sort' }
        },
        {
          path: 'audio',
          name: 'playground-audio',
          component: () => import('../views/playground/components/audio/AudioContainer.vue'),
          meta: { title: '语音服务', icon: 'headset' }
        },
        {
          path: 'image',
          name: 'playground-image',
          component: () => import('../views/playground/components/image/ImageContainer.vue'),
          meta: { title: '图像服务', icon: 'picture' }
        }
      ]
    },
    // 兼容旧路径
    {
      path: '/playground/main',
      redirect: '/playground/chat'
    },
    // 兼容 /admin/playground 路径
    {
      path: '/admin/playground/:pathMatch(.*)*',
      redirect: '/playground/chat'
    },
    // 异常管理
    {
      path: '/exceptions',
      name: 'exceptions',
      component: () => import('../views/Layout.vue'),
      redirect: '/exceptions/list',
      meta: { requiresAuth: true },
      children: [
        {
          path: 'list',
          name: 'exception-list',
          component: () => import('../views/exception/ExceptionManagement.vue'),
          meta: { title: '异常事件管理', icon: 'warning' }
        },
        {
          path: 'detail/:id',
          name: 'exception-detail',
          component: () => import('../views/exception/ExceptionDetail.vue'),
          meta: { title: '异常事件详情', icon: 'document-checked' }
        },
        {
          path: 'statistics',
          name: 'exception-statistics',
          component: () => import('../views/exception/ExceptionStatistics.vue'),
          meta: { title: '异常统计分析', icon: 'data-analysis' }
        }
      ]
    },
    // 限流器管理
    {
      path: '/rate-limiters',
      name: 'rate-limiters',
      component: () => import('../views/Layout.vue'),
      redirect: '/rate-limiters/monitoring',
      meta: { requiresAuth: true },
      children: [
        {
          path: 'monitoring',
          name: 'rate-limiter-monitoring',
          component: () => import('../views/rate-limiter/Monitoring.vue'),
          meta: { title: '实时监控', icon: 'monitor' }
        }
      ]
    },
    // 兼容旧路由
    {
      path: '/rate-limiters/management',
      redirect: '/rate-limiters/monitoring'
    },
    // API 调用历史路由
    {
      path: '/call-history',
      name: 'call-history',
      component: () => import('../views/Layout.vue'),
      redirect: '/call-history/dashboard',
      meta: { requiresAuth: true },
      children: [
        {
          path: 'dashboard',
          name: 'call-history-dashboard',
          component: () => import('../views/callHistory/Dashboard.vue'),
          meta: { title: '调用历史仪表盘', icon: 'data-analysis' }
        },
        {
          path: 'list',
          name: 'call-history-list',
          component: () => import('../views/callHistory/CallHistoryList.vue'),
          meta: { title: '调用历史列表', icon: 'list' }
        },
        {
          path: 'slow-calls',
          name: 'call-history-slow-calls',
          component: () => import('../views/callHistory/CallHistorySlowCalls.vue'),
          meta: { title: '慢调用', icon: 'timer' }
        },
        {
          path: 'token-usage',
          name: 'call-history-token-usage',
          component: () => import('../views/callHistory/TokenUsageStatistics.vue'),
          meta: { title: 'Token 统计', icon: 'DataAnalysis' }
        }
      ]
    }
  ]
})

// 路由守卫
router.beforeEach((to, from, next) => {
  const userStore = useUserStore()

  // 添加调试信息
  console.log('路由守卫触发:', {
    to: to.path,
    from: from.path,
    fullPath: to.fullPath
  })
  console.log('用户认证状态:', userStore.isAuthenticated())

  // 特殊处理根路径的重定向
  if (to.path === '/') {
    console.log('根路径重定向到仪表板')
    next({ name: 'dashboard-main' })
    return
  }

  // 检查是否需要认证
  if (to.meta.requiresAuth !== false) {
    // 检查是否有token
    if (!userStore.isAuthenticated()) {
      console.log('需要认证但未登录，重定向到登录页')
      next({ name: 'login' })
      return
    }

    // 检查token是否过期（超过1小时）
    const token = localStorage.getItem('admin_token')
    if (token && isTokenExpired(token)) {
      console.log('Token已过期，清除token并重定向到登录页')
      userStore.clearToken()
      next({ name: 'login' })
      return
    }
  }

  // 已登录用户访问登录页的处理
  if (to.name === 'login' && userStore.isAuthenticated()) {
    // 检查token是否过期
    const token = localStorage.getItem('admin_token')
    if (token && isTokenExpired(token)) {
      console.log('已登录但token过期，清除token并重定向到登录页')
      userStore.clearToken()
      next({ name: 'login' })
    } else {
      // token有效，重定向到仪表板
      console.log('已登录用户访问登录页，重定向到仪表板')
      next({ name: 'dashboard-main' })
    }
    return
  }

  console.log('正常路由跳转')
  next()
})

export default router