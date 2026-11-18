<template>
  <ConfigPageShell
    :title="t('quotaConfig.pageTitle')"
    :loading="loading"
    :saving="saving"
    :saving-label="savingLabel"
    :dirty-count="dirtyCount"
    :can-write="canWrite"
    save-permission="config:quota:write"
    @refresh="loadConfig"
    @save="handleSave"
    @discard="handleDiscard"
  >
    <!-- 顶部状态指标条（P2-1：四张彩卡压成一行中性指标，语义色只落在「降级放行」为真时） -->

    <!-- 页头提示：区分「即时生效」与「需重启」两类配置 -->
    <template #toolbar>
      <p class="restart-hint">
        <el-icon><InfoFilled /></el-icon>
        <span>{{ t('quotaConfig.restartHint') }}</span>
      </p>
    </template>

    <!-- 未就绪提示 -->
    <el-alert
      v-if="!config"
      :title="t('quotaConfig.pageTitle')"
      :description="t('quotaConfig.refreshStatus')"
      type="warning"
      :closable="false"
      show-icon
      style="margin-bottom: 16px"
    />

    <template v-else>
      <!-- 热编辑配置：两个布尔开关即时生效，窗口多选走动作条草稿 -->
      <ConfigSectionCard
        :title="t('quotaConfig.runtimeConfigTitle')"
        :description="t('quotaConfig.runtimeConfigHint')"
        :error="errors.windows || switchError"
      >
        <div class="runtime-config-grid">
          <div class="config-switch-item">
            <span class="config-switch-label">{{ t('quotaConfig.enableQuotaLabel') }}</span>
            <SettingsSwitch
              :model-value="form.enabled"
              :disabled="!canWrite"
              :immediate-hint="t('quotaConfig.immediateHint')"
              :submit="submitEnabled"
              @update:model-value="form.enabled = $event"
            />
          </div>
          <div class="config-switch-item">
            <span class="config-switch-label">{{ t('quotaConfig.failOpenSwitchLabel') }}</span>
            <SettingsSwitch
              :model-value="form.failOpen"
              :disabled="!canWrite"
              :immediate-hint="t('quotaConfig.immediateHint')"
              :submit="submitFailOpen"
              @update:model-value="form.failOpen = $event"
            />
          </div>
          <div class="config-switch-item">
            <span class="config-switch-label">{{ t('quotaConfig.windowsLabel') }}</span>
            <el-select
              v-model="form.windows"
              multiple
              :placeholder="t('quotaConfig.windowsPlaceholder')"
              :disabled="!canWrite"
              style="width: 220px"
            >
              <el-option v-for="w in windowOptions" :key="w" :label="w" :value="w" />
            </el-select>
          </div>
        </div>
      </ConfigSectionCard>

      <!-- 只读区：字段在应用启动时确定，改动需改 yaml 后重启 -->
      <ConfigSectionCard
        :title="t('quotaConfig.restartRequiredTitle')"
        :restart-required="readonlyNeedsRestart"
      >
        <template #actions>
          <el-tag size="small" type="info" effect="plain">
            {{ t('quotaConfig.readonlyCount', { count: readonlyFields.length }) }}
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
import { getQuotaConfig, updateQuotaConfig, type QuotaConfig } from '@/api/quota'

const { t } = useI18n()
const { hasPermission } = usePermission()

// 审计 #468：本页路由只要求 `config:quota:read`（只读角色也能进），
// 但写操作是 `PUT /api/config/quota`，后端要求 `config:quota:write`。
// 不做页内门控的话，只读角色会看到「点了必然 403」的开关与保存入口。
const canWrite = computed(() => hasPermission('config:quota:write'))

const loading = ref(false)
const config = ref<QuotaConfig | null>(null)
/** 即时开关提交失败的分区级错误（草稿错误由 useConfigDraft 写在 errors 上） */
const switchError = ref('')

const windowOptions = ['MINUTE', 'HOUR', 'DAY', 'MONTH']

// ── 草稿表单与基线 ──

interface RuntimeForm {
  enabled: boolean
  failOpen: boolean
  windows: string[]
}

const form = reactive<RuntimeForm>({ enabled: false, failOpen: false, windows: [] })
const baseline = reactive<RuntimeForm>({ enabled: false, failOpen: false, windows: [] })

const sameWindows = (a: string[], b: string[]): boolean =>
  a.length === b.length && a.every(window => b.includes(window))

/**
 * 用后端快照刷新页面状态。
 *
 * `keepWindowsDraft`：即时开关提交成功后只同步开关字段，别把用户还没保存的
 * 窗口草稿抹掉 —— 否则拨一下开关就等于丢弃草稿。
 */
const applySnapshot = (snapshot: QuotaConfig, keepWindowsDraft = false): void => {
  config.value = snapshot
  form.enabled = snapshot.enabled
  form.failOpen = snapshot.failOpen
  baseline.enabled = snapshot.enabled
  baseline.failOpen = snapshot.failOpen
  if (!keepWindowsDraft) {
    form.windows = [...snapshot.windows]
    baseline.windows = [...snapshot.windows]
  }
}

/** 取后端的 400 原文（AxiosError 的 message 只有 "Request failed with status code"） */
const apiMessageOf = (error: unknown, fallback: string): string => {
  const candidate = error as { response?: { data?: { message?: string } }; message?: string }
  return candidate?.response?.data?.message || candidate?.message || fallback
}

// ── 即时生效的开关 ──

const submitSwitch = async (patch: { enabled?: boolean; failOpen?: boolean }): Promise<void> => {
  switchError.value = ''
  try {
    const updated = await updateQuotaConfig(patch)
    applySnapshot(updated, true)
    ElMessage.success(t('quotaConfig.configUpdatedSuccess'))
  } catch (error) {
    // 失败：写进分区错误（红框 + 文案），并抛给 SettingsSwitch 让开关回弹
    const message = apiMessageOf(error, t('quotaConfig.saveFailedError'))
    switchError.value = message
    throw new Error(message)
  }
}

const submitEnabled = (next: boolean): Promise<void> => submitSwitch({ enabled: next })

const submitFailOpen = (next: boolean): Promise<void> => submitSwitch({ failOpen: next })

// ── 只读区（需重启生效） ──

interface ReadonlyField {
  /** 对应后端 restartRequiredFields 中的字段路径 */
  path: string
  label: string
  value: string
}

/** 只读项集中声明，模板只负责渲染，避免十段重复标记 */
const readonlyFields = computed<ReadonlyField[]>(() => {
  const c = config.value
  if (!c) return []
  return [
    { path: 'backendName', label: t('quotaConfig.backendNameLabel'), value: c.backendName },
    {
      path: 'distributed.enabled',
      label: t('quotaConfig.distributedEnabledLabel'),
      value: c.distributed.enabled ? '✓' : '✗',
    },
    {
      path: 'distributed.keyPrefix',
      label: t('quotaConfig.distributedKeyPrefixLabel'),
      value: c.distributed.keyPrefix,
    },
    {
      path: 'distributed.timeoutMs',
      label: t('quotaConfig.distributedTimeoutLabel'),
      value: `${c.distributed.timeoutMs} ${t('quotaConfig.distributedTimeoutUnit')}`,
    },
    {
      path: 'distributed.degradeToLocal',
      label: t('quotaConfig.distributedDegradeLabel'),
      value: c.distributed.degradeToLocal ? '✓' : '✗',
    },
    { path: 'retention.minute', label: t('quotaConfig.retentionMinuteLabel'), value: c.retention.minute },
    { path: 'retention.hour', label: t('quotaConfig.retentionHourLabel'), value: c.retention.hour },
    { path: 'retention.day', label: t('quotaConfig.retentionDayLabel'), value: c.retention.day },
    { path: 'retention.month', label: t('quotaConfig.retentionMonthLabel'), value: c.retention.month },
    {
      path: 'flushIntervalSeconds',
      label: t('quotaConfig.flushIntervalSecondsLabel'),
      value: `${c.flushIntervalSeconds} ${t('quotaConfig.flushIntervalUnit')}`,
    },
  ]
})

/** 判断字段是否在重启生效列表中（支持后端下发的 `a.b.*` 通配） */
const isRestartRequired = (fieldPath: string): boolean => {
  if (!config.value) return false
  return config.value.restartRequiredFields.some(pattern => {
    if (pattern.endsWith('.*')) {
      const prefix = pattern.slice(0, -2)
      return fieldPath === prefix || fieldPath.startsWith(`${prefix}.`)
    }
    return pattern === fieldPath
  })
}

/** 整个只读区都需重启：用卡片级标签统一表达，不再逐项挂 tag */
const readonlyNeedsRestart = computed(() => readonlyFields.value.some(field => isRestartRequired(field.path)))

// ── 草稿保存（窗口多选） ──

const { saving, savingLabel, errors, dirtyCount, save, clearError } = useConfigDraft(
  [
    {
      key: 'windows',
      label: () => t('quotaConfig.windowsLabel'),
      isDirty: () => !sameWindows(form.windows, baseline.windows),
      save: async () => {
        await updateQuotaConfig({ windows: [...form.windows] })
      },
      commit: () => {
        baseline.windows = [...form.windows]
      },
    },
  ],
  { fallbackError: () => t('quotaConfig.saveFailedError') }
)

// ── 数据加载 ──

const loadConfig = async (): Promise<void> => {
  loading.value = true
  switchError.value = ''
  try {
    const snapshot = await getQuotaConfig()
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
    ElMessage.error(result.failed?.message ?? t('quotaConfig.saveFailedError'))
    return
  }
  if (result.saved.length === 0) {
    ElMessage.info(t('quotaConfig.noChangesWarning'))
    return
  }
  ElMessage.success(t('quotaConfig.configUpdatedSuccess'))
  await loadConfig()
}

/** 「放弃修改」只回退草稿字段：开关是即时生效的，不存在可丢弃的开关状态 */
const handleDiscard = (): void => {
  form.windows = [...baseline.windows]
  switchError.value = ''
  clearError()
}

onMounted(loadConfig)
</script>

<style scoped>
.summary-strip {
  margin-bottom: 16px;
}

.runtime-config-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 16px 32px;
}

.config-switch-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 8px 12px;
  border-radius: var(--ja-radius-md, 8px);
  background: var(--ja-bg-hover, var(--el-fill-color-light));
}

.config-switch-label {
  font-size: 14px;
  color: var(--ja-text-primary);
  font-weight: 500;
}

.readonly-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 0 32px;
}

/* 只读区降对比：无填充块、仅用细分隔线，与可编辑网格区分权重 */
.readonly-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 4px;
  border-bottom: 1px solid var(--ja-border, var(--el-border-color-lighter));
}

.readonly-label {
  font-size: 13px;
  color: var(--ja-ink-3, var(--el-text-color-secondary));
  display: flex;
  align-items: center;
  gap: 6px;
}

.readonly-value {
  font-size: 13px;
  color: var(--ja-ink-2, var(--el-text-color-regular));
  text-align: right;
  word-break: break-all;
}

.restart-hint {
  display: flex;
  align-items: center;
  gap: 6px;
  margin: 0;
  font-size: 12px;
  color: var(--ja-ink-2, var(--el-text-color-secondary));
}

@media (max-width: 767px) {
  .runtime-config-grid,
  .readonly-grid {
    grid-template-columns: 1fr;
  }
}
</style>
