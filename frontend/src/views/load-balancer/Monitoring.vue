<template>
  <div class="load-balancer-monitoring">
    <!-- 统计卡片 -->
    <el-row :gutter="20" style="margin-bottom: 20px">
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-item">
            <div class="stat-value">{{ stats.totalLoadBalancers }}</div>
            <div class="stat-label">服务总数</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-item">
            <div class="stat-value">
              <el-tag :type="stats.validationStatus ? 'success' : 'danger'" size="large">
                {{ stats.validationStatus ? '有效' : '无效' }}
              </el-tag>
            </div>
            <div class="stat-label">配置验证</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card shadow="hover" class="stat-card">
          <div class="strategy-distribution">
            <span class="stat-label" style="margin-bottom: 8px">策略分布</span>
            <div class="distribution-tags">
              <el-tag
                v-for="(count, strategy) in stats.strategyDistribution"
                :key="strategy"
                :type="getStrategyTagType(strategy)"
                style="margin: 4px"
              >
                {{ getStrategyDisplayName(strategy) }}: {{ count }}
              </el-tag>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 状态监控表格 -->
    <el-card class="status-card">
      <template #header>
        <div class="card-header">
          <span class="card-title">负载均衡器状态监控</span>
          <el-button type="primary" @click="refreshStatus" :loading="loadingStatus">刷新状态</el-button>
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
        <el-table-column prop="loadBalancerClass" label="实现类" min-width="220" show-overflow-tooltip />
        <el-table-column prop="configType" label="配置类型" min-width="120">
          <template #default="{ row }">
            <span v-if="row.configType">{{ row.configType }}</span>
            <span v-else style="color: #909399">-</span>
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
        <el-table-column label="操作" min-width="120" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="viewDetails(row)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 详情对话框 -->
    <el-dialog
      v-model="detailsDialogVisible"
      :title="`${currentService?.serviceType} 负载均衡器详情`"
      width="600px"
    >
      <el-descriptions :column="2" border>
        <el-descriptions-item label="服务类型">{{ currentService?.serviceType }}</el-descriptions-item>
        <el-descriptions-item label="当前策略">{{ getStrategyDisplayName(currentService?.strategy || '') }}</el-descriptions-item>
        <el-descriptions-item label="实现类" :span="2">{{ currentService?.loadBalancerClass }}</el-descriptions-item>
        <el-descriptions-item label="配置类型">{{ currentService?.configType || '-' }}</el-descriptions-item>
        <el-descriptions-item label="Hash算法">{{ currentService?.hashAlgorithm || '-' }}</el-descriptions-item>
        <el-descriptions-item label="虚拟节点数">{{ currentService?.virtualNodes || '-' }}</el-descriptions-item>
      </el-descriptions>
      <template #footer>
        <el-button @click="detailsDialogVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import request from '@/utils/request'

interface LoadBalancerStatus {
  serviceType: string
  strategy: string
  loadBalancerClass: string
  configType?: string
  hashAlgorithm?: string
  virtualNodes?: number
}

interface LoadBalancerStats {
  totalLoadBalancers: number
  validationStatus: boolean
  strategyDistribution: Record<string, number>
}

interface StrategyInfo {
  name: string
  displayName: string
  description: string
}

const apiBaseUrl = '/loadbalancer'

const strategies = ref<StrategyInfo[]>([])
const loadBalancerStatus = ref<LoadBalancerStatus[]>([])
const stats = ref<LoadBalancerStats>({
  totalLoadBalancers: 0,
  validationStatus: true,
  strategyDistribution: {}
})

const loadingStatus = ref(false)
const detailsDialogVisible = ref(false)
const currentService = ref<LoadBalancerStatus | null>(null)

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
  loadLoadBalancerStatus()
  loadStats()
})
</script>

<style scoped>
.load-balancer-monitoring {
  padding: 24px;
  background: linear-gradient(180deg, #f7f9fc 0%, #ffffff 100%);
  min-height: calc(100vh - 80px);
}

.stat-card {
  height: 100%;
}

.stat-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 16px;
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
  display: flex;
  flex-direction: column;
  align-items: center;
}

.distribution-tags {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
}

.status-card {
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

.flex-table {
  width: 100%;
  table-layout: auto;
}
</style>
