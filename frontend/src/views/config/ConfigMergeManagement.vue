<template>
  <div class="config-merge-management">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>配置合并管理</span>
          <div>
            <el-button type="primary" @click="handleRefresh">刷新</el-button>
            <el-button type="success" @click="handleBatchOperation" :loading="batchLoading">一键合并</el-button>
          </div>
        </div>
      </template>

      <el-card style="margin-bottom: 20px;">
        <template #header>
          <span>合并模式设置</span>
        </template>
        <el-switch
            v-model="useVersionManager"
            active-text="版本管理系统模式"
            inactive-text="文件系统模式"
            @change="handleModeChange"
        />
        <p style="margin-top: 10px; color: #606266; font-size: 14px;">
          {{
            useVersionManager ?
                '版本管理系统模式：从配置版本管理系统获取版本数据，支持冲突检测和原子性操作' :
                '文件系统模式：直接扫描配置目录中的版本文件进行合并'
          }}
        </p>
      </el-card>

      <el-row :gutter="20">
        <el-col :span="12">
          <el-card class="stat-card">
            <template #header>
              <span>统计信息</span>
            </template>
            <el-descriptions :column="1" size="small" border>
              <el-descriptions-item label="版本文件数量">{{ statistics.totalVersionFiles || 0 }}</el-descriptions-item>
              <el-descriptions-item label="最早版本">{{ statistics.oldestVersion || '无' }}</el-descriptions-item>
              <el-descriptions-item label="最新版本">{{ statistics.newestVersion || '无' }}</el-descriptions-item>
              <el-descriptions-item label="预览可用">{{ statistics.previewAvailable ? '是' : '否' }}</el-descriptions-item>
            </el-descriptions>
          </el-card>
        </el-col>
        
        <el-col :span="12">
          <el-card class="stat-card">
            <template #header>
              <span>服务状态</span>
            </template>
            <el-descriptions :column="1" size="small" border>
              <el-descriptions-item label="可用版本文件">{{ status.availableVersionFiles || 0 }}</el-descriptions-item>
              <el-descriptions-item label="配置目录">{{ status.configDirectory || 'config' }}</el-descriptions-item>
              <el-descriptions-item label="服务状态">{{ status.serviceReady ? '就绪' : '未就绪' }}</el-descriptions-item>
            </el-descriptions>
          </el-card>
        </el-col>
      </el-row>
      
      <el-divider />
      
      <el-tabs v-model="activeTab" @tab-change="handleTabChange as any">
        <el-tab-pane label="版本文件" name="files">
          <el-table :data="versionFilesList" style="width: 100%" v-loading="loading">
            <el-table-column prop="version" label="版本号" width="100" />
            <el-table-column prop="filePath" label="文件路径" />
            <el-table-column label="操作" width="200">
              <template #default="scope">
                <el-button size="small" @click="handlePreviewFile(scope.row)">预览</el-button>
                <el-button size="small" type="danger" @click="handleDeleteFile(scope.row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
        
        <el-tab-pane label="合并预览" name="preview">
          <div v-if="previewData.mergedConfig" class="preview-content">
            <!-- 版本管理系统模式下显示额外信息 -->
            <div v-if="useVersionManager && (mergeConflicts.length > 0 || mergeWarnings.length > 0)"
                 style="margin-bottom: 20px;">
              <el-alert
                  v-if="mergeConflicts.length > 0"
                  :closable="false"
                  style="margin-bottom: 10px;"
                  title="合并冲突"
                  type="error"
              >
                <ul>
                  <li v-for="(conflict, index) in mergeConflicts" :key="index">{{ conflict }}</li>
                </ul>
              </el-alert>

              <el-alert
                  v-if="mergeWarnings.length > 0"
                  :closable="false"
                  title="合并警告"
                  type="warning"
              >
                <ul>
                  <li v-for="(warning, index) in mergeWarnings" :key="index">{{ warning }}</li>
                </ul>
              </el-alert>
            </div>

            <!-- 版本管理系统模式下显示统计信息 -->
            <div v-if="useVersionManager && previewData.mergeStatistics" style="margin-bottom: 20px;">
              <el-card>
                <template #header>
                  <span>合并统计信息</span>
                </template>
                <el-descriptions :column="2" border size="small">
                  <el-descriptions-item label="源版本数量">{{
                      previewData.mergeStatistics.sourceVersionCount || 0
                    }}
                  </el-descriptions-item>
                  <el-descriptions-item label="服务类型数量">{{
                      previewData.mergeStatistics.totalServiceTypes || 0
                    }}
                  </el-descriptions-item>
                  <el-descriptions-item label="源实例总数">{{
                      previewData.mergeStatistics.totalSourceInstances || 0
                    }}
                  </el-descriptions-item>
                  <el-descriptions-item label="合并后实例数">{{
                      previewData.mergeStatistics.mergedInstances || 0
                    }}
                  </el-descriptions-item>
                  <el-descriptions-item label="实例减少数量">{{
                      previewData.mergeStatistics.instanceReduction || 0
                    }}
                  </el-descriptions-item>
                  <el-descriptions-item label="合并后服务类型">{{
                      previewData.mergeStatistics.mergedServiceTypes || 0
                    }}
                  </el-descriptions-item>
                </el-descriptions>
              </el-card>
            </div>

            <el-card>
              <template #header>
                <div style="display: flex; justify-content: space-between; align-items: center;">
                  <span>合并后配置预览</span>
                  <div>
                    <el-button size="small" @click="copyConfig">复制配置</el-button>
                    <el-button size="small" @click="downloadConfig">下载配置</el-button>
                  </div>
                </div>
              </template>

              <!-- 配置概览 -->
              <div v-if="previewData.mergedConfig.services" style="margin-bottom: 15px;">
                <h5>配置概览</h5>
                <el-tag
                    v-for="(serviceConfig, serviceType) in previewData.mergedConfig.services"
                    :key="serviceType"
                    style="margin-right: 8px; margin-bottom: 8px;"
                    type="info"
                >
                  {{ serviceType }}: {{ serviceConfig.instances ? serviceConfig.instances.length : 0 }} 个实例
                </el-tag>
              </div>

              <!-- 详细配置 -->
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
            <el-empty description="暂无预览数据" />
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

      <!-- 操作进度显示 -->
      <el-card v-if="showProgress" style="margin-bottom: 20px;">
        <template #header>
          <span>操作进度</span>
        </template>
        <el-progress
            :percentage="operationProgress"
            :status="operationProgress === 100 ? 'success' : 'active'"
            :stroke-width="8"
        />
        <p style="margin-top: 10px; color: #606266;">{{ operationStatus }}</p>
      </el-card>

      <div class="action-buttons">
        <el-button :loading="scanLoading" @click="handleScan">
          {{ useVersionManager ? '扫描版本' : '扫描文件' }}
        </el-button>
        <el-button @click="handlePreview" :loading="previewLoading">生成预览</el-button>
        <el-button v-if="!useVersionManager" :loading="backupLoading" @click="handleBackup">备份配置</el-button>
        <el-button :loading="mergeLoading" type="primary" @click="handleMerge">
          {{ useVersionManager ? '智能合并' : '执行合并' }}
        </el-button>
        <el-button
            v-if="useVersionManager"
            :loading="atomicMergeLoading"
            type="success"
            @click="handleAtomicMerge"
        >
          原子性合并
        </el-button>
        <el-button v-if="!useVersionManager" :loading="cleanupLoading" @click="handleCleanup">清理文件</el-button>
      </div>
    </el-card>
    
    <!-- 文件预览对话框 -->
    <el-dialog v-model="previewDialogVisible" title="文件预览" width="600px">
      <div v-if="currentFileContent">
        <h4>{{ currentFilePath }}</h4>
        <pre>{{ JSON.stringify(currentFileContent, null, 2) }}</pre>
      </div>
      <div v-else>
        <el-empty description="无法加载文件内容" />
      </div>
      
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="previewDialogVisible = false">关闭</el-button>
        </span>
      </template>
    </el-dialog>
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
  getMergePreviewFromVersionManager,
  getMergeServiceStatus,
  performAtomicMergeWithVersionManager,
  performAutoMerge,
  performAutoMergeWithVersionManager,
  performBatchOperation,
  scanVersionFiles,
  scanVersionFilesFromVersionManager
} from '../../api/config'

interface VersionFile {
  version: number
  filePath: string
}

interface Statistics {
  totalVersionFiles?: number
  oldestVersion?: number | null
  newestVersion?: number | null
  previewAvailable?: boolean
}

interface ServiceStatus {
  availableVersionFiles?: number
  configDirectory?: string
  serviceReady?: boolean
}

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
const atomicMergeLoading = ref(false)

const versionFiles = ref<Record<number, string>>({})
const versionFilesList = ref<VersionFile[]>([])
const previewData = ref<Record<string, any>>({})
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

const previewDialogVisible = ref(false)
const currentFileContent = ref<any>(null)
const currentFilePath = ref('')

// 版本管理系统集成相关状态
const useVersionManager = ref(true) // 默认使用版本管理系统
const versionManagerData = ref<Record<string, any>>({})
const mergeConflicts = ref<string[]>([])
const mergeWarnings = ref<string[]>([])

// 进度和状态相关
const operationProgress = ref(0)
const operationStatus = ref('')
const showProgress = ref(false)

// 配置预览相关
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
    let response

    if (useVersionManager.value) {
      response = await scanVersionFilesFromVersionManager()
      addLog('从版本管理系统扫描版本完成')
    } else {
      response = await scanVersionFiles()
      addLog('扫描版本文件完成')
    }
    
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

// 模式切换处理
const handleModeChange = (value: boolean) => {
  addLog(`切换到${value ? '版本管理系统' : '文件系统'}模式`, 'info')
  // 清空当前数据
  versionFiles.value = {}
  versionFilesList.value = []
  previewData.value = {}
  mergeConflicts.value = []
  mergeWarnings.value = []
  // 重新获取数据
  handleRefresh()
}

// 获取统计信息
const fetchStatistics = async () => {
  try {
    const response = await getConfigStatistics()
    statistics.value = response.data.data || {}
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
    status.value = response.data.data || {}
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
    let response

    if (useVersionManager.value) {
      response = await getMergePreviewFromVersionManager()
      addLog('生成版本管理系统合并预览完成', 'success')
    } else {
      response = await getMergePreview()
      addLog('生成合并预览完成', 'success')
    }
    
    previewData.value = response.data.data || {}

    // 版本管理系统模式下处理冲突和警告信息
    if (useVersionManager.value && previewData.value.mergeStatistics) {
      // 这里可以从后端获取冲突和警告信息
      // 暂时模拟一些数据
      mergeConflicts.value = []
      mergeWarnings.value = []
    }
    
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
  const confirmMessage = useVersionManager.value
      ? '确定要执行智能配置合并吗？系统将使用版本管理接口进行合并，支持冲突检测。'
      : '确定要执行配置合并吗？此操作不可逆。'

  ElMessageBox.confirm(confirmMessage, '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(async () => {
    try {
      mergeLoading.value = true
      let response

      if (useVersionManager.value) {
        response = await performAutoMergeWithVersionManager()
        addLog('执行版本管理系统自动合并完成', 'success')
      } else {
        response = await performAutoMerge()
        addLog('执行自动合并完成', 'success')
      }
      
      if (response.data.success) {
        ElMessage.success(response.data.message || '合并成功')

        // 版本管理系统模式下显示更详细的结果信息
        if (useVersionManager.value && response.data.data) {
          const result = response.data.data
          if (result.conflicts && result.conflicts.length > 0) {
            ElMessage.warning(`合并完成，但发现 ${result.conflicts.length} 个冲突`)
          }
        }
        
        // 刷新数据
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

// 执行原子性合并
const handleAtomicMerge = async () => {
  ElMessageBox.confirm(
      '确定要执行原子性配置合并吗？此操作支持错误恢复，如果合并失败将自动回滚到原始状态。',
      '原子性合并确认',
      {
        confirmButtonText: '确定执行',
        cancelButtonText: '取消',
        type: 'info'
      }
  ).then(async () => {
    try {
      atomicMergeLoading.value = true
      startProgress('准备执行原子性合并...')

      // 模拟进度步骤
      updateProgress(20, '验证版本数据...')
      await new Promise(resolve => setTimeout(resolve, 500))

      updateProgress(40, '分析配置冲突...')
      await new Promise(resolve => setTimeout(resolve, 500))

      updateProgress(60, '执行配置合并...')
      const response = await performAtomicMergeWithVersionManager()

      updateProgress(80, '验证合并结果...')
      await new Promise(resolve => setTimeout(resolve, 300))

      if (response.data.success) {
        completeProgress('原子性合并完成！')
        ElMessage.success(response.data.message || '原子性合并成功')
        addLog('执行原子性合并完成', 'success')

        // 显示详细的合并结果
        if (response.data.data) {
          const result = response.data.data
          if (result.conflicts && result.conflicts.length > 0) {
            ElMessage.warning(`合并完成，处理了 ${result.conflicts.length} 个冲突`)
            mergeConflicts.value = result.conflicts
          }
          if (result.warnings && result.warnings.length > 0) {
            addLog(`合并过程中有 ${result.warnings.length} 个警告`, 'warning')
            mergeWarnings.value = result.warnings
          }
        }

        // 刷新数据
        setTimeout(() => {
          handleRefresh()
        }, 1000)
      } else {
        hideProgress()
        ElMessage.error(response.data.message || '原子性合并失败')
        addLog(response.data.message || '执行原子性合并失败', 'danger')
      }
    } catch (error: any) {
      hideProgress()
      console.error('原子性合并失败:', error)

      // 详细的错误处理和用户友好的错误显示
      let errorTitle = '原子性合并失败'
      let errorMessage = '未知错误'
      let errorDetails = ''

      if (error?.response?.data) {
        const errorData = error.response.data
        errorMessage = errorData.message || '服务器返回错误'
        errorDetails = errorData.details || errorData.error || ''

        // 根据错误类型提供具体的错误信息
        if (errorMessage.includes('冲突')) {
          errorTitle = '配置冲突'
          errorMessage = '发现严重配置冲突，合并已中止'
        } else if (errorMessage.includes('版本')) {
          errorTitle = '版本错误'
          errorMessage = '版本数据异常，无法执行合并'
        } else if (errorMessage.includes('权限')) {
          errorTitle = '权限不足'
          errorMessage = '您没有权限执行此操作'
        }
      } else if (error?.message) {
        errorMessage = error.message
      }

      // 显示详细错误对话框
      ElMessageBox.alert(
          `
        <div>
          <p><strong>错误详情：</strong></p>
          <p>${errorMessage}</p>
          ${errorDetails ? `<p><strong>技术详情：</strong></p><p style="color: #909399; font-size: 12px;">${errorDetails}</p>` : ''}
          <p style="margin-top: 15px; color: #606266;">
            <strong>可能的原因：</strong><br/>
            1. 配置版本存在严重冲突<br/>
            2. 网络连接问题<br/>
            3. 服务器内部错误<br/>
            4. 权限不足
          </p>
          <p style="margin-top: 10px; color: #606266;">
            <strong>建议操作：</strong><br/>
            1. 检查配置版本状态<br/>
            2. 先生成预览查看冲突<br/>
            3. 确认网络连接正常<br/>
            4. 如问题持续，请联系系统管理员
          </p>
        </div>
        `,
          errorTitle,
          {
            confirmButtonText: '我知道了',
            type: 'error',
            dangerouslyUseHTMLString: true
          }
      )

      addLog(`执行原子性合并失败: ${errorMessage}`, 'danger')
    } finally {
      atomicMergeLoading.value = false
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
        // 刷新数据
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
        // 刷新数据
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

// 预览文件内容
const handlePreviewFile = async (row: VersionFile) => {
  try {
    // 这里应该调用一个API来读取文件内容，暂时用模拟数据
    currentFilePath.value = row.filePath
    // 模拟文件内容
    currentFileContent.value = {
      version: row.version,
      message: "这是版本文件的内容预览",
      services: {
        chat: {
          instances: [
            {
              name: "chat-service-1",
              baseUrl: "http://localhost:8080"
            }
          ]
        }
      }
    }
    previewDialogVisible.value = true
    addLog(`预览文件: ${row.filePath}`, 'info')
  } catch (error) {
    console.error('预览文件失败:', error)
    ElMessage.error('预览文件失败')
    addLog(`预览文件失败: ${row.filePath}`, 'danger')
  }
}

// 删除文件
const handleDeleteFile = (row: VersionFile) => {
  ElMessageBox.confirm(`确定要删除版本 ${row.version} 的配置文件吗？此操作不可恢复。`, '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(async () => {
    try {
      // 这里应该调用一个API来删除文件，暂时模拟操作
      ElMessage.success('文件删除成功')
      addLog(`删除文件: ${row.filePath}`, 'success')
      // 刷新数据
      await fetchVersionFiles()
    } catch (error) {
      console.error('删除文件失败:', error)
      ElMessage.error('删除文件失败')
      addLog(`删除文件失败: ${row.filePath}`, 'danger')
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

// 组件挂载时获取数据
onMounted(() => {
  handleRefresh()
})
</script>

<style scoped>
.config-merge-management {
  padding: 20px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.stat-card {
  margin-bottom: 20px;
}

.action-buttons {
  margin-top: 20px;
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}

.preview-content pre {
  background-color: #f5f5f5;
  padding: 15px;
  border-radius: 4px;
  max-height: 500px;
  overflow-y: auto;
  white-space: pre-wrap;
  word-wrap: break-word;
  font-family: 'Courier New', monospace;
  font-size: 12px;
}

.no-preview {
  text-align: center;
  padding: 40px 0;
}

.config-detail {
  max-height: 600px;
  overflow-y: auto;
}

.config-section {
  background-color: #f8f9fa;
  padding: 12px;
  border-radius: 4px;
  border-left: 4px solid #409eff;
  white-space: pre-wrap;
  word-wrap: break-word;
  font-family: 'Courier New', monospace;
  font-size: 12px;
  line-height: 1.4;
  margin: 0;
}

.config-section:hover {
  background-color: #f0f2f5;
}
</style>