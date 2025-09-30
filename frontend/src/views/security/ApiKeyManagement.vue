<template>
  <div class="api-key-management">
    <el-card class="main-card" shadow="hover">
      <template #header>
        <div class="card-header">
          <span class="main-title">
            <el-icon><key/></el-icon>
            API密钥管理
          </span>
          <el-button icon="Plus" type="primary" @click="handleCreateApiKey">创建API密钥</el-button>
        </div>
      </template>

      <el-table v-loading="loading" :data="apiKeys" border stripe style="width: 100%">
        <el-table-column label="密钥ID" prop="keyId" width="220">
          <template #default="scope">
            <el-tag effect="plain" type="info">{{ scope.row.keyId }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="描述" min-width="120" prop="description">
          <template #default="scope">
            <span class="desc-text">{{ scope.row.description || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="权限" prop="permissions" width="250">
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
        <el-table-column label="创建时间" prop="createdAt" width="180"/>
        <el-table-column label="过期时间" prop="expiresAt" width="180"/>
        <el-table-column label="剩余天数" width="110">
          <template #default="scope">
            <span :class="getDaysClass(scope.row)">{{ getRemainingDays(scope.row.expiresAt) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" prop="enabled" width="80">
          <template #default="scope">
            <el-switch
                v-model="scope.row.enabled"
                active-color="#13ce66"
                inactive-color="#ff4949"
                @change="handleStatusChange(scope.row)"
            />
          </template>
        </el-table-column>
        <el-table-column fixed="right" label="操作" width="180">
          <template #default="scope">
            <el-button icon="Edit" size="small" @click="handleEdit(scope.row)">编辑</el-button>
            <el-button icon="Delete" size="small" type="danger" @click="handleDelete(scope.row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 创建/编辑API密钥对话框 -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" center width="500px">
      <el-form ref="formRef" :model="form" label-width="100px" status-icon>
        <el-form-item label="密钥ID" prop="keyId" :rules="keyIdRules">
          <el-input v-model="form.keyId" :disabled="isEdit" maxlength="64" placeholder="请输入密钥ID"
                    show-word-limit/>
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" maxlength="128" placeholder="密钥用途描述" show-word-limit
                    type="textarea"/>
        </el-form-item>
        <el-form-item label="过期时间">
          <el-date-picker
              v-model="form.expiresAt"
              clearable
              format="YYYY-MM-DD HH:mm:ss"
              placeholder="选择过期时间"
              style="width: 100%;"
              type="datetime"
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

    <!-- 创建成功后弹窗，展示密钥值 -->
    <el-dialog v-model="showKeyValueDialog" :close-on-click-modal="false" center title="API密钥已创建" width="400px">
      <div class="key-value-dialog-content">
        <el-icon style="font-size: 24px; color: #409EFF;">
          <key/>
        </el-icon>
        <p class="key-value-tip">请妥善保存以下密钥值，密钥值仅此一次显示：</p>
        <el-input v-model="createdKeyValue" readonly>
          <template #append>
            <el-button icon="CopyDocument" type="primary" @click="copyKeyValue">复制</el-button>
          </template>
        </el-input>
        <el-alert :closable="false" show-icon style="margin-top: 12px;" type="warning">
          密钥值只会显示一次，关闭弹窗后无法再次获取！
        </el-alert>
      </div>
      <template #footer>
        <span class="dialog-footer">
          <el-button type="primary" @click="closeKeyValueDialog">已保存并关闭</el-button>
        </span>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import {onMounted, ref} from 'vue'
import {ElMessage, ElMessageBox} from 'element-plus'
import {createApiKey, deleteApiKey, disableApiKey, enableApiKey, getApiKeys, updateApiKey} from '@/api/apiKey'
import type {ApiKeyCreationResponse, ApiKeyInfo, CreateApiKeyRequest, UpdateApiKeyRequest} from '@/types'
import {Key} from '@element-plus/icons-vue'

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
  {required: true, message: '请输入密钥ID', trigger: 'blur'}
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

// 获取剩余天数
const getRemainingDays = (expiresAt: string): string => {
  if (!expiresAt) return '永不过期'
  const expireDate = new Date(expiresAt)
  const currentDate = new Date()
  const diffTime = expireDate.getTime() - currentDate.getTime()
  const diffDays = Math.ceil(diffTime / (1000 * 60 * 60 * 24))
  if (diffDays < 0) return '已过期'
  return `${diffDays}天`
}

// 获取剩余天数的样式类
const getDaysClass = (row: ApiKey): string => {
  if (!row.expiresAt) return ''
  const expireDate = new Date(row.expiresAt)
  const currentDate = new Date()
  const diffTime = expireDate.getTime() - currentDate.getTime()
  const diffDays = Math.ceil(diffTime / (1000 * 60 * 60 * 24))
  if (diffDays < 0) return 'expired-days'
  if (diffDays < 30) return 'warning-days'
  return ''
}

// 格式化日期时间
const formatDateTime = (dateString: string): string => {
  if (!dateString) return ''
  const date = new Date(dateString)
  return date.getFullYear() + '-' +
      String(date.getMonth() + 1).padStart(2, '0') + '-' +
      String(date.getDate()).padStart(2, '0') + ' ' +
      String(date.getHours()).padStart(2, '0') + ':' +
      String(date.getMinutes()).padStart(2, '0') + ':' +
      String(date.getSeconds()).padStart(2, '0')
}

// API密钥列表
const fetchApiKeys = async () => {
  loading.value = true
  try {
    const data = await getApiKeys()
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

// 创建API密钥弹窗
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

// 编辑API密钥弹窗
const handleEdit = (row: ApiKey) => {
  dialogTitle.value = '编辑API密钥'
  isEdit.value = true
  const editRow = {...row}
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

// 创建成功后弹窗及密钥值处理
const showKeyValueDialog = ref(false)
const createdKeyValue = ref('')

// 保存API密钥
const handleSave = async () => {
  saveLoading.value = true
  try {
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
      if (!createData.expiresAt) delete createData.expiresAt;
      const response: ApiKeyCreationResponse = await createApiKey(createData)
      createdKeyValue.value = response.keyValue
      showKeyValueDialog.value = true
      ElMessage.success('创建成功，请妥善保存密钥值！')
    }
    dialogVisible.value = false
    await fetchApiKeys()
  } catch (error: any) {
    ElMessage.error(isEdit.value ? '编辑失败: ' + (error.message || '') : '创建失败: ' + (error.message || ''))
  } finally {
    saveLoading.value = false
  }
}

// 复制密钥值
const copyKeyValue = () => {
  navigator.clipboard.writeText(createdKeyValue.value)
      .then(() => ElMessage.success('密钥值已复制'))
      .catch(() => ElMessage.error('复制失败'))
}
const closeKeyValueDialog = () => {
  showKeyValueDialog.value = false
  createdKeyValue.value = ''
}

// 状态切换
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
    row.enabled = !row.enabled
    ElMessage.error('状态变更失败')
  }
}

// 挂载时加载
onMounted(() => {
  fetchApiKeys()
})
</script>

<style scoped>
.api-key-management {
  padding: 30px 40px;
  background: #f8fafe;
  min-height: 100vh;
}

.main-card {
  border-radius: 16px;
  box-shadow: 0 2px 12px 0 rgba(32, 103, 216, 0.06);
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.main-title {
  font-size: 21px;
  font-weight: 600;
  color: #2C3E50;
  display: flex;
  align-items: center;
  gap: 8px;
}

.desc-text {
  color: #606266;
  font-size: 15px;
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

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}

.key-value-dialog-content {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 10px;
  padding: 10px 0;
}

.key-value-tip {
  font-size: 15px;
  color: #555;
  margin-bottom: 6px;
}
</style>