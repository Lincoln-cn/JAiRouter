<template>
  <div class="version-management">
    <el-card class="version-card">
      <template #header>
        <div class="card-header">
          <div class="header-title">
            <el-icon>
              <History />
            </el-icon>
            <span>版本管理</span>
          </div>
          <el-button type="primary" @click="handleRefresh">
            <el-icon>
              <Refresh />
            </el-icon>
            刷新
          </el-button>
        </div>
      </template>

      <el-alert :closable="false" class="desc-alert" show-icon title="操作说明" type="info">
        <template #description>
          <ul class="desc-list">
            <li><b>应用：</b>将此版本的配置设为当前版本。</li>
            <li><b>删除：</b>只能删除历史版本，删除后不可恢复。</li>
            <li><b>查看：</b>可预览配置详情和变更说明。</li>
          </ul>
        </template>
      </el-alert>

      <div class="table-wrap">
        <el-table v-loading="loading" :data="versions" border class="version-table" fit>
          <el-table-column align="center" label="版本号" prop="version" width="110" />
          <el-table-column align="center" label="状态" prop="status" width="120">
            <template #default="scope">
              <el-tag :type="scope.row.status === 'current' ? 'success' : 'info'" size="large">
                <el-icon v-if="scope.row.status === 'current'" style="margin-right:2px">
                  <SuccessFilled />
                </el-icon>
                {{ scope.row.status === 'current' ? '当前版本' : '历史版本' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column align="center" label="操作类型" prop="operation" width="130">
            <template #default="scope">
              <el-tag :type="getOperationTagType(scope.row.operation)" effect="plain">
                {{ getOperationDisplayName(scope.row.operation) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作详情" min-width="180" prop="operationDetail" show-overflow-tooltip>
            <template #default="scope">
              <span v-if="scope.row.operationDetail">{{ scope.row.operationDetail }}</span>
              <span v-else>-</span>
            </template>
          </el-table-column>
          <el-table-column align="center" label="时间" prop="timestamp" width="170">
            <template #default="scope">
              <span class="timestamp">{{ formatTimestamp(scope.row.timestamp) }}</span>
            </template>
          </el-table-column>
          <el-table-column align="center" fixed="right" label="操作" width="360">
            <template #default="scope">
              <el-button @click="handleView(scope.row)"
              >
                查看
              </el-button>
              <el-button :disabled="scope.row.status === 'current' || applyingVersions.has(scope.row.version)"
                :loading="applyingVersions.has(scope.row.version)" type="primary" @click="handleApply(scope.row)">
                应用
              </el-button>
              <el-button :disabled="scope.row.status === 'current' || deletingVersions.has(scope.row.version)"
                :loading="deletingVersions.has(scope.row.version)" type="danger" @click="handleDelete(scope.row)">
                删除
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </el-card>

    <!-- 查看版本详情对话框 -->
    <el-dialog v-model="detailDialogVisible" class="version-dialog" title="版本详情" width="620px">
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
        <div class="preview-title">
          <el-icon>
            <Document />
          </el-icon>
          配置预览
        </div>
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
import { onMounted, ref } from 'vue'
import { ElLoading, ElMessage, ElMessageBox } from 'element-plus'
import { applyVersion, deleteConfigVersion, getAllVersionInfo, type Version } from '@/api/version.ts'

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
  const d = new Date(timestamp)
  return d.toLocaleString('zh-CN', { hour12: false })
}

// 获取版本列表（使用优化接口）
const fetchVersions = async () => {
  try {
    loading.value = true
    // 使用优化接口一次性获取所有版本信息
    const response = await getAllVersionInfo()
    console.log('Full API Response:', JSON.stringify(response, null, 2)) // 完整响应调试日志
    
    // 检查响应结构
    if (!response || !response.data) {
      console.error('Invalid API response structure:', response)
      ElMessage.error('API响应格式错误')
      return
    }
    
    console.log('Response data:', response.data) // 添加响应数据调试日志
    console.log('Response data type:', typeof response.data) // 添加数据类型调试日志
    
    // 处理可能的双重序列化问题
    let versionInfos: any[] = []
    if (typeof response.data === 'string') {
      // 如果data是字符串，需要先解析
      try {
        const parsedData = JSON.parse(response.data)
        versionInfos = parsedData.data || [] // 注意：这里要取parsedData.data
      } catch (parseError) {
        console.error('Failed to parse response data:', parseError)
        ElMessage.error('解析响应数据失败')
        return
      }
    } else {
      // 正常情况，data已经是对象
      versionInfos = response.data.data || []
    }
    
    console.log('Raw Version Infos:', versionInfos) // 添加调试日志
    console.log('Version Infos type:', typeof versionInfos) // 添加数据类型调试日志
    console.log('Is array:', Array.isArray(versionInfos)) // 检查是否为数组

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
    console.log('Processed Versions:', versions.value) // 添加调试日志
    console.log('Versions count:', versions.value.length) // 添加版本数量调试日志
  } catch (error) {
    console.error('获取版本列表失败:', error)
    ElMessage.error('获取版本列表失败: ' + (error as Error).message)
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
  background: linear-gradient(180deg, #f7f9fc 0%, #ffffff 100%);
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

.desc-alert {
  margin: 22px 22px 18px 22px;
}

.desc-list {
  margin: 0 0 0 20px;
  padding: 0;
  font-size: 14px;
  color: #444;
  line-height: 1.9;
}

.table-wrap {
  padding: 0 22px 20px 22px;
}

.version-table {
  border-radius: 8px;
  background: #fff;
  font-size: 14px;
}

.version-table :deep(.el-table__cell) {
  font-size: 14px;
  padding: 14px 8px;
}

.version-table :deep(.el-table__header th) {
  font-size: 14px;
  font-weight: 600;
  color: #2b3a4b;
  background-color: #fbfdff;
}

.timestamp {
  color: #909399;
  font-size: 13px;
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 15px;
  padding: 10px 20px;
}

.version-dialog :deep(.el-dialog__header) {
  background-color: #fbfdff;
  border-bottom: 1px solid #eef2f6;
  padding: 14px 20px;
}

.version-dialog :deep(.el-dialog__title) {
  font-weight: 700;
  color: #1f2d3d;
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
  background: #f8f8fa;
  border-radius: 8px;
  border: 1px solid #f0f2f5;
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
  color: #394150;
}

.preview-title {
  font-weight: 600;
  margin-bottom: 3px;
  color: #6b7785;
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 14px;
}
</style>