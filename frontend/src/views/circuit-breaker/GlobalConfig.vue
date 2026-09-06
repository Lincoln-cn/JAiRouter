<template>
  <div class="circuit-breaker-global-config">
    <el-card class="config-card">
      <template #header>
        <div class="card-header">
          <span class="card-title">{{ t('circuitBreaker.globalConfig.title') }}</span>
        </div>
      </template>

      <el-form :model="globalConfig" label-width="150px" class="config-form">
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item :label="t('circuitBreaker.globalConfig.adaptiveThresholdEnabled')">
              <div class="adaptive-switch-container">
                <el-switch v-model="globalConfig.adaptiveThresholdEnabled" />
                <span class="adaptive-hint" v-if="globalConfig.adaptiveThresholdEnabled">
                  <el-tag type="success" size="small">{{ t('circuitBreaker.globalConfig.enabled') }}</el-tag>
                  <span class="hint-text">{{ t('circuitBreaker.globalConfig.adaptiveHint') }}</span>
                </span>
                <span class="adaptive-hint" v-else>
                  <el-tag type="info" size="small">{{ t('circuitBreaker.globalConfig.disabled') }}</el-tag>
                </span>
              </div>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="t('circuitBreaker.globalConfig.stateSyncInterval')">
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
            <el-form-item :label="t('circuitBreaker.globalConfig.cleanupInterval')">
              <el-input-number
                v-model="globalConfig.cleanupIntervalMinutes"
                :min="1"
                :max="120"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="t('circuitBreaker.globalConfig.historyRetentionDays')">
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
            <el-form-item :label="t('circuitBreaker.globalConfig.defaultFailureThreshold')">
              <el-input-number v-model="globalConfig.defaultFailureThreshold" :min="1" :max="100" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item :label="t('circuitBreaker.globalConfig.defaultSuccessThreshold')">
              <el-input-number v-model="globalConfig.defaultSuccessThreshold" :min="1" :max="20" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item :label="t('circuitBreaker.globalConfig.defaultTimeout')">
              <el-input-number v-model="globalConfig.defaultTimeoutMs" :min="1000" :max="300000" :step="1000" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-form-item>
          <el-button type="primary" @click="saveGlobalConfig" :loading="savingConfig">{{ t('circuitBreaker.globalConfig.save') }}</el-button>
          <el-button @click="resetGlobalConfig">{{ t('circuitBreaker.globalConfig.resetToDefaults') }}</el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
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

const { t } = useI18n()

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
      ElMessage.success(t('circuitBreaker.globalConfig.messages.saveSuccess'))
    } else {
      ElMessage.error(response.data?.message || t('circuitBreaker.globalConfig.messages.saveFailed'))
    }
  } catch (error: any) {
    console.error('Failed to save global config:', error)
    ElMessage.error(t('circuitBreaker.globalConfig.messages.saveFailed'))
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
      ElMessage.info(t('circuitBreaker.globalConfig.messages.resetSuccess'))
    }
  } catch (error: any) {
    console.error('Failed to reset global config:', error)
    ElMessage.error(t('circuitBreaker.globalConfig.messages.resetFailed'))
  }
}

onMounted(() => {
  loadGlobalConfig()
})
</script>

<style scoped>
.circuit-breaker-global-config {
  padding: 24px;
  background: var(--ja-main-bg-gradient);
  min-height: calc(100vh - 80px);
}

.config-card {
  box-shadow: var(--ja-shadow-lg);
  border-radius: var(--ja-radius-lg);
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.card-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--ja-text-primary);
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
  color: var(--ja-text-regular);
  font-size: 12px;
}
</style>
