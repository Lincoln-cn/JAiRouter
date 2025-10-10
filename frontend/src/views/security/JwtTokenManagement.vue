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
            <el-button type="warning" @click="handleCleanupExpiredTokens">清理过期令牌</el-button>
          </div>
        </div>
      </template>

      <!-- 搜索和过滤区域 -->
      <div class="filter-section">
        <el-row :gutter="20">
          <el-col :span="6">
            <el-input
              v-model="searchForm.userId"
              placeholder="按用户ID搜索"
              clearable
              @clear="handleSearch"
              @keyup.enter="handleSearch"
            >
              <template #prefix>
                <el-icon><Search /></el-icon>
              </template>
            </el-input>
          </el-col>
          <el-col :span="4">
            <el-select v-model="searchForm.status" placeholder="状态筛选" clearable @change="handleSearch">
              <el-option label="全部" value="" />
              <el-option label="活跃" value="ACTIVE" />
              <el-option label="已撤销" value="REVOKED" />
              <el-option label="已过期" value="EXPIRED" />
            </el-select>
          </el-col>
          <el-col :span="4">
            <el-button type="primary" @click="handleSearch">搜索</el-button>
            <el-button @click="handleResetSearch">重置</el-button>
          </el-col>
        </el-row>
      </div>

      <el-table
          v-loading="loading"
          :data="tokenList.content"
          style="width: 100%"
          @selection-change="handleSelectionChange"
      >
        <el-table-column type="selection" width="55"/>
        <el-table-column prop="userId" label="用户ID" width="150" />
        <el-table-column label="令牌ID" prop="id" width="120" show-overflow-tooltip>
          <template #default="scope">
            <span>{{ formatTokenId(scope.row.id) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="令牌哈希" prop="tokenHash" show-overflow-tooltip>
          <template #default="scope">
            <span>{{ formatToken(scope.row.tokenHash) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="设备信息" prop="deviceInfo" width="120" show-overflow-tooltip />
        <el-table-column label="IP地址" prop="ipAddress" width="120" />
        <el-table-column label="签发时间" prop="issuedAt" width="160">
          <template #default="scope">
            {{ formatDateTime(scope.row.issuedAt) }}
          </template>
        </el-table-column>
        <el-table-column label="过期时间" prop="expiresAt" width="160">
          <template #default="scope">
            {{ formatDateTime(scope.row.expiresAt) }}
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100">
          <template #default="scope">
            <el-tag :type="getStatusTagType(scope.row.status)">
              {{ getStatusText(scope.row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="scope">
            <el-button 
              size="small" 
              type="primary"
              @click="handleViewDetails(scope.row)"
            >
              详情
            </el-button>
            <el-button 
              size="small" 
              type="danger" 
              :disabled="scope.row.status !== 'ACTIVE'"
              @click="handleRevoke(scope.row)"
            >
              撤销
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页组件 -->
      <div class="pagination-container">
        <el-pagination
          v-model:current-page="pagination.page"
          v-model:page-size="pagination.size"
          :page-sizes="[10, 20, 50, 100]"
          :total="tokenList.totalElements"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="handleSizeChange"
          @current-change="handleCurrentChange"
        />
      </div>
    </el-card>
    
    <!-- 令牌详情对话框 -->
    <el-dialog v-model="tokenDetailsDialogVisible" title="令牌详情" width="800px">
      <el-descriptions v-if="selectedTokenDetails" :column="2" border>
        <el-descriptions-item label="令牌ID">{{ selectedTokenDetails.id }}</el-descriptions-item>
        <el-descriptions-item label="用户ID">{{ selectedTokenDetails.userId }}</el-descriptions-item>
        <el-descriptions-item label="令牌哈希" :span="2">
          <el-input :value="selectedTokenDetails.tokenHash" readonly />
        </el-descriptions-item>
        <el-descriptions-item label="设备信息">{{ selectedTokenDetails.deviceInfo || '未知' }}</el-descriptions-item>
        <el-descriptions-item label="IP地址">{{ selectedTokenDetails.ipAddress || '未知' }}</el-descriptions-item>
        <el-descriptions-item label="签发时间">{{ formatDateTime(selectedTokenDetails.issuedAt) }}</el-descriptions-item>
        <el-descriptions-item label="过期时间">{{ formatDateTime(selectedTokenDetails.expiresAt) }}</el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ formatDateTime(selectedTokenDetails.createdAt) }}</el-descriptions-item>
        <el-descriptions-item label="更新时间">{{ formatDateTime(selectedTokenDetails.updatedAt) }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="getStatusTagType(selectedTokenDetails.status)">
            {{ getStatusText(selectedTokenDetails.status) }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="撤销者" v-if="selectedTokenDetails.revokedBy">
          {{ selectedTokenDetails.revokedBy }}
        </el-descriptions-item>
        <el-descriptions-item label="撤销时间" v-if="selectedTokenDetails.revokedAt">
          {{ formatDateTime(selectedTokenDetails.revokedAt) }}
        </el-descriptions-item>
        <el-descriptions-item label="撤销原因" :span="2" v-if="selectedTokenDetails.revokeReason">
          {{ selectedTokenDetails.revokeReason }}
        </el-descriptions-item>
      </el-descriptions>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="tokenDetailsDialogVisible = false">关闭</el-button>
          <el-button 
            v-if="selectedTokenDetails?.status === 'ACTIVE'" 
            type="danger" 
            @click="handleRevokeFromDetails"
          >
            撤销此令牌
          </el-button>
        </span>
      </template>
    </el-dialog>

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
import {onMounted, ref, reactive} from 'vue'
import {ElMessage, ElMessageBox} from 'element-plus'
import {
  type BatchTokenRevokeRequest,
  type BlacklistStats,
  type JwtTokenInfo,
  type PagedResult,
  type CleanupResult,
  getBlacklistStats,
  getTokens,
  getTokenDetails,
  cleanupExpiredTokens,
  revokeToken,
  revokeTokensBatch,
  type TokenRevokeRequest
} from '@/api/jwtToken'
import {CircleCloseFilled, SuccessFilled, Search} from '@element-plus/icons-vue'

// 令牌数据
const tokenList = ref<PagedResult<JwtTokenInfo>>({
  content: [],
  totalElements: 0,
  totalPages: 0,
  size: 20,
  page: 0,
  first: true,
  last: true,
  hasNext: false,
  hasPrevious: false
})

// 搜索表单
const searchForm = reactive({
  userId: '',
  status: ''
})

// 分页信息
const pagination = reactive({
  page: 1,
  size: 20
})

// 令牌详情
const selectedTokenDetails = ref<JwtTokenInfo | null>(null)
const tokenDetailsDialogVisible = ref(false)

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
const selectedTokens = ref<JwtTokenInfo[]>([])
const batchRevokeForm = ref({
  reason: ''
})

// 格式化令牌显示
const formatToken = (tokenHash: string) => {
  if (!tokenHash) return ''
  if (tokenHash.length <= 20) return tokenHash
  return `${tokenHash.substring(0, 10)}...${tokenHash.substring(tokenHash.length - 10)}`
}

// 格式化令牌ID显示
const formatTokenId = (tokenId: string) => {
  if (!tokenId) return ''
  if (tokenId.length <= 12) return tokenId
  return `${tokenId.substring(0, 8)}...`
}

// 获取状态标签类型
const getStatusTagType = (status: string) => {
  switch (status) {
    case 'ACTIVE':
      return 'success'
    case 'REVOKED':
      return 'danger'
    case 'EXPIRED':
      return 'warning'
    default:
      return 'info'
  }
}

// 获取状态文本
const getStatusText = (status: string) => {
  switch (status) {
    case 'ACTIVE':
      return '活跃'
    case 'REVOKED':
      return '已撤销'
    case 'EXPIRED':
      return '已过期'
    default:
      return '未知'
  }
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
const handleSelectionChange = (selection: JwtTokenInfo[]) => {
  selectedTokens.value = selection
}

// 加载令牌列表
const loadTokens = async () => {
  loading.value = true
  try {
    const result = await getTokens(
      pagination.page - 1, // API使用0基索引
      pagination.size,
      searchForm.userId || undefined,
      searchForm.status || undefined
    )
    tokenList.value = result
  } catch (error: any) {
    ElMessage.error('加载令牌列表失败: ' + (error.message || '未知错误'))
  } finally {
    loading.value = false
  }
}

// 刷新令牌列表
const handleRefreshTokens = async () => {
  await loadTokens()
  ElMessage.success('令牌列表已刷新')
}

// 搜索处理
const handleSearch = async () => {
  pagination.page = 1 // 重置到第一页
  await loadTokens()
}

// 重置搜索
const handleResetSearch = async () => {
  searchForm.userId = ''
  searchForm.status = ''
  pagination.page = 1
  await loadTokens()
}

// 分页大小变化
const handleSizeChange = async (newSize: number) => {
  pagination.size = newSize
  pagination.page = 1
  await loadTokens()
}

// 当前页变化
const handleCurrentChange = async (newPage: number) => {
  pagination.page = newPage
  await loadTokens()
}

// 查看令牌详情
const handleViewDetails = async (token: JwtTokenInfo) => {
  try {
    const details = await getTokenDetails(token.id)
    selectedTokenDetails.value = details
    tokenDetailsDialogVisible.value = true
  } catch (error: any) {
    ElMessage.error('获取令牌详情失败: ' + (error.message || '未知错误'))
  }
}

// 撤销令牌
const handleRevoke = (token: JwtTokenInfo) => {
  ElMessageBox.confirm(`确定要撤销用户 ${token.userId} 的令牌吗？`, '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(async () => {
    try {
      const revokeRequest: TokenRevokeRequest = {
        token: token.tokenHash, // 使用tokenHash而不是完整token
        userId: token.userId,
        reason: '管理员手动撤销'
      }

      const result = await revokeToken(revokeRequest)
      if (result) {
        ElMessage.success('令牌已撤销')
        // 刷新令牌列表和统计信息
        await loadTokens()
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

// 从详情页面撤销令牌
const handleRevokeFromDetails = async () => {
  if (!selectedTokenDetails.value) return
  
  ElMessageBox.confirm(`确定要撤销用户 ${selectedTokenDetails.value.userId} 的令牌吗？`, '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(async () => {
    try {
      const revokeRequest: TokenRevokeRequest = {
        token: selectedTokenDetails.value!.tokenHash,
        userId: selectedTokenDetails.value!.userId,
        reason: '管理员手动撤销'
      }

      const result = await revokeToken(revokeRequest)
      if (result) {
        ElMessage.success('令牌已撤销')
        tokenDetailsDialogVisible.value = false
        selectedTokenDetails.value = null
        // 刷新令牌列表和统计信息
        await loadTokens()
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

// 清理过期令牌
const handleCleanupExpiredTokens = async () => {
  ElMessageBox.confirm('确定要清理所有过期的令牌吗？此操作不可撤销。', '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(async () => {
    try {
      loading.value = true
      const result: CleanupResult = await cleanupExpiredTokens()
      ElMessage.success(`清理完成！清理了 ${result.cleanedTokens} 个过期令牌和 ${result.cleanedBlacklistEntries} 个黑名单条目`)
      // 刷新令牌列表和统计信息
      await loadTokens()
      await fetchBlacklistStats()
    } catch (error: any) {
      ElMessage.error('清理过期令牌失败: ' + (error.message || '未知错误'))
    } finally {
      loading.value = false
    }
  }).catch(() => {
    // 用户取消操作
  })
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
      tokens: selectedTokens.value.map(token => token.tokenHash), // 使用tokenHash
      reason: batchRevokeForm.value.reason
    }

    const result = await revokeTokensBatch(batchRevokeRequest)
    if (result) {
      ElMessage.success(`成功撤销${selectedTokens.value.length}个令牌`)
      batchRevokeDialogVisible.value = false
      selectedTokens.value = []
      // 刷新令牌列表和统计信息
      await loadTokens()
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
  // 加载令牌列表和黑名单统计信息
  await loadTokens()
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

.filter-section {
  margin-bottom: 20px;
  padding: 16px;
  background-color: #f5f7fa;
  border-radius: 4px;
}

.pagination-container {
  margin-top: 20px;
  display: flex;
  justify-content: center;
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}
</style>