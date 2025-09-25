<template>
  <div class="api-key-management">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>API密钥管理</span>
          <el-button type="primary" @click="handleCreateApiKey">创建API密钥</el-button>
        </div>
      </template>
      
      <el-table :data="apiKeys" style="width: 100%" v-loading="loading">
        <el-table-column prop="keyId" label="密钥ID" width="180" />
        <el-table-column prop="description" label="描述" />
        <el-table-column prop="permissions" label="权限" width="450">
          <template #default="scope">
            <el-tag
              v-for="permission in scope.row.permissions"
              :key="permission"
              :type="getPermissionTagType(permission)"
              size="small"
              style="margin-right: 5px;"
            >
              {{ formatPermission(permission) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" width="200" />
        <el-table-column prop="expiresAt" label="过期时间" width="200" />
        <el-table-column label="剩余天数" width="120">
          <template #default="scope">
            <span :class="getDaysClass(scope.row)">{{ getRemainingDays(scope.row.expiresAt) }}</span>
          </template>
        </el-table-column>
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
      <el-form :model="form" label-width="100px" ref="formRef">
        <el-form-item label="密钥ID" prop="keyId" :rules="keyIdRules">
          <el-input v-model="form.keyId" :disabled="isEdit">
            <template #append>
              <el-button @click="generateKeyId" :disabled="isEdit">生成</el-button>
            </template>
          </el-input>
          <div class="key-id-hint">长度不少于32个字符，包含字母、数字和特殊字符</div>
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
          <el-button type="primary" @click="handleSave" :loading="saveLoading">保存</el-button>
        </span>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, reactive } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { 
  getApiKeys, 
  createApiKey, 
  updateApiKey, 
  deleteApiKey, 
  enableApiKey, 
  disableApiKey 
} from '@/api/apiKey'
import type { ApiKeyInfo, CreateApiKeyRequest, UpdateApiKeyRequest, ApiKeyCreationResponse } from '@/types'

// 定义API密钥类型
interface ApiKey extends ApiKeyInfo {
  expired?: boolean
}

const apiKeys = ref<ApiKey[]>([])
const loading = ref(false)
const saveLoading = ref(false)
const dialogVisible = ref(false)
const dialogTitle = ref('')
const isEdit = ref(false)
const formRef = ref()

// 表单数据
const form = ref({
  keyId: '',
  description: '',
  expiresAt: '',
  enabled: true,
  permissions: [] as string[]
})

// 密钥ID验证规则
const keyIdRules = [
  { required: true, message: '请输入密钥ID', trigger: 'blur' },
  { min: 32, message: '密钥ID长度不能少于32个字符', trigger: 'blur' },
  {
    validator: (rule: any, value: string, callback: any) => {
      if (value && !/^(?=.*[a-zA-Z])(?=.*\d)(?=.*[!@#$%^&*()_+\-=\[\]{};':"\\|,.<>\/?]).+$/.test(value)) {
        callback(new Error('密钥ID必须包含字母、数字和特殊字符'))
      } else {
        callback()
      }
    },
    trigger: 'blur'
  }
]

// 获取权限标签类型
const getPermissionTagType = (permission: string) => {
  switch (permission) {
    case 'ADMIN':
      return 'danger'
    case 'WRITE':
      return 'warning'
    case 'DELETE':
      return 'warning'
    case 'READ':
      return 'success'
    default:
      return 'info'
  }
}

// 格式化权限显示
const formatPermission = (permission: string) => {
  switch (permission) {
    case 'ADMIN':
      return '管理员'
    case 'WRITE':
      return '写入'
    case 'DELETE':
      return '删除'
    case 'READ':
      return '读取'
    default:
      return permission
  }
}

// 生成复杂的密钥ID
const generateKeyId = () => {
  const chars = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%^&*()_+-=[]{}|;:,.<>?'
  let keyId = ''
  for (let i = 0; i < 32; i++) {
    keyId += chars.charAt(Math.floor(Math.random() * chars.length))
  }
  // 确保包含所有类型的字符
  const hasLetter = /[a-zA-Z]/.test(keyId)
  const hasDigit = /\d/.test(keyId)
  const hasSpecial = /[!@#$%^&*()_+\-=\[\]{};':"\\|,.<>\/?]/.test(keyId)
  
  if (!hasLetter) keyId = keyId.substring(0, 31) + 'A'
  if (!hasDigit) keyId = keyId.substring(0, 30) + '0' + keyId.substring(30)
  if (!hasSpecial) keyId = keyId.substring(0, 29) + '!' + keyId.substring(29)
  
  form.value.keyId = keyId
}

// 获取剩余天数
const getRemainingDays = (expiresAt: string): string => {
  if (!expiresAt) {
    return '永不过期'
  }
  
  const expireDate = new Date(expiresAt)
  const currentDate = new Date()
  const diffTime = expireDate.getTime() - currentDate.getTime()
  const diffDays = Math.ceil(diffTime / (1000 * 60 * 60 * 24))
  
  if (diffDays < 0) {
    return '已过期'
  }
  
  return `${diffDays}天`
}

// 获取剩余天数的样式类
const getDaysClass = (row: ApiKey): string => {
  if (!row.expiresAt) {
    return ''
  }
  
  const expireDate = new Date(row.expiresAt)
  const currentDate = new Date()
  const diffTime = expireDate.getTime() - currentDate.getTime()
  const diffDays = Math.ceil(diffTime / (1000 * 60 * 60 * 24))
  
  if (diffDays < 0) {
    return 'expired-days'
  }
  
  if (diffDays < 30) {
    return 'warning-days'
  }
  
  return ''
}

// 格式化日期时间
const formatDateTime = (dateString: string): string => {
  if (!dateString) {
    return ''
  }
  
  const date = new Date(dateString)
  return date.getFullYear() + '-' +
    String(date.getMonth() + 1).padStart(2, '0') + '-' +
    String(date.getDate()).padStart(2, '0') + ' ' +
    String(date.getHours()).padStart(2, '0') + ':' +
    String(date.getMinutes()).padStart(2, '0') + ':' +
    String(date.getSeconds()).padStart(2, '0')
}

// 获取API密钥列表
const fetchApiKeys = async () => {
  loading.value = true
  try {
    const data = await getApiKeys()
    // 格式化日期时间
    const formattedData = data.map(key => ({
      ...key,
      createdAt: formatDateTime(key.createdAt),
      expiresAt: formatDateTime(key.expiresAt)
    }))
    apiKeys.value = formattedData
  } catch (error) {
    ElMessage.error('获取API密钥列表失败')
  } finally {
    loading.value = false
  }
}

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
const handleEdit = (row: ApiKey) => {
  dialogTitle.value = '编辑API密钥'
  isEdit.value = true
  // 反格式化日期时间以用于表单
  const editRow = { ...row }
  form.value = editRow
  dialogVisible.value = true
}

// 删除API密钥
const handleDelete = (row: ApiKey) => {
  ElMessageBox.confirm(`确定要删除API密钥 ${row.keyId} 吗？`, '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(async () => {
    try {
      await deleteApiKey(row.keyId)
      await fetchApiKeys()
      ElMessage.success('删除成功')
    } catch (error) {
      ElMessage.error('删除失败')
    }
  })
}

// 保存API密钥
const handleSave = async () => {
  saveLoading.value = true
  try {
    // 验证表单
    await formRef.value.validate()
    
    if (isEdit.value) {
      // 编辑
      const updateData: UpdateApiKeyRequest = {
        description: form.value.description,
        expiresAt: form.value.expiresAt,
        enabled: form.value.enabled,
        permissions: form.value.permissions
      }
      
      await updateApiKey(form.value.keyId, updateData)
      ElMessage.success('编辑成功')
    } else {
      // 新增
      const createData: CreateApiKeyRequest = {
        keyId: form.value.keyId || undefined,
        description: form.value.description,
        expiresAt: form.value.expiresAt || undefined,
        enabled: form.value.enabled,
        permissions: form.value.permissions
      }
      
      // 移除空字符串的过期时间
      if (!createData.expiresAt) {
        delete createData.expiresAt;
      }
      
      const response: ApiKeyCreationResponse = await createApiKey(createData)
      ElMessage.success('创建成功')
    }
    
    dialogVisible.value = false
    await fetchApiKeys()
  } catch (error: any) {
    console.error('保存API密钥失败:', error)
    ElMessage.error(isEdit.value ? '编辑失败: ' + (error.message || '') : '创建失败: ' + (error.message || ''))
  } finally {
    saveLoading.value = false
  }
}

// 处理状态变更
const handleStatusChange = async (row: ApiKey) => {
  try {
    if (row.enabled) {
      await enableApiKey(row.keyId)
      ElMessage.success(`API密钥 ${row.keyId} 已启用`)
    } else {
      await disableApiKey(row.keyId)
      ElMessage.success(`API密钥 ${row.keyId} 已禁用`)
    }
    await fetchApiKeys()
  } catch (error) {
    // 回滚状态
    row.enabled = !row.enabled
    ElMessage.error('状态变更失败')
  }
}

// 组件挂载时获取数据
onMounted(() => {
  fetchApiKeys()
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

.warning-days {
  color: #e6a23c;
  font-weight: bold;
}

.expired-days {
  color: #f56c6c;
  font-weight: bold;
}

.key-id-hint {
  font-size: 12px;
  color: #909399;
  margin-top: 5px;
}
</style>