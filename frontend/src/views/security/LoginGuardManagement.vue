<template>
  <PageSkeleton :loading="loading" :error="error" :title="t('loginGuard.title')" :retry="loadAll">
    <template #actions>
      <el-button :loading="loading" @click="loadAll">{{ t('loginGuard.refresh') }}</el-button>
    </template>

    <template #toolbar>
      <el-alert type="info" :closable="false" show-icon :title="t('loginGuard.scopeHint')" />
    </template>

    <el-row :gutter="16">
      <el-col :xs="24" :lg="12">
        <el-card shadow="never" class="section-card">
          <template #header>
            <div class="card-header">
              <span>{{ t('loginGuard.thresholdTitle') }}</span>
              <span v-if="config?.updatedAt" class="updated-at">
                {{ t('loginGuard.updatedAt', { time: formatTime(config.updatedAt), by: config.updatedBy || '-' }) }}
              </span>
            </div>
          </template>

          <el-form label-position="top" :disabled="!canWrite">
            <el-form-item :label="t('loginGuard.principalFailures')">
              <el-input-number v-model="form.maxFailuresPerPrincipal" :min="1" :max="1000" :step="1" />
              <div class="field-hint">{{ t('loginGuard.principalFailuresHint') }}</div>
            </el-form-item>
            <el-form-item :label="t('loginGuard.perIpFailures')">
              <el-input-number v-model="form.maxFailuresPerIp" :min="1" :max="10000" :step="1" />
              <div class="field-hint">{{ t('loginGuard.perIpFailuresHint') }}</div>
            </el-form-item>
            <el-form-item :label="t('loginGuard.windowMinutes')">
              <el-input-number v-model="form.windowMinutes" :min="0" :max="1440" :step="1" />
              <div class="field-hint">{{ t('loginGuard.windowMinutesHint') }}</div>
            </el-form-item>
            <el-form-item :label="t('loginGuard.lockMinutes')">
              <el-input-number v-model="form.lockMinutes" :min="0" :max="1440" :step="1" />
              <div class="field-hint">{{ t('loginGuard.lockMinutesHint') }}</div>
            </el-form-item>
          </el-form>

          <div class="actions">
            <el-button
              v-if="canWrite"
              v-permission="'security:loginguard:manage'"
              type="primary"
              :loading="saving"
              @click="save"
            >
              {{ t('loginGuard.save') }}
            </el-button>
            <el-button :disabled="!dirty" @click="resetForm">{{ t('loginGuard.reset') }}</el-button>
          </div>
        </el-card>
      </el-col>

      <el-col :xs="24" :lg="12">
        <el-card shadow="never" class="section-card">
          <template #header>
            <div class="card-header">
              <span>{{ t('loginGuard.lockedTitle', { count: locked.length }) }}</span>
              <ConfirmAction
                :title="t('loginGuard.unlockAllConfirm')"
                :disabled="!canWrite || locked.length === 0"
                :width="320"
                @confirm="unlockAll"
              >
                <el-button
                  v-if="canWrite"
                  v-permission="'security:loginguard:manage'"
                  type="warning"
                  size="small"
                  :disabled="locked.length === 0"
                >
                  {{ t('loginGuard.unlockAll') }}
                </el-button>
              </ConfirmAction>
            </div>
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
        </el-card>
      </el-col>
    </el-row>
  </PageSkeleton>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import PageSkeleton from '@/components/PageSkeleton.vue'
import ConfirmAction from '@/components/ConfirmAction.vue'
import { usePermission } from '@/composables/usePermission'
import {
  getLockedPrincipals,
  getLoginGuardConfig,
  unlockLoginGuard,
  updateLoginGuardConfig,
  type LockedPrincipal,
  type LoginGuardConfig
} from '@/api/loginGuard'

const { t } = useI18n()
const { hasPermission } = usePermission()

const canWrite = computed(() => hasPermission('security:loginguard:manage'))

const loading = ref(false)
const saving = ref(false)
const unlocking = ref('')
const error = ref('')
const config = ref<LoginGuardConfig | null>(null)
const locked = ref<LockedPrincipal[]>([])

const form = reactive({
  maxFailuresPerPrincipal: 5,
  maxFailuresPerIp: 30,
  windowMinutes: 15,
  lockMinutes: 15
})

const dirty = computed(() => {
  if (!config.value) return false
  return (
    form.maxFailuresPerPrincipal !== config.value.maxFailuresPerPrincipal ||
    form.maxFailuresPerIp !== config.value.maxFailuresPerIp ||
    form.windowMinutes !== config.value.windowMinutes ||
    form.lockMinutes !== config.value.lockMinutes
  )
})

const loadAll = async () => {
  loading.value = true
  error.value = ''
  try {
    const [cfg, lockedRows] = await Promise.all([getLoginGuardConfig(), getLockedPrincipals()])
    config.value = cfg
    locked.value = lockedRows
    resetForm()
  } catch (e) {
    error.value = messageOf(e, t('loginGuard.loadFailed'))
  } finally {
    loading.value = false
  }
}

const resetForm = () => {
  if (!config.value) return
  form.maxFailuresPerPrincipal = config.value.maxFailuresPerPrincipal
  form.maxFailuresPerIp = config.value.maxFailuresPerIp
  form.windowMinutes = config.value.windowMinutes
  form.lockMinutes = config.value.lockMinutes
}

const save = async () => {
  saving.value = true
  try {
    const updated = await updateLoginGuardConfig({
      maxFailuresPerPrincipal: form.maxFailuresPerPrincipal,
      maxFailuresPerIp: form.maxFailuresPerIp,
      windowMinutes: form.windowMinutes,
      lockMinutes: form.lockMinutes
    })
    config.value = updated
    resetForm()
    ElMessage.success(t('loginGuard.saveSuccess'))
  } catch (e) {
    ElMessage.error(messageOf(e, t('loginGuard.saveFailed')))
  } finally {
    saving.value = false
  }
}

const unlockOne = async (row: LockedPrincipal) => {
  unlocking.value = `${row.scope}|${row.username}|${row.ip}`
  try {
    const cleared = await unlockLoginGuard(
      row.scope === 'ip' ? { ip: row.ip } : { username: row.username, ip: row.ip }
    )
    ElMessage.success(t('loginGuard.unlockSuccess', { count: cleared }))
    await loadAll()
  } catch (e) {
    ElMessage.error(messageOf(e, t('loginGuard.unlockFailed')))
  } finally {
    unlocking.value = ''
  }
}

const unlockAll = async () => {
  try {
    const cleared = await unlockLoginGuard({})
    ElMessage.success(t('loginGuard.unlockSuccess', { count: cleared }))
    await loadAll()
  } catch (e) {
    ElMessage.error(messageOf(e, t('loginGuard.unlockFailed')))
  }
}

const formatTime = (value: string) => {
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString()
}

const formatRemaining = (seconds: number) => {
  if (seconds < 60) return `${seconds}${t('loginGuard.unitSecond')}`
  return `${Math.floor(seconds / 60)}${t('loginGuard.unitMinute')} ${seconds % 60}${t('loginGuard.unitSecond')}`
}

const messageOf = (e: unknown, fallback: string) => {
  if (e instanceof Error && e.message) return e.message
  return fallback
}

onMounted(loadAll)
</script>

<style scoped>
.section-card {
  margin-bottom: 16px;
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.updated-at {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.field-hint {
  margin-top: 4px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.actions {
  margin-top: 8px;
}

.footnote {
  margin-top: 12px;
}
</style>
