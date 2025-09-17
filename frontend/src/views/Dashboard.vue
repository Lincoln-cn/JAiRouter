<template>
  <div class="dashboard">
    <el-row :gutter="16" class="stats-row">
      <el-col :span="24" :sm="12" :md="8" :lg="4">
        <el-card class="stat-card">
          <div class="stat-content">
            <div class="stat-icon success">
              <el-icon><Flag /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-value">{{ stats.serviceCount }}</div>
              <div class="stat-label">服务数量</div>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="24" :sm="12" :md="8" :lg="4">
        <el-card class="stat-card">
          <div class="stat-content">
            <div class="stat-icon info">
              <el-icon><Cpu /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-value">{{ stats.instanceCount }}</div>
              <div class="stat-label">实例数量</div>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="24" :sm="12" :md="8" :lg="4">
        <el-card class="stat-card">
          <div class="stat-content">
            <div class="stat-icon info">
              <el-icon><Monitor /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-value">{{ stats.totalModels }}</div>
              <div class="stat-label">模型数量</div>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="24" :sm="12" :md="8" :lg="4">
        <el-card class="stat-card">
          <div class="stat-content">
            <div class="stat-icon success">
              <el-icon><Check /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-value">{{ stats.healthyInstanceCount }}</div>
              <div class="stat-label">健康实例</div>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="24" :sm="12" :md="8" :lg="4">
        <el-card class="stat-card">
          <div class="stat-content">
            <div class="stat-icon danger">
              <el-icon><Warning /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-value">{{ stats.errorInstanceCount }}</div>
              <div class="stat-label">异常实例</div>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="24" :sm="12" :md="8" :lg="4">
        <el-card class="stat-card">
          <div class="stat-content">
            <div class="stat-icon info">
              <el-icon><DataLine /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-value">{{ stats.totalModels + stats.instanceCount }}</div>
              <div class="stat-label">资源总数</div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>
    
  
    
    <el-row :gutter="20" class="charts-row">
      <el-col :span="24" :lg="16">
        <el-card>
          <template #header>
            <div class="card-header">
              <span>系统概览</span>
            </div>
          </template>
          <div ref="systemChart" class="chart-container"></div>
        </el-card>
      </el-col>
      <el-col :span="24" :lg="8">
        <el-card>
          <template #header>
            <div class="card-header">
              <span>最近活动</span>
            </div>
          </template>
          <el-timeline>
            <el-timeline-item
              v-for="(activity, index) in recentActivities"
              :key="index"
              :timestamp="activity.timestamp"
              :type="activity.type"
            >
              {{ activity.content }}
            </el-timeline-item>
            <div v-if="recentActivities.length === 0" class="empty-placeholder">
              <el-icon><Calendar /></el-icon>
              <p>暂无活动记录</p>
            </div>
          </el-timeline>
        </el-card>
      </el-col>
    </el-row>

    <!-- 服务配置详情 -->
    <el-row :gutter="20" class="config-row" v-if="serviceConfigData">
      <el-col :span="24">
        <el-card>
          <template #header>
            <div class="card-header" style="display: flex; justify-content: space-between; align-items: center;">
              <span>服务配置</span>
              <el-button @click="fetchServiceConfig" :loading="configLoading">
                <el-icon><Refresh /></el-icon>
                刷新配置
              </el-button>
            </div>
          </template>
          <el-tabs type="border-card" style="margin-top: 20px;">
            <el-tab-pane label="全局配置">
              <el-descriptions :column="1" border>
                <el-descriptions-item label="适配器">{{ serviceConfigData.adapter || 'N/A' }}</el-descriptions-item>
                <el-descriptions-item label="负载均衡策略">{{ serviceConfigData.loadBalance?.type || 'N/A' }}</el-descriptions-item>
                <el-descriptions-item label="哈希算法">{{ serviceConfigData.loadBalance?.hashAlgorithm || 'N/A' }}</el-descriptions-item>
                <el-descriptions-item label="全局限流">
                  <el-tag :type="serviceConfigData.rateLimit?.enabled ? 'success' : 'info'">
                    {{ serviceConfigData.rateLimit?.enabled ? '启用' : '禁用' }}
                  </el-tag>
                </el-descriptions-item>
                <el-descriptions-item label="限流算法" v-if="serviceConfigData.rateLimit?.enabled">
                  {{ serviceConfigData.rateLimit?.algorithm || 'N/A' }}
                </el-descriptions-item>
                <el-descriptions-item label="限流速率" v-if="serviceConfigData.rateLimit?.enabled">
                  {{ serviceConfigData.rateLimit?.rate || 'N/A' }} 请求/秒
                </el-descriptions-item>
                <el-descriptions-item label="熔断器">
                  <el-tag :type="serviceConfigData.circuitBreaker?.enabled ? 'success' : 'info'">
                    {{ serviceConfigData.circuitBreaker?.enabled ? '启用' : '禁用' }}
                  </el-tag>
                </el-descriptions-item>
                <el-descriptions-item label="降级策略" v-if="serviceConfigData.fallback?.enabled">
                  {{ serviceConfigData.fallback?.strategy || 'N/A' }}
                </el-descriptions-item>
              </el-descriptions>
            </el-tab-pane>
          </el-tabs>
          
          <!-- 服务实例列表 -->
          <div class="instances-section" v-if="serviceConfigData.services">
            <h3>服务实例详情</h3>
            <el-tabs v-model="activeServiceTab" type="card">
              <el-tab-pane 
                v-for="serviceName in orderedServiceNames" 
                :key="serviceName"
                :label="getServiceTypeName(serviceName)"
                :name="serviceName"
              >
                <el-table :data="serviceConfigData.services[serviceName]?.instances || []" style="width: 100%">
                  <el-table-column prop="name" label="实例名称" />
                  <el-table-column prop="baseUrl" label="基础URL" />
                  <el-table-column label="适配器">
                    <template #default="scope">
                      {{ scope.row.adapter || serviceConfigData.adapter || 'N/A' }}
                    </template>
                  </el-table-column>
                  <el-table-column prop="weight" label="权重" />
                  <el-table-column prop="path" label="路径" />
                  <el-table-column label="健康状态">
                    <template #default="scope">
                      <el-tag :type="scope.row.health ? 'success' : 'danger'">
                        {{ scope.row.health ? '健康' : '异常' }}
                      </el-tag>
                    </template>
                  </el-table-column>
                </el-table>
              </el-tab-pane>
              <!-- 其他服务实例标签页 -->
              <el-tab-pane 
                v-if="otherServiceNames.length > 0"
                key="other"
                label="其他"
                name="other"
              >
                <el-table :data="otherServiceInstances" style="width: 100%">
                  <el-table-column prop="serviceName" label="服务类型" />
                  <el-table-column prop="name" label="实例名称" />
                  <el-table-column prop="baseUrl" label="基础URL" />
                  <el-table-column label="适配器">
                    <template #default="scope">
                      {{ scope.row.adapter || serviceConfigData.adapter || 'N/A' }}
                    </template>
                  </el-table-column>
                  <el-table-column prop="weight" label="权重" />
                  <el-table-column prop="path" label="路径" />
                  <el-table-column label="健康状态">
                    <template #default="scope">
                      <el-tag :type="scope.row.health ? 'success' : 'danger'">
                        {{ scope.row.health ? '健康' : '异常' }}
                      </el-tag>
                    </template>
                  </el-table-column>
                </el-table>
              </el-tab-pane>
            </el-tabs>
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount, computed } from 'vue'
import * as echarts from 'echarts'
import { getServiceStats, getSystemHealth, getAllServiceConfig } from '@/api/dashboard'
import { ElMessage } from 'element-plus'

// 统计数据
const stats = ref({
  serviceCount: 0,
  instanceCount: 0,
  totalModels: 0,
  alertCount: 0,
  userCount: 0,
  healthyInstanceCount: 0,
  errorInstanceCount: 0 // 新增错误实例数量
})

// 激活的服务标签页
const activeServiceTab = ref<string>('chat')

// 服务配置数据
const serviceConfigData = ref<any>(null)

// 配置加载状态
const configLoading = ref(false)

// 服务类型中英文映射
const serviceTypeMap: Record<string, string> = {
  'chat': '聊天服务',
  'embedding': '嵌入服务',
  'rerank': '重排序服务',
  'tts': '文本转语音服务',
  'stt': '语音转文本服务',
  'imgGen': '图像生成服务',
  'imgEdit': '图像编辑服务'
}

// 获取服务类型的中文名称
const getServiceTypeName = (type: string) => {
  return serviceTypeMap[type] || type
}

// 计算属性：按指定顺序排列的服务名称
const orderedServiceNames = computed(() => {
  if (!serviceConfigData.value?.services) return []
  
  const serviceNames = Object.keys(serviceConfigData.value.services)
  const orderedNames: string[] = []
  
  // 按照serviceTypeMap的顺序排列
  Object.keys(serviceTypeMap).forEach(serviceType => {
    if (serviceNames.includes(serviceType)) {
      orderedNames.push(serviceType)
    }
  })
  
  // 添加不在serviceTypeMap中的服务到"其他"分类
  const otherNames: string[] = []
  serviceNames.forEach(serviceName => {
    if (!orderedNames.includes(serviceName)) {
      otherNames.push(serviceName)
    }
  })
  
  return orderedNames
})

// 计算属性：其他服务名称列表
const otherServiceNames = computed(() => {
  if (!serviceConfigData.value?.services) return []
  
  const serviceNames = Object.keys(serviceConfigData.value.services)
  const otherNames: string[] = []
  
  serviceNames.forEach(serviceName => {
    if (!Object.keys(serviceTypeMap).includes(serviceName)) {
      otherNames.push(serviceName)
    }
  })
  
  return otherNames
})

// 计算属性：其他服务实例列表
const otherServiceInstances = computed(() => {
  if (!serviceConfigData.value?.services || otherServiceNames.value.length === 0) return []
  
  const instances: any[] = []
  otherServiceNames.value.forEach(serviceName => {
    const service = serviceConfigData.value.services[serviceName]
    if (service && service.instances) {
      service.instances.forEach((instance: any) => {
        instances.push({
          ...instance,
          serviceName: getServiceTypeName(serviceName)
        })
      })
    }
  })
  
  return instances
})

// 计算属性：健康实例数量
const healthyInstanceCount = computed(() => {
  if (!serviceConfigData.value?.services) return 0
  
  let count = 0
  Object.values(serviceConfigData.value.services).forEach((service: any) => {
    if (service.instances) {
      service.instances.forEach((instance: any) => {
        if (instance.health) {
          count++
        }
      })
    }
  })
  
  // 包括其他服务实例
  otherServiceInstances.value.forEach((instance: any) => {
    if (instance.health) {
      count++
    }
  })
  
  return count
})

// 计算属性：异常实例数量
const errorInstanceCount = computed(() => {
  if (!serviceConfigData.value?.services) return 0
  
  let count = 0
  Object.values(serviceConfigData.value.services).forEach((service: any) => {
    if (service.instances) {
      service.instances.forEach((instance: any) => {
        if (!instance.health) {
          count++
        }
      })
    }
  })
  
  // 包括其他服务实例
  otherServiceInstances.value.forEach((instance: any) => {
    if (!instance.health) {
      count++
    }
  })
  
  return count
})

// 最近活动
const recentActivities = ref([
  { content: '添加了新的聊天服务实例', timestamp: '2023-10-01 10:30:00', type: 'primary' },
  { content: '更新了嵌入服务配置', timestamp: '2023-10-01 09:45:00', type: 'success' },
  { content: 'Rerank服务出现异常', timestamp: '2023-10-01 09:15:00', type: 'warning' },
  { content: '创建了新的API密钥', timestamp: '2023-10-01 08:30:00', type: 'info' }
])

const systemChart = ref<HTMLElement | null>(null)
let systemChartInstance: echarts.ECharts | null = null

const initChart = () => {
  if (systemChart.value) {
    systemChartInstance = echarts.init(systemChart.value)
    systemChartInstance.setOption(getChartOption())
  }
}

const getChartOption = () => ({
  tooltip: { trigger: 'axis' },
  legend: { data: ['请求数', '错误数'] },
  xAxis: { type: 'category', data: ['00:00', '04:00', '08:00', '12:00', '16:00', '20:00', '24:00'] },
  yAxis: { type: 'value' },
  series: [
    { name: '请求数', data: [120, 200, 150, 80, 70, 110, 130], type: 'line', smooth: true },
    { name: '错误数', data: [2, 5, 3, 1, 2, 4, 3], type: 'line', smooth: true, itemStyle: { color: '#f56c6c' } }
  ]
})

const handleResize = () => systemChartInstance?.resize()

// 获取服务配置数据
const fetchServiceConfig = async () => {
  try {
    configLoading.value = true
    const response = await getAllServiceConfig()
    if (response.data.success) {
      serviceConfigData.value = response.data.data
      // 更新统计信息
      if (serviceConfigData.value) {
        let totalServices = 0
        let totalInstances = 0
        let totalModels = 0
        
        if (serviceConfigData.value.services) {
          totalServices = Object.keys(serviceConfigData.value.services).length
          
          Object.values(serviceConfigData.value.services).forEach((service: any) => {
            if (service.instances) {
              totalInstances += service.instances.length
            }
          })
        }
        
        if (serviceConfigData.value.models) {
          totalModels = serviceConfigData.value.models.length
        }
        
        stats.value.serviceCount = totalServices
        stats.value.instanceCount = totalInstances
        stats.value.totalModels = totalModels
        stats.value.healthyInstanceCount = healthyInstanceCount.value
        stats.value.errorInstanceCount = errorInstanceCount.value
      }
      
      // 设置默认激活的标签页
      if (serviceConfigData.value.services) {
        const serviceNames = Object.keys(serviceConfigData.value.services)
        if (serviceNames.length > 0) {
          // 使用orderedServiceNames的第一个作为默认激活标签
          const orderedNames = orderedServiceNames.value
          if (orderedNames.length > 0) {
            activeServiceTab.value = orderedNames[0]
          } else {
            activeServiceTab.value = serviceNames[0]
          }
        }
      }
      
      // 如果服务配置中的adapter为空，则使用全局的adapter值
      if (serviceConfigData.value.services) {
        Object.keys(serviceConfigData.value.services).forEach(serviceName => {
          const service = serviceConfigData.value.services[serviceName]
          if (!service.adapter) {
            service.adapter = serviceConfigData.value.adapter
          }
        })
      }
      
      ElMessage.success('服务配置获取成功')
    } else {
      ElMessage.error('获取服务配置失败: ' + (response.data.message || '未知错误'))
    }
  } catch (error: any) {
    ElMessage.error('获取服务配置失败: ' + (error.message || '网络错误'))
  } finally {
    configLoading.value = false
  }
}

const fetchDashboardData = async () => {
  try {
    // 服务统计
    const statsResponse = await getServiceStats()
    if (statsResponse.data.success) {
      const data = statsResponse.data.data as {
        totalServices?: number; totalInstances?: number; totalModels?: number;
        alertCount?: number; userCount?: number; errorInstanceCount?: number;
      }
      stats.value.serviceCount = data?.totalServices || 0
      stats.value.instanceCount = data?.totalInstances || 0
      stats.value.totalModels = data?.totalModels || 0
      stats.value.alertCount = data?.alertCount || 0
      stats.value.userCount = data?.userCount || 0
      // 健康和异常实例数量将从服务配置中计算，这里暂时保留原始逻辑
      stats.value.healthyInstanceCount = (data?.totalInstances || 0) - (data?.errorInstanceCount || 0)
      stats.value.errorInstanceCount = data?.errorInstanceCount || 0
    } else {
      ElMessage.error('获取服务统计信息失败: ' + (statsResponse.data.message || '未知错误'))
    }
    
    // 获取服务配置数据
    await fetchServiceConfig()
    
    // 更新健康和异常实例数量
    stats.value.healthyInstanceCount = healthyInstanceCount.value
    stats.value.errorInstanceCount = errorInstanceCount.value
    
    // 系统健康
    try {
      const healthResponse = await getSystemHealth()
      if (healthResponse.data && healthResponse.data.status === 'DOWN') {
        stats.value.alertCount = (stats.value.alertCount || 0) + 1
      }
    } catch (error: any) {
      ElMessage.error('获取系统健康状态失败: ' + (error.message || '网络错误'))
    }
  } catch (error: any) {
    ElMessage.error('获取Dashboard数据失败: ' + (error.message || '网络错误'))
  }
}

onMounted(() => {
  initChart()
  window.addEventListener('resize', handleResize)
  fetchDashboardData()
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  systemChartInstance?.dispose()
})
</script>

<style scoped>
.dashboard {
  padding: 20px;
}

.stats-row {
  margin-bottom: 20px;
}

.stat-card {
  height: 100%;
  min-height: 120px;
  display: flex;
  flex-direction: column;
  transition: transform 0.3s ease, box-shadow 0.3s ease;
  border-radius: 8px;
  overflow: hidden;
  background: linear-gradient(135deg, #ffffff 0%, #f8f9fa 100%);
  border: 1px solid #ebeef5;
}

.stat-card:hover {
  transform: translateY(-5px);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
}

.stat-content {
  display: flex;
  align-items: center;
  height: 100%;
  flex: 1;
  padding: 20px;
}

.stat-icon {
  width: 60px;
  height: 60px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-right: 20px;
  font-size: 24px;
  flex-shrink: 0;
  transition: transform 0.3s ease;
  box-shadow: 0 2px 6px rgba(0, 0, 0, 0.1);
}

.stat-card:hover .stat-icon {
  transform: scale(1.1);
}

.stat-info {
  flex: 1;
  overflow: hidden;
}

.stat-value {
  font-size: 24px;
  font-weight: bold;
  margin-bottom: 5px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  background: linear-gradient(90deg, #666666 0%, #333333 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

.stat-label {
  font-size: 14px;
  color: #909399;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

@media (max-width: 1200px) {
  .stat-card {
    min-height: 110px;
  }
  
  .stat-content {
    padding: 15px;
  }
  
  .stat-icon {
    width: 55px;
    height: 55px;
    font-size: 22px;
    margin-right: 15px;
  }
  
  .stat-value {
    font-size: 22px;
  }
}

@media (max-width: 992px) {
  .stat-card {
    min-height: 100px;
  }
  
  .stat-content {
    padding: 12px;
  }
  
  .stat-icon {
    width: 50px;
    height: 50px;
    font-size: 20px;
    margin-right: 12px;
  }
  
  .stat-value {
    font-size: 20px;
  }
  
  .stat-label {
    font-size: 12px;
  }
}

@media (max-width: 768px) {
  .dashboard {
    padding: 10px;
  }
  
  .stats-row {
    margin-bottom: 10px;
  }
  
  .stat-card {
    min-height: 100px;
    margin-bottom: 10px;
  }
  
  .stat-icon {
    width: 50px;
    height: 50px;
    font-size: 20px;
    margin-right: 15px;
  }
  
  .stat-value {
    font-size: 20px;
  }
  
  .stat-label {
    font-size: 14px;
    color: #909399;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }

  .charts-row, .services-row {
    margin-top: 10px;
  }
  
  .chart-container {
    height: 250px;
  }
  
  .card-header {
    flex-direction: column;
    align-items: flex-start;
  }
  
  .service-filter {
    width: 100%;
    margin-top: 10px;
  }
  
  .service-filter .el-select {
    width: 100% !important;
  }
  
  .service-filter .el-button {
    width: 100%;
    margin-top: 10px;
  }
  
  .el-timeline-item {
    margin-bottom: 10px;
  }
  
  .el-timeline-item__timestamp {
    font-size: 12px;
  }
  
  .el-timeline-item__content {
    font-size: 14px;
  }
  
  .pagination-container {
    justify-content: center;
    padding: 10px 0;
    margin-top: 10px;
  }
  
  .column-header {
    align-items: center;
  }
  
  .column-filter {
    margin-top: 8px;
    width: 100%;
  }
}

@media (max-width: 576px) {
  .stat-card {
    min-height: 90px;
  }
  
  .stat-icon {
    width: 45px;
    height: 45px;
    font-size: 18px;
    margin-right: 10px;
  }
  
  .stat-value {
    font-size: 18px;
  }
  
  .stat-label {
    font-size: 11px;
  }
  
  .chart-container {
    height: 200px;
  }
  
  .el-timeline-item__timestamp {
    font-size: 11px;
  }
  
  .el-timeline-item__content {
    font-size: 13px;
  }
}

@media (max-width: 400px) {
  .chart-container {
    height: 150px;
  }
  
  .stat-content {
    padding: 10px;
  }
  
  .stat-icon {
    width: 40px;
    height: 40px;
    font-size: 16px;
    margin-right: 10px;
  }
  
  .stat-value {
    font-size: 16px;
  }
  
  .stat-label {
    font-size: 10px;
  }
  
  .service-filter .el-button {
    padding: 8px 12px;
    font-size: 12px;
  }
  
  .column-filter .el-input__inner,
  .column-filter .el-select__inner {
    font-size: 12px;
    padding: 5px 8px;
  }
}

@media (max-width: 320px) {
  .dashboard {
    padding: 5px;
  }
  
  .stat-card {
    min-height: 80px;
  }
  
  .stat-content {
    padding: 8px;
  }
  
  .stat-icon {
    width: 35px;
    height: 35px;
    font-size: 14px;
    margin-right: 8px;
  }
  
  .stat-value {
    font-size: 14px;
  }
  
  .stat-label {
    font-size: 9px;
  }
  
  .chart-container {
    height: 120px;
  }
  
  .service-filter .el-button {
    padding: 6px 10px;
    font-size: 11px;
  }
  
  .el-table th, 
  .el-table td {
    padding: 6px 0;
    font-size: 12px;
  }
}

/* 新增样式 */
.config-row {
  margin-top: 20px;
}

.instances-section {
  margin-top: 20px;
}

.instances-section h3 {
  margin-bottom: 15px;
  font-size: 16px;
  font-weight: 600;
}
</style>
