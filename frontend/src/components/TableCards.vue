<template>
  <div class="table-cards" role="list" :aria-label="ariaLabel || undefined">
    <EmptyState
      v-if="isEmpty"
      :title="emptyTitle || undefined"
      :description="emptyDescription || undefined"
    />
    <div
      v-for="(row, index) in rows"
      v-else
      :key="rowKeyFor(row, index)"
      class="table-cards__item"
      role="listitem"
    >
      <slot :row="row" :index="index" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import EmptyState from './EmptyState.vue'

/**
 * 表格窄屏卡片容器（P1-3）。
 *
 * 只负责「列表语义 + 卡片外观 + 空态」，具体字段与操作由调用方通过
 * 默认作用域插槽（`row` / `index`）渲染，避免把任何一个页面的字段形状
 * 耦合进通用组件。
 *
 * rows 使用 readonly any[] 的原因：三个试点页的数据类型分别是
 * ApiCallHistoryRecord / ExceptionEvent / ApiKeyVO，容器不读取任何字段，
 * 若在这里引入泛型或联合类型，只会把页面类型反向泄漏到通用组件里。
 * 读取字段的责任在页面插槽内，类型安全由各页面自己的 ref 类型保证。
 */
/* eslint-disable @typescript-eslint/no-explicit-any -- 见上方注释：容器只透传行数据，不消费字段 */
interface Props {
  rows: readonly any[]
  rowKey: string | ((row: any) => string | number)
  empty?: boolean
  ariaLabel?: string
  emptyTitle?: string
  emptyDescription?: string
}
/* eslint-enable @typescript-eslint/no-explicit-any */

const props = withDefaults(defineProps<Props>(), {
  empty: false,
  ariaLabel: '',
  emptyTitle: '',
  emptyDescription: ''
})

const isEmpty = computed<boolean>(() => props.empty || props.rows.length === 0)

// eslint-disable-next-line @typescript-eslint/no-explicit-any -- 与 Props.rowKey 的签名保持一致
const getRowKey = (row: any): string | number => {
  if (typeof props.rowKey === 'function') {
    return props.rowKey(row)
  }
  const value = row?.[props.rowKey]
  return typeof value === 'number' ? value : String(value ?? '')
}

// 行数据缺少主键时退回序号，避免 undefined / 空串让 v-for 出现重复 key
// eslint-disable-next-line @typescript-eslint/no-explicit-any -- 与 Props.rowKey 的签名保持一致
const rowKeyFor = (row: any, index: number): string | number => {
  const key = getRowKey(row)
  return key === '' ? `table-card-${index}` : key
}
</script>

<style scoped>
.table-cards {
  display: flex;
  flex-direction: column;
  gap: var(--ja-space-3, 12px);
  min-width: 0;
}

.table-cards__item {
  display: flex;
  flex-direction: column;
  gap: var(--ja-space-2, 8px);
  min-width: 0;
  padding: var(--ja-space-4, 16px);
  background: var(--ja-paper);
  border: 1px solid var(--ja-border);
  border-radius: var(--ja-radius-lg, 12px);
  color: var(--ja-ink);
  overflow-wrap: anywhere;
}

/* 窄屏下卡片列表保持单列，仅收紧间距与内边距，避免一屏只放得下一张卡 */
@media (max-width: 767px) {
  .table-cards {
    gap: var(--ja-space-2, 8px);
  }

  .table-cards__item {
    padding: var(--ja-space-3, 12px);
  }
}
</style>
