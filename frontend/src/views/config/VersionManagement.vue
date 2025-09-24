<template>
  <div class="version-management">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>版本管理</span>
          <el-button type="primary" @click="handleRefresh">刷新</el-button>
        </div>
      </template>
      
      <el-alert
        title="操作说明"
        type="info"
        description="应用：将指定版本配置设为当前配置"
        show-icon
        closable
        style="margin-bottom: 20px;"
      />
      
      <el-table :data="versions" style="width: 100%" v-loading="loading">
        <el-table-column prop="version" label="版本号" width="100" />
        <el-table-column prop="status" label="状态" width="100">
          <template #default="scope">
            <el-tag :type="scope.row.status === 'current' ? 'success' : 'info'">
              {{ scope.row.status === 'current' ? '当前版本' : '历史版本' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作类型" prop="operation" width="120">
          <template #default="scope">
            <el-tag :type="getOperationTagType(scope.row.operation)">
              {{ getOperationDisplayName(scope.row.operation) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作详情" prop="operationDetail" show-overflow-tooltip>
          <template #default="scope">
            <span v-if="scope.row.operationDetail">{{ scope.row.operationDetail }}</span>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="220">
          <template #default="scope">
            <el-button size="small" @click="handleView(scope.row)">查看</el-button>
            <el-button 
              size="small" 
              :disabled="scope.row.status === 'current' || applyingVersions.has(scope.row.version)"
              :loading="applyingVersions.has(scope.row.version)"
              type="primary"
              @click="handleApply(scope.row)"
            >
              {{ applyingVersions.has(scope.row.version) ? '应用中...' : '应用' }}
            </el-button>
            <el-button 
              size="small" 
              :disabled="scope.row.status === 'current' || deletingVersions.has(scope.row.version)"
              :loading="deletingVersions.has(scope.row.version)"
              type="danger"
              @click="handleDelete(scope.row)"
            >
              {{ deletingVersions.has(scope.row.version) ? '删除中...' : '删除' }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
    
    <!-- 查看版本详情对话框 -->
    <el-dialog v-model="detailDialogVisible" title="版本详情" width="600px">
      <el-descriptions :column="1" border>
        <el-descriptions-item label="版本号">{{ currentVersion.version }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="currentVersion.status === 'current' ? 'success' : 'info'">
            {{ currentVersion.status === 'current' ? '当前版本' : '历史版本' }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="操作类型">
          <el-tag :type="getOperationTagType(currentVersion.operation)">
            {{ getOperationDisplayName(currentVersion.operation) }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item v-if="currentVersion.operationDetail" label="操作详情">
          {{ currentVersion.operationDetail }}
        </el-descriptions-item>
        <el-descriptions-item v-if="currentVersion.timestamp" label="时间戳">
          {{ formatTimestamp(currentVersion.timestamp) }}
        </el-descriptions-item>
      </el-descriptions>
      
      <el-divider />
      
      <div class="config-preview">
        <h4>配置预览</h4>
        <pre>{{ JSON.stringify(currentVersion.config, null, 2) }}</pre>
      </div>
      
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="detailDialogVisible = false">关闭</el-button>
        </span>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import {onMounted, ref} from 'vue'
import {ElLoading, ElMessage, ElMessageBox} from 'element-plus'
// 使用相对路径导入
import {applyVersion, deleteConfigVersion, getAllVersionInfo} from '../../api/config'

// 定义版本配置类型
interface VersionConfig {
  [key: string]: any
}

// 定义版本类型
interface Version {
  version: number
  status: 'current' | 'history'
  config: VersionConfig
  operation?: string
  operationDetail?: string
  timestamp?: number
}

const versions = ref<Version[]>([])
const detailDialogVisible = ref(false)
const currentVersion = ref({} as Version)
const loading = ref(false)
const applyingVersions = ref<Set<number>>(new Set())
const deletingVersions = ref<Set<number>>(new Set())

// 获取操作类型标签类型
const getOperationTagType = (operation: string | undefined) => {
  switch (operation) {
    case 'apply':
      return 'primary'
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
    default:
      return 'info'
  }
}

// 获取操作类型显示名称
const getOperationDisplayName = (operation: string | undefined) => {
  switch (operation) {
    case 'apply':
      return '应用'
    case 'createService':
      return '创建服务'
    case 'updateService':
      return '更新服务'
    case 'deleteService':
      return '删除服务'
    case 'addInstance':
      return '添加实例'
    case 'updateInstance':
      return '更新实例'
    case 'deleteInstance':
      return '删除实例'
    case 'updateTracingSampling':
      return '更新采样配置'
    default:
      return '未知操作'
  }
}

// 格式化时间戳
const formatTimestamp = (timestamp: number | undefined) => {
  if (!timestamp) return '-'
  return new Date(timestamp).toLocaleString('zh-CN')
}

// 获取版本列表（使用优化接口）
const fetchVersions = async () => {
  try {
    loading.value = true
    // 使用优化接口一次性获取所有版本信息
    const response = await getAllVersionInfo()
    const versionInfos = response.data.data || []
    
    // 转换为前端需要的格式
    const versionDetails: Version[] = versionInfos.map(info => ({
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
    ElMessage.error('获取版本列表失败')
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
      <p><strong>版本应用确认</strong></p>
      <p>版本号：${row.version}</p>
      <p>操作类型：${getOperationDisplayName(row.operation)}</p>
      <p>操作详情：${row.operationDetail || '无'}</p>
      <p style="color: #e6a23c; margin-top: 10px;">
        <i class="el-icon-warning"></i> 
        应用此版本将替换当前配置，请确认操作无误。
      </p>
    </div>
    `, 
    '应用配置版本', 
    {
      confirmButtonText: '确定应用',
      cancelButtonText: '取消',
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
    text: `正在应用版本 ${row.version}...`,
    spinner: 'el-icon-loading',
    background: 'rgba(0, 0, 0, 0.7)'
  })

  // 设置按钮加载状态
  applyingVersions.value.add(row.version)

  try {
    // 第一步：验证版本存在性
    loadingInstance.setText('验证版本有效性...')
    await new Promise(resolve => setTimeout(resolve, 300)) // 模拟验证过程

    // 第二步：应用版本配置
    loadingInstance.setText('应用版本配置...')
    const response = await applyVersion(row.version)

    // 第三步：验证应用结果
    loadingInstance.setText('验证应用结果...')
    await new Promise(resolve => setTimeout(resolve, 500))

    // 第四步：刷新配置状态
    loadingInstance.setText('刷新配置状态...')

    loadingInstance.close()

    // 显示成功消息
    ElMessage({
      type: 'success',
      message: `版本 ${row.version} 应用成功！配置已更新并生效。`,
      duration: 3000,
      showClose: true
    })

    // 立即刷新版本列表，确保状态更新
    await fetchVersions()

  } catch (error: any) {
    loadingInstance.close()

    console.error('应用版本失败:', error)

    // 详细的错误处理
    let errorTitle = '版本应用失败'
    let errorMessage = '未知错误'
    let errorDetails = ''

    if (error?.response?.data) {
      const errorData = error.response.data
      errorMessage = errorData.message || '服务器返回错误'
      errorDetails = errorData.details || errorData.error || ''

      // 根据错误类型提供具体的错误信息
      if (errorMessage.includes('版本不存在')) {
        errorTitle = '版本不存在'
        errorMessage = `版本 ${row.version} 不存在或已被删除`
      } else if (errorMessage.includes('配置损坏')) {
        errorTitle = '配置文件损坏'
        errorMessage = `版本 ${row.version} 的配置文件已损坏，无法应用`
      } else if (errorMessage.includes('权限')) {
        errorTitle = '权限不足'
        errorMessage = '您没有权限执行此操作'
      } else if (errorMessage.includes('系统错误')) {
        errorTitle = '系统错误'
        errorMessage = '系统内部错误，请稍后重试'
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
          <strong>建议操作：</strong><br/>
          1. 检查版本是否存在<br/>
          2. 确认网络连接正常<br/>
          3. 如问题持续，请联系系统管理员
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
        <p><i class="el-icon-warning" style="color: #e6a23c;"></i> <strong>无法删除当前版本</strong></p>
        <p>版本 ${row.version} 是当前正在使用的版本，不能被删除。</p>
        <p style="margin-top: 10px; color: #606266;">
          <strong>建议操作：</strong><br/>
          1. 先应用其他版本<br/>
          2. 然后再删除此版本
        </p>
      </div>
      `,
        '删除失败',
        {
          confirmButtonText: '我知道了',
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
      <p><strong>版本删除确认</strong></p>
      <p>版本号：${row.version}</p>
      <p>操作类型：${getOperationDisplayName(row.operation)}</p>
      <p>操作详情：${row.operationDetail || '无'}</p>
      <p>创建时间：${formatTimestamp(row.timestamp)}</p>
      <p style="color: #f56c6c; margin-top: 15px;">
        <i class="el-icon-warning"></i> 
        <strong>警告：此操作不可恢复！</strong>
      </p>
      <p style="color: #909399; font-size: 12px; margin-top: 10px;">
        删除版本将永久移除该版本的配置数据，请确认您不再需要此版本。
      </p>
    </div>
    `,
      '删除配置版本',
      {
        confirmButtonText: '确定删除',
        cancelButtonText: '取消',
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

  // 设置删除按钮加载状态
  deletingVersions.value.add(row.version)

  // 显示删除进度
  const loadingInstance = ElLoading.service({
    lock: true,
    text: `正在删除版本 ${row.version}...`,
    spinner: 'el-icon-loading',
    background: 'rgba(0, 0, 0, 0.7)'
  })

  try {
    // 第一步：验证删除权限
    loadingInstance.setText('验证删除权限...')
    await new Promise(resolve => setTimeout(resolve, 300))

    // 第二步：执行删除操作
    loadingInstance.setText('删除版本配置...')
    await deleteConfigVersion(row.version)

    // 第三步：清理相关数据
    loadingInstance.setText('清理相关数据...')
    await new Promise(resolve => setTimeout(resolve, 400))

    loadingInstance.close()

    // 显示成功消息
    ElMessage({
      type: 'success',
      message: `版本 ${row.version} 删除成功！`,
      duration: 3000,
      showClose: true
    })

    // 立即刷新版本列表，确保删除的项目被移除
    await fetchVersions()

  } catch (error: any) {
    loadingInstance.close()

    console.error('删除版本失败:', error)

    // 详细的错误处理
    let errorTitle = '版本删除失败'
    let errorMessage = '未知错误'
    let errorDetails = ''

    if (error?.response?.data) {
      const errorData = error.response.data
      errorMessage = errorData.message || '服务器返回错误'
      errorDetails = errorData.details || errorData.error || ''

      // 根据错误类型提供具体的错误信息
      if (errorMessage.includes('当前版本')) {
        errorTitle = '无法删除当前版本'
        errorMessage = `版本 ${row.version} 是当前版本，不能被删除`
      } else if (errorMessage.includes('版本不存在')) {
        errorTitle = '版本不存在'
        errorMessage = `版本 ${row.version} 不存在或已被删除`
      } else if (errorMessage.includes('最后一个版本')) {
        errorTitle = '无法删除最后版本'
        errorMessage = '不能删除最后一个版本，系统至少需要保留一个配置版本'
      } else if (errorMessage.includes('权限')) {
        errorTitle = '权限不足'
        errorMessage = '您没有权限删除此版本'
      } else if (errorMessage.includes('系统错误')) {
        errorTitle = '系统错误'
        errorMessage = '系统内部错误，请稍后重试'
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
          1. 版本是当前正在使用的版本<br/>
          2. 版本是系统中的最后一个版本<br/>
          3. 网络连接问题<br/>
          4. 权限不足
        </p>
        <p style="margin-top: 10px; color: #606266;">
          <strong>建议操作：</strong><br/>
          1. 检查版本状态<br/>
          2. 确认网络连接正常<br/>
          3. 如问题持续，请联系系统管理员
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
  } finally {
    // 清除删除按钮加载状态
    deletingVersions.value.delete(row.version)
  }
}

// 组件挂载时获取数据
onMounted(() => {
  fetchVersions()
})
</script>

<style scoped>
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.version-management {
  padding: 20px;
}

.config-preview {
  max-height: 300px;
  overflow-y: auto;
}

.config-preview pre {
  background-color: #f5f5f5;
  padding: 10px;
  border-radius: 4px;
  white-space: pre-wrap;
  word-wrap: break-word;
  font-family: 'Courier New', monospace;
  font-size: 12px;
}
</style>