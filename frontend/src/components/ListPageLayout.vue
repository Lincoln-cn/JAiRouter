<template>
  <div class="list-page-layout">
    <div v-if="$slots.header || $slots.actions" class="list-page-layout__header">
      <div class="list-page-layout__heading">
        <slot name="header" />
      </div>
      <div v-if="$slots.actions" class="list-page-layout__actions">
        <slot name="actions" />
      </div>
    </div>

    <div v-if="$slots.filters" class="list-page-layout__filters">
      <slot name="filters" />
    </div>

    <div v-if="$slots.toolbar" class="list-page-layout__toolbar">
      <slot name="toolbar" />
    </div>

    <div class="list-page-layout__body">
      <PageSkeleton v-if="loading" />
      <EmptyState
        v-else-if="empty"
        :title="emptyTitle"
        :description="emptyDescription"
      >
        <template v-if="$slots['empty-actions'] || emptyActionText" #actions>
          <slot name="empty-actions">
            <el-button v-if="emptyActionText" type="primary" @click="emit('empty-action')">
              {{ emptyActionText }}
            </el-button>
          </slot>
        </template>
      </EmptyState>
      <slot v-else />
    </div>

    <div v-if="$slots.footer" class="list-page-layout__footer">
      <slot name="footer" />
    </div>

    <slot name="drawer" />
  </div>
</template>

<script setup lang="ts">
import PageSkeleton from '@/components/PageSkeleton.vue'
import EmptyState from '@/components/EmptyState.vue'

/**
 * 列表页六槽骨架（设计 §4.2）：
 * `header` / `actions` / `filters` / `toolbar` / 默认（表格） / `footer`，另有 `drawer` 透传槽。
 *
 * 三态优先级：loading → PageSkeleton；empty → EmptyState；其余渲染默认槽。
 * 空态文案全部由调用方传入（组件内不写死任何中英文文案）。
 */
withDefaults(
  defineProps<{
    loading?: boolean
    empty?: boolean
    emptyTitle?: string
    emptyDescription?: string
    /** 传入后在空态里渲染主行动按钮，点击发 `empty-action` */
    emptyActionText?: string
  }>(),
  {
    loading: false,
    empty: false,
    emptyTitle: undefined,
    emptyDescription: undefined,
    emptyActionText: undefined
  }
)

const emit = defineEmits<{
  (e: 'empty-action'): void
}>()
</script>

<style scoped>
.list-page-layout {
  display: flex;
  flex-direction: column;
  gap: var(--ja-space-4, 16px);
  min-width: 0;
}

.list-page-layout__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--ja-space-3, 12px);
  flex-wrap: wrap;
}

.list-page-layout__heading {
  min-width: 0;
  color: var(--ja-ink, var(--ja-text-primary));
}

.list-page-layout__actions {
  display: flex;
  align-items: center;
  gap: var(--ja-space-2, 8px);
  flex-wrap: wrap;
}

.list-page-layout__body {
  min-width: 0;
}

/* 筛选区在窄屏必须能收缩：页面的 el-form 是 inline 布局，日期区间等控件带固定宽度，
   容器不设 min-width:0 时会把内容区撑出横向滚动（P1-3 实测 390px 下溢出 216px）。 */
.list-page-layout__filters {
  min-width: 0;
}

.list-page-layout__filters :deep(.el-form-item) {
  max-width: 100%;
}

.list-page-layout__filters :deep(.el-form-item__content) {
  min-width: 0;
  flex-wrap: wrap;
}

.list-page-layout__filters :deep(.el-date-editor),
.list-page-layout__filters :deep(.el-input),
.list-page-layout__filters :deep(.el-select) {
  max-width: 100%;
}

.list-page-layout__footer {
  display: flex;
  justify-content: flex-end;
  align-items: center;
  gap: var(--ja-space-2, 8px);
  flex-wrap: wrap;
}
</style>
