<template>
  <div class="page-skeleton">
    <!-- 页面标题区 -->
    <div class="page-header">
      <div class="page-title">
        <slot name="title">
          <h2 v-if="title">{{ title }}</h2>
        </slot>
      </div>
      <div v-if="$slots.actions" class="page-actions">
        <slot name="actions" />
      </div>
    </div>

    <!-- 统计卡片区（可选） -->
    <div v-if="$slots.stats" class="page-stats">
      <slot name="stats" />
    </div>

    <!-- 工具栏（搜索/筛选/排序） -->
    <div v-if="$slots.toolbar" class="page-toolbar">
      <slot name="toolbar" />
    </div>

    <!-- 主内容区 -->
    <div class="page-content">
      <slot />
    </div>

    <!-- 分页/底栏 -->
    <div v-if="$slots.footer" class="page-footer">
      <slot name="footer" />
    </div>
  </div>
</template>

<script setup lang="ts">
defineProps<{
  title?: string
}>()
</script>

<style scoped>
.page-skeleton {
  padding: var(--ja-page-padding, 18px);
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.page-title h2 {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
  color: var(--ja-text-primary);
}

.page-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.page-stats {
  margin-bottom: 16px;
}

.page-toolbar {
  margin-bottom: 16px;
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.page-content {
  /* 主内容区无需额外间距，由子元素自行控制 */
}

.page-footer {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
  align-items: center;
}
</style>
