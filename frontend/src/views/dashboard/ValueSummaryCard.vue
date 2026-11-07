<template>
  <el-card v-if="!loading" class="vs-card" shadow="never">
    <div class="vs-head">
      <div class="vs-title">{{ t('dashboard.valueTitle') }}</div>
      <div v-if="failed" class="vs-subtitle">{{ t('dashboard.valueUnavailable') }}</div>
      <div v-else class="vs-subtitle">{{ t('dashboard.valueSubtitle') }}</div>
    </div>

    <div class="vs-grid">
      <div class="vs-cell">
        <div class="vs-num">{{ failed ? '--' : hitRateText }}</div>
        <div class="vs-label">{{ t('dashboard.valueCacheHitRate') }}</div>
      </div>
      <div class="vs-cell">
        <div class="vs-num">{{ failed ? '--' : data.savedUpstreamCalls }}</div>
        <div class="vs-label">{{ t('dashboard.valueSavedCalls') }}</div>
      </div>
      <div class="vs-cell">
        <div class="vs-num">{{ failed ? '--' : `${data.guardRedactions} / ${data.guardAborts}` }}</div>
        <div class="vs-label">{{ t('dashboard.valueGuardAsked') }}</div>
      </div>
      <div class="vs-cell">
        <div class="vs-num vs-num-sm">{{ failed ? '--' : slowestText }}</div>
        <div class="vs-label">{{ t('dashboard.valueBackendSlowest') }}</div>
      </div>
    </div>
  </el-card>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { getValueSummary, type ValueSummaryPayload } from '@/api/dashboard'

const { t } = useI18n()

const loading = ref(true)
const failed = ref(false)
const data = ref<ValueSummaryPayload>({
  semanticCacheHits: 0,
  semanticCacheMisses: 0,
  semanticCacheHitRate: 0,
  savedUpstreamCalls: 0,
  guardRedactions: 0,
  guardAborts: 0,
  backendP95: {},
})

const hitRateText = computed(() => `${(data.value.semanticCacheHitRate * 100).toFixed(1)}%`)

// 后端 backendP95 是「每实例最大耗时」的近似（见 api/dashboard.ts 的类型注释），
// 这里展示最慢的那个实例与其耗时；没有出站流量时如实显示「暂无数据」。
const slowestText = computed(() => {
  const entries = Object.entries(data.value.backendP95)
  if (entries.length === 0) {
    return t('dashboard.valueEmpty')
  }
  const [name, ms] = entries.reduce((a, b) => (b[1] > a[1] ? b : a))
  return `${name} · ${Math.round(ms)}ms`
})

onMounted(async () => {
  try {
    const res = await getValueSummary()
    const payload = res.data?.data
    if (payload) {
      data.value = { ...data.value, ...payload }
    } else {
      failed.value = true
    }
  } catch {
    // 监控数据拿不到不该让概览页报错：显示「数据暂不可用」，卡片仍然可见
    failed.value = true
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
.vs-card {
  margin-bottom: 20px;
  border: 1px solid var(--el-border-color-lighter);
}
.vs-head {
  display: flex;
  align-items: baseline;
  gap: 10px;
  margin-bottom: 14px;
}
.vs-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}
.vs-subtitle {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.vs-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: 12px;
}
.vs-cell {
  padding: 12px 14px;
  border-radius: 8px;
  background: var(--el-fill-color-lighter);
}
.vs-num {
  font-size: 22px;
  font-weight: 600;
  line-height: 1.3;
  color: var(--el-text-color-primary);
}
.vs-num-sm {
  font-size: 14px;
  word-break: break-all;
}
.vs-label {
  margin-top: 4px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
</style>
