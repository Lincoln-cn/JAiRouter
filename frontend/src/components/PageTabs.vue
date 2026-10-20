<template>
  <el-tabs
    v-if="visibleTabs.length > 1"
    class="page-tabs"
    :model-value="activeKey"
    @tab-change="handleTabChange"
  >
    <el-tab-pane
      v-for="tab in visibleTabs"
      :key="tab.key"
      :label="tab.label"
      :name="tab.key"
    />
  </el-tabs>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { usePermission } from '@/composables/usePermission'

/**
 * 一个菜单项承载多个旧路由时的 tab 条（设计 §4.1）。
 *
 * - URL 是唯一状态源：`el-tabs` 只用 `:model-value`，导航一律走 `router.push`，
 *   不写 `v-model`（否则路由与组件双源，切页会互相打架）。
 * - 权限判定只经 `usePermission().hasPermission`；`permission` 为数组时是「或」语义。
 * - 可见 tab ≤ 1 时整体不渲染，避免只剩一个 tab 的空壳。
 */
interface PageTab {
  key: string
  label: string
  to: string
  permission?: string | string[]
}

const props = defineProps<{
  tabs: PageTab[]
  /** 选中项的 key（也接受 `to` 路径）；缺省按当前 `route.path` 反查 */
  active?: string
}>()

const route = useRoute()
const router = useRouter()
const { hasPermission } = usePermission()

const canSee = (tab: PageTab): boolean => {
  if (!tab.permission) return true
  const codes = Array.isArray(tab.permission) ? tab.permission : [tab.permission]
  // 「或」聚合：任一权限码命中即可见（空数组沿用「未配置 = 放行」不成立，故单独短路）
  if (codes.length === 0) return true
  return codes.some(code => hasPermission(code))
}

const visibleTabs = computed(() => props.tabs.filter(canSee))

const activeKey = computed(() => {
  if (props.active) {
    const matched = visibleTabs.value.find(
      tab => tab.key === props.active || tab.to === props.active
    )
    return matched ? matched.key : props.active
  }
  return visibleTabs.value.find(tab => tab.to === route.path)?.key ?? ''
})

const handleTabChange = (name: string | number): void => {
  const tab = visibleTabs.value.find(item => item.key === String(name))
  if (!tab) return
  // 同路径不重复导航（redundant navigation 会被 vue-router 记为重复）
  if (route.path === tab.to) return
  void router.push(tab.to)
}
</script>

<style scoped>
.page-tabs {
  margin-bottom: var(--ja-space-3, 12px);
}

.page-tabs :deep(.el-tabs__header) {
  margin-bottom: 0;
}

/* tab 只做导航，内容由 router-view 渲染 */
.page-tabs :deep(.el-tabs__content) {
  display: none;
}

.page-tabs :deep(.el-tabs__item) {
  color: var(--ja-ink-2, var(--ja-text-regular));
  font-size: 13px;
}

.page-tabs :deep(.el-tabs__item:hover) {
  color: var(--ja-primary);
}

.page-tabs :deep(.el-tabs__item.is-active) {
  color: var(--ja-primary);
  font-weight: 600;
}

.page-tabs :deep(.el-tabs__active-bar) {
  background-color: var(--ja-primary);
}

.page-tabs :deep(.el-tabs__nav-wrap::after) {
  background-color: var(--ja-border);
}
</style>
