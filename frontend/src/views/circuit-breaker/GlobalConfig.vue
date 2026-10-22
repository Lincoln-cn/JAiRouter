<template>
  <PageSkeleton :title="t('circuitBreaker.globalConfig.title')">
    <el-card class="config-card">
      <el-form :model="globalConfig" label-width="150px" class="config-form">
        <!-- 分组：阈值 / 超时 / 半开 / 历史维护（清理间隔与保留天数同属历史维护语义，合并为一组） -->
        <section class="form-section">
          <h3 class="form-section__title">{{ t('circuitBreaker.globalConfig.sections.thresholds') }}</h3>
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
              <el-form-item :label="t('circuitBreaker.globalConfig.defaultFailureThreshold')">
                <el-input-number v-model="globalConfig.defaultFailureThreshold" :min="1" :max="100" />
              </el-form-item>
            </el-col>
          </el-row>
        </section>

        <el-divider />

        <section class="form-section">
          <h3 class="form-section__title">{{ t('circuitBreaker.globalConfig.sections.timeouts') }}</h3>
          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item :label="t('circuitBreaker.globalConfig.defaultTimeout')">
                <div class="field-with-unit">
                  <el-input-number v-model="globalConfig.defaultTimeoutMs" :min="1000" :max="300000" :step="1000" />
                  <span class="field-unit">{{ t('circuitBreaker.globalConfig.unitMs') }}</span>
                </div>
              </el-form-item>
            </el-col>
          </el-row>
        </section>

        <el-divider />

        <section class="form-section">
          <h3 class="form-section__title">{{ t('circuitBreaker.globalConfig.sections.halfOpen') }}</h3>
          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item :label="t('circuitBreaker.globalConfig.defaultSuccessThreshold')">
                <div class="field-with-hint">
                  <el-input-number v-model="globalConfig.defaultSuccessThreshold" :min="1" :max="20" />
                  <p class="field-hint">{{ t('circuitBreaker.globalConfig.successThresholdHint') }}</p>
                </div>
              </el-form-item>
            </el-col>
          </el-row>
        </section>

        <el-divider />

        <section class="form-section">
          <h3 class="form-section__title">{{ t('circuitBreaker.globalConfig.sections.retention') }}</h3>
          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item :label="t('circuitBreaker.globalConfig.cleanupInterval')">
                <div class="field-with-unit">
                  <el-input-number
                    v-model="globalConfig.cleanupIntervalMinutes"
                    :min="1"
                    :max="120"
                  />
                  <span class="field-unit">{{ t('circuitBreaker.globalConfig.unitMinutes') }}</span>
                </div>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item :label="t('circuitBreaker.globalConfig.historyRetentionDays')">
                <div class="field-with-unit">
                  <el-input-number
                    v-model="globalConfig.historyRetentionDays"
                    :min="1"
                    :max="365"
                  />
                  <span class="field-unit">{{ t('circuitBreaker.globalConfig.unitDays') }}</span>
                </div>
              </el-form-item>
            </el-col>
          </el-row>
        </section>

        <!-- 保存操作收进卡片内底部，并显式告知未保存改动数 -->
        <div class="config-footer">
          <span class="dirty-indicator" :class="{ 'is-dirty': dirtyCount > 0 }">
            {{ dirtyCount > 0 ? t('circuitBreaker.globalConfig.dirtyCount', { count: dirtyCount }) : t('circuitBreaker.globalConfig.noChanges') }}
          </span>
          <div class="config-footer__actions">
            <el-button @click="resetGlobalConfig">{{ t('circuitBreaker.globalConfig.resetToDefaults') }}</el-button>
            <el-button type="primary" @click="saveGlobalConfig" :loading="savingConfig">
              {{ t('circuitBreaker.globalConfig.save') }}
            </el-button>
          </div>
        </div>
      </el-form>
    </el-card>
  </PageSkeleton>
</template>

<script setup lang="ts">
import { computed, ref, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/utils/request'
import PageSkeleton from '@/components/PageSkeleton.vue'

interface GlobalConfig {
  adaptiveThresholdEnabled: boolean
  cleanupIntervalMinutes: number
  historyRetentionDays: number
  defaultFailureThreshold: number
  defaultSuccessThreshold: number
  defaultTimeoutMs: number
}

const CONFIG_KEYS: (keyof GlobalConfig)[] = [
  'adaptiveThresholdEnabled',
  'cleanupIntervalMinutes',
  'historyRetentionDays',
  'defaultFailureThreshold',
  'defaultSuccessThreshold',
  'defaultTimeoutMs'
]

const globalConfig = ref<GlobalConfig>({
  adaptiveThresholdEnabled: false,
  cleanupIntervalMinutes: 30,
  historyRetentionDays: 30,
  defaultFailureThreshold: 5,
  defaultSuccessThreshold: 2,
  defaultTimeoutMs: 60000
})

/** 最近一次已保存/已加载的快照，仅用于「已修改 N 项」，不参与提交。 */
const savedConfig = ref<GlobalConfig | null>(null)
const savingConfig = ref(false)

const { t } = useI18n()

const normalizeConfig = (config: Partial<GlobalConfig> | null | undefined): GlobalConfig => ({
  adaptiveThresholdEnabled: config?.adaptiveThresholdEnabled || false,
  cleanupIntervalMinutes: config?.cleanupIntervalMinutes || 30,
  historyRetentionDays: config?.historyRetentionDays || 30,
  defaultFailureThreshold: config?.defaultFailureThreshold || 5,
  defaultSuccessThreshold: config?.defaultSuccessThreshold || 2,
  defaultTimeoutMs: config?.defaultTimeoutMs || 60000
})

const dirtyCount = computed(() => {
  const saved = savedConfig.value
  if (!saved) return 0
  return CONFIG_KEYS.filter(key => globalConfig.value[key] !== saved[key]).length
})

const loadGlobalConfig = async () => {
  try {
    const response = await request.get('/config/circuit-breaker/global-config')
    if (response.data?.success) {
      const config = response.data.data
      if (config) {
        globalConfig.value = normalizeConfig(config)
        savedConfig.value = { ...globalConfig.value }
      }
    }
  } catch (error) {
    console.error('Failed to load global config:', error)
  }
}

const saveGlobalConfig = async () => {
  savingConfig.value = true
  try {
    const response = await request.put('/config/circuit-breaker/global-config', globalConfig.value)
    if (response.data?.success) {
      ElMessage.success(t('circuitBreaker.globalConfig.messages.saveSuccess'))
      savedConfig.value = { ...globalConfig.value }
    } else {
      ElMessage.error(response.data?.message || t('circuitBreaker.globalConfig.messages.saveFailed'))
    }
  } catch (error) {
    console.error('Failed to save global config:', error)
    ElMessage.error(t('circuitBreaker.globalConfig.messages.saveFailed'))
  } finally {
    savingConfig.value = false
  }
}

// 重置全局配置会把熔断参数恢复出厂值（影响所有服务），属不可逆操作 ⇒ 必须二次确认。
// 审计 #460：原实现点一下就直接发请求，且**整个文件没有任何确认语句**。
const resetGlobalConfig = () => {
  ElMessageBox.confirm(
    t('circuitBreaker.globalConfig.confirmations.resetMessage'),
    t('circuitBreaker.globalConfig.confirmations.resetTitle'),
    {
      confirmButtonText: t('circuitBreaker.globalConfig.confirmations.confirm'),
      cancelButtonText: t('circuitBreaker.globalConfig.confirmations.cancel'),
      type: 'warning'
    }
  ).then(async () => {
    try {
      const response = await request.post('/config/circuit-breaker/global-config/reset')
      if (response.data?.success) {
        const config = response.data.data
        if (config) {
          globalConfig.value = normalizeConfig(config)
          savedConfig.value = { ...globalConfig.value }
        } else {
          await loadGlobalConfig()
        }
        ElMessage.info(t('circuitBreaker.globalConfig.messages.resetSuccess'))
      }
    } catch (error) {
      console.error('Failed to reset global config:', error)
      ElMessage.error(t('circuitBreaker.globalConfig.messages.resetFailed'))
    }
  }).catch(() => {
    // 用户取消（ElMessageBox 以 'cancel' reject）不当作错误，也不发任何请求
  })
}

onMounted(() => {
  loadGlobalConfig()
})
</script>

<style scoped>
.config-card {
  box-shadow: var(--ja-shadow-lg);
  border-radius: var(--ja-radius-lg);
}

.config-form {
  padding: 20px;
}

.form-section__title {
  margin: 0 0 12px;
  font-size: 14px;
  font-weight: 600;
  color: var(--ja-text-primary);
}

.config-form :deep(.el-divider) {
  margin: 8px 0 20px;
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

.field-with-unit {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.field-unit {
  font-size: 12px;
  color: var(--ja-text-secondary);
  white-space: nowrap;
}

.field-with-hint {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}

.field-hint {
  margin: 0;
  font-size: 12px;
  line-height: 1.5;
  color: var(--ja-text-secondary);
}

.config-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 12px;
  margin-top: 24px;
  padding-top: 16px;
  border-top: 1px solid var(--ja-border);
}

.dirty-indicator {
  font-size: 13px;
  color: var(--ja-text-secondary);
}

.dirty-indicator.is-dirty {
  color: var(--el-color-primary);
}

.config-footer__actions {
  display: flex;
  gap: 8px;
  margin-left: auto;
}

.config-footer__actions :deep(.el-button + .el-button) {
  margin-left: 0;
}
</style>
