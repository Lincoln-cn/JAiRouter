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
            <el-button @click="showConfigDialog = true">
              <el-icon><Setting /></el-icon>
              配置管理
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
    
    <!-- 配置管理对话框 -->
    <el-dialog 
      title="JWT账户配置管理" 
      v-model="showConfigDialog" 
      width="800px"
      @close="closeConfigDialog"
    >
      <el-tabs v-model="configActiveTab">
        <!-- 配置状态 -->
        <el-tab-pane label="配置状态" name="status">
          <el-descriptions :column="1" border>
            <el-descriptions-item label="是否存在持久化配置">
              <el-tag :type="configStatus.hasPersistedConfig ? 'success' : 'info'">
                {{ configStatus.hasPersistedConfig ? '是' : '否' }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="当前版本号">
              <el-tag type="primary">{{ configStatus.currentVersion }}</el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="总版本数">
              <el-tag type="primary">{{ configStatus.totalVersions }}</el-tag>
            </el-descriptions-item>
          </el-descriptions>
          
          <div style="margin-top: 20px; text-align: right;">
            <el-button type="warning" @click="resetToDefault" :loading="resetLoading">
              重置为默认配置
            </el-button>
          </div>
        </el-tab-pane>
        
        <!-- 版本管理 -->
        <el-tab-pane label="版本管理" name="versions">
          <el-table :data="versions" style="width: 100%" v-loading="versionsLoading">
            <el-table-column prop="version" label="版本号" width="100">
              <template #default="scope">
                <el-tag :type="scope.row.isCurrent ? 'success' : 'info'">
                  {{ scope.row.version }} 
                  <span v-if="scope.row.isCurrent">(当前)</span>
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="created" label="创建时间" width="200" />
            <el-table-column label="操作">
              <template #default="scope">
                <el-button 
                  size="small" 
                  type="primary" 
                  @click="viewVersionConfig(scope.row.version)"
                >
                  查看配置
                </el-button>
                <el-button 
                  size="small" 
                  :type="scope.row.isCurrent ? 'info' : 'success'"
                  :disabled="scope.row.isCurrent"
                  @click="applyVersion(scope.row.version)"
                >
                  应用此版本
                </el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
        
        <!-- 配置详情 -->
        <el-tab-pane label="配置详情" name="config-detail" v-if="showConfigDetail">
          <el-alert
            title="配置详情"
            type="info"
            description="当前显示的是指定版本的JWT账户配置信息"
            show-icon
            style="margin-bottom: 20px;"
          />
          
          <el-card>
            <pre style="white-space: pre-wrap; word-wrap: break-word;">{{ versionConfigContent }}</pre>
          </el-card>
        </el-tab-pane>
      </el-tabs>
      
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="showConfigDialog = false">关闭</el-button>
        </span>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { 
  getJwtAccounts, 
  createJwtAccount, 
  updateJwtAccount, 
  deleteJwtAccount, 
  toggleJwtAccountStatus,
  getJwtAccountVersions,
  getJwtAccountVersionConfig,
  applyJwtAccountVersion,
  getCurrentJwtAccountVersion,
  resetJwtAccountsToDefault,
  getJwtAccountConfigStatus
} from '@/api/account'
import type { JwtAccount, CreateJwtAccountRequest, JwtAccountConfigStatus } from '@/api/account'

// 数据状态
const accounts = ref<JwtAccount[]>([])
const loading = ref(false)
const showCreateDialog = ref(false)
const editingAccount = ref<JwtAccount | null>(null)

// 配置管理状态
const showConfigDialog = ref(false)
const configActiveTab = ref('status')
const configStatus = ref<JwtAccountConfigStatus>({
  hasPersistedConfig: false,
  currentVersion: 0,
  totalVersions: 0
})
const versions = ref<Array<{version: number, isCurrent: boolean, created?: string}>>([])
const versionsLoading = ref(false)
const resetLoading = ref(false)
const showConfigDetail = ref(false)
const versionConfigContent = ref('')

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
          // 更新账户
          await updateJwtAccount(accountForm.username, accountForm)
          ElMessage.success('账户更新成功')
        } else {
          // 创建账户
          const createRequest: CreateJwtAccountRequest = {
            username: accountForm.username,
            password: accountForm.password!,
            roles: accountForm.roles,
            enabled: accountForm.enabled
          }
          await createJwtAccount(createRequest)
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
    await toggleJwtAccountStatus(account.username, !account.enabled)
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
      await deleteJwtAccount(account.username)
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

// ==================== 配置管理功能 ====================

// 获取配置状态
const fetchConfigStatus = async () => {
  try {
    configStatus.value = await getJwtAccountConfigStatus()
  } catch (error) {
    console.error('获取配置状态失败:', error)
    ElMessage.error('获取配置状态失败')
  }
}

// 获取版本列表
const fetchVersions = async () => {
  versionsLoading.value = true
  try {
    const versionNumbers = await getJwtAccountVersions()
    const currentVersion = await getCurrentJwtAccountVersion()
    
    versions.value = versionNumbers.map(version => ({
      version,
      isCurrent: version === currentVersion,
      created: new Date().toLocaleString() // 简化处理，实际应从后端获取创建时间
    }))
  } catch (error) {
    console.error('获取版本列表失败:', error)
    ElMessage.error('获取版本列表失败')
  } finally {
    versionsLoading.value = false
  }
}

// 查看版本配置
const viewVersionConfig = async (version: number) => {
  try {
    const config = await getJwtAccountVersionConfig(version)
    versionConfigContent.value = JSON.stringify(config, null, 2)
    showConfigDetail.value = true
    configActiveTab.value = 'config-detail'
  } catch (error) {
    console.error('获取版本配置失败:', error)
    ElMessage.error('获取版本配置失败')
  }
}

// 应用版本
const applyVersion = async (version: number) => {
  ElMessageBox.confirm(
    `确定要应用版本 ${version} 的配置吗？这将替换当前的账户配置。`,
    '确认应用',
    {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    }
  ).then(async () => {
    try {
      await applyJwtAccountVersion(version)
      ElMessage.success(`版本 ${version} 应用成功`)
      // 刷新账户列表和配置状态
      await fetchAccounts()
      await fetchConfigStatus()
      await fetchVersions()
    } catch (error) {
      console.error('应用版本失败:', error)
      ElMessage.error('应用版本失败')
    }
  }).catch(() => {
    // 用户取消
  })
}

// 重置为默认配置
const resetToDefault = () => {
  ElMessageBox.confirm(
    '确定要重置JWT账户配置为默认值吗？这将清除所有自定义的账户配置。',
    '确认重置',
    {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    }
  ).then(async () => {
    resetLoading.value = true
    try {
      await resetJwtAccountsToDefault()
      ElMessage.success('配置已重置为默认值')
      // 刷新所有数据
      await fetchAccounts()
      await fetchConfigStatus()
      await fetchVersions()
    } catch (error) {
      console.error('重置配置失败:', error)
      ElMessage.error('重置配置失败')
    } finally {
      resetLoading.value = false
    }
  }).catch(() => {
    // 用户取消
  })
}

// 关闭配置对话框
const closeConfigDialog = () => {
  showConfigDialog.value = false
  configActiveTab.value = 'status'
  showConfigDetail.value = false
  versionConfigContent.value = ''
}

// 组件挂载时获取账户列表
onMounted(() => {
  fetchAccounts()
})

// 监听配置对话框打开事件
watch(showConfigDialog, (newVal: boolean) => {
  if (newVal) {
    fetchConfigStatus()
    fetchVersions()
  }
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