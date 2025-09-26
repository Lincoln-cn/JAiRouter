<template>
  <div class="jwt-token-management">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>JWT令牌管理</span>
          <div>
            <el-button :loading="loading" type="primary" @click="handleRefreshTokens">刷新令牌列表</el-button>
            <el-button :disabled="selectedTokens.length === 0" type="danger" @click="handleOpenBatchRevoke">
              批量撤销({{ selectedTokens.length }})
            </el-button>
          </div>
        </div>
      </template>

      <el-table
          v-loading="loading"
          :data="tokens"
          style="width: 100%"
          @selection-change="handleSelectionChange"
      >
        <el-table-column type="selection" width="55"/>
        <el-table-column prop="userId" label="用户ID" width="180" />
        <el-table-column label="令牌" prop="token" show-overflow-tooltip>
          <template #default="scope">
            <span>{{ formatToken(scope.row.token) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="签发时间" prop="issuedAt" width="200">
          <template #default="scope">
            {{ formatDateTime(scope.row.issuedAt) }}
          </template>
        </el-table-column>
        <el-table-column label="过期时间" prop="expiresAt" width="200">
          <template #default="scope">
            {{ formatDateTime(scope.row.expiresAt) }}
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100">
          <template #default="scope">
            <el-tag :type="scope.row.status === 'active' ? 'success' : 'danger'">
              {{ scope.row.status === 'active' ? '有效' : '已撤销' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200">
          <template #default="scope">
            <el-button 
              size="small" 
              type="danger" 
              :disabled="scope.row.status !== 'active'"
              @click="handleRevoke(scope.row)"
            >
              撤销
            </el-button>
            <el-button
                size="small"
                @click="handleValidate(scope.row)"
            >
              验证
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
    
    <!-- 批量撤销令牌对话框 -->
    <el-dialog v-model="batchRevokeDialogVisible" title="批量撤销令牌" width="500px">
      <el-form :model="batchRevokeForm" label-width="100px">
        <el-form-item label="撤销原因">
          <el-input
              v-model="batchRevokeForm.reason"
              placeholder="请输入撤销原因（可选）"
              type="textarea"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="batchRevokeDialogVisible = false">取消</el-button>
          <el-button :loading="batchRevokeLoading" type="primary" @click="handleBatchRevoke">撤销</el-button>
        </span>
      </template>
    </el-dialog>

    <!-- 黑名单统计信息卡片 -->
    <el-card style="margin-top: 20px;">
      <template #header>
        <div class="card-header">
          <span>黑名单统计</span>
          <el-button :loading="statsLoading" type="primary" @click="fetchBlacklistStats">刷新统计</el-button>
        </div>
      </template>
      <el-row :gutter="20">
        <el-col :span="8">
          <el-statistic :value="blacklistStats.memoryBlacklistSize" title="内存黑名单大小"/>
        </el-col>
        <el-col :span="8">
          <el-statistic :value="blacklistStats.blacklistEnabled ? '启用' : '禁用'" title="黑名单功能">
            <template #prefix>
              <el-icon :style="{ color: blacklistStats.blacklistEnabled ? '#67c23a' : '#f56c6c' }">
                <SuccessFilled v-if="blacklistStats.blacklistEnabled"/>
                <CircleCloseFilled v-else/>
              </el-icon>
            </template>
          </el-statistic>
        </el-col>
        <el-col :span="8">
          <el-statistic :value="formatDateTime(blacklistStats.lastCleanupTime)" title="最后清理时间"/>
        </el-col>
      </el-row>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import {onMounted, ref} from 'vue'
import {ElMessage, ElMessageBox} from 'element-plus'
import {
  type BatchTokenRevokeRequest,
  type BlacklistStats,
  getBlacklistStats,
  revokeToken,
  revokeTokensBatch,
  type TokenRevokeRequest,
  type TokenValidationRequest,
  type TokenValidationResponse,
  validateToken
} from '@/api/jwtToken'
import {CircleCloseFilled, SuccessFilled} from '@element-plus/icons-vue'

// 令牌数据
const tokens = ref<any[]>([
  { 
    userId: 'user-1',
    token: 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiaWF0IjoxNTE2MjM5MDIyfQ.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c',
    issuedAt: '2023-10-01T10:00:00',
    expiresAt: '2023-10-01T11:00:00', 
    status: 'active'
  },
  { 
    userId: 'user-2',
    token: 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiaWF0IjoxNTE2MjM5MDIyfQ.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c',
    issuedAt: '2023-10-01T09:30:00',
    expiresAt: '2023-10-01T10:30:00', 
    status: 'revoked'
  },
  { 
    userId: 'user-1',
    token: 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiaWF0IjoxNTE2MjM5MDIyfQ.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c',
    issuedAt: '2023-10-01T08:00:00',
    expiresAt: '2023-10-01T09:00:00', 
    status: 'revoked'
  }
])

// 黑名单统计数据
const blacklistStats = ref<BlacklistStats>({
  memoryBlacklistSize: 0,
  blacklistEnabled: true,
  lastCleanupTime: 0
})

// 加载状态
const loading = ref(false)
const statsLoading = ref(false)
const batchRevokeLoading = ref(false)

// 批量撤销相关
const batchRevokeDialogVisible = ref(false)
const selectedTokens = ref<any[]>([])
const batchRevokeForm = ref({
  reason: ''
})

// 格式化令牌显示
const formatToken = (token: string) => {
  if (!token) return ''
  if (token.length <= 20) return token
  return `${token.substring(0, 10)}...${token.substring(token.length - 10)}`
}

// 格式化日期时间
const formatDateTime = (dateTime: string | number) => {
  if (!dateTime) return ''

  // 如果是数字，转换为日期
  if (typeof dateTime === 'number') {
    return new Date(dateTime).toLocaleString('zh-CN')
  }

  // 如果是字符串，直接转换
  return new Date(dateTime).toLocaleString('zh-CN')
}

// 处理选择变化
const handleSelectionChange = (selection: any[]) => {
  selectedTokens.value = selection
}

// 刷新令牌列表
const handleRefreshTokens = () => {
  loading.value = true
  // 模拟异步操作
  setTimeout(() => {
    loading.value = false
    ElMessage.success('令牌列表已刷新')
  }, 500)
}

// 撤销令牌
const handleRevoke = (row: any) => {
  ElMessageBox.confirm(`确定要撤销用户 ${row.userId} 的令牌吗？`, '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(async () => {
    try {
      const revokeRequest: TokenRevokeRequest = {
        token: row.token,
        userId: row.userId,
        reason: '管理员手动撤销'
      }

      const result = await revokeToken(revokeRequest)
      if (result) {
        row.status = 'revoked'
        ElMessage.success('令牌已撤销')
        // 刷新统计信息
        await fetchBlacklistStats()
      } else {
        ElMessage.error('令牌撤销失败')
      }
    } catch (error: any) {
      ElMessage.error('令牌撤销失败: ' + (error.message || '未知错误'))
    }
  }).catch(() => {
    // 用户取消操作
  })
}

// 验证令牌
const handleValidate = async (row: any) => {
  try {
    const validationRequest: TokenValidationRequest = {
      token: row.token
    }

    const result: TokenValidationResponse = await validateToken(validationRequest)
    if (result.valid) {
      ElMessage.success(`令牌有效，用户ID: ${result.userId}`)
    } else {
      ElMessage.warning(`令牌无效: ${result.message}`)
    }
  } catch (error: any) {
    ElMessage.error('令牌验证失败: ' + (error.message || '未知错误'))
  }
}

// 打开批量撤销对话框
const handleOpenBatchRevoke = () => {
  batchRevokeForm.value = {
    reason: ''
  }
  batchRevokeDialogVisible.value = true
}

// 批量撤销令牌
const handleBatchRevoke = async () => {
  if (selectedTokens.value.length === 0) {
    ElMessage.warning('请先选择要撤销的令牌')
    return
  }

  batchRevokeLoading.value = true
  try {
    const batchRevokeRequest: BatchTokenRevokeRequest = {
      tokens: selectedTokens.value.map(token => token.token),
      reason: batchRevokeForm.value.reason
    }

    const result = await revokeTokensBatch(batchRevokeRequest)
    if (result) {
      // 更新本地状态
      selectedTokens.value.forEach(token => {
        const index = tokens.value.findIndex(t => t.token === token.token)
        if (index !== -1) {
          tokens.value[index].status = 'revoked'
        }
      })

      ElMessage.success(`成功撤销${selectedTokens.value.length}个令牌`)
      batchRevokeDialogVisible.value = false
      selectedTokens.value = []
      // 刷新统计信息
      await fetchBlacklistStats()
    } else {
      ElMessage.error('批量撤销失败')
    }
  } catch (error: any) {
    ElMessage.error('批量撤销失败: ' + (error.message || '未知错误'))
  } finally {
    batchRevokeLoading.value = false
  }
}

// 获取黑名单统计信息
const fetchBlacklistStats = async () => {
  statsLoading.value = true
  try {
    const stats = await getBlacklistStats()
    blacklistStats.value = stats
  } catch (error: any) {
    ElMessage.error('获取黑名单统计信息失败: ' + (error.message || '未知错误'))
  } finally {
    statsLoading.value = false
  }
}

// 组件挂载时获取数据
onMounted(async () => {
  // 获取黑名单统计信息
  await fetchBlacklistStats()
  console.log('JWT令牌管理页面已加载')
})
</script>

<style scoped>
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.jwt-token-management {
  padding: 20px;
}
</style>