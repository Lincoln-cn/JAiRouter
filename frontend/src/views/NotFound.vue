<template>
  <div class="not-found">
    <el-card shadow="never" class="not-found-card">
      <div class="code">404</div>
      <h2 class="title">{{ t('notFound.title') }}</h2>
      <p class="desc">{{ t('notFound.description') }}</p>
      <p class="path">
        <span class="path-label">{{ t('notFound.pathLabel') }}：</span>
        <code>{{ currentPath }}</code>
      </p>
      <div class="actions">
        <el-button type="primary" @click="goDashboard">{{ t('notFound.backHome') }}</el-button>
        <el-button @click="goBack">{{ t('notFound.backPrev') }}</el-button>
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
/**
 * 未匹配路由的兜底页面.
 *
 * <p>路由表此前没有 catch-all，手输或收藏一个已失效的地址会得到整页空白
 * （布局也不渲染，页面标题仍是默认的 "JAiRouter"），看起来像服务挂了。
 * 这里给出明确的 404 说明、当前路径与两条退路。</p>
 */
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()

const currentPath = computed(() => route.fullPath)

function goDashboard(): void {
  router.push({ name: 'dashboard-main' })
}

function goBack(): void {
  // 直接打开 404 页时没有上一条历史，退化成回仪表板，避免按钮点了没反应
  if (window.history.length > 1) {
    router.back()
  } else {
    goDashboard()
  }
}
</script>

<style scoped>
.not-found {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 60vh;
  padding: 24px;
}

.not-found-card {
  width: 100%;
  max-width: 520px;
  text-align: center;
}

.code {
  font-size: 64px;
  font-weight: 700;
  line-height: 1.1;
  color: var(--el-color-primary);
  letter-spacing: 2px;
}

.title {
  margin: 8px 0 12px;
  font-size: 20px;
  color: var(--el-text-color-primary);
}

.desc {
  margin: 0 0 16px;
  font-size: 14px;
  line-height: 1.6;
  color: var(--el-text-color-regular);
}

.path {
  margin: 0 0 24px;
  font-size: 13px;
  color: var(--el-text-color-secondary);
  word-break: break-all;
}

.path code {
  padding: 2px 6px;
  border-radius: 4px;
  background: var(--el-fill-color-light);
  font-family: var(--el-font-family-mono, monospace);
}

.actions {
  display: flex;
  gap: 12px;
  justify-content: center;
}
</style>
