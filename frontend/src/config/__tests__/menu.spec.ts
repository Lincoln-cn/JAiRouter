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


/**
 * 权限码白名单（#567）
 *
 * 前端不复制后端常量表：这里只守「`menu.ts` 里出现的每个权限码都在一份 63 码字面量清单内」。
 * 拼错一个码（例如把 `security:apikeys:manage` 写成 `security:apikey:manage`）不会报错，
 * 只会让菜单静默消失 —— 这份清单就是那种漂移的哨兵。
 */
describe('menu.ts 权限码白名单（#567）', () => {
  const KNOWN_PERMISSION_CODES = [
    'overview:dashboard:read',
    'config:services:read',
    'config:services:write',
    'config:instances:read',
    'config:instances:write',
    'config:versions:read',
    'config:versions:write',
    'config:persistence:read',
    'config:persistence:write',
    'config:adapters:read',
    'config:adapters:write',
    'config:rules:read',
    'config:rules:write',
    'config:pools:read',
    'config:pools:write',
    'config:circuitbreaker:read',
    'config:circuitbreaker:write',
    'config:callhistory:read',
    'config:callhistory:write',
    'config:cache:read',
    'config:cache:write',
    'config:validation:read',
    'config:validation:write',
    'config:quota:read',
    'config:quota:write',
    'lb:monitoring:read',
    'lb:config:read',
    'lb:config:write',
    'cb:monitoring:read',
    'cb:history:read',
    'rl:monitoring:read',
    'callhistory:view',
    'monitoring:metrics:read',
    'monitoring:metrics:write',
    'monitoring:slowquery:read',
    'monitoring:slowquery:write',
    'monitoring:tokenusage:read',
    'monitoring:modelstats:read',
    'monitoring:routing:read',
    'monitoring:quota:read',
    'monitoring:exceptions:read',
    'monitoring:exceptions:write',
    'monitoring:config:write',
    'tracing:dashboard:read',
    'tracing:search:read',
    'tracing:config:read',
    'tracing:config:manage',
    'security:apikeys:read',
    'security:apikeys:manage',
    'security:jwttokens:read',
    'security:jwttokens:manage',
    'security:blacklist:read',
    'security:blacklist:manage',
    'security:audit:read',
    'security:audit:write',
    'security:sanitization:read',
    'security:sanitization:manage',
    'system:accounts:read',
    'system:accounts:manage',
    'system:permissions:read',
    'system:permissions:manage',
    'ai:playground:use',
    'actuator:admin:manage'
  ]

  /** 收集 menu.ts 中每一项 / 每个 tab 声明的权限码（数组保持原样展开） */
  const collectPermissions = (): string[] => {
    const codes: string[] = []
    const push = (permission?: string | string[]): void => {
      if (!permission) return
      codes.push(...(Array.isArray(permission) ? permission : [permission]))
    }
    for (const group of menuGroups) {
      for (const item of group.children) {
        push(item.permission)
        for (const tab of item.tabs) push(tab.permission)
      }
    }
    return codes
  }

  it('白名单自身 = 63 个互不重复的权限码', () => {
    expect(KNOWN_PERMISSION_CODES).toHaveLength(63)
    expect(new Set(KNOWN_PERMISSION_CODES).size).toBe(63)
  })

  it('menu.ts 里出现的权限码全部在白名单内', () => {
    const codes = collectPermissions()
    expect(codes.length).toBeGreaterThan(0)
    const unknown = codes.filter(code => !KNOWN_PERMISSION_CODES.includes(code))
    expect(unknown).toEqual([])
  })

  it('9 个读写双挂入口按「读码或写码」放行（#567）', () => {
    const expected: Array<[string, string, string]> = [
      ['/load-balancers/strategy-config', 'lb:config:read', 'lb:config:write'],
      ['/config/cache', 'config:cache:read', 'config:cache:write'],
      ['/security/api-keys', 'security:apikeys:read', 'security:apikeys:manage'],
      ['/security/sanitization', 'security:sanitization:read', 'security:sanitization:manage'],
      ['/security/jwt-tokens', 'security:jwttokens:read', 'security:jwttokens:manage'],
      ['/security/blacklist', 'security:blacklist:read', 'security:blacklist:manage'],
      ['/system/accounts', 'system:accounts:read', 'system:accounts:manage'],
      ['/system/permissions', 'system:permissions:read', 'system:permissions:manage'],
      ['/tracing/management', 'tracing:config:read', 'tracing:config:manage']
    ]
    const items = menuGroups.flatMap(group => group.children)
    for (const [path, readCode, manageCode] of expected) {
      const tab = items.flatMap(item => item.tabs).find(candidate => candidate.to === path)
      expect(tab, path).toBeTruthy()
      expect(tab?.permission, path).toEqual([readCode, manageCode])
      const item = items.find(candidate => candidate.path === path)
      if (item) {
        // 菜单项级同样「或」：持读码或持写/管理码都能看到入口
        expect(item.permission, path).toEqual(expect.arrayContaining([readCode, manageCode]))
      }
    }
  })
})
