<template>
  <PageSkeleton :title="t('quotaMonitoring.pageTitle')">
    <template #actions>
      <el-button @click="loadAll" :loading="loading" size="default">
        <el-icon><Refresh /></el-icon>
        {{ t('quotaMonitoring.refresh') }}
      </el-button>
    </template>

    <!-- 顶部状态卡片 -->
    <template #stats>
      <el-row :gutter="16">
        <el-col :span="6">
          <StatCard
            :icon="status?.enabled ? 'CircleCheck' : 'CircleClose'"
            :label="t('quotaMonitoring.enabledLabel')"
            :value="status?.enabled ? t('quotaMonitoring.enabled') : t('quotaMonitoring.disabled')"
            :tone="status?.enabled ? 'success' : 'info'"
          />
        </el-col>
        <el-col :span="6">
          <StatCard
            icon="Coin"
            :label="t('quotaMonitoring.backendTypeLabel')"
            :value="status?.backendName ?? '—'"
            tone="primary"
          />
        </el-col>
        <el-col :span="6">
          <StatCard
            :icon="status?.degraded ? 'WarningFilled' : 'SuccessFilled'"
            :label="t('quotaMonitoring.degradedLabel')"
            :value="status?.degraded ? t('quotaMonitoring.degradedYes') : t('quotaMonitoring.degradedNo')"
            :tone="status?.degraded ? 'danger' : 'success'"
          />
        </el-col>
        <el-col :span="6">
          <StatCard
            icon="Calendar"
            :label="t('quotaMonitoring.windowCountLabel')"
            :value="status?.windows?.length ?? 0"
            tone="info"
          />
        </el-col>
      </el-row>
    </template>

    <!-- 降级预警 -->
    <el-alert
      v-if="status?.degraded"
      :title="t('quotaMonitoring.degradedAlertTitle')"
      type="warning"
      :closable="false"
      show-icon
      style="margin-bottom: 16px"
    >
      <template #default>
        <p v-if="status.degradedReason" style="margin: 0 0 4px">
          <strong>{{ t('quotaMonitoring.degradedReasonLabel') }}：</strong>{{ status.degradedReason }}
        </p>
        <p v-if="status.redisProbe?.reason" style="margin: 0 0 4px">
          <strong>{{ t('quotaMonitoring.redisProbeFailLabel') }}：</strong>{{ status.redisProbe.reason }}
        </p>
        <p v-if="status.counterMetrics" style="margin: 0">
          <strong>{{ t('quotaMonitoring.degradationCountLabel') }}：</strong>{{ status.counterMetrics.degradationCount }}
        </p>
      </template>
    </el-alert>

    <!-- 筛选区域 -->
    <el-card shadow="hover" style="margin-bottom: 16px">
      <template #header>
        <span class="card-title">{{ t('quotaMonitoring.filterTitle') }}</span>
      </template>
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
      </el-form>
    </el-card>

    <!-- 用量明细表格 -->
    <el-card shadow="hover" style="margin-bottom: 16px">
      <template #header>
        <span class="card-title">{{ t('quotaMonitoring.usageTitle') }}</span>
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
        <el-table-column prop="windowStart" :label="t('quotaMonitoring.windowStartColumn')" min-width="170" />
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
      </el-table>
      <el-empty
        v-if="!loadingUsage && usageList.length === 0"
        :description="status?.enabled ? t('quotaMonitoring.noUsageData') : t('quotaMonitoring.quotaNotEnabled')"
      />
    </el-card>

    <!-- 加载失败 -->
    <el-alert
      v-if="errorOccurred"
      :title="t('quotaMonitoring.loadFailedTitle')"
      :description="t('quotaMonitoring.loadFailedDescription')"
      type="warning"
      :closable="false"
      show-icon
      style="margin-top: 16px"
    />
  </PageSkeleton>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import {
  Refresh,
  Search,
  RefreshRight,
  CircleCheck,
  CircleClose,
  Coin,
  WarningFilled,
  SuccessFilled,
  Calendar,
} from '@element-plus/icons-vue'
import PageSkeleton from '@/components/PageSkeleton.vue'
import StatCard from '@/components/StatCard.vue'
import {
  getQuotaStatus,
  getQuotaUsage,
  type QuotaStatus,
  type QuotaUsageItem,
  type QuotaUsageQuery,
} from '@/api/quota'

const { t } = useI18n()

const loading = ref(false)
const loadingUsage = ref(false)
const errorOccurred = ref(false)
const status = ref<QuotaStatus | null>(null)
const usageList = ref<QuotaUsageItem[]>([])

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
  try {
    usageList.value = await getQuotaUsage({ ...query })
  } catch {
    usageList.value = []
  } finally {
    loadingUsage.value = false
  }
}

const loadAll = async () => {
  loading.value = true
  try {
    await Promise.all([loadStatus(), loadUsage()])
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

.filter-form {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
}
</style>
