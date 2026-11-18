<template>
  <ConfigPageShell
    :title="t('statePersistence.pageTitle')"
    :description="t('statePersistence.pageDesc')"
    :loading="loading"
    :can-write="canWrite"
    save-permission="config:persistence:write"
    :show-actions="false"
    @refresh="loadAll"
  >
        <section class="config-group">
      <h3 class="config-group-title">{{ t('statePersistence.tabConfig') }}</h3>
        <ConfigSectionCard
          :title="t('statePersistence.currentTierTitle')"
          :description="t('statePersistence.currentTierDesc')"
        >
          <template #actions>
            <el-tag size="small" effect="plain" type="info">{{ t('statePersistence.readonlyTag') }}</el-tag>
          </template>

          <el-alert
            :title="t('statePersistence.currentTierInfo', { tier: getTierDisplayName(currentTier), priority: currentTierPriority })"
            type="info"
            :closable="false"
            show-icon
          />
        </ConfigSectionCard>

        <ConfigSectionCard
          :title="t('statePersistence.switchTier')"
          :description="t('statePersistence.switchTierDesc')"
        >
          <div class="switch-tier-row">
            <div class="config-item">
              <span class="config-label">{{ t('statePersistence.targetTier') }}</span>
              <el-select
                v-model="targetTier"
                :placeholder="t('statePersistence.selectTier')"
                :disabled="!canWrite"
                style="width: 260px"
                @change="targetTierTouched = true"
              >
                <el-option label="Redis (Tier 1)" value="redis" :disabled="!tierHealth.redis?.healthy" />
                <el-option label="H2 Database (Tier 2)" value="h2" :disabled="!tierHealth.h2?.healthy" />
                <el-option label="File Storage (Tier 3)" value="file" :disabled="!tierHealth.file?.healthy" />
              </el-select>
            </div>

            <!-- 切换存储层决定「新状态写到哪里」，属需二次确认的危险动作，因此从原来的对话框改为就地确认 -->
            <ConfirmAction
              v-if="canWrite"
              :title="switchConfirm"
              :width="320"
              type="warning"
              :loading="switching"
              @confirm="switchTier"
            >
              <el-button
                v-permission="'config:persistence:write'"
                type="warning"
                :loading="switching"
              >
                <el-icon><Switch /></el-icon>
                {{ t('statePersistence.confirmSwitch') }}
              </el-button>
            </ConfirmAction>
          </div>
        </ConfigSectionCard>
    </section>

    <section class="config-group">
      <h3 class="config-group-title">{{ t('statePersistence.tabOperations') }}</h3>
        <ConfigSectionCard
          :title="t('statePersistence.tierCardTitle')"
          :description="t('statePersistence.tiersDesc')"
        >
          <template #actions>
            <el-button v-permission="'config:persistence:write'" size="small" @click="refreshTiers">
              <el-icon><Refresh /></el-icon>
              {{ t('statePersistence.refreshTiers') }}
            </el-button>
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
        </ConfigSectionCard>

        <ConfigSectionCard
          :title="t('statePersistence.actionsTitle')"
          :description="t('statePersistence.actionsDesc')"
        >
          <div class="action-row">
            <el-button
              type="primary"
              v-permission="'config:persistence:write'"
              :loading="recovering"
              @click="recoverAll"
            >
              <el-icon><RefreshRight /></el-icon>
              {{ t('statePersistence.recoverAll') }}
            </el-button>
            <el-button
              v-permission="'config:persistence:write'"
              :loading="syncing"
              @click="syncStates"
            >
              <el-icon><Upload /></el-icon>
              {{ t('statePersistence.syncNow') }}
            </el-button>
            <!-- 暗色对比收口：info 白字在 #909399 上 ratio≈3.1 <4.5，改默认类型按钮（文字随 --el-text-color-* 令牌） -->
            <el-button :loading="loadingDetails" @click="loadStateDetails">
              <el-icon><View /></el-icon>
              {{ t('statePersistence.viewDetail') }}
            </el-button>
          </div>
        </ConfigSectionCard>

        <ConfigSectionCard
          :title="t('statePersistence.detailsTitle')"
          :description="t('statePersistence.detailsDesc')"
        >
          <template #actions>
            <el-tag type="info" size="small">{{ t('statePersistence.totalCount', { count: stateDetails.length }) }}</el-tag>
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
        </ConfigSectionCard>
    </section>

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
  </ConfigPageShell>
</template>

<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { Refresh, RefreshRight, Switch, Upload, View } from '@element-plus/icons-vue'
import ConfigPageShell from '@/components/config/ConfigPageShell.vue'
import ConfigSectionCard from '@/components/config/ConfigSectionCard.vue'
import ConfirmAction from '@/components/ConfirmAction.vue'
import { usePermission } from '@/composables/usePermission'
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

const { t } = useI18n()
const { hasPermission } = usePermission()

/** 写操作统一走 config:persistence:write（运维角色有、只读角色无）。 */
const canWrite = computed(() => hasPermission('config:persistence:write'))

/**
 * 本页没有「草稿型配置」：所有端点要么是只读查询，要么是一点即生效的操作
 * （重算健康 / 恢复 / 同步 / 切换存储层），所以拆成「配置」与「操作与详情」两个 tab，
 * 且外壳只保留状态行（:show-actions="false"），不提供永远禁用的保存按钮。
 */

const loading = ref(false)
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

const loadingDetails = ref(false)
const recovering = ref(false)
const syncing = ref(false)
const switching = ref(false)

const stateDetailDialogVisible = ref(false)
const targetTier = ref('h2')
/** 用户是否亲手选过目标层：没选过就跟着当前层走，选过就别被刷新覆盖掉 */
const targetTierTouched = ref(false)
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

const switchConfirm = computed(() =>
  t('statePersistence.switchTierConfirm', { tier: getTierDisplayName(targetTier.value) })
)

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
      if (!targetTierTouched.value) {
        targetTier.value = currentTier.value
      }
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

/** 外壳的刷新按钮与首次进入都走这里：三个只读来源一次补齐。 */
const loadAll = async () => {
  loading.value = true
  try {
    await Promise.all([loadPersistenceStatus(), loadTiers(), loadStateDetails()])
  } finally {
    loading.value = false
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

/** 由 ConfigSectionCard 里的 ConfirmAction 触发：目标层与当前层相同就只提示，不发请求。 */
const switchTier = async () => {
  if (targetTier.value === currentTier.value) {
    ElMessage.info(t('statePersistence.messages.alreadyUsingTier'))
    return
  }

  switching.value = true
  try {
    const response = await switchTierApi(targetTier.value)
    if (response.data?.success) {
      currentTier.value = targetTier.value
      ElMessage.success(t('statePersistence.messages.tierSwitched', { tier: getTierDisplayName(targetTier.value) }))
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

onMounted(loadAll)
</script>

<style scoped>
.page-tabs {
  margin-bottom: 4px;
}

.switch-tier-row {
  display: flex;
  align-items: flex-end;
  gap: 16px;
  flex-wrap: wrap;
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

.action-row {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.summary-strip {
  margin-bottom: var(--ja-space-4, 16px);
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

.flex-table {
  width: 100%;
  table-layout: auto;
}
</style>
