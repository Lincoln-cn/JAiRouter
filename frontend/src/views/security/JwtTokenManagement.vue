<template>
  <PageSkeleton :title="t('jwtTokens.title')">
    <template #actions>
      <!-- 主操作只保留一个：批量撤销；刷新与清理过期移到工具栏 -->
      <el-button
        v-permission="'security:jwttokens:manage'"
        type="primary"
        :disabled="selectedTokens.length === 0"
        @click="handleOpenBatchRevoke"
      >
        {{ t('jwtTokens.batchRevokeCount', { count: selectedTokens.length }) }}
      </el-button>
    </template>

    <template #stats>
      <!-- 令牌指标：总数取自服务端分页；后三项是「当前页」构成，标签已写明本页，避免误读为全量 -->
      <MetricStrip :items="metricItems" :loading="loading" :columns="4" />
    </template>

    <template #toolbar>
      <!-- 工具栏顺序：筛选 → 刷新 → 次要动作 -->
      <el-input
        v-model="searchForm.userId"
        class="filter-input"
        :placeholder="t('jwtTokens.searchUserIdPlaceholder')"
        clearable
        @clear="handleSearch"
        @keyup.enter="handleSearch"
      >
        <template #prefix>
          <el-icon><Search /></el-icon>
        </template>
      </el-input>
      <el-select
        v-model="searchForm.status"
        class="filter-select"
        :placeholder="t('jwtTokens.statusFilter')"
        clearable
        @change="handleSearch"
      >
        <el-option :label="t('jwtTokens.all')" value="" />
        <el-option :label="t('jwtTokens.statusActive')" value="ACTIVE" />
        <el-option :label="t('jwtTokens.statusRevoked')" value="REVOKED" />
        <el-option :label="t('jwtTokens.statusExpired')" value="EXPIRED" />
      </el-select>
      <el-button type="primary" @click="handleSearch">{{ t('jwtTokens.search') }}</el-button>
      <el-button plain @click="handleResetSearch">{{ t('jwtTokens.reset') }}</el-button>
      <el-button :loading="loading" @click="handleRefreshTokens">
        <el-icon><Refresh /></el-icon>
        {{ t('jwtTokens.refreshList') }}
      </el-button>
      <!-- 清理过期属危险动作，统一走 ConfirmAction（不再用弹窗确认） -->
      <ConfirmAction
        v-permission="'security:jwttokens:manage'"
        :title="t('jwtTokens.messages.cleanupConfirmMessage')"
        :width="320"
        :confirm-text="t('jwtTokens.confirm')"
        :cancel-text="t('jwtTokens.cancel')"
        type="warning"
        :loading="loading"
        @confirm="handleCleanupExpiredTokens"
      >
        <el-button plain type="warning">
          <el-icon><Delete /></el-icon>
          {{ t('jwtTokens.cleanupExpired') }}
        </el-button>
      </ConfirmAction>
    </template>

    <el-table
      v-loading="loading"
      :data="tokenList.content"
      style="width: 100%"
      @selection-change="handleSelectionChange"
    >
      <!-- 空态区分「还没有数据」与「筛选无结果」；无数据时在空态里给下一步动作 -->
      <template #empty>
        <EmptyState
          icon="Key"
          :title="hasFilter ? t('jwtTokens.emptySearchTitle') : t('jwtTokens.emptyTitle')"
          :description="hasFilter ? t('jwtTokens.emptySearchDescription') : t('jwtTokens.emptyDescription')"
        >
          <template #actions>
            <el-button v-if="hasFilter" @click="handleResetSearch">{{ t('jwtTokens.reset') }}</el-button>
            <el-button v-else @click="handleRefreshTokens">{{ t('jwtTokens.refreshList') }}</el-button>
          </template>
        </EmptyState>
      </template>
      <el-table-column type="selection" width="55" />
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
          <el-tag :type="getStatusTagType(scope.row.status)" effect="plain">
            {{ getStatusText(scope.row.status) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column :label="t('jwtTokens.blacklist')" width="120" fixed="right">
        <template #default="scope">
          <!-- 加入黑名单：下拉选类型，确认动作落在抽屉里的 ConfirmAction 上 -->
          <el-dropdown v-permission="'security:blacklist:manage'" trigger="click" @command="(cmd: string) => handleAddToBlacklist(scope.row, cmd)">
            <el-button size="small" link type="danger">
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
            link
            @click="handleViewDetails(scope.row)"
          >
            {{ t('jwtTokens.details') }}
          </el-button>
          <!-- 撤销是危险动作：二次确认由行内 ConfirmAction 完成 -->
          <ConfirmAction
            v-permission="'security:jwttokens:manage'"
            :title="t('jwtTokens.messages.revokeConfirmMessage', { userId: scope.row.userId })"
            :width="280"
            :confirm-text="t('jwtTokens.revoke')"
            :cancel-text="t('jwtTokens.cancel')"
            type="danger"
            :disabled="scope.row.status !== 'ACTIVE'"
            @confirm="handleRevoke(scope.row)"
          >
            <el-button
              size="small"
              link
              type="danger"
              :disabled="scope.row.status !== 'ACTIVE'"
            >
              {{ t('jwtTokens.revoke') }}
            </el-button>
          </ConfirmAction>
        </template>
      </el-table-column>
    </el-table>

    <!-- 总数为 0 时不渲染无意义的分页条 -->
    <template #footer>
      <el-pagination
        v-if="tokenList.totalElements > 0"
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

  <!-- 令牌详情：只读明细走 DetailDrawer（520），不再用 el-dialog -->
  <DetailDrawer v-model="tokenDetailsVisible" :title="t('jwtTokens.detailTitle')" :width="520">
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
        <el-tag :type="getStatusTagType(selectedTokenDetails.status)" effect="plain">
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
      <span class="drawer-footer">
        <el-button @click="tokenDetailsVisible = false">{{ t('jwtTokens.close') }}</el-button>
        <ConfirmAction
          v-if="selectedTokenDetails?.status === 'ACTIVE'"
          :title="t('jwtTokens.messages.revokeConfirmMessage', { userId: selectedTokenDetails?.userId })"
          :width="280"
          :confirm-text="t('jwtTokens.revoke')"
          :cancel-text="t('jwtTokens.cancel')"
          type="danger"
          @confirm="handleRevokeFromDetails"
        >
          <el-button type="danger">{{ t('jwtTokens.revokeThisToken') }}</el-button>
        </ConfirmAction>
      </span>
    </template>
  </DetailDrawer>

  <!-- 批量撤销：带原因输入的表单，统一用右侧抽屉（不占用 el-dialog） -->
  <el-drawer
    v-model="batchRevokeVisible"
    :title="t('jwtTokens.batchRevokeTitle')"
    direction="rtl"
    :size="640"
    :close-on-click-modal="false"
    destroy-on-close
  >
    <el-alert
      :title="t('jwtTokens.batchRevokeCount', { count: selectedTokens.length })"
      type="warning"
      :closable="false"
      show-icon
      style="margin-bottom: var(--ja-space-4);"
    />
    <el-form :model="batchRevokeForm" label-width="100px">
      <el-form-item :label="t('jwtTokens.revokeReason')">
        <el-input
          v-model="batchRevokeForm.reason"
          :placeholder="t('jwtTokens.reasonPlaceholder')"
          type="textarea"
          :rows="3"
        />
      </el-form-item>
    </el-form>
    <template #footer>
      <span class="drawer-footer">
        <el-button @click="batchRevokeVisible = false">{{ t('jwtTokens.cancel') }}</el-button>
        <!-- 批量撤销是不可恢复的危险动作：提交前再走一次 ConfirmAction -->
        <ConfirmAction
          :title="t('jwtTokens.messages.batchRevokeConfirmMessage', { count: selectedTokens.length })"
          :width="300"
          :confirm-text="t('jwtTokens.revoke')"
          :cancel-text="t('jwtTokens.cancel')"
          type="danger"
          :loading="batchRevokeLoading"
          @confirm="handleBatchRevoke"
        >
          <el-button type="primary" :loading="batchRevokeLoading">{{ t('jwtTokens.revoke') }}</el-button>
        </ConfirmAction>
      </span>
    </template>
  </el-drawer>

  <!-- 加入黑名单：先看目标与原因，再由 ConfirmAction 提交（替代弹窗确认） -->
  <el-drawer
    v-model="blacklistVisible"
    :title="t('jwtTokens.messages.blacklistConfirmTitle')"
    direction="rtl"
    :size="640"
    :close-on-click-modal="false"
    @closed="resetBlacklistTarget"
  >
    <el-alert
      v-if="blacklistTarget"
      :title="t('jwtTokens.messages.blacklistConfirmMessage', {
        targetType: blacklistTypeText,
        target: blacklistTarget.value
      })"
      type="warning"
      :closable="false"
      show-icon
      style="margin-bottom: var(--ja-space-4);"
    />
    <el-descriptions v-if="blacklistTarget" :column="1" border>
      <el-descriptions-item :label="t('jwtTokens.blacklistType')">{{ blacklistTypeText }}</el-descriptions-item>
      <el-descriptions-item :label="t('jwtTokens.blacklistValue')">
        <code class="mono-value">{{ blacklistTarget.value }}</code>
      </el-descriptions-item>
      <el-descriptions-item :label="t('jwtTokens.blacklistReason')">{{ blacklistTarget.reason }}</el-descriptions-item>
    </el-descriptions>
    <template #footer>
      <span class="drawer-footer">
        <el-button @click="blacklistVisible = false">{{ t('jwtTokens.cancel') }}</el-button>
        <ConfirmAction
          :title="t('jwtTokens.messages.blacklistConfirmTitle')"
          :width="260"
          :confirm-text="t('jwtTokens.confirm')"
          :cancel-text="t('jwtTokens.cancel')"
          type="danger"
          @confirm="handleConfirmAddToBlacklist"
        >
          <el-button type="danger">{{ t('jwtTokens.confirm') }}</el-button>
        </ConfirmAction>
      </span>
    </template>
  </el-drawer>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getErrorMessage } from '@/utils/error'
import { useI18n } from 'vue-i18n'
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
import { Delete, Refresh, Search, Warning } from '@element-plus/icons-vue'
import PageSkeleton from '@/components/PageSkeleton.vue'
import MetricStrip from '@/components/MetricStrip.vue'
import type { MetricStripItem } from '@/components/MetricStrip.vue'
import EmptyState from '@/components/EmptyState.vue'
import ConfirmAction from '@/components/ConfirmAction.vue'
import DetailDrawer from '@/components/DetailDrawer.vue'
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
const tokenDetailsVisible = ref(false)

// 加载状态
const loading = ref(false)
const batchRevokeLoading = ref(false)

// 批量撤销相关
const batchRevokeVisible = ref(false)
const selectedTokens = ref<JwtTokenInfo[]>([])
const batchRevokeForm = ref({
  reason: ''
})

// 加入黑名单的待确认目标（确认动作在抽屉里完成）
const blacklistVisible = ref(false)
const blacklistTarget = ref<{
  type: 'TOKEN' | 'IP' | 'DEVICE'
  value: string
  userId: string
  reason: string
} | null>(null)

// 是否处于筛选状态：空态文案据此在「还没有数据」与「筛选无结果」之间切换
const hasFilter = computed(() => Boolean(searchForm.userId || searchForm.status))

/**
 * 顶部指标条。
 *
 * 「令牌总数」是服务端分页返回的真实总量；其余三项只能统计当前页，
 * 因此文案统一带「本页」，不冒充全量分布。
 */
const metricItems = computed<MetricStripItem[]>(() => {
  const pageRows = tokenList.value.content || []
  const active = pageRows.filter((row) => row.status === 'ACTIVE').length
  const revoked = pageRows.filter((row) => row.status === 'REVOKED').length
  const expired = pageRows.filter((row) => row.status === 'EXPIRED').length
  return [
    { key: 'total', label: t('jwtTokens.metricTotal'), value: tokenList.value.totalElements },
    { key: 'active', label: t('jwtTokens.metricActivePage'), value: active },
    {
      key: 'revoked',
      label: t('jwtTokens.metricRevokedPage'),
      value: revoked,
      // 有吊销记录属于需要被看见的状态，其余保持中性
      tone: revoked > 0 ? 'warning' : 'default'
    },
    { key: 'expired', label: t('jwtTokens.metricExpiredPage'), value: expired }
  ]
})

// 黑名单目标类型文案
const blacklistTypeText = computed(() => {
  if (!blacklistTarget.value) return ''
  switch (blacklistTarget.value.type) {
    case 'TOKEN':
      return t('jwtTokens.token')
    case 'IP':
      return t('jwtTokens.ip')
    default:
      return t('jwtTokens.device')
  }
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
  } catch (error) {
    ElMessage.error(t('jwtTokens.messages.loadFailed', { error: getErrorMessage(error) || t('jwtTokens.messages.unknownError') }))
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
    tokenDetailsVisible.value = true
  } catch (error) {
    ElMessage.error(t('jwtTokens.messages.detailsFetchFailed', { error: getErrorMessage(error) || t('jwtTokens.messages.unknownError') }))
  }
}

// 撤销令牌（二次确认由调用方的 ConfirmAction 完成）
const handleRevoke = async (token: JwtTokenInfo) => {
  try {
    const revokeRequest: TokenRevokeRequest = {
      token: token.tokenHash, // 使用tokenHash而不是完整token
      userId: token.userId,
      reason: '管理员手动撤销'
    }

    const result = await revokeToken(revokeRequest)
    if (result) {
      ElMessage.success(t('jwtTokens.messages.revokeSuccess'))
      await loadTokens()
    } else {
      ElMessage.error(t('jwtTokens.messages.revokeFailed'))
    }
  } catch (error) {
    ElMessage.error(t('jwtTokens.messages.revokeFailedDetail', { error: getErrorMessage(error) || t('jwtTokens.messages.unknownError') }))
  }
}

// 加入黑名单：先算出拦截目标与原因，弹出确认抽屉（真正的提交在 handleConfirmAddToBlacklist）
const handleAddToBlacklist = (token: JwtTokenInfo, type: string) => {
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

  blacklistTarget.value = {
    type: type as 'TOKEN' | 'IP' | 'DEVICE',
    value: targetValue,
    userId: token.userId,
    reason
  }
  blacklistVisible.value = true
}

// 抽屉关闭后清空待确认目标，下次打开不会残留上一次的值
const resetBlacklistTarget = () => {
  blacklistTarget.value = null
}

// 确认加入黑名单（由抽屉里的 ConfirmAction 触发）
const handleConfirmAddToBlacklist = async () => {
  if (!blacklistTarget.value) return

  try {
    const result = await addToBlacklist({
      blacklistType: blacklistTarget.value.type,
      targetValue: blacklistTarget.value.value,
      userId: blacklistTarget.value.userId,
      reason: blacklistTarget.value.reason,
      riskLevel: 'HIGH'
    })

    if (result.success) {
      ElMessage.success(t('jwtTokens.messages.blacklistAddSuccess'))
      blacklistVisible.value = false
      blacklistTarget.value = null
    } else {
      ElMessage.error(result.message || t('jwtTokens.messages.blacklistAddFailed'))
    }
  } catch (error) {
    ElMessage.error(getErrorMessage(error) || t('jwtTokens.messages.blacklistAddFailed'))
  }
}

// 从详情抽屉撤销令牌
const handleRevokeFromDetails = async () => {
  if (!selectedTokenDetails.value) return

  try {
    const revokeRequest: TokenRevokeRequest = {
      token: selectedTokenDetails.value.tokenHash,
      userId: selectedTokenDetails.value.userId,
      reason: '管理员手动撤销'
    }

    const result = await revokeToken(revokeRequest)
    if (result) {
      ElMessage.success(t('jwtTokens.messages.revokeSuccess'))
      tokenDetailsVisible.value = false
      selectedTokenDetails.value = null
      await loadTokens()
    } else {
      ElMessage.error(t('jwtTokens.messages.revokeFailed'))
    }
  } catch (error) {
    ElMessage.error(t('jwtTokens.messages.revokeFailedDetail', { error: getErrorMessage(error) || t('jwtTokens.messages.unknownError') }))
  }
}

// 清理过期令牌（二次确认由工具栏的 ConfirmAction 完成）
const handleCleanupExpiredTokens = async () => {
  loading.value = true
  try {
    const result: CleanupResult = await cleanupExpiredTokens()
    ElMessage.success(t('jwtTokens.messages.cleanupSuccess', { cleanedTokens: result.cleanedTokens, cleanedBlacklistEntries: result.cleanedBlacklistEntries }))
    await loadTokens()
  } catch (error) {
    ElMessage.error(t('jwtTokens.messages.cleanupFailedDetail', { error: getErrorMessage(error) || t('jwtTokens.messages.unknownError') }))
  } finally {
    loading.value = false
  }
}

// 打开批量撤销抽屉
const handleOpenBatchRevoke = () => {
  batchRevokeForm.value = {
    reason: ''
  }
  batchRevokeVisible.value = true
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
      batchRevokeVisible.value = false
      selectedTokens.value = []
      await loadTokens()
    } else {
      ElMessage.error(t('jwtTokens.messages.batchRevokeFailed'))
    }
  } catch (error) {
    ElMessage.error(t('jwtTokens.messages.batchRevokeFailedDetail', { error: getErrorMessage(error) || t('jwtTokens.messages.unknownError') }))
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
/* 抽屉底部动作条：右对齐、间距统一 */
.drawer-footer {
  display: flex;
  justify-content: flex-end;
  align-items: center;
  gap: var(--ja-space-2, 8px);
}

/* 工具条：输入框/下拉给定宽度，避免 el-input 默认 width:100% 各占一整行 */
.filter-input {
  width: 240px;
}

.filter-select {
  width: 160px;
}

/* 黑名单目标值：长令牌需要可选中、可换行 */
.mono-value {
  font-family: var(--el-font-family-mono, monospace);
  font-size: 12px;
  word-break: break-all;
}

@media (max-width: 767px) {
  .filter-input,
  .filter-select {
    width: 100%;
  }
}
</style>
