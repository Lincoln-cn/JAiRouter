<template>
  <ConfigPageShell
    :title="t('recordGovernance.pageTitle')"
    :loading="loading"
    :saving="saving"
    :saving-label="savingLabel"
    :dirty-count="dirtyCount"
    :can-write="canWrite"
    save-permission="config:callhistory:write"
    @refresh="load"
    @save="handleSave"
    @discard="resetDraft"
  >
    <MetricStrip :items="metricItems" :loading="loading" :columns="3" class="overview-strip" />

    <el-alert
      v-if="fallbackDetected"
      data-test="fallback-warning"
      type="warning"
      :closable="false"
      show-icon
      :title="t('recordGovernance.fallbackTitle')"
      :description="t('recordGovernance.fallbackDesc')"
      class="fallback-alert"
    />

    <ConfigSectionCard
      :title="t('recordGovernance.levelSection')"
      :error="errors.level"
    >
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
    </ConfigSectionCard>

    <!-- 加密与存储全部是服务端回显：算法/密钥来源/指纹/env key 是否可用都不可在页面改 -->
    <ConfigSectionCard
      :title="t('recordGovernance.encryptionSection')"
      :description="t('recordGovernance.storageHint')"
    >
      <template #actions>
        <el-tag v-if="config" size="small" effect="plain" :type="encrypted ? 'success' : 'info'">
          {{ encrypted ? t('recordGovernance.encryptedTag') : t('recordGovernance.plaintextTag') }}
        </el-tag>
        <el-tag size="small" effect="plain" type="info">{{ t('recordGovernance.readonlyTag') }}</el-tag>
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
    </ConfigSectionCard>

    <ConfigSectionCard
      :title="t('recordGovernance.auditSection')"
      :description="t('recordGovernance.auditDesc')"
    >
      <el-button data-test="open-audit" @click="openAuditLogs">
        {{ t('recordGovernance.openAuditLogs') }}
      </el-button>
    </ConfigSectionCard>
  </ConfigPageShell>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import ConfigPageShell from '@/components/config/ConfigPageShell.vue'
import ConfigSectionCard from '@/components/config/ConfigSectionCard.vue'
import MetricStrip from '@/components/MetricStrip.vue'
import type { MetricStripItem } from '@/components/MetricStrip.vue'
import { usePermission } from '@/composables/usePermission'
import { useConfigDraft } from '@/composables/useConfigDraft'
import { getErrorMessage } from '@/utils/error'
import {
  getCallHistoryConfig,
  updateCallHistoryConfig,
  type CallHistoryConfig
} from '@/api/callHistory'

const { t } = useI18n()
const router = useRouter()
const { hasPermission } = usePermission()

/** 写操作统一走 config:callhistory:write（原有页内保存按钮挂的就是这个码）。 */
const canWrite = computed(() => hasPermission('config:callhistory:write'))

const LEVELS = ['METADATA_ONLY', 'SUMMARY', 'FULL'] as const
type RecordLevel = (typeof LEVELS)[number]

const loading = ref(false)
const config = ref<CallHistoryConfig | null>(null)
const levelDraft = ref<RecordLevel>('METADATA_ONLY')

const loaded = computed(() => config.value !== null)
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

/**
 * 记录级别是这一页唯一的可写配置（后端 PUT 也只接受 recordLevel），
 * 所以整页只有一个草稿分区，保存入口由页脚动作条统一提供。
 */
const { saving, savingLabel, errors, dirtyCount, save, clearError } = useConfigDraft(
  [
    {
      key: 'level',
      label: () => t('recordGovernance.levelSection'),
      isDirty: () => loaded.value && levelDraft.value !== config.value?.recordLevel,
      save: async () => {
        config.value = await updateCallHistoryConfig({ recordLevel: levelDraft.value })
      },
      commit: () => {
        // 提交成功后 config 已换成服务端快照，dirty 判定直接对 config 比较，无需另存基线
      }
    }
  ],
  { fallbackError: () => t('recordGovernance.saveFailed') }
)

const load = async () => {
  loading.value = true
  try {
    config.value = await getCallHistoryConfig()
    syncDraft()
    clearError()
  } catch (e) {
    ElMessage.error(getErrorMessage(e) || t('recordGovernance.loadFailed'))
  } finally {
    loading.value = false
  }
}

const resetDraft = () => {
  syncDraft()
  clearError()
}

const handleSave = async (): Promise<void> => {
  const result = await save()
  if (!result.ok) {
    if (result.failed) {
      ElMessage.error(result.failed.message)
    }
    return
  }
  if (result.saved.length > 0) {
    ElMessage.success(t('recordGovernance.saveSuccess'))
  }
}

const openAuditLogs = () => {
  router.push('/security/audit-logs')
}

onMounted(load)
</script>

<style scoped>
.overview-strip {
  margin-bottom: var(--ja-space-4);
}

.fallback-alert {
  margin-bottom: var(--ja-space-4);
}

.level-desc {
  margin: var(--ja-space-3) 0 0;
  color: var(--ja-ink-3);
  font-size: 13px;
  line-height: 1.6;
}

.mono {
  font-family: var(--el-font-family-mono, monospace);
}
</style>
