<template>
  <div class="state-persistence-management">
    <!-- 存储层状态卡片 -->
    <el-card class="tier-card">
      <template #header>
        <div class="card-header">
          <span class="card-title">存储层状态</span>
          <el-button type="primary" size="small" @click="refreshTiers">
            <el-icon><Refresh /></el-icon>
            刷新
          </el-button>
        </div>
      </template>

      <el-row :gutter="20">
        <el-col :span="8">
          <div class="tier-box" :class="{ 'tier-active': currentTier === 'redis' }">
            <div class="tier-header">
              <el-icon class="tier-icon" :class="tierHealth.redis?.healthy ? 'healthy' : 'unhealthy'">
                <component :is="tierHealth.redis?.healthy ? 'SuccessFilled' : 'CircleCloseFilled'" />
              </el-icon>
              <span class="tier-name">Redis</span>
              <el-tag v-if="currentTier === 'redis'" type="success" size="small">活跃</el-tag>
            </div>
            <div class="tier-status">
              <el-tag :type="tierHealth.redis?.healthy ? 'success' : 'danger'" size="small">
                {{ tierHealth.redis?.healthy ? 'UP' : 'DOWN' }}
              </el-tag>
            </div>
            <div class="tier-info">
              <span class="tier-priority">Tier 1 (最高优先级)</span>
              <span v-if="tierHealth.redis?.message" class="tier-message">{{ tierHealth.redis?.message }}</span>
            </div>
          </div>
        </el-col>

        <el-col :span="8">
          <div class="tier-box" :class="{ 'tier-active': currentTier === 'h2' }">
            <div class="tier-header">
              <el-icon class="tier-icon" :class="tierHealth.h2?.healthy ? 'healthy' : 'unhealthy'">
                <component :is="tierHealth.h2?.healthy ? 'SuccessFilled' : 'CircleCloseFilled'" />
              </el-icon>
              <span class="tier-name">H2 Database</span>
              <el-tag v-if="currentTier === 'h2'" type="success" size="small">活跃</el-tag>
            </div>
            <div class="tier-status">
              <el-tag :type="tierHealth.h2?.healthy ? 'success' : 'danger'" size="small">
                {{ tierHealth.h2?.healthy ? 'UP' : 'DOWN' }}
              </el-tag>
            </div>
            <div class="tier-info">
              <span class="tier-priority">Tier 2 (默认退坡)</span>
              <span v-if="tierHealth.h2?.message" class="tier-message">{{ tierHealth.h2?.message }}</span>
            </div>
          </div>
        </el-col>

        <el-col :span="8">
          <div class="tier-box" :class="{ 'tier-active': currentTier === 'file' }">
            <div class="tier-header">
              <el-icon class="tier-icon" :class="tierHealth.file?.healthy ? 'healthy' : 'unhealthy'">
                <component :is="tierHealth.file?.healthy ? 'SuccessFilled' : 'CircleCloseFilled'" />
              </el-icon>
              <span class="tier-name">File Storage</span>
              <el-tag v-if="currentTier === 'file'" type="success" size="small">活跃</el-tag>
            </div>
            <div class="tier-status">
              <el-tag :type="tierHealth.file?.healthy ? 'success' : 'danger'" size="small">
                {{ tierHealth.file?.healthy ? 'UP' : 'DOWN' }}
              </el-tag>
            </div>
            <div class="tier-info">
              <span class="tier-priority">Tier 3 (兜底)</span>
              <span v-if="tierHealth.file?.message" class="tier-message">{{ tierHealth.file?.message }}</span>
            </div>
          </div>
        </el-col>
      </el-row>

      <div class="current-tier-info">
        <el-alert
          :title="`当前活跃存储层: ${getTierDisplayName(currentTier)} (${currentTierPriority})`"
          type="info"
          :closable="false"
          show-icon
        />
      </div>
    </el-card>

    <!-- 状态统计卡片 -->
    <el-card class="stats-card">
      <template #header>
        <div class="card-header">
          <span class="card-title">状态统计</span>
        </div>
      </template>

      <el-row :gutter="20">
        <el-col :span="6">
          <div class="stat-item">
            <div class="stat-value">{{ stats.circuitBreakerCount }}</div>
            <div class="stat-label">熔断器状态</div>
          </div>
        </el-col>
        <el-col :span="6">
          <div class="stat-item">
            <div class="stat-value">{{ stats.loadBalancerCount }}</div>
            <div class="stat-label">负载均衡器状态</div>
          </div>
        </el-col>
        <el-col :span="6">
          <div class="stat-item">
            <div class="stat-value">{{ stats.rateLimiterCount }}</div>
            <div class="stat-label">限流器状态</div>
          </div>
        </el-col>
        <el-col :span="6">
          <div class="stat-item">
            <div class="stat-value">{{ stats.pendingSync }}</div>
            <div class="stat-label">待同步</div>
          </div>
        </el-col>
      </el-row>
    </el-card>

    <!-- 操作卡片 -->
    <el-card class="actions-card">
      <template #header>
        <div class="card-header">
          <span class="card-title">管理操作</span>
        </div>
      </template>

      <el-row :gutter="20">
        <el-col :span="6">
          <el-button type="primary" @click="recoverAll" :loading="recovering">
            <el-icon><RefreshRight /></el-icon>
            全部恢复
          </el-button>
        </el-col>
        <el-col :span="6">
          <el-button type="warning" @click="showSwitchTierDialog">
            <el-icon><Switch /></el-icon>
            切换存储层
          </el-button>
        </el-col>
        <el-col :span="6">
          <el-button @click="syncStates" :loading="syncing">
            <el-icon><Upload /></el-icon>
            手动同步
          </el-button>
        </el-col>
        <el-col :span="6">
          <el-button type="info" @click="loadPersistenceStatus">
            <el-icon><View /></el-icon>
            查看详情
          </el-button>
        </el-col>
      </el-row>
    </el-card>

    <!-- 状态详情列表 -->
    <el-card class="details-card">
      <template #header>
        <div class="card-header">
          <span class="card-title">状态详情列表</span>
          <el-tag type="info">共 {{ stateDetails.length }} 条</el-tag>
        </div>
      </template>

      <el-table :data="stateDetails" stripe v-loading="loadingDetails" class="flex-table">
        <el-table-column prop="instanceId" label="实例ID" min-width="180">
          <template #default="{ row }">
            <span>{{ row.instanceId || row.serviceType || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="stateType" label="类型" min-width="120">
          <template #default="{ row }">
            <el-tag :type="getStateTypeTag(row.stateType)" size="small">
              {{ getStateTypeDisplayName(row.stateType) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="state" label="状态" min-width="100">
          <template #default="{ row }">
            <el-tag :type="getStateTagType(row.state)" size="small">
              {{ row.state || '-' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="lastModified" label="最后更新" min-width="180">
          <template #default="{ row }">
            <span>{{ formatTime(row.lastModified) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" min-width="150">
          <template #default="{ row }">
            <el-button size="small" type="primary" @click="recoverSingle(row)">
              恢复
            </el-button>
            <el-button size="small" @click="viewStateDetail(row)">
              详情
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 切换存储层对话框 -->
    <el-dialog
      v-model="switchTierDialogVisible"
      title="切换存储层"
      width="400px"
    >
      <el-form label-width="100px">
        <el-form-item label="目标存储层">
          <el-select v-model="targetTier" placeholder="选择存储层">
            <el-option label="Redis (Tier 1)" value="redis" :disabled="!tierHealth.redis?.healthy" />
            <el-option label="H2 Database (Tier 2)" value="h2" :disabled="!tierHealth.h2?.healthy" />
            <el-option label="File Storage (Tier 3)" value="file" :disabled="!tierHealth.file?.healthy" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="switchTierDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="switchTier" :loading="switching">确认切换</el-button>
      </template>
    </el-dialog>

    <!-- 状态详情对话框 -->
    <el-dialog
      v-model="stateDetailDialogVisible"
      title="状态详情"
      width="600px"
    >
      <el-descriptions :column="2" border>
        <el-descriptions-item label="实例ID">{{ currentDetail?.instanceId || currentDetail?.serviceType }}</el-descriptions-item>
        <el-descriptions-item label="状态类型">{{ getStateTypeDisplayName(currentDetail?.stateType) }}</el-descriptions-item>
        <el-descriptions-item label="当前状态">{{ currentDetail?.state }}</el-descriptions-item>
        <el-descriptions-item label="最后更新">{{ formatTime(currentDetail?.lastModified) }}</el-descriptions-item>
        <el-descriptions-item label="存储层">{{ getTierDisplayName(currentDetail?.tier) }}</el-descriptions-item>
        <el-descriptions-item label="数据大小">{{ currentDetail?.dataSize || '-' }}</el-descriptions-item>
      </el-descriptions>
      <template #footer>
        <el-button @click="stateDetailDialogVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh, RefreshRight, Switch, Upload, View, SuccessFilled, CircleCloseFilled } from '@element-plus/icons-vue'
import request from '@/utils/request'

interface TierHealth {
  healthy: boolean
  message?: string
}

interface PersistenceStatus {
  currentTier: string
  tierHealth: {
    redis?: TierHealth
    h2?: TierHealth
    file?: TierHealth
  }
  stats: {
    circuitBreakerCount: number
    loadBalancerCount: number
    rateLimiterCount: number
    pendingSync: number
  }
}

interface StateDetail {
  instanceId?: string
  serviceType?: string
  stateType: string
  state: string
  lastModified: string
  tier?: string
  dataSize?: string
}

const apiBaseUrl = '/state-persistence'

const currentTier = ref('h2')
const tierHealth = ref<{
  redis?: TierHealth
  h2?: TierHealth
  file?: TierHealth
}>({
  redis: { healthy: false },
  h2: { healthy: true },
  file: { healthy: true }
})
const stats = ref({
  circuitBreakerCount: 0,
  loadBalancerCount: 0,
  rateLimiterCount: 0,
  pendingSync: 0
})
const stateDetails = ref<StateDetail[]>([])

const loadingDetails = ref(false)
const recovering = ref(false)
const syncing = ref(false)
const switching = ref(false)

const switchTierDialogVisible = ref(false)
const stateDetailDialogVisible = ref(false)
const targetTier = ref('h2')
const currentDetail = ref<StateDetail | null>(null)

const currentTierPriority = computed(() => {
  switch (currentTier.value) {
    case 'redis': return '优先级 1'
    case 'h2': return '优先级 2'
    case 'file': return '优先级 3'
    default: return '-'
  }
})

const getTierDisplayName = (tier?: string) => {
  switch (tier) {
    case 'redis': return 'Redis'
    case 'h2': return 'H2 Database'
    case 'file': return 'File Storage'
    default: return '-'
  }
}

const getStateTypeDisplayName = (type?: string) => {
  switch (type) {
    case 'CIRCUIT_BREAKER': return '熔断器'
    case 'LOAD_BALANCER': return '负载均衡器'
    case 'RATE_LIMITER': return '限流器'
    default: return type || '-'
  }
}

const getStateTypeTag = (type?: string) => {
  switch (type) {
    case 'CIRCUIT_BREAKER': return 'danger'
    case 'LOAD_BALANCER': return 'success'
    case 'RATE_LIMITER': return 'warning'
    default: return 'info'
  }
}

const getStateTagType = (state?: string) => {
  switch (state) {
    case 'CLOSED': return 'success'
    case 'OPEN': return 'danger'
    case 'HALF_OPEN': return 'warning'
    case 'active': return 'success'
    case 'inactive': return 'info'
    default: return ''
  }
}

const formatTime = (time?: string) => {
  if (!time) return '-'
  try {
    const date = new Date(time)
    return date.toLocaleString('zh-CN')
  } catch {
    return time
  }
}

const loadPersistenceStatus = async () => {
  try {
    const response = await request.get(`${apiBaseUrl}/status`)
    if (response.data?.success) {
      const data = response.data.data
      currentTier.value = data.currentTier || 'h2'
      // tierHealth 由 loadTiers() 单独获取，此处不再覆盖
      // 因为 /status 返回的是 Map<String, Boolean>，而前端需要 { healthy, message } 结构
      stats.value = data.stats || stats.value
    }
  } catch (error: any) {
    console.error('Failed to load status:', error)
    ElMessage.warning('加载状态失败，使用默认值')
  }
}

const loadTiers = async () => {
  try {
    const response = await request.get(`${apiBaseUrl}/tiers`)
    if (response.data?.success) {
      tierHealth.value = response.data.data
    }
  } catch (error: any) {
    console.error('Failed to load tiers:', error)
  }
}

const refreshTiers = async () => {
  try {
    const response = await request.post(`${apiBaseUrl}/tiers/refresh`)
    if (response.data?.success) {
      tierHealth.value = response.data.data
      ElMessage.success('存储层状态已刷新')
    }
  } catch (error: any) {
    console.error('Failed to refresh tiers:', error)
    ElMessage.error('刷新存储层状态失败')
  }
}

const loadStateDetails = async () => {
  loadingDetails.value = true
  try {
    const response = await request.get(`${apiBaseUrl}/details`)
    if (response.data?.success) {
      stateDetails.value = response.data.data || []
    }
  } catch (error: any) {
    console.error('Failed to load state details:', error)
    // 如果 API 不存在，使用模拟数据
    stateDetails.value = []
  } finally {
    loadingDetails.value = false
  }
}

const recoverAll = async () => {
  recovering.value = true
  try {
    const response = await request.post(`${apiBaseUrl}/recovery/all`)
    if (response.data?.success) {
      ElMessage.success(`状态恢复完成: ${response.data.data?.message || '成功'}`)
      loadStateDetails()
      loadPersistenceStatus()
    } else {
      ElMessage.error(response.data?.message || '恢复失败')
    }
  } catch (error: any) {
    console.error('Failed to recover all:', error)
    ElMessage.error('状态恢复失败')
  } finally {
    recovering.value = false
  }
}

const recoverSingle = async (row: StateDetail) => {
  try {
    let endpoint = ''
    if (row.stateType === 'CIRCUIT_BREAKER') {
      endpoint = `${apiBaseUrl}/recovery/circuit-breaker/${row.instanceId}`
    } else if (row.stateType === 'LOAD_BALANCER') {
      endpoint = `${apiBaseUrl}/recovery/load-balancer/${row.serviceType}`
    } else if (row.stateType === 'RATE_LIMITER') {
      endpoint = `${apiBaseUrl}/recovery/rate-limiter/${row.instanceId}`
    }

    if (!endpoint) {
      ElMessage.warning('该状态类型不支持单独恢复')
      return
    }

    const response = await request.post(endpoint)
    if (response.data?.success) {
      ElMessage.success(`${row.instanceId || row.serviceType} 状态已恢复`)
      loadStateDetails()
    } else {
      ElMessage.error(response.data?.message || '恢复失败')
    }
  } catch (error: any) {
    console.error('Failed to recover single:', error)
    ElMessage.error('状态恢复失败')
  }
}

const syncStates = async () => {
  syncing.value = true
  try {
    const response = await request.post(`${apiBaseUrl}/sync`)
    if (response.data?.success) {
      ElMessage.success('状态同步完成')
      loadPersistenceStatus()
    } else {
      ElMessage.error(response.data?.message || '同步失败')
    }
  } catch (error: any) {
    console.error('Failed to sync:', error)
    ElMessage.error('状态同步失败')
  } finally {
    syncing.value = false
  }
}

const showSwitchTierDialog = () => {
  targetTier.value = currentTier.value
  switchTierDialogVisible.value = true
}

const switchTier = async () => {
  if (targetTier.value === currentTier.value) {
    ElMessage.info('当前已在使用该存储层')
    switchTierDialogVisible.value = false
    return
  }

  switching.value = true
  try {
    const response = await request.post(`${apiBaseUrl}/tiers/switch/${targetTier.value}`)
    if (response.data?.success) {
      currentTier.value = targetTier.value
      ElMessage.success(`已切换到 ${getTierDisplayName(targetTier.value)} 存储层`)
      switchTierDialogVisible.value = false
      loadPersistenceStatus()
    } else {
      ElMessage.error(response.data?.message || '切换失败')
    }
  } catch (error: any) {
    console.error('Failed to switch tier:', error)
    ElMessage.error('切换存储层失败')
  } finally {
    switching.value = false
  }
}

const viewStateDetail = (row: StateDetail) => {
  currentDetail.value = row
  stateDetailDialogVisible.value = true
}

onMounted(() => {
  loadPersistenceStatus()
  loadTiers()
  loadStateDetails()
})
</script>

<style scoped>
.state-persistence-management {
  padding: 24px;
  background: linear-gradient(180deg, #f7f9fc 0%, #ffffff 100%);
  min-height: calc(100vh - 80px);
}

.tier-card,
.stats-card,
.actions-card,
.details-card {
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

.tier-box {
  padding: 20px;
  border-radius: 12px;
  background: #f5f7fa;
  border: 2px solid transparent;
  transition: all 0.3s ease;
}

.tier-box.tier-active {
  border-color: #409eff;
  background: linear-gradient(135deg, #f0f7ff 0%, #e6f4ff 100%);
}

.tier-header {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 10px;
}

.tier-icon {
  font-size: 24px;
}

.tier-icon.healthy {
  color: #67c23a;
}

.tier-icon.unhealthy {
  color: #f56c6c;
}

.tier-name {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
}

.tier-status {
  margin-bottom: 10px;
}

.tier-info {
  display: flex;
  flex-direction: column;
  gap: 5px;
}

.tier-priority {
  font-size: 12px;
  color: #909399;
}

.tier-message {
  font-size: 12px;
  color: #606266;
}

.current-tier-info {
  margin-top: 20px;
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
  font-size: 28px;
  font-weight: 600;
  color: #409eff;
  margin-bottom: 8px;
}

.stat-label {
  font-size: 14px;
  color: #606266;
}

.flex-table {
  width: 100%;
  table-layout: auto;
}
</style>