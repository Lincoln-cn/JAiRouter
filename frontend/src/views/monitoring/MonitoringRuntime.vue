<template>
  <PageSkeleton :title="t('monitoringRuntime.pageTitle')">
    <template #actions>
      <el-button size="default" :loading="loading" @click="loadAll">
        <el-icon><Refresh /></el-icon>
        {{ t('monitoringRuntime.refresh') }}
      </el-button>
    </template>

    <template #toolbar>
      <p class="runtime-hint">
        <el-icon><InfoFilled /></el-icon>
        <span>{{ t('monitoringRuntime.hint') }}</span>
      </p>
    </template>

    <el-card shadow="hover" class="runtime-card">
      <template #header>
        <span class="card-title">{{ t('monitoringRuntime.healthTitle') }}</span>
      </template>
      <el-descriptions :column="2" border>
        <el-descriptions-item :label="t('monitoringRuntime.statusLabel')">
          <el-tag :type="health?.healthy ? 'success' : 'danger'" effect="plain">
            {{ health?.healthy ? t('monitoringRuntime.healthy') : t('monitoringRuntime.unhealthy') }}
          </el-tag>
          <span v-if="health?.status" class="status-text">{{ health.status }}</span>
        </el-descriptions-item>
        <el-descriptions-item :label="t('monitoringRuntime.issuesLabel')">
          {{ health?.issues?.length ? health.issues.join('; ') : t('monitoringRuntime.noIssues') }}
        </el-descriptions-item>
      </el-descriptions>
    </el-card>

    <el-card shadow="hover" class="runtime-card">
      <template #header>
        <span class="card-title">{{ t('monitoringRuntime.degradationTitle') }}</span>
      </template>
      <el-descriptions :column="2" border>
        <el-descriptions-item :label="t('monitoringRuntime.levelLabel')">
          {{ readString(degradation, 'level') }}
        </el-descriptions-item>
        <el-descriptions-item :label="t('monitoringRuntime.autoModeLabel')">
          {{ readBooleanLabel(degradation, 'autoModeEnabled') }}
        </el-descriptions-item>
      </el-descriptions>

      <div v-if="canWrite" class="runtime-actions">
        <div class="inline-field">
          <span class="config-label">{{ t('monitoringRuntime.levelTitle') }}</span>
          <el-select v-model="degradationLevel" style="width: 180px">
            <el-option v-for="level in degradationLevels" :key="level" :label="level" :value="level" />
          </el-select>
        </div>
        <ConfirmAction
          v-permission="'monitoring:config:write'"
          :title="forceRecoveryTitle"
          :width="300"
          :confirm-text="t('monitoringRuntime.forceRecovery')"
          @confirm="applyAction(forceMonitoringRecovery)"
        >
          <el-button type="warning" plain>{{ t('monitoringRuntime.forceRecovery') }}</el-button>
        </ConfirmAction>
        <el-button
          v-permission="'monitoring:config:write'"
          :loading="acting"
          @click="applyAction(() => setMonitoringDegradationLevel(degradationLevel))"
        >
          {{ t('monitoringRuntime.levelTitle') }}
        </el-button>
        <el-switch
          v-permission="'monitoring:config:write'"
          v-model="degradationAutoMode"
          :active-text="t('monitoringRuntime.autoModeTitle')"
          @change="onAutoModeChange"
        />
      </div>
    </el-card>

    <el-card shadow="hover" class="runtime-card">
      <template #header>
        <span class="card-title">{{ t('monitoringRuntime.errorStatsTitle') }}</span>
      </template>
      <pre class="raw-block">{{ pretty(errorStats) }}</pre>

      <div v-if="canWrite" class="runtime-actions">
        <div class="inline-field">
          <span class="config-label">{{ t('monitoringRuntime.componentLabel') }}</span>
          <el-input v-model="errorComponent" style="width: 160px" />
        </div>
        <div class="inline-field">
          <span class="config-label">{{ t('monitoringRuntime.operationLabel') }}</span>
          <el-input v-model="errorOperation" style="width: 160px" />
        </div>
        <ConfirmAction
          v-permission="'monitoring:config:write'"
          :title="resetErrorsTitle"
          :width="320"
          :confirm-text="t('monitoringRuntime.resetErrors')"
          @confirm="applyAction(() => resetMonitoringErrors(errorComponent, errorOperation))"
        >
          <el-button type="warning" plain>{{ t('monitoringRuntime.resetErrors') }}</el-button>
        </ConfirmAction>
      </div>
    </el-card>

    <el-card shadow="hover" class="runtime-card">
      <template #header>
        <span class="card-title">{{ t('monitoringRuntime.cacheTitle') }}</span>
      </template>
      <pre class="raw-block">{{ pretty(cacheStats) }}</pre>

      <div v-if="canWrite" class="runtime-actions">
        <ConfirmAction
          v-permission="'monitoring:config:write'"
          :title="clearCacheTitle"
          :width="320"
          :confirm-text="t('monitoringRuntime.clearCache')"
          @confirm="applyAction(clearMonitoringCache)"
        >
          <el-button type="danger" plain>{{ t('monitoringRuntime.clearCache') }}</el-button>
        </ConfirmAction>
      </div>
    </el-card>

    <el-card shadow="hover" class="runtime-card">
      <template #header>
        <span class="card-title">{{ t('monitoringRuntime.pipelineBreakerTitle') }}</span>
      </template>
      <pre class="raw-block">{{ pretty(pipelineBreakerStats) }}</pre>

      <div v-if="canWrite" class="runtime-actions">
        <ConfirmAction
          v-permission="'monitoring:config:write'"
          :title="pipelineBreakerTitle"
          :width="300"
          :confirm-text="t('monitoringRuntime.openBreaker')"
          @confirm="applyAction(forceOpenPipelineBreaker)"
        >
          <el-button type="danger" plain>{{ t('monitoringRuntime.openBreaker') }}</el-button>
        </ConfirmAction>
        <ConfirmAction
          v-permission="'monitoring:config:write'"
          :title="pipelineCloseBreakerTitle"
          :width="240"
          :confirm-text="t('monitoringRuntime.closeBreaker')"
          @confirm="applyAction(forceClosePipelineBreaker)"
        >
          <el-button type="success" plain>{{ t('monitoringRuntime.closeBreaker') }}</el-button>
        </ConfirmAction>
      </div>
    </el-card>

    <el-card shadow="hover" class="runtime-card">
      <template #header>
        <span class="card-title">{{ t('monitoringRuntime.monitoringBreakerTitle') }}</span>
      </template>
      <pre class="raw-block">{{ pretty(monitoringBreakerStats) }}</pre>

      <div v-if="canWrite" class="runtime-actions">
        <ConfirmAction
          v-permission="'monitoring:config:write'"
          :title="monitoringBreakerTitle"
          :width="300"
          :confirm-text="t('monitoringRuntime.openBreaker')"
          @confirm="applyAction(forceOpenMonitoringBreaker)"
        >
          <el-button type="danger" plain>{{ t('monitoringRuntime.openBreaker') }}</el-button>
        </ConfirmAction>
        <ConfirmAction
          v-permission="'monitoring:config:write'"
          :title="monitoringCloseBreakerTitle"
          :width="240"
          :confirm-text="t('monitoringRuntime.closeBreaker')"
          @confirm="applyAction(forceCloseMonitoringBreaker)"
        >
          <el-button type="success" plain>{{ t('monitoringRuntime.closeBreaker') }}</el-button>
        </ConfirmAction>
      </div>
    </el-card>
  </PageSkeleton>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { InfoFilled, Refresh } from '@element-plus/icons-vue'
import PageSkeleton from '@/components/PageSkeleton.vue'
import ConfirmAction from '@/components/ConfirmAction.vue'
import { usePermission } from '@/composables/usePermission'
import {
  countErrorEntries,
  readBreakerState,
  readCacheEntries,
  readDegradationLevel
} from './runtimePreview'
import {
  clearMonitoringCache,
  getMonitoringEmergencyPreview,
  type MonitoringEmergencyAction,
  forceCloseMonitoringBreaker,
  forceClosePipelineBreaker,
  forceMonitoringRecovery,
  forceOpenMonitoringBreaker,
  forceOpenPipelineBreaker,
  getMonitoringBreakerStats,
  getMonitoringCacheStats,
  getMonitoringDegradationStatus,
  getMonitoringErrorStats,
  getMonitoringHealth,
  getMonitoringPipelineBreakerStats,
  resetMonitoringErrors,
  setMonitoringDegradationAutoMode,
  setMonitoringDegradationLevel,
  type MonitoringHealth
} from '@/api/monitoringConfig'

const { t } = useI18n()
const { hasPermission } = usePermission()

const canWrite = computed(() => hasPermission('monitoring:config:write'))

const DEGRADATION_LEVELS = ['NONE', 'PARTIAL', 'FULL', 'EMERGENCY']

const loading = ref(false)
const acting = ref(false)
const health = ref<MonitoringHealth | null>(null)
const degradation = ref<Record<string, unknown> | null>(null)
const errorStats = ref<Record<string, unknown> | null>(null)
const cacheStats = ref<Record<string, unknown> | null>(null)
const pipelineBreakerStats = ref<Record<string, unknown> | null>(null)
const monitoringBreakerStats = ref<Record<string, unknown> | null>(null)

/**
 * 应急操作的 impact 快照（#1173）：key 是 action，value 是后端返回的 `impact` 对象。
 *
 * <p>拉不到时保持空对象，各确认文案自动退回「通用文案 / 本地可算的数字」。</p>
 */
const previews = ref<Partial<Record<MonitoringEmergencyAction, Record<string, unknown>>>>({})

const degradationLevel = ref('NONE')
const degradationAutoMode = ref(true)
const errorComponent = ref('')
const errorOperation = ref('')
const degradationLevels = DEGRADATION_LEVELS

/**
 * 取后端 preview 里的某个数字字段；缺失 / 类型不符返回 null。
 *
 * <p>预览接口（#1173）是「影响面」的权威来源：它能说出「清掉的是哪几个指标」，
 * 而页面从只读状态里只能算「有几条」。取不到时退回本地推算，再不行退回通用文案。</p>
 */
const previewNumber = (action: MonitoringEmergencyAction, key: string): number | null => {
  const value = previews.value[action]?.[key]
  return typeof value === 'number' && Number.isFinite(value) ? value : null
}

/** 取后端 preview 里的文本字段（状态 / 级别），缺失或空串返回 null。 */
const previewText = (action: MonitoringEmergencyAction, key: string): string | null => {
  const value = previews.value[action]?.[key]
  return typeof value === 'string' && value.trim().length > 0 ? value : null
}

/** 取后端 preview 里的键名样本（最多 3 个），用于在确认文案里举例。 */
const previewKeys = (action: MonitoringEmergencyAction, key: string): string[] => {
  const value = previews.value[action]?.[key]
  if (!Array.isArray(value)) {
    return []
  }
  return value
    .map((item) => (typeof item === 'string' ? item : ''))
    .filter((item) => item.length > 0)
    .slice(0, 3)
}

/**
 * 应急操作的确认文案：把当前影响面拼进去（见 runtimePreview.ts）。
 *
 * 字段缺失时一律退回 i18n 里的通用文案 —— 宁可少一句影响面说明，
 * 也不要在确认气泡里出现猜出来的数字。
 */
const clearCacheTitle = computed(() => {
  const entries = previewNumber('clear-cache', 'entries') ?? readCacheEntries(cacheStats.value)
  if (entries === null) {
    return t('monitoringRuntime.clearCacheConfirm')
  }
  const keys = previewKeys('clear-cache', 'sampleKeys')
  return keys.length > 0
    ? t('monitoringRuntime.clearCacheConfirmWithKeys', { entries, keys: keys.join(', ') })
    : t('monitoringRuntime.clearCacheConfirmWithEntries', { entries })
})

const resetErrorsTitle = computed(() => {
  const component = errorComponent.value.trim()
  const operation = errorOperation.value.trim()
  const keys = previewNumber('reset-errors', 'keys') ?? countErrorEntries(errorStats.value)
  if (!component || !operation || keys === null) {
    return t('monitoringRuntime.resetErrorsConfirm')
  }
  const total = previewNumber('reset-errors', 'totalErrors')
  return total === null
    ? t('monitoringRuntime.resetErrorsConfirmWithTarget', { component, operation, keys })
    : t('monitoringRuntime.resetErrorsConfirmWithTargetAndTotal', { component, operation, keys, total })
})

const forceRecoveryTitle = computed(() => {
  const level = previewText('force-recovery', 'level') ?? readDegradationLevel(degradation.value)
  return level === null
    ? t('monitoringRuntime.forceRecoveryConfirm')
    : t('monitoringRuntime.forceRecoveryConfirmWithLevel', { level })
})

const pipelineBreakerTitle = computed(() => {
  const state = previewText('breaker-open', 'state') ?? readBreakerState(pipelineBreakerStats.value)
  return state === null
    ? t('monitoringRuntime.openBreakerConfirm')
    : t('monitoringRuntime.openBreakerConfirmWithState', { state })
})

const monitoringBreakerTitle = computed(() => {
  const state = previewText('breaker-open', 'state') ?? readBreakerState(monitoringBreakerStats.value)
  return state === null
    ? t('monitoringRuntime.openBreakerConfirm')
    : t('monitoringRuntime.openBreakerConfirmWithState', { state })
})

const pipelineCloseBreakerTitle = computed(() => {
  const state = previewText('breaker-close', 'state') ?? readBreakerState(pipelineBreakerStats.value)
  return state === null
    ? t('monitoringRuntime.closeBreakerConfirm')
    : t('monitoringRuntime.closeBreakerConfirmWithState', { state })
})

const monitoringCloseBreakerTitle = computed(() => {
  const state = previewText('breaker-close', 'state') ?? readBreakerState(monitoringBreakerStats.value)
  return state === null
    ? t('monitoringRuntime.closeBreakerConfirm')
    : t('monitoringRuntime.closeBreakerConfirmWithState', { state })
})

/** 并发拉 5 个动作的影响面快照；任何一个失败都只是让对应文案退回本地推算。 */
const loadPreviews = async () => {
  const actions: MonitoringEmergencyAction[] = [
    'clear-cache',
    'reset-errors',
    'force-recovery',
    'breaker-open',
    'breaker-close'
  ]
  const results = await Promise.allSettled(
    actions.map((action) => getMonitoringEmergencyPreview(action))
  )
  const next: Partial<Record<MonitoringEmergencyAction, Record<string, unknown>>> = {}
  results.forEach((result, index) => {
    if (result.status === 'fulfilled' && result.value?.impact) {
      next[actions[index]] = result.value.impact
    }
  })
  previews.value = next
}

const readString = (source: Record<string, unknown> | null, key: string): string => {
  const value = source?.[key]
  return value === undefined || value === null ? '-' : String(value)
}

const readBooleanLabel = (source: Record<string, unknown> | null, key: string): string => {
  const value = source?.[key]
  return typeof value === 'boolean' ? (value ? 'true' : 'false') : '-'
}

const pretty = (source: Record<string, unknown> | null): string => {
  if (!source) {
    return '-'
  }
  return JSON.stringify(source, null, 2)
}

const loadAll = async () => {
  loading.value = true
  try {
    const [healthResult, degradationResult, errorResult, cacheResult, pipelineResult, breakerResult] =
      await Promise.allSettled([
        getMonitoringHealth(),
        getMonitoringDegradationStatus(),
        getMonitoringErrorStats(),
        getMonitoringCacheStats(),
        getMonitoringPipelineBreakerStats(),
        getMonitoringBreakerStats()
      ])

    await loadPreviews()

    health.value = healthResult.status === 'fulfilled' ? healthResult.value : null
    degradation.value = degradationResult.status === 'fulfilled' ? degradationResult.value : null
    errorStats.value = errorResult.status === 'fulfilled' ? errorResult.value : null
    cacheStats.value = cacheResult.status === 'fulfilled' ? cacheResult.value : null
    pipelineBreakerStats.value = pipelineResult.status === 'fulfilled' ? pipelineResult.value : null
    monitoringBreakerStats.value = breakerResult.status === 'fulfilled' ? breakerResult.value : null

    const level = degradation.value?.level
    if (typeof level === 'string' && DEGRADATION_LEVELS.includes(level)) {
      degradationLevel.value = level
    }
    const autoMode = degradation.value?.autoModeEnabled
    if (typeof autoMode === 'boolean') {
      degradationAutoMode.value = autoMode
    }
  } finally {
    loading.value = false
  }
}

/**
 * 统一执行应急操作：成功后提示并刷新状态；失败只提示，不改动页面状态。
 *
 * <p>这些端点在后端都会写 MONITORING_CONFIG_CHANGED 审计（含操作主体），
 * 所以前端不需要再单独上报。
 */
const applyAction = async (action: () => Promise<void>) => {
  acting.value = true
  try {
    await action()
    ElMessage.success(t('monitoringRuntime.done'))
    await loadAll()
  } catch {
    ElMessage.error(t('monitoringRuntime.failed'))
  } finally {
    acting.value = false
  }
}

const onAutoModeChange = async (enabled: boolean) => {
  await applyAction(() => setMonitoringDegradationAutoMode(enabled))
}

onMounted(loadAll)
</script>

<style scoped>
.runtime-hint {
  display: flex;
  gap: 8px;
  align-items: flex-start;
  margin: 0;
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.card-title {
  font-weight: 600;
}

.runtime-card {
  margin-bottom: 16px;
}

.runtime-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  align-items: center;
  margin-top: 16px;
}

.inline-field {
  display: flex;
  gap: 8px;
  align-items: center;
}

.config-label {
  font-size: 13px;
  font-weight: 500;
}

.status-text {
  margin-left: 8px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.raw-block {
  max-height: 240px;
  margin: 0;
  padding: 12px;
  overflow: auto;
  font-size: 12px;
  line-height: 1.5;
  background: var(--el-fill-color-lighter);
  border-radius: 6px;
}
</style>
