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
            <h4>合并后配置预览</h4>
            <pre>{{ JSON.stringify(previewData.mergedConfig, null, 2) }}</pre>
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
      
      <div class="action-buttons">
        <el-button @click="handleScan" :loading="scanLoading">扫描文件</el-button>
        <el-button @click="handlePreview" :loading="previewLoading">生成预览</el-button>
        <el-button @click="handleBackup" :loading="backupLoading">备份配置</el-button>
        <el-button @click="handleMerge" :loading="mergeLoading" type="primary">执行合并</el-button>
        <el-button @click="handleCleanup" :loading="cleanupLoading">清理文件</el-button>
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
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { 
  scanVersionFiles,
  getMergePreview,
  performAutoMerge,
  backupConfigFiles,
  cleanupConfigFiles,
  getMergeServiceStatus,
  getConfigStatistics,
  performBatchOperation,
  validateConfigFiles
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

// 添加操作日志
const addLog = (message: string, type: OperationLog['type'] = 'info') => {
  operationLogs.value.push({
    timestamp: new Date().toLocaleString(),
    message,
    type
  })
}

// 获取版本文件列表
const fetchVersionFiles = async () => {
  try {
    loading.value = true
    const response = await scanVersionFiles()
    versionFiles.value = response.data.data || {}
    versionFilesList.value = Object.entries(versionFiles.value).map(([version, filePath]) => ({
      version: parseInt(version),
      filePath
    }))
    addLog('扫描版本文件完成')
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
    const response = await getMergePreview()
    previewData.value = response.data.data || {}
    activeTab.value = 'preview'
    ElMessage.success('预览生成成功')
    addLog('生成合并预览完成', 'success')
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
      if (response.data.success) {
        ElMessage.success(response.data.message || '合并成功')
        addLog(response.data.message || '执行自动合并完成', 'success')
        // 刷新数据
        handleRefresh()
      } else {
        ElMessage.error(response.data.message || '合并失败')
        addLog(response.data.message || '执行自动合并失败', 'danger')
      }
    } catch (error) {
      console.error('合并失败:', error)
      ElMessage.error('合并失败')
      addLog('执行自动合并失败', 'danger')
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
</style>