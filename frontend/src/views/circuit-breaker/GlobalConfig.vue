<template>
  <div class="circuit-breaker-global-config">
    <el-card class="config-card">
      <template #header>
        <div class="card-header">
          <span class="card-title">熔断器全局配置</span>
        </div>
      </template>

      <el-form :model="globalConfig" label-width="150px" class="config-form">
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="启用自适应阈值调整">
              <div class="adaptive-switch-container">
                <el-switch v-model="globalConfig.adaptiveThresholdEnabled" />
                <span class="adaptive-hint" v-if="globalConfig.adaptiveThresholdEnabled">
                  <el-tag type="success" size="small">已启用</el-tag>
                  <span class="hint-text">系统将根据失败率自动调整阈值</span>
                </span>
                <span class="adaptive-hint" v-else>
                  <el-tag type="info" size="small">已禁用</el-tag>
                </span>
              </div>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="状态同步间隔(分钟)">
              <el-input-number
                v-model="globalConfig.stateSyncIntervalMinutes"
                :min="1"
                :max="60"
              />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="过期清理间隔(分钟)">
              <el-input-number
                v-model="globalConfig.cleanupIntervalMinutes"
                :min="1"
                :max="120"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="历史记录保留天数">
              <el-input-number
                v-model="globalConfig.historyRetentionDays"
                :min="1"
                :max="365"
              />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="默认失败阈值">
              <el-input-number v-model="globalConfig.defaultFailureThreshold" :min="1" :max="100" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="默认成功阈值">
              <el-input-number v-model="globalConfig.defaultSuccessThreshold" :min="1" :max="20" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="默认超时(ms)">
              <el-input-number v-model="globalConfig.defaultTimeoutMs" :min="1000" :max="300000" :step="1000" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-form-item>
          <el-button type="primary" @click="saveGlobalConfig" :loading="savingConfig">保存全局配置</el-button>
          <el-button @click="resetGlobalConfig">重置为默认值</el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import request from '@/utils/request'

interface GlobalConfig {
  adaptiveThresholdEnabled: boolean
  stateSyncIntervalMinutes: number
  cleanupIntervalMinutes: number
  historyRetentionDays: number
  defaultFailureThreshold: number
  defaultSuccessThreshold: number
  defaultTimeoutMs: number
}

const globalConfig = ref<GlobalConfig>({
  adaptiveThresholdEnabled: false,
  stateSyncIntervalMinutes: 5,
  cleanupIntervalMinutes: 30,
  historyRetentionDays: 30,
  defaultFailureThreshold: 5,
  defaultSuccessThreshold: 2,
  defaultTimeoutMs: 60000
})

const savingConfig = ref(false)

const loadGlobalConfig = async () => {
  try {
    const response = await request.get('/config/circuit-breaker/global-config')
    if (response.data?.success) {
      const config = response.data.data
      if (config) {
        globalConfig.value = {
          adaptiveThresholdEnabled: config.adaptiveThresholdEnabled || false,
          stateSyncIntervalMinutes: config.stateSyncIntervalMinutes || 5,
          cleanupIntervalMinutes: config.cleanupIntervalMinutes || 30,
          historyRetentionDays: config.historyRetentionDays || 30,
          defaultFailureThreshold: config.defaultFailureThreshold || 5,
          defaultSuccessThreshold: config.defaultSuccessThreshold || 2,
          defaultTimeoutMs: config.defaultTimeoutMs || 60000
        }
      }
    }
  } catch (error: any) {
    console.error('Failed to load global config:', error)
  }
}

const saveGlobalConfig = async () => {
  savingConfig.value = true
  try {
    const response = await request.put('/config/circuit-breaker/global-config', globalConfig.value)
    if (response.data?.success) {
      ElMessage.success('全局配置保存成功')
    } else {
      ElMessage.error(response.data?.message || '保存全局配置失败')
    }
  } catch (error: any) {
    console.error('Failed to save global config:', error)
    ElMessage.error('保存全局配置失败')
  } finally {
    savingConfig.value = false
  }
}

const resetGlobalConfig = async () => {
  try {
    const response = await request.post('/config/circuit-breaker/global-config/reset')
    if (response.data?.success) {
      const config = response.data.data
      if (config) {
        globalConfig.value = config
      }
      ElMessage.info('已重置为默认配置')
    }
  } catch (error: any) {
    console.error('Failed to reset global config:', error)
    ElMessage.error('重置全局配置失败')
  }
}

onMounted(() => {
  loadGlobalConfig()
})
</script>

<style scoped>
.circuit-breaker-global-config {
  padding: 24px;
  background: linear-gradient(180deg, #f7f9fc 0%, #ffffff 100%);
  min-height: calc(100vh - 80px);
}

.config-card {
  box-shadow: 0 6px 20px rgba(15, 23, 42, 0.06);
  border-radius: 12px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.card-title {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
}

.config-form {
  padding: 20px;
}

.adaptive-switch-container {
  display: flex;
  align-items: center;
  gap: 12px;
}

.adaptive-hint {
  display: flex;
  align-items: center;
  gap: 8px;
}

.hint-text {
  color: #606266;
  font-size: 12px;
}
</style>
