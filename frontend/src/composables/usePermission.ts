import { useUserStore } from '@/stores/user'
import type { Audience } from '@/stores/user'
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
 * 角色判定与 `router/index.ts` 的守卫同源（都是 `userInfo.roles`）。**当前没有任何菜单项
 * 或路由声明 roles**：`/security`、`/system` 父级的 `meta.roles` 已撤掉，授权边界统一交给
 * 权限码 —— 否则会出现「后端放行、前端藏入口」的反向不可达（四角色冒烟实测：
 * OPERATOR/VIEWER 持 `security:audit:read`，审计接口 200，UI 却进不去）。
 * `roles` 相关能力保留，将来若恢复角色级门控，菜单与路由必须同时声明并保持同口径。
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
   * 判断当前用户的 audience 是否命中菜单声明（数组语义为「或」）
   *
   * 与 permission 是**且**关系：角色不对或权限码不足都不显示。
   * 未声明 audience（或缺省/空数组）表示不按 audience 收窄。
   *
   * @param audience 允许的 audience 列表
   */
  const hasAudience = (audience?: Audience[]): boolean => {
    if (!audience || audience.length === 0) {
      return true
    }
    const mine = userStore.audiences
    return audience.some(a => mine.includes(a))
  }

  /**
   * 菜单项是否对当前用户可见：角色命中，且**至少有一个可进的 tab**
   *
   * 不能只判 `item.permission`：那是 tabs 的「或」聚合，可能出现「菜单项可点、
   * 但每个 tab 的码都缺」的情况（例：只有 `lb:config:read` 的用户），点进去会被
   * 路由守卫弹回仪表板 —— 2026-11-16 收口时改成本判定。
   */
  const canAccessItem = (item: MenuItem): boolean => {
    if (!hasAudience(item.audience)) {
      return false
    }
    if (!hasRouteRoles(item.roles)) {
      return false
    }
    if (item.tabs.length === 0) {
      return hasAnyPermission(item.permission)
    }
    return getAccessibleTabs(item).length > 0
  }

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
        hasAudience(tab.audience ?? item.audience) &&
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
    hasAudience,
    hasPermission,
    hasAnyPermission,
    hasRouteRoles,
    filterMenuByPermission,
    getAccessibleTabs,
    resolveLandingPath
  }
}
