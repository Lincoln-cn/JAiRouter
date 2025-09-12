<template>
  <div class="circuit-breaker">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>熔断器状态</span>
          <el-button type="primary" @click="handleRefresh">刷新</el-button>
        </div>
      </template>
      
      <el-table :data="circuitBreakers" style="width: 100%">
        <el-table-column prop="service" label="服务" width="180" />
        <el-table-column prop="instance" label="实例" width="250" />
        <el-table-column prop="status" label="状态" width="120">
          <template #default="scope">
            <el-tag :type="getCircuitBreakerStatusType(scope.row.status)">
              {{ getCircuitBreakerStatusText(scope.row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="failureRate" label="失败率" width="100">
          <template #default="scope">
            {{ scope.row.failureRate }}%
          </template>
        </el-table-column>
        <el-table-column prop="failureCount" label="失败次数" width="100" />
        <el-table-column prop="lastFailure" label="最后失败时间" width="200" />
        <el-table-column prop="nextAttempt" label="下次尝试时间" width="200" />
        <el-table-column label="操作" width="200">
          <template #default="scope">
            <el-button 
              size="small" 
              @click="handleReset(scope.row)"
              :disabled="scope.row.status === 'CLOSED'"
            >
              重置
            </el-button>
            <el-button 
              size="small" 
              type="danger" 
              @click="handleForceOpen(scope.row)"
            >
              强制开启
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
    
    <el-card class="config-card">
      <template #header>
        <div class="card-header">
          <span>熔断器配置</span>
          <el-button type="primary" @click="handleSaveConfig">保存配置</el-button>
        </div>
      </template>
      
      <el-form :model="config" label-width="150px">
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="失败阈值(%)">
              <el-input-number v-model="config.failureRateThreshold" :min="1" :max="100" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="最小请求数">
              <el-input-number v-model="config.minimumNumberOfCalls" :min="1" :max="1000" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="滑动窗口大小(秒)">
              <el-input-number v-model="config.slidingWindowSize" :min="1" :max="3600" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="等待时间(秒)">
              <el-input-number v-model="config.waitDurationInOpenState" :min="1" :max="3600" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="半开状态允许请求数">
              <el-input-number v-model="config.permittedNumberOfCallsInHalfOpenState" :min="1" :max="100" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="是否记录异常为失败">
              <el-switch v-model="config.recordExceptionsAsFailures" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'

// 熔断器状态数据
const circuitBreakers = ref([
  { 
    id: 1,
    service: 'Chat Service', 
    instance: 'http://localhost:11434', 
    status: 'CLOSED', 
    failureRate: 0.5, 
    failureCount: 2, 
    lastFailure: '2023-10-01 09:30:00',
    nextAttempt: ''
  },
  { 
    id: 2,
    service: 'Embedding Service', 
    instance: 'http://localhost:8000', 
    status: 'OPEN', 
    failureRate: 5.2, 
    failureCount: 15, 
    lastFailure: '2023-10-01 09:45:00',
    nextAttempt: '2023-10-01 10:00:00'
  },
  { 
    id: 3,
    service: 'Rerank Service', 
    instance: 'http://localhost:9000', 
    status: 'HALF_OPEN', 
    failureRate: 2.1, 
    failureCount: 5, 
    lastFailure: '2023-10-01 09:20:00',
    nextAttempt: ''
  }
])

// 熔断器配置
const config = ref({
  failureRateThreshold: 50,
  minimumNumberOfCalls: 100,
  slidingWindowSize: 100,
  waitDurationInOpenState: 60,
  permittedNumberOfCallsInHalfOpenState: 10,
  recordExceptionsAsFailures: true
})

// 获取熔断器状态类型
const getCircuitBreakerStatusType = (status: string) => {
  switch (status) {
    case 'CLOSED':
      return 'success'
    case 'OPEN':
      return 'danger'
    case 'HALF_OPEN':
      return 'warning'
    default:
      return 'info'
  }
}

// 获取熔断器状态文本
const getCircuitBreakerStatusText = (status: string) => {
  switch (status) {
    case 'CLOSED':
      return '关闭'
    case 'OPEN':
      return '开启'
    case 'HALF_OPEN':
      return '半开'
    default:
      return status
  }
}

// 刷新
const handleRefresh = () => {
  // 这里可以调用API获取最新数据
  ElMessage.success('数据已刷新')
}

// 重置熔断器
const handleReset = (row: any) => {
  ElMessageBox.confirm(`确定要重置 ${row.service} 的熔断器吗？`, '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(() => {
    // 这里可以调用API重置熔断器
    ElMessage.success('熔断器已重置')
  })
}

// 强制开启熔断器
const handleForceOpen = (row: any) => {
  ElMessageBox.confirm(`确定要强制开启 ${row.service} 的熔断器吗？`, '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(() => {
    // 这里可以调用API强制开启熔断器
    ElMessage.success('熔断器已强制开启')
  })
}

// 保存配置
const handleSaveConfig = () => {
  // 这里可以调用API保存配置
  ElMessage.success('配置已保存')
}

// 组件挂载时获取数据
onMounted(() => {
  // 这里可以调用API获取真实数据
  console.log('获取熔断器状态数据')
})
</script>

<style scoped>
.circuit-breaker {
  padding: 20px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.config-card {
  margin-top: 20px;
}
</style>