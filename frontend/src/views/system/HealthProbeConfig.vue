<template>
  <ConfigPageShell
    :title="t('healthConfig.pageTitle')"
    :description="t('healthConfig.hotHint')"
    :loading="loading"
    :can-write="canEdit"
    save-permission="config:health:write"
    :show-actions="false"
    @refresh="loadConfig"
  >
    <el-alert
      v-if="!config"
      :title="t('healthConfig.pageTitle')"
      :description="t('healthConfig.loadFailed')"
      type="warning"
      :closable="false"
      show-icon
      style="margin-bottom: 16px"
    />

    <template v-else>
      <ConfigSectionCard
        :title="t('healthConfig.readinessTitle')"
        :description="t('healthConfig.readinessDesc')"
        :error="errors.readiness"
      >
        <div class="switch-row">
          <div class="switch-text">
            <span class="switch-label">{{ t('healthConfig.readinessSwitch') }}</span>
            <span class="switch-hint">{{ t('healthConfig.readinessSwitchHint') }}</span>
          </div>
          <SettingsSwitch
            :model-value="form.includeDependencies"
            :disabled="!canEdit"
            :immediate-hint="t('healthConfig.immediateHint')"
            :confirm-message="readinessConfirm"
            :confirm-title="t('healthConfig.readinessSwitch')"
            :submit="submitReadiness"
            @update:model-value="form.includeDependencies = $event"
          />
        </div>

        <p class="impact" :class="{ 'impact-warn': form.includeDependencies }">
          <el-icon><InfoFilled /></el-icon>
          <span>{{ config.readiness.impact }}</span>
        </p>
      </ConfigSectionCard>

      <ConfigSectionCard
        :title="t('healthConfig.jwtRedisTitle')"
        :description="t('healthConfig.jwtRedisDesc')"
        :error="errors.jwtRedis"
      >
        <div class="switch-row">
          <div class="switch-text">
            <span class="switch-label">{{ t('healthConfig.jwtRedisSwitch') }}</span>
            <span class="switch-hint">{{ t('healthConfig.jwtRedisSwitchHint') }}</span>
          </div>
          <SettingsSwitch
            :model-value="form.failHealth"
            :disabled="!canEdit"
            :immediate-hint="t('healthConfig.immediateHint')"
            :submit="submitFailHealth"
            @update:model-value="form.failHealth = $event"
          />
        </div>

        <p class="impact">
          <el-icon><InfoFilled /></el-icon>
          <span>{{ config.jwtRedis.impact }}</span>
        </p>
      </ConfigSectionCard>

      <ConfigSectionCard :title="t('healthConfig.endpointTitle')" :description="t('healthConfig.endpointDesc')">
        <template #actions>
          <el-tag size="small" effect="plain" type="info">{{ t('healthConfig.readonlyTag') }}</el-tag>
        </template>
        <el-table :data="endpointRows" size="small" border>
          <el-table-column prop="consumer" :label="t('healthConfig.colConsumer')" min-width="180" />
          <el-table-column prop="endpoint" :label="t('healthConfig.colEndpoint')" min-width="220" />
          <el-table-column prop="semantics" :label="t('healthConfig.colSemantics')" min-width="280" />
        </el-table>
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
import { getHealthConfig, updateHealthConfig } from '@/api/healthConfig'
import type { HealthConfig, HealthConfigPayload } from '@/api/healthConfig'
import { usePermission } from '@/composables/usePermission'

const { t } = useI18n()
const { hasPermission } = usePermission()

interface HealthForm {
  includeDependencies: boolean
  failHealth: boolean
}

const config = ref<HealthConfig | null>(null)
const form = reactive<HealthForm>({ includeDependencies: false, failHealth: true })
const loading = ref(false)
const errors = reactive<{ readiness: string; jwtRedis: string }>({ readiness: '', jwtRedis: '' })

const canEdit = computed(() => hasPermission('config:health:write'))

const readinessConfirm = computed(() =>
  t('healthConfig.readinessConfirm', { impact: config.value?.readiness?.impact ?? '' })
)

function defaultForm(source: HealthConfig | null): HealthForm {
  return {
    includeDependencies: source?.readiness?.includeDependencies ?? false,
    failHealth: source?.jwtRedis?.failHealth ?? true
  }
}

const endpointRows = computed(() => [
  {
    consumer: t('healthConfig.rowLivenessConsumer'),
    endpoint: '/actuator/health/liveness',
    semantics: t('healthConfig.rowLivenessSemantics')
  },
  {
    consumer: t('healthConfig.rowReadinessConsumer'),
    endpoint: '/actuator/health/readiness',
    semantics: t('healthConfig.rowReadinessSemantics')
  },
  {
    consumer: t('healthConfig.rowAlertConsumer'),
    endpoint: '/actuator/health',
    semantics: t('healthConfig.rowAlertSemantics')
  }
])

async function loadConfig(): Promise<void> {
  loading.value = true
  try {
    const snapshot = await getHealthConfig()
    config.value = snapshot
    Object.assign(form, defaultForm(snapshot))
    errors.readiness = ''
    errors.jwtRedis = ''
  } catch {
    ElMessage.error(t('healthConfig.loadFailed'))
  } finally {
    loading.value = false
  }
}

const applySnapshot = (snapshot: HealthConfig | null, key: keyof typeof errors): void => {
  if (!snapshot) {
    return
  }
  config.value = snapshot
  Object.assign(form, defaultForm(snapshot))
  errors[key] = ''
}

/** 即时提交：成功由 SettingsSwitch 更新开关，失败抛错让其回弹。 */
async function submitReadiness(next: boolean): Promise<void> {
  const payload: HealthConfigPayload = { readinessIncludeDependencies: next }
  const snapshot = await updateHealthConfig(payload)
  applySnapshot(snapshot, 'readiness')
  ElMessage.success(t('healthConfig.saveSuccess'))
}

async function submitFailHealth(next: boolean): Promise<void> {
  const payload: HealthConfigPayload = { jwtRedisFailHealth: next }
  const snapshot = await updateHealthConfig(payload)
  applySnapshot(snapshot, 'jwtRedis')
  ElMessage.success(t('healthConfig.saveSuccess'))
}

onMounted(loadConfig)
</script>

<style scoped>
.switch-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 8px 0;
  border-top: 1px solid var(--el-border-color-lighter);
}

.switch-text {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.switch-label {
  font-size: 14px;
}

.switch-hint {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.impact {
  display: flex;
  align-items: flex-start;
  gap: 6px;
  margin: 12px 0 0;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  line-height: 1.6;
}

.impact-warn {
  color: var(--el-color-warning);
}
</style>
