<template>
  <PageSkeleton :title="t('recordGovernance.pageTitle')">
    <template #actions>
      <el-button data-test="refresh" @click="load" :loading="loading" size="default">
        <el-icon><Refresh /></el-icon>
        {{ t('recordGovernance.refresh') }}
      </el-button>
    </template>

    <template #stats>
      <MetricStrip :items="metricItems" :loading="loading" :columns="3" />
    </template>

    <el-alert
      v-if="fallbackDetected"
      data-test="fallback-warning"
      type="warning"
      :closable="false"
      show-icon
      :title="t('recordGovernance.fallbackTitle')"
      :description="t('recordGovernance.fallbackDesc')"
      style="margin-bottom: var(--ja-space-4)"
    />

    <el-card shadow="hover" style="margin-bottom: var(--ja-space-4)">
      <template #header>
        <span class="card-title">{{ t('recordGovernance.levelSection') }}</span>
      </template>
      <el-radio-group
        v-model="levelDraft"
        data-test="level-group"
        :disabled="!loaded || saving"
        @change="handleLevelChange"
      >
        <el-radio-button v-for="level in LEVELS" :key="level" :value="level">
          {{ t(`recordGovernance.level${levelPascal(level)}`) }}
        </el-radio-button>
      </el-radio-group>
      <p class="level-desc" data-test="level-desc">{{ levelDescription }}</p>
      <div class="config-actions">
        <el-button
          type="primary"
          data-test="save"
          :loading="saving"
          :disabled="!loaded || !dirty"
          @click="handleSave"
        >
          <el-icon><Check /></el-icon>
          {{ t('recordGovernance.save') }}
        </el-button>
        <el-button data-test="reset" :disabled="saving || !dirty" @click="resetDraft">
          {{ t('recordGovernance.reset') }}
        </el-button>
      </div>
    </el-card>

    <el-card shadow="hover" style="margin-bottom: var(--ja-space-4)">
      <template #header>
        <div class="card-header">
          <span class="card-title">{{ t('recordGovernance.encryptionSection') }}</span>
          <el-tag v-if="config" size="small" effect="plain" :type="encrypted ? 'success' : 'info'">
            {{ encrypted ? t('recordGovernance.encryptedTag') : t('recordGovernance.plaintextTag') }}
          </el-tag>
        </div>
      </template>
      <el-descriptions :column="2" border size="small">
        <el-descriptions-item :label="t('recordGovernance.algorithmLabel')">
          <span class="mono">{{ config?.encryptionAlgorithm || 'AES-256-GCM' }}</span>
        </el-descriptions-item>
        <el-descriptions-item :label="t('recordGovernance.sourceRequestedLabel')">
          <span class="mono" data-test="source-requested">{{ config?.encryptionKeySourceRequested || config?.encryptionKeySource || '—' }}</span>
        </el-descriptions-item>
        <el-descriptions-item :label="t('recordGovernance.sourceEffectiveLabel')">
          <span class="mono" data-test="source-effective">{{ effectiveSource }}</span>
        </el-descriptions-item>
        <el-descriptions-item :label="t('recordGovernance.envKeyPresentLabel')">
          <span data-test="env-key-present">
            {{ config?.environmentKeyPresent ? t('recordGovernance.keyPresentYes') : t('recordGovernance.keyPresentNo') }}
          </span>
        </el-descriptions-item>
        <el-descriptions-item :label="t('recordGovernance.keyFingerprintLabel')">
          <span class="mono" data-test="key-fingerprint">{{ config?.encryptionKeyFingerprint || '—' }}</span>
        </el-descriptions-item>
        <el-descriptions-item :label="t('recordGovernance.contentLimitLabel')">
          {{ formatBytes(config?.maxContentLength) }}
        </el-descriptions-item>
        <el-descriptions-item :label="t('recordGovernance.retentionLabel')">
          {{ config?.retentionDays != null ? t('recordGovernance.days', { n: config.retentionDays }) : '—' }}
        </el-descriptions-item>
        <el-descriptions-item :label="t('recordGovernance.maxRecordsLabel')">
          {{ config?.maxRecords != null ? String(config.maxRecords) : '—' }}
        </el-descriptions-item>
      </el-descriptions>
      <p class="hint">{{ t('recordGovernance.storageHint') }}</p>
    </el-card>

    <el-card shadow="hover">
      <template #header>
        <span class="card-title">{{ t('recordGovernance.auditSection') }}</span>
      </template>
      <p class="hint">{{ t('recordGovernance.auditDesc') }}</p>
      <el-button data-test="open-audit" @click="openAuditLogs">
        {{ t('recordGovernance.openAuditLogs') }}
      </el-button>
    </el-card>
  </PageSkeleton>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh, Check } from '@element-plus/icons-vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import PageSkeleton from '@/components/PageSkeleton.vue'
import MetricStrip from '@/components/MetricStrip.vue'
import type { MetricStripItem } from '@/components/MetricStrip.vue'
import { getErrorMessage } from '@/utils/error'
import {
  getCallHistoryConfig,
  updateCallHistoryConfig,
  type CallHistoryConfig
} from '@/api/callHistory'

const { t } = useI18n()
const router = useRouter()

const LEVELS = ['METADATA_ONLY', 'SUMMARY', 'FULL'] as const
type RecordLevel = (typeof LEVELS)[number]

const loading = ref(false)
const saving = ref(false)
const config = ref<CallHistoryConfig | null>(null)
const levelDraft = ref<RecordLevel>('METADATA_ONLY')

const loaded = computed(() => config.value !== null)
const dirty = computed(() => loaded.value && config.value?.recordLevel !== levelDraft.value)
const encrypted = computed(() => config.value?.contentStoredEncrypted === true)
const effectiveSource = computed(
  () => config.value?.encryptionKeySourceEffective
    || config.value?.encryptionKeySourceRequested
    || config.value?.encryptionKeySource
    || '—'
)
const fallbackDetected = computed(
  () => loaded.value && config.value?.encryptionKeySourceHonored === false
)

const levelDescription = computed(() => {
  switch (levelDraft.value) {
    case 'FULL':
      return t('recordGovernance.levelFullDesc')
    case 'SUMMARY':
      return t('recordGovernance.levelSummaryDesc')
    default:
      return t('recordGovernance.levelMetadataOnlyDesc')
  }
})

const metricItems = computed<MetricStripItem[]>(() => [
  {
    key: 'level',
    label: t('recordGovernance.metricLevel'),
    value: config.value?.recordLevel ?? '—'
  },
  {
    key: 'encrypted',
    label: t('recordGovernance.metricEncrypted'),
    value: encrypted.value ? t('common.enabled') : t('common.disabled'),
    tone: encrypted.value ? 'default' : 'warning'
  },
  {
    key: 'retention',
    label: t('recordGovernance.metricRetention'),
    value: config.value?.retentionDays != null
      ? t('recordGovernance.days', { n: config.value.retentionDays })
      : '—'
  }
])

const levelPascal = (level: RecordLevel): string =>
  level === 'METADATA_ONLY'
    ? 'MetadataOnly'
    : level === 'SUMMARY'
      ? 'Summary'
      : 'Full'

const formatBytes = (bytes?: number): string => {
  if (bytes == null) return '—'
  if (bytes >= 1024 * 1024) return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
  if (bytes >= 1024) return `${(bytes / 1024).toFixed(0)} KB`
  return `${bytes} B`
}

const syncDraft = () => {
  const current = config.value?.recordLevel
  levelDraft.value = (LEVELS as readonly string[]).includes(current ?? '')
    ? (current as RecordLevel)
    : 'METADATA_ONLY'
}

const load = async () => {
  loading.value = true
  try {
    config.value = await getCallHistoryConfig()
    syncDraft()
  } catch (e) {
    ElMessage.error(getErrorMessage(e) || t('recordGovernance.loadFailed'))
  } finally {
    loading.value = false
  }
}

const resetDraft = () => {
  syncDraft()
}

/**
 * 切到 FULL 需要二次确认：FULL 会把完整请求/响应体加密落库，
 * 查看详情时服务端解密并写 FULL_CONTENT_ACCESS 审计。
 * 取消时把草稿拨回当前配置值，避免界面显示与后端不一致。
 */
const handleLevelChange = async (next: string | number | boolean | undefined) => {
  if (next !== 'FULL') return
  try {
    await ElMessageBox.confirm(
      t('recordGovernance.fullConfirmMessage'),
      t('recordGovernance.fullConfirmTitle'),
      {
        type: 'warning',
        confirmButtonText: t('common.confirm'),
        cancelButtonText: t('common.cancel')
      }
    )
  } catch {
    syncDraft()
  }
}

const handleSave = async () => {
  if (!dirty.value) return
  saving.value = true
  try {
    config.value = await updateCallHistoryConfig({ recordLevel: levelDraft.value })
    syncDraft()
    ElMessage.success(t('recordGovernance.saveSuccess'))
  } catch (e) {
    ElMessage.error(getErrorMessage(e) || t('recordGovernance.saveFailed'))
  } finally {
    saving.value = false
  }
}

const openAuditLogs = () => {
  router.push('/security/audit-logs')
}

onMounted(load)
</script>

<style scoped>
.card-title {
  font-weight: 600;
}
.card-header {
  display: flex;
  align-items: center;
  gap: var(--ja-space-2);
}
.config-actions {
  display: flex;
  gap: var(--ja-space-2);
  margin-top: var(--ja-space-3);
}
.level-desc {
  margin: var(--ja-space-3) 0 0;
  color: var(--ja-ink-3);
  font-size: 13px;
  line-height: 1.6;
}
.hint {
  margin: var(--ja-space-3) 0;
  color: var(--ja-ink-3);
  font-size: 12px;
  line-height: 1.6;
}
.mono {
  font-family: var(--ja-font-mono);
}
</style>
