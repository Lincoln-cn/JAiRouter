<template>
  <ConfigPageShell
    :title="t('responseCache.pageTitle')"
    :loading="loading"
    :saving="saving"
    :saving-label="savingLabel"
    :dirty-count="dirtyCount"
    :can-write="canWrite"
    save-permission="config:cache:write"
    @refresh="loadAll"
    @save="handleSave"
    @discard="handleDiscard"
  >
    <MetricStrip
      class="summary-strip"
      :items="metricItems"
      :loading="loading && !status"
      :columns="4"
    />

    <el-alert
      v-if="!status"
      :title="t('responseCache.notReadyTitle')"
      :description="t('responseCache.notReadyDescription')"
      type="warning"
      :closable="false"
      show-icon
      style="margin-bottom: 16px"
    />

    <template v-else>
      <!-- 运行时配置：三个开关即时生效，TTL 走动作条草稿 -->
      <ConfigSectionCard
        :title="t('responseCache.runtimeConfigTitle')"
        :error="errors.runtime || switchError"
      >
        <div class="runtime-config-grid">
          <div class="config-switch-item">
            <span class="config-switch-label">{{ t('responseCache.enableCacheLabel') }}</span>
            <SettingsSwitch
              :model-value="form.enabled"
              :disabled="!canWrite"
              :immediate-hint="t('responseCache.immediateHint')"
              :submit="submitEnabled"
              @update:model-value="form.enabled = $event"
            />
          </div>
          <div class="config-switch-item">
            <span class="config-switch-label">{{ t('responseCache.skipStreamingLabel') }}</span>
            <SettingsSwitch
              :model-value="form.skipStreaming"
              :disabled="!canWrite"
              :submit="submitSkipStreaming"
              @update:model-value="form.skipStreaming = $event"
            />
          </div>
          <div class="config-switch-item">
            <span class="config-switch-label">{{ t('responseCache.onlyDeterministicLabel') }}</span>
            <SettingsSwitch
              :model-value="form.onlyDeterministic"
              :disabled="!canWrite"
              :submit="submitOnlyDeterministic"
              @update:model-value="form.onlyDeterministic = $event"
            />
          </div>
          <div class="config-switch-item">
            <span class="config-switch-label">{{ t('responseCache.ttlSecondsLabel') }}</span>
            <el-input-number
              v-model="form.ttlSeconds"
              :min="1"
              :max="604800"
              :step="60"
              :disabled="!canWrite"
              controls-position="right"
              style="width: 180px"
            />
            <span class="unit">{{ t('responseCache.ttlUnit') }}</span>
          </div>
        </div>

        <p class="config-hint">
          {{ t('responseCache.runtimeHintBefore') }}
          <code>jairouter.response-cache.*</code>
        </p>
        <p class="config-hint">
          <code>maxSize</code>
          {{ t('responseCache.maxSizeHintAfter') }}
        </p>
      </ConfigSectionCard>

      <!-- 缓存失效（危险操作）：不进动作条，逐个二次确认 -->
      <ConfigSectionCard
        :title="t('responseCache.invalidationTitle')"
        :error="invalidateError"
      >
        <div class="invalidate-row">
          <div class="invalidate-row__text">
            <span class="action-label">{{ t('responseCache.invalidateByServiceTypeLabel') }}</span>
            <el-select
              v-model="selectedServiceType"
              clearable
              :placeholder="t('responseCache.selectServiceTypePlaceholder')"
              :disabled="!canWrite"
              style="width: 200px"
            >
              <el-option v-for="type in serviceTypes" :key="type" :label="type" :value="type" />
            </el-select>
          </div>
          <ConfirmAction
            :title="serviceTypeConfirmTitle"
            :confirm-text="t('responseCache.invalidateByService')"
            :cancel-text="t('responseCache.cancel')"
            type="warning"
            :disabled="!canWrite || !selectedServiceType || !status?.enabled"
            :loading="invalidating"
            @confirm="handleInvalidateByServiceType"
          >
            <el-button
              v-permission="'config:cache:write'"
              type="warning"
              plain
              :icon="Delete"
              :disabled="!selectedServiceType || !status?.enabled"
              :loading="invalidating"
            >
              {{ t('responseCache.invalidateByService') }}
            </el-button>
          </ConfirmAction>
        </div>

        <el-divider />

        <div class="invalidate-row">
          <div class="invalidate-row__text">
            <span class="action-label">{{ t('responseCache.invalidateByModelLabel') }}</span>
            <el-input
              v-model="modelName"
              clearable
              :placeholder="t('responseCache.inputModelPlaceholder')"
              :disabled="!canWrite"
              style="width: 300px"
            />
          </div>
          <ConfirmAction
            :title="modelConfirmTitle"
            :confirm-text="t('responseCache.invalidateByModel')"
            :cancel-text="t('responseCache.cancel')"
            type="warning"
            :disabled="!canWrite || !modelName.trim() || !status?.enabled"
            :loading="invalidating"
            @confirm="handleInvalidateByModel"
          >
            <el-button
              v-permission="'config:cache:write'"
              type="warning"
              plain
              :icon="Delete"
              :disabled="!modelName.trim() || !status?.enabled"
              :loading="invalidating"
            >
              {{ t('responseCache.invalidateByModel') }}
            </el-button>
          </ConfirmAction>
        </div>

        <el-divider />

        <div class="invalidate-row danger-zone">
          <div class="invalidate-row__text">
            <span class="action-label action-label--danger">{{ t('responseCache.clearAllCache') }}</span>
            <p class="invalidate-row__hint">{{ t('responseCache.clearAllCacheHint') }}</p>
          </div>
          <ConfirmAction
            :title="t('responseCache.clearAllConfirmTitle')"
            :confirm-text="t('responseCache.confirm')"
            :cancel-text="t('responseCache.cancel')"
            type="danger"
            :disabled="!canWrite || !status?.enabled"
            :loading="invalidating"
            @confirm="handleInvalidateAll"
          >
            <el-button
              v-permission="'config:cache:write'"
              type="danger"
              plain
              :icon="Delete"
              :disabled="!status?.enabled"
              :loading="invalidating"
            >
              {{ t('responseCache.clearAllCache') }}
            </el-button>
          </ConfirmAction>
        </div>
      </ConfigSectionCard>

      <!-- 语义缓存：准入参数改了要重启，只读 -->
      <ConfigSectionCard :title="t('responseCache.semanticTitle')">
        <template #actions>
          <el-tag size="small" type="info" effect="plain">
            {{ semanticStatus?.enabled ? t('responseCache.enabled') : t('responseCache.disabled') }}
          </el-tag>
        </template>

        <el-alert
          v-if="semanticStatus"
          :title="t('responseCache.semanticHint')"
          type="info"
          :closable="false"
          show-icon
        />
        <el-descriptions v-if="semanticStatus" :column="3" border class="semantic-grid">
          <el-descriptions-item :label="t('responseCache.semanticEnabled')">
            {{ semanticStatus.enabled ? t('responseCache.enabled') : t('responseCache.disabled') }}
          </el-descriptions-item>
          <el-descriptions-item :label="t('responseCache.semanticTenantScope')">
            {{ semanticStatus.tenantScopeEnabled ? t('responseCache.enabled') : t('responseCache.disabled') }}
          </el-descriptions-item>
          <el-descriptions-item :label="t('responseCache.semanticPublicScope')">
            {{ semanticStatus.publicScopeEnabled ? t('responseCache.enabled') : t('responseCache.disabled') }}
          </el-descriptions-item>
          <el-descriptions-item :label="t('responseCache.semanticEntries')">
            {{ semanticStatus.size }} / {{ semanticStatus.globalMaxEntries }}
          </el-descriptions-item>
          <el-descriptions-item :label="t('responseCache.semanticTtl')">
            {{ semanticStatus.ttlSeconds }}
          </el-descriptions-item>
          <el-descriptions-item :label="t('responseCache.semanticMaxChars')">
            {{ semanticStatus.maxPublicChars }}
          </el-descriptions-item>
          <el-descriptions-item :label="t('responseCache.semanticDistance')">
            {{ semanticStatus.maxHammingDistance }}
          </el-descriptions-item>
        </el-descriptions>
      </ConfigSectionCard>

      <el-alert
        :title="t('responseCache.tipTitle')"
        type="info"
        :closable="false"
        show-icon
        class="cache-tip"
      >
        <p class="tip-text">
          {{ t('responseCache.tipBefore') }}
          <code>jairouter.response-cache.*</code>
          {{ t('responseCache.tipAfter') }}
        </p>
      </el-alert>
    </template>
  </ConfigPageShell>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { Delete } from '@element-plus/icons-vue'
import ConfigPageShell from '@/components/config/ConfigPageShell.vue'
import ConfigSectionCard from '@/components/config/ConfigSectionCard.vue'
import SettingsSwitch from '@/components/config/SettingsSwitch.vue'
import ConfirmAction from '@/components/ConfirmAction.vue'
import MetricStrip, { type MetricStripItem } from '@/components/MetricStrip.vue'
import { usePermission } from '@/composables/usePermission'
import { useConfigDraft } from '@/composables/useConfigDraft'
import {
  getCacheStatus,
  getSemanticCacheStatus,
  invalidateCache,
  updateCacheConfig,
  type CacheConfigPayload,
  type CacheStatus,
  type SemanticCacheStatus,
} from '@/api/responseCache'

const { t } = useI18n()
const { hasPermission } = usePermission()

const canWrite = computed(() => hasPermission('config:cache:write'))

const loading = ref(false)
const invalidating = ref(false)
const status = ref<CacheStatus | null>(null)
const semanticStatus = ref<SemanticCacheStatus | null>(null)
const selectedServiceType = ref('')
const modelName = ref('')

/** 即时开关失败信息（草稿错误由 useConfigDraft 写在 errors.runtime 上） */
const switchError = ref('')
/** 失效操作失败信息 */
const invalidateError = ref('')

const serviceTypes = ['chat', 'embedding', 'rerank', 'tts', 'stt', 'imgGen', 'imgEdit']

interface RuntimeForm {
  enabled: boolean
  skipStreaming: boolean
  onlyDeterministic: boolean
  ttlSeconds: number
}

const form = reactive<RuntimeForm>({
  enabled: false,
  skipStreaming: true,
  onlyDeterministic: true,
  ttlSeconds: 300,
})

const baseline = reactive<RuntimeForm>({ ...form })

/**
 * 用后端快照刷新页面。
 *
 * `keepTtlDraft`：失效操作后只刷新统计与开关，保留用户尚未保存的 TTL 草稿。
 */
const applyStatus = (snapshot: CacheStatus, keepTtlDraft = false): void => {
  status.value = snapshot
  form.enabled = snapshot.enabled
  form.skipStreaming = snapshot.skipStreaming
  form.onlyDeterministic = snapshot.onlyDeterministic
  baseline.enabled = snapshot.enabled
  baseline.skipStreaming = snapshot.skipStreaming
  baseline.onlyDeterministic = snapshot.onlyDeterministic
  if (!keepTtlDraft) {
    form.ttlSeconds = snapshot.ttlSeconds
    baseline.ttlSeconds = snapshot.ttlSeconds
  }
}

/** 取后端 400 原文，AXIOS 的默认 message 只有状态码 */
const apiMessageOf = (error: unknown, fallback: string): string => {
  const candidate = error as { response?: { data?: { message?: string } }; message?: string }
  return candidate?.response?.data?.message || candidate?.message || fallback
}

// ── 指标条 ──

const hitRatioDisplay = computed(() => {
  const ratio = status.value?.hitRatio
  if (ratio == null) return '—'
  return `${(ratio * 100).toFixed(1)}%`
})

const metricItems = computed<MetricStripItem[]>(() => {
  const snapshot = status.value
  return [
    { key: 'hitRatio', label: t('responseCache.hitRateLabel'), value: hitRatioDisplay.value },
    { key: 'hits', label: t('responseCache.hitsLabel'), value: `${snapshot?.hits ?? 0}` },
    {
      key: 'entries',
      label: t('responseCache.currentEntriesLabel'),
      value: `${snapshot?.size ?? '—'} / ${snapshot?.maxSize ?? '—'}`,
    },
    {
      key: 'ttl',
      label: t('responseCache.ttlLabel'),
      value: `${snapshot?.ttlSeconds ?? '—'}`,
    },
  ]
})

// ── 即时生效的开关 ──

const submitSwitch = async (payload: CacheConfigPayload): Promise<void> => {
  switchError.value = ''
  try {
    const updated = await updateCacheConfig(payload)
    applyStatus(updated, true)
    ElMessage.success(t('responseCache.configUpdatedSuccess'))
  } catch (error) {
    const message = apiMessageOf(error, t('responseCache.saveFailedError'))
    switchError.value = message
    throw new Error(message)
  }
}

const submitEnabled = (next: boolean): Promise<void> => submitSwitch({ enabled: next })
const submitSkipStreaming = (next: boolean): Promise<void> => submitSwitch({ skipStreaming: next })
const submitOnlyDeterministic = (next: boolean): Promise<void> =>
  submitSwitch({ onlyDeterministic: next })

// ── 草稿保存（TTL） ──

const { saving, savingLabel, errors, dirtyCount, save, clearError } = useConfigDraft(
  [
    {
      key: 'runtime',
      label: () => t('responseCache.runtimeConfigTitle'),
      isDirty: () => form.ttlSeconds !== baseline.ttlSeconds,
      validate: () => {
        const ttl = Number(form.ttlSeconds)
        if (!Number.isFinite(ttl) || ttl < 1) {
          return t('responseCache.ttlInvalid')
        }
        return null
      },
      save: async () => {
        const payload: CacheConfigPayload = {}
        if (form.ttlSeconds !== baseline.ttlSeconds) {
          payload.ttlSeconds = form.ttlSeconds
        }
        try {
          const updated = await updateCacheConfig(payload)
          applyStatus(updated, true)
        } catch (error) {
          throw new Error(apiMessageOf(error, t('responseCache.saveFailedError')))
        }
      },
      commit: () => {
        baseline.ttlSeconds = form.ttlSeconds
      },
    },
  ],
  { fallbackError: () => t('responseCache.saveFailedError') }
)

// ── 数据加载 ──

const loadStatus = async (): Promise<void> => {
  const snapshot = await getCacheStatus()
  if (snapshot) {
    applyStatus(snapshot, true)
  }
}

const loadAll = async (): Promise<void> => {
  loading.value = true
  switchError.value = ''
  invalidateError.value = ''
  try {
    const semantic = await getSemanticCacheStatus().catch(() => null)
    semanticStatus.value = semantic
    const snapshot = await getCacheStatus()
    if (snapshot) {
      applyStatus(snapshot)
    } else {
      status.value = null
    }
  } finally {
    loading.value = false
  }
}

const handleSave = async (): Promise<void> => {
  if (!status.value) return
  const result = await save()
  if (!result.ok) {
    ElMessage.error(result.failed?.message ?? t('responseCache.saveFailedError'))
    return
  }
  if (result.saved.length === 0) {
    ElMessage.info(t('responseCache.noChangesWarning'))
    return
  }
  ElMessage.success(t('responseCache.configUpdatedSuccess'))
}

/** 「放弃修改」只回退 TTL：三个开关是即时生效的，没有可丢弃的开关状态 */
const handleDiscard = (): void => {
  form.ttlSeconds = baseline.ttlSeconds
  switchError.value = ''
  invalidateError.value = ''
  clearError()
}

// ── 缓存失效（危险操作） ──

const afterInvalidate = async (executed: boolean, successMessage: string): Promise<void> => {
  if (!executed) {
    ElMessage.warning(t('responseCache.cacheNotEnabledWarning'))
  } else {
    ElMessage.success(successMessage)
  }
  await loadStatus()
}

const handleInvalidateAll = async (): Promise<void> => {
  invalidating.value = true
  invalidateError.value = ''
  try {
    const result = await invalidateCache()
    await afterInvalidate(result.executed, t('responseCache.cacheClearedSuccess'))
  } catch (error) {
    const message = apiMessageOf(error, t('responseCache.operationFailedError'))
    invalidateError.value = message
    ElMessage.error(message)
  } finally {
    invalidating.value = false
  }
}

const handleInvalidateByServiceType = async (): Promise<void> => {
  if (!selectedServiceType.value) return
  invalidating.value = true
  invalidateError.value = ''
  try {
    const serviceType = selectedServiceType.value
    const result = await invalidateCache({ serviceType })
    await afterInvalidate(
      result.executed,
      t('responseCache.invalidatedByServiceType', { serviceType })
    )
  } catch (error) {
    const message = apiMessageOf(error, t('responseCache.operationFailedError'))
    invalidateError.value = message
    ElMessage.error(message)
  } finally {
    invalidating.value = false
  }
}

const handleInvalidateByModel = async (): Promise<void> => {
  const model = modelName.value.trim()
  if (!model) return
  invalidating.value = true
  invalidateError.value = ''
  try {
    const result = await invalidateCache({ model })
    await afterInvalidate(result.executed, t('responseCache.invalidatedByModel', { model }))
  } catch (error) {
    const message = apiMessageOf(error, t('responseCache.operationFailedError'))
    invalidateError.value = message
    ElMessage.error(message)
  } finally {
    invalidating.value = false
  }
}

// ── 二次确认文案 ──

const serviceTypeConfirmTitle = computed(() =>
  t('responseCache.invalidateByServiceConfirmMessage', { serviceType: selectedServiceType.value })
)

const modelConfirmTitle = computed(() =>
  t('responseCache.invalidateByModelConfirmMessage', { model: modelName.value.trim() })
)

onMounted(loadAll)
</script>

<style scoped>
.summary-strip {
  margin-bottom: 16px;
}

.runtime-config-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
  gap: 16px;
}

.config-switch-item {
  display: flex;
  align-items: center;
  gap: 12px;
}

.config-switch-label {
  min-width: 140px;
  color: var(--el-text-color-regular);
  font-size: 13px;
}

.unit {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.config-hint {
  margin: 12px 0 0;
  color: var(--el-text-color-secondary);
  font-size: 12px;
  line-height: 1.6;
}

.config-hint code {
  padding: 0 4px;
  border-radius: 3px;
  background: var(--el-fill-color-light);
  font-family: var(--el-font-family-mono, monospace);
}

.invalidate-row {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
}

.invalidate-row__text {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.invalidate-row__hint {
  margin: 4px 0 0;
  color: var(--el-text-color-secondary);
  font-size: 12px;
  line-height: 1.6;
}

.action-label {
  color: var(--el-text-color-regular);
  font-size: 13px;
}

.action-label--danger {
  color: var(--el-color-danger);
}

.danger-zone {
  padding: 12px;
  border: 1px solid var(--el-color-danger-light-5);
  border-radius: var(--el-border-radius-base);
  background: var(--el-color-danger-light-9);
}

.danger-zone .invalidate-row__text {
  display: block;
}

.semantic-grid {
  margin-top: 12px;
}

.cache-tip {
  margin-top: 16px;
}

.tip-text {
  margin: 0;
  font-size: 12px;
  line-height: 1.6;
}

.tip-text code {
  padding: 0 4px;
  border-radius: 3px;
  background: var(--el-fill-color-light);
  font-family: var(--el-font-family-mono, monospace);
}
</style>
