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
  flex-wrap: wrap;
  gap: 12px;
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
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 8px;
  min-width: 0;
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

/* 窄屏：工具条里的内联表单默认把表单项排成一行（el-form--inline 的子项不换行），
   单个日期范围选择器就能把 390px 撑出 300px 横向滚动。断点与侧栏强制折叠一致。 */
@media (max-width: 767px) {
  .page-toolbar {
    min-width: 0;
  }

  .page-toolbar :deep(.el-form--inline) {
    display: flex;
    flex-wrap: wrap;
    gap: var(--ja-space-2);
    width: 100%;
  }

  .page-toolbar :deep(.el-form-item) {
    margin-right: 0;
    max-width: 100%;
  }

  .page-toolbar :deep(.el-form-item__content) {
    min-width: 0;
  }

  .page-toolbar :deep(.el-date-editor),
  .page-toolbar :deep(.el-input),
  .page-toolbar :deep(.el-select) {
    max-width: 100%;
  }
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
