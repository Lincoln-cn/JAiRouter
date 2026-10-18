import type { Directive, DirectiveBinding } from 'vue'
import { useUserStore } from '@/stores/user'

/**
 * `v-permission` 指令（`#468` 方案 A：无权限不渲染）
 *
 * 用法：
 * ```vue
 * <el-button v-permission="'config:adapters:write'" @click="handleCreate">新建</el-button>
 * <el-button v-permission="['config:rules:write', 'config:rules:manage']">…</el-button>
 * ```
 *
 * 语义：
 * - 值为**空**（`undefined` / `''` / `[]`）⇒ 不限制（与 `usePermission().hasPermission()` 一致）；
 * - 值为**单个权限码** ⇒ 有该码才渲染；
 * - 值为**数组** ⇒ **任一**命中即渲染（OR）；
 * - 判定统一走 `userStore.hasPermission(code)`：ADMIN 恒 true、旧令牌（`permissions` 为空）不限制。
 *
 * ## 为什么是"移除元素"而不是 `v-if`
 *
 * 指令只能在元素已创建后介入，所以这里从父节点摘掉它。副作用是**该组件实例仍会被创建**（`mounted` 已跑）。
 * 对"按钮级显隐"够用；若将来需要"连组件都不创建"，应当用 `v-if="hasPermission(...)"` 而不是本指令。
 *
 * ## 为什么不在 `updated` 里恢复
 *
 * 权限在会话内不变（切账号会重新登录并整页重载），所以只在 `mounted` 判定一次。
 * 若将来支持"运行中改权限"，需要改成 `v-if` 形态。
 *
 * ## 这只是**显示层**门控
 *
 * 后端仍会按 `PermissionRuleRegistry` 独立裁决（403）。本指令不构成任何安全边界。
 */
export function isPermissionGranted(
  codes: string | string[] | undefined | null,
  hasPermission: (code: string) => boolean
): boolean {
  if (codes === undefined || codes === null) {
    return true
  }
  const list = Array.isArray(codes) ? codes : [codes]
  const meaningful = list.filter((code) => typeof code === 'string' && code.length > 0)
  if (meaningful.length === 0) {
    return true
  }
  return meaningful.some((code) => hasPermission(code))
}

export const permission: Directive<HTMLElement, string | string[]> = {
  mounted(el: HTMLElement, binding: DirectiveBinding<string | string[]>) {
    const userStore = useUserStore()
    if (!isPermissionGranted(binding.value, (code) => userStore.hasPermission(code))) {
      // 从 DOM 里摘掉；父节点可能已被移除（并发渲染），故判空。
      el.parentNode?.removeChild(el)
    }
  }
}

export default permission
