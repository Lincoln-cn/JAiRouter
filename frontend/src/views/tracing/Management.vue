<template>
  <div class="tracing-management">
    <el-row :gutter="24">
      <!-- 左侧：追踪状态 + 健康状态，采用纵向堆叠卡片 -->
      <el-col :xl="8" :lg="10" :md="12" :sm="24" :xs="24">
        <div class="left-section">
          <el-card class="card-style" shadow="hover">
            <template #header>
              <div class="card-header">
                <span>追踪状态控制</span>
                <el-button @click="handleRefreshStatus" size="small">刷新</el-button>
              </div>
            </template>
            <el-form :model="statusForm" label-width="120px" class="form-style">
              <el-form-item label="追踪状态">
                <el-switch v-model="statusForm.enabled" active-text="启用" inactive-text="禁用" @change="handleToggleTracing"/>
              </el-form-item>
              <el-form-item label="服务名称">
                <el-input v-model="statusForm.serviceName" readonly/>
              </el-form-item>
              <el-form-item label="服务版本">
                <el-input v-model="statusForm.serviceVersion" readonly/>
              </el-form-item>
              <el-form-item label="服务命名空间">
                <el-input v-model="statusForm.serviceNamespace" readonly/>
              </el-form-item>
              <el-form-item label="OpenTelemetry">
                <el-tag :type="statusForm.openTelemetryEnabled ? 'success' : 'danger'">
                  {{ statusForm.openTelemetryEnabled ? '启用' : '禁用' }}
                </el-tag>
              </el-form-item>
              <el-form-item label="导出器类型">
                <el-tag>{{ statusForm.exporterType }}</el-tag>
              </el-form-item>
              <el-form-item label="全局采样率">
                <el-progress :percentage="statusForm.globalSamplingRatio * 100"
                  :format="percentage => `${percentage}%`"/>
              </el-form-item>
            </el-form>
          </el-card>
          <el-card class="card-style" shadow="hover">
            <template #header>
              <div class="card-header">
                <span>健康状态</span>
                <el-button @click="handleRefreshHealth" size="small">刷新</el-button>
              </div>
            </template>
            <el-descriptions :column="1" border class="health-desc">
              <el-descriptions-item label="整体状态">
                <el-tag :type="healthStatus.status === 'UP' ? 'success' : 'danger'">
                  {{ healthStatus.status }}
                </el-tag>
              </el-descriptions-item>
              <el-descriptions-item label="内存使用">
                <el-progress :percentage="healthStatus.memoryUsage"
                  :status="healthStatus.memoryUsage > 80 ? 'exception' : 'success'"/>
              </el-descriptions-item>
              <el-descriptions-item label="CPU使用">
                <el-progress :percentage="healthStatus.cpuUsage"
                  :status="healthStatus.cpuUsage > 80 ? 'exception' : 'success'"/>
              </el-descriptions-item>
              <el-descriptions-item label="活跃追踪数">
                {{ healthStatus.activeTraces }}
              </el-descriptions-item>
              <el-descriptions-item label="缓冲区使用">
                <el-progress :percentage="healthStatus.bufferUsage"
                  :status="healthStatus.bufferUsage > 90 ? 'exception' : 'success'"/>
              </el-descriptions-item>
              <el-descriptions-item label="最后更新">
                {{ healthStatus.lastUpdate }}
              </el-descriptions-item>
            </el-descriptions>
          </el-card>
          <!-- 采样统计信息卡片 -->
           <el-card class="card-style" shadow="hover">
            <template #header>
              <div class="card-header">
                <span>采样统计</span>
                <el-button @click="loadSamplingStats" size="small">刷新</el-button>
              </div>
            </template>
            <el-descriptions :column="1" border class="health-desc">
              <el-descriptions-item label="总采样数">
                {{ samplingStats.totalSamples }}
              </el-descriptions-item>
              <el-descriptions-item label="丢弃样本数">
                {{ samplingStats.droppedSamples }}
              </el-descriptions-item>
              <el-descriptions-item label="采样效率">
                <el-progress :percentage="samplingStats.samplingEfficiency"
                  :format="percentage => `${percentage.toFixed(1)}%`"/>
              </el-descriptions-item>
            </el-descriptions>
          </el-card>
        </div>
      </el-col>

      <!-- 右侧：配置管理卡片，改为充满右侧全屏 -->
      <el-col :xl="16" :lg="14" :md="12" :sm="24" :xs="24">
        <div class="right-section full-width">
          <el-card class="config-card card-style fill-card" shadow="hover">
            <template #header>
              <div class="card-header config-header">
                <span>配置管理</span>
                <div class="header-actions">
                  <el-button @click="handleRefreshConfig" size="small">刷新配置</el-button>
                  <el-button type="primary" @click="handleSaveConfig" :loading="saving" size="small">
                    保存配置
                  </el-button>
                  <el-button type="success" @click="handleExportConfig" size="small">
                    导出配置
                  </el-button>
                  <el-button type="warning" @click="handleClearCache" :loading="clearingCache" size="small">
                    清理缓存
                  </el-button>
                  <el-button type="danger" @click="handleCleanupExpired" :loading="cleaningUp" size="small">
                    清理过期数据
                  </el-button>
                </div>
              </div>
            </template>

            <div class="vertical-config-layout">
              <!-- 采样配置 - 全局配置 -->
              <el-card class="config-section" shadow="hover">
                <template #header>
                  <div class="section-header">
                    <span>采样配置 - 全局配置</span>
                  </div>
                </template>
                <el-form :model="samplingConfig" label-width="150px" class="form-style">
                  <el-form-item label="全局采样率(%)">
                    <el-slider
                      v-model="samplingConfig.globalRate"
                      :min="0"
                      :max="100"
                      show-input
                      :marks="{0: '0%', 25: '25%', 50: '50%', 75: '75%', 100: '100%'}"
                    />
                  </el-form-item>
                  <el-form-item label="自适应采样">
                    <el-switch v-model="samplingConfig.adaptiveSampling"/>
                  </el-form-item>
                  <el-form-item label="始终采样路径" v-if="samplingConfig.adaptiveSampling">
                    <el-select
                      v-model="samplingConfig.alwaysSamplePaths"
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
                  <el-form-item label="从不采样路径" v-if="samplingConfig.adaptiveSampling">
                    <el-select
                      v-model="samplingConfig.neverSamplePaths"
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
              </el-card>

              <!-- 采样配置 - 服务特定配置 -->
              <el-card class="config-section" shadow="hover">
                <template #header>
                  <div class="section-header">
                    <span>采样配置 - 服务特定配置</span>
                  </div>
                </template>
                <div class="service-config-table">
                  <el-button type="primary" @click="handleAddServiceConfig" style="margin-bottom: 16px;">
                    添加服务配置
                  </el-button>
                  <el-table :data="samplingConfig.serviceConfigs" style="width: 100%">
                    <el-table-column prop="service" label="服务名称" width="200">
                      <template #default="scope">
                        <el-select v-model="scope.row.service" placeholder="请选择服务" style="width: 90%;">
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
                          style="width: 90%;"
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
                </div>
              </el-card>

              <!-- 性能配置 -->
              <el-card class="config-section" shadow="hover">
                <template #header>
                  <div class="section-header">
                    <span>性能配置</span>
                  </div>
                </template>
                <el-form :model="configForm.performance" label-width="150px" class="form-style">
                  <el-form-item label="异步处理">
                    <el-switch v-model="configForm.performance.asyncProcessing"/>
                  </el-form-item>
                  <el-form-item label="线程池核心大小">
                    <el-input-number v-model="configForm.performance.threadPool.coreSize" :min="1" :max="100"/>
                  </el-form-item>
                  <el-form-item label="缓冲区大小">
                    <el-input-number v-model="configForm.performance.buffer.size" :min="100" :max="10000"/>
                  </el-form-item>
                  <el-form-item label="内存限制(MB)">
                    <el-input-number v-model="configForm.performance.memory.memoryLimitMb" :min="64" :max="2048"/>
                  </el-form-item>
                </el-form>
              </el-card>

              <!-- 导出器配置 -->
              <el-card class="config-section" shadow="hover">
                <template #header>
                  <div class="section-header">
                    <span>导出器配置</span>
                  </div>
                </template>
                <el-form :model="configForm.exporter" label-width="150px" class="form-style">
                  <el-form-item label="导出器类型">
                    <el-select v-model="configForm.exporter.type" style="width: 200px;">
                      <el-option label="Logging" value="logging"/>
                      <el-option label="OTLP" value="otlp"/>
                      <el-option label="Jaeger" value="jaeger"/>
                    </el-select>
                  </el-form-item>
                  <el-form-item label="日志导出">
                    <el-switch v-model="configForm.exporter.logging.enabled"/>
                  </el-form-item>
                </el-form>
              </el-card>
            </div>
          </el-card>
        </div>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
    getTracingStatus,
    getTracingHealth,
    getTracingConfig,
    updateTracingConfig,
    enableTracing,
    disableTracing,
    refreshSamplingStrategy,
    refreshTracingData,
    cleanupExpiredTraces,
    getTracingStats
} from '@/api/tracing'

// 添加导入获取服务类型的API
import { getServiceTypes } from '@/api/dashboard'

// 状态表单
const statusForm = ref({
    enabled: false,
    serviceName: '',
    serviceVersion: '',
    serviceNamespace: '',
    openTelemetryEnabled: false,
    exporterType: '',
    globalSamplingRatio: 0
})

// 健康状态
const healthStatus = ref({
    status: 'UP',
    memoryUsage: 0,
    cpuUsage: 0,
    activeTraces: 0,
    bufferUsage: 0,
    lastUpdate: ''
})

// 配置表单
const configForm = ref({
    performance: {
        asyncProcessing: true,
        threadPool: {
            coreSize: 10
        },
        buffer: {
            size: 1000
        },
        memory: {
            memoryLimitMb: 512
        }
    },
    exporter: {
        type: 'logging',
        logging: {
            enabled: true
        }
    }
})

// 采样配置
const samplingConfig = ref({
    globalRate: 10,
    adaptiveSampling: true,
    alwaysSamplePaths: ['/api/critical', '/api/payment'],
    neverSamplePaths: ['/api/health', '/api/metrics'],
    serviceConfigs: [
        { service: 'chat', rate: 20 },
        { service: 'embedding', rate: 15 },
        { service: 'rerank', rate: 10 },
        { service: 'tts', rate: 10 },
        { service: 'stt', rate: 10 },
        { service: 'imgGen', rate: 10 },
        { service: 'imgEdit', rate: 10 }
    ]
})

// 采样统计
const samplingStats = ref({
    totalSamples: 123456,
    droppedSamples: 111111,
    samplingEfficiency: 90.0
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
    'chat',
    'embedding',
    'rerank',
    'tts',
    'stt',
    'imgGen',
    'imgEdit'
])

// 状态
const saving = ref(false)
const refreshingSampling = ref(false)
const clearingCache = ref(false)
const cleaningUp = ref(false)
const activeConfigTab = ref('sampling')
const activeSamplingTab = ref('global')

// 定时器引用
const statsRefreshTimer = ref<number | undefined>(undefined)

// 切换追踪状态
const handleToggleTracing = async (enabled: boolean) => {
    try {
        if (enabled) {
            await enableTracing()
            ElMessage.success('追踪已启用')
        } else {
            await disableTracing()
            ElMessage.success('追踪已禁用')
        }

        // 刷新状态
        await loadTracingStatus()
    } catch (error) {
        console.error('切换追踪状态失败:', error)
        ElMessage.error('切换追踪状态失败')
        // 恢复原状态
        statusForm.value.enabled = !enabled
    }
}

// 加载追踪状态
const loadTracingStatus = async () => {
    try {
        const response = await getTracingStatus()
        console.log('追踪状态:', response)

        const data = response.data || response
        if (data) {
            statusForm.value = {
                enabled: (data as any).enabled || false,
                serviceName: (data as any).serviceName || '',
                serviceVersion: (data as any).serviceVersion || '',
                serviceNamespace: (data as any).serviceNamespace || '',
                openTelemetryEnabled: (data as any).openTelemetry?.enabled || false,
                exporterType: (data as any).exporter?.type || '',
                globalSamplingRatio: (data as any).sampling?.globalRatio || 0
            }
        }
    } catch (error) {
        console.error('加载追踪状态失败:', error)
        ElMessage.error('加载追踪状态失败')
    }
}

// 刷新状态
const handleRefreshStatus = () => {
    loadTracingStatus()
}

// 加载健康状态
const loadHealthStatus = async () => {
    try {
        const response = await getTracingHealth()
        console.log('健康状态:', response)

        const data = response.data || response
        if (data) {
            healthStatus.value = {
                status: (data as any).status || 'UP',
                memoryUsage: Math.floor(Math.random() * 100),
                cpuUsage: Math.floor(Math.random() * 100),
                activeTraces: Math.floor(Math.random() * 1000),
                bufferUsage: Math.floor(Math.random() * 100),
                lastUpdate: new Date().toLocaleString()
            }
        }
    } catch (error) {
        console.error('加载健康状态失败:', error)
        ElMessage.error('加载健康状态失败')
    }
}

// 刷新健康状态
const handleRefreshHealth = () => {
    loadHealthStatus()
}

// 加载配置
const loadTracingConfig = async () => {
    try {
        const response = await getTracingConfig()
        console.log('追踪配置:', response)

        const data = response.data || response
        if (data) {
            // 更新配置表单
            if ((data as any).performance) {
                configForm.value.performance = {
                    asyncProcessing: (data as any).performance.asyncProcessing !== undefined ? 
                        (data as any).performance.asyncProcessing : true,
                    threadPool: (data as any).performance.threadPool || { coreSize: 10 },
                    buffer: (data as any).performance.buffer || { size: 1000 },
                    memory: (data as any).performance.memory || { memoryLimitMb: 512 }
                }
            }

            if ((data as any).exporter) {
                configForm.value.exporter = {
                    type: (data as any).exporter.type || 'logging',
                    logging: (data as any).exporter.logging || { enabled: true }
                }
            }
            
            // 更新采样配置
            if ((data as any).sampling) {
                const samplingData = (data as any).sampling;
                samplingConfig.value.globalRate = (samplingData.ratio !== undefined ? samplingData.ratio : 0) * 100;
                samplingConfig.value.adaptiveSampling = samplingData.adaptive?.enabled !== undefined ? 
                    samplingData.adaptive.enabled : false;
                samplingConfig.value.alwaysSamplePaths = samplingData.alwaysSample || [];
                samplingConfig.value.neverSamplePaths = samplingData.neverSample || [];
                
                // 处理服务特定配置
                if (samplingData.serviceRatios) {
                    samplingConfig.value.serviceConfigs = Object.entries(samplingData.serviceRatios).map(([service, rate]) => ({
                        service,
                        rate: (rate as number) * 100
                    }));
                }
            }
        }
    } catch (error) {
        console.error('加载追踪配置失败:', error)
        ElMessage.error('加载追踪配置失败: ' + (error as any).message || '未知错误')
    }
}

// 刷新配置
const handleRefreshConfig = () => {
    refreshAllSamplingData()
}

// 保存配置
const handleSaveConfig = async () => {
    saving.value = true
    try {
        // 构造符合后端要求的数据结构
        const configToSend = {
            sampling: {
                ratio: samplingConfig.value.globalRate / 100,
                adaptive: {
                    enabled: samplingConfig.value.adaptiveSampling
                },
                alwaysSample: samplingConfig.value.alwaysSamplePaths,
                neverSample: samplingConfig.value.neverSamplePaths,
                serviceRatios: samplingConfig.value.serviceConfigs.reduce((acc, config) => {
                    acc[config.service] = config.rate / 100;
                    return acc;
                }, {} as Record<string, number>)
            },
            performance: configForm.value.performance,
            exporter: configForm.value.exporter
        };
        
        await updateTracingConfig(configToSend);
        ElMessage.success('配置保存成功');
        
        // 刷新采样策略
        await refreshSamplingStrategy();
    } catch (error) {
        console.error('保存配置失败:', error);
        ElMessage.error('保存配置失败: ' + (error as any).message || '未知错误');
    } finally {
        saving.value = false;
    }
}

// 保存采样配置
const handleSaveSamplingConfig = async () => {
    // 验证采样配置
    if (!validateSamplingConfig()) {
        return
    }
    
    // 验证服务配置
    if (!validateServiceConfig()) {
        return
    }
    
    // 优化服务配置
    optimizeServiceConfig()
    
    try {
        // 构造符合后端要求的数据结构
        const config = {
            sampling: {
                ratio: samplingConfig.value.globalRate / 100,
                adaptive: {
                    enabled: samplingConfig.value.adaptiveSampling
                },
                alwaysSample: samplingConfig.value.alwaysSamplePaths,
                neverSample: samplingConfig.value.neverSamplePaths,
                serviceRatios: samplingConfig.value.serviceConfigs.reduce((acc, config) => {
                    acc[config.service] = config.rate / 100;
                    return acc;
                }, {} as Record<string, number>)
            }
        };
        
        saving.value = true
        await updateTracingConfig(config)
        
        // 刷新采样策略
        await refreshSamplingStrategy()
        
        ElMessage.success('采样配置已保存')
    } catch (error) {
        console.error('保存采样配置失败:', error)
        ElMessage.error('保存采样配置失败: ' + (error as any).message || '未知错误')
    } finally {
        saving.value = false
    }
}

// 重置采样配置
const handleResetSamplingConfig = async () => {
    try {
        await ElMessageBox.confirm('确定要重置采样配置为默认值吗？此操作不可撤销。', '确认重置', {
            confirmButtonText: '确定',
            cancelButtonText: '取消',
            type: 'warning'
        })
        
        // 重置为默认配置
        samplingConfig.value = {
            globalRate: 10,
            adaptiveSampling: true,
            alwaysSamplePaths: ['/api/critical', '/api/payment'],
            neverSamplePaths: ['/api/health', '/api/metrics'],
            serviceConfigs: [
                { service: 'chat', rate: 20 },
                { service: 'embedding', rate: 15 },
                { service: 'rerank', rate: 10 },
                { service: 'tts', rate: 10 },
                { service: 'stt', rate: 10 },
                { service: 'imgGen', rate: 10 },
                { service: 'imgEdit', rate: 10 }
            ]
        }
        
        ElMessage.success('采样配置已重置')
    } catch (error) {
        if (error !== 'cancel') {
            console.error('重置采样配置失败:', error)
            ElMessage.error('重置采样配置失败: ' + (error as any).message || '')
        }
    }
}

// 添加服务配置
const handleAddServiceConfig = () => {
    // 如果有可用服务，使用第一个作为默认值
    const defaultService = availableServices.value.length > 0 ? availableServices.value[0] : ''
    
    samplingConfig.value.serviceConfigs.push({ 
        service: defaultService, 
        rate: 10 
    })
}

// 删除服务配置
const handleDeleteServiceConfig = (index: number) => {
    if (samplingConfig.value.serviceConfigs.length <= 1) {
        ElMessage.warning('至少需要保留一个服务配置')
        return
    }
    
    ElMessageBox.confirm('确定要删除此服务配置吗？', '确认删除', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
    }).then(() => {
        samplingConfig.value.serviceConfigs.splice(index, 1)
        ElMessage.success('服务配置已删除')
    }).catch(() => {
        // 用户取消删除
    })
}

// 刷新采样策略
const handleRefreshSampling = async () => {
    refreshingSampling.value = true
    try {
        await refreshSamplingStrategy()
        ElMessage.success('采样策略刷新成功')
    } catch (error) {
        console.error('刷新采样策略失败:', error)
        ElMessage.error('刷新采样策略失败: ' + (error as any).message || '未知错误')
    } finally {
        refreshingSampling.value = false
    }
}

// 清理缓存
const handleClearCache = async () => {
    clearingCache.value = true
    try {
        await refreshTracingData()
        ElMessage.success('缓存清理成功')
    } catch (error) {
        console.error('清理缓存失败:', error)
        ElMessage.error('清理缓存失败')
    } finally {
        clearingCache.value = false
    }
}

// 清理过期数据
const handleCleanupExpired = async () => {
    try {
        const { value } = await ElMessageBox.prompt('请输入保留时间（小时）', '清理过期数据', {
            confirmButtonText: '确定',
            cancelButtonText: '取消',
            inputValue: '24',
            inputValidator: (value) => {
                const num = parseInt(value)
                return !isNaN(num) && num > 0 ? true : '请输入有效的小时数'
            }
        })

        cleaningUp.value = true
        const retentionHours = parseInt(value)
        const response = await cleanupExpiredTraces(retentionHours)

        const data = response.data || response
        ElMessage.success(data.message || '过期数据清理成功')
    } catch (error) {
        if (error !== 'cancel') {
            console.error('清理过期数据失败:', error)
            ElMessage.error('清理过期数据失败')
        }
    } finally {
        cleaningUp.value = false
    }
}

// 导出配置
const handleExportConfig = () => {
    const configJson = JSON.stringify({ 
        tracing: configForm.value, 
        sampling: samplingConfig.value,
        samplingStats: samplingStats.value
    }, null, 2)
    const blob = new Blob([configJson], { type: 'application/json' })
    const url = URL.createObjectURL(blob)

    const a = document.createElement('a')
    a.href = url
    a.download = `tracing-config-${new Date().toISOString().split('T')[0]}.json`
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)
    URL.revokeObjectURL(url)

    ElMessage.success('配置导出成功')
}

// 加载可用服务
const loadAvailableServices = async () => {
    try {
        // 使用getServiceTypes API获取所有可用服务类型
        const response = await getServiceTypes()
        const data = response.data || response
        
        if (data && data.success && Array.isArray(data.data)) {
            // 过滤掉空字符串并去重
            const serviceTypes = data.data
                .filter((type: string) => typeof type === 'string' && type.trim() !== '')
                .map((type: string) => type.trim())
            
            if (serviceTypes.length > 0) {
                availableServices.value = serviceTypes
                ElMessage.success(`成功加载 ${serviceTypes.length} 个服务类型`)
            } else {
                ElMessage.warning('未获取到服务类型列表，使用默认服务配置')
            }
        } else {
            // 如果API调用失败或返回空数据，使用默认的服务类型
            availableServices.value = [
                'chat',
                'embedding',
                'rerank',
                'tts',
                'stt',
                'imgGen',
                'imgEdit'
            ]
            ElMessage.warning('未获取到服务类型列表，使用默认服务配置')
        }
    } catch (error) {
        console.error('加载服务类型列表失败:', error)
        // 如果API调用失败，使用默认的服务类型
        availableServices.value = [
            'chat',
            'embedding',
            'rerank',
            'tts',
            'stt',
            'imgGen',
            'imgEdit'
        ]
        ElMessage.error('加载服务类型列表失败，使用默认配置')
    }
}

// 验证采样统计数据的合理性
const validateSamplingStats = (stats: any) => {
    // 检查数据是否存在
    if (!stats) return false
    
    // 检查总采样数是否合理
    if (typeof stats.totalSamples !== 'number' || stats.totalSamples < 0) {
        return false
    }
    
    // 检查丢弃样本数是否合理
    if (typeof stats.droppedSamples !== 'number' || stats.droppedSamples < 0) {
        return false
    }
    
    // 检查丢弃样本数不能超过总采样数
    if (stats.droppedSamples > stats.totalSamples) {
        return false
    }
    
    // 检查采样效率是否合理 (0-100之间)
    if (typeof stats.samplingEfficiency !== 'number' || 
        stats.samplingEfficiency < 0 || stats.samplingEfficiency > 100) {
        return false
    }
    
    return true
}

// 加载采样统计
const loadSamplingStats = async () => {
  try {
    const response = await getTracingStats()
    const data = response.data || response
    
    if (data) {
      // 从接口返回的数据中提取正确的字段
      const processing = (data as any).processing;
      const configInfo = (data as any).configInfo;
      
      if (processing && configInfo) {
        // 更新采样统计信息
        samplingStats.value = {
          totalSamples: processing.processed_count || 0,
          droppedSamples: processing.dropped_count || 0,
          samplingEfficiency: configInfo.globalSamplingRatio !== undefined ? 
            (configInfo.globalSamplingRatio * 100) : 0
        }
      } else {
        // 如果数据结构不符合预期，使用默认值
        samplingStats.value = {
          totalSamples: 0,
          droppedSamples: 0,
          samplingEfficiency: 0
        }
      }
    } else {
      ElMessage.warning('未获取到采样统计数据')
      // 使用默认值
      samplingStats.value = {
        totalSamples: 0,
        droppedSamples: 0,
        samplingEfficiency: 0
      }
    }
  } catch (error) {
    console.error('加载采样统计失败:', error)
    ElMessage.error('加载采样统计失败: ' + (error as any).message || '未知错误')
    // 保留当前值或使用默认值
  }
}

// 开始定时刷新采样统计
const startStatsRefresh = () => {
    // 如果已有定时器，先清除
    if (statsRefreshTimer.value) {
        clearInterval(statsRefreshTimer.value)
    }
    
    // 设置定时刷新（每60秒刷新一次）
    statsRefreshTimer.value = window.setInterval(() => {
        loadSamplingStats()
    }, 60000) as any as number
}

// 停止定时刷新
const stopStatsRefresh = () => {
    if (statsRefreshTimer.value) {
        clearInterval(statsRefreshTimer.value)
        statsRefreshTimer.value = undefined
    }
}

// 刷新所有采样相关数据
const refreshAllSamplingData = async () => {
    try {
        // 显示加载状态
        ElMessage.info('正在刷新采样数据...')
        
        // 并行加载所有数据
        await Promise.all([
            loadTracingConfig(),
            loadSamplingStats()
        ])
        
        ElMessage.success('采样数据刷新完成')
    } catch (error) {
        console.error('刷新采样数据失败:', error)
        ElMessage.error('刷新采样数据失败: ' + (error as any).message || '未知错误')
    }
}

// 组件挂载时初始化
onMounted(() => {
    loadTracingStatus()
    loadHealthStatus()
    refreshAllSamplingData()
    
    // 开始定时刷新采样统计
    startStatsRefresh()
    
    // 加载可用服务
    loadAvailableServices()
})

// 组件卸载时清理定时器
onUnmounted(() => {
    stopStatsRefresh()
})

// 添加一个方法来验证采样配置
const validateSamplingConfig = () => {
    // 验证全局采样率
    if (samplingConfig.value.globalRate < 0 || samplingConfig.value.globalRate > 100) {
        ElMessage.error('全局采样率必须在0-100之间')
        return false
    }
    
    // 验证服务配置
    for (const config of samplingConfig.value.serviceConfigs) {
        if (!config.service) {
            ElMessage.error('服务配置中的服务名称不能为空')
            return false
        }
        if (config.rate < 0 || config.rate > 100) {
            ElMessage.error('服务采样率必须在0-100之间')
            return false
        }
    }
    
    return true
}

// 验证服务配置
const validateServiceConfig = () => {
    const serviceConfigs = samplingConfig.value.serviceConfigs
    
    // 检查是否有重复的服务配置
    const serviceNames = serviceConfigs.map(config => config.service).filter(service => service)
    const uniqueServices = new Set(serviceNames)
    
    if (serviceNames.length !== uniqueServices.size) {
        ElMessage.error('服务配置中存在重复的服务')
        return false
    }
    
    // 检查是否有空的服务名称
    if (serviceConfigs.some(config => !config.service)) {
        ElMessage.error('服务配置中存在空的服务名称')
        return false
    }
    
    // 检查采样率是否在有效范围内
    if (serviceConfigs.some(config => config.rate < 0 || config.rate > 100)) {
        ElMessage.error('服务采样率必须在0-100之间')
        return false
    }
    
    return true
}

// 优化服务配置（移除无效配置，确保配置完整性）
const optimizeServiceConfig = () => {
    // 移除空的服务配置
    samplingConfig.value.serviceConfigs = samplingConfig.value.serviceConfigs.filter(
        config => config.service && config.service.trim() !== ''
    )
    
    // 如果没有服务配置，添加一个默认配置
    if (samplingConfig.value.serviceConfigs.length === 0 && availableServices.value.length > 0) {
        samplingConfig.value.serviceConfigs.push({
            service: availableServices.value[0],
            rate: 10
        })
    }
}

</script>
<style scoped>
.tracing-management {
  padding: 32px 24px 32px 24px;
  background: #fafbfc;
  min-height: 100vh;
  box-sizing: border-box;
}
.left-section {
  display: flex;
  flex-direction: column;
  gap: 24px;
}
.right-section {
  display: flex;
  flex-direction: column;
  gap: 24px;
  height: 100%;
}
.full-width {
  height: 100%;
}
.fill-card {
  width: 100%;
  min-height: 600px;
  height: 100%;
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
}
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 18px;
  font-weight: 500;
  padding-bottom: 4px;
}
.config-header {
  align-items: flex-end;
}
.header-actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}
.card-style {
  border-radius: 10px;
  box-shadow: 0 2px 12px 0 rgb(0 0 0 / 7%);
  margin-bottom: 0;
}
.form-style {
  padding: 8px 0 0 0;
}
.health-desc {
  margin-top: 8px;
}
.sampling-actions {
  margin-top: 24px;
  display: flex;
  justify-content: flex-end;
  gap: 16px;
  flex-wrap: wrap;
}
.service-config-table {
  margin-top: 8px;
}
@media (max-width: 1200px) {
  .tracing-management {
    padding: 16px 8px;
  }
  .left-section, .right-section {
    gap: 16px;
  }
  .card-header {
    font-size: 16px;
  }
}
@media (max-width: 768px) {
  .tracing-management {
    padding: 8px 4px;
  }
  .left-section, .right-section {
    gap: 8px;
  }
  .card-style {
    box-shadow: 0 1px 6px 0 rgb(0 0 0 / 10%);
    border-radius: 8px;
  }
  .header-actions, .sampling-actions {
    flex-wrap: wrap;
    gap: 6px;
  }
  .fill-card {
    min-height: 400px;
  }
}
</style>
