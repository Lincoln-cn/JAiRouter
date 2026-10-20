<template>
  <section class="chart-card">
    <header class="chart-card__head">
      <h3 class="chart-card__title">{{ title }}</h3>
      <div v-if="$slots.actions" class="chart-card__actions">
        <slot name="actions" />
      </div>
    </header>

    <div class="chart-card__body" :style="bodyStyle">
      <!-- 默认槽始终挂载：页面在 onMounted 里对图表容器调 echarts.init()，卸载会让 ref 变 null -->
      <slot />

      <!-- 空态覆盖层：不卸载图表容器，数据回来时不会白屏 -->
      <div v-if="empty && !loading" class="chart-card__empty">
        <EmptyState :title="emptyTitle" :description="emptyDescription">
          <template v-if="$slots['empty-action']" #actions>
            <slot name="empty-action" />
          </template>
        </EmptyState>
      </div>

      <!-- 加载覆盖层：同样保留默认槽，避免 echarts 实例被销毁 -->
      <div v-if="loading" class="chart-card__skeleton" aria-busy="true" />
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import EmptyState from '@/components/EmptyState.vue'

/**
 * 统一图表卡（P1-2）：标题 + 右侧工具区 + 加载/空态覆盖层。
 *
 * 三态用「覆盖层」而不是「条件渲染默认槽」：三个目标页面都在 onMounted 里
 * 对默认槽中的图表容器调用 echarts.init()，若 empty/loading 时卸载默认槽，
 * ref 会变 null、实例被销毁，数据回来后白屏。
 *
 * 组件内不写死任何文案；空态标题/描述由调用方传入（页面侧用 i18n 的
 * `common.chartEmpty` / `common.chartEmptyHint`）。
 */
const props = withDefaults(
  defineProps<{
    title: string
    loading?: boolean
    empty?: boolean
    emptyTitle?: string
    emptyDescription?: string
    height?: number | string
  }>(),
  {
    loading: false,
    empty: false,
    emptyTitle: undefined,
    emptyDescription: undefined,
    height: 300
  }
)

/** 无论 loading / empty / 正常态，body 都保持同一高度，避免布局跳动。 */
const bodyStyle = computed<Record<string, string>>(() => ({
  height: typeof props.height === 'number' ? `${props.height}px` : props.height
}))
</script>

<style scoped>
.chart-card {
  display: flex;
  flex-direction: column;
  gap: var(--ja-space-4);
  min-width: 0;
  padding: var(--ja-space-5);
  background: var(--ja-paper);
  border: 1px solid var(--ja-border);
  border-radius: var(--ja-radius-lg);
}

.chart-card__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--ja-space-3);
  flex-wrap: wrap;
}

.chart-card__title {
  margin: 0;
  color: var(--ja-ink);
  font-size: 14px;
  font-weight: 600;
  line-height: 1.4;
}

.chart-card__actions {
  display: flex;
  align-items: center;
  gap: var(--ja-space-2);
  flex-wrap: wrap;
}

.chart-card__body {
  position: relative;
  min-width: 0;
}

.chart-card__empty {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--ja-paper);
  border-radius: var(--ja-radius);
}

.chart-card__skeleton {
  position: absolute;
  inset: 0;
  border-radius: var(--ja-radius);
  background: var(--ja-surface-2);
  animation: chart-card-pulse 1.4s ease-in-out infinite;
}

@keyframes chart-card-pulse {
  0%,
  100% {
    opacity: 1;
  }

  50% {
    opacity: 0.55;
  }
}

@media (prefers-reduced-motion: reduce) {
  .chart-card__skeleton {
    animation: none;
  }
}
</style>
