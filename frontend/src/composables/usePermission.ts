import { useUserStore } from '@/stores/user'
import type { MenuGroup, MenuItem } from '@/config/menu'

/**
 * 菜单权限 composable（v2.9.8 Phase 3 数据驱动）
 *
 * - `hasPermission(code)`：判断当前用户是否拥有指定权限码（ADMIN 恒 true；
 *   未提供 code 视为无权限限制；用户权限数据为空时不限制，向后兼容旧令牌）
 * - `hasAnyPermission(permission)`：支持 `string | string[]`，数组语义为「或」
 * - `hasRouteRoles(roles)`：判断当前用户的角色是否命中声明（数组语义为「或」）
 * - `filterMenuByPermission(groups)`：按当前用户权限**与角色**过滤菜单组/子项
 * - `getAccessibleTabs(item)`：菜单项的 tab 级门控（菜单可见 ≠ 每个 tab 可用）
 *
 * 角色判定与 `router/index.ts` 的守卫同源（都是 `userInfo.roles`）：`/security`、
 * `/system` 父级声明了 `roles: ['ADMIN']`，菜单若只看权限码就会列出「点了被重定向」
 * 的死入口 —— 所以菜单项用 `roles` 把同一条约束显式写下来。
 *
 * 核心判定逻辑在 user store 的 `hasPermission`，本 composable 仅做菜单层包装。
 * 注意：`hasPermission` 的 fail-open（无权限数据时不限制）是已裁定的既有语义，
 * 本批只扩展数组 OR，不改变单码判定。
 */
export function usePermission() {
  const userStore = useUserStore()

  /**
   * 判断当前用户是否拥有权限码
   *
   * @param code 权限码（module:resource:action）；缺省视为无权限限制
   * @returns 拥有权限（或无需权限限制）返回 true
   */
  const hasPermission = (code?: string): boolean => {
    if (!code) {
      return true
    }
    return userStore.hasPermission(code)
  }

  /**
   * 判断是否命中权限声明（数组为「任一命中即通过」）
   *
   * @param permission 单个权限码、权限码数组或空
   * @returns 空声明视为无需权限限制；数组为空同样视为无限制
   */
  const hasAnyPermission = (permission?: string | string[]): boolean => {
    if (!permission) {
      return true
    }
    const codes = Array.isArray(permission) ? permission : [permission]
    if (codes.length === 0) {
      return true
    }
    return codes.some(code => hasPermission(code))
  }

  /**
   * 判断当前用户是否命中角色限制（缺省或空数组视为不限制）
   *
   * 与路由守卫 `meta.roles` 判定同一数据源（`userStore.hasAnyRole` → `userInfo.roles`），
   * 保证「菜单可见」与「点进去会不会被重定向」口径一致。
   *
   * @param roles 允许的角色名；空/未声明表示不限制
   */
  const hasRouteRoles = (roles?: string[]): boolean => {
    if (!roles || roles.length === 0) {
      return true
    }
    return userStore.hasAnyRole(roles)
  }

  /**
   * 菜单项是否对当前用户可见：权限码（或）**且** 角色（或）
   */
  const canAccessItem = (item: MenuItem): boolean =>
    hasAnyPermission(item.permission) && hasRouteRoles(item.roles)

  /**
   * 按当前用户权限过滤菜单
   *
   * - 子项无 permission 字段 → 保留（所有已登录用户可见）
   * - 子项 permission 为数组 → 任一命中即保留（聚合了不同权限码页面）
   * - 子项声明 roles 时，还需角色命中（与路由守卫同口径）
   * - 子项有 permission 字段但用户无对应权限 → 移除
   * - 组内全部子项被移除 → 组整体隐藏
   *
   * @param groups 完整菜单配置（menuGroups）
   * @returns 过滤后的菜单（不修改入参）
   */
  const filterMenuByPermission = (groups: MenuGroup[]): MenuGroup[] => {
    return groups
      .map(group => ({
        ...group,
        children: group.children.filter(canAccessItem)
      }))
      .filter(group => group.children.length > 0)
  }

  /**
   * 取菜单项中当前用户可见的 tab（按 menu.ts 声明顺序）
   *
   * tab 未声明 permission/roles 时沿用菜单项的同名字段。
   *
   * @param item 菜单项
   * @returns 可见 tab 列表；可能为空（调用方负责回退）
   */
  const getAccessibleTabs = (item: MenuItem) => {
    return item.tabs.filter(
      tab =>
        hasAnyPermission(tab.permission ?? item.permission) &&
        hasRouteRoles(tab.roles ?? item.roles)
    )
  }

  /**
   * 菜单项的落地页：第一个有权限的 tab；全部无权限时回退第一个 tab（不留空导航）
   *
   * @param item 菜单项
   * @returns 目标路由；无 tab 时回退菜单项主路由
   */
  const resolveLandingPath = (item: MenuItem): string => {
    const accessible = getAccessibleTabs(item)
    return accessible[0]?.to ?? item.tabs[0]?.to ?? item.path
  }

  return {
    hasPermission,
    hasAnyPermission,
    hasRouteRoles,
    filterMenuByPermission,
    getAccessibleTabs,
    resolveLandingPath
  }
}
