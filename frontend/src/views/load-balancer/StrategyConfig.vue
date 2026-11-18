<template>
  <ConfigPageShell
    :title="t('loadBalancer.strategyConfig.pageTitle')"
    :description="t('loadBalancer.strategyConfig.pageDesc')"
    :loading="loading"
    :saving="saving"
    :saving-label="savingLabel"
    :dirty-count="dirtyCount"
    :can-write="canWrite"
    save-permission="lb:config:write"
    @refresh="loadAll"
    @save="handleSave"
    @discard="resetRows"
  >
        <section class="config-group">
      <h3 class="config-group-title">{{ t('loadBalancer.strategyConfig.tabs.global') }}</h3>
        <ConfigSectionCard
          :title="t('loadBalancer.strategyConfig.globalTitle')"
          :description="t('loadBalancer.strategyConfig.globalReadonlyHint')"
        >
          <template #actions>
            <el-tag size="small" type="info" effect="plain">{{ t('loadBalancer.strategyConfig.readonlyTag') }}</el-tag>
          </template>
          <el-descriptions :column="3" border size="small">
            <el-descriptions-item :label="t('loadBalancer.strategyConfig.defaultStrategy')">
              {{ getStrategyDisplayName(globalConfig.type) }}
            </el-descriptions-item>
            <el-descriptions-item :label="t('loadBalancer.strategyConfig.hashAlgorithm')">
              {{ isHashStrategy(globalConfig.type) ? globalConfig.hashAlgorithm || '-' : '-' }}
            </el-descriptions-item>
            <el-descriptions-item :label="t('loadBalancer.strategyConfig.virtualNodes')">
              {{ globalConfig.type === 'consistent-hash' ? globalConfig.virtualNodes : '-' }}
            </el-descriptions-item>
          </el-descriptions>
        </ConfigSectionCard>
    </section>

    <section class="config-group">
      <h3 class="config-group-title">{{ t('loadBalancer.strategyConfig.tabs.services') }}</h3>
        <ConfigSectionCard
          :title="t('loadBalancer.strategyConfig.serviceConfigsTitle')"
          :description="t('loadBalancer.strategyConfig.configureHint')"
          :error="errors.services"
        >
          <el-table :data="rows" v-loading="loadingConfigs" style="width: 100%">
            <el-table-column :label="t('loadBalancer.strategyConfig.columns.serviceType')" min-width="200">
              <template #default="{ row }">
                <div class="service-cell">
                  <el-tag type="info">{{ row.serviceType }}</el-tag>
                  <el-tag v-if="row.isGlobal" size="small" type="info" effect="plain">
                    {{ t('loadBalancer.strategyConfig.defaultTag') }}
                  </el-tag>
                </div>
              </template>
            </el-table-column>
            <el-table-column :label="t('loadBalancer.strategyConfig.columns.strategy')" min-width="220">
              <template #default="{ row }">
                <el-select v-model="row.type" :disabled="!canWrite" style="width: 100%">
                  <el-option
                    v-for="strategy in strategies"
                    :key="strategy.name"
                    :label="strategyDisplayLabel(strategy)"
                    :value="strategy.name"
                  />
                </el-select>
              </template>
            </el-table-column>
            <el-table-column :label="t('loadBalancer.strategyConfig.columns.hashAlgorithm')" width="160">
              <template #default="{ row }">
                <el-select v-if="isHashStrategy(row.type)" v-model="row.hashAlgorithm" :disabled="!canWrite">
                  <el-option label="MD5" value="md5" />
                  <el-option label="SHA256" value="sha256" />
                  <el-option label="MurmurHash" value="murmur" />
                </el-select>
                <span v-else class="muted">-</span>
              </template>
            </el-table-column>
            <el-table-column :label="t('loadBalancer.strategyConfig.columns.virtualNodes')" width="170">
              <template #default="{ row }">
                <el-input-number
                  v-if="row.type === 'consistent-hash'"
                  v-model="row.virtualNodes"
                  :min="50"
                  :max="500"
                  :step="50"
                  :disabled="!canWrite"
                  controls-position="right"
                />
                <span v-else class="muted">-</span>
              </template>
            </el-table-column>
          </el-table>
          <p class="save-hint">{{ t('loadBalancer.strategyConfig.saveHint') }}</p>
        </ConfigSectionCard>
    </section>
  </ConfigPageShell>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import ConfigPageShell from '@/components/config/ConfigPageShell.vue'
import ConfigSectionCard from '@/components/config/ConfigSectionCard.vue'
import { usePermission } from '@/composables/usePermission'
import { useConfigDraft } from '@/composables/useConfigDraft'
import request from '@/utils/request'

interface StrategyInfo {
  name: string
  displayName: string
  description: string
}

/** 表格行即一个服务的「生效负载均衡配置」草稿。 */
interface ServiceRow {
  serviceType: string
  type: string
  hashAlgorithm: string
  virtualNodes: number
  ewmaAlpha?: number
  /** 后端当前没有服务级配置、行内值取自全局默认（issue #575 的兜底行）。 */
  isGlobal: boolean
}

interface LoadBalanceConfig {
  type: string
  hashAlgorithm: string
  virtualNodes: number
  ewmaAlpha?: number
}

const apiBaseUrl = '/loadbalancer'

const { t } = useI18n()
const { hasPermission } = usePermission()

/** 与原页面一致：写操作走 lb:config:write。 */
const canWrite = computed(() => hasPermission('lb:config:write'))

const loadingStrategies = ref(false)
const loadingGlobal = ref(false)
const loadingConfigs = ref(false)
const loading = computed(() => loadingStrategies.value || loadingGlobal.value || loadingConfigs.value)
const strategies = ref<StrategyInfo[]>([])
const globalConfig = ref<LoadBalanceConfig>({ type: 'random', hashAlgorithm: 'md5', virtualNodes: 150 })
const globalConfigLoaded = ref(false)
const rows = ref<ServiceRow[]>([])
/** 基线：按 serviceType 索引的已保存值，dirty 判定与「放弃修改」都以它为准。 */
const baseline = ref<Record<string, ServiceRow>>({})

const strategyNameKeys: Record<string, string> = {
  random: 'random',
  'round-robin': 'roundRobin',
  'least-connections': 'leastConnections',
  'ip-hash': 'ipHash',
  'consistent-hash': 'consistentHash',
  latency: 'latency'
}

const getStrategyNameKey = (strategy: string): string => strategyNameKeys[strategy] || strategy

const strategyDisplayLabel = (strategy: StrategyInfo): string => {
  const key = getStrategyNameKey(strategy.name)
  const label = t(`loadBalancer.strategyConfig.strategies.${key}`)
  return label === `loadBalancer.strategyConfig.strategies.${key}` ? strategy.displayName : label
}

const getStrategyDisplayName = (strategy: string): string => {
  const key = getStrategyNameKey(strategy)
  const label = t(`loadBalancer.strategyConfig.strategies.${key}`)
  return label === `loadBalancer.strategyConfig.strategies.${key}` ? strategy : label
}

const isHashStrategy = (strategy: string): boolean => strategy === 'ip-hash' || strategy === 'consistent-hash'

const getResponseStatus = (error: unknown): number | undefined => {
  if (typeof error !== 'object' || error === null) {
    return undefined
  }
  return (error as { response?: { status?: number } }).response?.status
}

const localStrategyFallbacks = (): StrategyInfo[] => [
  { name: 'random', displayName: t('loadBalancer.strategyConfig.strategies.random'), description: t('loadBalancer.strategyConfig.strategyDescriptions.random') },
  { name: 'round-robin', displayName: t('loadBalancer.strategyConfig.strategies.roundRobin'), description: t('loadBalancer.strategyConfig.strategyDescriptions.roundRobin') },
  { name: 'least-connections', displayName: t('loadBalancer.strategyConfig.strategies.leastConnections'), description: t('loadBalancer.strategyConfig.strategyDescriptions.leastConnections') },
  { name: 'ip-hash', displayName: t('loadBalancer.strategyConfig.strategies.ipHash'), description: t('loadBalancer.strategyConfig.strategyDescriptions.ipHash') },
  { name: 'consistent-hash', displayName: t('loadBalancer.strategyConfig.strategies.consistentHash'), description: t('loadBalancer.strategyConfig.strategyDescriptions.consistentHash') },
  { name: 'latency', displayName: t('loadBalancer.strategyConfig.strategies.latency'), description: t('loadBalancer.strategyConfig.strategyDescriptions.latency') }
]

const loadStrategies = async (): Promise<void> => {
  loadingStrategies.value = true
  try {
    const response = await request.get(`${apiBaseUrl}/strategies`)
    if (response.data?.success) {
      strategies.value = response.data.data
    } else {
      strategies.value = localStrategyFallbacks()
    }
  } catch (error) {
    console.error('加载策略列表失败:', error)
    strategies.value = localStrategyFallbacks()
  } finally {
    loadingStrategies.value = false
  }
}

const loadGlobalConfig = async (): Promise<void> => {
  loadingGlobal.value = true
  try {
    const response = await request.get(`${apiBaseUrl}/config/global`)
    if (response.data?.success) {
      globalConfig.value = response.data.data
    }
  } catch (error) {
    console.error('加载全局配置失败:', error)
  } finally {
    globalConfigLoaded.value = true
    loadingGlobal.value = false
  }
}

const toRow = (serviceType: string, config: Partial<ServiceRow>, isGlobal: boolean): ServiceRow => ({
  serviceType,
  type: config.type || globalConfig.value.type || 'random',
  hashAlgorithm: config.hashAlgorithm || globalConfig.value.hashAlgorithm || 'md5',
  virtualNodes: config.virtualNodes ?? globalConfig.value.virtualNodes ?? 150,
  ewmaAlpha: config.ewmaAlpha,
  isGlobal
})

const cloneRow = (row: ServiceRow): ServiceRow => ({ ...row })

const loadServiceConfigs = async (): Promise<void> => {
  loadingConfigs.value = true
  try {
    if (!globalConfigLoaded.value) {
      await loadGlobalConfig()
    }
    const statusResponse = await request.get(`${apiBaseUrl}/status`)
    const services: Array<{ serviceType: string }> = statusResponse.data?.data || []
    const configs = await Promise.all(
      services.map(async (service) => {
        try {
          const response = await request.get(`${apiBaseUrl}/config/${service.serviceType}`)
          if (response.data?.success) {
            return toRow(service.serviceType, response.data.data as Partial<ServiceRow>, response.data.data?.isGlobal === true)
          }
          return null
        } catch (error) {
          // 该服务没有服务级配置时后端返回 404：用全局默认值补一行，避免表格缺行（issue #575）。
          if (getResponseStatus(error) === 404) {
            return toRow(service.serviceType, globalConfig.value, true)
          }
          console.error(`加载服务 ${service.serviceType} 配置失败:`, error)
          return null
        }
      })
    )
    rows.value = configs.filter((row): row is ServiceRow => row !== null)
    baseline.value = rows.value.reduce<Record<string, ServiceRow>>((acc, row) => {
      acc[row.serviceType] = cloneRow(row)
      return acc
    }, {})
  } catch (error) {
    console.error('加载服务配置失败:', error)
    ElMessage.error(t('loadBalancer.strategyConfig.messages.loadFailed'))
  } finally {
    loadingConfigs.value = false
  }
}

const loadAll = async (): Promise<void> => {
  await loadStrategies()
  await loadGlobalConfig()
  await loadServiceConfigs()
}

const sameRow = (left: ServiceRow, right: ServiceRow | undefined): boolean => {
  if (!right) {
    return false
  }
  return left.type === right.type
    && left.hashAlgorithm === right.hashAlgorithm
    && left.virtualNodes === right.virtualNodes
    && (left.ewmaAlpha ?? null) === (right.ewmaAlpha ?? null)
}

const dirtyRows = (): ServiceRow[] => rows.value.filter((row) => !sameRow(row, baseline.value[row.serviceType]))

const resetRows = (): void => {
  rows.value = Object.values(baseline.value).map(cloneRow)
}

/** PUT /config/{serviceType} 是整体替换（缺 hashAlgorithm / virtualNodes 会回落 md5 / 150），故显式带全字段。 */
const buildRowPayload = (row: ServiceRow): Record<string, unknown> => {
  const payload: Record<string, unknown> = {
    type: row.type,
    hashAlgorithm: row.hashAlgorithm,
    virtualNodes: row.virtualNodes
  }
  if (typeof row.ewmaAlpha === 'number') {
    payload.ewmaAlpha = row.ewmaAlpha
  }
  return payload
}

/** 本轮提交成功的服务类型：只有它们才能把「默认」标记摘掉（未保存的行仍是全局默认）。 */
let submittedTypes: string[] = []

const { saving, savingLabel, errors, dirtyCount, save } = useConfigDraft(
  [
    {
      key: 'services',
      label: () => t('loadBalancer.strategyConfig.serviceConfigsTitle'),
      isDirty: () => dirtyRows().length > 0,
      save: async () => {
        submittedTypes = []
        for (const row of dirtyRows()) {
          const response = await request.put(`${apiBaseUrl}/config/${row.serviceType}`, buildRowPayload(row))
          if (response.data?.success === false) {
            throw new Error(response.data?.message || t('loadBalancer.strategyConfig.messages.saveFailed'))
          }
          submittedTypes.push(row.serviceType)
        }
      },
      commit: () => {
        submittedTypes.forEach((serviceType) => {
          const row = rows.value.find((item) => item.serviceType === serviceType)
          if (row) {
            row.isGlobal = false
            baseline.value[serviceType] = cloneRow(row)
          }
        })
        submittedTypes = []
      }
    }
  ],
  { fallbackError: () => t('loadBalancer.strategyConfig.messages.saveFailed') }
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
    ElMessage.success(t('loadBalancer.strategyConfig.messages.saved'))
    await loadServiceConfigs()
  }
}

onMounted(loadAll)
</script>

<style scoped>
.service-cell {
  display: flex;
  align-items: center;
  gap: 8px;
}

.muted {
  color: var(--el-text-color-secondary);
}

.save-hint {
  margin: 12px 0 0;
  font-size: 12px;
  line-height: 1.6;
  color: var(--el-text-color-secondary);
}
</style>
