<template>
  <PageSkeleton :title="t('loadBalancer.strategyConfig.pageTitle')">
    <!-- 全局配置卡片 -->
    <el-card class="config-card">
      <template #header>
        <div class="card-header">
          <span class="card-title">{{ t('loadBalancer.strategyConfig.globalTitle') }}</span>
        </div>
      </template>

      <el-form :model="globalConfig" label-width="150px" class="config-form">
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item :label="t('loadBalancer.strategyConfig.defaultStrategy')">
              <el-select
                v-model="globalConfig.type"
                :placeholder="t('loadBalancer.strategyConfig.selectStrategy')"
                style="width: 100%"
              >
                <el-option
                  v-for="strategy in strategies"
                  :key="strategy.name"
                  :label="strategyDisplayLabel(strategy)"
                  :value="strategy.name"
                >
                  <div style="display: flex; align-items: center; gap: 8px">
                    <span>{{ strategyDisplayLabel(strategy) }}</span>
                    <span
                      style="
                        margin-left: auto;
                        text-align: right;
                        color: var(--ja-text-secondary);
                        font-size: 12px;
                      "
                    >
                      {{ strategyDescriptionLabel(strategy) }}
                    </span>
                  </div>
                </el-option>
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="t('loadBalancer.strategyConfig.hashAlgorithm')" v-if="isHashStrategy(globalConfig.type)">
              <el-select v-model="globalConfig.hashAlgorithm" :placeholder="t('loadBalancer.strategyConfig.selectHashAlgorithm')">
                <el-option label="MD5" value="md5" />
                <el-option label="SHA256" value="sha256" />
                <el-option label="MurmurHash" value="murmur" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20" v-if="globalConfig.type === 'consistent-hash'">
          <el-col :span="12">
            <el-form-item :label="t('loadBalancer.strategyConfig.virtualNodes')">
              <el-input-number
                v-model="globalConfig.virtualNodes"
                :min="50"
                :max="500"
                :step="50"
              />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
    </el-card>

    <!-- 服务级配置表格 -->
    <el-card class="service-config-card">
      <template #header>
        <div class="card-header">
          <span class="card-title">{{ t('loadBalancer.strategyConfig.serviceConfigsTitle') }}</span>
          <el-tag type="info">{{ t('loadBalancer.strategyConfig.configureHint') }}</el-tag>
        </div>
      </template>

      <el-table :data="serviceConfigs" stripe v-loading="loadingConfigs" class="flex-table">
        <el-table-column prop="serviceType" :label="t('loadBalancer.strategyConfig.columns.serviceType')" min-width="120">
          <template #default="{ row }">
            <el-tag type="primary">{{ row.serviceType }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="type" :label="t('loadBalancer.strategyConfig.columns.strategy')" min-width="150">
          <template #default="{ row }">
            <el-tag :type="getStrategyTagType(row.type)">
              {{ getStrategyDisplayName(row.type) }}
            </el-tag>
            <el-tag v-if="row.isGlobal" type="info" size="small" style="margin-left: 8px">{{ t('loadBalancer.strategyConfig.defaultTag') }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="hashAlgorithm" :label="t('loadBalancer.strategyConfig.columns.hashAlgorithm')" min-width="100">
          <template #default="{ row }">
            <span v-if="row.hashAlgorithm">{{ row.hashAlgorithm }}</span>
            <span v-else style="color: var(--ja-text-secondary)">-</span>
          </template>
        </el-table-column>
        <el-table-column prop="virtualNodes" :label="t('loadBalancer.strategyConfig.columns.virtualNodes')" min-width="80">
          <template #default="{ row }">
            <span v-if="row.virtualNodes">{{ row.virtualNodes }}</span>
            <span v-else style="color: var(--ja-text-secondary)">-</span>
          </template>
        </el-table-column>
        <el-table-column :label="t('loadBalancer.strategyConfig.columns.actions')" min-width="100" fixed="right">
          <template #default="{ row }">
            <el-button size="small" type="primary" @click="showConfigDialog(row)">{{ t('loadBalancer.strategyConfig.configure') }}</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 配置对话框 -->
    <el-dialog
      v-model="configDialogVisible"
      :title="t('loadBalancer.strategyConfig.configureDialogTitle', { serviceType: currentService?.serviceType })"
      width="500px"
    >
      <el-form :model="serviceConfig" label-width="150px">
        <el-form-item :label="t('loadBalancer.strategyConfig.loadBalanceStrategy')">
          <el-select
            v-model="serviceConfig.type"
            :placeholder="t('loadBalancer.strategyConfig.selectStrategy')"
            style="width: 100%"
          >
            <el-option
              v-for="strategy in strategies"
              :key="strategy.name"
              :label="strategyDisplayLabel(strategy)"
              :value="strategy.name"
            />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('loadBalancer.strategyConfig.hashAlgorithm')" v-if="isHashStrategy(serviceConfig.type)">
          <el-select v-model="serviceConfig.hashAlgorithm" :placeholder="t('loadBalancer.strategyConfig.selectHashAlgorithm')">
            <el-option label="MD5" value="md5" />
            <el-option label="SHA256" value="sha256" />
            <el-option label="MurmurHash" value="murmur" />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('loadBalancer.strategyConfig.virtualNodes')" v-if="serviceConfig.type === 'consistent-hash'">
          <el-input-number
            v-model="serviceConfig.virtualNodes"
            :min="50"
            :max="500"
            :step="50"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="configDialogVisible = false">{{ t('loadBalancer.strategyConfig.cancel') }}</el-button>
        <el-button type="primary" @click="saveServiceConfig" :loading="saving">{{ t('loadBalancer.strategyConfig.save') }}</el-button>
      </template>
    </el-dialog>
  </PageSkeleton>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import request from '@/utils/request'
import PageSkeleton from '@/components/PageSkeleton.vue'

interface StrategyInfo {
  name: string
  displayName: string
  description: string
}

interface ServiceConfig {
  serviceType: string
  type: string
  hashAlgorithm?: string
  virtualNodes?: number
  isGlobal?: boolean
}

interface LoadBalanceConfig {
  type: string
  hashAlgorithm: string
  virtualNodes: number
}

const apiBaseUrl = '/loadbalancer'

const { t } = useI18n()

/** 策略名(如 round-robin) -> 语言包 key 后缀 */
const strategyNameKeys: Record<string, string> = {
  random: 'random',
  'round-robin': 'roundRobin',
  'least-connections': 'leastConnections',
  'ip-hash': 'ipHash',
  'consistent-hash': 'consistentHash',
  latency: 'latency'
}

const getStrategyNameKey = (name: string) => strategyNameKeys[name] || null

const strategyDisplayLabel = (strategy: StrategyInfo) => {
  const key = getStrategyNameKey(strategy.name)
  return key ? t(`loadBalancer.strategyConfig.strategies.${key}`) : strategy.displayName
}

const strategyDescriptionLabel = (strategy: StrategyInfo) => {
  const key = getStrategyNameKey(strategy.name)
  return key ? t(`loadBalancer.strategyConfig.strategyDescriptions.${key}`) : strategy.description
}

const strategies = ref<StrategyInfo[]>([])
const globalConfig = ref<LoadBalanceConfig>({
  type: 'random',
  hashAlgorithm: 'md5',
  virtualNodes: 150
})
const serviceConfigs = ref<ServiceConfig[]>([])
const loadingConfigs = ref(false)
const configDialogVisible = ref(false)
const saving = ref(false)
const currentService = ref<ServiceConfig | null>(null)
const serviceConfig = ref<LoadBalanceConfig>({
  type: 'random',
  hashAlgorithm: 'md5',
  virtualNodes: 150
})

const isHashStrategy = (strategy: string) => {
  return strategy === 'ip-hash' || strategy === 'consistent-hash'
}

const loadStrategies = async () => {
  try {
    const response = await request.get(`${apiBaseUrl}/strategies`)
    if (response.data?.success) {
      strategies.value = response.data.data
    }
  } catch (error: any) {
    console.error('Failed to load strategies:', error)
    strategies.value = [
      { name: 'random', displayName: t('loadBalancer.strategyConfig.strategies.random'), description: t('loadBalancer.strategyConfig.strategyDescriptions.random') },
      { name: 'round-robin', displayName: t('loadBalancer.strategyConfig.strategies.roundRobin'), description: t('loadBalancer.strategyConfig.strategyDescriptions.roundRobin') },
      { name: 'least-connections', displayName: t('loadBalancer.strategyConfig.strategies.leastConnections'), description: t('loadBalancer.strategyConfig.strategyDescriptions.leastConnections') },
      { name: 'ip-hash', displayName: t('loadBalancer.strategyConfig.strategies.ipHash'), description: t('loadBalancer.strategyConfig.strategyDescriptions.ipHash') },
      { name: 'consistent-hash', displayName: t('loadBalancer.strategyConfig.strategies.consistentHash'), description: t('loadBalancer.strategyConfig.strategyDescriptions.consistentHash') },
      { name: 'latency', displayName: t('loadBalancer.strategyConfig.strategies.latency'), description: t('loadBalancer.strategyConfig.strategyDescriptions.latency') }
    ]
  }
}

const getStrategyDisplayName = (strategy: string) => {
  const key = getStrategyNameKey(strategy)
  if (key) return t(`loadBalancer.strategyConfig.strategies.${key}`)
  const found = strategies.value.find(s => s.name === strategy)
  return found ? found.displayName : strategy
}

const getStrategyTagType = (strategy: string) => {
  switch (strategy) {
    case 'random':
      return 'info'
    case 'round-robin':
      return 'primary'
    case 'least-connections':
      return 'success'
    case 'ip-hash':
      return 'warning'
    case 'consistent-hash':
      return 'danger'
    default:
      return ''
  }
}

const loadGlobalConfig = async () => {
  try {
    const response = await request.get(`${apiBaseUrl}/config/global`)
    if (response.data?.success) {
      globalConfig.value = response.data.data
    }
  } catch (error: any) {
    console.error('Failed to load global config:', error)
  }
}

const loadServiceConfigs = async () => {
  loadingConfigs.value = true
  try {
    const statusResponse = await request.get(`${apiBaseUrl}/status`)
    if (statusResponse.data?.success) {
      const services = statusResponse.data.data
      const configPromises = services.map(async (service: any) => {
        try {
          const configResponse = await request.get(`${apiBaseUrl}/config/${service.serviceType}`)
          if (configResponse.data?.success) {
            return configResponse.data.data
          }
        } catch (error) {
          console.error(`Failed to load config for ${service.serviceType}:`, error)
        }
        return null
      })
      const configs = await Promise.all(configPromises)
      serviceConfigs.value = configs.filter((c): c is ServiceConfig => c !== null)
    }
  } catch (error: any) {
    console.error('Failed to load service configs:', error)
    ElMessage.error(t('loadBalancer.strategyConfig.messages.loadFailed'))
  } finally {
    loadingConfigs.value = false
  }
}

const showConfigDialog = async (service: ServiceConfig) => {
  currentService.value = service
  serviceConfig.value = {
    type: service.type || globalConfig.value.type,
    hashAlgorithm: service.hashAlgorithm || globalConfig.value.hashAlgorithm,
    virtualNodes: service.virtualNodes || globalConfig.value.virtualNodes
  }
  configDialogVisible.value = true
}

const saveServiceConfig = async () => {
  if (!currentService.value) return

  saving.value = true
  try {
    const response = await request.put(
      `${apiBaseUrl}/config/${currentService.value.serviceType}`,
      serviceConfig.value
    )
    if (response.data?.success) {
      ElMessage.success(t('loadBalancer.strategyConfig.messages.configUpdated', { serviceType: currentService.value.serviceType }))
      configDialogVisible.value = false
      loadServiceConfigs()
    } else {
      ElMessage.error(response.data?.message || t('loadBalancer.strategyConfig.messages.saveFailed'))
    }
  } catch (error: any) {
    console.error('Failed to save service config:', error)
    ElMessage.error(t('loadBalancer.strategyConfig.messages.saveFailed'))
  } finally {
    saving.value = false
  }
}

onMounted(() => {
  loadStrategies()
  loadGlobalConfig()
  loadServiceConfigs()
})
</script>

<style scoped>
.config-card,
.service-config-card {
  margin-bottom: 20px;
  box-shadow: var(--ja-shadow-lg);
  border-radius: var(--ja-radius-lg);
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.card-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--ja-text-primary);
}

.config-form {
  padding: 20px;
}

.flex-table {
  width: 100%;
  table-layout: auto;
}
</style>
