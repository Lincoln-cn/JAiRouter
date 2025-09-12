<template>
  <div class="jwt-account-management">
    <el-card class="main-card">
      <template #header>
        <div class="card-header">
          <span>账户管理</span>
          <div>
            <el-button type="primary" @click="showCreateDialog = true">
              <el-icon><Plus /></el-icon>
              创建账户
            </el-button>
            <el-button @click="refreshAccounts">
              <el-icon><Refresh /></el-icon>
              刷新
            </el-button>
          </div>
        </div>
      </template>
      
      <!-- 账户列表 -->
      <el-table :data="accounts" style="width: 100%" v-loading="loading">
        <el-table-column prop="username" label="用户名" width="180" />
        <el-table-column label="角色" width="200">
          <template #default="scope">
            <el-tag v-for="role in scope.row.roles" :key="role" style="margin-right: 5px;">
              {{ role }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="scope">
            <el-tag :type="scope.row.enabled ? 'success' : 'danger'">
              {{ scope.row.enabled ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作">
          <template #default="scope">
            <el-button size="small" @click="editAccount(scope.row)">编辑</el-button>
            <el-button 
              size="small" 
              :type="scope.row.enabled ? 'danger' : 'success'"
              @click="toggleAccountStatus(scope.row)"
            >
              {{ scope.row.enabled ? '禁用' : '启用' }}
            </el-button>
            <el-button size="small" type="danger" @click="deleteAccount(scope.row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
    
    <!-- 创建/编辑账户对话框 -->
    <el-dialog 
      :title="editingAccount ? '编辑账户' : '创建账户'" 
      v-model="showCreateDialog" 
      width="500px"
      @close="resetForm"
    >
      <el-form :model="accountForm" :rules="accountRules" ref="accountFormRef" label-width="80px">
        <el-form-item label="用户名" prop="username">
          <el-input v-model="accountForm.username" :disabled="!!editingAccount" />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input v-model="accountForm.password" type="password" show-password />
        </el-form-item>
        <el-form-item label="角色" prop="roles">
          <el-select v-model="accountForm.roles" multiple placeholder="请选择角色">
            <el-option label="管理员" value="ADMIN" />
            <el-option label="用户" value="USER" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态" prop="enabled">
          <el-switch v-model="accountForm.enabled" />
        </el-form-item>
      </el-form>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="showCreateDialog = false">取消</el-button>
          <el-button type="primary" @click="submitAccount">确定</el-button>
        </span>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/utils/request'
import type { FormInstance, FormRules } from 'element-plus'

// 定义账户类型
interface JwtAccount {
  username: string
  password?: string
  roles: string[]
  enabled: boolean
}

// 数据状态
const accounts = ref<JwtAccount[]>([])
const loading = ref(false)
const showCreateDialog = ref(false)
const editingAccount = ref<JwtAccount | null>(null)

// 表单数据
const accountForm = reactive<JwtAccount>({
  username: '',
  password: '',
  roles: [],
  enabled: true
})

// 表单验证规则
const accountRules = reactive<FormRules>({
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, message: '密码长度至少6位', trigger: 'blur' }
  ],
  roles: [
    { required: true, message: '请选择角色', trigger: 'change' }
  ]
})

// 表单引用
const accountFormRef = ref<FormInstance | null>(null)

// 获取账户列表
const fetchAccounts = async () => {
  loading.value = true
  try {
    const response = await request.get('/api/security/jwt/accounts')
    accounts.value = response.data
  } catch (error) {
    console.error('获取账户列表失败:', error)
    ElMessage.error('获取账户列表失败')
  } finally {
    loading.value = false
  }
}

// 刷新账户列表
const refreshAccounts = () => {
  fetchAccounts()
}

// 提交账户（创建或更新）
const submitAccount = async () => {
  if (!accountFormRef.value) return
  
  await accountFormRef.value.validate(async (valid) => {
    if (valid) {
      try {
        if (editingAccount.value) {
          // 更新账户
          await request.put(`/api/security/jwt/accounts/${accountForm.username}`, accountForm)
          ElMessage.success('账户更新成功')
        } else {
          // 创建账户
          await request.post('/api/security/jwt/accounts', accountForm)
          ElMessage.success('账户创建成功')
        }
        
        showCreateDialog.value = false
        resetForm()
        fetchAccounts()
      } catch (error) {
        console.error('操作失败:', error)
        ElMessage.error(editingAccount.value ? '账户更新失败' : '账户创建失败')
      }
    }
  })
}

// 编辑账户
const editAccount = (account: JwtAccount) => {
  editingAccount.value = account
  accountForm.username = account.username
  accountForm.password = '' // 编辑时不显示密码
  accountForm.roles = [...account.roles]
  accountForm.enabled = account.enabled
  showCreateDialog.value = true
}

// 切换账户状态
const toggleAccountStatus = async (account: JwtAccount) => {
  try {
    await request.patch(`/api/security/jwt/accounts/${account.username}/status`, null, {
      params: { enabled: !account.enabled }
    })
    ElMessage.success(`账户${!account.enabled ? '启用' : '禁用'}成功`)
    fetchAccounts()
  } catch (error) {
    console.error('操作失败:', error)
    ElMessage.error(`账户${!account.enabled ? '启用' : '禁用'}失败`)
  }
}

// 删除账户
const deleteAccount = (account: JwtAccount) => {
  ElMessageBox.confirm(
    `确定要删除账户 "${account.username}" 吗？此操作不可恢复！`,
    '确认删除',
    {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    }
  ).then(async () => {
    try {
      await request.delete(`/api/security/jwt/accounts/${account.username}`)
      ElMessage.success('账户删除成功')
      fetchAccounts()
    } catch (error) {
      console.error('删除失败:', error)
      ElMessage.error('账户删除失败')
    }
  }).catch(() => {
    // 用户取消删除
  })
}

// 重置表单
const resetForm = () => {
  accountForm.username = ''
  accountForm.password = ''
  accountForm.roles = []
  accountForm.enabled = true
  editingAccount.value = null
  if (accountFormRef.value) {
    accountFormRef.value.resetFields()
  }
}

// 组件挂载时获取账户列表
onMounted(() => {
  fetchAccounts()
})
</script>

<style scoped>
.jwt-account-management {
  padding: 20px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.dialog-footer {
  text-align: right;
}

/* 美化操作列按钮间距 */
.el-table__row .el-button + .el-button {
  margin-left: 8px;
}
</style>
