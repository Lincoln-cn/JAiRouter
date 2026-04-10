<template>
  <div class="jwt-account-management">
    <el-card class="main-card">
      <template #header>
        <div class="card-header">
          <span>JWT 账户管理</span>
          <div class="header-actions">
            <el-input
              v-model="searchKeyword"
              placeholder="搜索用户名..."
              clearable
              style="width: 200px; margin-right: 10px;"
            >
              <template #prefix>
                <el-icon><Search /></el-icon>
              </template>
            </el-input>
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

      <!-- 统计信息 -->
      <el-row :gutter="20" style="margin-bottom: 20px;">
        <el-col :span="6">
          <el-statistic title="总账户数" :value="accounts.length" />
        </el-col>
        <el-col :span="6">
          <el-statistic title="已启用" :value="accounts.filter(a => a.enabled).length" />
        </el-col>
        <el-col :span="6">
          <el-statistic title="已禁用" :value="accounts.filter(a => !a.enabled).length" />
        </el-col>
        <el-col :span="6">
          <el-statistic title="管理员" :value="accounts.filter(a => a.roles?.includes('ADMIN')).length" />
        </el-col>
      </el-row>

      <!-- 账户列表 -->
      <el-table :data="filteredAccounts" style="width: 100%" v-loading="loading" stripe border>
        <el-table-column prop="username" label="用户名" min-width="120" show-overflow-tooltip />
        <el-table-column label="角色" min-width="150">
          <template #default="scope">
            <el-tag
              v-for="role in scope.row.roles"
              :key="role"
              :type="role === 'ADMIN' ? 'danger' : 'primary'"
              size="small"
              style="margin-right: 5px;"
            >
              {{ role === 'ADMIN' ? '管理员' : '用户' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" min-width="80" align="center">
          <template #default="scope">
            <el-switch
              v-model="scope.row.enabled"
              @change="handleStatusChange(scope.row)"
              :loading="scope.row.statusLoading"
            />
          </template>
        </el-table-column>
        <el-table-column label="创建时间" min-width="140">
          <template #default="scope">
            {{ formatDateTime(scope.row.createdAt) }}
          </template>
        </el-table-column>
        <el-table-column label="更新时间" min-width="140">
          <template #default="scope">
            {{ formatDateTime(scope.row.updatedAt) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" min-width="100" fixed="right">
          <template #default="scope">
            <el-button-group>
              <el-button size="small" type="primary" @click="editAccount(scope.row)">
                <el-icon><Edit /></el-icon>
              </el-button>
              <el-button
                size="small"
                type="danger"
                @click="deleteAccount(scope.row)"
                :disabled="scope.row.username === 'admin'"
              >
                <el-icon><Delete /></el-icon>
              </el-button>
            </el-button-group>
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
      destroy-on-close
    >
      <el-alert
        v-if="editingAccount"
        title="编辑提示"
        type="info"
        description="密码字段留空表示不修改密码"
        :closable="false"
        show-icon
        style="margin-bottom: 20px;"
      />
      <el-form :model="accountForm" :rules="accountRules" ref="accountFormRef" label-width="80px">
        <el-form-item label="用户名" prop="username">
          <el-input
            v-model="accountForm.username"
            :disabled="!!editingAccount"
            placeholder="请输入用户名"
          />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input
            v-model="accountForm.password"
            type="password"
            show-password
            :placeholder="editingAccount ? '留空表示不修改' : '请输入密码'"
          />
        </el-form-item>
        <el-form-item v-if="!editingAccount" label="确认密码" prop="confirmPassword">
          <el-input
            v-model="accountForm.confirmPassword"
            type="password"
            show-password
            placeholder="请再次输入密码"
          />
        </el-form-item>
        <el-form-item label="角色" prop="roles">
          <el-select v-model="accountForm.roles" multiple placeholder="请选择角色" style="width: 100%;">
            <el-option label="管理员 (ADMIN)" value="ADMIN">
              <el-tag type="danger" size="small">管理员</el-tag>
              <span style="margin-left: 10px; color: #909399;">拥有所有权限</span>
            </el-option>
            <el-option label="普通用户 (USER)" value="USER">
              <el-tag type="primary" size="small">用户</el-tag>
              <span style="margin-left: 10px; color: #909399;">基础访问权限</span>
            </el-option>
          </el-select>
        </el-form-item>
        <el-form-item label="状态" prop="enabled">
          <el-switch v-model="accountForm.enabled" active-text="启用" inactive-text="禁用" />
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
import { ref, reactive, onMounted, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { Plus, Refresh, Search, Edit, Delete } from '@element-plus/icons-vue'
import {
  getJwtAccounts,
  createJwtAccount,
  updateJwtAccount,
  deleteJwtAccount,
  toggleJwtAccountStatus
} from '@/api/account'
import type { JwtAccount, CreateJwtAccountRequest } from '@/api/account'

// 数据状态
const accounts = ref<JwtAccount[]>([])
const loading = ref(false)
const showCreateDialog = ref(false)
const editingAccount = ref<JwtAccount | null>(null)
const searchKeyword = ref('')

// 表单数据
const accountForm = reactive({
  username: '',
  password: '',
  confirmPassword: '',
  roles: [] as string[],
  enabled: true
})

// 过滤后的账户列表
const filteredAccounts = computed(() => {
  if (!searchKeyword.value) return accounts.value
  return accounts.value.filter(a =>
    a.username.toLowerCase().includes(searchKeyword.value.toLowerCase())
  )
})

// 格式化日期时间
const formatDateTime = (dateStr?: string) => {
  if (!dateStr) return '-'
  try {
    const date = new Date(dateStr)
    return date.toLocaleString('zh-CN', {
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit'
    })
  } catch {
    return dateStr
  }
}

// 表单验证规则（动态计算）
const accountRules = computed<FormRules>(() => ({
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 3, max: 50, message: '用户名长度 3-50 字符', trigger: 'blur' }
  ],
  password: [
    { required: !editingAccount.value, message: '请输入密码', trigger: 'blur' },
    { min: 6, message: '密码长度至少 6 位', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: !editingAccount.value, message: '请确认密码', trigger: 'blur' },
    {
      validator: (rule, value, callback) => {
        if (!editingAccount.value && value !== accountForm.password) {
          callback(new Error('两次输入的密码不一致'))
        } else {
          callback()
        }
      },
      trigger: 'blur'
    }
  ],
  roles: [
    { required: true, message: '请选择角色', trigger: 'change' }
  ]
}))

// 表单引用
const accountFormRef = ref<FormInstance | null>(null)

// 获取账户列表
const fetchAccounts = async () => {
  loading.value = true
  try {
    accounts.value = await getJwtAccounts()
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
          // 更新账户 - 只传需要更新的字段
          const updateData: Partial<JwtAccount> = {
            roles: accountForm.roles,
            enabled: accountForm.enabled
          }
          // 只有填写了密码才更新
          if (accountForm.password && accountForm.password.trim()) {
            updateData.password = accountForm.password
          }
          await updateJwtAccount(accountForm.username, updateData as JwtAccount)
          ElMessage.success('账户更新成功')
        } else {
          // 创建账户
          const createRequest: CreateJwtAccountRequest = {
            username: accountForm.username,
            password: accountForm.password,
            roles: accountForm.roles,
            enabled: accountForm.enabled
          }
          await createJwtAccount(createRequest)
          ElMessage.success('账户创建成功')
        }

        showCreateDialog.value = false
        resetForm()
        fetchAccounts()
      } catch (error: any) {
        console.error('操作失败:', error)
        const errorMsg = error.response?.data?.message || (editingAccount.value ? '账户更新失败' : '账户创建失败')
        ElMessage.error(errorMsg)
      }
    }
  })
}

// 编辑账户
const editAccount = (account: JwtAccount) => {
  editingAccount.value = account
  accountForm.username = account.username
  accountForm.password = ''
  accountForm.confirmPassword = ''
  accountForm.roles = [...account.roles]
  accountForm.enabled = account.enabled
  showCreateDialog.value = true
}

// 处理状态变更（Switch 组件）
const handleStatusChange = async (account: JwtAccount) => {
  // 添加 loading 状态
  account.statusLoading = true
  const newStatus = account.enabled
  try {
    await toggleJwtAccountStatus(account.username, newStatus)
    ElMessage.success(newStatus ? '账户已启用' : '账户已禁用')
    fetchAccounts()
  } catch (error: any) {
    // 恢复原状态
    account.enabled = !newStatus
    console.error('状态切换失败:', error)
    ElMessage.error('状态切换失败')
  } finally {
    account.statusLoading = false
  }
}

// 删除账户
const deleteAccount = (account: JwtAccount) => {
  // 禁止删除 admin 账户
  if (account.username === 'admin') {
    ElMessage.warning('无法删除默认管理员账户')
    return
  }

  ElMessageBox.confirm(
    `确定要删除账户 "${account.username}" 吗？此操作不可恢复！`,
    '确认删除',
    {
      confirmButtonText: '确定删除',
      cancelButtonText: '取消',
      type: 'warning',
      confirmButtonClass: 'el-button--danger'
    }
  ).then(async () => {
    try {
      await deleteJwtAccount(account.username)
      ElMessage.success('账户删除成功')
      fetchAccounts()
    } catch (error: any) {
      console.error('删除失败:', error)
      const errorMsg = error.response?.data?.message || '账户删除失败'
      ElMessage.error(errorMsg)
    }
  }).catch(() => {
    // 用户取消删除
  })
}

// 重置表单
const resetForm = () => {
  accountForm.username = ''
  accountForm.password = ''
  accountForm.confirmPassword = ''
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

.header-actions {
  display: flex;
  align-items: center;
}

.dialog-footer {
  text-align: right;
}

/* 统计卡片样式 */
.el-statistic {
  text-align: center;
}

/* 表格样式 */
.el-table {
  margin-top: 20px;
}

/* 角色标签样式 */
.el-tag {
  margin-right: 5px;
}

/* 确保表格操作按钮紧凑 */
.el-button-group .el-button {
  padding: 5px 10px;
}
</style>