<template>
  <div class="filter-bar">
    <div
      class="filter-bar__viewport"
      :class="{ 'is-collapsed': canToggle && isCollapsed }"
      :style="collapsedStyle"
    >
      <div ref="contentRef" class="filter-bar__content">
        <slot />
      </div>
    </div>

    <div v-if="canToggle" class="filter-bar__toggle">
      <slot name="toggle" :expanded="!isCollapsed" :toggle="toggle">
        <el-button
          class="filter-bar__toggle-button"
          link
          type="primary"
          :aria-expanded="!isCollapsed"
          :aria-label="isCollapsed ? expandLabel : collapseLabel"
          :title="isCollapsed ? expandLabel : collapseLabel"
          @click="toggle"
        >
          <el-icon>
            <ArrowDown v-if="isCollapsed" />
            <ArrowUp v-else />
          </el-icon>
        </el-button>
      </slot>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { ArrowDown, ArrowUp } from '@element-plus/icons-vue'

/**
 * 筛选区容器（设计 §4.3）：只负责响应式换行与「展开/收起」，不渲染字段、不做防抖。
 *
 * - 纯插槽：字段与「查询 / 重置」按钮全部由调用方提供。
 * - 收起时按「行」裁剪：用子元素 `offsetTop` 归组得到真实行高，不写死行高常量。
 * - 无横向溢出（行数 ≤ maxRows）时按钮不渲染。
 * - 展开/收起按钮默认只有图标，文案由 `expandLabel` / `collapseLabel`（或 `#toggle` 插槽）提供，
 *   组件内不写死任何中英文文案。
 */
const props = withDefaults(
  defineProps<{
    /** 是否启用「展开/收起」；关闭后只做换行容器 */
    collapsible?: boolean
    /** 收起时保留的行数 */
    maxRows?: number
    /** 初始是否展开（仅在有溢出时有意义） */
    defaultExpanded?: boolean
    /** 收起状态下按钮的 aria-label / title */
    expandLabel?: string
    /** 展开状态下按钮的 aria-label / title */
    collapseLabel?: string
  }>(),
  {
    collapsible: true,
    maxRows: 1,
    defaultExpanded: false,
    expandLabel: undefined,
    collapseLabel: undefined
  }
)

const contentRef = ref<HTMLElement | null>(null)
const rowCount = ref(0)
const collapsedHeight = ref<number | null>(null)
const isCollapsed = ref(!props.defaultExpanded)

const effectiveMaxRows = computed(() => Math.max(1, Math.floor(props.maxRows)))
const canToggle = computed(
  () => props.collapsible && rowCount.value > effectiveMaxRows.value
)

const collapsedStyle = computed<Record<string, string | undefined>>(() => {
  if (!canToggle.value || !isCollapsed.value || collapsedHeight.value === null) {
    return {}
  }
  return { maxHeight: `${collapsedHeight.value}px` }
})

const measure = (): void => {
  const el = contentRef.value
  if (!el || !props.collapsible) {
    rowCount.value = 0
    collapsedHeight.value = null
    return
  }

  const rows: number[] = []
  const children = Array.from(el.children) as HTMLElement[]
  for (const child of children) {
    if (!rows.includes(child.offsetTop)) {
      rows.push(child.offsetTop)
    }
  }
  rows.sort((a, b) => a - b)

  rowCount.value = rows.length
  const limit = effectiveMaxRows.value
  collapsedHeight.value =
    rows.length > limit ? Math.max(0, rows[limit]! - rows[0]!) : null
}

const toggle = (): void => {
  isCollapsed.value = !isCollapsed.value
}

let resizeObserver: ResizeObserver | null = null

onMounted(() => {
  measure()
  if (props.collapsible && typeof ResizeObserver !== 'undefined') {
    resizeObserver = new ResizeObserver(() => measure())
    if (contentRef.value) {
      resizeObserver.observe(contentRef.value)
    }
  }
})

onBeforeUnmount(() => {
  resizeObserver?.disconnect()
  resizeObserver = null
})

watch(
  () => [props.collapsible, props.maxRows] as const,
  () => {
    void nextTick(measure)
  }
)
</script>

<style scoped>
.filter-bar {
  display: flex;
  flex-direction: column;
  gap: var(--ja-space-2, 8px);
  min-width: 0;
}

.filter-bar__viewport {
  min-width: 0;
  transition: max-height 0.2s ease;
}

.filter-bar__viewport.is-collapsed {
  overflow: hidden;
}

.filter-bar__content {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--ja-space-3, 12px);
  min-width: 0;
}

.filter-bar__toggle {
  display: flex;
  justify-content: center;
}

.filter-bar__toggle-button {
  color: var(--ja-primary);
}
</style>
