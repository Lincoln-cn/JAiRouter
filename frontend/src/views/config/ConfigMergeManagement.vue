<template>
  <div class="config-merge-management">
    <el-card class="merge-card">
      <template #header>
        <div class="card-header">
          <div class="header-title">
            <el-icon>
              <Rank/>
            </el-icon>
            <span>配置合并管理</span>
          </div>
          <div>
            <el-button type="primary" @click="handleRefresh">
              <el-icon>
                <Refresh/>
              </el-icon>
              刷新
            </el-button>
            <el-button :loading="batchLoading" type="success" @click="handleBatchOperation">
              <el-icon>
                <Lightning/>
              </el-icon>
              一键合并
            </el-button>
          </div>
        </div>
      </template>

      <el-row :gutter="24" class="stat-row">
        <el-col :span="12">
          <el-card class="stat-card" shadow="hover">
            <template #header>
              <span><el-icon><DataLine/></el-icon> 统计信息</span>
            </template>
            <el-descriptions :column="1" size="small" border>
              <el-descriptions-item label="版本文件数量">{{ statistics.totalVersionFiles || 0 }}</el-descriptions-item>
              <el-descriptions-item label="最早版本">{{ statistics.oldestVersion || '无' }}</el-descriptions-item>
              <el-descriptions-item label="最新版本">{{ statistics.newestVersion || '无' }}</el-descriptions-item>
              <el-descriptions-item label="预览可用">{{
                  statistics.previewAvailable ? '是' : '否'
                }}
              </el-descriptions-item>
            </el-descriptions>
          </el-card>
        </el-col>
        <el-col :span="12">
          <el-card class="stat-card" shadow="hover">
            <template #header>
              <span><el-icon><Connection/></el-icon> 服务状态</span>
            </template>
            <el-descriptions :column="1" size="small" border>
              <el-descriptions-item label="可用版本文件">{{ status.availableVersionFiles || 0 }}</el-descriptions-item>
              <el-descriptions-item label="配置目录">{{ status.configDirectory || 'config' }}</el-descriptions-item>
              <el-descriptions-item label="服务状态">
                <el-tag :type="status.serviceReady ? 'success' : 'danger'">
                  {{ status.serviceReady ? '就绪' : '未就绪' }}
                </el-tag>
              </el-descriptions-item>
            </el-descriptions>
          </el-card>
        </el-col>
      </el-row>

      <el-divider/>

      <el-tabs v-model="activeTab" class="merge-tabs" type="border-card" @tab-change="handleTabChange as any">
        <el-tab-pane label="版本文件" name="files">
          <el-table v-loading="loading" :data="versionFilesList" border class="file-table" fit>
            <el-table-column align="center" label="版本号" prop="version" width="200"/>
            <el-table-column label="文件路径" min-width="280" prop="filePath" show-overflow-tooltip/>
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="合并预览" name="preview">
          <div v-if="previewData.mergedConfig" class="preview-content">
            <el-card shadow="never">
              <template #header>
                <div class="preview-header">
                  <span><el-icon><Document/></el-icon> 合并后配置预览</span>
                  <div>
                    <el-button icon="el-icon-document-copy" size="small" @click="copyConfig">复制配置</el-button>
                    <el-button icon="el-icon-download" size="small" @click="downloadConfig">下载配置</el-button>
                  </div>
                </div>
              </template>
              <div v-if="previewData.mergedConfig.services" class="overview-tags">
                <el-tag
                    v-for="(serviceConfig, serviceType) in previewData.mergedConfig.services"
                    :key="serviceType"
                    type="info"
                >
                  {{ serviceType }}: {{ serviceConfig.instances ? serviceConfig.instances.length : 0 }} 个实例
                </el-tag>
              </div>
              <div class="config-detail">
                <el-collapse v-model="activeConfigSections">
                  <el-collapse-item
                      v-for="(value, key) in previewData.mergedConfig"
                      :key="key"
                      :name="key"
                      :title="key"
                  >
                    <pre class="config-section">{{ JSON.stringify(value, null, 2) }}</pre>
                  </el-collapse-item>
                </el-collapse>
              </div>
            </el-card>
          </div>
          <div v-else class="no-preview">
            <el-empty description="暂无预览数据"/>
          </div>
        </el-tab-pane>

        <el-tab-pane label="操作日志" name="logs">
          <el-timeline>
            <el-timeline-item
                v-for="(log, index) in operationLogs"
                :key="index"
                :timestamp="log.timestamp"
                :type="log.type"
            >
              {{ log.message }}
            </el-timeline-item>
          </el-timeline>
        </el-tab-pane>
      </el-tabs>

      <el-card v-if="showProgress" class="progress-card" shadow="never">
        <template #header>
          <span><el-icon><Loading/></el-icon> 操作进度</span>
        </template>
        <el-progress
            :percentage="operationProgress"
            :status="operationProgress === 100 ? 'success' : 'active'"
            :stroke-width="8"
        />
        <p class="progress-status">{{ operationStatus }}</p>
      </el-card>

      <div class="action-buttons">
        <el-button :loading="scanLoading" @click="handleScan">
          <el-icon>
            <Search/>
          </el-icon>
          扫描文件
        </el-button>
        <el-button :loading="previewLoading" @click="handlePreview">
          <el-icon>
            <View/>
          </el-icon>
          生成预览
        </el-button>
        <el-button :loading="backupLoading" @click="handleBackup">
          <el-icon>
            <Upload/>
          </el-icon>
          备份配置
        </el-button>
        <el-button :loading="mergeLoading" type="primary" @click="handleMerge">
          <el-icon>
            <Switch/>
          </el-icon>
          执行合并
        </el-button>
        <el-button :loading="cleanupLoading" @click="handleCleanup">
          <el-icon>
            <Delete/>
          </el-icon>
          清理文件
        </el-button>
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import {onMounted, ref} from 'vue'
import {ElMessage, ElMessageBox} from 'element-plus'
import {
  backupConfigFiles,
  cleanupConfigFiles,
  getConfigStatistics,
  getMergePreview,
  getMergeServiceStatus,
  performAutoMerge,
  performBatchOperation,
  scanVersionFiles,
} from '@/api/config.ts'

import type {MergePreviewData, ServiceStatus, Statistics, VersionFile} from '@/types'
import {
  Connection,
  DataLine,
  Delete,
  Document,
  Lightning,
  Loading,
  Rank,
  Refresh,
  Search,
  Switch,
  Upload,
  View,
} from '@element-plus/icons-vue'

interface OperationLog {
  timestamp: string
  message: string
  type: 'primary' | 'success' | 'warning' | 'danger' | 'info'
}

const activeTab = ref('files')
const loading = ref(false)
const scanLoading = ref(false)
const previewLoading = ref(false)
const backupLoading = ref(false)
const mergeLoading = ref(false)
const cleanupLoading = ref(false)
const batchLoading = ref(false)

const versionFiles = ref<Record<number, string>>({})
const versionFilesList = ref<VersionFile[]>([])
const previewData = ref<MergePreviewData>({})
const statistics = ref<Statistics>({
  totalVersionFiles: 0,
  oldestVersion: null,
  newestVersion: null,
  previewAvailable: false
})
const status = ref<ServiceStatus>({
  availableVersionFiles: 0,
  configDirectory: 'config',
  serviceReady: true
})

const operationLogs = ref<OperationLog[]>([])

const operationProgress = ref(0)
const operationStatus = ref('')
const showProgress = ref(false)
const activeConfigSections = ref<string[]>(['services'])

// 添加操作日志
const addLog = (message: string, type: OperationLog['type'] = 'info') => {
  operationLogs.value.push({
    timestamp: new Date().toLocaleString(),
    message,
    type
  })
}

// 进度管理辅助函数
const startProgress = (initialStatus: string) => {
  showProgress.value = true
  operationProgress.value = 0
  operationStatus.value = initialStatus
}

const updateProgress = (percentage: number, status: string) => {
  operationProgress.value = percentage
  operationStatus.value = status
}

const completeProgress = (finalStatus: string) => {
  operationProgress.value = 100
  operationStatus.value = finalStatus
  setTimeout(() => {
    showProgress.value = false
  }, 2000)
}

const hideProgress = () => {
  showProgress.value = false
  operationProgress.value = 0
  operationStatus.value = ''
}

// 复制配置到剪贴板
const copyConfig = async () => {
  try {
    const configText = JSON.stringify(previewData.value.mergedConfig, null, 2)
    await navigator.clipboard.writeText(configText)
    ElMessage.success('配置已复制到剪贴板')
    addLog('复制配置到剪贴板', 'success')
  } catch (error) {
    console.error('复制失败:', error)
    ElMessage.error('复制失败，请手动选择复制')
    addLog('复制配置失败', 'danger')
  }
}

// 下载配置文件
const downloadConfig = () => {
  try {
    const configText = JSON.stringify(previewData.value.mergedConfig, null, 2)
    const blob = new Blob([configText], {type: 'application/json'})
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = `merged-config-${new Date().toISOString().slice(0, 19).replace(/:/g, '-')}.json`
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    URL.revokeObjectURL(url)

    ElMessage.success('配置文件下载成功')
    addLog('下载合并配置文件', 'success')
  } catch (error) {
    console.error('下载失败:', error)
    ElMessage.error('下载失败')
    addLog('下载配置文件失败', 'danger')
  }
}

// 获取版本文件列表
const fetchVersionFiles = async () => {
  try {
    loading.value = true
    const response = await scanVersionFiles()
    addLog('扫描版本文件完成')
    versionFiles.value = response.data.data || {}
    versionFilesList.value = Object.entries(versionFiles.value).map(([version, filePath]) => ({
      version: parseInt(version),
      filePath
    }))
  } catch (error) {
    console.error('获取版本文件列表失败:', error)
    ElMessage.error('获取版本文件列表失败')
    addLog('获取版本文件列表失败', 'danger')
  } finally {
    loading.value = false
  }
}

// 获取统计信息
const fetchStatistics = async () => {
  try {
    const response = await getConfigStatistics()
    statistics.value = response.data.data as Statistics || {}
    addLog('获取统计信息完成')
  } catch (error) {
    console.error('获取统计信息失败:', error)
    ElMessage.error('获取统计信息失败')
    addLog('获取统计信息失败', 'danger')
  }
}

// 获取服务状态
const fetchServiceStatus = async () => {
  try {
    const response = await getMergeServiceStatus()
    status.value = response.data.data as ServiceStatus || {}
    addLog('获取服务状态完成')
  } catch (error) {
    console.error('获取服务状态失败:', error)
    ElMessage.error('获取服务状态失败')
    addLog('获取服务状态失败', 'danger')
  }
}

// 刷新所有数据
const handleRefresh = () => {
  fetchVersionFiles()
  fetchStatistics()
  fetchServiceStatus()
}

// 扫描文件
const handleScan = async () => {
  try {
    scanLoading.value = true
    await fetchVersionFiles()
    ElMessage.success('扫描完成')
    addLog('扫描版本文件完成', 'success')
  } catch (error) {
    console.error('扫描失败:', error)
    ElMessage.error('扫描失败')
    addLog('扫描失败', 'danger')
  } finally {
    scanLoading.value = false
  }
}

// 生成预览
const handlePreview = async () => {
  try {
    previewLoading.value = true
    const response = await getMergePreview()
    addLog('生成合并预览完成', 'success')
    previewData.value = response.data.data as MergePreviewData || {}
    activeTab.value = 'preview'
    ElMessage.success('预览生成成功')
  } catch (error) {
    console.error('生成预览失败:', error)
    ElMessage.error('生成预览失败')
    addLog('生成预览失败', 'danger')
  } finally {
    previewLoading.value = false
  }
}

// 备份配置
const handleBackup = async () => {
  try {
    backupLoading.value = true
    const response = await backupConfigFiles()
    if (response.data.success) {
      ElMessage.success(response.data.message || '备份成功')
      addLog(response.data.message || '备份配置文件完成', 'success')
    } else {
      ElMessage.error(response.data.message || '备份失败')
      addLog(response.data.message || '备份配置文件失败', 'danger')
    }
  } catch (error) {
    console.error('备份失败:', error)
    ElMessage.error('备份失败')
    addLog('备份配置文件失败', 'danger')
  } finally {
    backupLoading.value = false
  }
}

// 执行合并
const handleMerge = async () => {
  ElMessageBox.confirm('确定要执行配置合并吗？此操作不可逆。', '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(async () => {
    try {
      mergeLoading.value = true
      const response = await performAutoMerge()
      addLog('执行自动合并完成', 'success')

      if (response.data.success) {
        ElMessage.success(response.data.message || '合并成功')
        handleRefresh()
      } else {
        ElMessage.error(response.data.message || '合并失败')
        addLog(response.data.message || '执行自动合并失败', 'danger')
      }
    } catch (error: any) {
      console.error('合并失败:', error)
      const errorMessage = error?.response?.data?.message || error?.message || '合并失败'
      ElMessage.error(errorMessage)
      addLog(`执行自动合并失败: ${errorMessage}`, 'danger')
    } finally {
      mergeLoading.value = false
    }
  }).catch(() => {
    // 用户取消操作
  })
}

// 清理文件
const handleCleanup = async () => {
  ElMessageBox.confirm('确定要清理配置文件吗？', '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(async () => {
    try {
      cleanupLoading.value = true
      const response = await cleanupConfigFiles(true)
      if (response.data.success) {
        ElMessage.success(response.data.message || '清理成功')
        addLog(response.data.message || '清理配置文件完成', 'success')
        handleRefresh()
      } else {
        ElMessage.error(response.data.message || '清理失败')
        addLog(response.data.message || '清理配置文件失败', 'danger')
      }
    } catch (error) {
      console.error('清理失败:', error)
      ElMessage.error('清理失败')
      addLog('清理配置文件失败', 'danger')
    } finally {
      cleanupLoading.value = false
    }
  }).catch(() => {
    // 用户取消操作
  })
}

// 一键合并（批量操作）
const handleBatchOperation = async () => {
  ElMessageBox.confirm('确定要执行一键合并吗？系统将依次执行备份、合并、清理操作。', '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(async () => {
    try {
      batchLoading.value = true
      const response = await performBatchOperation(true)
      if (response.data.success) {
        ElMessage.success('一键合并完成')
        addLog('批量操作完成', 'success')
        handleRefresh()
      } else {
        ElMessage.error(response.data.message || '批量操作失败')
        addLog(response.data.message || '批量操作失败', 'danger')
      }
    } catch (error) {
      console.error('批量操作失败:', error)
      ElMessage.error('批量操作失败')
      addLog('批量操作失败', 'danger')
    } finally {
      batchLoading.value = false
    }
  }).catch(() => {
    // 用户取消操作
  })
}

// 标签页切换
const handleTabChange = (tabName: string) => {
  if (tabName === 'preview' && Object.keys(previewData.value).length === 0) {
    handlePreview()
  }
}

onMounted(() => {
  handleRefresh()
})
</script>

<style scoped>
.config-merge-management {
  padding: 24px;
  background: linear-gradient(180deg, #f7f9fc 0%, #ffffff 100%);
  min-height: calc(100vh - 80px);
}

.merge-card {
  border-radius: 14px;
  box-shadow: 0 8px 32px rgba(15, 23, 42, 0.07);
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
  border-bottom: 1px solid #eef2f6;
}

.header-title {
  display: flex;
  align-items: center;
  font-size: 20px;
  font-weight: 700;
  color: #1f2d3d;
  gap: 10px;
}

.mode-card {
  margin: 0 0 18px 0;
  background: #f9fafc;
  border-radius: 8px;
}

.mode-desc {
  margin-top: 10px;
  color: #606266;
  font-size: 14px;
  padding-left: 4px;
}

.stat-row {
  margin-bottom: 8px;
}

.stat-card {
  border-radius: 10px;
  margin-bottom: 0;
  background: #fcfcfe;
}

.merge-tabs {
  margin: 16px 0 0 0;
}

.file-table {
  border-radius: 6px;
  background: #fff;
}

.file-table :deep(.el-table__cell) {
  font-size: 14px;
  padding: 12px;
}

.file-table :deep(.el-table__header th) {
  font-size: 14px;
  font-weight: 600;
  color: #2b3a4b;
  background-color: #fbfdff;
}

.preview-content {
  margin-bottom: 22px;
}

.preview-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.overview-tags {
  margin-bottom: 10px;
}

.overview-tags .el-tag {
  margin-right: 8px;
  margin-bottom: 6px;
  font-size: 13px;
  padding: 6px 12px;
}

.config-detail {
  max-height: 540px;
  overflow-y: auto;
  margin-top: 8px;
}

.config-section {
  background-color: #f8f9fa;
  padding: 14px;
  border-radius: 4px;
  border-left: 4px solid #409eff;
  white-space: pre-wrap;
  word-wrap: break-word;
  font-family: 'JetBrains Mono', 'Fira Mono', 'Consolas', 'Courier New', monospace;
  font-size: 13px;
  margin: 0;
  line-height: 1.5;
}

.config-section:hover {
  background-color: #f0f2f5;
}

.no-preview {
  text-align: center;
  padding: 44px 0;
}

.progress-card {
  margin: 22px 0 0 0;
  border-radius: 8px;
  background: #f8fafd;
}

.progress-status {
  margin-top: 10px;
  color: #606266;
}

.action-buttons {
  margin-top: 20px;
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
  padding-left: 3px;
}

.file-dialog :deep(.el-dialog__header) {
  background-color: #fbfdff;
  border-bottom: 1px solid #eef2f6;
  padding: 14px 20px;
}

.file-dialog :deep(.el-dialog__title) {
  font-weight: 700;
  color: #1f2d3d;
  font-size: 18px;
}

.file-dialog :deep(.el-dialog__body) {
  padding: 20px;
}

.file-preview {
  background-color: #f5f5f5;
  padding: 14px;
  border-radius: 5px;
  max-height: 400px;
  overflow-y: auto;
  font-family: 'JetBrains Mono', 'Fira Mono', 'Consolas', 'Courier New', monospace;
  font-size: 13px;
  white-space: pre-wrap;
  margin: 0;
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 15px;
  padding: 10px 20px;
}
</style>