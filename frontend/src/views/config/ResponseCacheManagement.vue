<template>
  <PageSkeleton :title="t('responseCache.pageTitle')">
    <template #actions>
      <el-button @click="loadStatus" :loading="loading" size="default">
        <el-icon><Refresh /></el-icon>
        {{ t('responseCache.refreshStatus') }}
      </el-button>
    </template>

    <!-- 顶部状态卡片 -->
    <template #stats>
      <el-row :gutter="16">
        <el-col :span="6">
          <StatCard
            :icon="status?.enabled ? 'CircleCheck' : 'CircleClose'"
            :label="t('responseCache.cacheStatusLabel')"
            :value="status?.enabled ? t('responseCache.enabled') : t('responseCache.disabled')"
            :tone="status?.enabled ? 'success' : 'info'"
          />
        </el-col>
        <el-col :span="6">
          <StatCard
            icon="Timer"
            :label="t('responseCache.ttlLabel')"
            :value="status?.ttlSeconds ?? '—'"
            :unit="t('responseCache.ttlUnit')"
            tone="primary"
          />
        </el-col>
        <el-col :span="6">
          <StatCard
            icon="Box"
            :label="t('responseCache.maxEntriesLabel')"
            :value="status?.maxSize ?? '—'"
            tone="warning"
          />
        </el-col>
        <el-col :span="6">
          <StatCard
            icon="DataLine"
            :label="t('responseCache.currentEntriesLabel')"
            :value="status?.size ?? '—'"
            tone="info"
          />
        </el-col>
      </el-row>
      <el-row :gutter="16" style="margin-top: 16px">
        <el-col :span="8">
          <StatCard
            icon="SuccessFilled"
            :label="t('responseCache.hitsLabel')"
            :value="status?.hits ?? '—'"
            tone="success"
          />
        </el-col>
        <el-col :span="8">
          <StatCard
            icon="CircleCloseFilled"
            :label="t('responseCache.missesLabel')"
            :value="status?.misses ?? '—'"
            tone="danger"
          />
        </el-col>
        <el-col :span="8">
          <StatCard
            icon="TrendCharts"
            :label="t('responseCache.hitRateLabel')"
            :value="hitRatioDisplay"
            tone="primary"
          />
        </el-col>
      </el-row>
    </template>

    <!-- 状态未就绪提示 -->
    <el-alert
      v-if="!status"
      :title="t('responseCache.notReadyTitle')"
      :description="t('responseCache.notReadyDescription')"
      type="warning"
      :closable="false"
      show-icon
      style="margin-bottom: 16px"
    />

    <!-- 运行时配置面板 -->
    <el-card v-if="status" shadow="hover" style="margin-bottom: 16px">
      <template #header>
        <span class="card-title">{{ t('responseCache.runtimeConfigTitle') }}</span>
      </template>

      <div class="runtime-config-grid">
        <div class="config-switch-item">
          <span class="config-switch-label">{{ t('responseCache.enableCacheLabel') }}</span>
          <el-switch v-model="runtimeForm.enabled" />
        </div>
        <div class="config-switch-item">
          <span class="config-switch-label">{{ t('responseCache.skipStreamingLabel') }}</span>
          <el-switch v-model="runtimeForm.skipStreaming" />
        </div>
        <div class="config-switch-item">
          <span class="config-switch-label">{{ t('responseCache.onlyDeterministicLabel') }}</span>
          <el-switch v-model="runtimeForm.onlyDeterministic" />
        </div>
        <div class="config-switch-item">
          <span class="config-switch-label">{{ t('responseCache.ttlSecondsLabel') }}</span>
          <el-input-number
            v-model="runtimeForm.ttlSeconds"
            :min="1"
            :max="604800"
            :step="60"
            controls-position="right"
            :placeholder="t('responseCache.ttlPlaceholder')"
            style="width: 180px"
          />
        </div>
      </div>

      <div class="config-actions">
        <el-button
          type="primary"
          :loading="savingConfig"
          :disabled="!configChanged"
          @click="handleSaveConfig"
        >
          <el-icon><Check /></el-icon>
          {{ t('responseCache.saveRuntimeConfig') }}
        </el-button>
        <el-button
          :disabled="savingConfig"
          @click="resetRuntimeForm"
        >
          {{ t('responseCache.reset') }}
        </el-button>
      </div>

      <div class="config-hint">
        <el-icon><InfoFilled /></el-icon>
        <span>{{ t('responseCache.runtimeHintBefore') }} <code>jairouter.response-cache.*</code></span>
      </div>
      <div class="config-hint">
        <el-icon><InfoFilled /></el-icon>
        <span><code>maxSize</code> {{ t('responseCache.maxSizeHintAfter') }}</span>
      </div>
    </el-card>

    <!-- 操作区：缓存失效 -->
    <el-card shadow="hover">
      <template #header>
        <span class="card-title">{{ t('responseCache.invalidationTitle') }}</span>
      </template>

      <!-- 清空全部 -->
      <div class="action-section">
        <div class="action-label">{{ t('responseCache.clearAllCache') }}</div>
        <el-popconfirm
          :title="t('responseCache.clearAllConfirmTitle')"
          :confirm-button-text="t('responseCache.confirm')"
          :cancel-button-text="t('responseCache.cancel')"
          @confirm="handleInvalidateAll"
        >
          <template #reference>
            <el-button type="danger" :loading="invalidating" :disabled="!status?.enabled">
              <el-icon><Delete /></el-icon>
              {{ t('responseCache.clearAllCache') }}
            </el-button>
          </template>
        </el-popconfirm>
      </div>

      <el-divider />

      <!-- 按服务类型失效 -->
      <div class="action-section">
        <div class="action-label">{{ t('responseCache.invalidateByServiceTypeLabel') }}</div>
        <div class="action-row">
          <el-select
            v-model="selectedServiceType"
            :placeholder="t('responseCache.selectServiceTypePlaceholder')"
            style="width: 200px"
            clearable
          >
            <el-option
              v-for="st in serviceTypes"
              :key="st"
              :label="st"
              :value="st"
            />
          </el-select>
          <el-button
            type="warning"
            :loading="invalidating"
            :disabled="!selectedServiceType || !status?.enabled"
            @click="handleInvalidateByServiceType"
          >
            <el-icon><Delete /></el-icon>
            {{ t('responseCache.invalidateByService') }}
          </el-button>
        </div>
      </div>

      <el-divider />

      <!-- 按模型失效 -->
      <div class="action-section">
        <div class="action-label">{{ t('responseCache.invalidateByModelLabel') }}</div>
        <div class="action-row">
          <el-input
            v-model="modelName"
            :placeholder="t('responseCache.inputModelPlaceholder')"
            style="width: 300px"
            clearable
          />
          <el-button
            type="warning"
            :loading="invalidating"
            :disabled="!modelName.trim() || !status?.enabled"
            @click="handleInvalidateByModel"
          >
            <el-icon><Delete /></el-icon>
            {{ t('responseCache.invalidateByModel') }}
          </el-button>
        </div>
      </div>
    </el-card>

    <!-- 提示 -->
    <el-alert
      :title="t('responseCache.tipTitle')"
      type="info"
      :closable="false"
      show-icon
      style="margin-top: 16px"
    >
      <template #default>
        <p style="margin: 0">{{ t('responseCache.tipBefore') }}<code>jairouter.response-cache.*</code>{{ t('responseCache.tipAfter') }}</p>
      </template>
    </el-alert>
  </PageSkeleton>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import {
  Refresh,
  Delete,
  InfoFilled,
  CircleCheck,
  CircleClose,
  Timer,
  Box,
  DataLine,
  Check,
  SuccessFilled,
  CircleCloseFilled,
  TrendCharts,
} from '@element-plus/icons-vue'
import PageSkeleton from '@/components/PageSkeleton.vue'
import StatCard from '@/components/StatCard.vue'
import {
  getCacheStatus,
  invalidateCache,
  updateCacheConfig,
  type CacheStatus,
  type CacheConfigPayload,
} from '@/api/responseCache'

const { t } = useI18n()

const loading = ref(false)
const invalidating = ref(false)
const savingConfig = ref(false)
const status = ref<CacheStatus | null>(null)
const selectedServiceType = ref('')
const modelName = ref('')

/** 后端 ServiceType 枚举值 */
const serviceTypes = ['chat', 'embedding', 'rerank', 'tts', 'stt', 'imgGen', 'imgEdit']

// ── 运行时配置表单 ──

interface RuntimeForm {
  enabled: boolean
  skipStreaming: boolean
  onlyDeterministic: boolean
  /** undefined = 未修改（不传），number = 用户修改后的值 */
  ttlSeconds: number | undefined
}

const runtimeForm = ref<RuntimeForm>({
  enabled: false,
  skipStreaming: false,
  onlyDeterministic: false,
  ttlSeconds: undefined,
})

/** 用当前 snapshot 填充运行时表单（同步基准值，不重新赋 ttlSeconds = undefined） */
const syncFormFromStatus = (s: CacheStatus) => {
  runtimeForm.value.enabled = s.enabled
  runtimeForm.value.skipStreaming = s.skipStreaming
  runtimeForm.value.onlyDeterministic = s.onlyDeterministic
  runtimeForm.value.ttlSeconds = undefined
}

/** 重置表单到 snapshot 原值 */
const resetRuntimeForm = () => {
  if (status.value) syncFormFromStatus(status.value)
}

/** 命中率显示 */
const hitRatioDisplay = computed(() => {
  const ratio = status.value?.hitRatio ?? null
  if (ratio === null) return t('responseCache.noData')
  return `${(ratio * 100).toFixed(1)}%`
})

/**
 * 判断表单是否有变更，决定「保存」按钮是否可点。
 * 仅发送与 snapshot 不同的字段；未动字段不传。
 */
const configChanged = computed(() => {
  if (!status.value) return false
  const s = status.value
  if (runtimeForm.value.enabled !== s.enabled) return true
  if (runtimeForm.value.skipStreaming !== s.skipStreaming) return true
  if (runtimeForm.value.onlyDeterministic !== s.onlyDeterministic) return true
  if (runtimeForm.value.ttlSeconds !== undefined && runtimeForm.value.ttlSeconds !== s.ttlSeconds) return true
  return false
})

// ── 数据加载 ──

const loadStatus = async () => {
  loading.value = true
  try {
    const s = await getCacheStatus()
    status.value = s
    if (s) syncFormFromStatus(s)
  } catch {
    status.value = null
  } finally {
    loading.value = false
  }
}

// ── 保存运行时配置（diff → payload） ──

const handleSaveConfig = async () => {
  if (!status.value) return
  const s = status.value
  const payload: CacheConfigPayload = {}

  if (runtimeForm.value.enabled !== s.enabled) {
    payload.enabled = runtimeForm.value.enabled
  }
  if (runtimeForm.value.skipStreaming !== s.skipStreaming) {
    payload.skipStreaming = runtimeForm.value.skipStreaming
  }
  if (runtimeForm.value.onlyDeterministic !== s.onlyDeterministic) {
    payload.onlyDeterministic = runtimeForm.value.onlyDeterministic
  }
  if (runtimeForm.value.ttlSeconds !== undefined && runtimeForm.value.ttlSeconds !== s.ttlSeconds) {
    payload.ttlSeconds = runtimeForm.value.ttlSeconds
  }

  // 后端：全空 payload → 400
  if (Object.keys(payload).length === 0) {
    ElMessage.warning(t('responseCache.noChangesWarning'))
    return
  }

  savingConfig.value = true
  try {
    await updateCacheConfig(payload)
    ElMessage.success(t('responseCache.configUpdatedSuccess'))
    await loadStatus()
  } catch {
    ElMessage.error(t('responseCache.saveFailedError'))
  } finally {
    savingConfig.value = false
  }
}

// ── 缓存失效操作 ──

const handleInvalidateAll = async () => {
  invalidating.value = true
  try {
    const result = await invalidateCache()
    if (result.executed) {
      ElMessage.success(t('responseCache.cacheClearedSuccess'))
    } else {
      ElMessage.warning(t('responseCache.cacheNotEnabledWarning'))
    }
    await loadStatus()
  } catch {
    ElMessage.error(t('responseCache.operationFailedError'))
  } finally {
    invalidating.value = false
  }
}

const handleInvalidateByServiceType = async () => {
  if (!selectedServiceType.value) return
  invalidating.value = true
  try {
    const result = await invalidateCache({ serviceType: selectedServiceType.value })
    if (result.executed) {
      ElMessage.success(t('responseCache.invalidatedByServiceType', { serviceType: selectedServiceType.value }))
    } else {
      ElMessage.warning(t('responseCache.cacheNotEnabledWarning'))
    }
    await loadStatus()
  } catch {
    ElMessage.error(t('responseCache.operationFailedError'))
  } finally {
    invalidating.value = false
  }
}

const handleInvalidateByModel = async () => {
  const model = modelName.value.trim()
  if (!model) return
  invalidating.value = true
  try {
    const result = await invalidateCache({ model })
    if (result.executed) {
      ElMessage.success(t('responseCache.invalidatedByModel', { model }))
    } else {
      ElMessage.warning(t('responseCache.cacheNotEnabledWarning'))
    }
    await loadStatus()
  } catch {
    ElMessage.error(t('responseCache.operationFailedError'))
  } finally {
    invalidating.value = false
  }
}

onMounted(loadStatus)
</script>

<style scoped>
.card-title {
  font-weight: 600;
  font-size: 15px;
}

.action-section {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.action-label {
  font-size: 14px;
  font-weight: 500;
  color: var(--ja-text-primary);
}

.action-row {
  display: flex;
  align-items: center;
  gap: 12px;
}

.config-hint {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 12px;
  font-size: 12px;
  color: var(--ja-text-secondary, var(--el-text-color-secondary));
}

.runtime-config-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 16px 32px;
  margin-bottom: 16px;
}

.config-switch-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 12px;
  border-radius: var(--ja-radius-md, 8px);
  background: var(--ja-bg-hover, var(--el-fill-color-light));
}

.config-switch-label {
  font-size: 14px;
  color: var(--ja-text-primary);
  font-weight: 500;
}

.config-actions {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 4px;
}
</style>
