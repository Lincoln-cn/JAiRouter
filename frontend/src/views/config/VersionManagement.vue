<template>
  <div class="version-management">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>版本管理</span>
          <el-button type="primary" @click="handleCreateVersion">创建版本</el-button>
        </div>
      </template>
      
      <el-table :data="versions" style="width: 100%">
        <el-table-column prop="version" label="版本号" width="100" />
        <el-table-column prop="createTime" label="创建时间" width="200" />
        <el-table-column prop="description" label="描述" />
        <el-table-column prop="status" label="状态" width="100">
          <template #default="scope">
            <el-tag :type="scope.row.status === 'current' ? 'success' : 'info'">
              {{ scope.row.status === 'current' ? '当前版本' : '历史版本' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="300">
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
        <el-descriptions-item label="创建时间">{{ currentVersion.createTime }}</el-descriptions-item>
        <el-descriptions-item label="描述">{{ currentVersion.description }}</el-descriptions-item>
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

// 定义版本配置类型
interface VersionConfig {
  services: string[]
  instances: { service: string; url: string }[]
}

// 定义版本类型
interface Version {
  version: number
  createTime: string
  description: string
  status: 'current' | 'history'
  config: VersionConfig
}

// 模拟数据
const versions = ref<Version[]>([
  { 
    version: 1, 
    createTime: '2023-10-01 10:00:00', 
    description: '初始版本', 
    status: 'current',
    config: {
      services: ['chat', 'embedding'],
      instances: []
    }
  },
  { 
    version: 2, 
    createTime: '2023-10-05 14:30:00', 
    description: '添加聊天服务实例', 
    status: 'history',
    config: {
      services: ['chat', 'embedding'],
      instances: [
        { service: 'chat', url: 'http://localhost:11434' }
      ]
    }
  },
  { 
    version: 3, 
    createTime: '2023-10-10 09:15:00', 
    description: '添加嵌入服务实例', 
    status: 'history',
    config: {
      services: ['chat', 'embedding'],
      instances: [
        { service: 'chat', url: 'http://localhost:11434' },
        { service: 'embedding', url: 'http://localhost:11434' }
      ]
    }
  }
])

const detailDialogVisible = ref(false)
const currentVersion = ref({} as Version)

// 创建版本
const handleCreateVersion = () => {
  ElMessageBox.prompt('请输入版本描述', '创建版本', {
    confirmButtonText: '确定',
    cancelButtonText: '取消'
  }).then(({ value }) => {
    const newVersion: Version = {
      version: versions.value.length + 1,
      createTime: new Date().toLocaleString(),
      description: value || '新版本',
      status: 'history',
      config: {
        services: [],
        instances: []
      }
    }
    versions.value.unshift(newVersion)
    ElMessage.success('版本创建成功')
  }).catch(() => {
    // 取消操作
  })
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
  }).then(() => {
    // 将当前版本标记为历史版本
    const current = versions.value.find(v => v.status === 'current')
    if (current) {
      current.status = 'history'
    }
    
    // 将选中版本标记为当前版本
    row.status = 'current'
    
    ElMessage.success('版本应用成功')
  })
}

// 回滚版本
const handleRollback = (row: Version) => {
  ElMessageBox.confirm(`确定要回滚到版本 ${row.version} 吗？`, '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(() => {
    // 将当前版本标记为历史版本
    const current = versions.value.find(v => v.status === 'current')
    if (current) {
      current.status = 'history'
    }
    
    // 将选中版本标记为当前版本
    row.status = 'current'
    
    ElMessage.success('版本回滚成功')
  })
}

// 删除版本
const handleDelete = (row: Version) => {
  ElMessageBox.confirm(`确定要删除版本 ${row.version} 吗？此操作不可恢复。`, '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(() => {
    versions.value = versions.value.filter(version => version.version !== row.version)
    ElMessage.success('版本删除成功')
  })
}

// 组件挂载时获取数据
onMounted(() => {
  // 这里可以调用API获取真实数据
  console.log('获取版本管理数据')
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
}
</style>