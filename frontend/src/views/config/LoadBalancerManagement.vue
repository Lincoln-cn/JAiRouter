<template>
  <div class="load-balancer-management">
    <!-- 全局配置卡片 -->
    <el-card class="config-card">
      <template #header>
        <div class="card-header">
          <span class="card-title">负载均衡器全局配置</span>
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

        <el-form-item>
          <el-button type="primary" @click="refreshStatus">刷新状态</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 状态监控卡片 -->
    <el-card class="status-card">
      <template #header>
        <div class="card-header">
          <span class="card-title">负载均衡器状态监控</span>
          <el-tag type="info">共 {{ loadBalancerStatus.length }} 个服务</el-tag>
        </div>
      </template>

      <el-table :data="loadBalancerStatus" stripe v-loading="loadingStatus" class="flex-table">
        <el-table-column prop="serviceType" label="服务类型" min-width="120">
          <template #default="{ row }">
            <el-tag type="primary">{{ row.serviceType }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="strategy" label="当前策略" min-width="150">
          <template #default="{ row }">
            <el-tag :type="getStrategyTagType(row.strategy)">
              {{ getStrategyDisplayName(row.strategy) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="loadBalancerClass" label="实现类" min-width="200" />
        <el-table-column prop="configType" label="配置类型" min-width="120">
          <template #default="{ row }">
            <span v-if="row.configType">{{ row.configType }}</span>
            <span v-else style="color: #909399">-</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" min-width="180">
          <template #default="{ row }">
            <el-button size="small" type="primary" @click="showConfigDialog(row)">
              配置
            </el-button>
            <el-button size="small" @click="viewDetails(row)">
              详情
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 统计信息卡片 -->
    <el-card class="stats-card">
      <template #header>
        <div class="card-header">
          <span class="card-title">负载均衡器统计</span>
        </div>
      </template>

      <el-row :gutter="20">
        <el-col :span="6">
          <div class="stat-item">
            <div class="stat-value">{{ stats.totalLoadBalancers }}</div>
            <div class="stat-label">总负载均衡器数</div>
          </div>
        </el-col>
        <el-col :span="6">
          <div class="stat-item">
            <div class="stat-value">
              <el-tag :type="stats.validationStatus ? 'success' : 'danger'">
                {{ stats.validationStatus ? '有效' : '无效' }}
              </el-tag>
            </div>
            <div class="stat-label">配置验证状态</div>
          </div>
        </el-col>
        <el-col :span="12">
          <div class="stat-item">
            <div class="strategy-distribution">
              <span class="stat-label" style="margin-bottom: 8px">策略分布：</span>
              <div class="distribution-charts">
                <el-tag
                  v-for="(count, strategy) in stats.strategyDistribution"
                  :key="strategy"
                  :type="getStrategyTagType(strategy)"
                  style="margin-right: 8px"
                >
                  {{ getStrategyDisplayName(strategy) }}: {{ count }}
                </el-tag>
              </div>
            </div>
          </div>
        </el-col>
      </el-row>
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
        <el-button type="primary" @click="saveServiceConfig">保存</el-button>
      </template>
    </el-dialog>

    <!-- 详情对话框 -->
    <el-dialog
      v-model="detailsDialogVisible"
      :title="`${currentService?.serviceType} 负载均衡器详情`"
      width="600px"
    >
      <el-descriptions :column="2" border>
        <el-descriptions-item label="服务类型">{{ currentService?.serviceType }}</el-descriptions-item>
        <el-descriptions-item label="当前策略">{{ getStrategyDisplayName(currentService?.strategy || '') }}</el-descriptions-item>
        <el-descriptions-item label="实现类">{{ currentService?.loadBalancerClass }}</el-descriptions-item>
        <el-descriptions-item label="配置类型">{{ currentService?.configType || '-' }}</el-descriptions-item>
        <el-descriptions-item label="Hash算法" v-if="currentService?.hashAlgorithm">
          {{ currentService?.hashAlgorithm }}
        </el-descriptions-item>
        <el-descriptions-item label="虚拟节点数" v-if="currentService?.virtualNodes">
          {{ currentService?.virtualNodes }}
        </el-descriptions-item>
      </el-descriptions>
      <template #footer>
        <el-button @click="detailsDialogVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { ElMessage } from 'element-plus'
import request from '@/utils/request'

interface StrategyInfo {
  name: string
  displayName: string
  description: string
}

interface LoadBalancerStatus {
  serviceType: string
  strategy: string
  loadBalancerClass: string
  configType?: string
  hashAlgorithm?: string
  virtualNodes?: number
}

interface LoadBalanceConfig {
  type: string
  hashAlgorithm: string
  virtualNodes: number
}

interface LoadBalancerStats {
  totalLoadBalancers: number
  validationStatus: boolean
  strategyDistribution: Record<string, number>
}

const apiBaseUrl = '/loadbalancer'

const strategies = ref<StrategyInfo[]>([])
const globalConfig = ref<LoadBalanceConfig>({
  type: 'random',
  hashAlgorithm: 'md5',
  virtualNodes: 150
})
const loadBalancerStatus = ref<LoadBalancerStatus[]>([])
const stats = ref<LoadBalancerStats>({
  totalLoadBalancers: 0,
  validationStatus: true,
  strategyDistribution: {}
})

const loadingStatus = ref(false)
const configDialogVisible = ref(false)
const detailsDialogVisible = ref(false)
const currentService = ref<LoadBalancerStatus | null>(null)
const serviceConfig = ref<LoadBalanceConfig>({
  type: 'random',
  hashAlgorithm: 'md5',
  virtualNodes: 150
})

const isHashStrategy = (strategy: string) => {
  return strategy === 'ip-hash' || strategy === 'consistent-hash'
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

const loadStrategies = async () => {
  try {
    const response = await request.get(`${apiBaseUrl}/strategies`)
    if (response.data?.success) {
      strategies.value = response.data.data
    }
  } catch (error: any) {
    console.error('Failed to load strategies:', error)
    // 使用默认策略列表
    strategies.value = [
      { name: 'random', displayName: '随机策略', description: '按权重随机选择实例' },
      { name: 'round-robin', displayName: '轮询策略', description: '按权重轮询选择实例' },
      { name: 'least-connections', displayName: '最少连接策略', description: '选择连接数最少的实例' },
      { name: 'ip-hash', displayName: 'IP Hash策略', description: '基于客户端IP哈希选择实例' },
      { name: 'consistent-hash', displayName: '一致性哈希策略', description: '使用一致性哈希环选择实例' }
    ]
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
    ElMessage.warning('加载全局配置失败，使用默认配置')
  }
}

const loadLoadBalancerStatus = async () => {
  loadingStatus.value = true
  try {
    const response = await request.get(`${apiBaseUrl}/status`)
    if (response.data?.success) {
      loadBalancerStatus.value = response.data.data
    }
  } catch (error: any) {
    console.error('Failed to load load balancer status:', error)
    ElMessage.error('加载负载均衡器状态失败')
  } finally {
    loadingStatus.value = false
  }
}

const loadStats = async () => {
  try {
    const response = await request.get(`${apiBaseUrl}/stats`)
    if (response.data?.success) {
      stats.value = response.data.data
    }
  } catch (error: any) {
    console.error('Failed to load stats:', error)
  }
}

const refreshStatus = () => {
  loadLoadBalancerStatus()
  loadStats()
  ElMessage.success('已刷新负载均衡器状态')
}

const showConfigDialog = async (service: LoadBalancerStatus) => {
  currentService.value = service
  try {
    const response = await request.get(`${apiBaseUrl}/config/${service.serviceType}`)
    if (response.data?.success) {
      serviceConfig.value = {
        type: response.data.data.type || 'random',
        hashAlgorithm: response.data.data.hashAlgorithm || 'md5',
        virtualNodes: response.data.data.virtualNodes || 150
      }
    }
  } catch (error: any) {
    console.error('Failed to load service config:', error)
    serviceConfig.value = { ...globalConfig.value }
  }
  configDialogVisible.value = true
}

const saveServiceConfig = async () => {
  if (!currentService.value) return

  try {
    const response = await request.put(
      `${apiBaseUrl}/config/${currentService.value.serviceType}`,
      serviceConfig.value
    )
    if (response.data?.success) {
      ElMessage.success(`服务 ${currentService.value.serviceType} 负载均衡配置已更新`)
      configDialogVisible.value = false
      loadLoadBalancerStatus()
      loadStats()
    } else {
      ElMessage.error(response.data?.message || '保存配置失败')
    }
  } catch (error: any) {
    console.error('Failed to save service config:', error)
    ElMessage.error('保存配置失败')
  }
}

const viewDetails = async (service: LoadBalancerStatus) => {
  currentService.value = service
  try {
    const response = await request.get(`${apiBaseUrl}/status/${service.serviceType}`)
    if (response.data?.success) {
      currentService.value = response.data.data
    }
  } catch (error: any) {
    console.error('Failed to load details:', error)
  }
  detailsDialogVisible.value = true
}

onMounted(() => {
  loadStrategies()
  loadGlobalConfig()
  loadLoadBalancerStatus()
  loadStats()
})
</script>

<style scoped>
.load-balancer-management {
  padding: 24px;
  background: linear-gradient(180deg, #f7f9fc 0%, #ffffff 100%);
  min-height: calc(100vh - 80px);
}

.config-card,
.status-card,
.stats-card {
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

.stat-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 20px;
  background: #f5f7fa;
  border-radius: 8px;
}

.stat-value {
  font-size: 24px;
  font-weight: 600;
  color: #409eff;
  margin-bottom: 8px;
}

.stat-label {
  font-size: 14px;
  color: #606266;
}

.strategy-distribution {
  width: 100%;
}

.distribution-charts {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
</style>