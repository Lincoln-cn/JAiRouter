<template>
  <PageSkeleton :title="t('jwtTokens.title')">
    <template #actions>
      <el-button :loading="loading" type="primary" @click="handleRefreshTokens">{{ t('jwtTokens.refreshList') }}</el-button>
      <el-button :disabled="selectedTokens.length === 0" type="danger" @click="handleOpenBatchRevoke">
        {{ t('jwtTokens.batchRevokeCount', { count: selectedTokens.length }) }}
      </el-button>
      <el-button type="warning" @click="handleCleanupExpiredTokens">{{ t('jwtTokens.cleanupExpired') }}</el-button>
    </template>

    <template #toolbar>
      <el-input
        v-model="searchForm.userId"
        :placeholder="t('jwtTokens.searchUserIdPlaceholder')"
        clearable
        @clear="handleSearch"
        @keyup.enter="handleSearch"
      >
        <template #prefix>
          <el-icon><Search /></el-icon>
        </template>
      </el-input>
      <el-select v-model="searchForm.status" :placeholder="t('jwtTokens.statusFilter')" clearable @change="handleSearch">
        <el-option :label="t('jwtTokens.all')" value="" />
        <el-option :label="t('jwtTokens.statusActive')" value="ACTIVE" />
        <el-option :label="t('jwtTokens.statusRevoked')" value="REVOKED" />
        <el-option :label="t('jwtTokens.statusExpired')" value="EXPIRED" />
      </el-select>
      <el-button type="primary" @click="handleSearch">{{ t('jwtTokens.search') }}</el-button>
      <el-button @click="handleResetSearch">{{ t('jwtTokens.reset') }}</el-button>
    </template>

    <el-table
        v-loading="loading"
        :data="tokenList.content"
        style="width: 100%"
        @selection-change="handleSelectionChange"
    >
      <el-table-column type="selection" width="55"/>
      <el-table-column prop="userId" :label="t('jwtTokens.userId')" width="150" />
      <el-table-column :label="t('jwtTokens.tokenId')" prop="id" width="120" show-overflow-tooltip>
        <template #default="scope">
          <span>{{ formatTokenId(scope.row.id) }}</span>
        </template>
      </el-table-column>
      <el-table-column :label="t('jwtTokens.tokenHash')" prop="tokenHash" show-overflow-tooltip>
        <template #default="scope">
          <span>{{ formatToken(scope.row.tokenHash) }}</span>
        </template>
      </el-table-column>
      <el-table-column :label="t('jwtTokens.deviceInfo')" prop="deviceInfo" width="120" show-overflow-tooltip />
      <el-table-column :label="t('jwtTokens.ipAddress')" prop="ipAddress" width="120" />
      <el-table-column :label="t('jwtTokens.issuedAt')" prop="issuedAt" width="160">
        <template #default="scope">
          {{ formatDateTime(scope.row.issuedAt) }}
        </template>
      </el-table-column>
      <el-table-column :label="t('jwtTokens.expiresAt')" prop="expiresAt" width="160">
        <template #default="scope">
          {{ formatDateTime(scope.row.expiresAt) }}
        </template>
      </el-table-column>
      <el-table-column prop="status" :label="t('jwtTokens.status')" width="100">
        <template #default="scope">
          <el-tag :type="getStatusTagType(scope.row.status)">
            {{ getStatusText(scope.row.status) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column :label="t('jwtTokens.blacklist')" width="120" fixed="right">
        <template #default="scope">
          <el-dropdown trigger="click" @command="(cmd: string) => handleAddToBlacklist(scope.row, cmd)">
            <el-button size="small" type="warning">
              <el-icon><Warning /></el-icon>{{ t('jwtTokens.join') }}
            </el-button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="TOKEN">{{ t('jwtTokens.banToken') }}</el-dropdown-item>
                <el-dropdown-item command="IP" :disabled="!scope.row.ipAddress">{{ t('jwtTokens.banIp', { ip: scope.row.ipAddress }) }}</el-dropdown-item>
                <el-dropdown-item command="DEVICE" :disabled="!scope.row.deviceInfo">{{ t('jwtTokens.banDevice') }}</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </template>
      </el-table-column>
      <el-table-column :label="t('jwtTokens.actions')" width="150" fixed="right">
        <template #default="scope">
          <el-button 
            size="small" 
            type="primary"
            @click="handleViewDetails(scope.row)"
          >
            {{ t('jwtTokens.details') }}
          </el-button>
          <el-button 
            size="small" 
            type="danger" 
            :disabled="scope.row.status !== 'ACTIVE'"
            @click="handleRevoke(scope.row)"
          >
            {{ t('jwtTokens.revoke') }}
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <template #footer>
      <el-pagination
        v-model:current-page="pagination.page"
        v-model:page-size="pagination.size"
        :page-sizes="[10, 20, 50, 100]"
        :total="tokenList.totalElements"
        layout="total, sizes, prev, pager, next, jumper"
        @size-change="handleSizeChange"
        @current-change="handleCurrentChange"
      />
    </template>
  </PageSkeleton>
    
    <!-- 令牌详情对话框 -->
    <el-dialog v-model="tokenDetailsDialogVisible" :title="t('jwtTokens.detailTitle')" width="800px">
      <el-descriptions v-if="selectedTokenDetails" :column="2" border>
        <el-descriptions-item :label="t('jwtTokens.tokenId')">{{ selectedTokenDetails.id }}</el-descriptions-item>
        <el-descriptions-item :label="t('jwtTokens.userId')">{{ selectedTokenDetails.userId }}</el-descriptions-item>
        <el-descriptions-item :label="t('jwtTokens.tokenHash')" :span="2">
          <el-input :value="selectedTokenDetails.tokenHash" readonly />
        </el-descriptions-item>
        <el-descriptions-item :label="t('jwtTokens.deviceInfo')">{{ selectedTokenDetails.deviceInfo || t('jwtTokens.unknown') }}</el-descriptions-item>
        <el-descriptions-item :label="t('jwtTokens.ipAddress')">{{ selectedTokenDetails.ipAddress || t('jwtTokens.unknown') }}</el-descriptions-item>
        <el-descriptions-item :label="t('jwtTokens.issuedAt')">{{ formatDateTime(selectedTokenDetails.issuedAt) }}</el-descriptions-item>
        <el-descriptions-item :label="t('jwtTokens.expiresAt')">{{ formatDateTime(selectedTokenDetails.expiresAt) }}</el-descriptions-item>
        <el-descriptions-item :label="t('jwtTokens.createdAt')">{{ formatDateTime(selectedTokenDetails.createdAt) }}</el-descriptions-item>
        <el-descriptions-item :label="t('jwtTokens.updatedAt')">{{ formatDateTime(selectedTokenDetails.updatedAt) }}</el-descriptions-item>
        <el-descriptions-item :label="t('jwtTokens.status')">
          <el-tag :type="getStatusTagType(selectedTokenDetails.status)">
            {{ getStatusText(selectedTokenDetails.status) }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item :label="t('jwtTokens.revokedBy')" v-if="selectedTokenDetails.revokedBy">
          {{ selectedTokenDetails.revokedBy }}
        </el-descriptions-item>
        <el-descriptions-item :label="t('jwtTokens.revokedAt')" v-if="selectedTokenDetails.revokedAt">
          {{ formatDateTime(selectedTokenDetails.revokedAt) }}
        </el-descriptions-item>
        <el-descriptions-item :label="t('jwtTokens.revokeReason')" :span="2" v-if="selectedTokenDetails.revokeReason">
          {{ selectedTokenDetails.revokeReason }}
        </el-descriptions-item>
      </el-descriptions>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="tokenDetailsDialogVisible = false">{{ t('jwtTokens.close') }}</el-button>
          <el-button 
            v-if="selectedTokenDetails?.status === 'ACTIVE'" 
            type="danger" 
            @click="handleRevokeFromDetails"
          >
            {{ t('jwtTokens.revokeThisToken') }}
          </el-button>
        </span>
      </template>
    </el-dialog>

    <!-- 批量撤销令牌对话框 -->
    <el-dialog v-model="batchRevokeDialogVisible" :title="t('jwtTokens.batchRevokeTitle')" width="500px">
      <el-form :model="batchRevokeForm" label-width="100px">
        <el-form-item :label="t('jwtTokens.revokeReason')">
          <el-input
              v-model="batchRevokeForm.reason"
              :placeholder="t('jwtTokens.reasonPlaceholder')"
              type="textarea"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="batchRevokeDialogVisible = false">{{ t('jwtTokens.cancel') }}</el-button>
          <el-button :loading="batchRevokeLoading" type="primary" @click="handleBatchRevoke">{{ t('jwtTokens.revoke') }}</el-button>
        </span>
      </template>
    </el-dialog>
</template>

<script setup lang="ts">
import {onMounted, ref, reactive} from 'vue'
import {ElMessage, ElMessageBox} from 'element-plus'
import {useI18n} from 'vue-i18n'
import {
  type BatchTokenRevokeRequest,
  type JwtTokenInfo,
  type PagedResult,
  type CleanupResult,
  getTokens,
  getTokenDetails,
  cleanupExpiredTokens,
  revokeToken,
  revokeTokensBatch,
  type TokenRevokeRequest
} from '@/api/jwtToken'
import { addToBlacklist } from '@/api/blacklist'
import {CircleCloseFilled, SuccessFilled, Search, Warning} from '@element-plus/icons-vue'
import PageSkeleton from '@/components/PageSkeleton.vue'
import { formatDateTime as formatDateTimeBase } from '@/utils/format'

const { t } = useI18n()

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

// 加载状态
const loading = ref(false)
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
      return t('jwtTokens.statusActive')
    case 'REVOKED':
      return t('jwtTokens.statusRevoked')
    case 'EXPIRED':
      return t('jwtTokens.statusExpired')
    default:
      return t('jwtTokens.unknown')
  }
}

// 格式化日期时间（委托共享 format.ts：zh 原样 YYYY-MM-DD HH:mm:ss，en 月份名）
const formatDateTime = (dateTime: string | number) => {
  if (!dateTime) return ''
  return formatDateTimeBase(dateTime)
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
    ElMessage.error(t('jwtTokens.messages.loadFailed', { error: error.message || t('jwtTokens.messages.unknownError') }))
  } finally {
    loading.value = false
  }
}

// 刷新令牌列表
const handleRefreshTokens = async () => {
  await loadTokens()
  ElMessage.success(t('jwtTokens.messages.refreshed'))
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
    ElMessage.error(t('jwtTokens.messages.detailsFetchFailed', { error: error.message || t('jwtTokens.messages.unknownError') }))
  }
}

// 撤销令牌
const handleRevoke = (token: JwtTokenInfo) => {
  ElMessageBox.confirm(t('jwtTokens.messages.revokeConfirmMessage', { userId: token.userId }), t('jwtTokens.confirmTitle'), {
    confirmButtonText: t('jwtTokens.confirm'),
    cancelButtonText: t('jwtTokens.cancel'),
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
        ElMessage.success(t('jwtTokens.messages.revokeSuccess'))
        // 刷新令牌列表和统计信息
        await loadTokens()
        
      } else {
        ElMessage.error(t('jwtTokens.messages.revokeFailed'))
      }
    } catch (error: any) {
      ElMessage.error(t('jwtTokens.messages.revokeFailedDetail', { error: error.message || t('jwtTokens.messages.unknownError') }))
    }
  }).catch(() => {
    // 用户取消操作
  })
}

// 添加到黑名单
const handleAddToBlacklist = async (token: JwtTokenInfo, type: string) => {
  let targetValue = ''
  let reason = ''
  
  switch (type) {
    case 'TOKEN':
      targetValue = token.tokenHash
      reason = '可疑令牌封禁'
      break
    case 'IP':
      targetValue = token.ipAddress || ''
      reason = '可疑IP封禁'
      break
    case 'DEVICE':
      targetValue = token.deviceInfo || ''
      reason = '可疑设备封禁'
      break
  }
  
  if (!targetValue) {
    ElMessage.warning(t('jwtTokens.messages.targetValueMissing'))
    return
  }
  
  try {
    const targetTypeText = type === 'TOKEN' ? t('jwtTokens.token') : type === 'IP' ? t('jwtTokens.ip') : t('jwtTokens.device')
    await ElMessageBox.confirm(
      t('jwtTokens.messages.blacklistConfirmMessage', { targetType: targetTypeText, target: `${targetValue.substring(0, 20)}...` }),
      t('jwtTokens.messages.blacklistConfirmTitle'),
      { type: 'warning' }
    )
    
    const result = await addToBlacklist({
      blacklistType: type as 'TOKEN' | 'IP' | 'DEVICE',
      targetValue,
      userId: token.userId,
      reason,
      riskLevel: 'HIGH'
    })
    
    if (result.success) {
      ElMessage.success(t('jwtTokens.messages.blacklistAddSuccess'))
    } else {
      ElMessage.error(result.message || t('jwtTokens.messages.blacklistAddFailed'))
    }
  } catch {
    // 用户取消
  }
}

// 从详情页面撤销令牌
const handleRevokeFromDetails = async () => {
  if (!selectedTokenDetails.value) return

  ElMessageBox.confirm(t('jwtTokens.messages.revokeConfirmMessage', { userId: selectedTokenDetails.value.userId }), t('jwtTokens.confirmTitle'), {
    confirmButtonText: t('jwtTokens.confirm'),
    cancelButtonText: t('jwtTokens.cancel'),
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
        ElMessage.success(t('jwtTokens.messages.revokeSuccess'))
        tokenDetailsDialogVisible.value = false
        selectedTokenDetails.value = null
        // 刷新令牌列表和统计信息
        await loadTokens()
        
      } else {
        ElMessage.error(t('jwtTokens.messages.revokeFailed'))
      }
    } catch (error: any) {
      ElMessage.error(t('jwtTokens.messages.revokeFailedDetail', { error: error.message || t('jwtTokens.messages.unknownError') }))
    }
  }).catch(() => {
    // 用户取消操作
  })
}

// 清理过期令牌
const handleCleanupExpiredTokens = async () => {
  ElMessageBox.confirm(t('jwtTokens.messages.cleanupConfirmMessage'), t('jwtTokens.confirmTitle'), {
    confirmButtonText: t('jwtTokens.confirm'),
    cancelButtonText: t('jwtTokens.cancel'),
    type: 'warning'
  }).then(async () => {
    try {
      loading.value = true
      const result: CleanupResult = await cleanupExpiredTokens()
      ElMessage.success(t('jwtTokens.messages.cleanupSuccess', { cleanedTokens: result.cleanedTokens, cleanedBlacklistEntries: result.cleanedBlacklistEntries }))
      // 刷新令牌列表和统计信息
      await loadTokens()
      
    } catch (error: any) {
      ElMessage.error(t('jwtTokens.messages.cleanupFailedDetail', { error: error.message || t('jwtTokens.messages.unknownError') }))
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
    ElMessage.warning(t('jwtTokens.messages.selectTokensFirst'))
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
      ElMessage.success(t('jwtTokens.messages.batchRevokeSuccess', { count: selectedTokens.value.length }))
      batchRevokeDialogVisible.value = false
      selectedTokens.value = []
      // 刷新令牌列表和统计信息
      await loadTokens()
      
    } else {
      ElMessage.error(t('jwtTokens.messages.batchRevokeFailed'))
    }
  } catch (error: any) {
    ElMessage.error(t('jwtTokens.messages.batchRevokeFailedDetail', { error: error.message || t('jwtTokens.messages.unknownError') }))
  } finally {
    batchRevokeLoading.value = false
  }
}

// 组件挂载时获取数据
onMounted(async () => {
  await loadTokens()
})
</script>

<style scoped>
.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}
</style>