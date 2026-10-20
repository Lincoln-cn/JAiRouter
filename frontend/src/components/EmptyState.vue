<template>
  <div class="empty-state">
    <div v-if="icon" class="empty-state__icon" aria-hidden="true">
      <el-icon :size="40">
        <component :is="icon" />
      </el-icon>
    </div>

    <p v-if="title" class="empty-state__title">{{ title }}</p>
    <p v-if="description" class="empty-state__description">{{ description }}</p>

    <div v-if="$slots.actions" class="empty-state__actions">
      <slot name="actions" />
    </div>
  </div>
</template>

<script setup lang="ts">
import type { Component } from 'vue'

/**
 * 空状态（设计 §4.6 的用户口径版本）。
 *
 * 组件内不写死任何文案：`title` / `description` 由调用方传入，
 * 主行动按钮通过 `actions` 插槽渲染。`icon` 接受全局注册的图标名
 * （`main.ts` 全量注册了 `@element-plus/icons-vue`）或组件本身。
 */
defineProps<{
  title?: string
  description?: string
  icon?: string | Component
}>()
</script>

<style scoped>
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: var(--ja-space-2, 8px);
  padding: var(--ja-space-8, 32px) var(--ja-space-4, 16px);
  text-align: center;
}

.empty-state__icon {
  color: var(--ja-ink-3);
  line-height: 1;
}

.empty-state__title {
  margin: 0;
  color: var(--ja-ink);
  font-size: 15px;
  font-weight: 600;
}

.empty-state__description {
  max-width: 480px;
  margin: 0;
  color: var(--ja-ink-2);
  font-size: 13px;
  line-height: 1.6;
}

.empty-state__actions {
  display: flex;
  flex-wrap: wrap;
  gap: var(--ja-space-2, 8px);
  justify-content: center;
  margin-top: var(--ja-space-2, 8px);
}
</style>
