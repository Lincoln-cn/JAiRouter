import { describe, expect, it } from 'vitest'
import {
  menuGroups,
  ROUTE_TO_MENU,
  findMenuEntry,
  findMenuTab,
  resolveMenuPath
} from '@/config/menu'
import { KNOWN_PERMISSION_CODES } from './knownPermissions'

/**
 * 导航信息架构（P0-3）静态断言
 *
 * 这些断言守的是「配置结构」，不是渲染：Layout.vue 的侧栏、深链高亮、
 * 默认展开组、面包屑都从 `menuGroups` / `ROUTE_TO_MENU` 推导，
 * 一旦结构漂移（多一项、少一组、tab 路由写错、反查表漏项），先在这里红。
 */
describe('menu.ts 信息架构', () => {
  it('7 组 32 项，组顺序与设计文档一致', () => {
    expect(menuGroups.map(g => g.index)).toEqual([
      'dashboard',
      'observability',
      'traffic',
      'model-services',
      'security',
      'system',
      'dev-tools'
    ])
    expect(menuGroups.map(g => g.children.length)).toEqual([1, 5, 7, 4, 6, 7, 2])
    expect(menuGroups.reduce((total, g) => total + g.children.length, 0)).toBe(32)
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
    // 40 → 31：12 个被收起页面仍在 tabs 里，因此 tab 总数 = 31 + 12 = 43
    // （v3.8.6 在「限流」项下新增 config tab、在「系统」项下新增集群设置 ⇒ 43；v3.8.7 新增健康探针设置 ⇒ 44；v3.8.8 新增管理操作 ⇒ 45）
    expect(routes.length).toBe(45)
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
    expect(Object.keys(ROUTE_TO_MENU)).toHaveLength(45)
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
 * `menu.ts` 里写错一个权限码（例如把 `security:apikeys:manage` 写成 `security:apikey:manage`）
 * 不会报错，只会让菜单静默消失 —— 这份白名单就是那种漂移的哨兵。
 *
 * 白名单本体在 `./knownPermissions.ts`（71 码），由 `permissionCodesBackend.spec.ts`
 * 与后端 `PermissionCodes.java` 做双向比对，这里只守「menu.ts 的码都在白名单内」。
 */
describe('menu.ts 权限码白名单（#567）', () => {
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

  it('白名单自身 = 71 个互不重复的权限码', () => {
    expect(KNOWN_PERMISSION_CODES).toHaveLength(71)
    expect(new Set(KNOWN_PERMISSION_CODES).size).toBe(71)
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

/**
 * 菜单角色声明（死入口回归，2026-11-16）
 *
 * `/security`、`/system` 两个父路由要求 `roles: ['ADMIN']`。菜单此前只按权限码过滤，
 * 导致 OPERATOR / USER / VIEWER 看得到「审计日志」「记录治理」「集群设置」等入口，
 * 点进去却被守卫重定向回仪表板。这里把「哪些项必须声明 ADMIN」钉成清单，
 * 与 `router/__tests__/menuRouteAlignment.spec.ts`（对照路由 meta.roles）互为保险。
 */
describe('menu.ts 角色声明（与路由守卫同口径）', () => {
  /** 落在 /security、/system 父级下的菜单项（11 项） */
  const ADMIN_ONLY_ITEMS = [
    '/security/api-keys',
    '/security/sanitization',
    '/security/jwt-tokens',
    '/security/blacklist',
    '/security/audit-logs',
    '/security/record-governance',
    '/system/accounts',
    '/system/permissions',
    '/system/cluster',
    '/system/health',
    '/system/console-operations'
  ]

  it('只有这两个父级下的 11 项声明 roles，且均为 ADMIN', () => {
    const items = menuGroups.flatMap(group => group.children)
    const withRoles = items.filter(item => (item.roles?.length ?? 0) > 0)
    expect(withRoles.map(item => item.path).sort()).toEqual([...ADMIN_ONLY_ITEMS].sort())
    for (const item of withRoles) {
      expect(item.roles, item.path).toEqual(['ADMIN'])
    }
  })

  it('状态持久化与追踪配置不在 /system 父级下，不声明 roles', () => {
    const items = menuGroups.flatMap(group => group.children)
    for (const path of ['/config/state-persistence', '/tracing/management']) {
      const item = items.find(candidate => candidate.path === path)
      expect(item, path).toBeTruthy()
      expect(item?.roles, path).toBeUndefined()
    }
  })
})
