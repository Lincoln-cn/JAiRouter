<template>
  <div class="blacklist-management">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>安全黑名单管理</span>
          <div>
            <el-button type="primary" @click="handleOpenAddDialog">
              <el-icon><Plus /></el-icon>添加黑名单
            </el-button>
            <el-button :loading="loading" @click="handleRefresh">
              <el-icon><Refresh /></el-icon>刷新
            </el-button>
            <el-button type="warning" @click="handleCleanup">
              <el-icon><Delete /></el-icon>清理过期
            </el-button>
          </div>
        </div>
      </template>

      <!-- 统计卡片 -->
      <div class="stats-section">
        <el-row :gutter="20">
          <el-col :span="6">
            <el-statistic title="活跃总数" :value="stats.totalActive">
              <template #suffix>
                <el-tag type="success" size="small">条</el-tag>
              </template>
            </el-statistic>
          </el-col>
          <el-col :span="6">
            <el-statistic title="Token黑名单" :value="stats.tokenCount">
              <template #suffix>
                <el-tag type="warning" size="small">令牌</el-tag>
              </template>
            </el-statistic>
          </el-col>
          <el-col :span="6">
            <el-statistic title="IP黑名单" :value="stats.ipCount">
              <template #suffix>
                <el-tag type="danger" size="small">地址</el-tag>
              </template>
            </el-statistic>
          </el-col>
          <el-col :span="6">
            <el-statistic title="设备黑名单" :value="stats.deviceCount">
              <template #suffix>
                <el-tag type="info" size="small">设备</el-tag>
              </template>
            </el-statistic>
          </el-col>
        </el-row>
      </div>

      <!-- 搜索和过滤 -->
      <div class="filter-section">
        <el-row :gutter="20">
          <el-col :span="4">
            <el-select v-model="filterForm.type" placeholder="类型筛选" clearable @change="handleSearch">
              <el-option label="全部类型" value="" />
              <el-option label="Token" value="TOKEN" />
              <el-option label="IP地址" value="IP" />
              <el-option label="设备" value="DEVICE" />
            </el-select>
          </el-col>
          <el-col :span="4">
            <el-select v-model="filterForm.status" placeholder="状态筛选" clearable @change="handleSearch">
              <el-option label="全部状态" value="" />
              <el-option label="活跃" value="ACTIVE" />
              <el-option label="已过期" value="EXPIRED" />
              <el-option label="已移除" value="REMOVED" />
            </el-select>
          </el-col>
          <el-col :span="4">
            <el-input v-model="filterForm.userId" placeholder="用户ID" clearable @keyup.enter="handleSearch" />
          </el-col>
          <el-col :span="4">
            <el-button type="primary" @click="handleSearch">搜索</el-button>
            <el-button @click="handleResetFilter">重置</el-button>
          </el-col>
        </el-row>
      </div>

      <!-- 黑名单列表 -->
      <el-table v-loading="loading" :data="pageData.content" style="width: 100%">
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="blacklistType" label="类型" width="100">
          <template #default="scope">
            <el-tag :type="getTypeTagType(scope.row.blacklistType)">
              {{ scope.row.blacklistType }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="targetValueMasked" label="目标值" show-overflow-tooltip />
        <el-table-column prop="userId" label="关联用户" width="120" show-overflow-tooltip />
        <el-table-column prop="reason" label="原因" width="150" show-overflow-tooltip />
        <el-table-column prop="riskLevel" label="风险等级" width="100">
          <template #default="scope">
            <el-tag :type="getRiskTagType(scope.row.riskLevel)" size="small">
              {{ scope.row.riskLevel }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="addedBy" label="添加者" width="100" />
        <el-table-column prop="addedAt" label="添加时间" width="160">
          <template #default="scope">
            {{ formatDateTime(scope.row.addedAt) }}
          </template>
        </el-table-column>
        <el-table-column prop="expiresAt" label="过期时间" width="160">
          <template #default="scope">
            <span v-if="scope.row.permanent">永久</span>
            <span v-else>{{ formatDateTime(scope.row.expiresAt) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="80">
          <template #default="scope">
            <el-tag :type="getStatusTagType(scope.row.status)" size="small">
              {{ scope.row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="scope">
            <el-button v-if="scope.row.status === 'ACTIVE'" type="danger" size="small" @click="handleRemove(scope.row)">
              移除
            </el-button>
            <el-button type="primary" size="small" link @click="handleViewDetail(scope.row)">
              详情
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <div class="pagination-section">
        <el-pagination
          v-model:current-page="filterForm.page"
          v-model:page-size="filterForm.size"
          :page-sizes="[10, 20, 50, 100]"
          :total="pageData.totalElements"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="handleSearch"
          @current-change="handleSearch"
        />
      </div>
    </el-card>

    <!-- 添加黑名单对话框 -->
    <el-dialog v-model="addDialogVisible" title="添加黑名单" width="600px">
      <el-form ref="addFormRef" :model="addForm" :rules="addFormRules" label-width="100px">
        <el-form-item label="类型" prop="blacklistType">
          <el-radio-group v-model="addForm.blacklistType" @change="handleTypeChange">
            <el-radio-button value="TOKEN">Token令牌</el-radio-button>
            <el-radio-button value="IP">IP地址</el-radio-button>
            <el-radio-button value="DEVICE">设备标识</el-radio-button>
          </el-radio-group>
        </el-form-item>
        
        <!-- Token选择器 -->
        <el-form-item v-if="addForm.blacklistType === 'TOKEN'" label="选择令牌" prop="targetValue">
          <div class="selector-container">
            <el-select
              v-model="addForm.targetValue"
              filterable
              remote
              reserve-keyword
              placeholder="搜索并选择令牌"
              :remote-method="searchTokens"
              :loading="tokenLoading"
              style="width: 100%"
            >
              <el-option
                v-for="token in tokenOptions"
                :key="token.id"
                :label="`用户: ${token.userId} - ${token.status}`"
                :value="token.tokenHash"
              >
                <div class="token-option">
                  <span class="token-user">{{ token.userId }}</span>
                  <el-tag :type="getTokenStatusTagType(token.status)" size="small">{{ token.status }}</el-tag>
                  <span class="token-time">{{ formatDateTime(token.issuedAt) }}</span>
                </div>
              </el-option>
            </el-select>
            <el-button type="primary" link @click="loadActiveTokens">加载活跃令牌</el-button>
          </div>
        </el-form-item>
        
        <!-- IP选择器 -->
        <el-form-item v-if="addForm.blacklistType === 'IP'" label="选择IP" prop="targetValue">
          <div class="selector-container">
            <el-select
              v-model="addForm.targetValue"
              filterable
              allow-create
              default-first-option
              placeholder="选择或输入IP地址"
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
                  <el-tag v-if="ip.suspicious" type="danger" size="small">可疑</el-tag>
                  <span class="ip-count">登录{{ ip.loginCount }}次</span>
                </div>
              </el-option>
            </el-select>
            <el-button type="primary" link @click="loadSuspiciousIPs">加载可疑IP</el-button>
          </div>
          <div class="ip-input-hint">
            <el-text size="small" type="info">支持直接输入IP或IP段(如 192.168.1.*)</el-text>
          </div>
        </el-form-item>
        
        <!-- 设备标识输入 -->
        <el-form-item v-if="addForm.blacklistType === 'DEVICE'" label="设备标识" prop="targetValue">
          <el-input v-model="addForm.targetValue" placeholder="输入设备标识或从令牌中选择">
            <template #append>
              <el-button @click="showDeviceSelector = true">从令牌选择</el-button>
            </template>
          </el-input>
        </el-form-item>

        <!-- 用户选择器 -->
        <el-form-item label="关联用户">
          <el-select
            v-model="addForm.userId"
            filterable
            clearable
            placeholder="选择关联用户（可选）"
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
                <el-tag v-if="user.enabled" type="success" size="small">启用</el-tag>
                <el-tag v-else type="danger" size="small">禁用</el-tag>
              </div>
            </el-option>
          </el-select>
        </el-form-item>
        
        <!-- 快速原因选择 -->
        <el-form-item label="加入原因">
          <div class="reason-selector">
            <el-select
              v-model="addForm.reason"
              filterable
              allow-create
              default-first-option
              placeholder="选择或输入原因"
              style="width: 100%"
            >
              <el-option label="恶意登录尝试" value="恶意登录尝试" />
              <el-option label="异常访问行为" value="异常访问行为" />
              <el-option label="账户被盗" value="账户被盗" />
              <el-option label="违规操作" value="违规操作" />
              <el-option label="安全风险" value="安全风险" />
              <el-option label="用户请求封禁" value="用户请求封禁" />
              <el-option label="其他" value="其他" />
            </el-select>
          </div>
        </el-form-item>
        
        <el-form-item label="风险等级">
          <el-radio-group v-model="addForm.riskLevel">
            <el-radio-button value="LOW">低</el-radio-button>
            <el-radio-button value="MEDIUM">中</el-radio-button>
            <el-radio-button value="HIGH">高</el-radio-button>
            <el-radio-button value="CRITICAL">严重</el-radio-button>
          </el-radio-group>
        </el-form-item>
        
        <el-form-item label="有效期">
          <el-radio-group v-model="addForm.expiryType">
            <el-radio value="permanent">永久</el-radio>
            <el-radio value="temporary">临时</el-radio>
          </el-radio-group>
          <el-input-number v-if="addForm.expiryType === 'temporary'" v-model="addForm.expiresInDays" :min="1" :max="365" style="margin-left: 10px" />
          <span v-if="addForm.expiryType === 'temporary'" style="margin-left: 5px">天</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="addDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="addLoading" @click="handleAdd">确认添加</el-button>
      </template>
    </el-dialog>

    <!-- 设备选择对话框 -->
    <el-dialog v-model="showDeviceSelector" title="从令牌选择设备" width="500px">
      <el-table :data="tokenOptions" @row-click="selectDeviceFromToken">
        <el-table-column prop="userId" label="用户" width="120" />
        <el-table-column prop="deviceInfo" label="设备信息" show-overflow-tooltip />
        <el-table-column prop="ipAddress" label="IP" width="130" />
      </el-table>
    </el-dialog>

    <!-- 详情对话框 -->
    <el-dialog v-model="detailDialogVisible" title="黑名单详情" width="500px">
      <el-descriptions :column="1" border>
        <el-descriptions-item label="ID">{{ detailData?.id }}</el-descriptions-item>
        <el-descriptions-item label="类型">
          <el-tag :type="getTypeTagType(detailData?.blacklistType)">{{ detailData?.blacklistType }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="目标值">{{ detailData?.targetValue }}</el-descriptions-item>
        <el-descriptions-item label="目标值掩码">{{ detailData?.targetValueMasked }}</el-descriptions-item>
        <el-descriptions-item label="关联用户">{{ detailData?.userId || '-' }}</el-descriptions-item>
        <el-descriptions-item label="加入原因">{{ detailData?.reason || '-' }}</el-descriptions-item>
        <el-descriptions-item label="风险等级">
          <el-tag :type="getRiskTagType(detailData?.riskLevel)">{{ detailData?.riskLevel }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="添加者">{{ detailData?.addedBy }}</el-descriptions-item>
        <el-descriptions-item label="添加时间">{{ formatDateTime(detailData?.addedAt) }}</el-descriptions-item>
        <el-descriptions-item label="过期时间">
          {{ detailData?.permanent ? '永久' : formatDateTime(detailData?.expiresAt) }}
        </el-descriptions-item>
        <el-descriptions-item label="剩余时间">
          {{ detailData?.permanent ? '永久有效' : formatRemainingTime(detailData?.remainingSeconds) }}
        </el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="getStatusTagType(detailData?.status)">{{ detailData?.status }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="来源">{{ detailData?.source }}</el-descriptions-item>
      </el-descriptions>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, watch } from 'vue'
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
import request from '@/utils/request'

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

const addFormRules = {
  blacklistType: [{ required: true, message: '请选择黑名单类型', trigger: 'change' }],
  targetValue: [{ required: true, message: '请输入或选择目标值', trigger: 'blur' }]
}

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
    ElMessage.warning('加载令牌列表失败')
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

// 加载可疑IP（从审计日志中提取）
async function loadSuspiciousIPs() {
  try {
    // 尝试从审计日志获取IP列表
    const response = await request.get('/security/audit/extended/suspicious-ips', {
      params: { limit: 50 }
    })
    if (response.data?.success && response.data?.data) {
      ipOptions.value = response.data.data.map((item: any) => ({
        ip: item.ipAddress || item.ip,
        loginCount: item.loginCount || item.count || 1,
        suspicious: item.suspicious || item.failedAttempts > 3
      }))
    }
  } catch (e) {
    // 如果API不存在，使用模拟数据
    console.warn('加载可疑IP API不存在，使用令牌中的IP')
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
      suspicious: count > 5
    }))
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
  ElMessage.success('刷新成功')
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
      ElMessage.success('添加成功')
      addDialogVisible.value = false
      await handleRefresh()
    } else {
      ElMessage.error(res.message || '添加失败')
    }
  } catch (e) {
    ElMessage.error('添加失败')
  } finally {
    addLoading.value = false
  }
}

// 移除黑名单
async function handleRemove(row: BlacklistEntry) {
  try {
    await ElMessageBox.confirm('确认从黑名单移除该条目？', '确认移除', { type: 'warning' })
    const res = await removeFromBlacklist(row.id)
    if (res.success) {
      ElMessage.success('移除成功')
      await handleRefresh()
    } else {
      ElMessage.error(res.message || '移除失败')
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
    await ElMessageBox.confirm('确认清理所有过期的黑名单条目？', '确认清理', { type: 'warning' })
    const res = await cleanupExpiredBlacklist()
    if (res.success) {
      ElMessage.success(`清理完成，共清理 ${res.data} 条`)
      await handleRefresh()
    } else {
      ElMessage.error(res.message || '清理失败')
    }
  } catch {}
}

// 格式化时间
function formatDateTime(dt: string | undefined) {
  if (!dt) return '-'
  return new Date(dt).toLocaleString('zh-CN')
}

// 格式化剩余时间
function formatRemainingTime(seconds: number | undefined) {
  if (!seconds) return '-'
  if (seconds <= 0) return '已过期'
  const days = Math.floor(seconds / 86400)
  const hours = Math.floor((seconds % 86400) / 3600)
  if (days > 0) return `${days}天${hours}小时`
  return `${hours}小时`
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
.blacklist-management {
  padding: 20px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.stats-section {
  margin-bottom: 20px;
  padding: 20px;
  background: #f5f7fa;
  border-radius: 4px;
}

.filter-section {
  margin-bottom: 15px;
}

.pagination-section {
  margin-top: 15px;
  display: flex;
  justify-content: flex-end;
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
  color: #909399;
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
  color: #909399;
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