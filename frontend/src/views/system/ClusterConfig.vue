<template>
  <PageSkeleton :title="t('clusterConfig.pageTitle')">
    <template #actions>
      <el-button @click="loadConfig" :loading="loading" size="default">
        <el-icon><Refresh /></el-icon>
        {{ t('clusterConfig.reload') }}
      </el-button>
    </template>

    <template #toolbar>
      <p class="restart-hint">
        <el-icon><InfoFilled /></el-icon>
        <span>{{ t('clusterConfig.restartHint') }}</span>
      </p>
    </template>

    <el-alert
      v-if="!config"
      :title="t('clusterConfig.pageTitle')"
      :description="t('clusterConfig.loadFailed')"
      type="warning"
      :closable="false"
      show-icon
      style="margin-bottom: 16px"
    />

    <!-- 部署形态 -->
    <el-card v-if="config" shadow="hover" style="margin-bottom: 16px">
      <template #header>
        <div class="card-header">
          <span class="card-title">{{ t('clusterConfig.modeTitle') }}</span>
          <el-tag size="small" :type="config.mode === 'cluster' ? 'success' : 'info'" effect="plain">
            {{ config.mode === 'cluster' ? t('clusterConfig.modeCluster') : t('clusterConfig.modeStandalone') }}
          </el-tag>
        </div>
      </template>

      <p class="card-desc">
        {{ config.mode === 'cluster' ? t('clusterConfig.modeDescCluster') : t('clusterConfig.modeDescStandalone') }}
      </p>

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
    </el-card>

    <!-- 集群成员 -->
    <el-card v-if="config && clusterStatus" shadow="hover" style="margin-bottom: 16px">
      <template #header>
        <div class="card-header">
          <span class="card-title">{{ t('clusterConfig.membersTitle') }}</span>
          <el-tag size="small" type="info" effect="plain">
            {{ t('clusterConfig.memberCount', { total: clusterStatus.summary.total }) }}
          </el-tag>
        </div>
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
    </el-card>

    <!-- 配置同步 -->
    <el-card v-if="config" shadow="hover" style="margin-bottom: 16px">
      <template #header>
        <div class="card-header">
          <span class="card-title">{{ t('clusterConfig.syncTitle') }}</span>
          <el-tag size="small" type="info" effect="plain">
            {{ t('clusterConfig.appliedVersion', { config: versionText(config.configSync.appliedConfigVersion), apiKey: versionText(config.configSync.appliedApiKeyVersion) }) }}
          </el-tag>
        </div>
      </template>

      <p class="card-desc">{{ t('clusterConfig.syncDesc') }}</p>

      <div class="runtime-config-grid">
        <div class="config-switch-item">
          <span class="config-switch-label">{{ t('clusterConfig.syncEnabledLabel') }}</span>
          <el-switch v-model="form.enabled" />
        </div>
        <div class="config-switch-item">
          <span class="config-switch-label">{{ t('clusterConfig.pollIntervalLabel') }}</span>
          <el-input-number
            v-model="form.pollIntervalMs"
            :min="1000"
            :max="600000"
            :step="1000"
            :disabled="!form.enabled"
            controls-position="right"
            style="width: 180px"
          />
          <span class="unit">{{ t('clusterConfig.pollIntervalUnit') }}</span>
        </div>
        <div class="config-switch-item">
          <span class="config-switch-label">{{ t('clusterConfig.eventBusLabel') }}</span>
          <el-switch v-model="form.eventBusEnabled" :disabled="!form.enabled" />
        </div>
      </div>

      <p class="card-desc hint">{{ t('clusterConfig.eventBusHint') }}</p>

      <p v-if="!form.enabled" class="mode-hint">
        {{ t('clusterConfig.syncDisabledHint') }}
      </p>

      <div class="config-actions">
        <el-button
          v-permission="'config:cluster:write'"
          :type="configChanged ? 'primary' : 'default'"
          :loading="saving"
          :disabled="!configChanged"
          @click="handleSave"
        >
          <el-icon><Check /></el-icon>
          {{ t('clusterConfig.save') }}
        </el-button>
        <el-button :disabled="saving" @click="resetForm">
          {{ t('clusterConfig.reset') }}
        </el-button>
      </div>
    </el-card>

    <!-- 上游健康共享 -->
    <el-card v-if="config" shadow="hover" style="margin-bottom: 16px">
      <template #header>
        <div class="card-header">
          <span class="card-title">{{ t('clusterConfig.healthTitle') }}</span>
          <el-tag
            size="small"
            :type="config.healthSharing.active ? 'success' : 'info'"
            effect="plain"
          >
            {{ config.healthSharing.active ? t('clusterConfig.healthActive') : t('clusterConfig.healthInactive') }}
          </el-tag>
        </div>
      </template>

      <p class="card-desc">{{ t('clusterConfig.healthDesc') }}</p>

      <div class="runtime-config-grid">
        <div class="config-switch-item">
          <span class="config-switch-label">{{ t('clusterConfig.healthEnabledLabel') }}</span>
          <el-switch v-model="form.healthEnabled" />
        </div>
        <div class="config-switch-item">
          <span class="config-switch-label">{{ t('clusterConfig.healthModeLabel') }}</span>
          <el-select
            v-model="form.healthMode"
            :disabled="!form.healthEnabled"
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
            :disabled="!form.healthEnabled || form.healthMode !== 'quorum'"
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
            :disabled="!form.healthEnabled"
            controls-position="right"
            style="width: 120px"
          />
          <span class="unit">{{ t('clusterConfig.healthGraceUnit') }}</span>
        </div>
      </div>

      <p class="card-desc hint">{{ t('clusterConfig.healthModeHint') }}</p>

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

      <p v-if="config.mode === 'standalone'" class="mode-hint">
        {{ t('clusterConfig.healthStandaloneHint') }}
      </p>

      <div class="config-actions">
        <el-button
          v-permission="'config:cluster:write'"
          :type="configChanged ? 'primary' : 'default'"
          :loading="saving"
          :disabled="!configChanged"
          @click="handleSave"
        >
          <el-icon><Check /></el-icon>
          {{ t('clusterConfig.save') }}
        </el-button>
        <el-button :disabled="saving" @click="resetForm">
          {{ t('clusterConfig.reset') }}
        </el-button>
      </div>
    </el-card>

    <!-- 字段清单 -->
    <el-card v-if="config" shadow="never" class="readonly-card">
      <el-collapse v-model="readonlyOpen">
        <el-collapse-item name="readonly">
          <template #title>
            <span class="card-title">{{ t('clusterConfig.fieldsTitle') }}</span>
            <el-tag size="small" type="info" effect="plain" class="readonly-count">
              {{ t('clusterConfig.fieldsCount', { count: readonlyFields.length }) }}
            </el-tag>
          </template>
          <div class="readonly-grid">
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
  getClusterConfig,
  getClusterStatus,
  updateClusterConfig,
  type ClusterConfig,
  type ClusterConfigPayload,
  type ClusterMember,
  type ClusterStatus
} from '@/api/clusterConfig'

const { t } = useI18n()

const loading = ref(false)
const saving = ref(false)
const config = ref<ClusterConfig | null>(null)
const clusterStatus = ref<ClusterStatus | null>(null)
const readonlyOpen = ref<string[]>([])

interface ClusterForm {
  enabled: boolean
  pollIntervalMs: number
  eventBusEnabled: boolean
  healthEnabled: boolean
  healthMode: 'local' | 'shared' | 'quorum'
  healthToleranceCount: number
  healthGraceSeconds: number
}

const form = ref<ClusterForm>(defaultForm(null))

/**
 * 从快照生成表单初值.
 */
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

/**
 * 时间戳的可读文本：0 表示尚未拉取.
 */
function timeText(timestamp: number): string {
  if (!timestamp) {
    return t('clusterConfig.neverRefreshed')
  }
  return new Date(timestamp).toLocaleTimeString()
}

/**
 * 已应用版本的可读文本：-1 表示尚未建立基线.
 */
function versionText(version: number): string {
  return version < 0 ? t('clusterConfig.notInitialized') : String(version)
}

/**
 * 加载集群设置快照与成员状态.
 */
async function loadConfig(): Promise<void> {
  loading.value = true
  try {
    const [snapshot, status] = await Promise.all([getClusterConfig(), getClusterStatus()])
    config.value = snapshot
    clusterStatus.value = status
    form.value = defaultForm(snapshot)
  } finally {
    loading.value = false
  }
}

/**
 * 成员状态样式：最近心跳超过 10 秒（2 个默认心跳周期）视为「疑似离线」.
 */
function statusClass(member: ClusterMember): string {
  return member.lastSeen < Date.now() - 10000 ? 'suspect' : 'online'
}

function statusText(member: ClusterMember): string {
  return statusClass(member) === 'suspect'
    ? t('clusterConfig.statusSuspect')
    : t('clusterConfig.statusOnline')
}

/**
 * 运行时长（天/小时/分钟）.
 */
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

/**
 * 最近心跳的相对时间.
 */
function lastSeenText(lastSeen: number): string {
  const seconds = Math.max(0, Math.floor((Date.now() - lastSeen) / 1000))
  if (seconds < 5) {
    return t('clusterConfig.justNow')
  }
  return t('clusterConfig.secondsAgo', { seconds })
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
    base.enabled !== form.value.enabled ||
    base.pollIntervalMs !== form.value.pollIntervalMs ||
    base.eventBusEnabled !== form.value.eventBusEnabled ||
    base.healthEnabled !== form.value.healthEnabled ||
    base.healthMode !== form.value.healthMode ||
    base.healthToleranceCount !== form.value.healthToleranceCount ||
    base.healthGraceSeconds !== form.value.healthGraceSeconds
  )
})

/**
 * 只读字段清单：可热改与需重启的路径.
 */
const readonlyFields = computed(() => {
  if (!config.value) {
    return []
  }
  return [
    ...config.value.hotEditableFields.map((path) => ({ path, label: t('clusterConfig.hotEditable'), restart: false })),
    ...config.value.restartRequiredFields.map((path) => ({ path, label: t('clusterConfig.restartRequired'), restart: true }))
  ]
})

/**
 * 保存热改字段并回填最新快照.
 */
async function handleSave(): Promise<void> {
  saving.value = true
  try {
    const payload: ClusterConfigPayload = {
      configSyncEnabled: form.value.enabled,
      configSyncPollIntervalMs: form.value.pollIntervalMs,
      configSyncEventBusEnabled: form.value.eventBusEnabled,
      healthSharingEnabled: form.value.healthEnabled,
      healthSharingMode: form.value.healthMode,
      healthSharingToleranceCount: form.value.healthToleranceCount,
      healthSharingGraceSeconds: form.value.healthGraceSeconds
    }
    const snapshot = await updateClusterConfig(payload)
    config.value = snapshot
    form.value = defaultForm(snapshot)
    ElMessage.success(t('clusterConfig.saved'))
  } catch (error) {
    const message =
      (error as { response?: { data?: { message?: string } } }).response?.data?.message ??
      t('clusterConfig.saveFailed')
    ElMessage.error(message)
  } finally {
    saving.value = false
  }
}

/**
 * 丢弃未保存的改动.
 */
function resetForm(): void {
  form.value = defaultForm(config.value)
}

onMounted(loadConfig)
</script>

<style scoped>
.restart-hint {
  display: flex;
  align-items: center;
  gap: 6px;
  margin: 0;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.card-title {
  font-weight: 600;
}

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

.config-actions {
  display: flex;
  gap: 12px;
  margin-top: 16px;
}

.readonly-card {
  margin-bottom: 16px;
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

.readonly-count {
  margin-left: 8px;
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
