import { describe, expect, it } from 'vitest'
import {
  menuGroups,
  ROUTE_TO_MENU,
  findMenuEntry,
  findMenuTab,
  resolveMenuPath
} from '@/config/menu'

/**
 * 导航信息架构（P0-3）静态断言
 *
 * 这些断言守的是「配置结构」，不是渲染：Layout.vue 的侧栏、深链高亮、
 * 默认展开组、面包屑都从 `menuGroups` / `ROUTE_TO_MENU` 推导，
 * 一旦结构漂移（多一项、少一组、tab 路由写错、反查表漏项），先在这里红。
 */
describe('menu.ts 信息架构', () => {
  it('7 组 28 项，组顺序与设计文档一致', () => {
    expect(menuGroups.map(g => g.index)).toEqual([
      'dashboard',
      'observability',
      'traffic',
      'model-services',
      'security',
      'system',
      'dev-tools'
    ])
    expect(menuGroups.map(g => g.children.length)).toEqual([1, 5, 7, 4, 5, 4, 2])
    expect(menuGroups.reduce((total, g) => total + g.children.length, 0)).toBe(28)
  })

  it('每项主路径 = 第一个 tab 的路由，且 tab 路由全局唯一', () => {
    const items = menuGroups.flatMap(g => g.children)
    for (const item of items) {
      expect(item.tabs.length).toBeGreaterThan(0)
      expect(item.path).toBe(item.tabs[0].to)
      for (const tab of item.tabs) {
        expect(tab.key).toBeTruthy()
        expect(tab.labelKey).toBeTruthy()
      }
    }
    const routes = items.flatMap(item => item.tabs.map(tab => tab.to))
    // 40 → 28 的 12 个被收起页面仍在 tabs 里，因此 tab 总数 = 28 + 12 = 40
    expect(routes.length).toBe(40)
    expect(new Set(routes).size).toBe(routes.length)
  })

  it('ROUTE_TO_MENU 覆盖每个 tab 并指回所属菜单项', () => {
    for (const group of menuGroups) {
      for (const item of group.children) {
        for (const tab of item.tabs) {
          expect(ROUTE_TO_MENU[tab.to]).toBe(item.path)
        }
      }
    }
    expect(Object.keys(ROUTE_TO_MENU)).toHaveLength(40)
  })

  it('深链未命中回退 route.path；异常详情归到异常列表', () => {
    expect(resolveMenuPath('/not-in-menu/42')).toBe('/not-in-menu/42')
    expect(resolveMenuPath('/monitoring/quota')).toBe('/call-history/token-usage')
    expect(resolveMenuPath('/exceptions/detail/abc')).toBe('/exceptions/list')
    expect(findMenuEntry('/exceptions/detail/abc')?.group.index).toBe('observability')
    // 详情页不是 tab，面包屑末段由 Layout 单独渲染
    expect(findMenuTab('/exceptions/detail/abc')).toBeUndefined()
    expect(findMenuTab('/exceptions/statistics')?.key).toBe('statistics')
  })

  it('12 个被收起的路由仍可反查到 tab（深链可达）', () => {
    const collapsed = [
      '/call-history/dashboard',
      '/tracing/dashboard',
      '/exceptions/statistics',
      '/monitoring/slow-queries',
      '/monitoring/quota',
      '/load-balancers/strategy-config',
      '/circuit-breakers/history',
      '/circuit-breakers/global-config',
      '/playground/embedding',
      '/playground/rerank',
      '/playground/audio',
      '/playground/image'
    ]
    for (const path of collapsed) {
      expect(findMenuTab(path), path).toBeTruthy()
      expect(ROUTE_TO_MENU[path], path).toBeTruthy()
    }
  })
})
