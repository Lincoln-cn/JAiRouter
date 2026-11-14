<template>
  <PageSkeleton :title="t('healthConfig.pageTitle')">
    <template #actions>
      <el-button @click="loadConfig" :loading="loading" size="default">
        <el-icon><Refresh /></el-icon>
        {{ t('healthConfig.reload') }}
      </el-button>
    </template>

    <template #toolbar>
      <p class="restart-hint">
        <el-icon><InfoFilled /></el-icon>
        <span>{{ t('healthConfig.hotHint') }}</span>
      </p>
    </template>

    <el-alert
      v-if="!config"
      :title="t('healthConfig.pageTitle')"
      :description="t('healthConfig.loadFailed')"
      type="warning"
      :closable="false"
      show-icon
      style="margin-bottom: 16px"
    />

    <!-- 探针端点对照 -->
    <el-card v-if="config" shadow="hover" class="section-card">
      <template #header>
        <div class="card-header">
          <span class="card-title">{{ t('healthConfig.endpointTitle') }}</span>
        </div>
      </template>
      <p class="card-desc">{{ t('healthConfig.endpointDesc') }}</p>
      <el-table :data="endpointRows" size="small" border>
        <el-table-column prop="consumer" :label="t('healthConfig.colConsumer')" min-width="180" />
        <el-table-column prop="endpoint" :label="t('healthConfig.colEndpoint')" min-width="220" />
        <el-table-column prop="semantics" :label="t('healthConfig.colSemantics')" min-width="280" />
      </el-table>
    </el-card>

    <!-- 就绪探针 -->
    <el-card v-if="config" shadow="hover" class="section-card">
      <template #header>
        <div class="card-header">
          <span class="card-title">{{ t('healthConfig.readinessTitle') }}</span>
          <el-tag size="small" :type="config.readiness.includeDependencies ? 'warning' : 'info'" effect="plain">
            {{ config.readiness.includeDependencies ? t('healthConfig.enabled') : t('healthConfig.disabled') }}
          </el-tag>
        </div>
      </template>
      <p class="card-desc">{{ t('healthConfig.readinessDesc') }}</p>

      <div class="switch-row">
        <div class="switch-text">
          <span class="switch-label">{{ t('healthConfig.readinessSwitch') }}</span>
          <span class="switch-hint">{{ t('healthConfig.readinessSwitchHint') }}</span>
        </div>
        <el-switch v-model="form.includeDependencies" :disabled="!canEdit" />
      </div>

      <p class="impact" :class="{ 'impact-warn': form.includeDependencies }">
        <el-icon><InfoFilled /></el-icon>
        <span>{{ config.readiness.impact }}</span>
      </p>
    </el-card>

    <!-- JWT Redis 故障判定 -->
    <el-card v-if="config" shadow="hover" class="section-card">
      <template #header>
        <div class="card-header">
          <span class="card-title">{{ t('healthConfig.jwtRedisTitle') }}</span>
          <el-tag size="small" :type="config.jwtRedis.failHealth ? 'warning' : 'success'" effect="plain">
            {{ config.jwtRedis.failHealth ? t('healthConfig.failHealthOn') : t('healthConfig.failHealthOff') }}
          </el-tag>
        </div>
      </template>
      <p class="card-desc">{{ t('healthConfig.jwtRedisDesc') }}</p>

      <div class="switch-row">
        <div class="switch-text">
          <span class="switch-label">{{ t('healthConfig.jwtRedisSwitch') }}</span>
          <span class="switch-hint">{{ t('healthConfig.jwtRedisSwitchHint') }}</span>
        </div>
        <el-switch v-model="form.failHealth" :disabled="!canEdit" />
      </div>

      <p class="impact">
        <el-icon><InfoFilled /></el-icon>
        <span>{{ config.jwtRedis.impact }}</span>
      </p>
    </el-card>

    <div v-if="config" class="actions-row">
      <el-button @click="resetForm" :disabled="!configChanged">{{ t('healthConfig.reset') }}</el-button>
      <el-button
        type="primary"
        @click="handleSave"
        :loading="saving"
        :disabled="!configChanged"
        v-permission="'config:health:write'"
      >
        {{ t('healthConfig.save') }}
      </el-button>
    </div>
  </PageSkeleton>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { InfoFilled, Refresh } from '@element-plus/icons-vue'
import PageSkeleton from '@/components/PageSkeleton.vue'
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
const form = ref<HealthForm>({ includeDependencies: false, failHealth: true })
const loading = ref(false)
const saving = ref(false)

const canEdit = computed(() => hasPermission('config:health:write'))

function defaultForm(source: HealthConfig | null): HealthForm {
  return {
    includeDependencies: source?.readiness?.includeDependencies ?? false,
    failHealth: source?.jwtRedis?.failHealth ?? true,
  }
}

const configChanged = computed(() => {
  if (!config.value) return false
  const base = defaultForm(config.value)
  return (
    form.value.includeDependencies !== base.includeDependencies ||
    form.value.failHealth !== base.failHealth
  )
})

const endpointRows = computed(() => [
  {
    consumer: t('healthConfig.rowLivenessConsumer'),
    endpoint: '/actuator/health/liveness',
    semantics: t('healthConfig.rowLivenessSemantics'),
  },
  {
    consumer: t('healthConfig.rowReadinessConsumer'),
    endpoint: '/actuator/health/readiness',
    semantics: t('healthConfig.rowReadinessSemantics'),
  },
  {
    consumer: t('healthConfig.rowAlertConsumer'),
    endpoint: '/actuator/health',
    semantics: t('healthConfig.rowAlertSemantics'),
  },
])

async function loadConfig(): Promise<void> {
  loading.value = true
  try {
    const snapshot = await getHealthConfig()
    config.value = snapshot
    form.value = defaultForm(snapshot)
  } catch (error) {
    ElMessage.error(t('healthConfig.loadFailed'))
  } finally {
    loading.value = false
  }
}

function resetForm(): void {
  form.value = defaultForm(config.value)
}

async function handleSave(): Promise<void> {
  if (!config.value) return
  const base = defaultForm(config.value)
  const payload: HealthConfigPayload = {}
  if (form.value.includeDependencies !== base.includeDependencies) {
    payload.readinessIncludeDependencies = form.value.includeDependencies
  }
  if (form.value.failHealth !== base.failHealth) {
    payload.jwtRedisFailHealth = form.value.failHealth
  }
  if (Object.keys(payload).length === 0) return

  saving.value = true
  try {
    const snapshot = await updateHealthConfig(payload)
    config.value = snapshot
    form.value = defaultForm(snapshot)
    ElMessage.success(t('healthConfig.saveSuccess'))
  } catch (error) {
    ElMessage.error(t('healthConfig.saveFailed'))
  } finally {
    saving.value = false
  }
}

onMounted(loadConfig)
</script>

<style scoped>
.section-card {
  margin-bottom: 16px;
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.card-title {
  font-size: 15px;
  font-weight: 600;
}

.card-desc {
  margin: 0 0 12px;
  font-size: 13px;
  color: var(--el-text-color-secondary);
  line-height: 1.7;
}

.switch-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
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

.restart-hint {
  display: flex;
  align-items: center;
  gap: 6px;
  margin: 0;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.actions-row {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}
</style>
