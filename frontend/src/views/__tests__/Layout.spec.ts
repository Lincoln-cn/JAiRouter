import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount, type VueWrapper } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import ElementPlus from 'element-plus'
import router from '@/router'
import { i18n } from '@/i18n'
import { useUserStore } from '@/stores/user'
import Layout from '@/views/Layout.vue'

/**
 * Layout 的「多 tab 切换条 / 面包屑」渲染断言（P0-3 第二部分）
 *
 * 40 → 28 之后，被收起的 12 个页面只能经由同组菜单项的 tab 切换条进入，
 * 所以「有没有渲染切换条」「点了能不能到位」「面包屑末段不可点」是 IA 可用性的硬条件。
 * 这里挂真实 Layout + 真实 router（只 stub 掉页面组件与语言切换器），
 * 断言的类名与 e2e/specs/02-layout-nav.mjs 的 `nav-tabs` / `nav-tab` 一致。
 */
const mountAt = async (path: string, permissions: string[], roles: string[] = ['USER']) => {
  // 每个用例独立 pinia，并显式传给 mount：否则 Layout 内 useUserStore() 可能解析到
  // 上一个用例残留的 pinia 实例，读到别人的 roles/permissions（实际踩过）。
  const pinia = createPinia()
  setActivePinia(pinia)
  const userStore = useUserStore()
  userStore.token = 'test-token'
  userStore.permissions = permissions
  // 角色判定与路由守卫同源（userInfo.roles）；不写 roles 时 hasRouteRoles 无法命中
  userStore.userInfo = { username: 'tester', roles, permissions }
  await router.push(path)
  await router.isReady()
  return mount(Layout, {
    global: {
      plugins: [pinia, ElementPlus, i18n, router],
      stubs: {
        RouterView: true,
        LanguageSwitcher: true
      }
    }
  })
}

describe('Layout 多 tab 切换条', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    i18n.global.locale.value = 'zh-CN'
  })

  it('多 tab 菜单项渲染切换条，点击第二个 tab 跳到对应 URL', async () => {
    const wrapper = await mountAt('/call-history/list', ['callhistory:view'])

    const tabs = wrapper.findAll('.nav-tabs .nav-tab')
    expect(tabs.map(tab => tab.text())).toEqual(['调用记录', '调用历史仪表盘'])

    await tabs[1].trigger('click')
    // 懒加载路由组件在 vitest 里要跨若干个宏任务才落定；整库并行跑时冷启动可能 >5s，
    // 这里给足余量（超时会让下一条用例继承未完成的导航，形成假失败）
    await vi.waitFor(() => {
      expect(router.currentRoute.value.path).toBe('/call-history/dashboard')
    }, { timeout: 15000 })
    wrapper.unmount()
  }, 20000)

  it('tab 级权限缺失时不渲染该 tab，只显示有权限的那些', async () => {
    const wrapper = await mountAt('/circuit-breakers/monitoring', [
      'cb:monitoring:read',
      'cb:history:read'
    ])

    expect(router.currentRoute.value.path).toBe('/circuit-breakers/monitoring')
    const tabs = wrapper.findAll('.nav-tabs .nav-tab')
    // 全局配置需要 config:circuitbreaker:read —— 未授予，故只有两个 tab
    expect(tabs.map(tab => tab.text())).toEqual(['熔断监控', '熔断历史'])
    wrapper.unmount()
  })

  it('单 tab 菜单项不渲染切换条', async () => {
    const wrapper = await mountAt('/config/services', ['config:services:read'])

    expect(wrapper.find('.nav-tabs').exists()).toBe(false)
    wrapper.unmount()
  })

  it('面包屑为「组 → 菜单项 → 当前 tab」，最后一段不可点', async () => {
    const wrapper = await mountAt('/monitoring/quota', ['monitoring:quota:read'])

    const crumbs = wrapper.findAll('.el-breadcrumb__inner').map(crumb => crumb.text())
    expect(crumbs).toEqual(['运行观测', '用量统计', '配额使用'])
    // 组与菜单项渲染成链接，末段（当前 tab）只是文本 —— 最后一段不可点
    expect(wrapper.findAll('.el-breadcrumb__inner a')).toHaveLength(2)
    expect(wrapper.findAll('.el-breadcrumb__item').at(-1)?.find('a').exists()).toBe(false)
    wrapper.unmount()
  })
})

/**
 * 角色门控（死入口回归，2026-11-16）
 *
 * 菜单项的 `roles` 声明只有真的进到渲染层才算修好：配置断言（menu.spec.ts /
 * menuRouteAlignment.spec.ts）证明「声明与路由同口径」，这里证明「侧栏里确实不再出现
 * 那些点不进去的入口」，并用守卫重定向验证路由侧仍然拦着。
 */
describe('Layout 角色门控', () => {
  beforeEach(() => {
    i18n.global.locale.value = 'zh-CN'
    localStorage.removeItem('ja-sidebar-collapsed')
  })

  /** 侧栏分组快照：组标题 → 组内菜单项文本（组标题可能与项名重名，不能拿整段 text() 断言） */
  const sidebarGroups = (wrapper: VueWrapper) =>
    wrapper.findAll('.el-sub-menu').map(group => ({
      title: group.find('.el-sub-menu__title').text(),
      items: group.findAll('.el-menu-item').map(node => node.text())
    }))

  it('OPERATOR 视角：安全组整体消失，系统组只剩状态持久化', async () => {
    const wrapper = await mountAt('/config/state-persistence', [
      'overview:dashboard:read',
      'config:persistence:read',
      'config:cluster:read',
      'config:health:read',
      'monitoring:console:read'
    ], ['OPERATOR'])

    const groups = sidebarGroups(wrapper)
    // 安全组 6 项全部落在 /security 父级（roles: ['ADMIN']）下 ⇒ 整组不渲染
    expect(groups.map(group => group.title)).not.toContain('安全')
    // 系统组仍在：状态持久化没有角色声明；其它 6 项要 ADMIN，即使权限码在手也不显示
    expect(groups.find(group => group.title === '系统')?.items).toEqual(['状态持久化'])
    wrapper.unmount()
  })

  it('ADMIN 视角：七组齐全，安全 6 项与系统 7 项都在（反证不是整栏都没渲染）', async () => {
    const wrapper = await mountAt('/system/cluster', [], ['ADMIN'])

    const groups = sidebarGroups(wrapper)
    expect(groups.map(group => group.title)).toEqual([
      '概览',
      '运行观测',
      '流量治理',
      '模型与实例',
      '安全',
      '系统',
      '开发工具'
    ])
    expect(groups.find(group => group.title === '安全')?.items).toHaveLength(6)
    expect(groups.find(group => group.title === '系统')?.items).toHaveLength(7)
    wrapper.unmount()
  })

  it('非 ADMIN 直接输入 /system/cluster 被守卫重定向回仪表板，状态持久化可直达', async () => {
    const pinia = createPinia()
    setActivePinia(pinia)
    const userStore = useUserStore()
    userStore.token = 'test-token'
    userStore.permissions = ['overview:dashboard:read', 'config:cluster:read', 'config:persistence:read']
    userStore.userInfo = {
      username: 'op',
      roles: ['OPERATOR'],
      permissions: userStore.permissions
    }

    // 从无权限码要求的页面出发：同路径 push 会被 vue-router 当成重复导航跳过，
    // 守卫根本不执行，会得到「没被重定向」的假红（实际踩过）。
    await router.push('/tools/client-access')
    await router.push('/system/cluster')
    expect(router.currentRoute.value.path).toBe('/dashboard/main')

    await router.push('/config/state-persistence')
    expect(router.currentRoute.value.path).toBe('/config/state-persistence')
  })

  it('连仪表板权限都没有时，落地页放行而不是无限重定向', async () => {
    const pinia = createPinia()
    setActivePinia(pinia)
    const userStore = useUserStore()
    userStore.token = 'test-token'
    userStore.permissions = ['config:cluster:read']
    userStore.userInfo = {
      username: 'op',
      roles: ['OPERATOR'],
      permissions: userStore.permissions
    }

    await router.push('/tools/client-access')
    await router.push('/system/cluster')
    // 守卫把用户送到 dashboard-main 后，若再跳一次就会抛 Infinite redirect；
    // 现在落地页自身无权限时直接放行，所以导航停在 /dashboard/main 不报错
    expect(router.currentRoute.value.path).toBe('/dashboard/main')
  })
})

/**
 * P1-3：侧栏折叠（220px ⇄ 64px）与 localStorage 持久化。
 *
 * 宽度断言看 el-aside 的 `--el-aside-width` 内联变量（Element Plus 用 CSS 变量承载 width），
 * 折叠态同时断言 el-menu 的 `el-menu--collapse` 类与 logo 文字的 v-if，
 * 持久化键固定为 `ja-sidebar-collapsed`（'1' / '0'）。
 */
describe('Layout 侧栏折叠', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    i18n.global.locale.value = 'zh-CN'
    vi.restoreAllMocks()
    localStorage.removeItem('ja-sidebar-collapsed')
  })

  it('默认展开，点击折叠到 64px 并写 localStorage，再点恢复 220px', async () => {
    const wrapper = await mountAt('/monitoring/quota', ['monitoring:quota:read'])

    const aside = wrapper.find('#layout-sidebar')
    const toggle = wrapper.find('.sidebar-toggle')
    expect(aside.attributes('style')).toContain('220px')
    expect(wrapper.find('.logo-text').exists()).toBe(true)
    expect(wrapper.find('.layout-menu').classes()).not.toContain('el-menu--collapse')
    expect(toggle.attributes('aria-label')).toBe('收起侧栏')
    expect(toggle.attributes('aria-expanded')).toBe('true')

    await toggle.trigger('click')
    expect(aside.attributes('style')).toContain('64px')
    expect(wrapper.find('.logo-text').exists()).toBe(false)
    expect(wrapper.find('.layout-menu').classes()).toContain('el-menu--collapse')
    expect(toggle.attributes('aria-label')).toBe('展开侧栏')
    expect(toggle.attributes('aria-expanded')).toBe('false')
    expect(localStorage.getItem('ja-sidebar-collapsed')).toBe('1')

    await toggle.trigger('click')
    expect(aside.attributes('style')).toContain('220px')
    expect(wrapper.find('.logo-text').exists()).toBe(true)
    expect(localStorage.getItem('ja-sidebar-collapsed')).toBe('0')
    wrapper.unmount()
  })

  it('初始化读取持久化状态：已折叠时首屏即 64px（不闪出 logo 文字）', async () => {
    localStorage.setItem('ja-sidebar-collapsed', '1')
    const wrapper = await mountAt('/monitoring/quota', ['monitoring:quota:read'])

    expect(wrapper.find('#layout-sidebar').attributes('style')).toContain('64px')
    expect(wrapper.find('.logo-text').exists()).toBe(false)
    expect(wrapper.find('.layout-menu').classes()).toContain('el-menu--collapse')
    // 折叠态下按钮仍在头部且变成「展开」语义，保证可点回去
    expect(wrapper.find('.sidebar-toggle').attributes('aria-label')).toBe('展开侧栏')
    wrapper.unmount()
  })

  it('非法存储值 / 读取抛异常时回退展开，写入抛异常时折叠仍生效', async () => {
    localStorage.setItem('ja-sidebar-collapsed', 'yes-please')
    const invalid = await mountAt('/monitoring/quota', ['monitoring:quota:read'])
    expect(invalid.find('#layout-sidebar').attributes('style')).toContain('220px')
    expect(invalid.find('.logo-text').exists()).toBe(true)
    invalid.unmount()

    const originalGetItem = localStorage.getItem.bind(localStorage)
    vi.spyOn(localStorage, 'getItem').mockImplementation((key: string) => {
      if (key === 'ja-sidebar-collapsed') throw new Error('storage disabled')
      return originalGetItem(key)
    })
    const blockedRead = await mountAt('/monitoring/quota', ['monitoring:quota:read'])
    expect(blockedRead.find('#layout-sidebar').attributes('style')).toContain('220px')
    expect(blockedRead.find('.logo-text').exists()).toBe(true)
    blockedRead.unmount()
    vi.restoreAllMocks()

    vi.spyOn(localStorage, 'setItem').mockImplementation((key: string, value: string) => {
      if (key === 'ja-sidebar-collapsed') throw new Error('quota exceeded')
      // 其余键（主题等）保持可写
      Object.getPrototypeOf(localStorage).setItem.call(localStorage, key, value)
    })
    const blockedWrite = await mountAt('/monitoring/quota', ['monitoring:quota:read'])
    await blockedWrite.find('.sidebar-toggle').trigger('click')
    expect(blockedWrite.find('#layout-sidebar').attributes('style')).toContain('64px')
    expect(blockedWrite.find('.sidebar-toggle').attributes('aria-label')).toBe('展开侧栏')
    blockedWrite.unmount()
  })
})
