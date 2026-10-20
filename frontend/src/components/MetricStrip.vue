<template>
  <section class="metric-strip" :style="gridStyle" :aria-busy="loading">
    <template v-if="loading">
      <div
        v-for="n in columnCount"
        :key="`skeleton-${n}`"
        class="metric metric--skeleton"
      >
        <span class="metric__skeleton-line metric__skeleton-line--value" aria-hidden="true" />
        <span class="metric__skeleton-line metric__skeleton-line--label" aria-hidden="true" />
      </div>
    </template>

    <template v-else>
      <div v-for="item in items" :key="item.key" class="metric">
        <span class="metric__value" :class="toneClass(item.tone)">{{ item.value }}</span>
        <span class="metric__label">{{ item.label }}</span>
        <span v-if="item.hint" class="metric__hint">{{ item.hint }}</span>
      </div>
    </template>
  </section>
</template>

<script lang="ts">
/**
 * 指标条单元。
 *
 * `tone` 只表达「状态」：success / warning / danger 给值上色，
 * default（或缺省）保持中性；不得用于装饰。
 */
export interface MetricStripItem {
  key: string
  label: string
  value: string | number
  hint?: string
  tone?: 'default' | 'success' | 'warning' | 'danger'
}
</script>

<script setup lang="ts">
import { computed } from 'vue'

/**
 * 一行式中性指标条（P1-2）：用于替换「多张彩色 KPI 卡」。
 *
 * 设计纪律：容器与文字只用中性阶，语义色只落在状态值上；
 * 没有背景渐变、没有彩色图标，`--ja-accent` 不参与渲染。
 */
const props = withDefaults(
  defineProps<{
    items: MetricStripItem[]
    loading?: boolean
    columns?: number
  }>(),
  { loading: false, columns: 4 }
)

/** 列数同时决定网格列数与 loading 骨架数量，非法值收敛到 1。 */
const columnCount = computed(() => Math.max(1, Math.floor(props.columns)))

/** 通过 CSS 自定义属性下发列数，样式表用 repeat(var(--metric-columns), …) 消费。 */
const gridStyle = computed(() => ({ '--metric-columns': String(columnCount.value) }))

const toneClass = (tone?: MetricStripItem['tone']): string =>
  tone && tone !== 'default' ? `metric__value--${tone}` : ''
</script>

<style scoped>
.metric-strip {
  display: grid;
  grid-template-columns: repeat(var(--metric-columns, 4), minmax(0, 1fr));
  row-gap: var(--ja-space-5);
  min-width: 0;
  padding: var(--ja-space-5);
  background: var(--ja-paper);
  border: 1px solid var(--ja-border);
  border-radius: var(--ja-radius-lg);
}

.metric {
  display: flex;
  flex-direction: column;
  gap: var(--ja-space-1);
  min-width: 0;
  padding-left: var(--ja-space-5);
}

.metric:first-child {
  padding-left: 0;
}

/* 相邻指标之间一条 1px 分隔线；换行后由媒体查询修正行首 */
.metric + .metric {
  border-left: 1px solid var(--ja-border);
}

.metric__value {
  color: var(--ja-ink);
  font-size: 20px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
  line-height: 1.2;
  overflow-wrap: anywhere;
}

.metric__value--success {
  color: var(--ja-success);
}

.metric__value--warning {
  color: var(--ja-warning);
}

.metric__value--danger {
  color: var(--ja-danger);
}

.metric__label {
  color: var(--ja-ink-3);
  font-size: 12px;
  line-height: 1.4;
}

.metric__hint {
  color: var(--ja-ink-3);
  font-size: 12px;
  line-height: 1.4;
}

/* ── loading 骨架 ── */
.metric__skeleton-line {
  display: block;
  border-radius: var(--ja-radius-sm);
  background: var(--ja-surface-2);
}

.metric__skeleton-line--value {
  width: 60%;
  height: 20px;
}

.metric__skeleton-line--label {
  width: 40%;
  height: 12px;
}

/* ── 响应式：900px 以下 2 列，560px 以下 1 列 ── */
@media (max-width: 900px) {
  .metric-strip {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .metric:nth-child(n) {
    border-left: none;
    padding-left: 0;
  }

  .metric:nth-child(even) {
    border-left: 1px solid var(--ja-border);
    padding-left: var(--ja-space-5);
  }
}

@media (max-width: 560px) {
  .metric-strip {
    grid-template-columns: minmax(0, 1fr);
  }

  .metric:nth-child(n) {
    border-left: none;
    padding-left: 0;
  }
}
</style>
