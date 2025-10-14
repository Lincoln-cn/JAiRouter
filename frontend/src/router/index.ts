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
          path: 'merge',
          name: 'config-merge-management',
          component: () => import('../views/config/ConfigMergeManagement.vue'),
          meta: { title: '配置合并', icon: 'files' }
        }
      ]
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
    // 监控管理
    {
      path: '/monitoring',
      name: 'monitoring',
      component: () => import('../views/Layout.vue'),
      meta: { requiresAuth: true },
      children: [
        {
          path: 'overview',
          name: 'monitoring-overview',
          component: () => import('../views/monitoring/Overview.vue'),
          meta: { title: '监控概览', icon: 'data-analysis' }
        },
        {
          path: 'circuit-breaker',
          name: 'circuit-breaker',
          component: () => import('../views/monitoring/CircuitBreaker.vue'),
          meta: { title: '熔断器', icon: 'help' }
        },
        {
          path: 'health',
          name: 'health-check',
          component: () => import('../views/monitoring/HealthCheck.vue'),
          meta: { title: '健康检查', icon: 'monitor' }
        },
        {
          path: 'system',
          name: 'system-monitoring',
          component: () => import('../views/monitoring/SystemMonitoring.vue'),
          meta: { title: '系统监控', icon: 'data-board' }
        }
      ]
    },
    // 追踪管理
    {
      path: '/tracing',
      name: 'tracing',
      component: () => import('../views/Layout.vue'),
      meta: { requiresAuth: true },
      children: [
        {
          path: 'overview',
          name: 'tracing-overview',
          component: () => import('../views/tracing/Overview.vue'),
          meta: { title: '追踪概览', icon: 'connection' }
        },
        {
          path: 'search',
          name: 'tracing-search',
          component: () => import('../views/tracing/Search.vue'),
          meta: { title: '追踪搜索', icon: 'search' }
        },
        {
          path: 'performance',
          name: 'tracing-performance',
          component: () => import('../views/tracing/Performance.vue'),
          meta: { title: '性能分析', icon: 'odometer' }
        },
        {
          path: 'management',
          name: 'tracing-management',
          component: () => import('../views/tracing/Management.vue'),
          meta: { title: '追踪管理', icon: 'setting' }
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
    next('/dashboard')
    return
  }
  
  // 检查是否需要认证
  if (to.meta.requiresAuth !== false) {
    // 检查是否有token
    if (!userStore.isAuthenticated()) {
      console.log('需要认证但未登录，重定向到登录页')
      next('/login')
      return
    }
    
    // 检查token是否过期（超过1小时）
    const token = localStorage.getItem('admin_token')
    if (token && isTokenExpired(token)) {
      console.log('Token已过期，清除token并重定向到登录页')
      userStore.clearToken()
      next('/login')
      return
    }
  }
  
  // 已登录用户访问登录页的处理
  if (to.path === '/login' && userStore.isAuthenticated()) {
    // 检查token是否过期
    const token = localStorage.getItem('admin_token')
    if (token && isTokenExpired(token)) {
      console.log('已登录但token过期，清除token并重定向到登录页')
      userStore.clearToken()
      next('/login')
    } else {
      // token有效，重定向到仪表板
      console.log('已登录用户访问登录页，重定向到仪表板')
      next('/dashboard')
    }
    return
  }
  
  console.log('正常路由跳转')
  next()
})

export default router