<template>
  <ConfigPageShell
    :title="t('tracing.management.title')"
    :description="t('tracing.management.pageDesc')"
    :loading="loading"
    :saving="saving"
    :saving-label="savingLabel"
    :dirty-count="dirtyCount"
    :can-write="canWrite"
    save-permission="tracing:config:manage"
    @refresh="loadAllData"
    @save="handleSave"
    @discard="resetForms"
  >
        <section class="config-group">
      <h3 class="config-group-title">{{ t('tracing.management.tabs.config') }}</h3>
        <ConfigSectionCard :title="t('tracing.management.tracingStatus')">
          <template #actions>
            <el-tag size="small" type="info" effect="plain">{{ t('tracing.management.readonlyTag') }}</el-tag>
          </template>
          <div class="status-grid">
            <div class="status-block">
              <div class="config-item">
                <span class="config-label">{{ t('tracing.management.enabled') }}</span>
                <SettingsSwitch
                  :model-value="tracingEnabled"
                  :disabled="!canWrite"
                  :immediate-hint="t('tracing.management.immediateHint')"
                  :submit="submitTracingEnabled"
                  @update:model-value="tracingEnabled = $event"
                />
              </div>
              <el-descriptions :column="1" border size="small">
                <el-descriptions-item :label="t('tracing.management.serviceName')">
                  {{ statusInfo.serviceName }}
                </el-descriptions-item>
                <el-descriptions-item :label="t('tracing.management.serviceVersion')">
                  {{ statusInfo.serviceVersion }}
                </el-descriptions-item>
                <el-descriptions-item label="OpenTelemetry">
                  <el-tag :type="statusInfo.openTelemetryEnabled ? 'info' : 'danger'" size="small">
                    {{ statusInfo.openTelemetryEnabled ? t('tracing.management.enabled') : t('tracing.management.disabled') }}
                  </el-tag>
                </el-descriptions-item>
                <el-descriptions-item :label="t('tracing.management.exporterType')">
                  <el-tag size="small">{{ statusInfo.exporterType || 'logging' }}</el-tag>
                </el-descriptions-item>
                <el-descriptions-item :label="t('tracing.management.globalSamplingRate')">
                  <el-progress :percentage="statusInfo.globalSamplingRatio" :format="formatPercentage" />
                </el-descriptions-item>
              </el-descriptions>
            </div>
            <div class="status-block">
              <span class="config-label">{{ t('tracing.management.healthStatus') }}</span>
              <el-descriptions :column="1" border size="small">
                <el-descriptions-item :label="t('tracing.management.overallStatus')">
                  <el-tag :type="healthStatus.status === 'UP' ? 'info' : 'danger'">{{ healthStatus.status }}</el-tag>
                </el-descriptions-item>
                <el-descriptions-item :label="t('tracing.management.memoryUsage')">
                  <el-progress
                    :percentage="healthStatus.memoryUsage"
                    :status="healthStatus.memoryUsage > 80 ? 'exception' : ''"
                  />
                </el-descriptions-item>
                <el-descriptions-item :label="t('tracing.management.activeTraces')">
                  {{ healthStatus.activeTraces }}
                </el-descriptions-item>
                <el-descriptions-item :label="t('tracing.management.bufferUsage')">
                  <el-progress
                    :percentage="healthStatus.bufferUsage"
                    :status="healthStatus.bufferUsage > 90 ? 'exception' : ''"
                  />
                </el-descriptions-item>
              </el-descriptions>
            </div>
          </div>
        </ConfigSectionCard>

        <ConfigSectionCard
          :title="t('tracing.management.samplingStrategy')"
          :description="t('tracing.management.samplingHint')"
          :error="errors.sampling"
        >
          <div class="config-grid">
            <div class="config-item">
              <span class="config-label">{{ t('tracing.management.samplingStrategy') }}</span>
              <el-select v-model="samplingForm.strategy" :disabled="!canWrite" style="width: 100%">
                <el-option
                  v-for="option in strategyOptions"
                  :key="option.value"
                  :label="option.label"
                  :value="option.value"
                />
              </el-select>
              <span class="config-hint">{{ t('tracing.management.strategyHint') }}</span>
            </div>
            <div class="config-item is-wide">
              <span class="config-label">{{ t('tracing.management.globalSamplingRate') }}</span>
              <el-slider v-model="samplingForm.globalRate" :min="0" :max="100" show-input :disabled="!canWrite" />
            </div>
            <div class="config-item">
              <span class="config-label">{{ t('tracing.management.adaptiveSampling') }}</span>
              <el-switch v-model="samplingForm.adaptiveSampling" :disabled="!canWrite" />
            </div>
          </div>

          <div v-if="samplingForm.adaptiveSampling" class="config-grid is-spaced">
            <div class="config-item">
              <span class="config-label">{{ t('tracing.management.alwaysSamplePaths') }}</span>
              <el-select
                v-model="samplingForm.alwaysSamplePaths"
                multiple
                filterable
                allow-create
                default-first-option
                :placeholder="t('tracing.management.pathPlaceholder')"
                :disabled="!canWrite"
                style="width: 100%"
              />
            </div>
            <div class="config-item">
              <span class="config-label">{{ t('tracing.management.neverSamplePaths') }}</span>
              <el-select
                v-model="samplingForm.neverSamplePaths"
                multiple
                filterable
                allow-create
                default-first-option
                :placeholder="t('tracing.management.pathPlaceholder')"
                :disabled="!canWrite"
                style="width: 100%"
              />
            </div>
          </div>
        </ConfigSectionCard>

        <ConfigSectionCard
          :title="t('tracing.management.tabs.exporter')"
          :description="t('tracing.management.exporterHint')"
          :error="errors.exporter"
        >
          <div class="config-grid">
            <div class="config-item">
              <span class="config-label">{{ t('tracing.management.exporterType') }}</span>
              <el-select v-model="exporterForm.type" :disabled="!canWrite" style="width: 100%">
                <el-option :label="t('tracing.management.loggingOption')" value="logging" />
                <el-option :label="t('tracing.management.otlpOption')" value="otlp" />
                <el-option :label="t('tracing.management.jaegerOption')" value="jaeger" />
              </el-select>
            </div>
            <div v-if="exporterForm.type === 'otlp'" class="config-item">
              <span class="config-label">OTLP Endpoint</span>
              <el-input
                v-model="exporterForm.otlpEndpoint"
                :disabled="!canWrite"
                placeholder="http://localhost:4318/v1/traces"
              />
            </div>
            <div v-if="exporterForm.type === 'jaeger'" class="config-item">
              <span class="config-label">Jaeger Endpoint</span>
              <el-input
                v-model="exporterForm.jaegerEndpoint"
                :disabled="!canWrite"
                placeholder="http://localhost:14268/api/traces"
              />
            </div>
            <div class="config-item">
              <span class="config-label">{{ t('tracing.management.loggingEnabled') }}</span>
              <el-switch v-model="exporterForm.loggingEnabled" :disabled="!canWrite" />
            </div>
          </div>
        </ConfigSectionCard>

        <ConfigSectionCard
          :title="t('tracing.management.performanceTitle')"
          :description="t('tracing.management.dataManagementHint')"
          :error="errors.performance"
        >
          <template #actions>
            <ConfirmAction
              :title="t('tracing.management.clearCacheMessage')"
              :confirm-text="t('tracing.management.confirm')"
              :cancel-text="t('tracing.management.cancel')"
              type="danger"
              :disabled="!canWrite"
              :loading="clearingCache"
              @confirm="handleClearCache"
            >
              <el-button
                v-permission="'tracing:config:manage'"
                type="danger"
                plain
                size="small"
                :loading="clearingCache"
                :disabled="!canWrite"
              >
                {{ t('tracing.management.clearCache') }}
              </el-button>
            </ConfirmAction>
            <ConfirmAction
              :title="t('tracing.management.cleanupExpiredConfirm', { hours: retentionHours })"
              :confirm-text="t('tracing.management.confirm')"
              :cancel-text="t('tracing.management.cancel')"
              type="danger"
              :disabled="!canWrite"
              :loading="cleaningUp"
              @confirm="handleCleanupExpired"
            >
              <el-button
                v-permission="'tracing:config:manage'"
                type="danger"
                plain
                size="small"
                :loading="cleaningUp"
                :disabled="!canWrite"
              >
                {{ t('tracing.management.cleanupExpired') }}
              </el-button>
            </ConfirmAction>
            <el-button size="small" @click="handleExportConfig">{{ t('tracing.management.exportConfig') }}</el-button>
          </template>

          <div class="config-grid">
            <div class="config-item">
              <span class="config-label">{{ t('tracing.management.asyncProcessing') }}</span>
              <SettingsSwitch
                :model-value="performanceForm.asyncProcessing"
                :disabled="!canWrite"
                :immediate-hint="t('tracing.management.immediateHint')"
                :submit="submitAsyncProcessing"
                @update:model-value="performanceForm.asyncProcessing = $event"
              />
            </div>
            <div class="config-item">
              <span class="config-label">{{ t('tracing.management.threadPoolCoreSize') }}</span>
              <el-input-number v-model="performanceForm.threadPoolCoreSize" :min="1" :max="100" :disabled="!canWrite" />
            </div>
            <div class="config-item">
              <span class="config-label">{{ t('tracing.management.bufferSize') }}</span>
              <el-input-number
                v-model="performanceForm.bufferSize"
                :min="100"
                :max="10000"
                :step="100"
                :disabled="!canWrite"
              />
            </div>
            <div class="config-item">
              <span class="config-label">{{ t('tracing.management.memoryLimitMb') }}</span>
              <el-input-number
                v-model="performanceForm.memoryLimitMb"
                :min="64"
                :max="2048"
                :step="64"
                :disabled="!canWrite"
              />
            </div>
            <div class="config-item">
              <span class="config-label">{{ t('tracing.management.retentionHours') }}</span>
              <el-input-number v-model="retentionHours" :min="1" :max="720" :disabled="!canWrite" />
              <span class="config-hint">{{ t('tracing.management.retentionPrompt') }}</span>
            </div>
          </div>
        </ConfigSectionCard>
    </section>

    <section class="config-group">
      <h3 class="config-group-title">{{ t('tracing.management.tabs.services') }}</h3>
        <ConfigSectionCard
          :title="t('tracing.management.serviceConfigsTitle')"
          :description="t('tracing.management.servicesHint')"
          :error="errors.services"
        >
          <el-table :data="serviceForm.rows" style="width: 100%">
            <el-table-column :label="t('tracing.management.serviceName')" min-width="200">
              <template #default="{ row }">
                <el-input v-model="row.service" :placeholder="t('tracing.management.serviceName')" :disabled="!canWrite" />
              </template>
            </el-table-column>
            <el-table-column :label="t('tracing.management.samplingRate')" min-width="260">
              <template #default="{ row }">
                <el-slider v-model="row.rate" :min="0" :max="100" show-input :disabled="!canWrite" />
              </template>
            </el-table-column>
            <el-table-column :label="t('tracing.management.action')" width="100">
              <template #default="{ $index }">
                <el-button
                  v-permission="'tracing:config:manage'"
                  type="danger"
                  text
                  :disabled="!canWrite"
                  @click="removeServiceConfig($index)"
                >
                  {{ t('tracing.management.delete') }}
                </el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-button
            v-permission="'tracing:config:manage'"
            type="primary"
            text
            style="margin-top: 12px"
            :disabled="!canWrite"
            @click="addServiceConfig"
          >
            {{ t('tracing.management.addServiceConfig') }}
          </el-button>
        </ConfigSectionCard>
    </section>
  </ConfigPageShell>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import ConfigPageShell from '@/components/config/ConfigPageShell.vue'
import ConfigSectionCard from '@/components/config/ConfigSectionCard.vue'
import SettingsSwitch from '@/components/config/SettingsSwitch.vue'
import ConfirmAction from '@/components/ConfirmAction.vue'
import { usePermission } from '@/composables/usePermission'
import { useConfigDraft } from '@/composables/useConfigDraft'
import {
  cleanupExpiredTraces,
  disableTracing,
  enableTracing,
  getTracingConfig,
  getTracingHealth,
  getTracingStatus,
  refreshSamplingStrategy,
  refreshTracingData,
  updateTracingConfig
} from '@/api/tracing'
import type { TracingConfig, TracingHealth, TracingStatus } from '@/types/tracing'

const { t } = useI18n()
const { hasPermission } = usePermission()

/** 写操作统一走 tracing:config:manage（与原页面 v-permission 一致）。 */
const canWrite = computed(() => hasPermission('tracing:config:manage'))

interface ServiceRateRow {
  service: string
  rate: number
}

/** 后端返回的段可能是任意形状（`TracingConfig` 只声明了本页用到的字段），统一用记录读。 */
const asRecord = (value: unknown): Record<string, unknown> =>
  typeof value === 'object' && value !== null ? (value as Record<string, unknown>) : {}

const asNumber = (value: unknown, fallback: number): number =>
  typeof value === 'number' && Number.isFinite(value) ? value : fallback

const asString = (value: unknown, fallback: string): string =>
  typeof value === 'string' ? value : fallback

const asStringArray = (value: unknown): string[] =>
  Array.isArray(value) ? value.filter((item): item is string => typeof item === 'string') : []

const loading = ref(false)
const clearingCache = ref(false)
const cleaningUp = ref(false)
const retentionHours = ref(24)
const tracingEnabled = ref(true)

/**
 * GET /config 返回整份 TracingConfiguration；PUT /config 里 sampling / exporter 整段替换、
 * performance 的子对象整段替换，未传的段会用默认对象覆盖回去（logging / monitoring 亦然）。
 * 所以保存时必须「整份快照 + 覆盖本页编辑字段」，不能只传被改的那一段。
 */
const fullConfig = ref<Record<string, unknown>>({})

const statusInfo = ref({
  serviceName: '',
  serviceVersion: '',
  openTelemetryEnabled: false,
  exporterType: 'logging',
  globalSamplingRatio: 0.1
})

const healthStatus = ref({
  status: 'UP',
  memoryUsage: 0,
  activeTraces: 0,
  bufferUsage: 0
})

const samplingForm = reactive({
  strategy: 'ratio',
  globalRate: 10,
  adaptiveSampling: false,
  alwaysSamplePaths: [] as string[],
  neverSamplePaths: [] as string[]
})

const exporterForm = reactive({
  type: 'logging',
  otlpEndpoint: '',
  jaegerEndpoint: '',
  loggingEnabled: false
})

const performanceForm = reactive({
  asyncProcessing: true,
  threadPoolCoreSize: 2,
  bufferSize: 1024,
  memoryLimitMb: 100
})

const serviceForm = reactive<{ rows: ServiceRateRow[] }>({ rows: [] })

/** 基线 = 上次保存成功（或最近一次加载）的值，dirty 判定与「放弃修改」都以它为准。 */
const samplingBaseline = reactive({
  strategy: 'ratio',
  globalRate: 10,
  adaptiveSampling: false,
  alwaysSamplePaths: [] as string[],
  neverSamplePaths: [] as string[]
})

const exporterBaseline = reactive({
  type: 'logging',
  otlpEndpoint: '',
  jaegerEndpoint: '',
  loggingEnabled: false
})

const performanceBaseline = reactive({
  asyncProcessing: true,
  threadPoolCoreSize: 2,
  bufferSize: 1024,
  memoryLimitMb: 100
})

const serviceBaseline = reactive<{ rows: ServiceRateRow[] }>({ rows: [] })

const strategyOptions = computed(() => [
  { value: 'ratio', label: t('tracing.management.strategyRatio') },
  { value: 'rule', label: t('tracing.management.strategyRule') },
  { value: 'adaptive', label: t('tracing.management.strategyAdaptive') }
])

const formatPercentage = (value: number): string => `${Math.round(value)}%`

const copySamplingBaseline = (): void => {
  samplingBaseline.strategy = samplingForm.strategy
  samplingBaseline.globalRate = samplingForm.globalRate
  samplingBaseline.adaptiveSampling = samplingForm.adaptiveSampling
  samplingBaseline.alwaysSamplePaths = [...samplingForm.alwaysSamplePaths]
  samplingBaseline.neverSamplePaths = [...samplingForm.neverSamplePaths]
}

const copyExporterBaseline = (): void => {
  exporterBaseline.type = exporterForm.type
  exporterBaseline.otlpEndpoint = exporterForm.otlpEndpoint
  exporterBaseline.jaegerEndpoint = exporterForm.jaegerEndpoint
  exporterBaseline.loggingEnabled = exporterForm.loggingEnabled
}

const copyPerformanceBaseline = (): void => {
  performanceBaseline.asyncProcessing = performanceForm.asyncProcessing
  performanceBaseline.threadPoolCoreSize = performanceForm.threadPoolCoreSize
  performanceBaseline.bufferSize = performanceForm.bufferSize
  performanceBaseline.memoryLimitMb = performanceForm.memoryLimitMb
}

const copyServiceBaseline = (): void => {
  serviceBaseline.rows = serviceForm.rows.map((row) => ({ service: row.service, rate: row.rate }))
}

const loadStatus = async (): Promise<void> => {
  try {
    const response = await getTracingStatus()
    const data = (response.data?.data || response.data) as TracingStatus | undefined
    if (data) {
      statusInfo.value = {
        serviceName: data.serviceName || 'jairouter',
        serviceVersion: data.serviceVersion || '1.0.0',
        openTelemetryEnabled: data.openTelemetry?.enabled ?? true,
        exporterType: data.exporter?.type || 'logging',
        globalSamplingRatio: Math.round((data.sampling?.globalRatio ?? 0.1) * 100)
      }
      tracingEnabled.value = data.enabled !== false
    }
  } catch (error) {
    console.error('加载状态失败:', error)
  }
}

const loadHealth = async (): Promise<void> => {
  try {
    const response = await getTracingHealth()
    const data = (response.data?.data || response.data) as TracingHealth | undefined
    if (data) {
      healthStatus.value = {
        status: data.status || 'UP',
        memoryUsage: Math.round((data.memoryUsage || 0) * 100),
        activeTraces: data.activeTraces || 0,
        bufferUsage: Math.round((data.bufferUsage || 0) * 100)
      }
    }
  } catch (error) {
    console.error('加载健康状态失败:', error)
  }
}

const loadConfig = async (): Promise<void> => {
  try {
    const response = await getTracingConfig()
    const data = (response.data?.data || response.data) as TracingConfig | undefined
    if (!data) {
      return
    }
    const raw = data as unknown as Record<string, unknown>
    fullConfig.value = raw

    const sampling = asRecord(raw.sampling)
    samplingForm.strategy = asString(sampling.strategy, 'ratio')
    samplingForm.globalRate = Math.round(asNumber(sampling.ratio, 0.1) * 100)
    samplingForm.adaptiveSampling = asRecord(sampling.adaptive).enabled === true
    samplingForm.alwaysSamplePaths = asStringArray(sampling.alwaysSample)
    samplingForm.neverSamplePaths = asStringArray(sampling.neverSample)
    copySamplingBaseline()

    const exporter = asRecord(raw.exporter)
    exporterForm.type = asString(exporter.type, 'logging')
    exporterForm.loggingEnabled = asRecord(exporter.logging).enabled === true
    exporterForm.otlpEndpoint = asString(asRecord(exporter.otlp).endpoint, '')
    exporterForm.jaegerEndpoint = asString(asRecord(exporter.jaeger).endpoint, '')
    copyExporterBaseline()

    const performance = asRecord(raw.performance)
    performanceForm.asyncProcessing = performance.asyncProcessing !== false
    performanceForm.threadPoolCoreSize = asNumber(asRecord(performance.threadPool).coreSize, 2)
    performanceForm.bufferSize = asNumber(asRecord(performance.buffer).size, 1024)
    performanceForm.memoryLimitMb = asNumber(asRecord(performance.memory).memoryLimitMb, 100)
    copyPerformanceBaseline()

    serviceForm.rows = Object.entries(asRecord(sampling.serviceRatios)).map(([service, rate]) => ({
      service,
      rate: Math.round(asNumber(rate, 0) * 100)
    }))
    copyServiceBaseline()
  } catch (error) {
    console.error('加载配置失败:', error)
  }
}

const loadAllData = async (): Promise<void> => {
  loading.value = true
  try {
    await Promise.all([loadStatus(), loadHealth(), loadConfig()])
  } finally {
    loading.value = false
  }
}

const resetForms = (): void => {
  samplingForm.strategy = samplingBaseline.strategy
  samplingForm.globalRate = samplingBaseline.globalRate
  samplingForm.adaptiveSampling = samplingBaseline.adaptiveSampling
  samplingForm.alwaysSamplePaths = [...samplingBaseline.alwaysSamplePaths]
  samplingForm.neverSamplePaths = [...samplingBaseline.neverSamplePaths]

  exporterForm.type = exporterBaseline.type
  exporterForm.otlpEndpoint = exporterBaseline.otlpEndpoint
  exporterForm.jaegerEndpoint = exporterBaseline.jaegerEndpoint
  exporterForm.loggingEnabled = exporterBaseline.loggingEnabled

  performanceForm.asyncProcessing = performanceBaseline.asyncProcessing
  performanceForm.threadPoolCoreSize = performanceBaseline.threadPoolCoreSize
  performanceForm.bufferSize = performanceBaseline.bufferSize
  performanceForm.memoryLimitMb = performanceBaseline.memoryLimitMb

  serviceForm.rows = serviceBaseline.rows.map((row) => ({ service: row.service, rate: row.rate }))
}

const sameList = (left: string[], right: string[]): boolean =>
  [...left].sort().join('\u0000') === [...right].sort().join('\u0000')

const samplingDirty = (): boolean =>
  samplingForm.strategy !== samplingBaseline.strategy
  || samplingForm.globalRate !== samplingBaseline.globalRate
  || samplingForm.adaptiveSampling !== samplingBaseline.adaptiveSampling
  || !sameList(samplingForm.alwaysSamplePaths, samplingBaseline.alwaysSamplePaths)
  || !sameList(samplingForm.neverSamplePaths, samplingBaseline.neverSamplePaths)

const exporterDirty = (): boolean =>
  exporterForm.type !== exporterBaseline.type
  || exporterForm.otlpEndpoint !== exporterBaseline.otlpEndpoint
  || exporterForm.jaegerEndpoint !== exporterBaseline.jaegerEndpoint
  || exporterForm.loggingEnabled !== exporterBaseline.loggingEnabled

/** asyncProcessing 是即时开关（拨动即写库），不进草稿 dirty。 */
const performanceDirty = (): boolean =>
  performanceForm.threadPoolCoreSize !== performanceBaseline.threadPoolCoreSize
  || performanceForm.bufferSize !== performanceBaseline.bufferSize
  || performanceForm.memoryLimitMb !== performanceBaseline.memoryLimitMb

const serviceDirty = (): boolean =>
  JSON.stringify(serviceForm.rows) !== JSON.stringify(serviceBaseline.rows)

interface SnapshotOptions {
  /** 覆盖异步处理开关（即时提交用） */
  asyncProcessing?: boolean
  /** false = 用基线值拼快照（即时提交不应顺带写入未保存的草稿） */
  useDraft?: boolean
}

/**
 * 拼出「整份配置」的提交体：基底是 GET /config 的完整快照，只覆盖本页可编辑字段。
 * sampling / exporter 是整段替换语义，performance 的子对象也是整段替换，
 * 因此被编辑段必须显式带上未暴露字段（rules / adaptive.custom / threadPool.maxSize …）。
 */
const buildSnapshot = ({ asyncProcessing, useDraft = true }: SnapshotOptions = {}): Record<string, unknown> => {
  const samplingBase = asRecord(fullConfig.value.sampling)
  const exporterBase = asRecord(fullConfig.value.exporter)
  const performanceBase = asRecord(fullConfig.value.performance)

  const sampling = useDraft ? samplingForm : samplingBaseline
  const exporter = useDraft ? exporterForm : exporterBaseline
  const performance = useDraft ? performanceForm : performanceBaseline
  const serviceRows = useDraft ? serviceForm.rows : serviceBaseline.rows

  const serviceRatios = serviceRows.reduce<Record<string, number>>((acc, row) => {
    const name = row.service.trim()
    if (name) {
      acc[name] = row.rate / 100
    }
    return acc
  }, {})

  return {
    ...fullConfig.value,
    sampling: {
      ...samplingBase,
      strategy: sampling.strategy,
      ratio: sampling.globalRate / 100,
      adaptive: { ...asRecord(samplingBase.adaptive), enabled: sampling.adaptiveSampling },
      alwaysSample: [...sampling.alwaysSamplePaths],
      neverSample: [...sampling.neverSamplePaths],
      serviceRatios
    },
    exporter: {
      ...exporterBase,
      type: exporter.type,
      logging: { ...asRecord(exporterBase.logging), enabled: exporter.loggingEnabled },
      otlp: { ...asRecord(exporterBase.otlp), endpoint: exporter.otlpEndpoint },
      jaeger: { ...asRecord(exporterBase.jaeger), endpoint: exporter.jaegerEndpoint }
    },
    performance: {
      ...performanceBase,
      asyncProcessing: asyncProcessing ?? performance.asyncProcessing,
      threadPool: { ...asRecord(performanceBase.threadPool), coreSize: performance.threadPoolCoreSize },
      buffer: { ...asRecord(performanceBase.buffer), size: performance.bufferSize },
      memory: { ...asRecord(performanceBase.memory), memoryLimitMb: performance.memoryLimitMb }
    }
  }
}

const submitSnapshot = async (options: SnapshotOptions = {}): Promise<void> => {
  await updateTracingConfig(buildSnapshot(options) as unknown as Partial<TracingConfig>)
}

/** 开关即时生效：失败时抛错，SettingsSwitch 会让开关回弹。 */
const submitTracingEnabled = async (next: boolean): Promise<void> => {
  if (next) {
    await enableTracing()
    ElMessage.success(t('tracing.management.messages.tracingEnabled'))
  } else {
    await disableTracing()
    ElMessage.success(t('tracing.management.messages.tracingDisabled'))
  }
  await loadStatus()
}

const submitAsyncProcessing = async (next: boolean): Promise<void> => {
  await submitSnapshot({ asyncProcessing: next, useDraft: false })
  performanceForm.asyncProcessing = next
  performanceBaseline.asyncProcessing = next
  ElMessage.success(t('tracing.management.messages.configSaved'))
}

const { saving, savingLabel, errors, dirtyCount, save } = useConfigDraft(
  [
    {
      key: 'sampling',
      label: () => t('tracing.management.samplingStrategy'),
      isDirty: samplingDirty,
      save: async () => {
        await submitSnapshot()
        await refreshSamplingStrategy()
      },
      commit: copySamplingBaseline
    },
    {
      key: 'services',
      label: () => t('tracing.management.serviceConfigsTitle'),
      isDirty: serviceDirty,
      validate: () => {
        const names = serviceForm.rows.map((row) => row.service.trim())
        if (names.some((name) => !name)) {
          return t('tracing.management.serviceNameRequired')
        }
        if (new Set(names).size !== names.length) {
          return t('tracing.management.duplicateService')
        }
        return null
      },
      save: async () => {
        await submitSnapshot()
        await refreshSamplingStrategy()
      },
      commit: copyServiceBaseline
    },
    {
      key: 'exporter',
      label: () => t('tracing.management.tabs.exporter'),
      isDirty: exporterDirty,
      save: async () => {
        await submitSnapshot()
      },
      commit: copyExporterBaseline
    },
    {
      key: 'performance',
      label: () => t('tracing.management.performanceTitle'),
      isDirty: performanceDirty,
      save: async () => {
        await submitSnapshot()
      },
      commit: copyPerformanceBaseline
    }
  ],
  { fallbackError: () => t('tracing.management.messages.saveFailed') }
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
    ElMessage.success(t('tracing.management.messages.configSaved'))
    await loadConfig()
    await loadStatus()
  }
}

// 清缓存会丢弃所有正在采样的链路数据 ⇒ 二次确认由 ConfirmAction 承担。
// 注意 refreshTracingData() 名字像只读，实际是 POST /tracing/actuator/clear-cache。
const handleClearCache = async (): Promise<void> => {
  clearingCache.value = true
  try {
    await refreshTracingData()
    ElMessage.success(t('tracing.management.messages.cacheCleared'))
  } catch (error) {
    console.error('清理追踪缓存失败:', error)
    ElMessage.error(t('tracing.management.messages.clearFailed'))
  } finally {
    clearingCache.value = false
  }
}

const handleCleanupExpired = async (): Promise<void> => {
  cleaningUp.value = true
  try {
    const hours = Math.max(1, Math.round(retentionHours.value))
    const response = await cleanupExpiredTraces(hours)
    const removed = response.data?.data?.removedCount || 0
    ElMessage.success(t('tracing.management.messages.cleanedUp', { count: removed }))
  } catch (error) {
    console.error('清理过期追踪数据失败:', error)
    ElMessage.error(t('tracing.management.messages.clearFailed'))
  } finally {
    cleaningUp.value = false
  }
}

const handleExportConfig = (): void => {
  const snapshot = buildSnapshot()
  const config = {
    sampling: snapshot.sampling,
    exporter: snapshot.exporter,
    performance: snapshot.performance
  }
  const dataStr = JSON.stringify(config, null, 2)
  const blob = new Blob([dataStr], { type: 'application/json' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = `tracing-config-${new Date().toISOString().slice(0, 10)}.json`
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  URL.revokeObjectURL(url)
  ElMessage.success(t('tracing.management.messages.configExported'))
}

const addServiceConfig = (): void => {
  serviceForm.rows.push({ service: '', rate: 10 })
}

const removeServiceConfig = (index: number): void => {
  serviceForm.rows.splice(index, 1)
}

onMounted(loadAllData)
</script>

<style scoped>
.status-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(320px, 1fr));
  gap: 20px;
}

.status-block {
  display: flex;
  flex-direction: column;
  gap: 8px;
  min-width: 0;
}

.config-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
  gap: 20px;
}

.config-grid.is-spaced {
  margin-top: 16px;
}

.config-item {
  display: flex;
  flex-direction: column;
  gap: 8px;
  min-width: 0;
}

.config-item.is-wide {
  grid-column: span 2;
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
