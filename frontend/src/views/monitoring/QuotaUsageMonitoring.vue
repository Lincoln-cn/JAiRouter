<template>
  <PageSkeleton :title="t('quotaMonitoring.pageTitle')">
    <!-- 顶部指标条 -->
    <template #stats>
      <MetricStrip :items="summaryItems" :loading="loading" :columns="4" />
    </template>

    <!-- 工具栏（P4 固定顺序）：维度筛选 → 查询/重置 → 刷新 -->
    <template #toolbar>
      <FilterBar :collapsible="false" class="toolbar-filter">
        <el-form :inline="true" class="filter-form">
          <el-form-item :label="t('quotaMonitoring.tenantIdLabel')">
            <el-input
              v-model="query.tenantId"
              :placeholder="t('quotaMonitoring.tenantIdPlaceholder')"
              clearable
              style="width: 160px"
            />
          </el-form-item>
          <el-form-item :label="t('quotaMonitoring.apiKeyIdLabel')">
            <el-input
              v-model="query.apiKeyId"
              :placeholder="t('quotaMonitoring.apiKeyIdPlaceholder')"
              clearable
              style="width: 160px"
            />
          </el-form-item>
          <el-form-item :label="t('quotaMonitoring.userIdLabel')">
            <el-input
              v-model="query.userId"
              :placeholder="t('quotaMonitoring.userIdPlaceholder')"
              clearable
              style="width: 160px"
            />
          </el-form-item>
          <el-form-item :label="t('quotaMonitoring.serviceTypeLabel')">
            <el-input
              v-model="query.serviceType"
              :placeholder="t('quotaMonitoring.serviceTypePlaceholder')"
              clearable
              style="width: 160px"
            />
          </el-form-item>
          <el-form-item :label="t('quotaMonitoring.modelLabel')">
            <el-input
              v-model="query.model"
              :placeholder="t('quotaMonitoring.modelPlaceholder')"
              clearable
              style="width: 160px"
            />
          </el-form-item>
          <el-form-item :label="t('quotaMonitoring.windowLabel')">
            <el-select
              v-model="query.window"
              :placeholder="t('quotaMonitoring.windowPlaceholder')"
              clearable
              style="width: 140px"
            >
              <el-option
                v-for="w in windowOptions"
                :key="w"
                :label="w"
                :value="w"
              />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="loadUsage">
              <el-icon><Search /></el-icon>
              {{ t('quotaMonitoring.applyFilter') }}
            </el-button>
            <el-button @click="resetQuery">
              <el-icon><RefreshRight /></el-icon>
              {{ t('quotaMonitoring.resetFilter') }}
            </el-button>
          </el-form-item>
          <el-form-item class="toolbar-refresh">
            <el-button @click="loadAll" :loading="loading">
              <el-icon><Refresh /></el-icon>
              {{ t('quotaMonitoring.refresh') }}
            </el-button>
          </el-form-item>
        </el-form>
      </FilterBar>
    </template>

    <!-- 降级预警 -->
    <el-alert
      v-if="status?.degraded"
      :title="t('quotaMonitoring.degradedAlertTitle')"
      type="warning"
      :closable="false"
      show-icon
      class="section-gap"
    >
      <template #default>
        <p v-if="status.degradedReason" class="alert-line">
          <strong>{{ t('quotaMonitoring.degradedReasonLabel') }}：</strong>{{ status.degradedReason }}
        </p>
        <p v-if="status.redisProbe?.reason" class="alert-line">
          <strong>{{ t('quotaMonitoring.redisProbeFailLabel') }}：</strong>{{ status.redisProbe.reason }}
        </p>
        <p v-if="status.counterMetrics" class="alert-line alert-line--last">
          <strong>{{ t('quotaMonitoring.degradationCountLabel') }}：</strong>{{ status.counterMetrics.degradationCount }}
        </p>
      </template>
    </el-alert>

    <!-- 用量明细表格 -->
    <el-card shadow="hover" class="section-gap">
      <template #header>
        <div class="card-header-row">
          <span class="card-title">{{ t('quotaMonitoring.usageTitle') }}</span>
          <el-button size="small" type="primary" link @click="goApiKeyLimits">
            {{ t('quotaMonitoring.gotoApiKeyLimits') }}
          </el-button>
        </div>
      </template>
      <el-table
        :data="usageList"
        v-loading="loadingUsage"
        stripe
        border
        style="width: 100%"
      >
        <el-table-column prop="dimensions.tenantId" :label="t('quotaMonitoring.tenantIdColumn')" min-width="120" show-overflow-tooltip />
        <el-table-column prop="dimensions.apiKeyId" :label="t('quotaMonitoring.apiKeyIdColumn')" min-width="120" show-overflow-tooltip />
        <el-table-column prop="dimensions.userId" :label="t('quotaMonitoring.userIdColumn')" min-width="100" show-overflow-tooltip />
        <el-table-column prop="dimensions.serviceType" :label="t('quotaMonitoring.serviceTypeColumn')" min-width="100" />
        <el-table-column prop="dimensions.model" :label="t('quotaMonitoring.modelColumn')" min-width="140" show-overflow-tooltip />
        <el-table-column prop="window" :label="t('quotaMonitoring.windowColumn')" width="100" align="center" />
        <el-table-column prop="windowStart" :label="t('quotaMonitoring.windowStartColumn')" min-width="170">
          <template #default="{ row }">
            <TimeText :value="row.windowStart" mode="absolute" precision="minute" />
          </template>
        </el-table-column>
        <el-table-column prop="requestCount" :label="t('quotaMonitoring.requestCountColumn')" width="110" align="right">
          <template #default="{ row }">
            {{ row.requestCount.toLocaleString() }}
          </template>
        </el-table-column>
        <el-table-column prop="tokenCount" :label="t('quotaMonitoring.tokenCountColumn')" width="120" align="right">
          <template #default="{ row }">
            {{ row.tokenCount.toLocaleString() }}
          </template>
        </el-table-column>
        <el-table-column :label="t('quotaMonitoring.limitRequestColumn')" width="120" align="right">
          <template #default="{ row }">
            {{ formatLimit(limitMap.get(row.dimensions?.apiKeyId)?.dailyRequestLimit) }}
          </template>
        </el-table-column>
        <el-table-column :label="t('quotaMonitoring.limitTokenColumn')" width="120" align="right">
          <template #default="{ row }">
            {{ formatLimit(limitMap.get(row.dimensions?.apiKeyId)?.dailyTokenLimit) }}
          </template>
        </el-table-column>
        <el-table-column :label="t('quotaMonitoring.progressColumn')" min-width="140">
          <template #default="{ row }">
            <el-progress
              :percentage="usagePercent(row)"
              :status="usagePercent(row) >= 80 ? 'exception' : undefined"
              :stroke-width="10"
            />
          </template>
        </el-table-column>
        <template #empty>
          <EmptyState :icon="emptyIcon" :title="emptyTitle" :description="emptyDescription">
            <template v-if="hasActiveQuery" #actions>
              <el-button @click="clearQuery">{{ t('quotaMonitoring.clearFilter') }}</el-button>
            </template>
          </EmptyState>
        </template>
      </el-table>
    </el-card>

    <!-- 加载失败 -->
    <el-alert
      v-if="errorOccurred"
      :title="t('quotaMonitoring.loadFailedTitle')"
      :description="t('quotaMonitoring.loadFailedDescription')"
      type="warning"
      :closable="false"
      show-icon
      class="section-gap-top"
    />
  </PageSkeleton>
</template>

<script setup lang="ts">
import { computed, ref, reactive, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import {
  Refresh,
  Search,
  RefreshRight,
} from '@element-plus/icons-vue'
import PageSkeleton from '@/components/PageSkeleton.vue'
import EmptyState from '@/components/EmptyState.vue'
import FilterBar from '@/components/FilterBar.vue'
import TimeText from '@/components/TimeText.vue'
import MetricStrip, { type MetricStripItem } from '@/components/MetricStrip.vue'
import { useRouter } from 'vue-router'
import {
  getQuotaStatus,
  getQuotaUsage,
  type QuotaStatus,
  type QuotaUsageItem,
  type QuotaUsageQuery,
} from '@/api/quota'
import { getQuotaOverview } from '@/api/apiKey'
import type { QuotaUsageDetail } from '@/types'

const { t } = useI18n()
const router = useRouter()

const loading = ref(false)
const loadingUsage = ref(false)
const errorOccurred = ref(false)
const status = ref<QuotaStatus | null>(null)
const usageList = ref<QuotaUsageItem[]>([])
const limitMap = ref(new Map<string, QuotaUsageDetail>())

const summaryItems = computed<MetricStripItem[]>(() => [
  {
    key: 'enabled',
    label: t('quotaMonitoring.enabledLabel'),
    value: status.value?.enabled ? t('quotaMonitoring.enabled') : t('quotaMonitoring.disabled'),
  },
  {
    key: 'backend',
    label: t('quotaMonitoring.backendTypeLabel'),
    value: status.value?.backendName ?? '—',
  },
  {
    key: 'degraded',
    label: t('quotaMonitoring.degradedLabel'),
    value: status.value?.degraded ? t('quotaMonitoring.degradedYes') : t('quotaMonitoring.degradedNo'),
    // 只有「已降级」属真语义（失败/降级），正常态不给色
    tone: status.value?.degraded ? 'danger' : 'default',
  },
  {
    key: 'windows',
    label: t('quotaMonitoring.windowCountLabel'),
    value: status.value?.windows?.length ?? 0,
  },
])

const goApiKeyLimits = () => {
  router.push('/security/api-keys')
}

const formatLimit = (value?: number | null) => {
  if (value == null || value === 0) return t('quotaMonitoring.unlimited')
  return Number(value).toLocaleString()
}

const usagePercent = (row: QuotaUsageItem) => {
  const keyId = row.dimensions?.apiKeyId
  if (!keyId) return 0
  const limits = limitMap.value.get(keyId)
  if (!limits) return 0
  if (row.window === 'DAY') {
    const reqLimit = limits.dailyRequestLimit || 0
    const tokLimit = limits.dailyTokenLimit || 0
    const reqPct = reqLimit > 0 ? (row.requestCount / reqLimit) * 100 : 0
    const tokPct = tokLimit > 0 ? (row.tokenCount / tokLimit) * 100 : 0
    return Math.min(100, Math.round(Math.max(reqPct, tokPct)))
  }
  return 0
}

const loadLimits = async () => {
  try {
    const overview = await getQuotaOverview()
    const map = new Map<string, QuotaUsageDetail>()
    overview.forEach(item => map.set(item.keyId, item))
    limitMap.value = map
  } catch {
    limitMap.value = new Map()
  }
}

const windowOptions = ['MINUTE', 'HOUR', 'DAY', 'MONTH']

const query = reactive<QuotaUsageQuery>({
  tenantId: '',
  apiKeyId: '',
  userId: '',
  serviceType: '',
  model: '',
  window: '',
})

const resetQuery = () => {
  query.tenantId = ''
  query.apiKeyId = ''
  query.userId = ''
  query.serviceType = ''
  query.model = ''
  query.window = ''
}

/**
 * 最近一次真正提交给后端的查询条件快照。
 * 用它而不是直接看 `query`：`query` 随输入框实时变化，若据它判定空态，
 * 用户刚在输入框里打字（还没点「查询」）列表就会被判成「筛选无结果」。
 */
const appliedQuery = ref<QuotaUsageQuery>({ ...query })

/** P4 空态分流依据：本次查询带了任何维度条件 ⇒ 提示「筛选无结果」 */
const hasActiveQuery = computed(() => {
  const q = appliedQuery.value
  return Boolean(
    q.tenantId || q.apiKeyId || q.userId || q.serviceType || q.model || q.window
  )
})

const emptyIcon = computed(() => (hasActiveQuery.value ? 'Search' : 'DataLine'))

const emptyTitle = computed(() => {
  if (hasActiveQuery.value) return t('quotaMonitoring.filterEmptyTitle')
  return status.value?.enabled
    ? t('quotaMonitoring.noUsageData')
    : t('quotaMonitoring.quotaNotEnabled')
})

const emptyDescription = computed(() => {
  if (hasActiveQuery.value) return t('quotaMonitoring.filterEmptyDescription')
  return status.value?.enabled
    ? t('quotaMonitoring.noUsageDataDescription')
    : t('quotaMonitoring.quotaNotEnabledDescription')
})

const loadStatus = async () => {
  try {
    status.value = await getQuotaStatus()
    errorOccurred.value = false
  } catch {
    status.value = null
    errorOccurred.value = true
  }
}

const loadUsage = async () => {
  loadingUsage.value = true
  const params: QuotaUsageQuery = { ...query }
  try {
    usageList.value = await getQuotaUsage(params)
    appliedQuery.value = params
  } catch {
    usageList.value = []
  } finally {
    loadingUsage.value = false
  }
}

/** 空态里的「清除筛选」：清空条件并按无条件重新查询 */
const clearQuery = async () => {
  resetQuery()
  await loadUsage()
}

const loadAll = async () => {
  loading.value = true
  try {
    await Promise.all([loadStatus(), loadUsage(), loadLimits()])
  } finally {
    loading.value = false
  }
}

onMounted(loadAll)
</script>

<style scoped>
.card-title {
  font-weight: 600;
  font-size: 15px;
}

.card-header-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

/* FilterBar 自身不带面板样式，容器宽度与面板由本页补（同 AuditLogManagement 口径） */
.toolbar-filter {
  width: 100%;
}

.filter-form {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  width: 100%;
  padding: 16px;
  background-color: var(--ja-paper);
  border: 1px solid var(--ja-border);
  border-radius: var(--ja-radius);
}

/* P4 工具栏顺序：维度筛选 → 查询/重置 → 刷新（刷新固定在行末） */
.toolbar-refresh {
  margin-left: auto;
  margin-right: 0;
}

.section-gap {
  margin-bottom: var(--ja-space-4);
}

.section-gap-top {
  margin-top: var(--ja-space-4);
}

.alert-line {
  margin: 0 0 var(--ja-space-1);
}

.alert-line--last {
  margin-bottom: 0;
}
</style>
