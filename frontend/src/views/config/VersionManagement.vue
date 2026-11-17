<template>
  <PageSkeleton>
    <template #title>
      <h2 class="page-heading">{{ t('version.title') }}</h2>
      <p class="page-subtitle">{{ t('version.description') }}</p>
    </template>

    <template #stats>
      <!-- 版本页没有「新建」，统计条只给行数型指标：记录数 / 可回退数 / 当前版本号 -->
      <MetricStrip :items="metricItems" :loading="loading" :columns="3" />
    </template>

    <template #toolbar>
      <!-- 工具栏顺序：筛选 → 刷新（本页无新建动作，主操作位置留空） -->
      <el-input
        v-model="keyword"
        :placeholder="t('version.searchPlaceholder')"
        clearable
        style="width: 260px;"
      >
        <template #prefix>
          <el-icon><Search /></el-icon>
        </template>
      </el-input>
      <el-button :loading="loading" @click="handleRefresh">
        <el-icon><Refresh /></el-icon>
        {{ t('version.refresh') }}
      </el-button>
    </template>

    <!-- 操作说明：原 el-alert 占一整块高度，收成一行提示 -->
    <p class="page-hint">
      <span class="hint-item"><b>{{ t('version.help.apply') }}</b>{{ t('version.help.applyBody') }}</span>
      <span class="hint-item"><b>{{ t('version.help.delete') }}</b>{{ t('version.help.deleteBody') }}</span>
      <span class="hint-item"><b>{{ t('version.help.view') }}</b>{{ t('version.help.viewBody') }}</span>
    </p>

    <el-table v-loading="loading" :data="pagedVersions" border class="version-table" fit>
      <template #empty>
        <!-- 空态区分「还没有数据」与「筛选无结果」（P3 规范第 4 条） -->
        <EmptyState
          icon="Clock"
          :title="keyword ? t('version.noMatchTitle') : t('version.emptyTitle')"
          :description="keyword ? t('version.noMatchHint') : t('version.emptyDescription')"
        >
          <template #actions>
            <el-button v-if="keyword" @click="keyword = ''">{{ t('version.clearFilter') }}</el-button>
            <el-button v-else :loading="loading" @click="handleRefresh">{{ t('version.refresh') }}</el-button>
          </template>
        </EmptyState>
      </template>
      <el-table-column align="center" :label="t('version.versionNo')" prop="version" width="110" />
      <el-table-column align="center" :label="t('version.status')" prop="status" width="120">
        <template #default="scope">
          <el-tag :type="scope.row.status === 'current' ? 'primary' : 'info'" size="small">
            <el-icon v-if="scope.row.status === 'current'" style="margin-right:2px">
              <SuccessFilled />
            </el-icon>
            {{ scope.row.status === 'current' ? t('version.currentStatus') : t('version.historyStatus') }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column align="center" :label="t('version.operationType')" prop="operation" width="130">
        <template #default="scope">
          <span class="cell-text">{{ getOperationDisplayName(scope.row.operation) }}</span>
        </template>
      </el-table-column>
      <el-table-column :label="t('version.operationDetail')" min-width="180" prop="operationDetail" show-overflow-tooltip>
        <template #default="scope">
          <span v-if="scope.row.operationDetail">{{ scope.row.operationDetail }}</span>
          <span v-else>-</span>
        </template>
      </el-table-column>
      <el-table-column align="center" :label="t('version.time')" prop="timestamp" width="170">
        <template #default="scope">
          <span class="timestamp">{{ formatTimestamp(scope.row.timestamp) }}</span>
        </template>
      </el-table-column>
      <el-table-column align="center" fixed="right" :label="t('version.actions')" width="240">
        <template #default="scope">
          <el-button link size="small" @click="handleView(scope.row)">
            {{ t('version.view') }}
          </el-button>
          <!-- 二次确认统一用 ConfirmAction（气泡确认），不再用 MessageBox 的 confirm 弹层 -->
          <ConfirmAction
            class="action-item"
            :title="t('version.confirmApplyTitle', { version: scope.row.version })"
            :confirm-text="t('version.confirmApply')"
            :cancel-text="t('version.confirmCancel')"
            :width="280"
            type="warning"
            :disabled="scope.row.status === 'current' || applyingVersions.has(scope.row.version)"
            :loading="applyingVersions.has(scope.row.version)"
            @confirm="handleApply(scope.row)"
          >
            <el-button plain size="small" :disabled="scope.row.status === 'current' || applyingVersions.has(scope.row.version)"
              :loading="applyingVersions.has(scope.row.version)" v-permission="'config:versions:write'">
              {{ t('version.apply') }}
            </el-button>
          </ConfirmAction>
          <ConfirmAction
            class="action-item"
            type="danger"
            :title="t('version.confirmDeleteTitle', { version: scope.row.version })"
            :confirm-text="t('version.confirmDelete')"
            :cancel-text="t('version.confirmCancel')"
            :width="280"
            :disabled="scope.row.status === 'current' || deletingVersions.has(scope.row.version)"
            :loading="deletingVersions.has(scope.row.version)"
            @confirm="handleDelete(scope.row)"
          >
            <el-button link type="danger" size="small" :disabled="scope.row.status === 'current' || deletingVersions.has(scope.row.version)"
              :loading="deletingVersions.has(scope.row.version)" v-permission="'config:versions:write'">
              {{ t('version.delete') }}
            </el-button>
          </ConfirmAction>
        </template>
      </el-table-column>
    </el-table>

    <!-- 版本记录会随配置变更持续增长，超过一页才出现分页（总数 0 时隐藏） -->
    <div v-if="filteredVersions.length > 0" class="pagination-bar">
      <el-pagination
        v-model:current-page="currentPage"
        v-model:page-size="pageSize"
        :page-sizes="[10, 20, 50]"
        :total="filteredVersions.length"
        layout="total, sizes, prev, pager, next"
      />
    </div>
  </PageSkeleton>

  <!-- 版本详情：只读抽屉（DetailDrawer 已内置 close-on-click-modal / loading 骨架） -->
  <DetailDrawer v-model="detailDrawerVisible" :title="t('version.detailTitle')" :width="detailDrawerWidth">
    <el-descriptions :column="1" border>
      <el-descriptions-item :label="t('version.versionNo')">{{ currentVersion.version }}</el-descriptions-item>
      <el-descriptions-item :label="t('version.status')">
        <el-tag :type="currentVersion.status === 'current' ? 'primary' : 'info'">
          {{ currentVersion.status === 'current' ? t('version.currentStatus') : t('version.historyStatus') }}
        </el-tag>
      </el-descriptions-item>
      <el-descriptions-item :label="t('version.operationType')">
        <span class="cell-text">{{ getOperationDisplayName(currentVersion.operation) }}</span>
      </el-descriptions-item>
      <el-descriptions-item v-if="currentVersion.operationDetail" :label="t('version.operationDetail')">
        {{ currentVersion.operationDetail }}
      </el-descriptions-item>
      <el-descriptions-item v-if="currentVersion.timestamp" :label="t('version.timestamp')">
        {{ formatTimestamp(currentVersion.timestamp) }}
      </el-descriptions-item>
    </el-descriptions>

    <el-divider />

    <div class="config-preview">
      <div class="preview-title">
        <el-icon>
          <Document />
        </el-icon>
        {{ t('version.configPreview') }}
      </div>
      <pre>{{ JSON.stringify(currentVersion.config, null, 2) }}</pre>
    </div>

    <template #footer>
      <el-button @click="detailDrawerVisible = false">{{ t('version.close') }}</el-button>
    </template>
  </DetailDrawer>

  <!--
    应用失败的详情：一次性结果展示，保留 el-dialog（规范 §二.8 允许的例外）；
    这里只读、必须显式关闭，且不承担任何编辑/确认职责。
  -->
  <el-dialog v-model="errorDialogVisible" :title="errorDialogTitle" width="480px">
    <p class="dialog-line">
      <strong>{{ t('version.errorDialog.details') }}</strong>{{ errorMessage }}
    </p>
    <p v-if="errorDetails" class="dialog-line">
      <strong>{{ t('version.errorDialog.techDetails') }}</strong>
      <span class="dialog-tech">{{ errorDetails }}</span>
    </p>
    <div class="dialog-advice">
      <strong>{{ t('version.errorDialog.advice') }}</strong>
      <p class="dialog-advice__item">{{ t('version.errorDialog.advice1') }}</p>
      <p class="dialog-advice__item">{{ t('version.errorDialog.advice2') }}</p>
      <p class="dialog-advice__item">{{ t('version.errorDialog.advice3') }}</p>
    </div>
    <template #footer>
      <el-button type="primary" @click="errorDialogVisible = false">
        {{ t('version.errorDialog.acknowledge') }}
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElLoading, ElMessage } from 'element-plus'
import type { VersionInfo } from '@/api/version'
import { getErrorMessage } from '@/utils/error'
import { applyVersion, deleteConfigVersion, getAllVersionInfo, type Version } from '@/api/version.ts'
import { formatDateTime } from '@/utils/format'
import PageSkeleton from '@/components/PageSkeleton.vue'
import MetricStrip from '@/components/MetricStrip.vue'
import ConfirmAction from '@/components/ConfirmAction.vue'
import DetailDrawer from '@/components/DetailDrawer.vue'
import EmptyState from '@/components/EmptyState.vue'
import { useViewport } from '@/composables/useViewport'
import type { MetricStripItem } from '@/components/MetricStrip.vue'
import { Document, Refresh, Search, SuccessFilled } from '@element-plus/icons-vue'

const { t } = useI18n()
const { isNarrow } = useViewport()
const versions = ref<Version[]>([])
const detailDrawerVisible = ref(false)
/** 详情抽屉：普通屏 520，窄屏占满（规范 §P3 抽屉尺寸） */
const detailDrawerWidth = computed(() => (isNarrow.value ? '100%' : 520))
const currentVersion = ref({} as Version)
const loading = ref(false)
const applyingVersions = ref<Set<number>>(new Set())
const deletingVersions = ref<Set<number>>(new Set())

// 工具栏筛选：版本号 / 操作类型 / 操作详情都可命中
const keyword = ref('')
// 前端分页：版本记录会持续增长，超过一页时给出分页
const currentPage = ref(1)
const pageSize = ref(10)

// 应用失败详情对话框（替代 MessageBox 的 alert 弹层）
const errorDialogVisible = ref(false)
const errorDialogTitle = ref('')
const errorMessage = ref('')
const errorDetails = ref('')

// 获取操作类型显示名称
const getOperationDisplayName = (operation: string | undefined) => {
  switch (operation) {
    case 'init':
      return t('version.operationNames.systemInit')
    case 'instanceChange':
      return t('version.operationNames.instanceChange')
    case 'apply':
      return t('version.operationNames.applyVersion')
    case 'serviceConfigChange':
      return t('version.operationNames.serviceConfigChange')
    case 'createService':
      return t('version.operationNames.createService')
    case 'updateService':
      return t('version.operationNames.updateService')
    case 'deleteService':
      return t('version.operationNames.deleteService')
    case 'addInstance':
      return t('version.operationNames.addInstance')
    case 'updateInstance':
      return t('version.operationNames.updateInstance')
    case 'deleteInstance':
      return t('version.operationNames.deleteInstance')
    case 'updateTracingSampling':
      return t('version.operationNames.updateTracingSampling')
    case 'rateLimitChange':
      return t('version.operationNames.rateLimitChange')
    case 'circuitBreakerChange':
      return t('version.operationNames.circuitBreakerChange')
    default:
      return operation || t('version.unknownOperation')
  }
}

// 格式化时间戳
const formatTimestamp = (timestamp: number | undefined) => {
  if (!timestamp) return '-'
  return formatDateTime(timestamp)
}

/** 统计条：只统计行数（记录总数 / 可回退的历史版本数）与当前版本号，不造指标 */
const metricItems = computed<MetricStripItem[]>(() => {
  const total = versions.value.length
  const history = versions.value.filter((item) => item.status !== 'current').length
  const current = versions.value.find((item) => item.status === 'current')
  return [
    { key: 'total', label: t('version.metricTotal'), value: total },
    { key: 'history', label: t('version.metricHistory'), value: history },
    {
      key: 'current',
      label: t('version.metricCurrent'),
      value: current ? `v${current.version}` : '-'
    }
  ]
})

/** 筛选结果：关键词同时匹配版本号、操作类型展示名与操作详情 */
const filteredVersions = computed<Version[]>(() => {
  const kw = keyword.value.trim().toLowerCase()
  if (!kw) return versions.value
  return versions.value.filter((item) => {
    const haystack = [
      String(item.version),
      getOperationDisplayName(item.operation),
      item.operationDetail || ''
    ]
      .join(' ')
      .toLowerCase()
    return haystack.includes(kw)
  })
})

/** 当前页数据（前端分页） */
const pagedVersions = computed<Version[]>(() => {
  const start = (currentPage.value - 1) * pageSize.value
  return filteredVersions.value.slice(start, start + pageSize.value)
})

// 筛选条件或每页条数变化后，把页码收敛回有效范围
watch([filteredVersions, pageSize], () => {
  const maxPage = Math.max(1, Math.ceil(filteredVersions.value.length / pageSize.value))
  if (currentPage.value > maxPage) currentPage.value = maxPage
})
watch(keyword, () => {
  currentPage.value = 1
})

// 获取版本列表（使用优化接口）
const fetchVersions = async () => {
  try {
    loading.value = true
    // 使用优化接口一次性获取所有版本信息
    const response = await getAllVersionInfo()

    // 检查响应结构
    if (!response || !response.data) {
      console.error('Invalid API response structure:', response)
      ElMessage.error(t('version.messages.invalidResponse'))
      return
    }

    // 处理可能的双重序列化问题
    let versionInfos: VersionInfo[] = []
    if (typeof response.data === 'string') {
      // 如果data是字符串，需要先解析
      try {
        const parsedData = JSON.parse(response.data)
        versionInfos = parsedData.data || [] // 注意：这里要取parsedData.data
      } catch (parseError) {
        console.error('Failed to parse response data:', parseError)
        ElMessage.error(t('version.messages.parseFailed'))
        return
      }
    } else {
      // 正常情况，data已经是对象
      versionInfos = response.data.data || []
    }

    // 转换为前端需要的格式
    const versionDetails: Version[] = versionInfos.map((info: VersionInfo) => ({
      version: info.version,
      status: info.current ? 'current' : 'history',
      config: info.config || {},
      operation: info.operation,
      operationDetail: info.operationDetail,
      timestamp: info.timestamp
    }))

    // 按版本号降序排列
    versions.value = versionDetails.sort((a, b) => b.version - a.version)
  } catch (error) {
    console.error('获取版本列表失败:', error)
    ElMessage.error(t('version.messages.fetchFailed', { message: (error as Error).message }))
  } finally {
    loading.value = false
  }
}

// 刷新数据
const handleRefresh = () => {
  fetchVersions()
}

// 查看版本详情
const handleView = (row: Version) => {
  currentVersion.value = row
  detailDrawerVisible.value = true
}

// 应用版本：二次确认由模板里的 ConfirmAction 承担，这里只做执行
const handleApply = async (row: Version) => {
  // 显示应用进度
  const loadingInstance = ElLoading.service({
    lock: true,
    text: t('version.applyLoading', { version: row.version }),
    spinner: 'el-icon-loading',
    // 遮罩色走 Element Plus 主题变量，避免写死 rgba
    background: 'var(--el-overlay-color-light)'
  })

  // 设置按钮加载状态
  applyingVersions.value.add(row.version)

  try {
    // 第一步：验证版本存在性
    loadingInstance.setText(t('version.validateLoading'))
    await new Promise(resolve => setTimeout(resolve, 300)) // 模拟验证过程

    // 第二步：应用版本配置
    loadingInstance.setText(t('version.applyConfigLoading'))
    await applyVersion(row.version)

    // 第三步：验证应用结果
    loadingInstance.setText(t('version.validateResultLoading'))
    await new Promise(resolve => setTimeout(resolve, 500))

    // 第四步：刷新配置状态
    loadingInstance.setText(t('version.refreshConfigLoading'))

    loadingInstance.close()

    // 显示成功消息
    ElMessage({
      type: 'success',
      message: t('version.applySuccess', { version: row.version }),
      duration: 3000,
      showClose: true
    })

    // 立即刷新版本列表，确保状态更新
    await fetchVersions()

  } catch (error) {
    loadingInstance.close()

    console.error('应用版本失败:', error)

    // 详细的错误处理
    let title = t('version.errorDialog.title')
    let message = t('version.errors.unknown')
    let details = ''

    // catch 的载荷是 unknown（#523）：这里按「后端错误体」形状收窄
    const err = error as {
      response?: { data?: { message?: string; details?: string; error?: string } }
    }

    if (err.response?.data) {
      const errorData = err.response.data
      message = errorData.message || t('version.errors.server')
      details = errorData.details || errorData.error || ''

      // 中文哨兵分类：后端 API 直返的 message 恒为中文原文（en 界面下也不随界面语言翻译），
      // 故按中文关键词匹配错误类别，再映射为 i18n（t()）展示文案；
      // 未命中分类时错误详情保持后端原文，无后端消息时走 i18n 通用文案。
      if (message.includes('版本不存在')) {
        title = t('version.errors.versionNotFoundTitle')
        message = t('version.errors.versionNotFound', { version: row.version })
      } else if (message.includes('配置损坏')) {
        title = t('version.errors.configCorruptTitle')
        message = t('version.errors.configCorrupt', { version: row.version })
      } else if (message.includes('权限')) {
        title = t('version.errors.permissionTitle')
        message = t('version.errors.permissionDenied')
      } else if (message.includes('系统错误')) {
        title = t('version.errors.systemTitle')
        message = t('version.errors.systemError')
      }
    } else {
      // 没有后端错误体时，用异常自带的文案；仍为空则保留上面的 i18n 通用文案
      message = getErrorMessage(error) || message
    }

    // 展示只读错误详情对话框（不再用 MessageBox）
    errorDialogTitle.value = title
    errorMessage.value = message
    errorDetails.value = details
    errorDialogVisible.value = true
  } finally {
    // 清除按钮加载状态
    applyingVersions.value.delete(row.version)
  }
}

// 删除版本：二次确认由模板里的 ConfirmAction（type="danger"）承担，这里只做执行
const handleDelete = async (row: Version) => {
  // 防御性守卫：当前版本不可删除（按钮已置灰，正常路径点不到）
  if (row.status === 'current') {
    ElMessage.warning(t('version.cannotDelete.message', { version: row.version }))
    return
  }

  deletingVersions.value.add(row.version)
  try {
    await deleteConfigVersion(row.version)
    await fetchVersions()
  } catch (error) {
    console.error('删除版本失败:', error)
    ElMessage.error(t('version.messages.deleteFailed', { message: getErrorMessage(error) || t('version.errors.unknown') }))
  } finally {
    deletingVersions.value.delete(row.version)
  }
}

// 组件挂载时获取数据
onMounted(() => {
  fetchVersions()
})
</script>

<style scoped>
/* 与其它配置页同形：18px/600 的页标题（PageSkeleton 的 scoped 样式作用不到插槽内容） */
.page-heading {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
  line-height: 1.3;
  color: var(--ja-text-primary);
}

.page-subtitle {
  margin: 4px 0 0;
  font-size: 13px;
  line-height: 1.6;
  color: var(--ja-text-secondary);
}

/* 操作说明：一行提示，替代原来的整块 el-alert */
.page-hint {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 18px;
  margin: 0 0 14px;
  font-size: 13px;
  line-height: 1.6;
  color: var(--ja-text-secondary);
}

.hint-item b {
  font-weight: 600;
  color: var(--ja-text-regular);
}

.cell-text {
  color: var(--ja-text-primary);
}

.version-table {
  border-radius: 6px;
  background: var(--ja-bg-card);
  font-size: 14px;
}

.version-table :deep(.el-table__cell) {
  font-size: 14px;
  padding: 14px 8px;
}

.version-table :deep(.el-table__header th) {
  font-size: 13px;
  font-weight: 600;
  color: var(--ja-text-primary);
  background-color: var(--el-fill-color-light);
}

.timestamp {
  color: var(--ja-text-secondary);
  font-size: 13px;
}

/* 行内操作：ConfirmAction 包住按钮后相邻间距由 span 承担，补回来 */
.action-item {
  margin-left: 8px;
}

/* 分页条：右对齐，与表格底部留白一致 */
.pagination-bar {
  display: flex;
  justify-content: flex-end;
  margin-top: var(--ja-space-4, 16px);
}

.config-preview {
  max-height: 300px;
  overflow-y: auto;
  background: var(--ja-primary-light-9, #f8f8fa);
  border-radius: 8px;
  border: 1px solid var(--ja-border-lighter);
  margin-top: 12px;
  padding: 12px 12px 0 12px;
}

.config-preview pre {
  background: transparent;
  padding: 0;
  margin: 0;
  white-space: pre-wrap;
  word-wrap: break-word;
  font-family: 'JetBrains Mono', 'Fira Mono', 'Consolas', 'Courier New', monospace;
  font-size: 12px;
  color: var(--ja-text-primary);
}

.preview-title {
  font-weight: 600;
  margin-bottom: 3px;
  color: var(--ja-text-secondary);
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 14px;
}

/* 应用失败详情：纯文本渲染，颜色走语义令牌 */
.dialog-line {
  margin: 0 0 10px;
  font-size: 13px;
  line-height: 1.7;
  color: var(--ja-text-primary);
  word-break: break-word;
}

.dialog-line strong {
  margin-right: 4px;
  font-weight: 600;
  color: var(--ja-text-regular);
}

.dialog-tech {
  font-family: var(--el-font-family-mono, monospace);
  font-size: 12px;
  color: var(--ja-text-secondary);
}

.dialog-advice {
  margin-top: 12px;
  font-size: 13px;
  line-height: 1.7;
  color: var(--ja-text-regular);
}

.dialog-advice strong {
  font-weight: 600;
}

.dialog-advice__item {
  margin: 2px 0 0;
  color: var(--ja-text-secondary);
}
</style>
