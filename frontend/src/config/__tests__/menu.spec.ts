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
  it('8 组 35 项，组顺序与设计文档一致', () => {
    expect(menuGroups.map(g => g.index)).toEqual([
      'dashboard',
      'observability',
      'traffic',
      'model-services',
      'security',
      'access',
      'system',
      'dev-tools'
    ])
    expect(menuGroups.map(g => g.children.length)).toEqual([1, 6, 5, 4, 6, 3, 8, 2])
    expect(menuGroups.reduce((total, g) => total + g.children.length, 0)).toBe(35)
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
    // （v3.8.6 在「限流」项下新增 config tab、在「系统」项下新增集群设置 ⇒ 43；v3.8.7 新增健康探针设置 ⇒ 44；v3.8.8 新增管理操作 ⇒ 45；
    // v3.8.8 之后「系统」组新增「监控配置」一项两 tab ⇒ 47；#1174 再加「SLA 接入」一项一 tab ⇒ 48；#1176 再加「登录保护」一项一 tab ⇒ 49）
  // 2026-11-18 菜单重分类（方案 B）不改变 tab 总数：审计日志从安全组移到运行观测组、
  // 响应缓存与配额运行时从流量治理移到平台设置组 ⇒ 仍是 49 个页面。
    expect(routes.length).toBe(49)
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
    expect(Object.keys(ROUTE_TO_MENU)).toHaveLength(49)
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
 * 白名单本体在 `./knownPermissions.ts`（74 码），由 `permissionCodesBackend.spec.ts`
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

  it('白名单自身 = 76 个互不重复的权限码', () => {
    expect(KNOWN_PERMISSION_CODES).toHaveLength(76)
    expect(new Set(KNOWN_PERMISSION_CODES).size).toBe(76)
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
 * 菜单角色声明的现状（2026-11-16 第五轮）
 *
 * 第五轮前的做法是「`/security`、`/system` 父路由要求 `roles: ['ADMIN']`，菜单给对应 11 项
 * 补同样的 roles」—— 修掉了「看得见但点不进」的死入口。
 *
 * 但四角色运行态冒烟又发现反向问题：OPERATOR / VIEWER 持有 `security:audit:read`
 * （VIEWER 模板是「所有 :read 减 8 个管理面 read」），后端 `/api/security/audit/extended/**`
 * 返回 200，前端却因父路由角色限制进不去 —— 同一条授权边界在两处各写一遍，必然漂移。
 *
 * 现在的口径：**授权边界只由权限码 + 路由 `meta.permissions` 表达**，两个父级的
 * `meta.roles` 已移除，菜单也不再声明 roles。`roles` 字段保留但当前无人使用；
 * 若将来重新启用，必须同时改菜单与路由并让本文件与
 * `router/__tests__/menuRouteAlignment.spec.ts` 的一致性断言继续通过。
 */
describe('menu.ts 角色声明（当前统一交给权限码）', () => {
  it('没有任何菜单项声明 roles', () => {
    const items = menuGroups.flatMap(group => group.children)
    const withRoles = items.filter(item => (item.roles?.length ?? 0) > 0)
    expect(withRoles.map(item => item.path)).toEqual([])
  })

  it('tab 级也没有 roles 残留', () => {
    const tabs = menuGroups.flatMap(group => group.children).flatMap(item => item.tabs)
    const withRoles = tabs.filter(tab => (tab.roles?.length ?? 0) > 0)
    expect(withRoles.map(tab => tab.to)).toEqual([])
  })
})
