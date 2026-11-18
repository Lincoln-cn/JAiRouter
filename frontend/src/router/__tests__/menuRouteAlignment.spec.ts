import { describe, expect, it } from 'vitest'
import { menuGroups } from '@/config/menu'
import { KNOWN_PERMISSION_CODES } from '@/config/__tests__/knownPermissions'
import router from '@/router'

/**
 * 菜单 ↔ 路由守卫一致性（2026-11-16）
 *
 * 第四轮的背景：`/security`、`/system` 两个父路由声明了 `meta.roles = ['ADMIN']`，而菜单
 * 只按权限码过滤 ⇒ OPERATOR / USER / VIEWER 能看到「点了被重定向」的入口。当时的修法是
 * 给菜单项补同样的 `roles`。
 *
 * 第五轮撤回这一步：运行态冒烟发现后端对 OPERATOR / VIEWER 是放行审计接口的
 * （它们持有 `security:audit:read`），前端父级角色限制反而造出「有权限却看不到」的
 * 反向不可达。现在授权边界只由权限码 + `meta.permissions` 表达，本文件守的是：
 * ① 菜单声明的 roles 与路由合并后的 `meta.roles` 必须一致（当前都为空）；
 * ② 菜单声明的权限码都能在路由 `meta.permissions` 找到（菜单可见则路由必须放行）；
 * ③ 路由表里不再有 `meta.roles`；
 * ④ 菜单 URL 真实存在；⑤ 业务页面必须声明权限码；⑥ 页面必须能从菜单进入。
 */
const toArray = (value?: string | string[]): string[] =>
  value ? (Array.isArray(value) ? [...value] : [value]) : []

/** 只做重定向的父级容器与公共页，不承担权限判定（子路由各自声明） */
const CONTAINER_PATHS = new Set([
  '/',
  '/login',
  '/dashboard',
  '/config',
  '/load-balancers',
  '/circuit-breakers',
  '/security',
  '/system',
  '/tracing',
  '/playground',
  '/exceptions',
  '/rate-limiters',
  '/monitoring',
  '/call-history',
  '/tools',
  '/tools/client-access'
])

describe('菜单与路由守卫一致性', () => {
  it('每个 tab 的 roles 与路由合并 meta.roles 完全一致', () => {
    for (const group of menuGroups) {
      for (const item of group.children) {
        for (const tab of item.tabs) {
          const routeRoles = toArray(router.resolve(tab.to).meta.roles).sort()
          const menuRoles = toArray(tab.roles ?? item.roles).sort()
          expect(menuRoles, `${tab.to} 的菜单 roles 与路由 meta.roles 不一致`).toEqual(routeRoles)
        }
      }
    }
  })

  it('菜单声明的权限码都能在路由 meta.permissions 中找到（菜单可见则路由必须放行）', () => {
    for (const group of menuGroups) {
      for (const item of group.children) {
        for (const tab of item.tabs) {
          const routeCodes = toArray(router.resolve(tab.to).meta.permissions)
          const menuCodes = toArray(tab.permission ?? item.permission)
          const missing = menuCodes.filter(code => !routeCodes.includes(code))
          expect(missing, `${tab.to} 的路由缺少菜单声明的权限码`).toEqual([])
        }
      }
    }
  })

  it('每个 tab 的 to 都能解析到真实路由（防菜单指向不存在的 URL）', () => {
    for (const group of menuGroups) {
      for (const item of group.children) {
        for (const tab of item.tabs) {
          const resolved = router.resolve(tab.to)
          expect(resolved.matched.length, `${tab.to} 没有匹配到任何路由`).toBeGreaterThan(0)
          expect(resolved.path, `${tab.to} 解析后的路径与菜单写法不一致`).toBe(tab.to)
          expect(resolved.name, `${tab.to} 对应的路由缺少 name`).toBeTruthy()
        }
      }
    }
  })

  it('除容器与公共页外，每个路由都必须声明 meta.permissions（防新增页面漏门控）', () => {
    const missing = router
      .getRoutes()
      .filter(route => !route.path.includes(':'))
      .filter(route => !CONTAINER_PATHS.has(route.path))
      .filter(route => toArray(route.meta.permissions).length === 0)
      .map(route => route.path)
      .sort()
    expect(missing, '这些路由没有声明 meta.permissions，守卫不会做权限判断').toEqual([])
  })

  it('路由声明的权限码都在白名单内（拼错码会让入口永远被拒）', () => {
    const unknown = router
      .getRoutes()
      .flatMap(route => toArray(route.meta.permissions))
      .filter(code => !KNOWN_PERMISSION_CODES.includes(code))
    expect([...new Set(unknown)].sort()).toEqual([])
  })

  it('路由表里没有任何路由声明 meta.roles（授权边界统一由权限码表达）', () => {
    // 第五轮把 `/security`、`/system` 的 `meta.roles: ['ADMIN']` 撤掉了：
    // 它和权限码表达的是同一条边界，两处各写一遍必然漂移（运行态冒烟发现
    // OPERATOR/VIEWER 的后端 200 但 UI 进不去）。将来若要恢复角色级门控，
    // 请同时给菜单项加 roles，并把这条断言改成「菜单声明集合 == 路由声明集合」。
    const paths = router
      .getRoutes()
      .filter(route => (route.meta.roles?.length ?? 0) > 0)
      .map(route => route.path)
      .sort()
    expect(paths).toEqual([])
  })

  it('菜单项 permission 等于第一视图的 permission（默认落点的门控与菜单一致）', () => {
    const offenders: string[] = []
    for (const group of menuGroups) {
      for (const item of group.children) {
        const first = item.tabs[0]
        const itemCodes = toArray(item.permission).slice().sort()
        // 首视图未声明码时继承菜单项（这是 menu.ts 的既有语义）
        const tabCodes = toArray(first.permission ?? item.permission).slice().sort()
        if (JSON.stringify(itemCodes) !== JSON.stringify(tabCodes)) {
          offenders.push(`${item.path}: 项=${JSON.stringify(itemCodes)} 首视图=${JSON.stringify(tabCodes)}`)
        }
      }
    }
    expect(offenders).toEqual([])
  })

  it('多视图项允许视图各自声明码，但每个视图码都必须被该视图的路由 meta.permissions 覆盖', () => {
    const offenders: string[] = []
    for (const group of menuGroups) {
      for (const item of group.children) {
        for (const tab of item.tabs) {
          const declared = toArray(tab.permission ?? item.permission)
          const meta = toArray(router.resolve(tab.to).meta.permissions)
          const uncovered = declared.filter(code => !meta.includes(code))
          if (uncovered.length > 0) {
            offenders.push(`${tab.to} 的 ${uncovered.join(', ')} 不在路由 meta.permissions 里`)
          }
        }
      }
    }
    expect(offenders).toEqual([])
  })

  it('除登录页外，每个可直达页面都能从菜单进入（防新增页面忘记挂菜单）', () => {
    const menuTabPaths = new Set(
      menuGroups.flatMap(group => group.children.flatMap(item => item.tabs.map(tab => tab.to)))
    )
    expect(menuTabPaths.size, '菜单 tab 集合异常小，疑似 menu.ts 结构变动').toBeGreaterThanOrEqual(45)

    /** 有意不挂菜单的页面：登录页。新增例外必须写清理由，不要为了让测试变绿而放宽 */
    const NON_MENU_PAGES = new Set(['/login'])

    const uncovered = router
      .getRoutes()
      .filter(route => route.components?.default && !route.redirect && !route.path.includes(':'))
      .map(route => route.path)
      .filter(path => !menuTabPaths.has(path) && !NON_MENU_PAGES.has(path))
      .sort()

    expect(uncovered, '这些页面没有对应的菜单入口：挂到某个 tab，或登记到 NON_MENU_PAGES 并说明理由').toEqual([])
  })
})


