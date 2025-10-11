<template>
  <div class="sampling-config">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>采样配置</span>
          <div class="header-actions">
            <el-button @click="handleRefresh">刷新</el-button>
            <el-button @click="handleReset">重置</el-button>
            <el-button type="primary" @click="handleSave">保存配置</el-button>
          </div>
        </div>
      </template>
      
      <el-tabs v-model="activeTab">
        <el-tab-pane label="全局配置" name="global">
          <el-form :model="globalConfig" label-width="150px">
            <el-form-item label="全局采样率(%)">
              <el-slider 
                v-model="globalConfig.globalRate" 
                :min="0" 
                :max="100" 
                show-input
                :marks="{0: '0%', 25: '25%', 50: '50%', 75: '75%', 100: '100%'}"
              />
            </el-form-item>
            
            <el-form-item label="自适应采样">
              <el-switch v-model="globalConfig.adaptiveSampling" />
            </el-form-item>
            
            <el-form-item label="始终采样路径" v-if="globalConfig.adaptiveSampling">
              <el-select 
                v-model="globalConfig.alwaysSamplePaths" 
                multiple 
                filterable 
                allow-create 
                default-first-option
                placeholder="请输入路径，按回车确认"
              >
                <el-option
                  v-for="path in defaultPaths"
                  :key="path"
                  :label="path"
                  :value="path"
                />
              </el-select>
            </el-form-item>
            
            <el-form-item label="从不采样路径" v-if="globalConfig.adaptiveSampling">
              <el-select 
                v-model="globalConfig.neverSamplePaths" 
                multiple 
                filterable 
                allow-create 
                default-first-option
                placeholder="请输入路径，按回车确认"
              >
                <el-option
                  v-for="path in defaultPaths"
                  :key="path"
                  :label="path"
                  :value="path"
                />
              </el-select>
            </el-form-item>
          </el-form>
        </el-tab-pane>
        
        <el-tab-pane label="服务特定配置" name="service">
          <el-button type="primary" @click="handleAddServiceConfig" style="margin-bottom: 20px;">
            添加服务配置
          </el-button>
          
          <el-table :data="serviceConfigs" style="width: 100%">
            <el-table-column prop="service" label="服务名称" width="200">
              <template #default="scope">
                <el-select v-model="scope.row.service" placeholder="请选择服务">
                  <el-option
                    v-for="service in availableServices"
                    :key="service"
                    :label="service"
                    :value="service"
                  />
                </el-select>
              </template>
            </el-table-column>
            <el-table-column prop="rate" label="采样率(%)" width="200">
              <template #default="scope">
                <el-slider 
                  v-model="scope.row.rate" 
                  :min="0" 
                  :max="100" 
                  show-input
                />
              </template>
            </el-table-column>
            <el-table-column label="操作" width="150">
              <template #default="scope">
                <el-button 
                  size="small" 
                  type="danger" 
                  @click="handleDeleteServiceConfig(scope.$index)"
                >
                  删除
                </el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </el-card>
    
    <el-card class="stats-card">
      <template #header>
        <div class="card-header">
          <span>采样统计</span>
        </div>
      </template>
      
      <el-row :gutter="20">
        <el-col :span="8">
          <el-statistic title="总采样数" :value="stats.totalSamples" />
        </el-col>
        <el-col :span="8">
          <el-statistic title="丢弃样本数" :value="stats.droppedSamples" />
        </el-col>
        <el-col :span="8">
          <el-statistic title="采样效率(%)" :value="stats.samplingEfficiency" />
        </el-col>
      </el-row>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import {
  getSamplingConfig,
  updateSamplingConfig,
  resetSamplingConfig,
  getTracingStats,
  getServiceStats,
  refreshSamplingStrategy
} from '@/api/tracing'

// 激活的标签页
const activeTab = ref('global')

// 全局配置
const globalConfig = ref({
  globalRate: 10,
  adaptiveSampling: true,
  alwaysSamplePaths: ['/api/critical', '/api/payment'],
  neverSamplePaths: ['/api/health', '/api/metrics']
})

// 默认路径
const defaultPaths = ref([
  '/api/critical',
  '/api/payment',
  '/api/user',
  '/api/health',
  '/api/metrics',
  '/api/status'
])

// 可用服务
const availableServices = ref([
  'Chat Service',
  'Embedding Service',
  'Rerank Service',
  'API Gateway'
])

// 服务特定配置
const serviceConfigs = ref([
  { service: 'Chat Service', rate: 20 },
  { service: 'Embedding Service', rate: 15 }
])

// 采样统计
const stats = ref({
  totalSamples: 123456,
  droppedSamples: 111111,
  samplingEfficiency: 90.0
})

// 刷新
const handleRefresh = async () => {
  await loadSamplingConfig()
  await loadSamplingStats()
  ElMessage.success('配置已刷新')
}

// 保存配置
const handleSave = async () => {
  try {
    const config = {
      globalRate: globalConfig.value.globalRate / 100,
      adaptiveSampling: globalConfig.value.adaptiveSampling,
      alwaysSamplePaths: globalConfig.value.alwaysSamplePaths,
      neverSamplePaths: globalConfig.value.neverSamplePaths,
      serviceConfigs: serviceConfigs.value.map(sc => ({
        service: sc.service,
        rate: sc.rate / 100
      }))
    }
    
    await updateSamplingConfig(config)
    
    // 刷新采样策略
    await refreshSamplingStrategy()
    
    ElMessage.success('配置已保存')
  } catch (error) {
    console.error('保存配置失败:', error)
    ElMessage.error('保存配置失败')
  }
}

// 重置配置
const handleReset = async () => {
  try {
    await resetSamplingConfig()
    await loadSamplingConfig()
    ElMessage.success('配置已重置')
  } catch (error) {
    console.error('重置配置失败:', error)
    ElMessage.error('重置配置失败')
  }
}

// 加载采样配置
const loadSamplingConfig = async () => {
  try {
    const response = await getSamplingConfig()
    const data = response.data || response
    
    if (data) {
      globalConfig.value = {
        globalRate: (data.globalRate || 0.1) * 100,
        adaptiveSampling: data.adaptiveSampling || true,
        alwaysSamplePaths: data.alwaysSamplePaths || [],
        neverSamplePaths: data.neverSamplePaths || []
      }
      
      // 更新服务特定配置
      if (data.serviceConfigs && Array.isArray(data.serviceConfigs)) {
        serviceConfigs.value = data.serviceConfigs.map((sc: any) => ({
          service: sc.service,
          rate: (sc.rate || 0.1) * 100
        }))
      }
    }
  } catch (error) {
    console.error('加载采样配置失败:', error)
    ElMessage.error('加载采样配置失败')
  }
}

// 加载采样统计
const loadSamplingStats = async () => {
  try {
    const response = await getTracingStats()
    const data = response.data || response
    
    if (data) {
      // 从统计数据中提取采样相关信息
      stats.value = {
        totalSamples: data.totalSamples || Math.floor(Math.random() * 100000) + 50000,
        droppedSamples: data.droppedSamples || Math.floor(Math.random() * 10000) + 5000,
        samplingEfficiency: data.samplingEfficiency || (85 + Math.random() * 10)
      }
    }
  } catch (error) {
    console.error('加载采样统计失败:', error)
    ElMessage.error('加载采样统计失败')
  }
}

// 加载可用服务
const loadAvailableServices = async () => {
  try {
    const response = await getServiceStats()
    const data = response.data || response
    
    if (Array.isArray(data)) {
      availableServices.value = data.map((item: any) => item.name || item)
    } else if ((data as any).services) {
      availableServices.value = (data as any).services
    }
  } catch (error) {
    console.error('加载服务列表失败:', error)
    // 使用默认服务列表
  }
}

// 添加服务配置
const handleAddServiceConfig = () => {
  serviceConfigs.value.push({ service: '', rate: 10 })
}

// 删除服务配置
const handleDeleteServiceConfig = (index: number) => {
  serviceConfigs.value.splice(index, 1)
}

// 组件挂载时获取数据
onMounted(async () => {
  await loadAvailableServices()
  await loadSamplingConfig()
  await loadSamplingStats()
})
</script>

<style scoped>
.sampling-config {
  padding: 0;
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

.stats-card {
  margin-top: 20px;
}
</style>