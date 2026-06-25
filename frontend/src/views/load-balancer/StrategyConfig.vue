<template>
  <div class="load-balancer-config">
    <!-- 全局配置卡片 -->
    <el-card class="config-card">
      <template #header>
        <div class="card-header">
          <span class="card-title">全局默认配置</span>
        </div>
      </template>

      <el-form :model="globalConfig" label-width="150px" class="config-form">
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="默认策略">
              <el-select v-model="globalConfig.type" placeholder="选择策略">
                <el-option
                  v-for="strategy in strategies"
                  :key="strategy.name"
                  :label="strategy.displayName"
                  :value="strategy.name"
                >
                  <span>{{ strategy.displayName }}</span>
                  <span style="float: right; color: #8492a6; font-size: 12px">
                    {{ strategy.description }}
                  </span>
                </el-option>
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="Hash算法" v-if="isHashStrategy(globalConfig.type)">
              <el-select v-model="globalConfig.hashAlgorithm" placeholder="选择Hash算法">
                <el-option label="MD5" value="md5" />
                <el-option label="SHA256" value="sha256" />
                <el-option label="MurmurHash" value="murmur" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20" v-if="globalConfig.type === 'consistent-hash'">
          <el-col :span="12">
            <el-form-item label="虚拟节点数">
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
          <span class="card-title">服务级配置</span>
          <el-tag type="info">点击配置按钮修改单个服务的负载均衡策略</el-tag>
        </div>
      </template>

      <el-table :data="serviceConfigs" stripe v-loading="loadingConfigs" class="flex-table">
        <el-table-column prop="serviceType" label="服务类型" min-width="120">
          <template #default="{ row }">
            <el-tag type="primary">{{ row.serviceType }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="type" label="策略" min-width="150">
          <template #default="{ row }">
            <el-tag :type="getStrategyTagType(row.type)">
              {{ getStrategyDisplayName(row.type) }}
            </el-tag>
            <el-tag v-if="row.isGlobal" type="info" size="small" style="margin-left: 8px">默认</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="hashAlgorithm" label="Hash算法" min-width="100">
          <template #default="{ row }">
            <span v-if="row.hashAlgorithm">{{ row.hashAlgorithm }}</span>
            <span v-else style="color: #909399">-</span>
          </template>
        </el-table-column>
        <el-table-column prop="virtualNodes" label="虚拟节点" min-width="80">
          <template #default="{ row }">
            <span v-if="row.virtualNodes">{{ row.virtualNodes }}</span>
            <span v-else style="color: #909399">-</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" min-width="100" fixed="right">
          <template #default="{ row }">
            <el-button size="small" type="primary" @click="showConfigDialog(row)">配置</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 配置对话框 -->
    <el-dialog
      v-model="configDialogVisible"
      :title="`配置 ${currentService?.serviceType} 负载均衡器`"
      width="500px"
    >
      <el-form :model="serviceConfig" label-width="150px">
        <el-form-item label="负载均衡策略">
          <el-select v-model="serviceConfig.type" placeholder="选择策略">
            <el-option
              v-for="strategy in strategies"
              :key="strategy.name"
              :label="strategy.displayName"
              :value="strategy.name"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="Hash算法" v-if="isHashStrategy(serviceConfig.type)">
          <el-select v-model="serviceConfig.hashAlgorithm" placeholder="选择Hash算法">
            <el-option label="MD5" value="md5" />
            <el-option label="SHA256" value="sha256" />
            <el-option label="MurmurHash" value="murmur" />
          </el-select>
        </el-form-item>
        <el-form-item label="虚拟节点数" v-if="serviceConfig.type === 'consistent-hash'">
          <el-input-number
            v-model="serviceConfig.virtualNodes"
            :min="50"
            :max="500"
            :step="50"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="configDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="saveServiceConfig" :loading="saving">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import request from '@/utils/request'

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
      { name: 'random', displayName: '随机策略', description: '按权重随机选择实例' },
      { name: 'round-robin', displayName: '轮询策略', description: '按权重轮询选择实例' },
      { name: 'least-connections', displayName: '最少连接策略', description: '选择连接数最少的实例' },
      { name: 'ip-hash', displayName: 'IP Hash策略', description: '基于客户端IP哈希选择实例' },
      { name: 'consistent-hash', displayName: '一致性哈希策略', description: '使用一致性哈希环选择实例' }
    ]
  }
}

const getStrategyDisplayName = (strategy: string) => {
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
    ElMessage.error('加载服务配置失败')
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
      ElMessage.success(`服务 ${currentService.value.serviceType} 负载均衡配置已更新`)
      configDialogVisible.value = false
      loadServiceConfigs()
    } else {
      ElMessage.error(response.data?.message || '保存配置失败')
    }
  } catch (error: any) {
    console.error('Failed to save service config:', error)
    ElMessage.error('保存配置失败')
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
.load-balancer-config {
  padding: 24px;
  background: linear-gradient(180deg, #f7f9fc 0%, #ffffff 100%);
  min-height: calc(100vh - 80px);
}

.config-card,
.service-config-card {
  margin-bottom: 20px;
  box-shadow: 0 6px 20px rgba(15, 23, 42, 0.06);
  border-radius: 12px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.card-title {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
}

.config-form {
  padding: 20px;
}

.flex-table {
  width: 100%;
  table-layout: auto;
}
</style>
