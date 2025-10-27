<template>
  <el-container class="layout-container">
    <el-aside width="200px">
      <el-menu :default-active="activeMenu" :default-openeds="defaultOpeneds" class="layout-menu"
        background-color="#545c64" text-color="#fff" active-text-color="#ffd04b" :router="false"
        @select="handleMenuSelect">
        <div class="logo">
          <div class="logo-icon">
            <el-icon :size="32">
              <Connection />
            </el-icon>
          </div>
          <h2 class="logo-text">JAiRouter</h2>
        </div>

        <el-sub-menu index="dashboard">
          <template #title>
            <el-icon>
              <House />
            </el-icon>
            <span>概览</span>
          </template>
          <el-menu-item index="/dashboard/main">仪表板</el-menu-item>
        </el-sub-menu>

        <!-- 配置管理 -->
        <el-sub-menu index="config">
          <template #title>
            <el-icon>
              <Setting />
            </el-icon>
            <span>配置管理</span>
          </template>
          <el-menu-item index="/config/services">服务管理</el-menu-item>
          <el-menu-item index="/config/instances">实例管理</el-menu-item>
          <el-menu-item index="/config/versions">版本管理</el-menu-item>
          <el-menu-item index="/config/merge">配置合并</el-menu-item>
        </el-sub-menu>

        <!-- 安全管理 -->
        <el-sub-menu index="security">
          <template #title>
            <el-icon>
              <Lock />
            </el-icon>
            <span>安全管理</span>
          </template>
          <el-menu-item index="/security/api-keys">API密钥管理</el-menu-item>
          <el-menu-item index="/security/jwt-tokens">JWT令牌管理</el-menu-item>
          <el-menu-item index="/security/audit-logs">审计日志</el-menu-item>
        </el-sub-menu>

        <!-- 系统管理 -->
        <el-sub-menu index="system">
          <template #title>
            <el-icon>
              <User />
            </el-icon>
            <span>系统管理</span>
          </template>
          <el-menu-item index="/system/accounts">账户管理</el-menu-item>
        </el-sub-menu>

        <!-- 追踪管理 -->
        <el-sub-menu index="tracing">
          <template #title>
            <el-icon>
              <Connection />
            </el-icon>
            <span>追踪管理</span>
          </template>
          <el-menu-item index="/tracing/overview">追踪概览</el-menu-item>
          <el-menu-item index="/tracing/search">追踪搜索</el-menu-item>
          <el-menu-item index="/tracing/performance">性能分析</el-menu-item>
          <el-menu-item index="/tracing/management">追踪管理</el-menu-item>
        </el-sub-menu>

        <!-- API测试试验场 -->
        <el-sub-menu index="playground">
          <template #title>
            <el-icon>
              <Monitor />
            </el-icon>
            <span>API测试试验场</span>
          </template>
          <el-menu-item index="/playground/main">API测试试验场</el-menu-item>
        </el-sub-menu>
      </el-menu>
    </el-aside>

    <el-container>
      <el-header class="layout-header">
        <div class="header-left">
          <el-breadcrumb separator="/">
            <el-breadcrumb-item v-for="item in breadcrumbs" :key="item.path" :to="item.path">
              {{ item.title }}
            </el-breadcrumb-item>
          </el-breadcrumb>
        </div>
        <div class="header-right">
          <el-dropdown @command="handleUserCommand">
            <span class="user-info">
              <el-avatar :size="30" icon="UserFilled" />
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
        <router-view :key="route.fullPath" />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import {
  House,
  Setting,
  Lock,
  User,
  Connection,
  Monitor
} from '@element-plus/icons-vue'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

// 防止快速连续点击导致的导航问题
const isNavigating = ref(false)

// 计算当前激活的菜单项
const activeMenu = computed(() => {
  const { path } = route
  // 对于根路径，激活概览菜单项
  if (path === '/') {
    return '/dashboard/main'
  }
  // 对于仪表板路径，激活概览菜单项
  if (path === '/dashboard/main') {
    return '/dashboard/main'
  }
  return path
})

// 计算默认打开的子菜单
const defaultOpeneds = computed(() => {
  const { path } = route
  const openeds = []

  if (path === '/dashboard/main') {
    openeds.push('dashboard')
  } else if (path.startsWith('/config')) {
    openeds.push('config')
  } else if (path.startsWith('/security')) {
    openeds.push('security')
  } else if (path.startsWith('/system')) {
    openeds.push('system')
  } else if (path.startsWith('/tracing')) {
    openeds.push('tracing')
  } else if (path.startsWith('/playground')) {
    openeds.push('playground')
  }

  return openeds
})

// 面包屑导航
const breadcrumbs = computed(() => {
  const pathArray = route.path.split('/').filter(item => item)
  const breadcrumbArray = []

  // 添加首页面包屑
  breadcrumbArray.push({ path: '/', title: '首页' })

  // 特殊处理仪表板页面
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
        title: (routeMatched.meta?.title as string) || (routeMatched.name as string)
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
            title: (childRoute.meta?.title as string) || (childRoute.name as string)
          })
        }
      }
    }
  }

  return breadcrumbArray
})

// 处理菜单选择
const handleMenuSelect = async (index: string) => {
  console.log('菜单选择:', index)

  // 防止快速连续点击
  if (isNavigating.value) {
    return
  }

  // 如果已经在目标路由，不进行跳转
  if (route.path === index) {
    return
  }

  isNavigating.value = true

  try {
    // 使用编程式导航而不是让Element Plus处理
    await router.push(index)
  } catch (error: any) {
    console.error('路由跳转失败:', error)
    // 如果是导航重复错误，可以忽略
    if (error?.name !== 'NavigationDuplicated') {
      console.error('Navigation error:', error)
    }
  } finally {
    // 延迟重置导航状态，防止过快的连续点击
    setTimeout(() => {
      isNavigating.value = false
    }, 300)
  }
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
  background: linear-gradient(135deg, #2c3e50 0%, #34495e 100%);
  box-shadow: inset 0 0 20px rgba(0, 0, 0, 0.3);
}

.layout-menu {
  height: 100vh;
  background: linear-gradient(135deg, #2c3e50 0%, #34495e 100%);
  border-right: none;
  box-shadow: 2px 0 10px rgba(0, 0, 0, 0.2);
  transition: all 0.3s ease;
}

.layout-menu:hover {
  box-shadow: 4px 0 20px rgba(0, 0, 0, 0.3);
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