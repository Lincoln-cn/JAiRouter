import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
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
const mountAt = async (path: string, permissions: string[]) => {
  const userStore = useUserStore()
  userStore.token = 'test-token'
  userStore.permissions = permissions
  await router.push(path)
  await router.isReady()
  return mount(Layout, {
    global: {
      plugins: [ElementPlus, i18n, router],
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
    // 懒加载路由组件在 vitest 里要跨若干个宏任务才落定，用 waitFor 等真实导航完成
    await vi.waitFor(() => {
      expect(router.currentRoute.value.path).toBe('/call-history/dashboard')
    }, { timeout: 5000 })
    wrapper.unmount()
  })

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
