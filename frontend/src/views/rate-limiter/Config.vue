<template>
  <ConfigPageShell
    :title="t('ratelimitGlobal.pageTitle')"
    :loading="loading"
    :saving="saving"
    :saving-label="savingLabel"
    :dirty-count="dirtyCount"
    :can-write="canWrite"
    save-permission="config:ratelimit:write"
    @refresh="loadConfig"
    @save="handleSave"
    @discard="handleDiscard"
  >
    <template #toolbar>
      <p class="restart-hint">
        <el-icon><InfoFilled /></el-icon>
        <span>{{ t('ratelimitGlobal.restartHint') }}</span>
      </p>
    </template>

    <el-alert
      v-if="!config"
      :title="t('ratelimitGlobal.pageTitle')"
      :description="t('ratelimitGlobal.loadFailed')"
      type="warning"
      :closable="false"
      show-icon
      style="margin-bottom: 16px"
    />

    <template v-else>
      <!-- L1：API Key 级每分钟限流（开关即时生效） -->
      <ConfigSectionCard
        :title="t('ratelimitGlobal.apiKeyTitle')"
        :description="t('ratelimitGlobal.apiKeyDesc')"
        :error="switchErrors.apiKey"
      >
        <template #actions>
          <el-tag size="small" type="info" effect="plain">
            {{ t('ratelimitGlobal.apiKeyBackend', { backend: config.distributed.apiKeyBackend }) }}
          </el-tag>
        </template>

        <div class="config-switch-item">
          <span class="config-switch-label">{{ t('ratelimitGlobal.enableLabel') }}</span>
          <SettingsSwitch
            :model-value="form.apiKeyEnabled"
            :disabled="!canWrite"
            :immediate-hint="t('ratelimitGlobal.immediateHint')"
            :submit="submitApiKeyEnabled"
            @update:model-value="form.apiKeyEnabled = $event"
          />
        </div>

        <p v-if="!form.apiKeyEnabled" class="mode-hint">
          {{ t('ratelimitGlobal.apiKeyDisabledHint') }}
        </p>
      </ConfigSectionCard>

      <!-- L2：终端用户级限流（两个开关即时生效，阈值与缺失策略走动作条草稿） -->
      <ConfigSectionCard
        :title="t('ratelimitGlobal.endUserTitle')"
        :description="t('ratelimitGlobal.endUserDesc')"
        :error="errors.endUser || switchErrors.endUser"
      >
        <template #actions>
          <el-tag size="small" type="info" effect="plain">
            {{ t('ratelimitGlobal.endUserBackend', { backend: config.distributed.endUserBackend }) }}
          </el-tag>
        </template>

        <div class="runtime-config-grid">
          <div class="config-switch-item">
            <span class="config-switch-label">{{ t('ratelimitGlobal.enableLabel') }}</span>
            <SettingsSwitch
              :model-value="form.endUserEnabled"
              :disabled="!canWrite"
              :immediate-hint="t('ratelimitGlobal.immediateHint')"
              :submit="submitEndUserEnabled"
              @update:model-value="form.endUserEnabled = $event"
            />
          </div>
          <div class="config-switch-item">
            <span class="config-switch-label">{{ t('ratelimitGlobal.thresholdLabel') }}</span>
            <el-input-number
              v-model="form.endUserRequestsPerMinute"
              :min="1"
              :max="1000000"
              :step="10"
              :disabled="!canWrite || !form.endUserEnabled"
              controls-position="right"
              style="width: 180px"
            />
            <span class="unit">{{ t('ratelimitGlobal.thresholdUnit') }}</span>
          </div>
          <div class="config-switch-item">
            <span class="config-switch-label">{{ t('ratelimitGlobal.headerEnabledLabel') }}</span>
            <SettingsSwitch
              :model-value="form.endUserHeaderEnabled"
              :disabled="!canWrite || !form.endUserEnabled"
              :submit="submitEndUserHeaderEnabled"
              @update:model-value="form.endUserHeaderEnabled = $event"
            />
          </div>
          <div class="config-switch-item">
            <span class="config-switch-label">{{ t('ratelimitGlobal.missingIdentityLabel') }}</span>
            <el-select
              v-model="form.endUserMissingIdentity"
              :disabled="!canWrite || !form.endUserEnabled"
              style="width: 200px"
            >
              <el-option value="SKIP" :label="t('ratelimitGlobal.missingIdentitySkip')" />
              <el-option value="REJECT" :label="t('ratelimitGlobal.missingIdentityReject')" />
            </el-select>
          </div>
        </div>

        <p class="card-desc hint">{{ t('ratelimitGlobal.headerEnabledHint') }}</p>

        <p v-if="!form.endUserEnabled" class="mode-hint">
          {{ t('ratelimitGlobal.endUserDisabledHint') }}
        </p>
      </ConfigSectionCard>

      <!-- 装配期决定、需重启才能改的字段 -->
      <ConfigSectionCard
        :title="t('ratelimitGlobal.readonlyTitle')"
        :restart-required="readonlyNeedsRestart"
      >
        <template #actions>
          <el-tag size="small" type="info" effect="plain">
            {{ t('ratelimitGlobal.readonlyCount', { count: readonlyFields.length }) }}
          </el-tag>
        </template>
        <div class="readonly-grid">
          <div v-for="field in readonlyFields" :key="field.path" class="readonly-item">
            <span class="readonly-label">{{ field.label }}</span>
            <span class="readonly-value">{{ field.value }}</span>
          </div>
        </div>
      </ConfigSectionCard>
    </template>
  </ConfigPageShell>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { InfoFilled } from '@element-plus/icons-vue'
import ConfigPageShell from '@/components/config/ConfigPageShell.vue'
import ConfigSectionCard from '@/components/config/ConfigSectionCard.vue'
import SettingsSwitch from '@/components/config/SettingsSwitch.vue'
import { usePermission } from '@/composables/usePermission'
import { useConfigDraft } from '@/composables/useConfigDraft'
import {
  getRatelimitConfig,
  updateRatelimitConfig,
  type RatelimitConfig,
  type RatelimitConfigPayload,
} from '@/api/ratelimitConfig'

const { t } = useI18n()
const { hasPermission } = usePermission()

// 路由只要求 `config:ratelimit:read`（只读角色也能进），写接口要 `config:ratelimit:write`
const canWrite = computed(() => hasPermission('config:ratelimit:write'))

const loading = ref(false)
const config = ref<RatelimitConfig | null>(null)

/** 即时开关提交失败的分区级错误（草稿错误由 useConfigDraft 写在 errors 上） */
const switchErrors = reactive<{ apiKey: string; endUser: string }>({ apiKey: '', endUser: '' })

interface RatelimitForm {
  apiKeyEnabled: boolean
  endUserEnabled: boolean
  endUserRequestsPerMinute: number
  endUserHeaderEnabled: boolean
  endUserMissingIdentity: 'SKIP' | 'REJECT'
}

const form = reactive<RatelimitForm>({
  apiKeyEnabled: true,
  endUserEnabled: false,
  endUserRequestsPerMinute: 60,
  endUserHeaderEnabled: true,
  endUserMissingIdentity: 'SKIP',
})

const baseline = reactive<RatelimitForm>({ ...form })

/** 从快照生成表单值（与后端字段一一对应） */
const formOf = (source: RatelimitConfig): RatelimitForm => ({
  apiKeyEnabled: source.apiKey.enabled,
  endUserEnabled: source.endUser.enabled,
  endUserRequestsPerMinute: source.endUser.requestsPerMinute,
  endUserHeaderEnabled: source.endUser.headerEnabled,
  endUserMissingIdentity: source.endUser.missingIdentity,
})

/**
 * 用后端快照刷新页面。
 *
 * `keepDraft`：即时开关提交成功后只同步开关字段，保留用户尚未保存的阈值/策略草稿；
 * `false` 时（首次加载、刷新）整体覆盖。
 */
const applySnapshot = (snapshot: RatelimitConfig, keepDraft = false): void => {
  config.value = snapshot
  const next = formOf(snapshot)
  form.apiKeyEnabled = next.apiKeyEnabled
  form.endUserEnabled = next.endUserEnabled
  form.endUserHeaderEnabled = next.endUserHeaderEnabled
  baseline.apiKeyEnabled = next.apiKeyEnabled
  baseline.endUserEnabled = next.endUserEnabled
  baseline.endUserHeaderEnabled = next.endUserHeaderEnabled
  if (!keepDraft) {
    form.endUserRequestsPerMinute = next.endUserRequestsPerMinute
    form.endUserMissingIdentity = next.endUserMissingIdentity
    baseline.endUserRequestsPerMinute = next.endUserRequestsPerMinute
    baseline.endUserMissingIdentity = next.endUserMissingIdentity
  }
}

/** 取后端的 400 原文（AxiosError 的 message 只有 "Request failed with status code"） */
const apiMessageOf = (error: unknown, fallback: string): string => {
  const candidate = error as { response?: { data?: { message?: string } }; message?: string }
  return candidate?.response?.data?.message || candidate?.message || fallback
}

// ── 即时生效的开关 ──

const submitSwitch = async (
  patch: RatelimitConfigPayload,
  field: 'apiKey' | 'endUser'
): Promise<void> => {
  switchErrors[field] = ''
  try {
    const updated = await updateRatelimitConfig(patch)
    applySnapshot(updated, true)
    ElMessage.success(t('ratelimitGlobal.saved'))
  } catch (error) {
    // 失败：写进分区错误（红框 + 文案），并抛给 SettingsSwitch 让开关回弹
    const message = apiMessageOf(error, t('ratelimitGlobal.saveFailed'))
    switchErrors[field] = message
    throw new Error(message)
  }
}

const submitApiKeyEnabled = (next: boolean): Promise<void> =>
  submitSwitch({ apiKeyEnabled: next }, 'apiKey')

const submitEndUserEnabled = (next: boolean): Promise<void> =>
  submitSwitch({ endUserEnabled: next }, 'endUser')

const submitEndUserHeaderEnabled = (next: boolean): Promise<void> =>
  submitSwitch({ endUserHeaderEnabled: next }, 'endUser')

// ── 只读字段（装配期决定） ──

const readonlyFields = computed(() => {
  const c = config.value
  if (!c) return []
  const d = c.distributed
  return [
    {
      path: 'distributed.enabled',
      label: t('ratelimitGlobal.distributedLabel'),
      value: d.enabled ? t('ratelimitGlobal.on') : t('ratelimitGlobal.off'),
    },
    { path: 'distributed.timeoutMs', label: t('ratelimitGlobal.timeoutLabel'), value: `${d.timeoutMs}` },
    {
      path: 'apiKey.localCacheSize',
      label: t('ratelimitGlobal.apiKeyCacheLabel'),
      value: `${c.apiKey.localCacheSize}`,
    },
    {
      path: 'endUser.localCacheSize',
      label: t('ratelimitGlobal.endUserCacheLabel'),
      value: `${c.endUser.localCacheSize}`,
    },
    {
      path: 'endUser.fingerprintLength',
      label: t('ratelimitGlobal.fingerprintLabel'),
      value: `${c.endUser.fingerprintLength}`,
    },
  ]
})

/** 该字段是否属于「需重启」一档 */
const isRestartRequired = (path: string): boolean =>
  config.value?.restartRequiredFields.includes(path) ?? false

/** 只读区只要有一项需重启就整体标注（这些字段都在启动时装配） */
const readonlyNeedsRestart = computed(() => readonlyFields.value.some(field => isRestartRequired(field.path)))

// ── 草稿保存（阈值与缺失身份策略） ──

const { saving, savingLabel, errors, dirtyCount, save, clearError } = useConfigDraft(
  [
    {
      key: 'endUser',
      label: () => t('ratelimitGlobal.endUserTitle'),
      isDirty: () =>
        form.endUserRequestsPerMinute !== baseline.endUserRequestsPerMinute ||
        form.endUserMissingIdentity !== baseline.endUserMissingIdentity,
      validate: () => {
        const threshold = Number(form.endUserRequestsPerMinute)
        if (!Number.isFinite(threshold) || threshold < 1) {
          return t('ratelimitGlobal.thresholdInvalid')
        }
        return null
      },
      save: async () => {
        const payload: RatelimitConfigPayload = {}
        if (form.endUserRequestsPerMinute !== baseline.endUserRequestsPerMinute) {
          payload.endUserRequestsPerMinute = form.endUserRequestsPerMinute
        }
        if (form.endUserMissingIdentity !== baseline.endUserMissingIdentity) {
          payload.endUserMissingIdentity = form.endUserMissingIdentity
        }
        try {
          const updated = await updateRatelimitConfig(payload)
          applySnapshot(updated, true)
        } catch (error) {
          throw new Error(apiMessageOf(error, t('ratelimitGlobal.saveFailed')))
        }
      },
      commit: () => {
        baseline.endUserRequestsPerMinute = form.endUserRequestsPerMinute
        baseline.endUserMissingIdentity = form.endUserMissingIdentity
      },
    },
  ],
  { fallbackError: () => t('ratelimitGlobal.saveFailed') }
)

// ── 数据加载 ──

const loadConfig = async (): Promise<void> => {
  loading.value = true
  switchErrors.apiKey = ''
  switchErrors.endUser = ''
  try {
    const snapshot = await getRatelimitConfig()
    if (snapshot) {
      applySnapshot(snapshot)
    } else {
      config.value = null
    }
  } catch {
    config.value = null
  } finally {
    loading.value = false
  }
}

const handleSave = async (): Promise<void> => {
  if (!config.value) return
  const result = await save()
  if (!result.ok) {
    ElMessage.error(result.failed?.message ?? t('ratelimitGlobal.saveFailed'))
    return
  }
  if (result.saved.length === 0) {
    ElMessage.info(t('configShell.allSaved'))
    return
  }
  ElMessage.success(t('ratelimitGlobal.saved'))
}

/** 「放弃修改」只回退草稿字段：三个开关是即时生效的，没有可丢弃的开关状态 */
const handleDiscard = (): void => {
  form.endUserRequestsPerMinute = baseline.endUserRequestsPerMinute
  form.endUserMissingIdentity = baseline.endUserMissingIdentity
  switchErrors.apiKey = ''
  switchErrors.endUser = ''
  clearError()
}

onMounted(loadConfig)
</script>

<style scoped>
.restart-hint {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 0;
  color: var(--el-text-color-secondary);
  font-size: 13px;
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

.card-desc {
  margin: 0 0 16px;
  color: var(--el-text-color-secondary);
  font-size: 13px;
  line-height: 1.6;
}

.card-desc.hint {
  margin: 12px 0 0;
}

.mode-hint {
  margin: 16px 0 0;
  color: var(--el-color-warning);
  font-size: 12px;
}

.readonly-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(260px, 1fr));
  gap: 12px 24px;
}

.readonly-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 6px 0;
  border-bottom: 1px dashed var(--el-border-color-lighter);
}

.readonly-label {
  display: flex;
  align-items: center;
  gap: 6px;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

.readonly-value {
  color: var(--el-text-color-primary);
  font-size: 13px;
  font-family: var(--el-font-family-mono, monospace);
}
</style>
