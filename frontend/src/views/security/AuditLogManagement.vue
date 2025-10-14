<template>
  <div class="audit-log-management">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>审计日志</span>
          <div class="header-actions">
            <el-button @click="handleRefresh">刷新</el-button>
            <el-button type="primary" @click="handleExport">导出</el-button>
          </div>
        </div>
      </template>
      
      <!-- 搜索条件 -->
      <el-form :model="searchForm" label-width="100px" class="search-form">
        <el-row :gutter="20">
          <el-col :span="6">
            <el-form-item label="开始时间">
              <el-date-picker
                v-model="searchForm.startTime"
                type="datetime"
                placeholder="选择开始时间"
                format="YYYY-MM-DD HH:mm:ss"
                value-format="YYYY-MM-DD HH:mm:ss"
              />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="结束时间">
              <el-date-picker
                v-model="searchForm.endTime"
                type="datetime"
                placeholder="选择结束时间"
                format="YYYY-MM-DD HH:mm:ss"
                value-format="YYYY-MM-DD HH:mm:ss"
              />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="事件类型">
              <el-select v-model="searchForm.eventType" placeholder="请选择事件类型" clearable>
                <el-option label="JWT令牌颁发" value="JWT_TOKEN_ISSUED" />
                <el-option label="JWT令牌刷新" value="JWT_TOKEN_REFRESHED" />
                <el-option label="JWT令牌撤销" value="JWT_TOKEN_REVOKED" />
                <el-option label="JWT令牌验证" value="JWT_TOKEN_VALIDATED" />
                <el-option label="API密钥创建" value="API_KEY_CREATED" />
                <el-option label="API密钥使用" value="API_KEY_USED" />
                <el-option label="API密钥撤销" value="API_KEY_REVOKED" />
                <el-option label="认证失败" value="AUTHENTICATION_FAILED" />
                <el-option label="授权失败" value="AUTHORIZATION_FAILED" />
                <el-option label="可疑活动" value="SUSPICIOUS_ACTIVITY" />
                <el-option label="安全告警" value="SECURITY_ALERT" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="用户ID">
              <el-input v-model="searchForm.userId" placeholder="请输入用户ID" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="6">
            <el-form-item label="客户端IP">
              <el-input v-model="searchForm.clientIp" placeholder="请输入客户端IP" />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="操作结果">
              <el-select v-model="searchForm.success" placeholder="请选择操作结果" clearable>
                <el-option label="成功" :value="true" />
                <el-option label="失败" :value="false" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label=" ">
              <el-button type="primary" @click="handleSearch">搜索</el-button>
              <el-button @click="handleReset">重置</el-button>
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      
      <!-- 日志表格 -->
      <el-table :data="logs" style="width: 100%" border v-loading="loading">
        <el-table-column prop="timestamp" label="时间" width="180">
          <template #default="scope">
            {{ formatDateTime(scope.row.timestamp) }}
          </template>
        </el-table-column>
        <el-table-column prop="userId" label="用户ID" width="120" />
        <el-table-column prop="type" label="事件类型" width="120">
          <template #default="scope">
            <el-tag :type="getEventTypeColor(scope.row.type)">
              {{ getEventTypeText(scope.row.type) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="resourceId" label="资源ID" width="150" show-overflow-tooltip />
        <el-table-column prop="ipAddress" label="客户端IP" width="150" />
        <el-table-column prop="success" label="结果" width="80">
          <template #default="scope">
            <el-tag :type="scope.row.success ? 'success' : 'danger'">
              {{ scope.row.success ? '成功' : '失败' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="details" label="描述" show-overflow-tooltip />
        <el-table-column label="操作" width="100">
          <template #default="scope">
            <el-button size="small" @click="handleViewDetail(scope.row)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>
      
      <!-- 分页 -->
      <el-pagination
        v-model:current-page="pagination.currentPage"
        v-model:page-size="pagination.pageSize"
        :page-sizes="[10, 20, 50, 100]"
        :total="pagination.total"
        layout="total, sizes, prev, pager, next, jumper"
        @size-change="handleSizeChange"
        @current-change="handleCurrentChange"
        class="pagination"
      />
    </el-card>
    
    <!-- 日志详情对话框 -->
    <el-dialog v-model="detailDialogVisible" title="日志详情" width="600px">
      <el-descriptions v-if="currentLog" :column="1" border>
        <el-descriptions-item label="事件ID">{{ currentLog.id }}</el-descriptions-item>
        <el-descriptions-item label="时间">{{ formatDateTime(currentLog.timestamp) }}</el-descriptions-item>
        <el-descriptions-item label="用户ID">{{ currentLog.userId }}</el-descriptions-item>
        <el-descriptions-item label="事件类型">
          <el-tag :type="getEventTypeColor(currentLog.type)">
            {{ getEventTypeText(currentLog.type) }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="资源ID">{{ currentLog.resourceId }}</el-descriptions-item>
        <el-descriptions-item label="操作">{{ currentLog.action }}</el-descriptions-item>
        <el-descriptions-item label="客户端IP">{{ currentLog.ipAddress }}</el-descriptions-item>
        <el-descriptions-item label="用户代理" v-if="currentLog.userAgent">{{ currentLog.userAgent }}</el-descriptions-item>
        <el-descriptions-item label="结果">
          <el-tag :type="currentLog.success ? 'success' : 'danger'">
            {{ currentLog.success ? '成功' : '失败' }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="描述">{{ currentLog.details }}</el-descriptions-item>
        <el-descriptions-item label="元数据" v-if="currentLog.metadata">
          <pre class="metadata-pre">{{ JSON.stringify(currentLog.metadata, null, 2) }}</pre>
        </el-descriptions-item>
      </el-descriptions>
      
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="detailDialogVisible = false">关闭</el-button>
        </span>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import {
  queryAuditEventsAdvanced,
  generateSecurityReport,
  type AuditEvent,
  type ExtendedAuditQueryResponse
} from '@/api/auditLog'

// 搜索表单
const searchForm = ref({
  startTime: '',
  endTime: '',
  eventType: '',
  userId: '',
  clientIp: '',
  success: null as boolean | null
})

// 分页
const pagination = ref({
  currentPage: 1,
  pageSize: 20,
  total: 0
})

// 审计日志数据
const logs = ref<AuditEvent[]>([])
const loading = ref(false)

const detailDialogVisible = ref(false)
const currentLog = ref<AuditEvent | null>(null)

// 搜索
const handleSearch = async () => {
  await loadAuditLogs()
  ElMessage.success('搜索完成')
}

// 重置
const handleReset = async () => {
  searchForm.value = {
    startTime: '',
    endTime: '',
    eventType: '',
    userId: '',
    clientIp: '',
    success: null
  }
  pagination.value.currentPage = 1
  await loadAuditLogs()
}

// 刷新
const handleRefresh = async () => {
  await loadAuditLogs()
  ElMessage.success('数据已刷新')
}

// 导出
const handleExport = async () => {
  try {
    const report = await generateSecurityReport(searchForm.value.startTime, searchForm.value.endTime)
    // 这里可以处理报告数据，比如下载为文件
    console.log('安全报告:', report)
    ElMessage.success('导出任务已提交')
  } catch (error: any) {
    ElMessage.error('导出失败: ' + (error.message || '未知错误'))
  }
}

// 查看详情
const handleViewDetail = (row: AuditEvent) => {
  currentLog.value = row
  detailDialogVisible.value = true
}

// 分页大小变更
const handleSizeChange = async (val: number) => {
  pagination.value.pageSize = val
  pagination.value.currentPage = 1
  await loadAuditLogs()
}

// 当前页变更
const handleCurrentChange = async (val: number) => {
  pagination.value.currentPage = val
  await loadAuditLogs()
}

// 加载审计日志数据
const loadAuditLogs = async () => {
  loading.value = true
  try {
    const query = {
      startTime: searchForm.value.startTime || undefined,
      endTime: searchForm.value.endTime || undefined,
      userId: searchForm.value.userId || undefined,
      ipAddress: searchForm.value.clientIp || undefined,
      success: searchForm.value.success,
      page: pagination.value.currentPage - 1, // API使用0基索引
      size: pagination.value.pageSize
    }

    // 根据事件类型过滤
    if (searchForm.value.eventType) {
      (query as any).eventType = searchForm.value.eventType
    }

    const result: ExtendedAuditQueryResponse = await queryAuditEventsAdvanced(query)
    logs.value = result.events
    pagination.value.total = result.totalElements
  } catch (error: any) {
    ElMessage.error('加载审计日志失败: ' + (error.message || '未知错误'))
  } finally {
    loading.value = false
  }
}

// 格式化日期时间
const formatDateTime = (dateTime: string) => {
  if (!dateTime) return ''
  return new Date(dateTime).toLocaleString('zh-CN')
}

// 获取事件类型颜色
const getEventTypeColor = (type: string) => {
  if (type.startsWith('JWT_TOKEN')) return 'primary'
  if (type.startsWith('API_KEY')) return 'success'
  if (type.includes('FAILED') || type.includes('SUSPICIOUS')) return 'danger'
  if (type.includes('LOGIN') || type.includes('CREATE')) return 'success'
  if (type.includes('UPDATE')) return 'warning'
  if (type.includes('DELETE') || type.includes('REVOKE')) return 'danger'
  return 'info'
}

// 获取事件类型文本
const getEventTypeText = (type: string) => {
  const typeMap: Record<string, string> = {
    'JWT_TOKEN_ISSUED': 'JWT令牌颁发',
    'JWT_TOKEN_REFRESHED': 'JWT令牌刷新',
    'JWT_TOKEN_REVOKED': 'JWT令牌撤销',
    'JWT_TOKEN_VALIDATED': 'JWT令牌验证',
    'JWT_TOKEN_EXPIRED': 'JWT令牌过期',
    'API_KEY_CREATED': 'API密钥创建',
    'API_KEY_USED': 'API密钥使用',
    'API_KEY_REVOKED': 'API密钥撤销',
    'API_KEY_EXPIRED': 'API密钥过期',
    'LOGIN': '登录',
    'LOGOUT': '登出',
    'CREATE': '创建',
    'UPDATE': '更新',
    'DELETE': '删除',
    'AUTHENTICATION_FAILED': '认证失败',
    'AUTHORIZATION_FAILED': '授权失败',
    'SUSPICIOUS_ACTIVITY': '可疑活动',
    'SECURITY_ALERT': '安全告警'
  }
  return typeMap[type] || type
}

// 组件挂载时获取数据
onMounted(async () => {
  await loadAuditLogs()
  console.log('审计日志管理页面已加载')
})
</script>

<style scoped>
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.header-actions {
  display: flex;
  gap: 10px;
}

.search-form {
  margin-bottom: 20px;
  padding: 20px;
  background-color: #f5f5f5;
  border-radius: 4px;
}

.pagination {
  margin-top: 20px;
  text-align: right;
}

.audit-log-management {
  padding: 20px;
}

.metadata-pre {
  max-height: 200px;
  overflow-y: auto;
  background-color: #f5f7fa;
  padding: 10px;
  border-radius: 4px;
  font-size: 12px;
  white-space: pre-wrap;
}
</style>