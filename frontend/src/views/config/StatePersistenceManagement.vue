<template>
  <PageSkeleton :title="t('statePersistence.pageTitle')">
    <template #actions>
      <el-button type="primary" size="small" v-permission="'config:persistence:write'" @click="refreshTiers">
        <el-icon><Refresh /></el-icon>
        {{ t('statePersistence.refresh') }}
      </el-button>
    </template>

    <!-- 顶部指标条 -->
    <template #stats>
      <MetricStrip :items="summaryItems" :columns="4" />
    </template>

    <!-- 存储层状态卡片 -->
    <el-card class="tier-card">
      <template #header>
        <span class="card-title">{{ t('statePersistence.tierCardTitle') }}</span>
      </template>

      <el-row :gutter="20">
        <el-col :span="8">
          <div class="tier-box" :class="{ 'tier-active': currentTier === 'redis' }">
            <div class="tier-header">
              <el-icon class="tier-icon" :class="tierHealth.redis?.healthy ? 'healthy' : 'unhealthy'">
                <component :is="tierHealth.redis?.healthy ? 'SuccessFilled' : 'CircleCloseFilled'" />
              </el-icon>
              <span class="tier-name">Redis</span>
              <el-tag v-if="currentTier === 'redis'" type="success" size="small">{{ t('statePersistence.active') }}</el-tag>
            </div>
            <div class="tier-status">
              <el-tag :type="tierHealth.redis?.healthy ? 'success' : 'danger'" size="small">
                {{ tierHealth.redis?.healthy ? 'UP' : 'DOWN' }}
              </el-tag>
            </div>
            <div class="tier-info">
              <span class="tier-priority">{{ t('statePersistence.tier1Priority') }}</span>
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
              <el-tag v-if="currentTier === 'h2'" type="success" size="small">{{ t('statePersistence.active') }}</el-tag>
            </div>
            <div class="tier-status">
              <el-tag :type="tierHealth.h2?.healthy ? 'success' : 'danger'" size="small">
                {{ tierHealth.h2?.healthy ? 'UP' : 'DOWN' }}
              </el-tag>
            </div>
            <div class="tier-info">
              <span class="tier-priority">{{ t('statePersistence.tier2Priority') }}</span>
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
              <el-tag v-if="currentTier === 'file'" type="success" size="small">{{ t('statePersistence.active') }}</el-tag>
            </div>
            <div class="tier-status">
              <el-tag :type="tierHealth.file?.healthy ? 'success' : 'danger'" size="small">
                {{ tierHealth.file?.healthy ? 'UP' : 'DOWN' }}
              </el-tag>
            </div>
            <div class="tier-info">
              <span class="tier-priority">{{ t('statePersistence.tier3Priority') }}</span>
              <span v-if="tierHealth.file?.message" class="tier-message">{{ tierHealth.file?.message }}</span>
            </div>
          </div>
        </el-col>
      </el-row>

      <div class="current-tier-info">
        <el-alert
          :title="t('statePersistence.currentTierInfo', { tier: getTierDisplayName(currentTier), priority: currentTierPriority })"
          type="info"
          :closable="false"
          show-icon
        />
      </div>
    </el-card>

    <!-- 操作卡片 -->
    <el-card class="actions-card">
      <template #header>
        <div class="card-header">
          <span class="card-title">{{ t('statePersistence.actionsTitle') }}</span>
        </div>
      </template>

      <el-row :gutter="20">
        <el-col :span="6">
          <el-button type="primary" v-permission="'config:persistence:write'" @click="recoverAll" :loading="recovering">
            <el-icon><RefreshRight /></el-icon>
            {{ t('statePersistence.recoverAll') }}
          </el-button>
        </el-col>
        <el-col :span="6">
          <!-- 切换存储层是「可逆的运维操作」而非告警/危险动作，故不给语义色 -->
          <el-button v-permission="'config:persistence:write'" @click="showSwitchTierDialog">
            <el-icon><Switch /></el-icon>
            {{ t('statePersistence.switchTier') }}
          </el-button>
        </el-col>
        <el-col :span="6">
          <el-button v-permission="'config:persistence:write'" @click="syncStates" :loading="syncing">
            <el-icon><Upload /></el-icon>
            {{ t('statePersistence.syncNow') }}
          </el-button>
        </el-col>
        <el-col :span="6">
          <!-- 暗色对比收口：info 白字在 #909399 上 ratio≈3.1 <4.5，改默认类型按钮（文字随 --el-text-color-* 令牌） -->
          <el-button @click="loadPersistenceStatus">
            <el-icon><View /></el-icon>
            {{ t('statePersistence.viewDetail') }}
          </el-button>
        </el-col>
      </el-row>
    </el-card>

    <!-- 状态详情列表 -->
    <el-card class="details-card">
      <template #header>
        <div class="card-header">
          <span class="card-title">{{ t('statePersistence.detailsTitle') }}</span>
          <el-tag type="info">{{ t('statePersistence.totalCount', { count: stateDetails.length }) }}</el-tag>
        </div>
      </template>

      <el-table :data="stateDetails" stripe v-loading="loadingDetails" class="flex-table">
        <el-table-column prop="instanceId" :label="t('statePersistence.instanceId')" min-width="180">
          <template #default="{ row }">
            <span>{{ row.instanceId || row.serviceType || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="stateType" :label="t('statePersistence.type')" min-width="120">
          <template #default="{ row }">
            <!-- 状态类型是分类维度，不是语义状态 ⇒ 中性标签；语义色只留给「状态」列 -->
            <el-tag type="info" size="small">
              {{ getStateTypeDisplayName(row.stateType) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="state" :label="t('statePersistence.status')" min-width="100">
          <template #default="{ row }">
            <el-tag :type="getStateTagType(row.state)" size="small">
              {{ row.state || '-' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="lastModified" :label="t('statePersistence.lastModified')" min-width="180">
          <template #default="{ row }">
            <span>{{ formatTime(row.lastModified) }}</span>
          </template>
        </el-table-column>
        <el-table-column :label="t('statePersistence.actions')" min-width="150">
          <template #default="{ row }">
            <el-button size="small" type="primary" v-permission="'config:persistence:write'" @click="recoverSingle(row)">
              {{ t('statePersistence.recover') }}
            </el-button>
            <el-button size="small" @click="viewStateDetail(row)">
              {{ t('statePersistence.detail') }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 切换存储层对话框 -->
    <el-dialog
      v-model="switchTierDialogVisible"
      :title="t('statePersistence.switchDialogTitle')"
      width="400px"
    >
      <el-form label-width="100px">
        <el-form-item :label="t('statePersistence.targetTier')">
          <el-select v-model="targetTier" :placeholder="t('statePersistence.selectTier')">
            <el-option label="Redis (Tier 1)" value="redis" :disabled="!tierHealth.redis?.healthy" />
            <el-option label="H2 Database (Tier 2)" value="h2" :disabled="!tierHealth.h2?.healthy" />
            <el-option label="File Storage (Tier 3)" value="file" :disabled="!tierHealth.file?.healthy" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="switchTierDialogVisible = false">{{ t('statePersistence.cancel') }}</el-button>
        <el-button type="primary" v-permission="'config:persistence:write'" @click="switchTier" :loading="switching">{{ t('statePersistence.confirmSwitch') }}</el-button>
      </template>
    </el-dialog>

    <!-- 状态详情对话框 -->
    <el-dialog
      v-model="stateDetailDialogVisible"
      :title="t('statePersistence.detailDialogTitle')"
      width="600px"
    >
      <el-descriptions :column="2" border>
        <el-descriptions-item :label="t('statePersistence.instanceId')">{{ currentDetail?.instanceId || currentDetail?.serviceType }}</el-descriptions-item>
        <el-descriptions-item :label="t('statePersistence.stateType')">{{ getStateTypeDisplayName(currentDetail?.stateType) }}</el-descriptions-item>
        <el-descriptions-item :label="t('statePersistence.currentState')">{{ currentDetail?.state }}</el-descriptions-item>
        <el-descriptions-item :label="t('statePersistence.lastModified')">{{ formatTime(currentDetail?.lastModified) }}</el-descriptions-item>
        <el-descriptions-item :label="t('statePersistence.tier')">{{ getTierDisplayName(currentDetail?.tier) }}</el-descriptions-item>
        <el-descriptions-item :label="t('statePersistence.dataSize')">{{ currentDetail?.dataSize || '-' }}</el-descriptions-item>
      </el-descriptions>
      <template #footer>
        <el-button @click="stateDetailDialogVisible = false">{{ t('statePersistence.close') }}</el-button>
      </template>
    </el-dialog>
  </PageSkeleton>
</template>

<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { Refresh, RefreshRight, Switch, Upload, View } from '@element-plus/icons-vue'
import {
  getPersistenceStatus,
  getTierHealth,
  refreshTiers as refreshTiersApi,
  getStateDetails,
  recoverAll as recoverAllApi,
  recoverSingle as recoverSingleApi,
  syncStates as syncStatesApi,
  switchTier as switchTierApi,
  type TierHealth,
  type StateDetail,
  type PersistenceStats
} from '@/api/statePersistence'
import PageSkeleton from '@/components/PageSkeleton.vue'
import MetricStrip, { type MetricStripItem } from '@/components/MetricStrip.vue'

const { t } = useI18n()

const currentTier = ref('h2')
// 后端返回的是 Map<String, TierHealth>（键名由三层实现决定），因此按 Record 收，
// 而不是写死 redis/h2/file —— 否则后端加一层就要改前端类型。
const tierHealth = ref<Record<string, TierHealth>>({
  redis: { healthy: false },
  h2: { healthy: true },
  file: { healthy: true }
})
const stats = ref<PersistenceStats>({
  circuitBreakerCount: 0,
  loadBalancerCount: 0,
  rateLimiterCount: 0,
  pendingSync: 0
})
const stateDetails = ref<StateDetail[]>([])

const summaryItems = computed<MetricStripItem[]>(() => [
  { key: 'circuitBreaker', label: t('statePersistence.cbStatLabel'), value: stats.value.circuitBreakerCount },
  { key: 'loadBalancer', label: t('statePersistence.lbStatLabel'), value: stats.value.loadBalancerCount },
  { key: 'rateLimiter', label: t('statePersistence.rlStatLabel'), value: stats.value.rateLimiterCount },
  {
    key: 'pendingSync',
    label: t('statePersistence.pendingSyncLabel'),
    value: stats.value.pendingSync,
    // 「有未同步积压」属真语义（需要关注），为 0 时保持中性
    tone: stats.value.pendingSync > 0 ? 'warning' : 'default',
  },
])

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
    case 'redis': return t('statePersistence.priority1')
    case 'h2': return t('statePersistence.priority2')
    case 'file': return t('statePersistence.priority3')
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
    case 'CIRCUIT_BREAKER': return t('statePersistence.stateTypes.circuitBreaker')
    case 'LOAD_BALANCER': return t('statePersistence.stateTypes.loadBalancer')
    case 'RATE_LIMITER': return t('statePersistence.stateTypes.rateLimiter')
    default: return type || '-'
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
    const response = await getPersistenceStatus()
    const data = response.data?.data
    if (response.data?.success && data) {
      currentTier.value = data.currentTier || 'h2'
      // tierHealth 由 loadTiers() 单独获取，此处不再覆盖
      // 因为 /status 返回的是 Map<String, Boolean>，而前端需要 { healthy, message } 结构
      stats.value = data.stats || stats.value
    }
  } catch (error: unknown) {
    console.error('Failed to load status:', error)
    ElMessage.warning(t('statePersistence.messages.loadFailed'))
  }
}

const loadTiers = async () => {
  try {
    const response = await getTierHealth()
    const data = response.data?.data
    if (response.data?.success && data) {
      tierHealth.value = data
    }
  } catch (error: unknown) {
    console.error('Failed to load tiers:', error)
  }
}

const refreshTiers = async () => {
  try {
    const response = await refreshTiersApi()
    const data = response.data?.data
    if (response.data?.success && data) {
      tierHealth.value = data
      ElMessage.success(t('statePersistence.messages.tiersRefreshed'))
    }
  } catch (error: unknown) {
    console.error('Failed to refresh tiers:', error)
    ElMessage.error(t('statePersistence.messages.tiersRefreshFailed'))
  }
}

const loadStateDetails = async () => {
  loadingDetails.value = true
  try {
    const response = await getStateDetails()
    if (response.data?.success) {
      stateDetails.value = response.data.data || []
    }
  } catch (error: unknown) {
    console.error('Failed to load state details:', error)
    // 接口不可用时置空，页面展示空态（原注释写的是「使用模拟数据」，与实现不符）
    stateDetails.value = []
  } finally {
    loadingDetails.value = false
  }
}

const recoverAll = async () => {
  recovering.value = true
  try {
    const response = await recoverAllApi()
    if (response.data?.success) {
      ElMessage.success(t('statePersistence.messages.recoverAllSuccess', { message: response.data.data?.message || t('statePersistence.messages.success') }))
      loadStateDetails()
      loadPersistenceStatus()
    } else {
      ElMessage.error(response.data?.message || t('statePersistence.messages.recoverFailed'))
    }
  } catch (error: unknown) {
    console.error('Failed to recover all:', error)
    ElMessage.error(t('statePersistence.messages.recoverAllFailed'))
  } finally {
    recovering.value = false
  }
}

const recoverSingle = async (row: StateDetail) => {
  try {
    // 支持的三种状态类型；其余类型的"恢复"后端没有对应端点（原实现靠拼空串兜住，这里显式判）。
    const supported = ['CIRCUIT_BREAKER', 'LOAD_BALANCER', 'RATE_LIMITER']
    if (!supported.includes(row.stateType)) {
      ElMessage.warning(t('statePersistence.messages.recoverNotSupported'))
      return
    }

    const target = row.instanceId || row.serviceType || ''
    const response = await recoverSingleApi(row.stateType, target)
    if (response.data?.success) {
      ElMessage.success(t('statePersistence.messages.recoverSuccess', { id: row.instanceId || row.serviceType }))
      loadStateDetails()
    } else {
      ElMessage.error(response.data?.message || t('statePersistence.messages.recoverFailed'))
    }
  } catch (error: unknown) {
    console.error('Failed to recover single:', error)
    ElMessage.error(t('statePersistence.messages.recoverAllFailed'))
  }
}

const syncStates = async () => {
  syncing.value = true
  try {
    const response = await syncStatesApi()
    if (response.data?.success) {
      ElMessage.success(t('statePersistence.messages.syncSuccess'))
      loadPersistenceStatus()
    } else {
      ElMessage.error(response.data?.message || t('statePersistence.messages.syncFailed'))
    }
  } catch (error: unknown) {
    console.error('Failed to sync:', error)
    ElMessage.error(t('statePersistence.messages.syncError'))
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
    ElMessage.info(t('statePersistence.messages.alreadyUsingTier'))
    switchTierDialogVisible.value = false
    return
  }

  switching.value = true
  try {
    const response = await switchTierApi(targetTier.value)
    if (response.data?.success) {
      currentTier.value = targetTier.value
      ElMessage.success(t('statePersistence.messages.tierSwitched', { tier: getTierDisplayName(targetTier.value) }))
      switchTierDialogVisible.value = false
      loadPersistenceStatus()
    } else {
      ElMessage.error(response.data?.message || t('statePersistence.messages.tierSwitchFailed'))
    }
  } catch (error: unknown) {
    console.error('Failed to switch tier:', error)
    ElMessage.error(t('statePersistence.messages.tierSwitchError'))
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
.tier-card,
.actions-card,
.details-card {
  margin-bottom: var(--ja-space-5);
  box-shadow: var(--ja-shadow-lg);
  border-radius: var(--ja-radius-lg);
}

.card-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--ja-ink);
}

/* 存储层卡片一律中性底；只有「当前生效层」用主色描边，不用背景渐变 */
.tier-box {
  padding: var(--ja-space-5);
  border-radius: var(--ja-radius-lg);
  background: var(--ja-surface-2);
  border: 1px solid var(--ja-border);
  transition: border-color 0.3s ease;
}

.tier-box.tier-active {
  border-color: var(--ja-primary);
  background: var(--ja-paper);
}

.tier-header {
  display: flex;
  align-items: center;
  gap: var(--ja-space-2);
  margin-bottom: var(--ja-space-2);
}

.tier-icon {
  font-size: 24px;
}

/* 健康/异常是真语义：绿/红只落在这一处图标与 UP/DOWN 标签上 */
.tier-icon.healthy {
  color: var(--ja-success);
}

.tier-icon.unhealthy {
  color: var(--ja-danger);
}

.tier-name {
  font-size: 16px;
  font-weight: 600;
  color: var(--ja-ink);
}

.tier-status {
  margin-bottom: var(--ja-space-2);
}

.tier-info {
  display: flex;
  flex-direction: column;
  gap: var(--ja-space-1);
}

.tier-priority {
  font-size: 12px;
  color: var(--ja-ink-3);
}

.tier-message {
  font-size: 12px;
  color: var(--ja-ink-2);
}

.current-tier-info {
  margin-top: var(--ja-space-5);
}

.flex-table {
  width: 100%;
  table-layout: auto;
}
</style>