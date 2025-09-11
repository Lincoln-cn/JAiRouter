<template>
  <div class="api-key-management">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>API密钥管理</span>
          <el-button type="primary" @click="handleCreateApiKey">创建API密钥</el-button>
        </div>
      </template>
      
      <el-table :data="apiKeys" style="width: 100%">
        <el-table-column prop="keyId" label="密钥ID" width="180" />
        <el-table-column prop="description" label="描述" />
        <el-table-column prop="createdAt" label="创建时间" width="200" />
        <el-table-column prop="expiresAt" label="过期时间" width="200" />
        <el-table-column prop="enabled" label="状态" width="100">
          <template #default="scope">
            <el-switch
              v-model="scope.row.enabled"
              @change="handleStatusChange(scope.row)"
            />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200">
          <template #default="scope">
            <el-button size="small" @click="handleEdit(scope.row)">编辑</el-button>
            <el-button size="small" type="danger" @click="handleDelete(scope.row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
    
    <!-- 创建/编辑API密钥对话框 -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="500px">
      <el-form :model="form" label-width="100px">
        <el-form-item label="密钥ID">
          <el-input v-model="form.keyId" :disabled="isEdit" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" />
        </el-form-item>
        <el-form-item label="过期时间">
          <el-date-picker
            v-model="form.expiresAt"
            type="datetime"
            placeholder="选择过期时间"
            format="YYYY-MM-DD HH:mm:ss"
            value-format="YYYY-MM-DD HH:mm:ss"
          />
        </el-form-item>
        <el-form-item label="权限">
          <el-checkbox-group v-model="form.permissions">
            <el-checkbox label="READ">读取</el-checkbox>
            <el-checkbox label="WRITE">写入</el-checkbox>
            <el-checkbox label="DELETE">删除</el-checkbox>
            <el-checkbox label="ADMIN">管理员</el-checkbox>
          </el-checkbox-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="dialogVisible = false">取消</el-button>
          <el-button type="primary" @click="handleSave">保存</el-button>
        </span>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'

// 模拟数据
const apiKeys = ref([
  { 
    keyId: 'key-1', 
    description: '管理后台API密钥', 
    createdAt: '2023-10-01 10:00:00', 
    expiresAt: '2024-10-01 10:00:00', 
    enabled: true,
    permissions: ['READ', 'WRITE', 'ADMIN']
  },
  { 
    keyId: 'key-2', 
    description: '只读API密钥', 
    createdAt: '2023-10-05 14:30:00', 
    expiresAt: '2024-10-05 14:30:00', 
    enabled: true,
    permissions: ['READ']
  },
  { 
    keyId: 'key-3', 
    description: '已禁用密钥', 
    createdAt: '2023-09-01 09:00:00', 
    expiresAt: '2024-09-01 09:00:00', 
    enabled: false,
    permissions: ['READ', 'WRITE']
  }
])

const dialogVisible = ref(false)
const dialogTitle = ref('')
const isEdit = ref(false)

const form = ref({
  keyId: '',
  description: '',
  expiresAt: '',
  enabled: true,
  permissions: [] as string[]
})

// 创建API密钥
const handleCreateApiKey = () => {
  dialogTitle.value = '创建API密钥'
  isEdit.value = false
  form.value = {
    keyId: '',
    description: '',
    expiresAt: '',
    enabled: true,
    permissions: []
  }
  dialogVisible.value = true
}

// 编辑API密钥
const handleEdit = (row: any) => {
  dialogTitle.value = '编辑API密钥'
  isEdit.value = true
  form.value = { ...row }
  dialogVisible.value = true
}

// 删除API密钥
const handleDelete = (row: any) => {
  ElMessageBox.confirm(`确定要删除API密钥 ${row.keyId} 吗？`, '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(() => {
    apiKeys.value = apiKeys.value.filter(key => key.keyId !== row.keyId)
    ElMessage.success('删除成功')
  })
}

// 保存API密钥
const handleSave = () => {
  if (isEdit.value) {
    // 编辑
    const index = apiKeys.value.findIndex(key => key.keyId === form.value.keyId)
    if (index !== -1) {
      apiKeys.value[index] = { ...form.value }
    }
  } else {
    // 新增
    const newKey = {
      keyId: 'key-' + Date.now(),
      createdAt: new Date().toLocaleString(),
      ...form.value
    }
    apiKeys.value.push(newKey)
  }
  
  dialogVisible.value = false
  ElMessage.success(isEdit.value ? '编辑成功' : '创建成功')
}

// 处理状态变更
const handleStatusChange = (row: any) => {
  ElMessage.success(`API密钥 ${row.keyId} 已${row.enabled ? '启用' : '禁用'}`)
}

// 组件挂载时获取数据
onMounted(() => {
  // 这里可以调用API获取真实数据
  console.log('获取API密钥管理数据')
})
</script>

<style scoped>
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.api-key-management {
  padding: 20px;
}
</style>