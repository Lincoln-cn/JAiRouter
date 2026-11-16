import { describe, expect, it } from 'vitest'
import { menuGroups } from '@/config/menu'
import router from '@/router'

/**
 * 菜单 ↔ 路由守卫一致性（死入口守卫，2026-11-16）
 *
 * 背景：`/security`、`/system` 两个父路由声明了 `meta.roles = ['ADMIN']`，而菜单此前
 * 只按权限码过滤 ⇒ OPERATOR / USER / VIEWER 能看到「审计日志」「记录治理」「集群设置」
 * 等入口，点击后被守卫重定向回仪表板（看得见、点不进）。
 *
 * 修法：菜单项显式声明 `roles`，本测试用真实的 `router.resolve()` 取**合并后**的
 * `meta.roles`（vue-router 会把父级 roles 合并到子路由）与菜单声明逐一对照，
 * 任何一侧单边改动都会在这里红。
 */
const toArray = (value?: string | string[]): string[] =>
  value ? (Array.isArray(value) ? [...value] : [value]) : []

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

  it('路由表里声明 meta.roles 的只有 /security 与 /system 两个父级', () => {
    const paths = router
      .getRoutes()
      .filter(route => (route.meta.roles?.length ?? 0) > 0)
      .map(route => route.path)
      .sort()
    expect(paths).toEqual(['/security', '/system'])
  })
})
