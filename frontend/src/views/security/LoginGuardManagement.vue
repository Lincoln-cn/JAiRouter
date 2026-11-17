<template>
  <ConfigPageShell
    :title="t('loginGuard.title')"
    :description="t('loginGuard.scopeHint')"
    :loading="loading"
    :saving="saving"
    :saving-label="savingLabel"
    :dirty-count="dirtyCount"
    :can-write="canWrite"
    save-permission="security:loginguard:manage"
    @refresh="loadAll"
    @save="handleSave"
    @discard="discardChanges"
  >
    <el-alert
      v-if="loadError"
      class="load-error"
      type="error"
      :closable="false"
      show-icon
      :title="loadError"
    />

    <el-tabs v-model="activeTab" class="page-tabs">
      <el-tab-pane :label="t('loginGuard.tabThresholds')" name="thresholds">
        <ConfigSectionCard
          :title="t('loginGuard.thresholdTitle')"
          :description="t('loginGuard.thresholdDesc')"
          :error="errors.thresholds"
        >
          <template #actions>
            <span v-if="config?.updatedAt" class="updated-at">
              {{ t('loginGuard.updatedAt', { time: formatTime(config.updatedAt), by: config.updatedBy || '-' }) }}
            </span>
          </template>

          <div class="config-grid">
            <div v-for="field in thresholdFields" :key="field.key" class="config-item">
              <span class="config-label">{{ t(field.labelKey) }}</span>
              <el-input-number
                v-model="form[field.key]"
                :min="field.min"
                :max="field.max"
                :step="1"
                :disabled="!canWrite"
              />
              <span class="config-hint">{{ t(field.hintKey) }}</span>
            </div>
          </div>
        </ConfigSectionCard>
      </el-tab-pane>

      <el-tab-pane :label="t('loginGuard.tabLocked')" name="locked">
        <ConfigSectionCard
          :title="t('loginGuard.lockedTitle', { count: locked.length })"
          :description="t('loginGuard.lockedDesc')"
        >
          <template #actions>
            <ConfirmAction
              v-if="canWrite"
              :title="t('loginGuard.unlockAllConfirm')"
              :disabled="locked.length === 0"
              :width="320"
              @confirm="unlockAll"
            >
              <el-button
                v-permission="'security:loginguard:manage'"
                type="warning"
                size="small"
                :disabled="locked.length === 0"
              >
                {{ t('loginGuard.unlockAll') }}
              </el-button>
            </ConfirmAction>
          </template>

          <el-alert
            v-if="locked.length === 0"
            type="success"
            :closable="false"
            show-icon
            :title="t('loginGuard.noLocked')"
          />
          <el-table v-else :data="locked" size="small">
            <el-table-column prop="scope" :label="t('loginGuard.colScope')" width="120">
              <template #default="{ row }">
                {{ row.scope === 'ip' ? t('loginGuard.scopeIp') : t('loginGuard.scopeAccount') }}
              </template>
            </el-table-column>
            <el-table-column prop="username" :label="t('loginGuard.colUsername')">
              <template #default="{ row }">{{ row.username || '-' }}</template>
            </el-table-column>
            <el-table-column prop="ip" :label="t('loginGuard.colIp')" width="140" />
            <el-table-column :label="t('loginGuard.colRemaining')" width="120">
              <template #default="{ row }">{{ formatRemaining(row.remainingSeconds) }}</template>
            </el-table-column>
            <el-table-column :label="t('loginGuard.colOperations')" width="100">
              <template #default="{ row }">
                <el-button
                  v-if="canWrite"
                  v-permission="'security:loginguard:manage'"
                  link
                  type="primary"
                  :loading="unlocking === row.scope + '|' + row.username + '|' + row.ip"
                  @click="unlockOne(row)"
                >
                  {{ t('loginGuard.unlock') }}
                </el-button>
              </template>
            </el-table-column>
          </el-table>

          <el-alert
            class="footnote"
            type="warning"
            :closable="false"
            show-icon
            :title="t('loginGuard.instanceOnlyHint')"
          />
        </ConfigSectionCard>
      </el-tab-pane>
    </el-tabs>
  </ConfigPageShell>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import ConfigPageShell from '@/components/config/ConfigPageShell.vue'
import ConfigSectionCard from '@/components/config/ConfigSectionCard.vue'
import ConfirmAction from '@/components/ConfirmAction.vue'
import { usePermission } from '@/composables/usePermission'
import { useConfigDraft } from '@/composables/useConfigDraft'
import {
  getLockedPrincipals,
  getLoginGuardConfig,
  unlockLoginGuard,
  updateLoginGuardConfig,
  type LockedPrincipal,
  type LoginGuardConfig
} from '@/api/loginGuard'

interface ThresholdForm {
  maxFailuresPerPrincipal: number
  maxFailuresPerIp: number
  windowMinutes: number
  lockMinutes: number
}

interface ThresholdField {
  key: keyof ThresholdForm
  labelKey: string
  hintKey: string
  min: number
  max: number
}

/** 四个阈值字段的渲染/校验元数据：脏判定与越界校验都靠它遍历，避免逐个字段抄一遍 */
const THRESHOLD_FIELDS: ThresholdField[] = [
  {
    key: 'maxFailuresPerPrincipal',
    labelKey: 'loginGuard.principalFailures',
    hintKey: 'loginGuard.principalFailuresHint',
    min: 1,
    max: 1000
  },
  {
    key: 'maxFailuresPerIp',
    labelKey: 'loginGuard.perIpFailures',
    hintKey: 'loginGuard.perIpFailuresHint',
    min: 1,
    max: 10000
  },
  {
    key: 'windowMinutes',
    labelKey: 'loginGuard.windowMinutes',
    hintKey: 'loginGuard.windowMinutesHint',
    min: 0,
    max: 1440
  },
  {
    key: 'lockMinutes',
    labelKey: 'loginGuard.lockMinutes',
    hintKey: 'loginGuard.lockMinutesHint',
    min: 0,
    max: 1440
  }
]

const DEFAULT_THRESHOLDS: ThresholdForm = {
  maxFailuresPerPrincipal: 5,
  maxFailuresPerIp: 30,
  windowMinutes: 15,
  lockMinutes: 15
}

const { t } = useI18n()
const { hasPermission } = usePermission()

const canWrite = computed(() => hasPermission('security:loginguard:manage'))

/** 阈值配置与锁定情况是两个关注点：前者有草稿，后者是即时操作，故拆两个 tab。 */
const activeTab = ref('thresholds')
const thresholdFields = THRESHOLD_FIELDS

const loading = ref(false)
const loadError = ref('')
const unlocking = ref('')
const config = ref<LoginGuardConfig | null>(null)
const locked = ref<LockedPrincipal[]>([])

const form = reactive<ThresholdForm>({ ...DEFAULT_THRESHOLDS })

/**
 * 基线 = 最近一次加载成功（或保存成功）的值。dirty 判定与「放弃修改」都拿它比，
 * 不能直接比 `config`：保存后 `config` 会被重新拉取，且服务端可能归一化字段。
 */
const baseline = reactive<ThresholdForm>({ ...DEFAULT_THRESHOLDS })

const syncFromSnapshot = (snapshot: LoginGuardConfig | null): void => {
  const next: ThresholdForm = {
    maxFailuresPerPrincipal: snapshot?.maxFailuresPerPrincipal ?? DEFAULT_THRESHOLDS.maxFailuresPerPrincipal,
    maxFailuresPerIp: snapshot?.maxFailuresPerIp ?? DEFAULT_THRESHOLDS.maxFailuresPerIp,
    windowMinutes: snapshot?.windowMinutes ?? DEFAULT_THRESHOLDS.windowMinutes,
    lockMinutes: snapshot?.lockMinutes ?? DEFAULT_THRESHOLDS.lockMinutes
  }
  Object.assign(form, next)
  Object.assign(baseline, next)
}

const refreshDirty = (): boolean =>
  THRESHOLD_FIELDS.some(({ key }) => form[key] !== baseline[key])

const discardChanges = (): void => {
  Object.assign(form, { ...baseline })
}

/** 只拉配置：解锁/失败重试都走这里，避免冲掉另一个 tab 里未保存的草稿。 */
const loadConfig = async (): Promise<void> => {
  const snapshot = await getLoginGuardConfig()
  config.value = snapshot
  syncFromSnapshot(snapshot)
}

/** 只拉锁定列表：解锁之后只需刷新这一半。 */
const loadLocked = async (): Promise<void> => {
  locked.value = await getLockedPrincipals()
}

const loadAll = async (): Promise<void> => {
  loading.value = true
  loadError.value = ''
  try {
    await Promise.all([loadConfig(), loadLocked()])
  } catch (e) {
    loadError.value = messageOf(e, t('loginGuard.loadFailed'))
  } finally {
    loading.value = false
  }
}

/**
 * 阈值是一次性整体提交：后端 PUT 要四个字段全给，所以这里没有「只提交脏字段」的余地。
 */
const { saving, savingLabel, errors, dirtyCount, save } = useConfigDraft(
  [
    {
      key: 'thresholds',
      label: () => t('loginGuard.thresholdTitle'),
      isDirty: refreshDirty,
      validate: () => {
        const outOfRange = THRESHOLD_FIELDS.some(({ key, min, max }) => form[key] < min || form[key] > max)
        return outOfRange ? t('loginGuard.invalidThreshold') : null
      },
      save: async () => {
        const updated = await updateLoginGuardConfig({
          maxFailuresPerPrincipal: form.maxFailuresPerPrincipal,
          maxFailuresPerIp: form.maxFailuresPerIp,
          windowMinutes: form.windowMinutes,
          lockMinutes: form.lockMinutes
        })
        if (updated) {
          config.value = updated
        }
      },
      commit: () => {
        Object.assign(baseline, { ...form })
      }
    }
  ],
  { fallbackError: () => t('loginGuard.saveFailed') }
)

const handleSave = async (): Promise<void> => {
  const result = await save()
  if (!result.ok) {
    if (result.failed) {
      ElMessage.error(result.failed.message)
    }
    return
  }
  if (result.saved.length === 0) {
    return
  }
  ElMessage.success(t('loginGuard.saveSuccess'))
  try {
    await loadConfig()
  } catch (e) {
    ElMessage.error(messageOf(e, t('loginGuard.loadFailed')))
  }
}

const unlockOne = async (row: LockedPrincipal): Promise<void> => {
  unlocking.value = `${row.scope}|${row.username}|${row.ip}`
  try {
    const cleared = await unlockLoginGuard(
      row.scope === 'ip' ? { ip: row.ip } : { username: row.username, ip: row.ip }
    )
    ElMessage.success(t('loginGuard.unlockSuccess', { count: cleared }))
    await loadLocked()
  } catch (e) {
    ElMessage.error(messageOf(e, t('loginGuard.unlockFailed')))
  } finally {
    unlocking.value = ''
  }
}

const unlockAll = async (): Promise<void> => {
  try {
    const cleared = await unlockLoginGuard({})
    ElMessage.success(t('loginGuard.unlockSuccess', { count: cleared }))
    await loadLocked()
  } catch (e) {
    ElMessage.error(messageOf(e, t('loginGuard.unlockFailed')))
  }
}

const formatTime = (value: string): string => {
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString()
}

const formatRemaining = (seconds: number): string => {
  if (seconds < 60) return `${seconds}${t('loginGuard.unitSecond')}`
  return `${Math.floor(seconds / 60)}${t('loginGuard.unitMinute')} ${seconds % 60}${t('loginGuard.unitSecond')}`
}

const messageOf = (e: unknown, fallback: string): string => {
  if (e instanceof Error && e.message) return e.message
  return fallback
}

onMounted(loadAll)
</script>

<style scoped>
.page-tabs {
  margin-bottom: 4px;
}

.load-error {
  margin-bottom: 16px;
}

.config-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
  gap: 20px;
}

.config-item {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.config-label {
  font-size: 13px;
  font-weight: 500;
}

.config-hint {
  margin: 0;
  font-size: 12px;
  line-height: 1.6;
  color: var(--el-text-color-secondary);
}

.updated-at {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.footnote {
  margin-top: 12px;
}
</style>
