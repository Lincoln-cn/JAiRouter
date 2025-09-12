<template>
  <div class="health-check">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>健康检查</span>
          <div class="header-actions">
            <el-button @click="handleRefresh">刷新</el-button>
            <el-button type="primary" @click="handleRunAll">运行所有检查</el-button>
          </div>
        </div>
      </template>
      
      <el-table :data="healthChecks" style="width: 100%">
        <el-table-column prop="name" label="检查项" width="200" />
        <el-table-column prop="status" label="状态" width="120">
          <template #default="scope">
            <el-tag :type="scope.row.status === 'healthy' ? 'success' : 'danger'">
              {{ scope.row.status === 'healthy' ? '健康' : '异常' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="lastCheck" label="最后检查时间" width="200" />
        <el-table-column prop="message" label="消息" show-overflow-tooltip />
        <el-table-column label="操作" width="150">
          <template #default="scope">
            <el-button size="small" @click="handleRunCheck(scope.row)">运行检查</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
    
    <el-card class="details-card">
      <template #header>
        <div class="card-header">
          <span>详细信息</span>
        </div>
      </template>
      
      <el-tabs v-model="activeTab">
        <el-tab-pane label="系统信息" name="system">
          <el-descriptions :column="2" border>
            <el-descriptions-item label="操作系统">{{ systemInfo.os }}</el-descriptions-item>
            <el-descriptions-item label="系统架构">{{ systemInfo.arch }}</el-descriptions-item>
            <el-descriptions-item label="Java版本">{{ systemInfo.javaVersion }}</el-descriptions-item>
            <el-descriptions-item label="运行时间">{{ systemInfo.uptime }}</el-descriptions-item>
            <el-descriptions-item label="CPU核心数">{{ systemInfo.cpuCores }}</el-descriptions-item>
            <el-descriptions-item label="内存总量">{{ systemInfo.totalMemory }}</el-descriptions-item>
            <el-descriptions-item label="已用内存">{{ systemInfo.usedMemory }}</el-descriptions-item>
            <el-descriptions-item label="内存使用率">{{ systemInfo.memoryUsage }}%</el-descriptions-item>
          </el-descriptions>
        </el-tab-pane>
        
        <el-tab-pane label="数据库" name="database">
          <el-descriptions :column="1" border>
            <el-descriptions-item label="连接状态">
              <el-tag type="success">已连接</el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="数据库类型">{{ databaseInfo.type }}</el-descriptions-item>
            <el-descriptions-item label="版本">{{ databaseInfo.version }}</el-descriptions-item>
            <el-descriptions-item label="连接池使用率">{{ databaseInfo.poolUsage }}%</el-descriptions-item>
          </el-descriptions>
        </el-tab-pane>
        
        <el-tab-pane label="缓存" name="cache">
          <el-descriptions :column="1" border>
            <el-descriptions-item label="连接状态">
              <el-tag type="success">已连接</el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="缓存类型">{{ cacheInfo.type }}</el-descriptions-item>
            <el-descriptions-item label="命中率">{{ cacheInfo.hitRate }}%</el-descriptions-item>
            <el-descriptions-item label="使用内存">{{ cacheInfo.usedMemory }}</el-descriptions-item>
          </el-descriptions>
        </el-tab-pane>
      </el-tabs>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'

// 健康检查数据
const healthChecks = ref([
  { 
    id: 1,
    name: '数据库连接', 
    status: 'healthy', 
    lastCheck: '2023-10-01 10:00:00', 
    message: '连接正常' 
  },
  { 
    id: 2,
    name: '缓存服务', 
    status: 'healthy', 
    lastCheck: '2023-10-01 10:00:00', 
    message: '服务正常' 
  },
  { 
    id: 3,
    name: '磁盘空间', 
    status: 'healthy', 
    lastCheck: '2023-10-01 10:00:00', 
    message: '磁盘使用率 45%' 
  },
  { 
    id: 4,
    name: '内存使用', 
    status: 'healthy', 
    lastCheck: '2023-10-01 10:00:00', 
    message: '内存使用率 65%' 
  },
  { 
    id: 5,
    name: '下游服务', 
    status: 'unhealthy', 
    lastCheck: '2023-10-01 09:55:00', 
    message: 'Chat Service 无法连接' 
  }
])

// 系统信息
const systemInfo = ref({
  os: 'Linux 5.4.0-123-generic',
  arch: 'amd64',
  javaVersion: '17.0.8',
  uptime: '15天 3小时 25分钟',
  cpuCores: 8,
  totalMemory: '16GB',
  usedMemory: '10.4GB',
  memoryUsage: 65
})

// 数据库信息
const databaseInfo = ref({
  type: 'Redis',
  version: '6.2.7',
  poolUsage: 25
})

// 缓存信息
const cacheInfo = ref({
  type: 'Redis',
  hitRate: 92.5,
  usedMemory: '256MB'
})

// 激活的标签页
const activeTab = ref('system')

// 刷新
const handleRefresh = () => {
  // 这里可以调用API获取最新数据
  ElMessage.success('数据已刷新')
}

// 运行所有检查
const handleRunAll = () => {
  // 这里可以调用API运行所有健康检查
  ElMessage.success('已开始运行所有健康检查')
}

// 运行单个检查
const handleRunCheck = (row: any) => {
  // 这里可以调用API运行指定的健康检查
  ElMessage.success(`已开始运行 ${row.name} 检查`)
}

// 组件挂载时获取数据
onMounted(() => {
  // 这里可以调用API获取真实数据
  console.log('获取健康检查数据')
})
</script>

<style scoped>
.health-check {
  padding: 20px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.header-actions {
  display: flex;
  gap: 10px;
}

.details-card {
  margin-top: 20px;
}
</style>