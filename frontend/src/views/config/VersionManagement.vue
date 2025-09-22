<template>
  <div class="version-management">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>版本管理</span>
          <el-button type="primary" @click="handleRefresh">刷新</el-button>
        </div>
      </template>
      
      <el-table :data="versions" style="width: 100%" v-loading="loading">
        <el-table-column prop="version" label="版本号" width="100" />
        <el-table-column prop="status" label="状态" width="100">
          <template #default="scope">
            <el-tag :type="scope.row.status === 'current' ? 'success' : 'info'">
              {{ scope.row.status === 'current' ? '当前版本' : '历史版本' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="250">
          <template #default="scope">
            <el-button size="small" @click="handleView(scope.row)">查看</el-button>
            <el-button 
              size="small" 
              type="primary" 
              :disabled="scope.row.status === 'current'"
              @click="handleApply(scope.row)"
            >
              应用
            </el-button>
            <el-button 
              size="small" 
              type="warning" 
              :disabled="scope.row.status === 'current'"
              @click="handleRollback(scope.row)"
            >
              回滚
            </el-button>
            <el-button 
              size="small" 
              type="danger" 
              :disabled="scope.row.status === 'current'"
              @click="handleDelete(scope.row)"
            >
              删除
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
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
// 使用相对路径导入
import { 
  applyVersion, 
  rollbackVersion, 
  deleteConfigVersion,
  getAllVersionInfo
} from '../../api/config'

// 定义版本配置类型
interface VersionConfig {
  [key: string]: any
}

// 定义版本类型
interface Version {
  version: number
  status: 'current' | 'history'
  config: VersionConfig
}

const versions = ref<Version[]>([])
const detailDialogVisible = ref(false)
const currentVersion = ref({} as Version)
const loading = ref(false)

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
      config: info.config || {}
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
const handleApply = (row: Version) => {
  ElMessageBox.confirm(`确定要应用版本 ${row.version} 吗？这将替换当前配置。`, '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(async () => {
    try {
      await applyVersion(row.version)
      ElMessage.success('版本应用成功')
      fetchVersions()
    } catch (error) {
      console.error('应用版本失败:', error)
      ElMessage.error('版本应用失败')
    }
  })
}

// 回滚版本
const handleRollback = (row: Version) => {
  ElMessageBox.confirm(`确定要回滚到版本 ${row.version} 吗？`, '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(async () => {
    try {
      await rollbackVersion(row.version)
      ElMessage.success('版本回滚成功')
      fetchVersions()
    } catch (error) {
      console.error('回滚版本失败:', error)
      ElMessage.error('版本回滚失败')
    }
  })
}

// 删除版本
const handleDelete = (row: Version) => {
  ElMessageBox.confirm(`确定要删除版本 ${row.version} 吗？此操作不可恢复。`, '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(async () => {
    try {
      await deleteConfigVersion(row.version)
      ElMessage.success('版本删除成功')
      fetchVersions()
    } catch (error) {
      console.error('删除版本失败:', error)
      ElMessage.error('版本删除失败')
    }
  })
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