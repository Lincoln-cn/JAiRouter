import { useUserStore } from '@/stores/user'
import type { MenuGroup } from '@/config/menu'

/**
 * 菜单权限 composable（v2.9.8 Phase 3 数据驱动）
 *
 * - `hasPermission(code)`：判断当前用户是否拥有指定权限码（ADMIN 恒 true；
 *   未提供 code 视为无权限限制；用户权限数据为空时不限制，向后兼容旧令牌）
 * - `filterMenuByPermission(groups)`：按当前用户权限过滤菜单组/子项
 *
 * 核心判定逻辑在 user store 的 `hasPermission`，本 composable 仅做菜单层包装。
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
   * 按当前用户权限过滤菜单
   *
   * - 子项无 permission 字段 → 保留（所有已登录用户可见）
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
        children: group.children.filter(item => hasPermission(item.permission))
      }))
      .filter(group => group.children.length > 0)
  }

  return {
    hasPermission,
    filterMenuByPermission
  }
}
