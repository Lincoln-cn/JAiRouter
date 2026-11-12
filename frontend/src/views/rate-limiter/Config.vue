<template>
  <PageSkeleton :title="t('ratelimitGlobal.pageTitle')">
    <template #actions>
      <el-button @click="loadConfig" :loading="loading" size="default">
        <el-icon><Refresh /></el-icon>
        {{ t('ratelimitGlobal.reload') }}
      </el-button>
    </template>

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

    <!-- L1：API Key 级每分钟限流 -->
    <el-card v-if="config" shadow="hover" style="margin-bottom: 16px">
      <template #header>
        <div class="card-header">
          <span class="card-title">{{ t('ratelimitGlobal.apiKeyTitle') }}</span>
          <el-tag size="small" type="info" effect="plain">
            {{ t('ratelimitGlobal.apiKeyBackend', { backend: config.distributed.apiKeyBackend }) }}
          </el-tag>
        </div>
      </template>

      <p class="card-desc">{{ t('ratelimitGlobal.apiKeyDesc') }}</p>

      <div class="config-switch-item">
        <span class="config-switch-label">{{ t('ratelimitGlobal.enableLabel') }}</span>
        <el-switch v-model="form.apiKeyEnabled" />
      </div>

      <p v-if="!form.apiKeyEnabled" class="mode-hint">
        {{ t('ratelimitGlobal.apiKeyDisabledHint') }}
      </p>
    </el-card>

    <!-- L2：终端用户级限流 -->
    <el-card v-if="config" shadow="hover" style="margin-bottom: 16px">
      <template #header>
        <div class="card-header">
          <span class="card-title">{{ t('ratelimitGlobal.endUserTitle') }}</span>
          <el-tag size="small" type="info" effect="plain">
            {{ t('ratelimitGlobal.endUserBackend', { backend: config.distributed.endUserBackend }) }}
          </el-tag>
        </div>
      </template>

      <p class="card-desc">{{ t('ratelimitGlobal.endUserDesc') }}</p>

      <div class="runtime-config-grid">
        <div class="config-switch-item">
          <span class="config-switch-label">{{ t('ratelimitGlobal.enableLabel') }}</span>
          <el-switch v-model="form.endUserEnabled" />
        </div>
        <div class="config-switch-item">
          <span class="config-switch-label">{{ t('ratelimitGlobal.thresholdLabel') }}</span>
          <el-input-number
            v-model="form.endUserRequestsPerMinute"
            :min="1"
            :max="1000000"
            :step="10"
            :disabled="!form.endUserEnabled"
            controls-position="right"
            style="width: 180px"
          />
          <span class="unit">{{ t('ratelimitGlobal.thresholdUnit') }}</span>
        </div>
        <div class="config-switch-item">
          <span class="config-switch-label">{{ t('ratelimitGlobal.headerEnabledLabel') }}</span>
          <el-switch v-model="form.endUserHeaderEnabled" :disabled="!form.endUserEnabled" />
        </div>
        <div class="config-switch-item">
          <span class="config-switch-label">{{ t('ratelimitGlobal.missingIdentityLabel') }}</span>
          <el-select
            v-model="form.endUserMissingIdentity"
            :disabled="!form.endUserEnabled"
            style="width: 200px"
          >
            <el-option value="SKIP" :label="t('ratelimitGlobal.missingIdentitySkip')" />
            <el-option value="REJECT" :label="t('ratelimitGlobal.missingIdentityReject')" />
          </el-select>
        </div>
      </div>

      <p class="card-desc hint">
        {{ t('ratelimitGlobal.headerEnabledHint') }}
      </p>

      <p v-if="!form.endUserEnabled" class="mode-hint">
        {{ t('ratelimitGlobal.endUserDisabledHint') }}
      </p>

      <div class="config-actions">
        <el-button
          v-permission="'config:ratelimit:write'"
          :type="configChanged ? 'primary' : 'default'"
          :loading="saving"
          :disabled="!configChanged"
          @click="handleSave"
        >
          <el-icon><Check /></el-icon>
          {{ t('ratelimitGlobal.save') }}
        </el-button>
        <el-button :disabled="saving" @click="resetForm">
          {{ t('ratelimitGlobal.reset') }}
        </el-button>
      </div>
    </el-card>

    <!-- 装配期决定、需重启才能改的字段 -->
    <el-card v-if="config" shadow="never" class="readonly-card">
      <el-collapse v-model="readonlyOpen">
        <el-collapse-item name="readonly">
          <template #title>
            <span class="card-title">{{ t('ratelimitGlobal.readonlyTitle') }}</span>
            <el-tag size="small" type="info" effect="plain" class="readonly-count">
              {{ t('ratelimitGlobal.readonlyCount', { count: readonlyFields.length }) }}
            </el-tag>
          </template>
          <div class="readonly-grid">
            <div v-for="field in readonlyFields" :key="field.path" class="readonly-item">
              <span class="readonly-label">
                {{ field.label }}
                <el-tag v-if="isRestartRequired(field.path)" size="small" type="info" effect="plain">
                  {{ t('ratelimitGlobal.restartRequiredBadge') }}
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
import {
  getRatelimitConfig,
  updateRatelimitConfig,
  type RatelimitConfig,
  type RatelimitConfigPayload
} from '@/api/ratelimitConfig'

const { t } = useI18n()

const loading = ref(false)
const saving = ref(false)
const config = ref<RatelimitConfig | null>(null)
const readonlyOpen = ref<string[]>([])

interface RatelimitForm {
  apiKeyEnabled: boolean
  endUserEnabled: boolean
  endUserRequestsPerMinute: number
  endUserHeaderEnabled: boolean
  endUserMissingIdentity: 'SKIP' | 'REJECT'
}

const form = ref<RatelimitForm>(defaultForm(null))

/**
 * 从快照生成表单初值.
 */
function defaultForm(source: RatelimitConfig | null): RatelimitForm {
  return {
    apiKeyEnabled: source?.apiKey.enabled ?? true,
    endUserEnabled: source?.endUser.enabled ?? false,
    endUserRequestsPerMinute: source?.endUser.requestsPerMinute ?? 60,
    endUserHeaderEnabled: source?.endUser.headerEnabled ?? true,
    endUserMissingIdentity: source?.endUser.missingIdentity ?? 'SKIP'
  }
}

/**
 * 加载配置快照.
 */
async function loadConfig(): Promise<void> {
  loading.value = true
  try {
    const snapshot = await getRatelimitConfig()
    config.value = snapshot
    form.value = defaultForm(snapshot)
  } finally {
    loading.value = false
  }
}

/**
 * 表单是否有未保存的改动.
 */
const configChanged = computed(() => {
  if (!config.value) {
    return false
  }
  const base = defaultForm(config.value)
  return (
    base.apiKeyEnabled !== form.value.apiKeyEnabled ||
    base.endUserEnabled !== form.value.endUserEnabled ||
    base.endUserRequestsPerMinute !== form.value.endUserRequestsPerMinute ||
    base.endUserHeaderEnabled !== form.value.endUserHeaderEnabled ||
    base.endUserMissingIdentity !== form.value.endUserMissingIdentity
  )
})

/**
 * 只读字段清单（装配期决定的值）.
 */
const readonlyFields = computed(() => {
  if (!config.value) {
    return []
  }
  const d = config.value.distributed
  return [
    {
      path: 'distributed.enabled',
      label: t('ratelimitGlobal.distributedLabel'),
      value: d.enabled ? t('ratelimitGlobal.on') : t('ratelimitGlobal.off')
    },
    { path: 'distributed.timeoutMs', label: t('ratelimitGlobal.timeoutLabel'), value: `${d.timeoutMs}` },
    {
      path: 'apiKey.localCacheSize',
      label: t('ratelimitGlobal.apiKeyCacheLabel'),
      value: `${config.value.apiKey.localCacheSize}`
    },
    {
      path: 'endUser.localCacheSize',
      label: t('ratelimitGlobal.endUserCacheLabel'),
      value: `${config.value.endUser.localCacheSize}`
    },
    {
      path: 'endUser.fingerprintLength',
      label: t('ratelimitGlobal.fingerprintLabel'),
      value: `${config.value.endUser.fingerprintLength}`
    }
  ]
})

/**
 * 该字段是否属于「需重启」一档.
 */
function isRestartRequired(path: string): boolean {
  return config.value?.restartRequiredFields.includes(path) ?? false
}

/**
 * 保存（仅提交发生变化的字段）.
 */
async function handleSave(): Promise<void> {
  if (!config.value) {
    return
  }
  const base = defaultForm(config.value)
  const payload: RatelimitConfigPayload = {}
  if (base.apiKeyEnabled !== form.value.apiKeyEnabled) {
    payload.apiKeyEnabled = form.value.apiKeyEnabled
  }
  if (base.endUserEnabled !== form.value.endUserEnabled) {
    payload.endUserEnabled = form.value.endUserEnabled
  }
  if (base.endUserRequestsPerMinute !== form.value.endUserRequestsPerMinute) {
    payload.endUserRequestsPerMinute = form.value.endUserRequestsPerMinute
  }
  if (base.endUserHeaderEnabled !== form.value.endUserHeaderEnabled) {
    payload.endUserHeaderEnabled = form.value.endUserHeaderEnabled
  }
  if (base.endUserMissingIdentity !== form.value.endUserMissingIdentity) {
    payload.endUserMissingIdentity = form.value.endUserMissingIdentity
  }

  saving.value = true
  try {
    const updated = await updateRatelimitConfig(payload)
    config.value = updated
    form.value = defaultForm(updated)
    ElMessage.success(t('ratelimitGlobal.saved'))
  } catch (error) {
    const message =
      (error as { response?: { data?: { message?: string } } })?.response?.data?.message ??
      t('ratelimitGlobal.saveFailed')
    ElMessage.error(message)
  } finally {
    saving.value = false
  }
}

/**
 * 还原为最近一次加载的快照.
 */
function resetForm(): void {
  form.value = defaultForm(config.value)
}

onMounted(loadConfig)
</script>

<style scoped>
.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.card-title {
  font-weight: 600;
  font-size: 15px;
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

.mode-hint {
  margin: 16px 0 0;
  color: var(--el-color-warning);
  font-size: 12px;
}

.config-actions {
  display: flex;
  gap: 12px;
  margin-top: 20px;
}

.readonly-card {
  background: var(--el-fill-color-lighter);
}

.readonly-card :deep(.el-card__body) {
  padding-top: 8px;
}

.readonly-count {
  margin-left: 8px;
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
