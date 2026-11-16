<template>
  <PageSkeleton :title="t('monitoringConfig.pageTitle')">
    <template #actions>
      <el-button size="default" :loading="loading" @click="loadConfig">
        <el-icon><Refresh /></el-icon>
        {{ t('monitoringConfig.refresh') }}
      </el-button>
    </template>

    <template #toolbar>
      <p class="restart-hint">
        <el-icon><InfoFilled /></el-icon>
        <span>{{ t('monitoringConfig.restartHint') }}</span>
      </p>
    </template>

    <el-alert
      v-if="!config"
      type="warning"
      :title="t('monitoringConfig.noConfig')"
      :closable="false"
      show-icon
      style="margin-bottom: 16px"
    />

    <template v-else>
      <el-card shadow="hover" class="config-card">
        <template #header>
          <span class="card-title">{{ t('monitoringConfig.basicTitle') }}</span>
        </template>

        <div class="config-grid">
          <div class="config-item">
            <span class="config-label">{{ t('monitoringConfig.enabledLabel') }}</span>
            <el-switch v-model="basicForm.enabled" />
            <span class="config-hint">{{ t('monitoringConfig.enabledHint') }}</span>
          </div>
          <div class="config-item">
            <span class="config-label">{{ t('monitoringConfig.prefixLabel') }}</span>
            <el-input v-model="basicForm.prefix" style="width: 220px" />
            <span class="config-hint">{{ t('monitoringConfig.prefixHint') }}</span>
          </div>
          <div class="config-item">
            <span class="config-label">{{ t('monitoringConfig.intervalLabel') }}</span>
            <el-input-number v-model="basicForm.intervalSeconds" :min="1" :max="3600" />
          </div>
          <div class="config-item">
            <span class="config-label">{{ t('monitoringConfig.categoriesLabel') }}</span>
            <el-select v-model="basicForm.categories" multiple style="width: 260px">
              <el-option
                v-for="category in categoryOptions"
                :key="category"
                :label="t(`monitoringConfig.category${capitalize(category)}`)"
                :value="category"
              />
            </el-select>
          </div>
        </div>

        <div class="config-actions">
          <el-button
            v-if="canWrite"
            v-permission="'monitoring:config:write'"
            type="primary"
            :loading="saving"
            @click="saveBasic"
          >
            <el-icon><Check /></el-icon>
            {{ t('monitoringConfig.save') }}
          </el-button>
          <el-button :disabled="saving" @click="resetForms">{{ t('monitoringConfig.reset') }}</el-button>
        </div>
      </el-card>

      <el-card shadow="hover" class="config-card">
        <template #header>
          <span class="card-title">{{ t('monitoringConfig.samplingTitle') }}</span>
        </template>
        <p class="config-hint">{{ t('monitoringConfig.samplingHint') }}</p>

        <div class="config-grid">
          <div v-for="field in samplingFields" :key="field.key" class="config-item">
            <span class="config-label">{{ t(field.labelKey) }}</span>
            <el-input-number
              v-model="samplingForm[field.key]"
              :min="0"
              :max="1"
              :step="0.05"
              :precision="2"
            />
          </div>
        </div>

        <div class="config-actions">
          <el-button
            v-if="canWrite"
            v-permission="'monitoring:config:write'"
            type="primary"
            :loading="saving"
            @click="saveSampling"
          >
            <el-icon><Check /></el-icon>
            {{ t('monitoringConfig.save') }}
          </el-button>
        </div>
      </el-card>

      <el-card shadow="hover" class="config-card">
        <template #header>
          <span class="card-title">{{ t('monitoringConfig.performanceTitle') }}</span>
        </template>
        <p class="config-hint">{{ t('monitoringConfig.performanceHint') }}</p>

        <div class="config-grid">
          <div class="config-item">
            <span class="config-label">{{ t('monitoringConfig.asyncLabel') }}</span>
            <el-switch v-model="performanceForm.asyncProcessing" />
          </div>
          <div class="config-item">
            <span class="config-label">{{ t('monitoringConfig.batchSizeLabel') }}</span>
            <el-input-number v-model="performanceForm.batchSize" :min="1" :max="10000" />
          </div>
          <div class="config-item">
            <span class="config-label">{{ t('monitoringConfig.bufferSizeLabel') }}</span>
            <el-input-number v-model="performanceForm.bufferSize" :min="1" :max="100000" />
          </div>
        </div>

        <div class="config-actions">
          <el-button
            v-if="canWrite"
            v-permission="'monitoring:config:write'"
            type="primary"
            :loading="saving"
            @click="savePerformance"
          >
            <el-icon><Check /></el-icon>
            {{ t('monitoringConfig.save') }}
          </el-button>
        </div>
      </el-card>
    </template>
  </PageSkeleton>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { Check, InfoFilled, Refresh } from '@element-plus/icons-vue'
import PageSkeleton from '@/components/PageSkeleton.vue'
import { usePermission } from '@/composables/usePermission'
import {
  formatDurationSeconds,
  getMonitoringConfig,
  parseDurationSeconds,
  updateMonitoringCategories,
  updateMonitoringCollectionInterval,
  updateMonitoringEnabled,
  updateMonitoringPerformance,
  updateMonitoringPrefix,
  updateMonitoringSampling,
  type MonitoringConfig,
  type MonitoringPerformance,
  type MonitoringSampling
} from '@/api/monitoringConfig'

const { t } = useI18n()
const { hasPermission } = usePermission()

/** 写操作统一走 monitoring:config:write（运营角色有、只读角色无）。 */
const canWrite = computed(() => hasPermission('monitoring:config:write'))

const PREFIX_PATTERN = /^[a-zA-Z][a-zA-Z0-9_]*$/
const CATEGORY_OPTIONS = ['system', 'business', 'infrastructure']
const SAMPLING_FIELDS: { key: keyof MonitoringSampling; labelKey: string }[] = [
  { key: 'requestMetrics', labelKey: 'monitoringConfig.samplingRequest' },
  { key: 'backendMetrics', labelKey: 'monitoringConfig.samplingBackend' },
  { key: 'infrastructureMetrics', labelKey: 'monitoringConfig.samplingInfrastructure' },
  { key: 'traceMetrics', labelKey: 'monitoringConfig.samplingTrace' },
  { key: 'traceProcessingMetrics', labelKey: 'monitoringConfig.samplingTraceProcessing' },
  { key: 'traceAnalysisMetrics', labelKey: 'monitoringConfig.samplingTraceAnalysis' }
]

const loading = ref(false)
const saving = ref(false)
const config = ref<MonitoringConfig | null>(null)

const basicForm = reactive({
  enabled: true,
  prefix: '',
  intervalSeconds: 10,
  categories: [] as string[]
})
const samplingForm = reactive<MonitoringSampling>({
  requestMetrics: 1,
  backendMetrics: 1,
  infrastructureMetrics: 1,
  traceMetrics: 1,
  traceProcessingMetrics: 1,
  traceAnalysisMetrics: 1
})
const performanceForm = reactive<MonitoringPerformance>({
  asyncProcessing: true,
  batchSize: 100,
  bufferSize: 1000
})

const categoryOptions = CATEGORY_OPTIONS
const samplingFields = SAMPLING_FIELDS

const capitalize = (value: string): string => value.charAt(0).toUpperCase() + value.slice(1)

const applyConfig = (value: MonitoringConfig) => {
  config.value = value
  basicForm.enabled = value.enabled
  basicForm.prefix = value.prefix
  basicForm.intervalSeconds = parseDurationSeconds(value.collectionInterval) ?? 10
  basicForm.categories = [...(value.enabledCategories ?? [])]
  Object.assign(samplingForm, {
    requestMetrics: value.sampling?.requestMetrics ?? 1,
    backendMetrics: value.sampling?.backendMetrics ?? 1,
    infrastructureMetrics: value.sampling?.infrastructureMetrics ?? 1,
    traceMetrics: value.sampling?.traceMetrics ?? 1,
    traceProcessingMetrics: value.sampling?.traceProcessingMetrics ?? 1,
    traceAnalysisMetrics: value.sampling?.traceAnalysisMetrics ?? 1
  })
  performanceForm.asyncProcessing = value.performance?.asyncProcessing ?? true
  performanceForm.batchSize = value.performance?.batchSize ?? 100
  performanceForm.bufferSize = value.performance?.bufferSize ?? 1000
}

const loadConfig = async () => {
  loading.value = true
  try {
    const value = await getMonitoringConfig()
    if (value) {
      applyConfig(value)
    } else {
      config.value = null
    }
  } catch {
    ElMessage.error(t('monitoringConfig.saveFailed'))
  } finally {
    loading.value = false
  }
}

const resetForms = () => {
  if (config.value) {
    applyConfig(config.value)
  }
}

/** 基础配置有 4 个独立端点：只提交真正变化的项，避免把未改的项也写一遍审计。 */
const saveBasic = async () => {
  const current = config.value
  if (!current) {
    return
  }
  if (!PREFIX_PATTERN.test(basicForm.prefix)) {
    ElMessage.error(t('monitoringConfig.invalidPrefix'))
    return
  }
  if (!(basicForm.intervalSeconds > 0)) {
    ElMessage.error(t('monitoringConfig.invalidInterval'))
    return
  }

  saving.value = true
  try {
    if (basicForm.enabled !== current.enabled) {
      await updateMonitoringEnabled(basicForm.enabled)
    }
    if (basicForm.prefix !== current.prefix) {
      await updateMonitoringPrefix(basicForm.prefix)
    }
    const currentSeconds = parseDurationSeconds(current.collectionInterval)
    if (currentSeconds !== basicForm.intervalSeconds) {
      await updateMonitoringCollectionInterval(formatDurationSeconds(basicForm.intervalSeconds))
    }
    const currentCategories = [...(current.enabledCategories ?? [])].sort().join(',')
    if ([...basicForm.categories].sort().join(',') !== currentCategories) {
      await updateMonitoringCategories([...basicForm.categories])
    }
    ElMessage.success(t('monitoringConfig.saved'))
    await loadConfig()
  } catch {
    ElMessage.error(t('monitoringConfig.saveFailed'))
  } finally {
    saving.value = false
  }
}

const saveSampling = async () => {
  const invalid = SAMPLING_FIELDS.some(({ key }) => {
    const value = samplingForm[key]
    return value < 0 || value > 1
  })
  if (invalid) {
    ElMessage.error(t('monitoringConfig.invalidSampling'))
    return
  }

  saving.value = true
  try {
    await updateMonitoringSampling({ ...samplingForm })
    ElMessage.success(t('monitoringConfig.saved'))
    await loadConfig()
  } catch {
    ElMessage.error(t('monitoringConfig.saveFailed'))
  } finally {
    saving.value = false
  }
}

const savePerformance = async () => {
  if (performanceForm.bufferSize < performanceForm.batchSize) {
    ElMessage.error(t('monitoringConfig.invalidBuffer'))
    return
  }

  saving.value = true
  try {
    await updateMonitoringPerformance({ ...performanceForm })
    ElMessage.success(t('monitoringConfig.saved'))
    await loadConfig()
  } catch {
    ElMessage.error(t('monitoringConfig.saveFailed'))
  } finally {
    saving.value = false
  }
}

onMounted(loadConfig)
</script>

<style scoped>
.restart-hint {
  display: flex;
  gap: 8px;
  align-items: flex-start;
  margin: 0;
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.card-title {
  font-weight: 600;
}

.config-card {
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
  margin: 0 0 12px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.config-actions {
  display: flex;
  gap: 12px;
  margin-top: 20px;
}
</style>
