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
                <el-option label="登录" value="LOGIN" />
                <el-option label="登出" value="LOGOUT" />
                <el-option label="创建" value="CREATE" />
                <el-option label="更新" value="UPDATE" />
                <el-option label="删除" value="DELETE" />
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
      <el-table :data="logs" style="width: 100%" border>
        <el-table-column prop="timestamp" label="时间" width="180" />
        <el-table-column prop="userId" label="用户ID" width="120" />
        <el-table-column prop="eventType" label="事件类型" width="100" />
        <el-table-column prop="resource" label="资源" width="150" />
        <el-table-column prop="clientIp" label="客户端IP" width="150" />
        <el-table-column prop="success" label="结果" width="80">
          <template #default="scope">
            <el-tag :type="scope.row.success ? 'success' : 'danger'">
              {{ scope.row.success ? '成功' : '失败' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="description" label="描述" show-overflow-tooltip />
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
      <el-descriptions :column="1" border>
        <el-descriptions-item label="时间">{{ currentLog.timestamp }}</el-descriptions-item>
        <el-descriptions-item label="用户ID">{{ currentLog.userId }}</el-descriptions-item>
        <el-descriptions-item label="事件类型">{{ currentLog.eventType }}</el-descriptions-item>
        <el-descriptions-item label="资源">{{ currentLog.resource }}</el-descriptions-item>
        <el-descriptions-item label="客户端IP">{{ currentLog.clientIp }}</el-descriptions-item>
        <el-descriptions-item label="结果">
          <el-tag :type="currentLog.success ? 'success' : 'danger'">
            {{ currentLog.success ? '成功' : '失败' }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="描述">{{ currentLog.description }}</el-descriptions-item>
        <el-descriptions-item label="详细信息">
          <pre>{{ JSON.stringify(currentLog.details, null, 2) }}</pre>
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
  total: 100
})

// 模拟数据
const logs = ref([
  { 
    id: 1,
    timestamp: '2023-10-01 10:00:00', 
    userId: 'admin', 
    eventType: 'LOGIN', 
    resource: '/api/auth/login',
    clientIp: '192.168.1.100',
    success: true,
    description: '用户登录成功',
    details: {
      userAgent: 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36'
    }
  },
  { 
    id: 2,
    timestamp: '2023-10-01 09:30:00', 
    userId: 'user1', 
    eventType: 'CREATE', 
    resource: '/api/config/services',
    clientIp: '192.168.1.101',
    success: true,
    description: '创建服务配置',
    details: {
      serviceName: 'chat'
    }
  },
  { 
    id: 3,
    timestamp: '2023-10-01 08:45:00', 
    userId: 'user2', 
    eventType: 'UPDATE', 
    resource: '/api/config/instances',
    clientIp: '192.168.1.102',
    success: false,
    description: '更新实例配置失败',
    details: {
      error: '权限不足'
    }
  }
])

const detailDialogVisible = ref(false)
const currentLog = ref({} as any)

// 搜索
const handleSearch = () => {
  // 这里可以调用API进行搜索
  ElMessage.success('搜索完成')
}

// 重置
const handleReset = () => {
  searchForm.value = {
    startTime: '',
    endTime: '',
    eventType: '',
    userId: '',
    clientIp: '',
    success: null
  }
}

// 刷新
const handleRefresh = () => {
  // 这里可以调用API刷新数据
  ElMessage.success('数据已刷新')
}

// 导出
const handleExport = () => {
  // 这里可以调用API导出数据
  ElMessage.success('导出任务已提交')
}

// 查看详情
const handleViewDetail = (row: any) => {
  currentLog.value = row
  detailDialogVisible.value = true
}

// 分页大小变更
const handleSizeChange = (val: number) => {
  pagination.value.pageSize = val
  // 这里可以调用API获取新数据
  console.log(`每页 ${val} 条`)
}

// 当前页变更
const handleCurrentChange = (val: number) => {
  pagination.value.currentPage = val
  // 这里可以调用API获取新数据
  console.log(`当前页: ${val}`)
}

// 组件挂载时获取数据
onMounted(() => {
  // 这里可以调用API获取真实数据
  console.log('获取审计日志数据')
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
</style>