<template>
  <div class="version-management">
    <el-card class="version-card">
      <template #header>
        <div class="card-header">
          <div class="header-title">
            <el-icon>
              <History />
            </el-icon>
            <span>{{ t('version.title') }}</span>
          </div>
          <el-button type="primary" @click="handleRefresh">
            <el-icon>
              <Refresh />
            </el-icon>
            {{ t('version.refresh') }}
          </el-button>
        </div>
      </template>

      <el-alert :closable="false" class="desc-alert" show-icon :title="t('version.help.title')" type="info">
        <template #description>
          <ul class="desc-list">
            <li><b>{{ t('version.help.apply') }}</b>{{ t('version.help.applyBody') }}</li>
            <li><b>{{ t('version.help.delete') }}</b>{{ t('version.help.deleteBody') }}</li>
            <li><b>{{ t('version.help.view') }}</b>{{ t('version.help.viewBody') }}</li>
          </ul>
        </template>
      </el-alert>

      <div class="table-wrap">
        <el-table v-loading="loading" :data="versions" border class="version-table" fit>
          <el-table-column align="center" :label="t('version.versionNo')" prop="version" width="110" />
          <el-table-column align="center" :label="t('version.status')" prop="status" width="120">
            <template #default="scope">
              <el-tag :type="scope.row.status === 'current' ? 'success' : 'info'" size="large">
                <el-icon v-if="scope.row.status === 'current'" style="margin-right:2px">
                  <SuccessFilled />
                </el-icon>
                {{ scope.row.status === 'current' ? t('version.currentStatus') : t('version.historyStatus') }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column align="center" :label="t('version.operationType')" prop="operation" width="130">
            <template #default="scope">
              <el-tag :type="getOperationTagType(scope.row.operation)" effect="plain">
                {{ getOperationDisplayName(scope.row.operation) }}
              </el-tag>
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
          <el-table-column align="center" fixed="right" :label="t('version.actions')" width="360">
            <template #default="scope">
              <el-button @click="handleView(scope.row)"
              >
                {{ t('version.view') }}
              </el-button>
              <el-button :disabled="scope.row.status === 'current' || applyingVersions.has(scope.row.version)"
                :loading="applyingVersions.has(scope.row.version)" type="primary" @click="handleApply(scope.row)">
                {{ t('version.apply') }}
              </el-button>
              <el-button :disabled="scope.row.status === 'current' || deletingVersions.has(scope.row.version)"
                :loading="deletingVersions.has(scope.row.version)" type="danger" @click="handleDelete(scope.row)">
                {{ t('version.delete') }}
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </el-card>

    <!-- 查看版本详情对话框 -->
    <el-dialog v-model="detailDialogVisible" class="version-dialog" :title="t('version.detailTitle')" width="620px">
      <el-descriptions :column="1" border>
        <el-descriptions-item :label="t('version.versionNo')">{{ currentVersion.version }}</el-descriptions-item>
        <el-descriptions-item :label="t('version.status')">
          <el-tag :type="currentVersion.status === 'current' ? 'success' : 'info'">
            {{ currentVersion.status === 'current' ? t('version.currentStatus') : t('version.historyStatus') }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item :label="t('version.operationType')">
          <el-tag :type="getOperationTagType(currentVersion.operation)">
            {{ getOperationDisplayName(currentVersion.operation) }}
          </el-tag>
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
        <span class="dialog-footer">
          <el-button @click="detailDialogVisible = false">{{ t('version.close') }}</el-button>
        </span>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElLoading, ElMessage, ElMessageBox } from 'element-plus'
import { applyVersion, deleteConfigVersion, getAllVersionInfo, type Version } from '@/api/version.ts'
import { formatDateTime } from '@/utils/format'

const { t } = useI18n()
const versions = ref<Version[]>([])
const detailDialogVisible = ref(false)
const currentVersion = ref({} as Version)
const loading = ref(false)
const applyingVersions = ref<Set<number>>(new Set())
const deletingVersions = ref<Set<number>>(new Set())

// 获取操作类型标签类型
const getOperationTagType = (operation: string | undefined) => {
  switch (operation) {
    case 'init':
      return 'success'
    case 'instanceChange':
      return 'primary'
    case 'apply':
      return 'primary'
    case 'serviceConfigChange':
      return 'info'
    case 'createService':
      return 'success'
    case 'updateService':
      return 'info'
    case 'deleteService':
      return 'danger'
    case 'addInstance':
      return 'success'
    case 'updateInstance':
      return 'info'
    case 'deleteInstance':
      return 'danger'
    case 'updateTracingSampling':
      return 'info'
    case 'rateLimitChange':
      return 'warning'
    case 'circuitBreakerChange':
      return 'warning'
    default:
      return 'info'
  }
}

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
    let versionInfos: any[] = []
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
    const versionDetails: Version[] = versionInfos.map((info: any) => ({
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
  detailDialogVisible.value = true
}

// 应用版本
const handleApply = async (row: Version) => {
  // 显示详细的确认对话框
  const confirmResult = await ElMessageBox.confirm(
    `
    <div>
      <p><strong>${t('version.applyDialog.heading')}</strong></p>
      <p>${t('version.applyDialog.version', { version: row.version })}</p>
      <p>${t('version.applyDialog.operation', { operation: getOperationDisplayName(row.operation) })}</p>
      <p>${t('version.applyDialog.detail', { detail: row.operationDetail || t('version.none') })}</p>
      <p style="color: var(--ja-warning); margin-top: 10px;">
        <i class="el-icon-warning"></i>
        ${t('version.applyDialog.warning')}
      </p>
    </div>
    `,
    t('version.applyDialog.title'),
    {
      confirmButtonText: t('version.applyDialog.confirm'),
      cancelButtonText: t('version.applyDialog.cancel'),
      type: 'warning',
      dangerouslyUseHTMLString: true,
      showClose: false,
      closeOnClickModal: false,
      closeOnPressEscape: false
    }
  ).catch(() => {
    // 用户取消操作
    return false
  })

  if (!confirmResult) {
    return
  }

  // 显示应用进度
  const loadingInstance = ElLoading.service({
    lock: true,
    text: t('version.applyLoading', { version: row.version }),
    spinner: 'el-icon-loading',
    background: 'rgba(0, 0, 0, 0.7)'
  })

  // 设置按钮加载状态
  applyingVersions.value.add(row.version)

  try {
    // 第一步：验证版本存在性
    loadingInstance.setText(t('version.validateLoading'))
    await new Promise(resolve => setTimeout(resolve, 300)) // 模拟验证过程

    // 第二步：应用版本配置
    loadingInstance.setText(t('version.applyConfigLoading'))
    const response = await applyVersion(row.version)

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

  } catch (error: any) {
    loadingInstance.close()

    console.error('应用版本失败:', error)

    // 详细的错误处理
    let errorTitle = t('version.errorDialog.title')
    let errorMessage = t('version.errors.unknown')
    let errorDetails = ''

    if (error?.response?.data) {
      const errorData = error.response.data
      errorMessage = errorData.message || t('version.errors.server')
      errorDetails = errorData.details || errorData.error || ''

      // 根据错误类型提供具体的错误信息
      if (errorMessage.includes('版本不存在')) {
        errorTitle = t('version.errors.versionNotFoundTitle')
        errorMessage = t('version.errors.versionNotFound', { version: row.version })
      } else if (errorMessage.includes('配置损坏')) {
        errorTitle = t('version.errors.configCorruptTitle')
        errorMessage = t('version.errors.configCorrupt', { version: row.version })
      } else if (errorMessage.includes('权限')) {
        errorTitle = t('version.errors.permissionTitle')
        errorMessage = t('version.errors.permissionDenied')
      } else if (errorMessage.includes('系统错误')) {
        errorTitle = t('version.errors.systemTitle')
        errorMessage = t('version.errors.systemError')
      }
    } else if (error?.message) {
      errorMessage = error.message
    }

    // 显示详细错误对话框
    ElMessageBox.alert(
      `
      <div>
        <p><strong>${t('version.errorDialog.details')}</strong></p>
        <p>${errorMessage}</p>
        ${errorDetails ? `<p><strong>${t('version.errorDialog.techDetails')}</strong></p><p style="color: var(--ja-text-secondary); font-size: 12px;">${errorDetails}</p>` : ''}
        <p style="margin-top: 15px; color: var(--ja-text-regular);">
          <strong>${t('version.errorDialog.advice')}</strong><br/>
          ${t('version.errorDialog.advice1')}<br/>
          ${t('version.errorDialog.advice2')}<br/>
          ${t('version.errorDialog.advice3')}
        </p>
      </div>
      `,
      errorTitle,
      {
        confirmButtonText: t('version.errorDialog.acknowledge'),
        type: 'error',
        dangerouslyUseHTMLString: true
      }
    )
  } finally {
    // 清除按钮加载状态
    applyingVersions.value.delete(row.version)
  }
}

// 删除版本
const handleDelete = async (row: Version) => {
  // 检查是否为当前版本
  if (row.status === 'current') {
    ElMessageBox.alert(
      `
      <div>
        <p><i class="el-icon-warning" style="color: var(--ja-warning);"></i> <strong>${t('version.cannotDelete.heading')}</strong></p>
        <p>${t('version.cannotDelete.message', { version: row.version })}</p>
        <p style="margin-top: 10px; color: var(--ja-text-regular);">
          <strong>${t('version.cannotDelete.advice')}</strong><br/>
          ${t('version.cannotDelete.advice1')}<br/>
          ${t('version.cannotDelete.advice2')}
        </p>
      </div>
      `,
      t('version.cannotDelete.title'),
      {
        confirmButtonText: t('version.cannotDelete.acknowledge'),
        type: 'warning',
        dangerouslyUseHTMLString: true
      }
    )
    return
  }

  // 显示详细的删除确认对话框
  const confirmResult = await ElMessageBox.confirm(
    `
    <div>
      <p><strong>${t('version.deleteDialog.heading')}</strong></p>
      <p>${t('version.deleteDialog.version', { version: row.version })}</p>
      <p>${t('version.deleteDialog.operation', { operation: getOperationDisplayName(row.operation) })}</p>
      <p>${t('version.deleteDialog.detail', { detail: row.operationDetail || t('version.none') })}</p>
      <p>${t('version.deleteDialog.createdAt', { time: formatTimestamp(row.timestamp) })}</p>
      <p style="color: var(--ja-danger); margin-top: 15px;">
        <i class="el-icon-warning"></i>
        <strong>${t('version.deleteDialog.warning')}</strong>
      </p>
      <p style="color: var(--ja-text-secondary); font-size: 12px; margin-top: 10px;">
        ${t('version.deleteDialog.hint')}
      </p>
    </div>
    `,
    t('version.deleteDialog.title'),
    {
      confirmButtonText: t('version.deleteDialog.confirm'),
      cancelButtonText: t('version.deleteDialog.cancel'),
      type: 'error',
      dangerouslyUseHTMLString: true,
      showClose: false,
      closeOnClickModal: false,
      closeOnPressEscape: false,
      buttonSize: 'default'
    }
  ).catch(() => {
    // 用户取消操作
    return false
  })

  if (!confirmResult) {
    return
  }
  await deleteConfigVersion(row.version)
  await fetchVersions()
}

// 组件挂载时获取数据
onMounted(() => {
  fetchVersions()
})
</script>

<style scoped>
.version-management {
  padding: 24px;
  background: var(--ja-main-bg-gradient);
  min-height: calc(100vh - 80px);
}

.version-card {
  box-shadow: 0 6px 20px rgba(15, 23, 42, 0.06);
  border-radius: 12px;
  padding: 0;
  width: 100%;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 18px 22px;
  gap: 12px;
  flex-wrap: wrap;
  border-bottom: 1px solid var(--ja-border-lighter);
}

.header-title {
  display: flex;
  align-items: center;
  font-size: 20px;
  font-weight: 700;
  color: var(--ja-text-primary);
  gap: 10px;
}

.desc-alert {
  margin: 22px 22px 18px 22px;
}

.desc-list {
  margin: 0 0 0 20px;
  padding: 0;
  font-size: 14px;
  color: var(--ja-text-regular);
  line-height: 1.9;
}

.table-wrap {
  padding: 0 22px 20px 22px;
}

.version-table {
  border-radius: 8px;
  background: var(--ja-bg-card);
  font-size: 14px;
}

.version-table :deep(.el-table__cell) {
  font-size: 14px;
  padding: 14px 8px;
}

.version-table :deep(.el-table__header th) {
  font-size: 14px;
  font-weight: 600;
  color: var(--ja-text-primary);
  background-color: var(--ja-primary-light-9, #fbfdff);
}

.timestamp {
  color: var(--ja-text-secondary);
  font-size: 13px;
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 15px;
  padding: 10px 20px;
}

.version-dialog :deep(.el-dialog__header) {
  background-color: var(--ja-primary-light-9, #fbfdff);
  border-bottom: 1px solid var(--ja-border-lighter);
  padding: 14px 20px;
}

.version-dialog :deep(.el-dialog__title) {
  font-weight: 700;
  color: var(--ja-text-primary);
  font-size: 18px;
}

.version-dialog :deep(.el-dialog__body) {
  padding: 20px;
}

.version-dialog :deep(.el-descriptions__header) {
  margin-bottom: 4px;
}

.version-dialog :deep(.el-descriptions__label) {
  font-weight: 600;
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
</style>