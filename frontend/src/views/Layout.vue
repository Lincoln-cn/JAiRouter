<template>
  <el-container class="layout-container">
    <el-aside id="layout-sidebar" :width="asideWidth" class="layout-aside"
      :class="{ 'is-collapsed': collapsed }">
      <div class="logo">
        <div class="logo-icon">
          <!-- 浅底圆角方块版徽标：透明圆形徽标在深色侧栏上轮廓会消失，故用带底色的组合图 -->
          <img class="logo-mark" src="@/assets/brand/logo-mark-64.png" alt="" width="32" height="32" />
        </div>
        <!-- 折叠态只留图标：64px 宽度放不下文字，且 el-menu 折叠后也只显示图标 -->
        <h2 v-if="!collapsed" class="logo-text">JAiRouter</h2>
      </div>

      <el-menu :default-active="activeMenu" :default-openeds="defaultOpeneds" :collapse="collapsed"
        :collapse-transition="false" class="layout-menu" background-color="transparent"
        text-color="var(--ja-sidebar-text)" active-text-color="var(--ja-sidebar-active)" :router="false"
        @select="handleMenuSelect">
        <el-sub-menu v-for="group in filteredMenuGroups" :key="group.index" :index="group.index">
          <template #title>
            <el-icon>
              <component :is="groupIconMap[group.icon]" />
            </el-icon>
            <span class="menu-label" :title="t(group.title)">{{ t(group.title) }}</span>
          </template>
          <el-menu-item v-for="item in group.children" :key="item.path" :index="item.path">
            <span class="menu-label" :title="t(item.title)">{{ t(item.title) }}</span>
          </el-menu-item>
        </el-sub-menu>
      </el-menu>
    </el-aside>

    <el-container class="layout-body">
      <el-header class="layout-header">
        <div class="header-left">
          <!-- 侧栏折叠开关：放在最前（面包屑之前），折叠后仍留在头部，始终可见可点。
               窄屏下侧栏是强制折叠态，开关没有意义，直接隐藏。 -->
          <el-button v-if="!isNarrow" class="sidebar-toggle" circle :aria-label="sidebarToggleLabel"
            :title="sidebarToggleLabel" :aria-expanded="!collapsed" aria-controls="layout-sidebar"
            @click="toggleSidebar">
            <el-icon :size="18">
              <Expand v-if="collapsed" />
              <Fold v-else />
            </el-icon>
          </el-button>
          <el-breadcrumb separator="/">
            <el-breadcrumb-item v-for="(item, index) in breadcrumbs" :key="`${index}-${item.title}`">
              <router-link v-if="item.path && index < breadcrumbs.length - 1" :to="item.path">
                {{ displayTitle(item.title) }}
              </router-link>
              <span v-else>{{ displayTitle(item.title) }}</span>
            </el-breadcrumb-item>
          </el-breadcrumb>
          <!-- 多 tab 菜单项的切换条：40 → 28 后被收起的页面在这里进入，URL 不变 -->
          <div v-if="currentTabs.length > 1" class="nav-tabs" role="tablist">
            <button v-for="tab in currentTabs" :key="tab.key" type="button" role="tab" class="nav-tab"
              :class="{ active: tab.to === route.path }" :aria-selected="tab.to === route.path"
              @click="handleTabSelect(tab.to)">
              {{ displayTitle(tab.labelKey) }}
            </button>
          </div>
        </div>
        <div class="header-right">
          <StatusBar />
          <GlobalSearch ref="globalSearchRef" />
          <LanguageSwitcher class="lang-toggle" />
          <el-button class="theme-toggle" circle @click="toggleTheme">
            <el-icon :size="18">
              <Moon v-if="!isDark" />
              <Sunny v-else />
            </el-icon>
          </el-button>
          <el-dropdown @command="handleUserCommand">
            <span class="user-info">
              <el-avatar :size="30" icon="UserFilled" />
              <span class="username">{{ displayName }}</span>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="profile">{{ t('layout.profile') }}</el-dropdown-item>
                <el-dropdown-item command="logout">{{ t('layout.logout') }}</el-dropdown-item>
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
import { computed, onBeforeUnmount, onMounted, ref, type Component } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useUserStore } from '@/stores/user'
import { useTheme } from '@/composables/useTheme'
import { usePermission } from '@/composables/usePermission'
import { useViewport } from '@/composables/useViewport'
import {
  menuGroups,
  findMenuEntry,
  findMenuTab,
  resolveMenuPath
} from '@/config/menu'
import type { MenuItem } from '@/config/menu'
import LanguageSwitcher from '@/components/LanguageSwitcher.vue'
import StatusBar from '@/components/StatusBar.vue'
import GlobalSearch from '@/components/GlobalSearch.vue'
import {
  House,
  Setting,
  Lock,
  User,
  Connection,
  DataAnalysis,
  Tools,
  Moon,
  Sunny,
  Expand,
  Fold
} from '@element-plus/icons-vue'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const { t, te } = useI18n()
const { isDark, toggleTheme } = useTheme()
const { filterMenuByPermission, getAccessibleTabs, resolveLandingPath } = usePermission()

// v2.10.3: 显示真实用户名（JWT sub），缺省回退「管理员」
const displayName = computed(() => userStore.userInfo?.username || t('layout.admin'))

/* ========== ⌘K / Ctrl+K 全局搜索 ==========
 * 快捷键挂在 document 上：输入框聚焦时也要能唤起（命令面板的常见行为），
 * 面板自身负责 Esc 关闭与 ↑↓/Enter 导航，这里只负责打开。
 */
const globalSearchRef = ref<{ open: () => Promise<void>; close: () => void } | null>(null)

const onGlobalShortcut = (event: KeyboardEvent): void => {
  if (!(event.ctrlKey || event.metaKey) || event.altKey || event.shiftKey) {
    return
  }
  if (event.key.toLowerCase() !== 'k') {
    return
  }
  event.preventDefault()
  void globalSearchRef.value?.open()
}

onMounted(() => {
  document.addEventListener('keydown', onGlobalShortcut)
})

onBeforeUnmount(() => {
  document.removeEventListener('keydown', onGlobalShortcut)
})

/* ========== 侧栏折叠（状态持久化 + 无侧栏滚动条） ==========
 * 展开 220px / 折叠 64px（折叠宽度与 Element Plus 的 --el-menu-collapse-width 一致）。
 * 展开态从 200px 提到 220px：英文菜单里「Traffic Governance / Model & Instances」
 * 在 200px 下（减去图标与箭头预留后只剩约 80px 文本宽度）会被省略号截断。
 * 宽度由 :width 绑定，过渡写在 scoped 样式里（并尊重 prefers-reduced-motion）。
 */
const SIDEBAR_COLLAPSED_KEY = 'ja-sidebar-collapsed'
const SIDEBAR_WIDTH_EXPANDED = '220px'
const SIDEBAR_WIDTH_COLLAPSED = '64px'

/**
 * 读取持久化折叠状态。
 *
 * localStorage 在隐私模式 / 站点数据被禁用的环境下 getItem 会直接抛异常，
 * 手工改过的键值也可能是任意字符串 ⇒ 一律容错为「展开」，不允许它阻断布局渲染。
 */
const readSidebarCollapsed = (): boolean => {
  try {
    return window.localStorage.getItem(SIDEBAR_COLLAPSED_KEY) === '1'
  } catch {
    // 存储不可用：本次会话按默认展开渲染，不抛出
    return false
  }
}

const userCollapsed = ref(readSidebarCollapsed())

// 窄屏（≤767px）强制折叠：200px 侧栏放进 390px 视口后内容区只剩 190px，
// 表格与筛选表单必然横向溢出。强制态只影响渲染，不写回 localStorage，
// 也不允许用切换按钮改回展开 —— 视口变宽后自动恢复用户偏好。
const { isNarrow } = useViewport()
const collapsed = computed(() => isNarrow.value || userCollapsed.value)

const asideWidth = computed(() => (collapsed.value ? SIDEBAR_WIDTH_COLLAPSED : SIDEBAR_WIDTH_EXPANDED))

// aria-label / title 体现「点击后会发生什么」：展开时提示收起，折叠时提示展开
const sidebarToggleLabel = computed(() =>
  collapsed.value ? t('common.expandSidebar') : t('common.collapseSidebar')
)

const toggleSidebar = (): void => {
  userCollapsed.value = !userCollapsed.value
  try {
    window.localStorage.setItem(SIDEBAR_COLLAPSED_KEY, userCollapsed.value ? '1' : '0')
  } catch {
    // 写入失败只影响「下次进入是否记住」，当前这次折叠/展开照常生效
  }
}

// 菜单数据驱动 + 权限过滤（menu.ts 提供 7 组 28 项结构与权限码；数组权限为「或」）
const filteredMenuGroups = computed(() => filterMenuByPermission(menuGroups))

// 组图标名 → 图标组件（menu.ts 的 icon 为 kebab-case 名，7 组各一）
const groupIconMap: Record<string, Component> = {
  house: House,
  'data-analysis': DataAnalysis,
  connection: Connection,
  setting: Setting,
  lock: Lock,
  user: User,
  tools: Tools
}

// 防止快速连续点击导致的导航问题
const isNavigating = ref(false)

// 当前激活的菜单项：深链查表 + 未命中回退 route.path（menu.ts 的 ROUTE_TO_MENU 由 tabs 派生）
const activeMenu = computed(() => {
  const { path } = route
  if (path === '/') {
    return '/dashboard/main'
  }
  return resolveMenuPath(path)
})

// 默认展开的组：由「当前路由反查所属组」得到，不再维护一份前缀表
const defaultOpeneds = computed(() => {
  const entry = findMenuEntry(route.path)
  return entry ? [entry.group.index] : []
})

// 菜单项反查（el-menu-item 的 index 即菜单项主路径）
const findItemByPath = (path: string): MenuItem | undefined => {
  for (const group of menuGroups) {
    for (const item of group.children) {
      if (item.path === path) {
        return item
      }
    }
  }
  return undefined
}

/**
 * 面包屑：「组 → 菜单项 → 当前 tab」
 *
 * - 最后一段不带链接（模板里只有非末段才渲染 router-link）；
 * - 单页菜单项的 tab 与菜单项同名（labelKey 相同）时去重，避免「路由规则 / 路由规则」；
 * - 详情页等非 tab 路由：追加 `#<id>` 作为末段；
 * - 完全不在菜单里的路由：回退「首页 → meta.titleKey / 路由 name」。
 */
const breadcrumbs = computed<{ path?: string; title: string }[]>(() => {
  const entry = findMenuEntry(route.path)
  if (!entry) {
    const fallbackTitle =
      (route.meta?.titleKey as string | undefined) ||
      (typeof route.name === 'string' ? route.name : undefined) ||
      route.path
    return [
      { path: '/dashboard/main', title: 'layout.home' },
      { title: fallbackTitle }
    ]
  }

  const { group, item } = entry
  const landing = resolveLandingPath(item)
  const crumbs: { path?: string; title: string }[] = [
    { path: landing, title: group.title },
    { path: landing, title: item.title }
  ]

  const tab = findMenuTab(route.path)
  if (tab) {
    if (tab.labelKey !== item.title) {
      crumbs.push({ title: tab.labelKey })
    }
  } else if (route.path !== item.path) {
    // 详情页：末段不可点，用 #id 标识具体记录
    const detailId = String(route.params.id ?? '')
    crumbs.push({ title: detailId ? `#${detailId}` : route.path })
  }

  return crumbs
})

// i18n key 存在才翻译，否则原样显示（详情页的 #id、meta 未配置 titleKey 时的路由名）
const displayTitle = (key: string): string => (te(key) ? t(key) : key)

// 当前菜单项下、当前用户可见的 tab（单 tab 项不渲染切换条）
const currentTabs = computed(() => {
  const entry = findMenuEntry(route.path)
  return entry ? getAccessibleTabs(entry.item) : []
})

// tab 切换（被收起的 12 个页面在这里进入，路由路径与单独成项时完全一致）
const handleTabSelect = async (to: string) => {
  if (route.path === to) {
    return
  }
  try {
    await router.push(to)
  } catch (error) {
    console.error('路由跳转失败:', error)
  }
}

// 处理菜单选择：进入该菜单项下「第一个当前用户有权限的 tab」，无权限则回退第一个 tab
const handleMenuSelect = async (index: string) => {
  // 防止快速连续点击
  if (isNavigating.value) {
    return
  }

  const item = findItemByPath(index)
  const target = item ? resolveLandingPath(item) : index

  // 如果已经在目标路由，不进行跳转
  if (route.path === target) {
    return
  }

  isNavigating.value = true

  try {
    // 使用编程式导航而不是让Element Plus处理
    await router.push(target)
  } catch (error) {
    console.error('路由跳转失败:', error)
    // 如果是导航重复错误，可以忽略
    if ((error as { name?: string })?.name !== 'NavigationDuplicated') {
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
    // 跳转到个人资料页面（待实现）
  }
}
</script>

<style scoped>
.layout-container {
  height: 100vh;
  background: var(--ja-sidebar-bg);
  overflow: hidden;
}

/* 侧栏固定列：渐变背景挂在「不滚动」的全高容器上，
   任何滚动位置背景都铺满可见区域，子菜单/折叠层透明叠放不会断层 */
.layout-aside {
  display: flex;
  flex-direction: column;
  /* 覆盖 EP 默认 .el-aside { overflow: auto }，避免侧栏整体滚动 */
  overflow: hidden;
  /* 宽度来自 :width 绑定的 200px / 64px，这里只负责过渡（不写死宽度，避免与内联样式打架） */
  transition: width 0.2s ease;
  background: var(--ja-sidebar-bg);
  background-color: var(--ja-sidebar-solid-bg);
  border-right: 1px solid var(--ja-sidebar-border);
  box-shadow: var(--ja-sidebar-shadow);
}

/* 折叠态：logo 只剩图标，去掉文字留下的底部间距，让徽标垂直居中 */
.layout-aside.is-collapsed .logo-icon {
  margin-bottom: 0;
}

/* 折叠后主列可收缩：flex 子项默认 min-width:auto，宽内容会把布局撑出横向滚动 */
.layout-body {
  min-width: 0;
}

@media (prefers-reduced-motion: reduce) {
  .layout-aside {
    transition: none;
  }
}

/* hover 保持静态，无 box-shadow 跳动 */

/* 菜单区 = 独立滚动容器（logo 固定不动，仅此项区域滚动）；
   背景透明，露出 .layout-aside 的固定渐变，滚动时无缝 */
.layout-menu {
  flex: 1 1 auto;
  min-height: 0;
  overflow-y: auto;
  /* 折叠态菜单宽度固定 64px，Windows 经典滚动条会占掉约 15px：
     若放任 overflow-x 取 auto，竖向滚动条出现时会在侧栏内再冒一条横向滚动条。
     这里显式裁掉横向溢出（折叠后只显示图标，右侧多出的留白无信息） */
  overflow-x: hidden;
  background-color: transparent;
  border-right: none;
  /* 滚动条与侧栏同色系、仅在滚动时可见：Windows 经典滚动条会在浅色侧栏上留一条
     高对比度的灰槽，割裂感明显（2026-10-21 用户反馈） */
  scrollbar-width: thin;
  scrollbar-color: var(--ja-sidebar-scrollbar) transparent;
  scrollbar-gutter: stable;
}

.layout-menu::-webkit-scrollbar {
  width: 8px;
  height: 8px;
}

.layout-menu::-webkit-scrollbar-track {
  background: transparent;
}

.layout-menu::-webkit-scrollbar-thumb {
  background-color: var(--ja-sidebar-scrollbar);
  border-radius: 999px;
  border: 2px solid transparent;
  background-clip: content-box;
}

.layout-menu::-webkit-scrollbar-thumb:hover {
  background-color: var(--ja-sidebar-scrollbar);
}

/* 菜单项：选中用「淡主色底 + 主色深阶文字」，悬停用次级面。
   Element Plus 默认只改文字色，不加底，浅色侧栏下选中项辨识度不够。 */
.layout-menu :deep(.el-menu-item),
.layout-menu :deep(.el-sub-menu__title) {
  border-radius: var(--ja-radius, 8px);
  margin: 2px 6px;
}

/* 分组标题右侧 Element Plus 预留 44px 给箭头，吃掉了英文标题的空间：
   收到 30px 并把箭头贴近右边，配合 220px 展开宽度，英文标题可完整显示。
   只在展开态生效 —— 折叠态只显示图标，不该被这个内边距推偏。 */
.layout-menu:not(.el-menu--collapse) :deep(.el-sub-menu__title) {
  padding-right: 30px;
}

.layout-menu:not(.el-menu--collapse) :deep(.el-sub-menu__icon-arrow) {
  right: 10px;
}

.layout-menu :deep(.el-menu-item:hover),
.layout-menu :deep(.el-sub-menu__title:hover) {
  background-color: var(--ja-surface-2);
}

.layout-menu :deep(.el-menu-item.is-active) {
  background-color: var(--ja-sidebar-active-bg);
}

/* 文案溢出兜底：英文菜单项比中文长，超长时省略而不是顶出侧栏（node_modules 里的
   Element Plus 不给菜单项做省略，展开态下文本会照原样溢出）。
   菜单文字统一包在 .menu-label 里，才能吃到 text-overflow。 */
.layout-menu:not(.el-menu--collapse) :deep(.el-menu-item),
.layout-menu:not(.el-menu--collapse) :deep(.el-sub-menu__title) {
  overflow: hidden;
}

.layout-menu :deep(.menu-label) {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 菜单项占满整行（下划线式高亮需要），分组标题按内容宽度、只在放不下时才收缩 */
.layout-menu :deep(.el-menu-item .menu-label) {
  flex: 1 1 auto;
}

/* 子菜单容器不再叠加背景，避免与侧栏底色出现色块 */
.layout-menu :deep(.el-sub-menu .el-menu) {
  background-color: transparent;
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
}

/* P0-1：占位字形换成真徽标后，旧的蓝色圆底与 glow 不再需要（会与方形图标叠出光晕） */
.logo-icon {
  margin-bottom: 10px;
  width: 56px;
  height: 56px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.logo-text {
  color: var(--ja-sidebar-text);
  font-size: 20px;
  font-weight: 600;
  margin: 0;
  letter-spacing: 1px;
}

/* P0-1 品牌与令牌：真 logo（32px 显示尺寸，64px 位图供 2x 屏；圆角与素材自身的 25% 一致） */
.logo-mark {
  width: 32px;
  height: 32px;
  border-radius: 8px;
  object-fit: contain;
  display: block;
}

.layout-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  background-color: var(--ja-bg-page);
  border-bottom: 1px solid var(--ja-border);
  padding: 0 20px;
}

/* 面包屑 + tab 切换条同排：多 tab 项才出现切换条，单 tab 项视觉不变 */
.header-left {
  display: flex;
  align-items: center;
  gap: 16px;
  min-width: 0;
}

/* 右侧操作区：语言 / 主题 / 账户三项固定不换行，窄屏时由左侧 tab 让位 */
.header-right {
  display: flex;
  align-items: center;
  gap: 8px;
  flex: 0 0 auto;
}

/* 窄屏（≤767px，与侧栏强制折叠同一断点）：面包屑三行会在 60px 头部里溢出并压住右侧
   语言/主题/账户区，这里只保留 tab 切换条；路由位置由 tab 与侧栏高亮表达。 */
@media (max-width: 767px) {
  .layout-header {
    padding: 0 12px;
    gap: 8px;
  }

  .header-left :deep(.el-breadcrumb) {
    display: none;
  }

  .header-left {
    gap: 8px;
    overflow: hidden;
  }

  .nav-tabs {
    flex: 1 1 auto;
  }
}

.nav-tabs {
  display: flex;
  align-items: center;
  gap: 4px;
  overflow-x: auto;
  scrollbar-width: none;
}

.nav-tabs::-webkit-scrollbar {
  display: none;
}

.nav-tab {
  flex: 0 0 auto;
  border: none;
  background: transparent;
  color: var(--ja-text-secondary);
  font-size: 13px;
  line-height: 1;
  padding: 6px 10px;
  border-radius: 6px;
  cursor: pointer;
  transition: background-color 0.2s, color 0.2s;
}

.nav-tab:hover {
  background-color: var(--el-fill-color-light);
  color: var(--ja-text-primary);
}

.nav-tab.active {
  background-color: var(--ja-primary);
  color: #fff;
}

.sidebar-toggle {
  flex: 0 0 auto;
}

.theme-toggle {
  margin-right: 12px;
}

.lang-toggle {
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
  min-width: 0;
  background-color: var(--ja-main-bg);
  padding: 20px;
}

/* 面包屑链接与末段文字统一用主题前景色，避免默认 link 蓝与深色侧栏主题冲突 */
.layout-header :deep(.el-breadcrumb__inner a),
.layout-header :deep(.el-breadcrumb__inner.is-link) {
  color: var(--ja-text-secondary);
  font-weight: 400;
}

.layout-header :deep(.el-breadcrumb__inner a:hover) {
  color: var(--ja-primary);
}

.layout-header :deep(.el-breadcrumb__item:last-child .el-breadcrumb__inner) {
  color: var(--ja-text-primary);
  font-weight: 500;
}
</style>
