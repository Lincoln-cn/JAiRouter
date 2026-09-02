<template>
  <el-container class="layout-container">
    <el-aside width="200px" class="layout-aside">
      <div class="logo">
        <div class="logo-icon">
          <el-icon :size="32">
            <Connection />
          </el-icon>
        </div>
        <h2 class="logo-text">JAiRouter</h2>
      </div>

      <el-menu :default-active="activeMenu" :default-openeds="defaultOpeneds" class="layout-menu"
        background-color="transparent" text-color="var(--ja-sidebar-text)" active-text-color="var(--ja-sidebar-active)" :router="false"
        @select="handleMenuSelect">
        <el-sub-menu v-for="group in filteredMenuGroups" :key="group.index" :index="group.index">
          <template #title>
            <el-icon>
              <component :is="groupIconMap[group.icon]" />
            </el-icon>
            <span>{{ group.title }}</span>
          </template>
          <el-menu-item v-for="item in group.children" :key="item.path" :index="item.path">
            <el-icon v-if="item.icon">
              <component :is="itemIconMap[item.icon]" />
            </el-icon>
            {{ item.title }}
          </el-menu-item>
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
          <el-button class="theme-toggle" circle @click="toggleTheme">
            <el-icon :size="18">
              <Moon v-if="!isDark" />
              <Sunny v-else />
            </el-icon>
          </el-button>
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
import { computed, ref, type Component } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { useTheme } from '@/composables/useTheme'
import { usePermission } from '@/composables/usePermission'
import { menuGroups } from '@/config/menu'
import {
  House,
  Setting,
  Lock,
  User,
  Connection,
  Monitor,
  ChatDotRound,
  DataLine,
  Sort,
  Headset,
  Picture,
  Position,
  Document,
  Moon,
  Sunny
} from '@element-plus/icons-vue'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const { isDark, toggleTheme } = useTheme()
const { filterMenuByPermission } = usePermission()

// v2.9.8 Phase 4: 菜单数据驱动 + 权限过滤（menu.ts 提供 8 组 34 项结构与权限码）
const filteredMenuGroups = computed(() => filterMenuByPermission(menuGroups))

// 组图标名 → 图标组件（menu.ts 的 icon 为 kebab-case 名）
const groupIconMap: Record<string, Component> = {
  house: House,
  setting: Setting,
  connection: Connection,
  document: Document,
  position: Position,
  lock: Lock,
  user: User,
  monitor: Monitor
}

// 子项图标名 → 图标组件（仅 AI 试验场子项使用）
const itemIconMap: Record<string, Component> = {
  'chat-dot-round': ChatDotRound,
  'data-line': DataLine,
  sort: Sort,
  headset: Headset,
  picture: Picture
}

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

// 计算默认打开的子菜单（v2.9.8 Phase 4：8 组结构，/config 子路由按组拆分归属）
const defaultOpeneds = computed(() => {
  const { path } = route
  const openeds: string[] = []

  if (path === '/dashboard/main') {
    openeds.push('dashboard')
  } else if (path.startsWith('/config/state-persistence')) {
    // 状态持久化归属「系统管理」
    openeds.push('system')
  } else if (path.startsWith('/config/rules') || path.startsWith('/config/pools')) {
    // 路由规则 / 资源池归属「流量治理」
    openeds.push('traffic')
  } else if (path.startsWith('/config')) {
    // 服务 / 实例 / 版本 / Adapter 归属「模型服务」
    openeds.push('model-services')
  } else if (
    path.startsWith('/load-balancers') ||
    path.startsWith('/circuit-breakers') ||
    path.startsWith('/rate-limiters')
  ) {
    openeds.push('traffic')
  } else if (path.startsWith('/call-history') || path.startsWith('/exceptions')) {
    openeds.push('records')
  } else if (path.startsWith('/tracing')) {
    openeds.push('tracing')
  } else if (path.startsWith('/security')) {
    openeds.push('security')
  } else if (path.startsWith('/system')) {
    openeds.push('system')
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
    path += `/${  pathArray[i]}`
    const routeMatched = router.options.routes?.find(r => r.path === path)
    if (routeMatched) {
      breadcrumbArray.push({
        path,
        title: (routeMatched.meta?.title as string) || (routeMatched.name as string)
      })
    } else {
      // 查找子路由
      const parentPath = pathArray.slice(0, i).join('/')
      const parentRoute = router.options.routes?.find(r => r.path === `/${  parentPath}`)
      if (parentRoute && parentRoute.children) {
        const childRoute = parentRoute.children.find(child => child.path === pathArray[i])
        if (childRoute) {
          breadcrumbArray.push({
            path,
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
const handleUserCommand = async (command: string) => {
  if (command === 'logout') {
    await userStore.logout()
    router.push({ name: 'login' })
  } else if (command === 'profile') {
    // 跳转到个人资料页面
    console.log('跳转到个人资料页面')
  }
}
</script>

<style scoped>
.layout-container {
  height: 100vh;
  background: var(--ja-sidebar-bg);
  box-shadow: inset 0 0 20px rgba(0, 0, 0, 0.3);
  overflow: hidden;
}

/* 侧栏固定列：渐变背景挂在「不滚动」的全高容器上，
   任何滚动位置背景都铺满可见区域，子菜单/折叠层透明叠放不会断层 */
.layout-aside {
  display: flex;
  flex-direction: column;
  /* 覆盖 EP 默认 .el-aside { overflow: auto }，避免侧栏整体滚动 */
  overflow: hidden;
  background-image: var(--ja-sidebar-bg);
  background-color: var(--ja-sidebar-solid-bg);
  box-shadow:
    inset 0 0 20px rgba(0, 0, 0, 0.3),
    2px 0 10px rgba(0, 0, 0, 0.2);
  transition: box-shadow 0.3s ease;
}

.layout-aside:hover {
  box-shadow:
    inset 0 0 20px rgba(0, 0, 0, 0.3),
    4px 0 20px rgba(0, 0, 0, 0.3);
}

/* 菜单区 = 独立滚动容器（logo 固定不动，仅此项区域滚动）；
   背景透明，露出 .layout-aside 的固定渐变，滚动时无缝 */
.layout-menu {
  flex: 1 1 auto;
  min-height: 0;
  overflow-y: auto;
  background-color: transparent;
  border-right: none;
}

.logo {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-direction: column;
  padding: 20px 0;
  background: var(--ja-sidebar-logo-bg);
  border-bottom: 1px solid var(--ja-sidebar-logo-border);
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.2);
}

.logo-icon {
  margin-bottom: 10px;
  color: var(--ja-primary);
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
  color: var(--ja-sidebar-text);
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
  background-color: var(--ja-bg-page);
  border-bottom: 1px solid var(--ja-border);
  padding: 0 20px;
}

.theme-toggle {
  margin-right: 12px;
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
  background-color: var(--ja-main-bg);
  padding: 20px;
}
</style>