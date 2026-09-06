<template>
  <PageSkeleton :title="t('slowQuery.pageTitle')">
    <template #actions>
      <el-button @click="loadAll" :loading="loading" size="default">
        <el-icon><Refresh /></el-icon>
        {{ t('slowQuery.refresh') }}
      </el-button>
      <el-popconfirm
        :title="t('slowQuery.resetConfirmTitle')"
        :confirm-button-text="t('slowQuery.confirm')"
        :cancel-button-text="t('slowQuery.cancel')"
        @confirm="handleResetStats"
      >
        <template #reference>
          <el-button type="danger" plain size="default">
            <el-icon><Delete /></el-icon>
            {{ t('slowQuery.resetStats') }}
          </el-button>
        </template>
      </el-popconfirm>
    </template>

    <!-- 汇总卡片 -->
    <template #stats>
      <el-row :gutter="16">
        <el-col :span="6">
          <StatCard
            icon="Warning"
            :label="t('slowQuery.totalSlowQueriesLabel')"
            :value="totalCount"
            tone="danger"
          />
        </el-col>
        <el-col :span="6">
          <StatCard
            icon="Histogram"
            :label="t('slowQuery.operationTypesLabel')"
            :value="Object.keys(stats).length"
            tone="primary"
          />
        </el-col>
        <el-col :span="6">
          <StatCard
            icon="Bell"
            :label="t('slowQuery.alertsTriggeredLabel')"
            :value="alertStats.totalAlertsTriggered"
            tone="warning"
          />
        </el-col>
        <el-col :span="6">
          <StatCard
            icon="Mute"
            :label="t('slowQuery.alertsSuppressedLabel')"
            :value="alertStats.totalAlertsSuppressed"
            tone="info"
          />
        </el-col>
      </el-row>
    </template>

    <!-- 筛选区域 -->
    <el-card shadow="hover" style="margin-bottom: 16px">
      <el-form :inline="true" class="filter-form">
        <el-form-item :label="t('slowQuery.minCountLabel')">
          <el-input-number
            v-model="filterThreshold"
            :min="0"
            :max="999999"
            :placeholder="t('slowQuery.minCountPlaceholder')"
            controls-position="right"
            style="width: 150px"
          />
        </el-form-item>
        <el-form-item :label="t('slowQuery.operationLabel')">
          <el-select
            v-model="filterOperation"
            :placeholder="t('slowQuery.allOperationsPlaceholder')"
            clearable
            style="width: 220px"
          >
            <el-option
              v-for="op in allOperations"
              :key="op"
              :label="op"
              :value="op"
            />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="applyFilter">
            <el-icon><Search /></el-icon>
            {{ t('slowQuery.applyFilter') }}
          </el-button>
          <el-button @click="resetFilter">
            <el-icon><RefreshRight /></el-icon>
            {{ t('slowQuery.resetFilter') }}
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 慢查询列表表格 -->
    <el-card shadow="hover" style="margin-bottom: 16px">
      <template #header>
        <span class="card-title">{{ t('slowQuery.tableTitle') }}</span>
      </template>
      <el-table
        :data="filteredTableData"
        v-loading="loading"
        stripe
        border
        style="width: 100%"
        :default-sort="{ prop: 'totalDuration', order: 'descending' }"
      >
        <el-table-column prop="operationName" :label="t('slowQuery.operationNameColumn')" min-width="200" sortable />
        <el-table-column prop="count" :label="t('slowQuery.slowQueryCountColumn')" width="130" sortable align="center" />
        <el-table-column :label="t('slowQuery.averageDurationColumn')" width="130" sortable sort-by="averageDuration" align="center">
          <template #default="{ row }">
            {{ t('slowQuery.durationMs', { value: row.averageDuration.toFixed(1) }) }}
          </template>
        </el-table-column>
        <el-table-column :label="t('slowQuery.maxDurationColumn')" width="130" sortable sort-by="maxDuration" align="center">
          <template #default="{ row }">
            <span :class="{ 'danger-text': row.maxDuration > 5000 }">{{ t('slowQuery.durationMs', { value: row.maxDuration }) }}</span>
          </template>
        </el-table-column>
        <el-table-column :label="t('slowQuery.minDurationColumn')" width="130" sortable sort-by="minDuration" align="center">
          <template #default="{ row }">{{ t('slowQuery.durationMs', { value: row.minDuration }) }}</template>
        </el-table-column>
        <el-table-column :label="t('slowQuery.totalDurationColumn')" width="150" sortable sort-by="totalDuration" align="center">
          <template #default="{ row }">
            {{ formatDuration(row.totalDuration) }}
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!loading && filteredTableData.length === 0" :description="t('slowQuery.noDataEmpty')" />
    </el-card>

    <!-- 性能热点 -->
    <el-card shadow="hover" style="margin-bottom: 16px">
      <template #header>
        <div class="card-header">
          <span class="card-title">{{ t('slowQuery.hotspotsTitle', { limit: hotspotLimit }) }}</span>
          <el-input-number
            v-model="hotspotLimit"
            :min="5"
            :max="50"
            size="small"
            controls-position="right"
            style="width: 100px"
            @change="loadHotspots"
          />
        </div>
      </template>
      <el-table
        :data="hotspots"
        v-loading="loadingHotspots"
        stripe
        border
        style="width: 100%"
      >
        <el-table-column type="index" label="#" width="60" align="center" />
        <el-table-column prop="operationName" :label="t('slowQuery.operationNameColumn')" min-width="200" />
        <el-table-column :label="t('slowQuery.callCountColumn')" width="120" align="center">
          <template #default="{ row }">{{ row.stats.callCount }}</template>
        </el-table-column>
        <el-table-column :label="t('slowQuery.averageDurationColumn')" width="130" align="center">
          <template #default="{ row }">{{ t('slowQuery.durationMs', { value: row.stats.averageDuration.toFixed(1) }) }}</template>
        </el-table-column>
        <el-table-column :label="t('slowQuery.maxDurationColumn')" width="130" align="center">
          <template #default="{ row }">{{ t('slowQuery.durationMs', { value: row.stats.maxDuration }) }}</template>
        </el-table-column>
        <el-table-column :label="t('slowQuery.totalDurationColumn')" width="150" align="center">
          <template #default="{ row }">{{ formatDuration(row.totalDuration) }}</template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!loadingHotspots && hotspots.length === 0" :description="t('slowQuery.noHotspotsEmpty')" />
    </el-card>

    <!-- 告警状态 -->
    <el-card v-if="alertStatus.alertServiceEnabled" shadow="hover">
      <template #header>
        <span class="card-title">{{ t('slowQuery.alertSystemTitle') }}</span>
      </template>
      <el-descriptions :column="3" border size="small">
        <el-descriptions-item :label="t('slowQuery.alertServiceLabel')">
          <el-tag type="success" size="small">{{ t('slowQuery.enabled') }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item :label="t('slowQuery.activeAlertKeysLabel')">
          {{ alertStats.activeAlertKeys }}
        </el-descriptions-item>
        <el-descriptions-item :label="t('slowQuery.triggerRateLabel')">
          {{ ((alertStatus.alertTriggerRate ?? 0) * 100).toFixed(1) }}%
        </el-descriptions-item>
        <el-descriptions-item :label="t('slowQuery.suppressionRateLabel')">
          {{ ((alertStatus.alertSuppressionRate ?? 0) * 100).toFixed(1) }}%
        </el-descriptions-item>
        <el-descriptions-item :label="t('slowQuery.avgAlertsPerOperationLabel')">
          {{ (alertStatus.averageAlertsPerOperation ?? 0).toFixed(1) }}
        </el-descriptions-item>
        <el-descriptions-item :label="t('slowQuery.activeOperationsLabel')">
          {{ alertStats.activeOperations?.length ?? 0 }}
        </el-descriptions-item>
      </el-descriptions>
    </el-card>

    <!-- 数据缺失降级 -->
    <el-alert
      v-if="!loading && Object.keys(stats).length === 0 && !errorOccurred"
      :title="t('slowQuery.noDataEmpty')"
      :description="t('slowQuery.noDataAlertDescription')"
      type="info"
      :closable="false"
      show-icon
      style="margin-top: 16px"
    />
    <el-alert
      v-if="errorOccurred"
      :title="t('slowQuery.loadFailedTitle')"
      :description="t('slowQuery.loadFailedDescription')"
      type="warning"
      :closable="false"
      show-icon
      style="margin-top: 16px"
    />
  </PageSkeleton>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { Refresh, Delete, Search, RefreshRight, Warning, Histogram, Bell, Mute } from '@element-plus/icons-vue'
import PageSkeleton from '@/components/PageSkeleton.vue'
import StatCard from '@/components/StatCard.vue'
import {
  getSlowQueryStats,
  getSlowQueryCount,
  getPerformanceHotspots,
  getAlertStats,
  getAlertSystemStatus,
  resetSlowQueryStats,
  type SlowQueryStats,
  type PerformanceHotspot,
  type SlowQueryAlertStats,
  type AlertSystemStatus
} from '@/api/slowQuery'

const { t } = useI18n()

interface TableRow {
  operationName: string
  count: number
  totalDuration: number
  maxDuration: number
  minDuration: number
  averageDuration: number
}

const loading = ref(false)
const loadingHotspots = ref(false)
const errorOccurred = ref(false)
const stats = ref<Record<string, SlowQueryStats>>({})
const totalCount = ref(0)
const hotspots = ref<PerformanceHotspot[]>([])
const alertStats = ref<SlowQueryAlertStats>({
  totalAlertsTriggered: 0,
  totalAlertsSuppressed: 0,
  activeAlertKeys: 0,
  activeOperations: []
})
const alertStatus = ref<AlertSystemStatus>({ alertServiceEnabled: false })

const filterThreshold = ref(0)
const filterOperation = ref('')
const hotspotLimit = ref(10)

const allOperations = computed(() => Object.keys(stats.value))

const tableData = computed<TableRow[]>(() => {
  return Object.entries(stats.value).map(([operationName, s]) => ({
    operationName,
    count: s.count,
    totalDuration: s.totalDuration,
    maxDuration: s.maxDuration,
    minDuration: s.minDuration,
    averageDuration: s.averageDuration
  }))
})

const filteredTableData = computed(() => {
  let data = tableData.value
  if (filterThreshold.value > 0) {
    data = data.filter(row => row.count >= filterThreshold.value)
  }
  if (filterOperation.value) {
    data = data.filter(row => row.operationName === filterOperation.value)
  }
  return data.sort((a, b) => b.totalDuration - a.totalDuration)
})

const formatDuration = (ms: number): string => {
  if (ms >= 60000) return t('slowQuery.durationMinutes', { value: (ms / 60000).toFixed(1) })
  if (ms >= 1000) return t('slowQuery.durationSeconds', { value: (ms / 1000).toFixed(1) })
  return t('slowQuery.durationMs', { value: ms })
}

const applyFilter = () => {
  // Filter is reactive via computed; this triggers a UI feedback.
  ElMessage.success(t('slowQuery.filterAppliedMessage'))
}

const resetFilter = () => {
  filterThreshold.value = 0
  filterOperation.value = ''
  ElMessage.info(t('slowQuery.filterResetMessage'))
}

const loadStats = async () => {
  try {
    stats.value = await getSlowQueryStats()
    errorOccurred.value = false
  } catch {
    stats.value = {}
    errorOccurred.value = true
  }
}

const loadCount = async () => {
  try {
    totalCount.value = await getSlowQueryCount()
  } catch {
    totalCount.value = 0
  }
}

const loadHotspots = async () => {
  loadingHotspots.value = true
  try {
    hotspots.value = await getPerformanceHotspots(hotspotLimit.value)
  } catch {
    hotspots.value = []
  } finally {
    loadingHotspots.value = false
  }
}

const loadAlerts = async () => {
  try {
    alertStats.value = await getAlertStats()
    alertStatus.value = await getAlertSystemStatus()
  } catch {
    // fallback defaults already set
  }
}

const loadAll = async () => {
  loading.value = true
  try {
    await Promise.all([loadStats(), loadCount(), loadHotspots(), loadAlerts()])
  } finally {
    loading.value = false
  }
}

const handleResetStats = async () => {
  try {
    await resetSlowQueryStats()
    ElMessage.success(t('slowQuery.statsResetMessage'))
    await loadAll()
  } catch {
    ElMessage.error(t('slowQuery.resetFailedMessage'))
  }
}

onMounted(loadAll)
</script>

<style scoped>
.card-title {
  font-weight: 600;
  font-size: 15px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.filter-form {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
}

.danger-text {
  color: var(--el-color-danger);
  font-weight: 600;
}
</style>
