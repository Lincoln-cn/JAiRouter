<template>
  <div class="sampling-config">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>采样配置</span>
          <div class="header-actions">
            <el-button @click="handleRefresh">刷新</el-button>
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
const handleRefresh = () => {
  // 这里可以调用API获取最新配置
  ElMessage.success('配置已刷新')
}

// 保存配置
const handleSave = () => {
  // 这里可以调用API保存配置
  ElMessage.success('配置已保存')
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
onMounted(() => {
  // 这里可以调用API获取真实数据
  console.log('获取采样配置数据')
})
</script>

<style scoped>
.sampling-config {
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

.stats-card {
  margin-top: 20px;
}
</style>