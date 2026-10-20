<template>
  <span
    class="status-pill"
    :class="`status-pill--${resolvedTone}`"
    role="status"
    :title="status"
    :aria-label="status"
  >
    <span class="status-pill__dot" aria-hidden="true" />
    <span class="status-pill__text">{{ status }}</span>
  </span>
</template>

<script setup lang="ts">
import { computed } from 'vue'

/**
 * 状态徽标（设计 §4.4 的用户口径版本）：
 * `status` 是展示文本，`tone` 决定配色；未给 `tone` 时按关键词推断。
 *
 * 颜色不是唯一信息载体：文本 + `role="status"` + `title`/`aria-label` 一并给出。
 */
type StatusTone = 'success' | 'warning' | 'danger' | 'info' | 'neutral' | 'brand'

const props = defineProps<{
  status: string
  tone?: StatusTone
}>()

/** 设计 §4.4 的精确状态映射（大写枚举）优先于关键词推断 */
const EXACT_TONES: Record<string, StatusTone> = {
  CLOSED: 'success',
  ACTIVE: 'success',
  HEALTHY: 'success',
  HALF_OPEN: 'warning',
  DEGRADED: 'warning',
  OPEN: 'danger',
  INACTIVE: 'danger',
  UNHEALTHY: 'danger'
}

/**
 * 关键词推断。按「词」比对而非子串，避免 `INACTIVE` 命中 `active`；
 * danger 先于 success，保证 `FAILED/DOWN` 不被别的词抢走。
 */
const KEYWORD_TONES: ReadonlyArray<{ tone: StatusTone; keywords: readonly string[] }> = [
  { tone: 'danger', keywords: ['danger', 'error', 'failed', 'fail', 'down'] },
  { tone: 'warning', keywords: ['warning', 'warn', 'degraded', 'slow'] },
  { tone: 'success', keywords: ['success', 'running', 'healthy', 'active', 'ok'] }
]

const resolvedTone = computed<StatusTone>(() => {
  if (props.tone) return props.tone

  const normalized = props.status.trim().toUpperCase()
  const exact = EXACT_TONES[normalized]
  if (exact) return exact

  const tokens = props.status
    .toLowerCase()
    .split(/[^a-z0-9]+/)
    .filter(Boolean)
  for (const rule of KEYWORD_TONES) {
    if (rule.keywords.some(keyword => tokens.includes(keyword))) {
      return rule.tone
    }
  }
  return 'neutral'
})
</script>

<style scoped>
.status-pill {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  max-width: 100%;
  padding: 2px 8px;
  border: 1px solid color-mix(in srgb, currentColor 32%, transparent);
  border-radius: 999px;
  background: color-mix(in srgb, currentColor 12%, transparent);
  color: var(--ja-ink-2);
  font-size: 12px;
  font-weight: 500;
  line-height: 18px;
  white-space: nowrap;
}

.status-pill__dot {
  flex: none;
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: currentColor;
}

.status-pill__text {
  overflow: hidden;
  text-overflow: ellipsis;
}

.status-pill--success {
  color: var(--ja-success);
}

.status-pill--warning {
  color: var(--ja-warning);
}

.status-pill--danger {
  color: var(--ja-danger);
}

.status-pill--info {
  color: var(--ja-info);
}

.status-pill--neutral {
  color: var(--ja-ink-2);
}

.status-pill--brand {
  color: var(--ja-accent);
}
</style>
