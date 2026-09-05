<template>
  <PageSkeleton title="慢查询分析">
    <template #actions>
      <el-button @click="loadAll" :loading="loading" size="default">
        <el-icon><Refresh /></el-icon>
        刷新
      </el-button>
      <el-popconfirm
        title="确认重置全部慢查询统计？"
        confirm-button-text="确认"
        cancel-button-text="取消"
        @confirm="handleResetStats"
      >
        <template #reference>
          <el-button type="danger" plain size="default">
            <el-icon><Delete /></el-icon>
            重置统计
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
            label="慢查询总数"
            :value="totalCount"
            tone="danger"
          />
        </el-col>
        <el-col :span="6">
          <StatCard
            icon="Histogram"
            label="操作种类数"
            :value="Object.keys(stats).length"
            tone="primary"
          />
        </el-col>
        <el-col :span="6">
          <StatCard
            icon="Bell"
            label="告警触发"
            :value="alertStats.totalAlertsTriggered"
            tone="warning"
          />
        </el-col>
        <el-col :span="6">
          <StatCard
            icon="Mute"
            label="告警抑制"
            :value="alertStats.totalAlertsSuppressed"
            tone="info"
          />
        </el-col>
      </el-row>
    </template>

    <!-- 筛选区域 -->
    <el-card shadow="hover" style="margin-bottom: 16px">
      <el-form :inline="true" class="filter-form">
        <el-form-item label="最低慢查询数">
          <el-input-number
            v-model="filterThreshold"
            :min="0"
            :max="999999"
            placeholder="最低次数"
            controls-position="right"
            style="width: 150px"
          />
        </el-form-item>
        <el-form-item label="服务操作">
          <el-select
            v-model="filterOperation"
            placeholder="全部操作"
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
            筛选
          </el-button>
          <el-button @click="resetFilter">
            <el-icon><RefreshRight /></el-icon>
            重置
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 慢查询列表表格 -->
    <el-card shadow="hover" style="margin-bottom: 16px">
      <template #header>
        <span class="card-title">慢查询操作列表</span>
      </template>
      <el-table
        :data="filteredTableData"
        v-loading="loading"
        stripe
        border
        style="width: 100%"
        :default-sort="{ prop: 'totalDuration', order: 'descending' }"
      >
        <el-table-column prop="operationName" label="操作名称" min-width="200" sortable />
        <el-table-column prop="count" label="慢查询次数" width="130" sortable align="center" />
        <el-table-column label="平均耗时" width="130" sortable sort-by="averageDuration" align="center">
          <template #default="{ row }">
            {{ row.averageDuration.toFixed(1) }} ms
          </template>
        </el-table-column>
        <el-table-column label="最大耗时" width="130" sortable sort-by="maxDuration" align="center">
          <template #default="{ row }">
            <span :class="{ 'danger-text': row.maxDuration > 5000 }">{{ row.maxDuration }} ms</span>
          </template>
        </el-table-column>
        <el-table-column label="最小耗时" width="130" sortable sort-by="minDuration" align="center">
          <template #default="{ row }">{{ row.minDuration }} ms</template>
        </el-table-column>
        <el-table-column label="总耗时" width="150" sortable sort-by="totalDuration" align="center">
          <template #default="{ row }">
            {{ formatDuration(row.totalDuration) }}
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!loading && filteredTableData.length === 0" description="暂无慢查询数据" />
    </el-card>

    <!-- 性能热点 -->
    <el-card shadow="hover" style="margin-bottom: 16px">
      <template #header>
        <div class="card-header">
          <span class="card-title">性能热点 Top {{ hotspotLimit }}</span>
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
        <el-table-column prop="operationName" label="操作名称" min-width="200" />
        <el-table-column label="调用次数" width="120" align="center">
          <template #default="{ row }">{{ row.stats.callCount }}</template>
        </el-table-column>
        <el-table-column label="平均耗时" width="130" align="center">
          <template #default="{ row }">{{ row.stats.averageDuration.toFixed(1) }} ms</template>
        </el-table-column>
        <el-table-column label="最大耗时" width="130" align="center">
          <template #default="{ row }">{{ row.stats.maxDuration }} ms</template>
        </el-table-column>
        <el-table-column label="总耗时" width="150" align="center">
          <template #default="{ row }">{{ formatDuration(row.totalDuration) }}</template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!loadingHotspots && hotspots.length === 0" description="暂无性能热点数据" />
    </el-card>

    <!-- 告警状态 -->
    <el-card v-if="alertStatus.alertServiceEnabled" shadow="hover">
      <template #header>
        <span class="card-title">告警系统状态</span>
      </template>
      <el-descriptions :column="3" border size="small">
        <el-descriptions-item label="告警服务">
          <el-tag type="success" size="small">已启用</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="活跃告警键">
          {{ alertStats.activeAlertKeys }}
        </el-descriptions-item>
        <el-descriptions-item label="触发率">
          {{ ((alertStatus.alertTriggerRate ?? 0) * 100).toFixed(1) }}%
        </el-descriptions-item>
        <el-descriptions-item label="抑制率">
          {{ ((alertStatus.alertSuppressionRate ?? 0) * 100).toFixed(1) }}%
        </el-descriptions-item>
        <el-descriptions-item label="每操作平均告警">
          {{ (alertStatus.averageAlertsPerOperation ?? 0).toFixed(1) }}
        </el-descriptions-item>
        <el-descriptions-item label="活跃操作数">
          {{ alertStats.activeOperations?.length ?? 0 }}
        </el-descriptions-item>
      </el-descriptions>
    </el-card>

    <!-- 数据缺失降级 -->
    <el-alert
      v-if="!loading && Object.keys(stats).length === 0 && !errorOccurred"
      title="暂无慢查询数据"
      description="当前系统中未检测到慢查询记录，请稍后刷新或检查监控配置。"
      type="info"
      :closable="false"
      show-icon
      style="margin-top: 16px"
    />
    <el-alert
      v-if="errorOccurred"
      title="数据加载失败"
      description="慢查询分析接口返回异常，请确认后端 SlowQueryAnalysisController 已就绪。"
      type="warning"
      :closable="false"
      show-icon
      style="margin-top: 16px"
    />
  </PageSkeleton>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
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
  if (ms >= 60000) return `${(ms / 60000).toFixed(1)} 分钟`
  if (ms >= 1000) return `${(ms / 1000).toFixed(1)} 秒`
  return `${ms} ms`
}

const applyFilter = () => {
  // Filter is reactive via computed; this triggers a UI feedback.
  ElMessage.success('筛选已应用')
}

const resetFilter = () => {
  filterThreshold.value = 0
  filterOperation.value = ''
  ElMessage.info('筛选已重置')
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
    ElMessage.success('慢查询统计已重置')
    await loadAll()
  } catch {
    ElMessage.error('重置失败')
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
