<template>
  <el-container class="layout-container">
    <el-aside width="200px">
      <el-menu
        :default-active="activeMenu"
        class="layout-menu"
        background-color="#545c64"
        text-color="#fff"
        active-text-color="#ffd04b"
        :router="false"
        @select="handleMenuSelect"
      >
        <div class="logo">
          <div class="logo-icon">
            <el-icon :size="32"><Connection /></el-icon>
          </div>
          <h2 class="logo-text">JAiRouter</h2>
        </div>

        <el-sub-menu index="dashboard">
          <template #title>
            <el-icon><House /></el-icon>
            <span>概览</span>
          </template>
          <el-menu-item index="/dashboard/main">仪表板</el-menu-item>
        </el-sub-menu>
        
        <!-- 配置管理 -->
        <el-sub-menu index="config">
          <template #title>
            <el-icon><Setting /></el-icon>
            <span>配置管理</span>
          </template>
          <el-menu-item index="/config/services">服务管理</el-menu-item>
          <el-menu-item index="/config/instances">实例管理</el-menu-item>
          <el-menu-item index="/config/versions">版本管理</el-menu-item>
        </el-sub-menu>
        
        <!-- 安全管理 -->
        <el-sub-menu index="security">
          <template #title>
            <el-icon><Lock /></el-icon>
            <span>安全管理</span>
          </template>
          <el-menu-item index="/security/api-keys">API密钥管理</el-menu-item>
          <el-menu-item index="/security/jwt-tokens">JWT令牌管理</el-menu-item>
          <el-menu-item index="/security/audit-logs">审计日志</el-menu-item>
        </el-sub-menu>
        
        <!-- 系统管理 -->
        <el-sub-menu index="system">
          <template #title>
            <el-icon><User /></el-icon>
            <span>系统管理</span>
          </template>
          <el-menu-item index="/system/accounts">账户管理</el-menu-item>
        </el-sub-menu>
        
        <!-- 监控管理 -->
        <el-sub-menu index="monitoring">
          <template #title>
            <el-icon><DataAnalysis /></el-icon>
            <span>监控管理</span>
          </template>
          <el-menu-item index="/monitoring/overview">监控概览</el-menu-item>
          <el-menu-item index="/monitoring/circuit-breaker">熔断器</el-menu-item>
          <el-menu-item index="/monitoring/health">健康检查</el-menu-item>
        </el-sub-menu>
        
        <!-- 追踪管理 -->
        <el-sub-menu index="tracing">
          <template #title>
            <el-icon><Connection /></el-icon>
            <span>追踪管理</span>
          </template>
          <el-menu-item index="/tracing/overview">追踪概览</el-menu-item>
          <el-menu-item index="/tracing/sampling">采样配置</el-menu-item>
          <el-menu-item index="/tracing/performance">性能分析</el-menu-item>
        </el-sub-menu>
      </el-menu>
    </el-aside>
    
    <el-container>
      <el-header class="layout-header">
        <div class="header-left">
          <el-breadcrumb separator="/">
            <el-breadcrumb-item 
              v-for="item in breadcrumbs" 
              :key="item.path" 
              :to="item.path"
            >
              {{ item.title }}
            </el-breadcrumb-item>
          </el-breadcrumb>
        </div>
        <div class="header-right">
          <el-dropdown @command="handleUserCommand">
            <span class="user-info">
              <el-avatar :size="30" :icon="UserFilled" />
              <span class="username">管理员</span>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="profile">个人资料</el-dropdown-item>
                <el-dropdown-item command="logout">退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>
      
      <el-main class="layout-main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { 
  House,
  Setting, 
  Lock, 
  DataAnalysis, 
  Connection,
  UserFilled,
  User
} from '@/icons'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

// 计算当前激活的菜单项
const activeMenu = computed(() => {
  const { path } = route
  // 对于根路径，激活概览菜单项
  if (path === '/') {
    return '/dashboard'
  }
  // 对于仪表板路径，激活概览菜单项
  if (path === '/dashboard/main') {
    return '/dashboard'
  }
  return path
})

// 面包屑导航
const breadcrumbs = computed(() => {
  const pathArray = route.path.split('/').filter(item => item)
  const breadcrumbArray = []
  
  // 添加首页面包屑
  breadcrumbArray.push({ path: '/', title: '首页' })
  
  // 如果是仪表板页面，添加概览和仪表板面包屑
  if (route.path === '/dashboard/main') {
    breadcrumbArray.push({ path: '/dashboard', title: '概览' })
    breadcrumbArray.push({ path: '/dashboard/main', title: '仪表板' })
    return breadcrumbArray
  }
  
  // 处理其他路径的面包屑
  let path = ''
  for (let i = 0; i < pathArray.length; i++) {
    path += '/' + pathArray[i]
    const routeMatched = router.options.routes?.find(r => r.path === path)
    if (routeMatched) {
      breadcrumbArray.push({
        path: path,
        title: routeMatched.meta?.title || routeMatched.name
      })
    } else {
      // 查找子路由
      const parentPath = pathArray.slice(0, i).join('/')
      const parentRoute = router.options.routes?.find(r => r.path === '/' + parentPath)
      if (parentRoute && parentRoute.children) {
        const childRoute = parentRoute.children.find(child => child.path === pathArray[i])
        if (childRoute) {
          breadcrumbArray.push({
            path: path,
            title: childRoute.meta?.title || childRoute.name
          })
        }
      }
    }
  }
  
  return breadcrumbArray
})

// 处理菜单选择
const handleMenuSelect = (index: string) => {
  console.log('菜单选择:', index)
  // 使用编程式导航而不是让Element Plus处理
  router.push(index)
}

// 处理用户命令
const handleUserCommand = (command: string) => {
  if (command === 'logout') {
    userStore.logout()
    router.push('/login')
  } else if (command === 'profile') {
    // 跳转到个人资料页面
    console.log('跳转到个人资料页面')
  }
}
</script>

<style scoped>
.layout-container {
  height: 100vh;
}

.layout-menu {
  height: 100%;
  border-right: none;
}

.logo {
  display: flex;
  align-items: center;
  justify-content: center;
  flex-direction: column;
  padding: 20px 0;
  background: linear-gradient(135deg, #434b52 0%, #2c3e50 100%);
  border-bottom: 1px solid #3d444d;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.2);
}

.logo-icon {
  margin-bottom: 10px;
  color: #409eff;
  background: rgba(64, 158, 255, 0.1);
  border-radius: 50%;
  padding: 12px;
  width: 56px;
  height: 56px;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 0 10px rgba(64, 158, 255, 0.3);
  animation: pulse 2s infinite;
}

@keyframes pulse {
  0% {
    box-shadow: 0 0 10px rgba(64, 158, 255, 0.3);
  }
  50% {
    box-shadow: 0 0 20px rgba(64, 158, 255, 0.6);
  }
  100% {
    box-shadow: 0 0 10px rgba(64, 158, 255, 0.3);
  }
}

.logo-text {
  color: #ffffff;
  font-size: 20px;
  font-weight: 600;
  margin: 0;
  text-shadow: 0 0 5px rgba(255, 255, 255, 0.3);
  letter-spacing: 1px;
}

.layout-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  background-color: #ffffff;
  border-bottom: 1px solid #e6e6e6;
  padding: 0 20px;
}

.user-info {
  display: flex;
  align-items: center;
  cursor: pointer;
}

.username {
  margin-left: 10px;
  font-size: 14px;
}

.layout-main {
  background-color: #f5f5f5;
  padding: 20px;
}
</style>