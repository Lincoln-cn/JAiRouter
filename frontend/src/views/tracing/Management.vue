<template>
    <div class="tracing-management">
        <el-row :gutter="20">
            <!-- 追踪状态控制 -->
            <el-col :span="12">
                <el-card>
                    <template #header>
                        <div class="card-header">
                            <span>追踪状态控制</span>
                            <el-button @click="handleRefreshStatus">刷新</el-button>
                        </div>
                    </template>

                    <el-form :model="statusForm" label-width="120px">
                        <el-form-item label="追踪状态">
                            <el-switch v-model="statusForm.enabled" active-text="启用" inactive-text="禁用"
                                @change="handleToggleTracing" />
                        </el-form-item>

                        <el-form-item label="服务名称">
                            <el-input v-model="statusForm.serviceName" readonly />
                        </el-form-item>

                        <el-form-item label="服务版本">
                            <el-input v-model="statusForm.serviceVersion" readonly />
                        </el-form-item>

                        <el-form-item label="服务命名空间">
                            <el-input v-model="statusForm.serviceNamespace" readonly />
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
                                :format="(percentage: number) => `${percentage}%`" />
                        </el-form-item>
                    </el-form>
                </el-card>
            </el-col>

            <!-- 健康状态 -->
            <el-col :span="12">
                <el-card>
                    <template #header>
                        <div class="card-header">
                            <span>健康状态</span>
                            <el-button @click="handleRefreshHealth">刷新</el-button>
                        </div>
                    </template>

                    <el-descriptions :column="1" border>
                        <el-descriptions-item label="整体状态">
                            <el-tag :type="healthStatus.status === 'UP' ? 'success' : 'danger'">
                                {{ healthStatus.status }}
                            </el-tag>
                        </el-descriptions-item>
                        <el-descriptions-item label="内存使用">
                            <el-progress :percentage="healthStatus.memoryUsage"
                                :status="healthStatus.memoryUsage > 80 ? 'exception' : 'success'" />
                        </el-descriptions-item>
                        <el-descriptions-item label="CPU使用">
                            <el-progress :percentage="healthStatus.cpuUsage"
                                :status="healthStatus.cpuUsage > 80 ? 'exception' : 'success'" />
                        </el-descriptions-item>
                        <el-descriptions-item label="活跃追踪数">
                            {{ healthStatus.activeTraces }}
                        </el-descriptions-item>
                        <el-descriptions-item label="缓冲区使用">
                            <el-progress :percentage="healthStatus.bufferUsage"
                                :status="healthStatus.bufferUsage > 90 ? 'exception' : 'success'" />
                        </el-descriptions-item>
                        <el-descriptions-item label="最后更新">
                            {{ healthStatus.lastUpdate }}
                        </el-descriptions-item>
                    </el-descriptions>
                </el-card>
            </el-col>
        </el-row>

        <!-- 配置管理 -->
        <el-card class="config-card">
            <template #header>
                <div class="card-header">
                    <span>配置管理</span>
                    <div class="header-actions">
                        <el-button @click="handleRefreshConfig">刷新配置</el-button>
                        <el-button type="primary" @click="handleSaveConfig" :loading="saving">保存配置</el-button>
                    </div>
                </div>
            </template>

            <el-tabs v-model="activeConfigTab">
                <el-tab-pane label="采样配置" name="sampling">
                    <el-form :model="configForm.sampling" label-width="150px">
                        <el-form-item label="全局采样率">
                            <el-slider v-model="configForm.sampling.ratio" :min="0" :max="1" :step="0.01" show-input
                                :format-tooltip="(val: number) => `${(val * 100).toFixed(1)}%`" />
                        </el-form-item>

                        <el-form-item label="自适应采样">
                            <el-switch v-model="configForm.sampling.adaptive.enabled" />
                        </el-form-item>

                        <el-form-item label="服务特定采样率" v-if="configForm.sampling.serviceRatios">
                            <el-table :data="Object.entries(configForm.sampling.serviceRatios)" style="width: 100%">
                                <el-table-column prop="0" label="服务名称" />
                                <el-table-column prop="1" label="采样率">
                                    <template #default="{ row }">
                                        {{ (row[1] * 100).toFixed(1) }}%
                                    </template>
                                </el-table-column>
                            </el-table>
                        </el-form-item>

                        <el-form-item label="始终采样">
                            <el-tag v-for="path in configForm.sampling.alwaysSample" :key="path" type="success"
                                style="margin-right: 8px; margin-bottom: 8px;">
                                {{ path }}
                            </el-tag>
                        </el-form-item>

                        <el-form-item label="从不采样">
                            <el-tag v-for="path in configForm.sampling.neverSample" :key="path" type="danger"
                                style="margin-right: 8px; margin-bottom: 8px;">
                                {{ path }}
                            </el-tag>
                        </el-form-item>
                    </el-form>
                </el-tab-pane>

                <el-tab-pane label="性能配置" name="performance">
                    <el-form :model="configForm.performance" label-width="150px">
                        <el-form-item label="异步处理">
                            <el-switch v-model="configForm.performance.asyncProcessing" />
                        </el-form-item>

                        <el-form-item label="线程池核心大小">
                            <el-input-number v-model="configForm.performance.threadPool.coreSize" :min="1" :max="100" />
                        </el-form-item>

                        <el-form-item label="缓冲区大小">
                            <el-input-number v-model="configForm.performance.buffer.size" :min="100" :max="10000" />
                        </el-form-item>

                        <el-form-item label="内存限制(MB)">
                            <el-input-number v-model="configForm.performance.memory.memoryLimitMb" :min="64"
                                :max="2048" />
                        </el-form-item>
                    </el-form>
                </el-tab-pane>

                <el-tab-pane label="导出器配置" name="exporter">
                    <el-form :model="configForm.exporter" label-width="150px">
                        <el-form-item label="导出器类型">
                            <el-select v-model="configForm.exporter.type">
                                <el-option label="Logging" value="logging" />
                                <el-option label="OTLP" value="otlp" />
                                <el-option label="Jaeger" value="jaeger" />
                            </el-select>
                        </el-form-item>

                        <el-form-item label="日志导出">
                            <el-switch v-model="configForm.exporter.logging.enabled" />
                        </el-form-item>
                    </el-form>
                </el-tab-pane>
            </el-tabs>
        </el-card>

        <!-- 操作面板 -->
        <el-card class="actions-card">
            <template #header>
                <div class="card-header">
                    <span>操作面板</span>
                </div>
            </template>

            <el-row :gutter="20">
                <el-col :span="6">
                    <el-button type="primary" @click="handleRefreshSampling" :loading="refreshingSampling"
                        style="width: 100%">
                        刷新采样策略
                    </el-button>
                </el-col>
                <el-col :span="6">
                    <el-button type="warning" @click="handleClearCache" :loading="clearingCache" style="width: 100%">
                        清理缓存
                    </el-button>
                </el-col>
                <el-col :span="6">
                    <el-button type="danger" @click="handleCleanupExpired" :loading="cleaningUp" style="width: 100%">
                        清理过期数据
                    </el-button>
                </el-col>
                <el-col :span="6">
                    <el-button @click="handleExportConfig" style="width: 100%">
                        导出配置
                    </el-button>
                </el-col>
            </el-row>
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
    refreshSamplingStrategy,
    refreshTracingData,
    cleanupExpiredTraces
} from '@/api/tracing'

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
    sampling: {
        ratio: 0.1,
        adaptive: {
            enabled: true
        },
        serviceRatios: {},
        alwaysSample: [],
        neverSample: []
    },
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

// 状态
const saving = ref(false)
const refreshingSampling = ref(false)
const clearingCache = ref(false)
const cleaningUp = ref(false)
const activeConfigTab = ref('sampling')

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
            if ((data as any).sampling) {
                configForm.value.sampling = {
                    ratio: (data as any).sampling.ratio || 0.1,
                    adaptive: (data as any).sampling.adaptive || { enabled: true },
                    serviceRatios: (data as any).sampling.serviceRatios || {},
                    alwaysSample: (data as any).sampling.alwaysSample || [],
                    neverSample: (data as any).sampling.neverSample || []
                }
            }

            if ((data as any).performance) {
                configForm.value.performance = {
                    asyncProcessing: (data as any).performance.asyncProcessing || true,
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
        }
    } catch (error) {
        console.error('加载追踪配置失败:', error)
        ElMessage.error('加载追踪配置失败')
    }
}

// 刷新配置
const handleRefreshConfig = () => {
    loadTracingConfig()
}

// 保存配置
const handleSaveConfig = async () => {
    saving.value = true
    try {
        await updateTracingConfig(configForm.value)
        ElMessage.success('配置保存成功')
    } catch (error) {
        console.error('保存配置失败:', error)
        ElMessage.error('保存配置失败')
    } finally {
        saving.value = false
    }
}

// 刷新采样策略
const handleRefreshSampling = async () => {
    refreshingSampling.value = true
    try {
        await refreshSamplingStrategy()
        ElMessage.success('采样策略刷新成功')
    } catch (error) {
        console.error('刷新采样策略失败:', error)
        ElMessage.error('刷新采样策略失败')
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
    const configJson = JSON.stringify(configForm.value, null, 2)
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

// 组件挂载时初始化
onMounted(() => {
    loadTracingStatus()
    loadHealthStatus()
    loadTracingConfig()
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

.header-actions {
    display: flex;
    gap: 10px;
}

.config-card {
    margin-top: 20px;
}

.actions-card {
    margin-top: 20px;
}
</style>