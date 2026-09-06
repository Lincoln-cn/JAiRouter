<template>
  <PageSkeleton :title="t('blacklist.pageTitle')">
    <template #actions>
      <el-button type="primary" @click="handleOpenAddDialog">
        <el-icon><Plus /></el-icon>{{ t('blacklist.add') }}
      </el-button>
      <el-button :loading="loading" @click="handleRefresh">
        <el-icon><Refresh /></el-icon>{{ t('blacklist.refresh') }}
      </el-button>
      <el-button type="warning" @click="handleCleanup">
        <el-icon><Delete /></el-icon>{{ t('blacklist.cleanupExpired') }}
      </el-button>
    </template>

    <template #stats>
      <!-- 统计卡片 -->
      <div class="stats-section">
        <el-row :gutter="20">
          <el-col :span="6">
            <el-statistic :title="t('blacklist.totalActiveTitle')" :value="stats.totalActive">
              <template #suffix>
                <el-tag type="success" size="small">{{ t('blacklist.entryUnit') }}</el-tag>
              </template>
            </el-statistic>
          </el-col>
          <el-col :span="6">
            <el-statistic :title="t('blacklist.tokenBlacklistTitle')" :value="stats.tokenCount">
              <template #suffix>
                <el-tag type="warning" size="small">{{ t('blacklist.tokenUnit') }}</el-tag>
              </template>
            </el-statistic>
          </el-col>
          <el-col :span="6">
            <el-statistic :title="t('blacklist.ipBlacklistTitle')" :value="stats.ipCount">
              <template #suffix>
                <el-tag type="danger" size="small">{{ t('blacklist.addressUnit') }}</el-tag>
              </template>
            </el-statistic>
          </el-col>
          <el-col :span="6">
            <el-statistic :title="t('blacklist.deviceBlacklistTitle')" :value="stats.deviceCount">
              <template #suffix>
                <el-tag type="info" size="small">{{ t('blacklist.deviceUnit') }}</el-tag>
              </template>
            </el-statistic>
          </el-col>
        </el-row>
      </div>
    </template>

    <template #toolbar>
      <!-- 搜索和过滤 -->
      <el-select v-model="filterForm.type" :placeholder="t('blacklist.typeFilterPlaceholder')" clearable @change="handleSearch">
        <el-option :label="t('blacklist.allTypes')" value="" />
        <el-option :label="t('blacklist.typeToken')" value="TOKEN" />
        <el-option :label="t('blacklist.typeIp')" value="IP" />
        <el-option :label="t('blacklist.typeDevice')" value="DEVICE" />
      </el-select>
      <el-select v-model="filterForm.status" :placeholder="t('blacklist.statusFilterPlaceholder')" clearable @change="handleSearch">
        <el-option :label="t('blacklist.allStatuses')" value="" />
        <el-option :label="t('blacklist.statusActive')" value="ACTIVE" />
        <el-option :label="t('blacklist.expired')" value="EXPIRED" />
        <el-option :label="t('blacklist.statusRemoved')" value="REMOVED" />
      </el-select>
      <el-input v-model="filterForm.userId" :placeholder="t('blacklist.userIdPlaceholder')" clearable @keyup.enter="handleSearch" style="width: 200px" />
      <el-button type="primary" @click="handleSearch">{{ t('blacklist.search') }}</el-button>
      <el-button @click="handleResetFilter">{{ t('blacklist.reset') }}</el-button>
    </template>

    <!-- 黑名单列表 -->
    <el-table v-loading="loading" :data="pageData.content" style="width: 100%">
      <el-table-column prop="id" label="ID" width="80" />
      <el-table-column prop="blacklistType" :label="t('blacklist.type')" width="100">
        <template #default="scope">
          <el-tag :type="getTypeTagType(scope.row.blacklistType)">
            {{ scope.row.blacklistType }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="targetValueMasked" :label="t('blacklist.target')" show-overflow-tooltip />
      <el-table-column prop="userId" :label="t('blacklist.relatedUser')" width="120" show-overflow-tooltip />
      <el-table-column prop="reason" :label="t('blacklist.reason')" width="150" show-overflow-tooltip />
      <el-table-column prop="riskLevel" :label="t('blacklist.riskLevel')" width="100">
        <template #default="scope">
          <el-tag :type="getRiskTagType(scope.row.riskLevel)" size="small">
            {{ scope.row.riskLevel }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="addedBy" :label="t('blacklist.addedBy')" width="100" />
      <el-table-column prop="addedAt" :label="t('blacklist.addedAt')" width="160">
        <template #default="scope">
          {{ formatDateTime(scope.row.addedAt) }}
        </template>
      </el-table-column>
      <el-table-column prop="expiresAt" :label="t('blacklist.expiresAt')" width="160">
        <template #default="scope">
          <span v-if="scope.row.permanent">{{ t('blacklist.permanent') }}</span>
          <span v-else>{{ formatDateTime(scope.row.expiresAt) }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="status" :label="t('blacklist.status')" width="80">
        <template #default="scope">
          <el-tag :type="getStatusTagType(scope.row.status)" size="small">
            {{ scope.row.status }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column :label="t('blacklist.actions')" width="120" fixed="right">
        <template #default="scope">
          <el-button v-if="scope.row.status === 'ACTIVE'" type="danger" size="small" @click="handleRemove(scope.row)">
            {{ t('blacklist.remove') }}
          </el-button>
          <el-button type="primary" size="small" link @click="handleViewDetail(scope.row)">
            {{ t('blacklist.detail') }}
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <template #footer>
      <el-pagination
        v-model:current-page="filterForm.page"
        v-model:page-size="filterForm.size"
        :page-sizes="[10, 20, 50, 100]"
        :total="pageData.totalElements"
        layout="total, sizes, prev, pager, next, jumper"
        @size-change="handleSearch"
        @current-change="handleSearch"
      />
    </template>
  </PageSkeleton>

    <!-- 添加黑名单对话框 -->
    <el-dialog v-model="addDialogVisible" :title="t('blacklist.add')" width="600px">
      <el-form ref="addFormRef" :model="addForm" :rules="addFormRules" label-width="100px">
        <el-form-item :label="t('blacklist.type')" prop="blacklistType">
          <el-radio-group v-model="addForm.blacklistType" @change="handleTypeChange">
            <el-radio-button value="TOKEN">{{ t('blacklist.radioToken') }}</el-radio-button>
            <el-radio-button value="IP">{{ t('blacklist.radioIp') }}</el-radio-button>
            <el-radio-button value="DEVICE">{{ t('blacklist.radioDevice') }}</el-radio-button>
          </el-radio-group>
        </el-form-item>
        
        <!-- Token选择器 -->
        <el-form-item v-if="addForm.blacklistType === 'TOKEN'" :label="t('blacklist.selectToken')" prop="targetValue">
          <div class="selector-container">
            <el-select
              v-model="addForm.targetValue"
              filterable
              remote
              reserve-keyword
              :placeholder="t('blacklist.tokenSelectPlaceholder')"
              :remote-method="searchTokens"
              :loading="tokenLoading"
              style="width: 100%"
            >
              <el-option
                v-for="token in tokenOptions"
                :key="token.id"
                :label="t('blacklist.tokenOptionLabel', { userId: token.userId, status: token.status })"
                :value="token.tokenHash"
              >
                <div class="token-option">
                  <span class="token-user">{{ token.userId }}</span>
                  <el-tag :type="getTokenStatusTagType(token.status)" size="small">{{ token.status }}</el-tag>
                  <span class="token-time">{{ formatDateTime(token.issuedAt) }}</span>
                </div>
              </el-option>
            </el-select>
            <el-button type="primary" link @click="loadActiveTokens">{{ t('blacklist.loadActiveTokens') }}</el-button>
          </div>
        </el-form-item>
        
        <!-- IP选择器 -->
        <el-form-item v-if="addForm.blacklistType === 'IP'" :label="t('blacklist.selectIp')" prop="targetValue">
          <div class="selector-container">
            <el-select
              v-model="addForm.targetValue"
              filterable
              allow-create
              default-first-option
              :placeholder="t('blacklist.ipSelectPlaceholder')"
              style="width: 100%"
            >
              <el-option
                v-for="ip in ipOptions"
                :key="ip.ip"
                :label="ip.ip"
                :value="ip.ip"
              >
                <div class="ip-option">
                  <span class="ip-address">{{ ip.ip }}</span>
                  <el-tag v-if="ip.suspicious" type="danger" size="small">{{ t('blacklist.suspicious') }}</el-tag>
                  <span class="ip-count">{{ t('blacklist.ipLoginCount', { count: ip.loginCount }) }}</span>
                </div>
              </el-option>
            </el-select>
            <el-button type="primary" link @click="loadSuspiciousIPs">{{ t('blacklist.loadSuspiciousIps') }}</el-button>
          </div>
          <div class="ip-input-hint">
            <el-text size="small" type="info">{{ t('blacklist.ipInputHint') }}</el-text>
          </div>
        </el-form-item>
        
        <!-- 设备标识输入 -->
        <el-form-item v-if="addForm.blacklistType === 'DEVICE'" :label="t('blacklist.device')" prop="targetValue">
          <el-input v-model="addForm.targetValue" :placeholder="t('blacklist.devicePlaceholder')">
            <template #append>
              <el-button @click="showDeviceSelector = true">{{ t('blacklist.pickFromToken') }}</el-button>
            </template>
          </el-input>
        </el-form-item>

        <!-- 用户选择器 -->
        <el-form-item :label="t('blacklist.relatedUser')">
          <el-select
            v-model="addForm.userId"
            filterable
            clearable
            :placeholder="t('blacklist.userPlaceholder')"
            style="width: 100%"
          >
            <el-option
              v-for="user in userOptions"
              :key="user.username"
              :label="user.username"
              :value="user.username"
            >
              <div class="user-option">
                <span>{{ user.username }}</span>
                <el-tag v-if="user.enabled" type="success" size="small">{{ t('blacklist.enabled') }}</el-tag>
                <el-tag v-else type="danger" size="small">{{ t('blacklist.disabled') }}</el-tag>
              </div>
            </el-option>
          </el-select>
        </el-form-item>
        
        <!-- 快速原因选择 -->
        <el-form-item :label="t('blacklist.addReason')">
          <div class="reason-selector">
            <el-select
              v-model="addForm.reason"
              filterable
              allow-create
              default-first-option
              :placeholder="t('blacklist.reasonPlaceholder')"
              style="width: 100%"
            >
              <el-option :label="t('blacklist.reasonMaliciousLogin')" value="恶意登录尝试" />
              <el-option :label="t('blacklist.reasonAbnormalAccess')" value="异常访问行为" />
              <el-option :label="t('blacklist.reasonCompromisedAccount')" value="账户被盗" />
              <el-option :label="t('blacklist.reasonViolation')" value="违规操作" />
              <el-option :label="t('blacklist.reasonSecurityRisk')" value="安全风险" />
              <el-option :label="t('blacklist.reasonUserRequest')" value="用户请求封禁" />
              <el-option :label="t('blacklist.reasonOther')" value="其他" />
            </el-select>
          </div>
        </el-form-item>
        
        <el-form-item :label="t('blacklist.riskLevel')">
          <el-radio-group v-model="addForm.riskLevel">
            <el-radio-button value="LOW">{{ t('blacklist.riskLow') }}</el-radio-button>
            <el-radio-button value="MEDIUM">{{ t('blacklist.riskMedium') }}</el-radio-button>
            <el-radio-button value="HIGH">{{ t('blacklist.riskHigh') }}</el-radio-button>
            <el-radio-button value="CRITICAL">{{ t('blacklist.riskCritical') }}</el-radio-button>
          </el-radio-group>
        </el-form-item>
        
        <el-form-item :label="t('blacklist.validity')">
          <el-radio-group v-model="addForm.expiryType">
            <el-radio value="permanent">{{ t('blacklist.permanent') }}</el-radio>
            <el-radio value="temporary">{{ t('blacklist.temporary') }}</el-radio>
          </el-radio-group>
          <el-input-number v-if="addForm.expiryType === 'temporary'" v-model="addForm.expiresInDays" :min="1" :max="365" style="margin-left: 10px" />
          <span v-if="addForm.expiryType === 'temporary'" style="margin-left: 5px">{{ t('blacklist.dayUnit') }}</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="addDialogVisible = false">{{ t('blacklist.cancel') }}</el-button>
        <el-button type="primary" :loading="addLoading" @click="handleAdd">{{ t('blacklist.confirmAdd') }}</el-button>
      </template>
    </el-dialog>

    <!-- 设备选择对话框 -->
    <el-dialog v-model="showDeviceSelector" :title="t('blacklist.deviceDialogTitle')" width="500px">
      <el-table :data="tokenOptions" @row-click="selectDeviceFromToken">
        <el-table-column prop="userId" :label="t('blacklist.user')" width="120" />
        <el-table-column prop="deviceInfo" :label="t('blacklist.deviceInfo')" show-overflow-tooltip />
        <el-table-column prop="ipAddress" label="IP" width="130" />
      </el-table>
    </el-dialog>

    <!-- 详情对话框 -->
    <el-dialog v-model="detailDialogVisible" :title="t('blacklist.detailTitle')" width="500px">
      <el-descriptions :column="1" border>
        <el-descriptions-item label="ID">{{ detailData?.id }}</el-descriptions-item>
        <el-descriptions-item :label="t('blacklist.type')">
          <el-tag :type="getTypeTagType(detailData?.blacklistType)">{{ detailData?.blacklistType }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item :label="t('blacklist.target')">{{ detailData?.targetValue }}</el-descriptions-item>
        <el-descriptions-item :label="t('blacklist.maskedTarget')">{{ detailData?.targetValueMasked }}</el-descriptions-item>
        <el-descriptions-item :label="t('blacklist.relatedUser')">{{ detailData?.userId || '-' }}</el-descriptions-item>
        <el-descriptions-item :label="t('blacklist.addReason')">{{ detailData?.reason || '-' }}</el-descriptions-item>
        <el-descriptions-item :label="t('blacklist.riskLevel')">
          <el-tag :type="getRiskTagType(detailData?.riskLevel)">{{ detailData?.riskLevel }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item :label="t('blacklist.addedBy')">{{ detailData?.addedBy }}</el-descriptions-item>
        <el-descriptions-item :label="t('blacklist.addedAt')">{{ formatDateTime(detailData?.addedAt) }}</el-descriptions-item>
        <el-descriptions-item :label="t('blacklist.expiresAt')">
          {{ detailData?.permanent ? t('blacklist.permanent') : formatDateTime(detailData?.expiresAt) }}
        </el-descriptions-item>
        <el-descriptions-item :label="t('blacklist.remainingTime')">
          {{ detailData?.permanent ? t('blacklist.permanentValid') : formatRemainingTime(detailData?.remainingSeconds) }}
        </el-descriptions-item>
        <el-descriptions-item :label="t('blacklist.status')">
          <el-tag :type="getStatusTagType(detailData?.status)">{{ detailData?.status }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item :label="t('blacklist.source')">{{ detailData?.source }}</el-descriptions-item>
      </el-descriptions>
    </el-dialog>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Refresh, Delete } from '@element-plus/icons-vue'
import {
  getBlacklistPage,
  getBlacklistStats,
  addToBlacklist,
  removeFromBlacklist,
  cleanupExpiredBlacklist,
  type BlacklistEntry,
  type BlacklistStats,
  type AddBlacklistRequest,
  type PagedResult,
  type BlacklistType,
  type BlacklistStatus,
  type RiskLevel
} from '@/api/blacklist'
import { getTokens, type JwtTokenInfo } from '@/api/jwtToken'
import { getJwtAccounts, type JwtAccount } from '@/api/account'
import PageSkeleton from '@/components/PageSkeleton.vue'
import { formatDateTime as formatDateTimeBase } from '@/utils/format'

const { t } = useI18n()

// 状态
const loading = ref(false)
const addLoading = ref(false)
const addDialogVisible = ref(false)
const detailDialogVisible = ref(false)
const detailData = ref<BlacklistEntry | null>(null)
const showDeviceSelector = ref(false)

// 选择器数据
const tokenLoading = ref(false)
const tokenOptions = ref<JwtTokenInfo[]>([])
const ipOptions = ref<{ ip: string; loginCount: number; suspicious: boolean }[]>([])
const userOptions = ref<JwtAccount[]>([])

// 统计数据
const stats = reactive<BlacklistStats>({
  totalActive: 0,
  typeCounts: { TOKEN: 0, IP: 0, DEVICE: 0 },
  tokenCount: 0,
  ipCount: 0,
  deviceCount: 0
})

// 分页数据
const pageData = reactive<PagedResult<BlacklistEntry>>({
  content: [],
  totalElements: 0,
  totalPages: 0,
  size: 20,
  page: 0,
  first: true,
  last: true
})

// 过滤表单
const filterForm = reactive({
  type: '' as BlacklistType | '',
  status: '' as BlacklistStatus | '',
  userId: '',
  page: 0,
  size: 20
})

// 添加表单
const addFormRef = ref()
const addForm = reactive<AddBlacklistRequest & { expiryType: string; expiresInDays: number }>({
  blacklistType: 'IP',
  targetValue: '',
  userId: '',
  reason: '',
  riskLevel: 'MEDIUM',
  expiresInSeconds: undefined,
  expiryType: 'permanent',
  expiresInDays: 30
})

const addFormRules = computed(() => ({
  blacklistType: [{ required: true, message: t('blacklist.validationType'), trigger: 'change' }],
  targetValue: [{ required: true, message: t('blacklist.validationTarget'), trigger: 'blur' }]
}))

// 初始化
onMounted(() => {
  loadStats()
  loadPage()
  loadUsers()
})

// 类型切换时清空目标值
function handleTypeChange() {
  addForm.targetValue = ''
  if (addForm.blacklistType === 'TOKEN') {
    loadActiveTokens()
  } else if (addForm.blacklistType === 'IP') {
    loadSuspiciousIPs()
  }
}

// 加载活跃令牌
async function loadActiveTokens() {
  tokenLoading.value = true
  try {
    const result = await getTokens(0, 50, undefined, 'ACTIVE')
    tokenOptions.value = result.content || []
  } catch (e) {
    console.error('加载令牌失败', e)
    ElMessage.warning(t('blacklist.loadTokensFailed'))
  } finally {
    tokenLoading.value = false
  }
}

// 搜索令牌
async function searchTokens(query: string) {
  if (!query) {
    loadActiveTokens()
    return
  }
  tokenLoading.value = true
  try {
    const result = await getTokens(0, 20, query)
    tokenOptions.value = result.content || []
  } catch (e) {
    console.error('搜索令牌失败', e)
  } finally {
    tokenLoading.value = false
  }
}

// 从活跃令牌中提取IP列表
async function loadSuspiciousIPs() {
  try {
    const result = await getTokens(0, 50, undefined, 'ACTIVE')
    const ipMap = new Map<string, number>()
    result.content?.forEach(t => {
      if (t.ipAddress) {
        ipMap.set(t.ipAddress, (ipMap.get(t.ipAddress) || 0) + 1)
      }
    })
    ipOptions.value = Array.from(ipMap.entries()).map(([ip, count]) => ({
      ip,
      loginCount: count,
      suspicious: false
    }))
  } catch (e) {
    console.error('加载IP列表失败', e)
  }
}

// 加载用户列表
async function loadUsers() {
  try {
    const accounts = await getJwtAccounts()
    userOptions.value = accounts || []
  } catch (e) {
    console.error('加载用户列表失败', e)
  }
}

// 从令牌选择设备
function selectDeviceFromToken(row: JwtTokenInfo) {
  if (row.deviceInfo) {
    addForm.targetValue = row.deviceInfo
    addForm.userId = row.userId
  }
  showDeviceSelector.value = false
}

// 令牌状态标签类型
function getTokenStatusTagType(status: string) {
  switch (status) {
    case 'ACTIVE': return 'success'
    case 'REVOKED': return 'danger'
    case 'EXPIRED': return 'info'
    default: return ''
  }
}

// 加载统计
async function loadStats() {
  try {
    const res = await getBlacklistStats()
    if (res.success && res.data) {
      Object.assign(stats, res.data)
    }
  } catch (e) {
    console.error('加载统计失败', e)
  }
}

// 加载分页数据
async function loadPage() {
  loading.value = true
  try {
    const params: any = { page: filterForm.page, size: filterForm.size }
    if (filterForm.type) params.type = filterForm.type
    if (filterForm.status) params.status = filterForm.status

    const res = await getBlacklistPage(params)
    if (res.success && res.data) {
      Object.assign(pageData, res.data)
    }
  } catch (e) {
    console.error('加载列表失败', e)
  } finally {
    loading.value = false
  }
}

// 刷新
async function handleRefresh() {
  await Promise.all([loadStats(), loadPage()])
  ElMessage.success(t('blacklist.refreshSuccess'))
}

// 搜索
function handleSearch() {
  loadPage()
}

// 重置过滤
function handleResetFilter() {
  filterForm.type = ''
  filterForm.status = ''
  filterForm.userId = ''
  filterForm.page = 0
  loadPage()
}

// 打开添加对话框
function handleOpenAddDialog() {
  addForm.blacklistType = 'IP'
  addForm.targetValue = ''
  addForm.userId = ''
  addForm.reason = ''
  addForm.riskLevel = 'MEDIUM'
  addForm.expiryType = 'permanent'
  addForm.expiresInDays = 30
  addDialogVisible.value = true
  // 默认加载IP列表
  loadSuspiciousIPs()
}

// 添加黑名单
async function handleAdd() {
  try {
    await addFormRef.value.validate()
  } catch {
    return
  }

  addLoading.value = true
  try {
    const request: AddBlacklistRequest = {
      blacklistType: addForm.blacklistType,
      targetValue: addForm.targetValue,
      userId: addForm.userId || undefined,
      reason: addForm.reason || undefined,
      riskLevel: addForm.riskLevel,
      expiresInSeconds: addForm.expiryType === 'temporary' ? addForm.expiresInDays * 24 * 3600 : undefined
    }

    const res = await addToBlacklist(request)
    if (res.success) {
      ElMessage.success(t('blacklist.addSuccess'))
      addDialogVisible.value = false
      await handleRefresh()
    } else {
      ElMessage.error(res.message || t('blacklist.addFailed'))
    }
  } catch (e) {
    ElMessage.error(t('blacklist.addFailed'))
  } finally {
    addLoading.value = false
  }
}

// 移除黑名单
async function handleRemove(row: BlacklistEntry) {
  try {
    await ElMessageBox.confirm(t('blacklist.removeConfirm'), t('blacklist.removeConfirmTitle'), { type: 'warning' })
    const res = await removeFromBlacklist(row.id)
    if (res.success) {
      ElMessage.success(t('blacklist.removeSuccess'))
      await handleRefresh()
    } else {
      ElMessage.error(res.message || t('blacklist.removeFailed'))
    }
  } catch {}
}

// 查看详情
function handleViewDetail(row: BlacklistEntry) {
  detailData.value = row
  detailDialogVisible.value = true
}

// 清理过期
async function handleCleanup() {
  try {
    await ElMessageBox.confirm(t('blacklist.cleanupConfirm'), t('blacklist.cleanupConfirmTitle'), { type: 'warning' })
    const res = await cleanupExpiredBlacklist()
    if (res.success) {
      ElMessage.success(t('blacklist.cleanupDone', { count: res.data }))
      await handleRefresh()
    } else {
      ElMessage.error(res.message || t('blacklist.cleanupFailed'))
    }
  } catch {}
}

// 格式化时间（委托共享 format.ts）
function formatDateTime(dt: string | undefined) {
  if (!dt) return '-'
  return formatDateTimeBase(dt)
}

// 格式化剩余时间
function formatRemainingTime(seconds: number | undefined) {
  if (!seconds) return '-'
  if (seconds <= 0) return t('blacklist.expired')
  const days = Math.floor(seconds / 86400)
  const hours = Math.floor((seconds % 86400) / 3600)
  if (days > 0) return t('blacklist.remainingDaysHours', { days, hours })
  return t('blacklist.remainingHours', { hours })
}

// 类型标签颜色
function getTypeTagType(type: BlacklistType | undefined) {
  switch (type) {
    case 'TOKEN': return 'warning'
    case 'IP': return 'danger'
    case 'DEVICE': return 'info'
    default: return ''
  }
}

// 状态标签颜色
function getStatusTagType(status: BlacklistStatus | undefined) {
  switch (status) {
    case 'ACTIVE': return 'success'
    case 'EXPIRED': return 'info'
    case 'REMOVED': return 'danger'
    default: return ''
  }
}

// 风险等级标签颜色
function getRiskTagType(level: RiskLevel | undefined) {
  switch (level) {
    case 'LOW': return 'success'
    case 'MEDIUM': return 'warning'
    case 'HIGH': return 'danger'
    case 'CRITICAL': return 'danger'
    default: return ''
  }
}
</script>

<style scoped>
.stats-section {
  margin-bottom: 16px;
  padding: 20px;
  background: var(--el-fill-color-light);
  border-radius: 4px;
}

/* 选择器容器 */
.selector-container {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.selector-container .el-button {
  align-self: flex-start;
}

/* 令牌选项样式 */
.token-option {
  display: flex;
  align-items: center;
  gap: 10px;
}

.token-user {
  font-weight: 500;
  min-width: 80px;
}

.token-time {
  color: var(--ja-text-secondary);
  font-size: 12px;
  margin-left: auto;
}

/* IP选项样式 */
.ip-option {
  display: flex;
  align-items: center;
  gap: 10px;
}

.ip-address {
  font-family: monospace;
  font-weight: 500;
}

.ip-count {
  color: var(--ja-text-secondary);
  font-size: 12px;
  margin-left: auto;
}

/* 用户选项样式 */
.user-option {
  display: flex;
  align-items: center;
  gap: 10px;
}

/* IP输入提示 */
.ip-input-hint {
  margin-top: 5px;
}

/* 原因选择器 */
.reason-selector {
  width: 100%;
}
</style>