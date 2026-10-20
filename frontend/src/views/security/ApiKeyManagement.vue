<template>
  <PageSkeleton :title="t('apiKeys.title')">
    <template #actions>
      <el-button icon="Download" @click="handleExport">{{ t('apiKeys.exportConfig') }}</el-button>
      <el-button icon="Upload" @click="showImportDialog">{{ t('apiKeys.importConfig') }}</el-button>
      <el-button icon="Plus" type="primary" @click="handleCreateApiKey">{{ t('apiKeys.createApiKey') }}</el-button>
      <el-button icon="DataAnalysis" @click="goQuotaMonitoring">{{ t('apiKeys.gotoQuotaMonitoring') }}</el-button>
    </template>

    <!-- 配额告警摘要：无告警时只留一行文本，不占整卡 -->
    <p class="quota-alert-line">
      {{ quotaAlerts.length > 0
        ? t('apiKeys.quotaAlertSummaryCount', { count: quotaAlerts.length })
        : t('apiKeys.quotaAlertSummaryNone') }}
    </p>

    <!-- KPI 指标条（P1-2）：中性色，仅「已禁用/已过期 > 0」时着色 -->
    <MetricStrip class="kpi-strip" :items="kpiItems" />

    <!-- 配额告警区：仅在有告警时渲染整卡 -->
    <QuotaAlertsCard
      v-if="quotaAlerts.length > 0"
      :alerts="quotaAlerts"
      :loading="quotaAlertsLoading"
      @refresh="fetchQuotaAlerts"
    />

    <!-- 主卡片 -->
    <el-card class="main-card" shadow="hover">

      <div class="table-wrapper">
        <ApiKeyTable
          v-if="!isNarrow"
          :rows="pagedApiKeys"
          :loading="loading"
          :compact="isCompact"
          @quota="openQuotaDrawer"
          @command="handleRowCommand"
          @status-change="handleStatusChange"
        />

        <!-- 窄屏卡片列表：一行一卡，保留该页主要操作 -->
        <ApiKeyCards
          v-else
          :rows="pagedApiKeys"
          :loading="loading"
          @quota="openQuotaDrawer"
          @command="handleRowCommand"
          @status-change="handleStatusChange"
        />

        <!-- 分页组件 -->
        <div class="pagination-wrapper">
          <el-pagination
            v-model:current-page="pagination.currentPage"
            v-model:page-size="pagination.pageSize"
            :page-sizes="pagination.pageSizes"
            :total="pagination.total"
            layout="total, sizes, prev, pager, next, jumper"
            @size-change="handleSizeChange"
            @current-change="handleCurrentChange"
          />
        </div>
      </div>
    </el-card>

    <!-- 配额快捷抽屉（v3.2.1） -->
    <QuotaDrawer
      ref="quotaDrawerRef"
      :refresh="refreshAfterQuotaChange"
    />
  </PageSkeleton>

  <!-- 创建/编辑API密钥对话框 -->
  <ApiKeyFormDialog
    ref="formDialogRef"
    v-model:visible="dialogVisible"
    v-model:key-id="form.keyId"
    v-model:description="form.description"
    v-model:expires-at="form.expiresAt"
    v-model:permissions="form.permissions"
    v-model:allowed-ip-addresses="form.allowedIpAddresses"
    v-model:daily-request-limit="form.dailyRequestLimit"
    v-model:daily-token-limit="form.dailyTokenLimit"
    v-model:rate-limit-per-minute="form.rateLimitPerMinute"
    v-model:quota-alert-threshold="form.quotaAlertThreshold"
    v-model:rotation-period-days="form.rotationPeriodDays"
    :model="form"
    :title="dialogTitle"
    :is-edit="isEdit"
    :rules="keyIdRules"
    :saving="saveLoading"
    @save="handleSave"
  />

  <!-- 创建成功后弹窗，展示密钥值 -->
  <KeyValueDialog
    v-model:visible="showKeyValueDialog"
    :key-value="createdKeyValue"
    @copy="copyKeyValue"
    @close="closeKeyValueDialog"
  />

  <!-- 导入配置 / 导入结果对话框 -->
  <ImportConfigDialog
    ref="importDialogRef"
    :refresh="refreshAfterImport"
  />
</template>

<script setup lang="ts">
import { onMounted, ref, reactive, computed } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getErrorMessage } from '@/utils/error'
import { useI18n } from 'vue-i18n'
import PageSkeleton from '@/components/PageSkeleton.vue'
import MetricStrip, { type MetricStripItem } from '@/components/MetricStrip.vue'
import { useViewport } from '@/composables/useViewport'
import {
  createApiKey,
  deleteApiKey,
  disableApiKey,
  enableApiKey,
  getApiKeys,
  updateApiKey,
  resetApiKey,
  rotateApiKey,
  exportApiKeys,
  getQuotaOverview,
  getQuotaAlerts,
  resetApiKeyQuota
} from '@/api/apiKey'
import type {
  ApiKeyVO,
  ApiKeyListVO,
  ApiKeyCreationVO,
  ApiKeyCreateRequest,
  ApiKeyUpdateRequest,
  QuotaUsageDetail,
  QuotaAlertInfo
} from '@/types'
import QuotaAlertsCard from './apikeys/QuotaAlertsCard.vue'
import ApiKeyTable from './apikeys/ApiKeyTable.vue'
import ApiKeyCards from './apikeys/ApiKeyCards.vue'
import QuotaDrawer from './apikeys/QuotaDrawer.vue'
import ApiKeyFormDialog from './apikeys/ApiKeyFormDialog.vue'
import KeyValueDialog from './apikeys/KeyValueDialog.vue'
import ImportConfigDialog from './apikeys/ImportConfigDialog.vue'
import type { ApiKeyFormModel } from './apikeys/types'

const { t } = useI18n()
const router = useRouter()
const { isNarrow, isCompact } = useViewport()

const goQuotaMonitoring = () => {
  router.push('/monitoring/quota')
}

// ===== v3.2.1 配额抽屉 =====
// 抽屉的表单/详情状态与保存、重置逻辑都搬进了 apikeys/QuotaDrawer.vue；
// 这里只保留「打开某一行」的入口与「变更后刷新列表/告警」的回调。
const quotaDrawerRef = ref<InstanceType<typeof QuotaDrawer> | null>(null)

const openQuotaDrawer = (row: ApiKeyVO) => {
  void quotaDrawerRef.value?.open(row)
}

// 保存/重置配额成功后的刷新：与原 `await Promise.all([fetchApiKeys(), fetchQuotaAlerts()])` 等价
const refreshAfterQuotaChange = async (): Promise<void> => {
  await Promise.all([fetchApiKeys(), fetchQuotaAlerts()])
}

// 列表数据
const listData = reactive<ApiKeyListVO>({
  items: [],
  total: 0,
  enabledCount: 0,
  disabledCount: 0,
  expiredCount: 0,
  summary: {
    todayTotalRequests: 0,
    todaySuccessfulRequests: 0,
    todayFailedRequests: 0
  }
})

// KPI 指标条（P1-2）：默认中性，仅「已禁用 / 已过期 > 0」时给该项的值着色
const kpiItems = computed<MetricStripItem[]>(() => [
  { key: 'total', label: t('apiKeys.stats.total'), value: listData.total },
  { key: 'enabled', label: t('apiKeys.stats.enabled'), value: listData.enabledCount },
  {
    key: 'disabled',
    label: t('apiKeys.stats.disabled'),
    value: listData.disabledCount,
    tone: listData.disabledCount > 0 ? 'warning' : 'default'
  },
  {
    key: 'expired',
    label: t('apiKeys.stats.expired'),
    value: listData.expiredCount,
    tone: listData.expiredCount > 0 ? 'danger' : 'default'
  }
])

const apiKeys = ref<ApiKeyVO[]>([])
const loading = ref(false)
const saveLoading = ref(false)
const dialogVisible = ref(false)
const dialogTitle = ref('')
const isEdit = ref(false)
const formDialogRef = ref<InstanceType<typeof ApiKeyFormDialog> | null>(null)

// 分页状态
const pagination = reactive({
  currentPage: 1,
  pageSize: 20,
  pageSizes: [10, 20, 50, 100],
  total: 0
})

// 表单数据（弹窗通过逐字段 v-model 写回，见 apikeys/ApiKeyFormDialog.vue）
const form = ref<ApiKeyFormModel>({
  keyId: '',
  description: '',
  expiresAt: '',
  enabled: true,
  permissions: [] as string[],
  allowedIpAddresses: [] as string[],
  dailyRequestLimit: 0,
  dailyTokenLimit: 0,
  rateLimitPerMinute: 0,
  quotaAlertThreshold: 0.8,
  rotationPeriodDays: 0
})

// 密钥ID验证规则（computed：语言切换后校验文案即时更新）
const keyIdRules = computed(() => [
  { max: 64, message: t('apiKeys.messages.keyIdTooLong'), trigger: 'blur' }
])

// 分页数据
const pagedApiKeys = computed(() => {
  const start = (pagination.currentPage - 1) * pagination.pageSize
  const end = start + pagination.pageSize
  return apiKeys.value.slice(start, end)
})

// 分页处理
const handleSizeChange = (val: number) => {
  pagination.pageSize = val
  pagination.currentPage = 1
}

const handleCurrentChange = (val: number) => {
  pagination.currentPage = val
}

// 获取API密钥列表
const fetchApiKeys = async () => {
  loading.value = true
  try {
    const data = await getApiKeys()
    // 列表行保留后端规范日期字符串（编辑回写/日期选择器需要），表格列内再做本地化展示
    const formattedItems = data.items

    apiKeys.value = formattedItems
    listData.items = formattedItems
    listData.total = data.total
    listData.enabledCount = data.enabledCount
    listData.disabledCount = data.disabledCount
    listData.expiredCount = data.expiredCount
    listData.summary = data.summary
    pagination.total = data.total
  } catch (error) {
    ElMessage.error(t('apiKeys.messages.fetchListFailed'))
  } finally {
    // 列表 spinner 只绑定主列表请求，避免被配额接口拖死
    loading.value = false
  }
  // 配额概览/告警异步补齐，失败不影响列表
  void fetchQuotaOverview()
  void fetchQuotaAlerts()
}

// 获取配额概览数据
const fetchQuotaOverview = async () => {
  try {
    const quotaData = await getQuotaOverview()
    // 创建配额数据映射
    const quotaMap = new Map<string, QuotaUsageDetail>()
    quotaData.forEach(quota => quotaMap.set(quota.keyId, quota))

    // 将配额数据合并到列表项
    apiKeys.value = apiKeys.value.map(key => ({
      ...key,
      todayTokenUsage: quotaMap.get(key.keyId)?.todayTokenUsage ?? key.todayTokenUsage,
      todayRequestCount: quotaMap.get(key.keyId)?.todayRequestCount ?? key.todayRequestCount,
      quotaAlertTriggered: quotaMap.get(key.keyId)?.alertTriggered ?? key.quotaAlertTriggered
    }))
    listData.items = apiKeys.value
  } catch (error) {
    // 配额数据获取失败不影响列表显示
    console.error('获取配额概览失败:', error)
  }
}

// ===== 配额告警 =====
const quotaAlerts = ref<QuotaAlertInfo[]>([])
const quotaAlertsLoading = ref(false)

/**
 * 获取配额告警列表
 * 数据获取失败时优雅降级：静默记录错误，不影响页面其它功能
 */
const fetchQuotaAlerts = async () => {
  quotaAlertsLoading.value = true
  try {
    const alerts = await getQuotaAlerts()
    quotaAlerts.value = Array.isArray(alerts) ? alerts : []
  } catch (error) {
    // 告警数据获取失败不影响列表显示
    console.error('获取配额告警失败:', error)
    quotaAlerts.value = []
  } finally {
    quotaAlertsLoading.value = false
  }
}

// 创建API密钥弹窗
const handleCreateApiKey = () => {
  dialogTitle.value = t('apiKeys.createApiKey')
  isEdit.value = false
  form.value = {
    keyId: '',
    description: '',
    expiresAt: '',
    enabled: true,
    permissions: [],
    allowedIpAddresses: [],
    dailyRequestLimit: 0,
    dailyTokenLimit: 0,
    rateLimitPerMinute: 0,
    quotaAlertThreshold: 0.8,
    rotationPeriodDays: 0
  }
  dialogVisible.value = true
}

// 编辑API密钥弹窗
const handleEdit = (row: ApiKeyVO) => {
  dialogTitle.value = t('apiKeys.editApiKey')
  isEdit.value = true
  form.value = {
    keyId: row.keyId,
    description: row.description || '',
    expiresAt: row.expiresAt || '',
    enabled: row.enabled,
    permissions: row.permissions || [],
    allowedIpAddresses: [],
    dailyRequestLimit: row.dailyRequestLimit || 0,
    dailyTokenLimit: row.dailyTokenLimit || 0,
    rateLimitPerMinute: row.rateLimitPerMinute || 0,
    quotaAlertThreshold: row.quotaAlertThreshold ?? 0.8,
    rotationPeriodDays: row.rotationPeriodDays || 0
  }
  dialogVisible.value = true
}

// 删除API密钥
const handleDelete = (row: ApiKeyVO) => {
  ElMessageBox.confirm(t('apiKeys.confirmations.deleteMessage', { keyId: row.keyId }), t('apiKeys.confirmations.deleteTitle'), {
    confirmButtonText: t('apiKeys.confirmations.confirmDelete'),
    cancelButtonText: t('apiKeys.actions.cancel'),
    type: 'warning'
  }).then(async () => {
    try {
      await deleteApiKey(row.keyId)
      await fetchApiKeys()
      ElMessage.success(t('apiKeys.messages.deleteSuccess'))
    } catch (error) {
      ElMessage.error(t('apiKeys.messages.deleteFailed'))
    }
  })
}

// 重置API密钥
const handleReset = (row: ApiKeyVO) => {
  ElMessageBox.confirm(
    t('apiKeys.confirmations.resetMessage', { keyId: row.keyId }),
    t('apiKeys.confirmations.resetTitle'),
    {
      confirmButtonText: t('apiKeys.confirmations.confirmReset'),
      cancelButtonText: t('apiKeys.actions.cancel'),
      type: 'warning'
    }
  ).then(async () => {
    try {
      const response: ApiKeyCreationVO = await resetApiKey(row.keyId)
      createdKeyValue.value = response.keyValue
      showKeyValueDialog.value = true
      await fetchApiKeys()
      ElMessage.success(t('apiKeys.messages.resetSuccess'))
    } catch (error) {
      ElMessage.error(t('apiKeys.messages.resetFailedDetail', { message: getErrorMessage(error) || '' }))
    }
  })
}

// 强制轮换API密钥
const handleRotate = (row: ApiKeyVO) => {
  ElMessageBox.confirm(
    t('apiKeys.confirmations.rotateMessage', { keyId: row.keyId }),
    t('apiKeys.confirmations.rotateTitle'),
    {
      confirmButtonText: t('apiKeys.confirmations.confirmRotate'),
      cancelButtonText: t('apiKeys.actions.cancel'),
      type: 'warning'
    }
  ).then(async () => {
    try {
      const response: ApiKeyCreationVO = await rotateApiKey(row.keyId)
      createdKeyValue.value = response.keyValue
      showKeyValueDialog.value = true
      await fetchApiKeys()
      ElMessage.success(t('apiKeys.messages.rotateSuccess'))
    } catch (error) {
      ElMessage.error(t('apiKeys.messages.rotateFailedDetail', { message: getErrorMessage(error) || '' }))
    }
  })
}

// 重置API密钥每日配额
const handleResetQuota = (row: ApiKeyVO) => {
  ElMessageBox.confirm(
    t('apiKeys.confirmations.quotaResetMessage', { keyId: row.keyId }),
    t('apiKeys.confirmations.quotaResetTitle'),
    {
      confirmButtonText: t('apiKeys.confirmations.confirmReset'),
      cancelButtonText: t('apiKeys.actions.cancel'),
      type: 'warning'
    }
  ).then(async () => {
    try {
      await resetApiKeyQuota(row.keyId)
      await fetchQuotaOverview()
      ElMessage.success(t('apiKeys.messages.quotaResetSuccess'))
    } catch (error) {
      ElMessage.error(t('apiKeys.messages.quotaResetFailedDetail', { message: getErrorMessage(error) || '' }))
    }
  })
}

// 行操作「更多」下拉的命令分发：统一转交既有 handler，不复制业务逻辑
const handleRowCommand = (command: string, row: ApiKeyVO) => {
  switch (command) {
    case 'edit':
      handleEdit(row)
      break
    case 'rotate':
      handleRotate(row)
      break
    case 'reset':
      handleReset(row)
      break
    case 'resetQuota':
      handleResetQuota(row)
      break
    case 'delete':
      handleDelete(row)
      break
  }
}

// 创建成功后弹窗及密钥值处理
const showKeyValueDialog = ref(false)
const createdKeyValue = ref('')

// 保存API密钥
const handleSave = async () => {
  saveLoading.value = true
  try {
    // 校验仍在弹窗组件内部完成（与原 `if (formRef.value) await formRef.value.validate()` 等价）
    await formDialogRef.value?.validate()

    if (isEdit.value) {
      // 编辑
      const updateData: ApiKeyUpdateRequest = {
        description: form.value.description,
        expiresAt: form.value.expiresAt || undefined,
        enabled: form.value.enabled,
        permissions: form.value.permissions,
        allowedIpAddresses: form.value.allowedIpAddresses,
        dailyRequestLimit: form.value.dailyRequestLimit,
        dailyTokenLimit: form.value.dailyTokenLimit,
        rateLimitPerMinute: form.value.rateLimitPerMinute,
        quotaAlertThreshold: form.value.quotaAlertThreshold,
        rotationPeriodDays: form.value.rotationPeriodDays
      }
      await updateApiKey(form.value.keyId, updateData)
      ElMessage.success(t('apiKeys.messages.editSuccess'))
    } else {
      // 新增
      const createData: ApiKeyCreateRequest = {
        keyId: form.value.keyId || undefined,
        description: form.value.description,
        expiresAt: form.value.expiresAt || undefined,
        enabled: form.value.enabled,
        permissions: form.value.permissions,
        allowedIpAddresses: form.value.allowedIpAddresses,
        dailyRequestLimit: form.value.dailyRequestLimit,
        dailyTokenLimit: form.value.dailyTokenLimit,
        rateLimitPerMinute: form.value.rateLimitPerMinute,
        quotaAlertThreshold: form.value.quotaAlertThreshold,
        rotationPeriodDays: form.value.rotationPeriodDays
      }
      const response: ApiKeyCreationVO = await createApiKey(createData)
      createdKeyValue.value = response.keyValue
      showKeyValueDialog.value = true
      ElMessage.success(t('apiKeys.messages.createSuccess'))
    }
    dialogVisible.value = false
    await fetchApiKeys()
  } catch (error) {
    ElMessage.error(isEdit.value
      ? t('apiKeys.messages.editFailedDetail', { message: getErrorMessage(error) || '' })
      : t('apiKeys.messages.createFailedDetail', { message: getErrorMessage(error) || '' }))
  } finally {
    saveLoading.value = false
  }
}

// 复制密钥值
const copyKeyValue = () => {
  navigator.clipboard.writeText(createdKeyValue.value)
      .then(() => ElMessage.success(t('apiKeys.messages.keyCopied')))
      .catch(() => ElMessage.error(t('apiKeys.messages.copyFailed')))
}

const closeKeyValueDialog = () => {
  showKeyValueDialog.value = false
  createdKeyValue.value = ''
}

// ============ 批量导入/导出功能 ============
// 导入对话框及其文件解析、预览、结果展示都在 apikeys/ImportConfigDialog.vue 内；
// 这里只保留「打开」入口与「导入成功后刷新列表」的回调。
const importDialogRef = ref<InstanceType<typeof ImportConfigDialog> | null>(null)

// 显示导入对话框
const showImportDialog = () => {
  importDialogRef.value?.open()
}

// 导入成功后的刷新（原 `await fetchApiKeys()`）
const refreshAfterImport = async (): Promise<void> => {
  await fetchApiKeys()
}

// 执行导出
const handleExport = async () => {
  try {
    const exportData = await exportApiKeys()
    // 创建 JSON 文件并下载
    const blob = new Blob([JSON.stringify(exportData, null, 2)], { type: 'application/json' })
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = `api-keys-export-${new Date().toISOString().slice(0, 10)}.json`
    link.click()
    URL.revokeObjectURL(url)
    ElMessage.success(t('apiKeys.messages.exportCompleted', { total: exportData.total }))
  } catch (error) {
    ElMessage.error(t('apiKeys.messages.exportFailedDetail', { message: getErrorMessage(error) || '' }))
  }
}

// 状态切换
const handleStatusChange = async (row: ApiKeyVO) => {
  try {
    if (row.enabled) {
      await enableApiKey(row.keyId)
      ElMessage.success(t('apiKeys.messages.enabledDetail', { keyId: row.keyId }))
    } else {
      await disableApiKey(row.keyId)
      ElMessage.success(t('apiKeys.messages.disabledDetail', { keyId: row.keyId }))
    }
    await fetchApiKeys()
  } catch (error) {
    row.enabled = !row.enabled
    ElMessage.error(t('apiKeys.messages.statusChangeFailed'))
  }
}

// 挂载时加载
onMounted(() => {
  fetchApiKeys()
})
</script>

<style scoped>
.api-key-management {
  padding: 0;
  height: 100%;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.quota-alert-line {
  margin: 0;
  color: var(--ja-ink-3);
  font-size: 13px;
}

.kpi-strip {
  margin-bottom: 16px;
}

.main-card {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  margin: 0;
}

.main-card :deep(.el-card__body) {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  padding: 0;
}

.table-wrapper {
  flex: 1;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.desc-text {
  color: var(--ja-text-regular);
  font-size: 14px;
}

.pagination-wrapper {
  display: flex;
  justify-content: flex-end;
  padding: 16px;
  border-top: 1px solid var(--ja-border-light);
  flex-shrink: 0;
}

:deep(.el-table) {
  border-radius: 8px;
}
</style>
