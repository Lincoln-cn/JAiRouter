import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '@/stores/user'

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
      redirect: '/dashboard'
    },
    {
      path: '/dashboard',
      name: 'dashboard',
      component: () => import('../views/Dashboard.vue'),
      meta: { requiresAuth: true }
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
  
  if (to.meta.requiresAuth !== false && !userStore.isAuthenticated()) {
    // 需要认证但未登录，跳转到登录页
    console.log('需要认证但未登录，重定向到登录页')
    next('/login')
  } else if (to.path === '/login' && userStore.isAuthenticated()) {
    // 已登录用户访问登录页，跳转到首页
    console.log('已登录用户访问登录页，重定向到仪表板')
    next('/dashboard')
  } else {
    console.log('正常路由跳转')
    next()
  }
})

export default router