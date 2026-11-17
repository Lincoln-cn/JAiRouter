<template>
  <ConfigPageShell
    :title="t('clusterConfig.pageTitle')"
    :description="t('clusterConfig.restartHint')"
    :loading="loading"
    :saving="saving"
    :saving-label="savingLabel"
    :dirty-count="dirtyCount"
    :can-write="canWrite"
    save-permission="config:cluster:write"
    @refresh="loadConfig"
    @save="handleSave"
    @discard="resetForm"
  >
    <el-alert
      v-if="!config"
      :title="t('clusterConfig.pageTitle')"
      :description="t('clusterConfig.loadFailed')"
      type="warning"
      :closable="false"
      show-icon
      style="margin-bottom: 16px"
    />

    <template v-else>
      <ConfigSectionCard
        :title="t('clusterConfig.modeTitle')"
        :description="config.mode === 'cluster' ? t('clusterConfig.modeDescCluster') : t('clusterConfig.modeDescStandalone')"
      >
        <template #actions>
          <el-tag size="small" :type="config.mode === 'cluster' ? 'success' : 'info'" effect="plain">
            {{ config.mode === 'cluster' ? t('clusterConfig.modeCluster') : t('clusterConfig.modeStandalone') }}
          </el-tag>
          <el-tag size="small" type="info" effect="plain">{{ t('clusterConfig.readonlyTag') }}</el-tag>
        </template>
        <div class="readonly-grid">
          <div class="readonly-item">
            <span class="readonly-label">{{ t('clusterConfig.replicasLabel') }}</span>
            <span class="readonly-value">{{ config.replicas }}</span>
          </div>
          <div class="readonly-item">
            <span class="readonly-label">{{ t('clusterConfig.eventsEnabledLabel') }}</span>
            <span class="readonly-value">
              {{ config.clusterEventsEnabled ? t('clusterConfig.on') : t('clusterConfig.off') }}
            </span>
          </div>
        </div>
      </ConfigSectionCard>

      <ConfigSectionCard
        v-if="clusterStatus"
        :title="t('clusterConfig.membersTitle')"
        :description="t('clusterConfig.memberCount', { total: clusterStatus.summary.total })"
      >
        <template #actions>
          <el-tag size="small" type="info" effect="plain">{{ t('clusterConfig.readonlyTag') }}</el-tag>
        </template>

        <el-empty
          v-if="clusterStatus.members.length === 0"
          :description="t('clusterConfig.membersEmpty')"
          :image-size="72"
        />

        <el-table v-else :data="clusterStatus.members" size="small">
          <el-table-column :label="t('clusterConfig.colReplica')" min-width="160">
            <template #default="{ row }">
              {{ row.displayName }}
              <el-tag
                v-if="row.instanceId === clusterStatus.self.instanceId"
                size="small"
                type="success"
                effect="plain"
                style="margin-left: 6px"
              >
                {{ t('clusterConfig.selfTag') }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column :label="t('clusterConfig.colStatus')" width="110">
            <template #default="{ row }">
              <span class="status-dot" :class="statusClass(row)"></span>
              {{ statusText(row) }}
            </template>
          </el-table-column>
          <el-table-column :label="t('clusterConfig.colUptime')" width="120">
            <template #default="{ row }">{{ uptimeText(row.startedAt) }}</template>
          </el-table-column>
          <el-table-column :label="t('clusterConfig.colConfigVersion')" width="130">
            <template #default="{ row }">
              <span :class="{ 'stale-version': row.configVersion < clusterStatus.clusterConfigVersion }">
                v{{ row.configVersion }}
              </span>
            </template>
          </el-table-column>
          <el-table-column :label="t('clusterConfig.colInstances')" width="140">
            <template #default="{ row }">
              {{ t('clusterConfig.instancesValue', { healthy: row.healthyInstances, total: row.instanceCount }) }}
            </template>
          </el-table-column>
          <el-table-column :label="t('clusterConfig.colLastSeen')" width="120">
            <template #default="{ row }">{{ lastSeenText(row.lastSeen) }}</template>
          </el-table-column>
        </el-table>
      </ConfigSectionCard>

      <ConfigSectionCard
        :title="t('clusterConfig.syncTitle')"
        :description="t('clusterConfig.syncDesc')"
        :error="errors.sync"
      >
        <div class="runtime-config-grid">
          <div class="config-switch-item">
            <span class="config-switch-label">{{ t('clusterConfig.syncEnabledLabel') }}</span>
            <SettingsSwitch
              :model-value="form.enabled"
              :disabled="!canWrite"
              :immediate-hint="t('clusterConfig.immediateHint')"
              :submit="submitSyncEnabled"
              @update:model-value="form.enabled = $event"
            />
          </div>
          <div class="config-switch-item">
            <span class="config-switch-label">{{ t('clusterConfig.pollIntervalLabel') }}</span>
            <el-input-number
              v-model="form.pollIntervalMs"
              :min="1000"
              :max="600000"
              :step="1000"
              :disabled="!form.enabled || !canWrite"
              controls-position="right"
              style="width: 180px"
            />
            <span class="unit">{{ t('clusterConfig.pollIntervalUnit') }}</span>
          </div>
          <div class="config-switch-item">
            <span class="config-switch-label">{{ t('clusterConfig.eventBusLabel') }}</span>
            <SettingsSwitch
              :model-value="form.eventBusEnabled"
              :disabled="!form.enabled || !canWrite"
              :immediate-hint="t('clusterConfig.immediateHint')"
              :submit="submitEventBusEnabled"
              @update:model-value="form.eventBusEnabled = $event"
            />
          </div>
        </div>

        <p class="card-desc hint">{{ t('clusterConfig.eventBusHint') }}</p>
        <p v-if="!form.enabled" class="mode-hint">{{ t('clusterConfig.syncDisabledHint') }}</p>
      </ConfigSectionCard>

      <ConfigSectionCard
        :title="t('clusterConfig.healthTitle')"
        :description="t('clusterConfig.healthDesc')"
        :error="errors.health"
      >
        <template #actions>
          <el-tag
            size="small"
            :type="config.healthSharing.active ? 'success' : 'info'"
            effect="plain"
          >
            {{ config.healthSharing.active ? t('clusterConfig.healthActive') : t('clusterConfig.healthInactive') }}
          </el-tag>
        </template>

        <div class="runtime-config-grid">
          <div class="config-switch-item">
            <span class="config-switch-label">{{ t('clusterConfig.healthEnabledLabel') }}</span>
            <SettingsSwitch
              :model-value="form.healthEnabled"
              :disabled="!canWrite"
              :immediate-hint="t('clusterConfig.immediateHint')"
              :submit="submitHealthEnabled"
              @update:model-value="form.healthEnabled = $event"
            />
          </div>
          <div class="config-switch-item">
            <span class="config-switch-label">{{ t('clusterConfig.healthModeLabel') }}</span>
            <el-select
              v-model="form.healthMode"
              :disabled="!form.healthEnabled || !canWrite"
              style="width: 200px"
            >
              <el-option :label="t('clusterConfig.healthModeLocal')" value="local" />
              <el-option :label="t('clusterConfig.healthModeShared')" value="shared" />
              <el-option :label="t('clusterConfig.healthModeQuorum')" value="quorum" />
            </el-select>
          </div>
          <div class="config-switch-item">
            <span class="config-switch-label">{{ t('clusterConfig.healthToleranceLabel') }}</span>
            <el-input-number
              v-model="form.healthToleranceCount"
              :min="1"
              :max="64"
              :disabled="!form.healthEnabled || form.healthMode !== 'quorum' || !canWrite"
              controls-position="right"
              style="width: 120px"
            />
          </div>
          <div class="config-switch-item">
            <span class="config-switch-label">{{ t('clusterConfig.healthGraceLabel') }}</span>
            <el-input-number
              v-model="form.healthGraceSeconds"
              :min="0"
              :max="3600"
              :disabled="!form.healthEnabled || !canWrite"
              controls-position="right"
              style="width: 120px"
            />
            <span class="unit">{{ t('clusterConfig.healthGraceUnit') }}</span>
          </div>
        </div>

        <p class="card-desc hint">{{ t('clusterConfig.healthModeHint') }}</p>
        <p v-if="config.mode === 'standalone'" class="mode-hint">{{ t('clusterConfig.healthStandaloneHint') }}</p>

        <div class="runtime-kv-grid">
          <div class="runtime-kv">
            <span class="runtime-kv-label">{{ t('clusterConfig.healthTrackedLabel') }}</span>
            <span class="runtime-kv-value">{{ config.healthSharing.trackedInstances }}</span>
          </div>
          <div class="runtime-kv">
            <span class="runtime-kv-label">{{ t('clusterConfig.healthLastRefreshLabel') }}</span>
            <span class="runtime-kv-value">{{ timeText(config.healthSharing.lastRefreshAt) }}</span>
          </div>
        </div>
      </ConfigSectionCard>

      <ConfigSectionCard
        :title="t('clusterConfig.fieldsTitle')"
        :description="t('clusterConfig.fieldsHint')"
      >
        <template #actions>
          <el-tag size="small" type="info" effect="plain">{{ t('clusterConfig.readonlyTag') }}</el-tag>
          <el-button link type="primary" @click="toggleReadonly">
            {{ readonlyOpen ? t('clusterConfig.collapse') : t('clusterConfig.expand') }}
          </el-button>
        </template>
        <div v-if="readonlyOpen" class="readonly-grid">
          <div v-for="field in readonlyFields" :key="field.path" class="readonly-item">
            <span class="readonly-label">
              {{ field.label }}
              <el-tag v-if="field.restart" size="small" type="info" effect="plain">
                {{ t('clusterConfig.restartRequiredBadge') }}
              </el-tag>
            </span>
            <span class="readonly-value">{{ field.path }}</span>
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
import ConfigPageShell from '@/components/config/ConfigPageShell.vue'
import ConfigSectionCard from '@/components/config/ConfigSectionCard.vue'
import SettingsSwitch from '@/components/config/SettingsSwitch.vue'
import { usePermission } from '@/composables/usePermission'
import { useConfigDraft } from '@/composables/useConfigDraft'
import {
  getClusterConfig,
  getClusterStatus,
  updateClusterConfig,
  type ClusterConfig,
  type ClusterConfigPayload,
  type ClusterMember,
  type ClusterStatus
} from '@/api/clusterConfig'

const { t } = useI18n()
const { hasPermission } = usePermission()

const canWrite = computed(() => hasPermission('config:cluster:write'))

const loading = ref(false)
const config = ref<ClusterConfig | null>(null)
const clusterStatus = ref<ClusterStatus | null>(null)
const readonlyOpen = ref(false)

interface ClusterForm {
  enabled: boolean
  pollIntervalMs: number
  eventBusEnabled: boolean
  healthEnabled: boolean
  healthMode: 'local' | 'shared' | 'quorum'
  healthToleranceCount: number
  healthGraceSeconds: number
}

const form = reactive<ClusterForm>({
  enabled: true,
  pollIntervalMs: 5000,
  eventBusEnabled: true,
  healthEnabled: false,
  healthMode: 'quorum',
  healthToleranceCount: 1,
  healthGraceSeconds: 30
})

/** 基线 = 服务端快照；即时开关提交时以它为底，避免把未保存的草稿一起写进去。 */
const baseline = reactive<ClusterForm>({ ...form })

function syncFromSnapshot(source: ClusterConfig): void {
  const next = defaultForm(source)
  Object.assign(form, next)
  Object.assign(baseline, next)
}

function defaultForm(source: ClusterConfig | null): ClusterForm {
  return {
    enabled: source?.configSync.enabled ?? true,
    pollIntervalMs: source?.configSync.pollIntervalMs ?? 5000,
    eventBusEnabled: source?.configSync.eventBusEnabled ?? true,
    healthEnabled: source?.healthSharing.enabled ?? false,
    healthMode: source?.healthSharing.mode ?? 'quorum',
    healthToleranceCount: source?.healthSharing.toleranceCount ?? 1,
    healthGraceSeconds: source?.healthSharing.graceSeconds ?? 30
  }
}

/** 用基线值 + 改动字段组装全量 payload（后端 update 是整体替换语义）。 */
function payloadOf(overrides: Partial<ClusterForm>): ClusterConfigPayload {
  const merged = { ...baseline, ...overrides }
  return {
    configSyncEnabled: merged.enabled,
    configSyncPollIntervalMs: merged.pollIntervalMs,
    configSyncEventBusEnabled: merged.eventBusEnabled,
    healthSharingEnabled: merged.healthEnabled,
    healthSharingMode: merged.healthMode,
    healthSharingToleranceCount: merged.healthToleranceCount,
    healthSharingGraceSeconds: merged.healthGraceSeconds
  }
}

function timeText(timestamp: number): string {
  if (!timestamp) {
    return t('clusterConfig.neverRefreshed')
  }
  return new Date(timestamp).toLocaleTimeString()
}

async function loadConfig(): Promise<void> {
  loading.value = true
  try {
    const [snapshot, status] = await Promise.all([getClusterConfig(), getClusterStatus()])
    config.value = snapshot
    clusterStatus.value = status
    if (snapshot) {
      syncFromSnapshot(snapshot)
    }
  } finally {
    loading.value = false
  }
}

function statusClass(member: ClusterMember): string {
  return member.lastSeen < Date.now() - 10000 ? 'suspect' : 'online'
}

function statusText(member: ClusterMember): string {
  return statusClass(member) === 'suspect'
    ? t('clusterConfig.statusSuspect')
    : t('clusterConfig.statusOnline')
}

function uptimeText(startedAt: number): string {
  const minutes = Math.max(0, Math.floor((Date.now() - startedAt) / 60000))
  if (minutes < 60) {
    return t('clusterConfig.uptimeMinutes', { minutes })
  }
  const hours = Math.floor(minutes / 60)
  if (hours < 24) {
    return t('clusterConfig.uptimeHours', { hours, minutes: minutes % 60 })
  }
  return t('clusterConfig.uptimeDays', { days: Math.floor(hours / 24), hours: hours % 24 })
}

function lastSeenText(lastSeen: number): string {
  const seconds = Math.max(0, Math.floor((Date.now() - lastSeen) / 1000))
  if (seconds < 5) {
    return t('clusterConfig.justNow')
  }
  return t('clusterConfig.secondsAgo', { seconds })
}

const readonlyFields = computed(() => {
  if (!config.value) {
    return []
  }
  return [
    ...config.value.hotEditableFields.map((path) => ({ path, label: t('clusterConfig.hotEditable'), restart: false })),
    ...config.value.restartRequiredFields.map((path) => ({ path, label: t('clusterConfig.restartRequired'), restart: true }))
  ]
})

const toggleReadonly = (): void => {
  readonlyOpen.value = !readonlyOpen.value
}

const applySnapshot = (snapshot: ClusterConfig): void => {
  config.value = snapshot
  syncFromSnapshot(snapshot)
}

/** 即时开关：只提交该字段，其余取基线，草稿保持不动。 */
async function submitSyncEnabled(next: boolean): Promise<void> {
  const snapshot = await updateClusterConfig(payloadOf({ enabled: next }))
  applySnapshot(snapshot)
  ElMessage.success(t('clusterConfig.saved'))
}

async function submitEventBusEnabled(next: boolean): Promise<void> {
  const snapshot = await updateClusterConfig(payloadOf({ eventBusEnabled: next }))
  applySnapshot(snapshot)
  ElMessage.success(t('clusterConfig.saved'))
}

async function submitHealthEnabled(next: boolean): Promise<void> {
  const snapshot = await updateClusterConfig(payloadOf({ healthEnabled: next }))
  applySnapshot(snapshot)
  ElMessage.success(t('clusterConfig.saved'))
}

const { saving, savingLabel, errors, dirtyCount, save } = useConfigDraft(
  [
    {
      key: 'sync',
      label: () => t('clusterConfig.syncTitle'),
      isDirty: () => form.pollIntervalMs !== baseline.pollIntervalMs,
      validate: () => (form.pollIntervalMs < 1000 ? t('clusterConfig.invalidPollInterval') : null),
      save: async () => {
        await updateClusterConfig(payloadOf({ pollIntervalMs: form.pollIntervalMs }))
      },
      commit: () => {
        baseline.pollIntervalMs = form.pollIntervalMs
      }
    },
    {
      key: 'health',
      label: () => t('clusterConfig.healthTitle'),
      isDirty: () => form.healthMode !== baseline.healthMode
        || form.healthToleranceCount !== baseline.healthToleranceCount
        || form.healthGraceSeconds !== baseline.healthGraceSeconds,
      save: async () => {
        await updateClusterConfig(payloadOf({
          healthMode: form.healthMode,
          healthToleranceCount: form.healthToleranceCount,
          healthGraceSeconds: form.healthGraceSeconds
        }))
      },
      commit: () => {
        baseline.healthMode = form.healthMode
        baseline.healthToleranceCount = form.healthToleranceCount
        baseline.healthGraceSeconds = form.healthGraceSeconds
      }
    }
  ],
  { fallbackError: () => t('clusterConfig.saveFailed') }
)

async function handleSave(): Promise<void> {
  const result = await save()
  if (!result.ok) {
    if (result.failed) {
      ElMessage.error(result.failed.message)
    }
    return
  }
  if (result.saved.length > 0) {
    ElMessage.success(t('clusterConfig.saved'))
    await loadConfig()
  }
}

function resetForm(): void {
  Object.assign(form, { ...baseline })
}

onMounted(loadConfig)
</script>

<style scoped>
.card-desc {
  margin: 0 0 12px;
  color: var(--el-text-color-regular);
  font-size: 13px;
  line-height: 1.6;
}

.card-desc.hint {
  margin-top: 8px;
  color: var(--el-text-color-secondary);
}

.runtime-config-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 20px 32px;
}

.config-switch-item {
  display: flex;
  align-items: center;
  gap: 8px;
}

.config-switch-label {
  color: var(--el-text-color-regular);
  font-size: 13px;
}

.unit {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.runtime-kv-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 32px;
  margin-top: 12px;
}

.runtime-kv {
  display: flex;
  align-items: center;
  gap: 8px;
}

.runtime-kv-label {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.runtime-kv-value {
  color: var(--el-text-color-regular);
  font-size: 13px;
  font-variant-numeric: tabular-nums;
}

.mode-hint {
  margin: 8px 0 0;
  color: var(--el-color-warning);
  font-size: 12px;
}

.readonly-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
  gap: 12px 24px;
}

.readonly-item {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.readonly-label {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.readonly-value {
  color: var(--el-text-color-primary);
  font-size: 13px;
  font-family: var(--el-font-family-mono, monospace);
  word-break: break-all;
}

.status-dot {
  display: inline-block;
  width: 7px;
  height: 7px;
  margin-right: 5px;
  border-radius: 50%;
  vertical-align: middle;
}

.status-dot.online {
  background: var(--el-color-success);
}

.status-dot.suspect {
  background: var(--el-color-warning);
}

.stale-version {
  color: var(--el-color-warning);
  font-weight: 600;
}
</style>
