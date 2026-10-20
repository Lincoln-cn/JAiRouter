<template>
  <PageSkeleton :title="t('quotaConfig.pageTitle')">
    <template #actions>
      <el-button @click="loadConfig" :loading="loading" size="default">
        <el-icon><Refresh /></el-icon>
        {{ t('quotaConfig.refreshStatus') }}
      </el-button>
    </template>

    <!-- 顶部状态指标条（P2-1：四张彩卡压成一行中性指标，语义色只落在「降级放行」为真时） -->
    <template #stats>
      <MetricStrip :items="summaryItems" :columns="4" />
    </template>

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

    <!-- 热编辑配置 -->
    <el-card v-if="config" shadow="hover" style="margin-bottom: 16px">
      <template #header>
        <span class="card-title">{{ t('quotaConfig.runtimeConfigTitle') }}</span>
      </template>

      <div class="runtime-config-grid">
        <div class="config-switch-item">
          <span class="config-switch-label">{{ t('quotaConfig.enableQuotaLabel') }}</span>
          <el-switch v-model="runtimeForm.enabled" />
        </div>
        <div class="config-switch-item">
          <span class="config-switch-label">{{ t('quotaConfig.failOpenSwitchLabel') }}</span>
          <el-switch v-model="runtimeForm.failOpen" />
        </div>
        <div class="config-switch-item">
          <span class="config-switch-label">{{ t('quotaConfig.windowsLabel') }}</span>
          <el-select
            v-model="runtimeForm.windows"
            multiple
            :placeholder="t('quotaConfig.windowsPlaceholder')"
            style="width: 220px"
          >
            <el-option
              v-for="w in windowOptions"
              :key="w"
              :label="w"
              :value="w"
            />
          </el-select>
        </div>
      </div>

      <div class="config-actions">
        <el-button
          v-if="canWrite"
          :type="configChanged ? 'primary' : 'default'"
          :loading="saving"
          :disabled="!configChanged"
          v-permission="'config:quota:write'" @click="handleSaveConfig"
        >
          <el-icon><Check /></el-icon>
          {{ t('quotaConfig.saveRuntimeConfig') }}
        </el-button>
        <el-button :disabled="saving" @click="resetRuntimeForm">
          {{ t('quotaConfig.reset') }}
        </el-button>
      </div>
    </el-card>

    <!-- 需重启生效（只读区）：折叠 + 降对比，避免与可编辑网格同权 -->
    <el-card v-if="config" shadow="never" class="readonly-card">
      <el-collapse v-model="readonlyOpen">
        <el-collapse-item name="readonly">
          <template #title>
            <span class="card-title">{{ t('quotaConfig.restartRequiredTitle') }}</span>
            <el-tag size="small" type="info" effect="plain" class="readonly-count">
              {{ t('quotaConfig.readonlyCount', { count: readonlyFields.length }) }}
            </el-tag>
          </template>
          <div class="readonly-grid">
            <div v-for="field in readonlyFields" :key="field.path" class="readonly-item">
              <span class="readonly-label">
                {{ field.label }}
                <el-tag v-if="isRestartRequired(field.path)" size="small" type="info" effect="plain">
                  {{ t('quotaConfig.restartRequiredBadge') }}
                </el-tag>
              </span>
              <span class="readonly-value">{{ field.value }}</span>
            </div>
          </div>
        </el-collapse-item>
      </el-collapse>
    </el-card>
  </PageSkeleton>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { Refresh, Check, InfoFilled } from '@element-plus/icons-vue'
import PageSkeleton from '@/components/PageSkeleton.vue'
import MetricStrip, { type MetricStripItem } from '@/components/MetricStrip.vue'
import { useUserStore } from '@/stores/user'
import {
  getQuotaConfig,
  updateQuotaConfig,
  type QuotaConfig,
  type QuotaConfigPayload,
} from '@/api/quota'

const { t } = useI18n()
const userStore = useUserStore()

// 审计 #468：本页路由只要求 `config:quota:read`（只读角色也能进），
// 但「保存」是 `PUT /api/config/quota`，后端要求 `config:quota:write`。
// 不做页内门控的话，只读角色会看到一个「点了必然 403」的按钮（实测：低权限 403 / ADMIN 200）。
const canWrite = computed(() => userStore.hasPermission('config:quota:write'))

const loading = ref(false)
const saving = ref(false)
const config = ref<QuotaConfig | null>(null)

/** 顶部指标条：只呈现后端已返回的运行时状态，不新增统计口径 */
const summaryItems = computed<MetricStripItem[]>(() => {
  const c = config.value
  return [
    {
      key: 'enabled',
      label: t('quotaConfig.enabledLabel'),
      value: c ? (c.enabled ? t('quotaConfig.enabled') : t('quotaConfig.disabled')) : '—',
    },
    {
      key: 'backend',
      label: t('quotaConfig.backendLabel'),
      value: c?.backendName ?? '—',
    },
    {
      key: 'failOpen',
      label: t('quotaConfig.failOpenLabel'),
      value: c ? (c.failOpen ? t('quotaConfig.failOpenEnabled') : t('quotaConfig.failOpenDisabled')) : '—',
      tone: c?.failOpen ? 'warning' : 'default',
    },
    {
      key: 'flushInterval',
      label: t('quotaConfig.flushIntervalLabel'),
      value:
        c?.flushIntervalSeconds != null
          ? `${c.flushIntervalSeconds} ${t('quotaConfig.flushIntervalUnit')}`
          : '—',
    },
  ]
})

const windowOptions = ['MINUTE', 'HOUR', 'DAY', 'MONTH']

// ── 运行时表单 ──

interface RuntimeForm {
  enabled: boolean
  failOpen: boolean
  windows: string[]
}

const runtimeForm = ref<RuntimeForm>({
  enabled: false,
  failOpen: false,
  windows: [],
})

const syncFormFromConfig = (c: QuotaConfig) => {
  runtimeForm.value.enabled = c.enabled
  runtimeForm.value.failOpen = c.failOpen
  runtimeForm.value.windows = [...c.windows]
}

const resetRuntimeForm = () => {
  if (config.value) syncFormFromConfig(config.value)
}

// ── 只读区（需重启生效） ──

interface ReadonlyField {
  /** 对应后端 restartRequiredFields 中的字段路径 */
  path: string
  label: string
  value: string
}

const readonlyOpen = ref<string[]>([])

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

/** 判断字段是否在重启生效列表中 */
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

/** 判断是否有变更 */
const configChanged = computed(() => {
  if (!config.value) return false
  const c = config.value
  if (runtimeForm.value.enabled !== c.enabled) return true
  if (runtimeForm.value.failOpen !== c.failOpen) return true
  if (
    runtimeForm.value.windows.length !== c.windows.length ||
    runtimeForm.value.windows.some(w => !c.windows.includes(w))
  ) {
    return true
  }
  return false
})

// ── 数据加载 ──

const loadConfig = async () => {
  loading.value = true
  try {
    const c = await getQuotaConfig()
    config.value = c
    if (c) syncFormFromConfig(c)
  } catch {
    config.value = null
  } finally {
    loading.value = false
  }
}

// ── 保存 ──

const handleSaveConfig = async () => {
  if (!config.value) return
  const c = config.value
  const payload: QuotaConfigPayload = {}

  if (runtimeForm.value.enabled !== c.enabled) {
    payload.enabled = runtimeForm.value.enabled
  }
  if (runtimeForm.value.failOpen !== c.failOpen) {
    payload.failOpen = runtimeForm.value.failOpen
  }
  if (
    runtimeForm.value.windows.length !== c.windows.length ||
    runtimeForm.value.windows.some(w => !c.windows.includes(w))
  ) {
    payload.windows = [...runtimeForm.value.windows]
  }

  if (Object.keys(payload).length === 0) {
    ElMessage.warning(t('quotaConfig.noChangesWarning'))
    return
  }

  saving.value = true
  try {
    await updateQuotaConfig(payload)
    ElMessage.success(t('quotaConfig.configUpdatedSuccess'))
    await loadConfig()
  } catch (err: unknown) {
    // 提取后端 400 原文消息
    const axiosErr = err as { response?: { data?: { message?: string } } }
    const msg = axiosErr.response?.data?.message
    if (msg) {
      ElMessage.error(msg)
    } else {
      ElMessage.error(t('quotaConfig.saveFailedError'))
    }
  } finally {
    saving.value = false
  }
}

onMounted(loadConfig)
</script>

<style scoped>
.card-title {
  font-weight: 600;
  font-size: 15px;
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

.readonly-count {
  margin-left: 8px;
}

.readonly-card :deep(.el-collapse) {
  border-top: none;
  border-bottom: none;
}

.readonly-card :deep(.el-collapse-item__header) {
  border-bottom: none;
  height: auto;
  min-height: 32px;
}

.restart-hint {
  display: flex;
  align-items: center;
  gap: 6px;
  margin: 0;
  font-size: 12px;
  color: var(--ja-ink-2, var(--el-text-color-secondary));
}
</style>
