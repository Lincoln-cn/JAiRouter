<template>
  <el-card
    class="stat-card"
    :class="tone !== 'default' ? `stat-card--${tone}` : ''"
    shadow="hover"
  >
    <div class="stat-inner">
      <div class="icon-box">
        <el-icon class="stat-icon">
          <component :is="icon" />
        </el-icon>
      </div>
      <div class="text-box">
        <div class="value">
          {{ formattedValue }}<span v-if="unit" class="unit">{{ unit }}</span>
        </div>
        <div class="label">{{ label }}</div>
      </div>
    </div>
  </el-card>
</template>

<script setup lang="ts">
import { computed } from 'vue'

const props = withDefaults(defineProps<{
  icon: any
  label: string
  value: string | number
  unit?: string
  /** 色调：映射到 --ja-stat-tone-* 功能色令牌 */
  tone?: 'primary' | 'success' | 'warning' | 'danger' | 'info' | 'default'
}>(), {
  tone: 'default',
})

const formattedValue = computed(() => {
  if (typeof props.value === 'number') return props.value.toLocaleString()
  return props.value
})
</script>

<style scoped>
.stat-card {
  border-radius: var(--ja-radius-lg);
  overflow: hidden;
  min-height: 88px;
  display: flex;
  align-items: center;
  background: var(--ja-bg-card);
  /* 默认色调 */
  --ja-stat-tone-bg: var(--ja-primary-light-9);
  --ja-stat-tone-icon: var(--ja-dashboard-icon-color);
}

.stat-inner {
  display: flex;
  align-items: center;
  width: 100%;
}

.icon-box {
  width: 56px;
  height: 56px;
  border-radius: var(--ja-radius-lg);
  display: flex;
  align-items: center;
  justify-content: center;
  margin-right: 12px;
  box-shadow: var(--ja-shadow-sm);
  background: var(--ja-stat-tone-bg);
}

.stat-icon {
  font-size: 22px;
  color: var(--ja-stat-tone-icon);
}

.text-box {
  flex: 1;
  min-width: 0;
}

.value {
  font-size: 20px;
  font-weight: 700;
  color: var(--ja-dashboard-value-color);
}

.unit {
  font-size: 12px;
  font-weight: 400;
  margin-left: 2px;
  color: var(--ja-dashboard-label-color);
}

.label {
  font-size: 12px;
  color: var(--ja-dashboard-label-color);
  margin-top: 4px;
}

/* ── 色调变体 ── */
.stat-card--primary {
  --ja-stat-tone-bg: var(--ja-primary-light-9);
  --ja-stat-tone-icon: var(--ja-primary);
}

.stat-card--success {
  --ja-stat-tone-bg: var(--el-color-success-light-9, var(--ja-primary-light-9));
  --ja-stat-tone-icon: var(--ja-success);
}

.stat-card--warning {
  --ja-stat-tone-bg: var(--el-color-warning-light-9, var(--ja-primary-light-9));
  --ja-stat-tone-icon: var(--ja-warning);
}

.stat-card--danger {
  --ja-stat-tone-bg: var(--el-color-danger-light-9, var(--ja-primary-light-9));
  --ja-stat-tone-icon: var(--ja-danger);
}

.stat-card--info {
  --ja-stat-tone-bg: var(--el-color-info-light-9, var(--ja-primary-light-9));
  --ja-stat-tone-icon: var(--ja-info);
}

/* ── 响应式 ── */
@media (max-width: 640px) {
  .stat-card {
    min-height: 78px;
  }
  .icon-box {
    width: 48px;
    height: 48px;
  }
}
</style>
