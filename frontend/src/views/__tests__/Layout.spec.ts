import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
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

describe('Layout 多视图切换条', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    i18n.global.locale.value = 'zh-CN'
  })

  it('调用项合并后有两个视图：切换条渲染「调用记录 / 调用统计」', async () => {
    const wrapper = await mountAt('/call-history/list', [], ['ADMIN'])

    const tabs = wrapper.findAll('.nav-tabs .nav-tab').map(node => node.text())
    expect(tabs).toEqual(['调用记录', '调用统计'])
    expect(wrapper.find('.nav-tabs .nav-tab.active').text()).toBe('调用记录')
    wrapper.unmount()
  })

  it('熔断项合并后有三个视图：点「熔断历史」跳到它自己的 URL', async () => {
    const wrapper = await mountAt('/circuit-breakers/monitoring', [], ['ADMIN'])

    const tabs = wrapper.findAll('.nav-tabs .nav-tab')
    expect(tabs.map(node => node.text())).toEqual(['熔断监控', '熔断历史', '熔断配置'])

    // 每个视图的 URL 各自独立：直接导航到历史视图仍然可达，且当前项高亮随之切换
    await router.push('/circuit-breakers/history')
    await flushPromises()
    expect(router.currentRoute.value.path).toBe('/circuit-breakers/history')
    expect(wrapper.find('.nav-tabs .nav-tab.active').text()).toBe('熔断历史')
    wrapper.unmount()
  })

  it('单视图菜单项不渲染切换条', async () => {
    const wrapper = await mountAt('/config/cache', [], ['ADMIN'])

    expect(wrapper.find('.nav-tabs').exists()).toBe(false)
    wrapper.unmount()
  })

  it('面包屑为「组 → 菜单项 → 视图」，末段不可点（合并视图带回第三段）', async () => {
    const wrapper = await mountAt('/monitoring/quota', [], ['ADMIN'])

    const crumbs = wrapper.findAll('.el-breadcrumb__inner').map(crumb => crumb.text())
    expect(crumbs).toEqual(['运行观测', '用量', '配额使用'])
    // 组与菜单项可点，视图段不可点
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
describe('Layout 菜单可见性（权限码是唯一门控）', () => {
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

  it('OPERATOR 视角：安全组只剩记录治理，平台设置组只剩有码的 5 项', async () => {
    const wrapper = await mountAt('/config/state-persistence', [
      'overview:dashboard:read',
      'security:audit:read',
      'config:callhistory:read',
      'config:persistence:read',
      'config:cluster:read',
      'config:health:read',
      'monitoring:console:read',
      'monitoring:config:read',
      'monitoring:config:write',
      'tracing:config:read',
      'config:cache:read',
      'config:quota:read'
    ], ['OPERATOR'])

    const groups = sidebarGroups(wrapper)
    // 2026-11-18 菜单重分类：审计日志移到运行观测组，安全组只剩记录治理
    // （API Key / PII 脱敏 / JWT 令牌 / 黑名单 / 登录保护 仍需各自的 read 码，OPERATOR 不持有）。
    expect(groups.find(group => group.title === '安全')?.items).toEqual(['记录治理'])
    // 平台设置组（原「系统」组）按小节排序：运行时配置 → 部署与基础设施 → 审计
    // 合并后：监控（配置 + 运行时）、追踪配置、响应缓存、配额运行时、部署（三合一）
    expect(groups.find(group => group.title === '平台设置')?.items).toEqual([
      '监控配置',
      '追踪配置',
      '响应缓存管理',
      '配额运行时配置',
      '部署'
    ])
    // 小节标题：只渲染「当前可见项所属」的小节，且同一小节只出现一次
    // （操作记录已移到运行观测组，所以平台设置只剩两节）
    expect(wrapper.findAll('.menu-section-title').map(node => node.text())).toEqual([
      '异常与审计',
      '运行时配置',
      '部署与基础设施'
    ])
    wrapper.unmount()
  })

  it('ADMIN 视角：八组齐全，安全 6 项、访问与身份 3 项与平台设置 5 项都在（反证不是整栏都没渲染）', async () => {
    const wrapper = await mountAt('/system/cluster', [], ['ADMIN'])

    const groups = sidebarGroups(wrapper)
    expect(groups.map(group => group.title)).toEqual([
      '概览',
      '运行观测',
      '流量治理',
      '模型与实例',
      '安全',
      '访问与身份',
      '平台设置',
      '开发工具'
    ])
    expect(groups.find(group => group.title === '安全')?.items).toEqual([
      'API 密钥管理',
      'JWT 令牌管理',
      '黑名单管理',
      'PII 脱敏管理',
      '记录治理',
      '登录保护'
    ])
    expect(groups.find(group => group.title === '访问与身份')?.items).toEqual([
      '账户管理',
      '权限管理',
      'SLA 接入',
      '页面访问热度'
    ])
    expect(groups.find(group => group.title === '平台设置')?.items).toHaveLength(5)
    // 平台设置组两节，各出现一次（观测组与流量治理组的小节也会渲染，这里只比对集合）
    const sectionTitles = wrapper.findAll('.menu-section-title').map(node => node.text())
    expect(sectionTitles).toContain('运行时配置')
    expect(sectionTitles).toContain('部署与基础设施')
    expect(sectionTitles).not.toContain('审计')
    wrapper.unmount()
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
