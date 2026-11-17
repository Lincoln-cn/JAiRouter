<template>
  <ConfigPageShell
    :title="t('monitoringConfig.pageTitle')"
    :description="t('monitoringConfig.pageDesc')"
    :loading="loading"
    :saving="saving"
    :saving-label="savingLabel"
    :dirty-count="dirtyCount"
    :can-write="canWrite"
    save-permission="monitoring:config:write"
    @refresh="loadConfig"
    @save="handleSave"
    @discard="resetForms"
  >
    <el-alert
      v-if="!config"
      type="warning"
      :title="t('monitoringConfig.noConfig')"
      :closable="false"
      show-icon
      style="margin-bottom: 16px"
    />

    <template v-else>
      <ConfigSectionCard
        :title="t('monitoringConfig.basicTitle')"
        :description="t('monitoringConfig.restartHint')"
        :error="errors.basic"
      >
        <div class="config-grid">
          <div class="config-item">
            <span class="config-label">{{ t('monitoringConfig.enabledLabel') }}</span>
            <SettingsSwitch
              :model-value="basicForm.enabled"
              :disabled="!canWrite"
              :immediate-hint="t('monitoringConfig.immediateHint')"
              :submit="submitEnabled"
              @update:model-value="basicForm.enabled = $event"
            />
            <span class="config-hint">{{ t('monitoringConfig.enabledHint') }}</span>
          </div>
          <div class="config-item">
            <span class="config-label">{{ t('monitoringConfig.prefixLabel') }}</span>
            <el-input v-model="basicForm.prefix" :disabled="!canWrite" style="width: 220px" />
            <span class="config-hint">{{ t('monitoringConfig.prefixHint') }}</span>
          </div>
          <div class="config-item">
            <span class="config-label">{{ t('monitoringConfig.intervalLabel') }}</span>
            <el-input-number v-model="basicForm.intervalSeconds" :min="1" :max="3600" :disabled="!canWrite" />
          </div>
          <div class="config-item">
            <span class="config-label">{{ t('monitoringConfig.categoriesLabel') }}</span>
            <el-select v-model="basicForm.categories" multiple :disabled="!canWrite" style="width: 260px">
              <el-option
                v-for="category in categoryOptions"
                :key="category"
                :label="t(`monitoringConfig.category${capitalize(category)}`)"
                :value="category"
              />
            </el-select>
          </div>
        </div>
      </ConfigSectionCard>

      <ConfigSectionCard
        :title="t('monitoringConfig.samplingTitle')"
        :description="t('monitoringConfig.samplingHint')"
        :error="errors.sampling"
      >
        <div class="config-grid">
          <div v-for="field in samplingFields" :key="field.key" class="config-item">
            <span class="config-label">{{ t(field.labelKey) }}</span>
            <el-input-number
              v-model="samplingForm[field.key]"
              :min="0"
              :max="1"
              :step="0.05"
              :precision="2"
              :disabled="!canWrite"
            />
          </div>
        </div>
      </ConfigSectionCard>

      <ConfigSectionCard
        :title="t('monitoringConfig.performanceTitle')"
        :description="t('monitoringConfig.performanceHint')"
        :error="errors.performance"
      >
        <div class="config-grid">
          <div class="config-item">
            <span class="config-label">{{ t('monitoringConfig.asyncLabel') }}</span>
            <el-switch v-model="performanceForm.asyncProcessing" :disabled="!canWrite" />
          </div>
          <div class="config-item">
            <span class="config-label">{{ t('monitoringConfig.batchSizeLabel') }}</span>
            <el-input-number v-model="performanceForm.batchSize" :min="1" :max="10000" :disabled="!canWrite" />
          </div>
          <div class="config-item">
            <span class="config-label">{{ t('monitoringConfig.bufferSizeLabel') }}</span>
            <el-input-number v-model="performanceForm.bufferSize" :min="1" :max="100000" :disabled="!canWrite" />
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

/**
 * 基线 = 上次保存成功（或最近一次加载）的值。`放弃修改` 与 dirty 判定都以它为准，
 * 所以它必须与表单分别维护，不能拿 `config` 直接比 —— `config` 在保存后会被重新拉取，
 * 而 duration 之类字段会被服务端归一化。
 */
const baseline = reactive({
  prefix: '',
  intervalSeconds: 10,
  categories: [] as string[],
  sampling: { ...samplingForm },
  performance: { ...performanceForm }
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

  baseline.prefix = basicForm.prefix
  baseline.intervalSeconds = basicForm.intervalSeconds
  baseline.categories = [...basicForm.categories]
  Object.assign(baseline.sampling, { ...samplingForm })
  Object.assign(baseline.performance, { ...performanceForm })
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

const sameCategories = (a: string[], b: string[]): boolean =>
  [...a].sort().join(',') === [...b].sort().join(',')

const basicDirty = (): boolean =>
  basicForm.prefix !== baseline.prefix
  || basicForm.intervalSeconds !== baseline.intervalSeconds
  || !sameCategories(basicForm.categories, baseline.categories)

const samplingDirty = (): boolean =>
  SAMPLING_FIELDS.some(({ key }) => samplingForm[key] !== baseline.sampling[key])

const performanceDirty = (): boolean =>
  performanceForm.asyncProcessing !== baseline.performance.asyncProcessing
  || performanceForm.batchSize !== baseline.performance.batchSize
  || performanceForm.bufferSize !== baseline.performance.bufferSize

/** 开关即时生效：失败时抛错，SettingsSwitch 会让开关回弹。 */
const submitEnabled = async (next: boolean): Promise<void> => {
  await updateMonitoringEnabled(next)
  config.value = config.value ? { ...config.value, enabled: next } : config.value
}

const { saving, savingLabel, errors, dirtyCount, save } = useConfigDraft(
  [
    {
      key: 'basic',
      label: () => t('monitoringConfig.basicTitle'),
      isDirty: basicDirty,
      validate: () => {
        if (!PREFIX_PATTERN.test(basicForm.prefix)) {
          return t('monitoringConfig.invalidPrefix')
        }
        if (!(basicForm.intervalSeconds > 0)) {
          return t('monitoringConfig.invalidInterval')
        }
        return null
      },
      save: async () => {
        if (basicForm.prefix !== baseline.prefix) {
          await updateMonitoringPrefix(basicForm.prefix)
        }
        if (basicForm.intervalSeconds !== baseline.intervalSeconds) {
          await updateMonitoringCollectionInterval(formatDurationSeconds(basicForm.intervalSeconds))
        }
        if (!sameCategories(basicForm.categories, baseline.categories)) {
          await updateMonitoringCategories([...basicForm.categories])
        }
      },
      commit: () => {
        baseline.prefix = basicForm.prefix
        baseline.intervalSeconds = basicForm.intervalSeconds
        baseline.categories = [...basicForm.categories]
      }
    },
    {
      key: 'sampling',
      label: () => t('monitoringConfig.samplingTitle'),
      isDirty: samplingDirty,
      validate: () => {
        const invalid = SAMPLING_FIELDS.some(({ key }) => {
          const value = samplingForm[key]
          return value < 0 || value > 1
        })
        return invalid ? t('monitoringConfig.invalidSampling') : null
      },
      save: async () => {
        await updateMonitoringSampling({ ...samplingForm })
      },
      commit: () => {
        Object.assign(baseline.sampling, { ...samplingForm })
      }
    },
    {
      key: 'performance',
      label: () => t('monitoringConfig.performanceTitle'),
      isDirty: performanceDirty,
      validate: () => (performanceForm.bufferSize < performanceForm.batchSize
        ? t('monitoringConfig.invalidBuffer')
        : null),
      save: async () => {
        await updateMonitoringPerformance({ ...performanceForm })
      },
      commit: () => {
        Object.assign(baseline.performance, { ...performanceForm })
      }
    }
  ],
  { fallbackError: () => t('monitoringConfig.saveFailed') }
)

const handleSave = async (): Promise<void> => {
  const result = await save()
  if (!result.ok) {
    if (result.failed) {
      ElMessage.error(`${result.failed.message}`)
    }
    return
  }
  if (result.saved.length > 0) {
    ElMessage.success(t('monitoringConfig.saved'))
    await loadConfig()
  }
}

onMounted(loadConfig)
</script>

<style scoped>
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
</style>
