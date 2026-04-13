<template>
  <div class="tracing-management">
    <el-card>
      <template #header>
        <div class="card-header">
          <h2>追踪配置</h2>
          <div class="header-actions">
            <el-button @click="handleRefresh" :loading="loading">刷新</el-button>
            <el-button type="primary" @click="handleSaveConfig" :loading="saving">保存配置</el-button>
          </div>
        </div>
      </template>

      <el-tabs v-model="activeTab" type="border-card">
        <!-- 状态监控 Tab -->
        <el-tab-pane label="状态监控" name="status">
          <el-row :gutter="24">
            <el-col :xs="24" :lg="12">
              <el-card shadow="never" class="status-card">
                <template #header>
                  <div class="section-header">
                    <span>追踪状态</span>
                    <el-switch
                      v-model="tracingEnabled"
                      active-text="启用"
                      inactive-text="禁用"
                      @change="handleToggleTracing"
                    />
                  </div>
                </template>
                <el-descriptions :column="1" border>
                  <el-descriptions-item label="服务名称">{{ statusInfo.serviceName }}</el-descriptions-item>
                  <el-descriptions-item label="服务版本">{{ statusInfo.serviceVersion }}</el-descriptions-item>
                  <el-descriptions-item label="OpenTelemetry">
                    <el-tag :type="statusInfo.openTelemetryEnabled ? 'success' : 'danger'" size="small">
                      {{ statusInfo.openTelemetryEnabled ? '启用' : '禁用' }}
                    </el-tag>
                  </el-descriptions-item>
                  <el-descriptions-item label="导出器类型">
                    <el-tag size="small">{{ statusInfo.exporterType || 'logging' }}</el-tag>
                  </el-descriptions-item>
                  <el-descriptions-item label="全局采样率">
                    <el-progress
                      :percentage="statusInfo.globalSamplingRatio * 100"
                      :format="(p: number) => `${p}%`"
                    />
                  </el-descriptions-item>
                </el-descriptions>
              </el-card>
            </el-col>
            <el-col :xs="24" :lg="12">
              <el-card shadow="never" class="status-card">
                <template #header>
                  <span>健康状态</span>
                </template>
                <el-descriptions :column="1" border>
                  <el-descriptions-item label="整体状态">
                    <el-tag :type="healthStatus.status === 'UP' ? 'success' : 'danger'">
                      {{ healthStatus.status }}
                    </el-tag>
                  </el-descriptions-item>
                  <el-descriptions-item label="内存使用">
                    <el-progress
                      :percentage="healthStatus.memoryUsage"
                      :status="healthStatus.memoryUsage > 80 ? 'exception' : 'success'"
                    />
                  </el-descriptions-item>
                  <el-descriptions-item label="活跃追踪数">{{ healthStatus.activeTraces }}</el-descriptions-item>
                  <el-descriptions-item label="缓冲区使用">
                    <el-progress
                      :percentage="healthStatus.bufferUsage"
                      :status="healthStatus.bufferUsage > 90 ? 'exception' : 'success'"
                    />
                  </el-descriptions-item>
                </el-descriptions>
              </el-card>
            </el-col>
          </el-row>
        </el-tab-pane>

        <!-- 采样配置 Tab -->
        <el-tab-pane label="采样配置" name="sampling">
          <el-card shadow="never">
            <el-form :model="samplingConfig" label-width="150px">
              <el-form-item label="全局采样率">
                <el-slider
                  v-model="samplingConfig.globalRate"
                  :min="0"
                  :max="100"
                  show-input
                  :marks="{ 0: '0%', 25: '25%', 50: '50%', 75: '75%', 100: '100%' }"
                />
              </el-form-item>
              <el-form-item label="自适应采样">
                <el-switch v-model="samplingConfig.adaptiveSampling" />
              </el-form-item>
              <template v-if="samplingConfig.adaptiveSampling">
                <el-form-item label="始终采样路径">
                  <el-select
                    v-model="samplingConfig.alwaysSamplePaths"
                    multiple
                    filterable
                    allow-create
                    default-first-option
                    placeholder="输入路径后按回车"
                    style="width: 100%"
                  />
                </el-form-item>
                <el-form-item label="从不采样路径">
                  <el-select
                    v-model="samplingConfig.neverSamplePaths"
                    multiple
                    filterable
                    allow-create
                    default-first-option
                    placeholder="输入路径后按回车"
                    style="width: 100%"
                  />
                </el-form-item>
              </template>
            </el-form>

            <el-divider content-position="left">服务特定采样配置</el-divider>

            <el-table :data="samplingConfig.serviceConfigs" style="width: 100%">
              <el-table-column prop="service" label="服务名称" width="200">
                <template #default="{ row }">
                  <el-input v-model="row.service" placeholder="服务名称" />
                </template>
              </el-table-column>
              <el-table-column prop="rate" label="采样率(%)" width="250">
                <template #default="{ row }">
                  <el-slider v-model="row.rate" :min="0" :max="100" show-input />
                </template>
              </el-table-column>
              <el-table-column label="操作" width="100">
                <template #default="{ $index }">
                  <el-button type="danger" text @click="removeServiceConfig($index)">
                    删除
                  </el-button>
                </template>
              </el-table-column>
            </el-table>
            <el-button type="primary" text @click="addServiceConfig" style="margin-top: 12px">
              + 添加服务配置
            </el-button>
          </el-card>
        </el-tab-pane>

        <!-- 导出器配置 Tab -->
        <el-tab-pane label="导出器配置" name="exporter">
          <el-card shadow="never">
            <el-form :model="exporterConfig" label-width="150px">
              <el-form-item label="导出器类型">
                <el-select v-model="exporterConfig.type" style="width: 200px">
                  <el-option label="Logging (日志)" value="logging" />
                  <el-option label="OTLP" value="otlp" />
                  <el-option label="Jaeger" value="jaeger" />
                </el-select>
              </el-form-item>

              <template v-if="exporterConfig.type === 'otlp'">
                <el-form-item label="OTLP Endpoint">
                  <el-input v-model="exporterConfig.otlpEndpoint" placeholder="http://localhost:4317" />
                </el-form-item>
              </template>

              <template v-if="exporterConfig.type === 'jaeger'">
                <el-form-item label="Jaeger Endpoint">
                  <el-input v-model="exporterConfig.jaegerEndpoint" placeholder="http://localhost:14268/api/traces" />
                </el-form-item>
              </template>

              <el-form-item label="日志导出">
                <el-switch v-model="exporterConfig.loggingEnabled" />
              </el-form-item>
            </el-form>
          </el-card>
        </el-tab-pane>

        <!-- 高级配置 Tab -->
        <el-tab-pane label="高级配置" name="advanced">
          <el-card shadow="never">
            <el-form :model="performanceConfig" label-width="150px">
              <el-form-item label="异步处理">
                <el-switch v-model="performanceConfig.asyncProcessing" />
              </el-form-item>
              <el-form-item label="线程池核心大小">
                <el-input-number v-model="performanceConfig.threadPoolCoreSize" :min="1" :max="100" />
              </el-form-item>
              <el-form-item label="缓冲区大小">
                <el-input-number v-model="performanceConfig.bufferSize" :min="100" :max="10000" :step="100" />
              </el-form-item>
              <el-form-item label="内存限制(MB)">
                <el-input-number v-model="performanceConfig.memoryLimitMb" :min="64" :max="2048" :step="64" />
              </el-form-item>
            </el-form>

            <el-divider content-position="left">数据管理</el-divider>

            <div class="action-buttons">
              <el-button type="warning" @click="handleClearCache" :loading="clearingCache">
                清理缓存
              </el-button>
              <el-button type="danger" @click="handleCleanupExpired" :loading="cleaningUp">
                清理过期数据
              </el-button>
              <el-button type="success" @click="handleExportConfig">
                导出配置
              </el-button>
            </div>
          </el-card>
        </el-tab-pane>
      </el-tabs>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getTracingStatus,
  getTracingHealth,
  getTracingConfig,
  updateTracingConfig,
  enableTracing,
  disableTracing,
  refreshTracingData,
  cleanupExpiredTraces,
  refreshSamplingStrategy
} from '@/api/tracing'

// 状态
const loading = ref(false)
const saving = ref(false)
const clearingCache = ref(false)
const cleaningUp = ref(false)
const activeTab = ref('status')
const tracingEnabled = ref(true)

// 状态信息
const statusInfo = ref({
  serviceName: '',
  serviceVersion: '',
  openTelemetryEnabled: false,
  exporterType: 'logging',
  globalSamplingRatio: 0.1
})

// 健康状态
const healthStatus = ref({
  status: 'UP',
  memoryUsage: 0,
  activeTraces: 0,
  bufferUsage: 0
})

// 采样配置
const samplingConfig = ref({
  globalRate: 10,
  adaptiveSampling: true,
  alwaysSamplePaths: [] as string[],
  neverSamplePaths: [] as string[],
  serviceConfigs: [
    { service: 'chat', rate: 20 },
    { service: 'embedding', rate: 15 },
    { service: 'rerank', rate: 10 }
  ]
})

// 导出器配置
const exporterConfig = ref({
  type: 'logging',
  otlpEndpoint: '',
  jaegerEndpoint: '',
  loggingEnabled: true
})

// 性能配置
const performanceConfig = ref({
  asyncProcessing: true,
  threadPoolCoreSize: 10,
  bufferSize: 1000,
  memoryLimitMb: 512
})

// 加载数据
const loadAllData = async () => {
  loading.value = true
  try {
    await Promise.all([
      loadStatus(),
      loadHealth(),
      loadConfig()
    ])
  } catch (error) {
    console.error('加载数据失败:', error)
  } finally {
    loading.value = false
  }
}

const loadStatus = async () => {
  try {
    const response = await getTracingStatus()
    const data = response.data?.data || response.data || response
    if (data) {
      statusInfo.value = {
        serviceName: data.serviceName || 'jairouter',
        serviceVersion: data.serviceVersion || '1.0.0',
        openTelemetryEnabled: data.openTelemetry?.enabled ?? true,
        exporterType: data.exporter?.type || 'logging',
        globalSamplingRatio: data.sampling?.globalRatio || 0.1
      }
      tracingEnabled.value = data.enabled !== false
    }
  } catch (error) {
    console.error('加载状态失败:', error)
  }
}

const loadHealth = async () => {
  try {
    const response = await getTracingHealth()
    const data = response.data?.data || response.data || response
    if (data) {
      healthStatus.value = {
        status: data.status || 'UP',
        memoryUsage: Math.round((data.memoryUsage || 0) * 100),
        activeTraces: data.activeTraces || 0,
        bufferUsage: Math.round((data.bufferUsage || 0) * 100)
      }
    }
  } catch (error) {
    console.error('加载健康状态失败:', error)
  }
}

const loadConfig = async () => {
  try {
    const response = await getTracingConfig()
    const data = response.data?.data || response.data || response
    if (data) {
      if (data.sampling) {
        samplingConfig.value.globalRate = (data.sampling.ratio || 0.1) * 100
        samplingConfig.value.adaptiveSampling = data.sampling.adaptive?.enabled ?? true
        samplingConfig.value.alwaysSamplePaths = data.sampling.alwaysSample || []
        samplingConfig.value.neverSamplePaths = data.sampling.neverSample || []
        if (data.sampling.serviceRatios) {
          samplingConfig.value.serviceConfigs = Object.entries(data.sampling.serviceRatios).map(
            ([service, rate]) => ({ service, rate: (rate as number) * 100 })
          )
        }
      }
      if (data.exporter) {
        exporterConfig.value.type = data.exporter.type || 'logging'
        exporterConfig.value.loggingEnabled = data.exporter.logging?.enabled ?? true
      }
      if (data.performance) {
        performanceConfig.value.asyncProcessing = data.performance.asyncProcessing ?? true
        performanceConfig.value.threadPoolCoreSize = data.performance.threadPool?.coreSize || 10
        performanceConfig.value.bufferSize = data.performance.buffer?.size || 1000
        performanceConfig.value.memoryLimitMb = data.performance.memory?.memoryLimitMb || 512
      }
    }
  } catch (error) {
    console.error('加载配置失败:', error)
  }
}

// 事件处理
const handleRefresh = () => {
  loadAllData()
}

const handleToggleTracing = async (enabled: boolean) => {
  try {
    if (enabled) {
      await enableTracing()
      ElMessage.success('追踪已启用')
    } else {
      await disableTracing()
      ElMessage.success('追踪已禁用')
    }
  } catch (error) {
    console.error('切换追踪状态失败:', error)
    ElMessage.error('操作失败')
    tracingEnabled.value = !enabled
  }
}

const handleSaveConfig = async () => {
  saving.value = true
  try {
    const config = {
      sampling: {
        ratio: samplingConfig.value.globalRate / 100,
        adaptive: { enabled: samplingConfig.value.adaptiveSampling },
        alwaysSample: samplingConfig.value.alwaysSamplePaths,
        neverSample: samplingConfig.value.neverSamplePaths,
        serviceRatios: samplingConfig.value.serviceConfigs.reduce((acc, { service, rate }) => {
          acc[service] = rate / 100
          return acc
        }, {} as Record<string, number>)
      },
      exporter: {
        type: exporterConfig.value.type,
        logging: { enabled: exporterConfig.value.loggingEnabled }
      },
      performance: {
        asyncProcessing: performanceConfig.value.asyncProcessing,
        threadPool: { coreSize: performanceConfig.value.threadPoolCoreSize },
        buffer: { size: performanceConfig.value.bufferSize },
        memory: { memoryLimitMb: performanceConfig.value.memoryLimitMb }
      }
    }

    await updateTracingConfig(config)
    await refreshSamplingStrategy()
    ElMessage.success('配置保存成功')
  } catch (error) {
    console.error('保存配置失败:', error)
    ElMessage.error('保存失败')
  } finally {
    saving.value = false
  }
}

const handleClearCache = async () => {
  clearingCache.value = true
  try {
    await refreshTracingData()
    ElMessage.success('缓存清理成功')
  } catch (error) {
    ElMessage.error('清理失败')
  } finally {
    clearingCache.value = false
  }
}

const handleCleanupExpired = async () => {
  try {
    const { value } = await ElMessageBox.prompt('请输入保留时间（小时）', '清理过期数据', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      inputValue: '24',
      inputValidator: (v) => !isNaN(parseInt(v)) && parseInt(v) > 0 || '请输入有效的小时数'
    })

    cleaningUp.value = true
    const hours = parseInt(value)
    const response = await cleanupExpiredTraces(hours)
    const removed = response.data?.data?.removedCount || 0
    ElMessage.success(`已清理 ${removed} 条过期数据`)
  } catch (e) {
    if (e !== 'cancel') {
      ElMessage.error('清理失败')
    }
  } finally {
    cleaningUp.value = false
  }
}

const handleExportConfig = () => {
  const config = {
    sampling: samplingConfig.value,
    exporter: exporterConfig.value,
    performance: performanceConfig.value
  }
  const dataStr = JSON.stringify(config, null, 2)
  const blob = new Blob([dataStr], { type: 'application/json' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = `tracing-config-${new Date().toISOString().slice(0, 10)}.json`
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  URL.revokeObjectURL(url)
  ElMessage.success('配置已导出')
}

const addServiceConfig = () => {
  samplingConfig.value.serviceConfigs.push({ service: '', rate: 10 })
}

const removeServiceConfig = (index: number) => {
  samplingConfig.value.serviceConfigs.splice(index, 1)
}

onMounted(() => {
  loadAllData()
})
</script>

<style scoped>
.tracing-management {
  padding: 0;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.card-header h2 {
  margin: 0;
  font-size: 18px;
}

.header-actions {
  display: flex;
  gap: 12px;
}

.status-card {
  margin-bottom: 16px;
}

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.action-buttons {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}

@media (max-width: 768px) {
  .card-header {
    flex-direction: column;
    align-items: flex-start;
    gap: 12px;
  }
}
</style>