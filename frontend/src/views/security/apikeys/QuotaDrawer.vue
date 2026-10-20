<template>
  <el-drawer
    v-model="quotaDrawerVisible"
    :title="t('apiKeys.quotaDrawerTitle')"
    size="420px"
  >
    <div class="quota-drawer-body">
      <p class="quota-key-id">{{ quotaDrawer.keyId }}</p>
      <el-alert v-if="quotaDrawer.detail" type="info" :closable="false" style="margin-bottom: 12px">
        <div>
          {{ t('apiKeys.quotaForm.todayRequests') }}：
          {{ quotaDrawer.detail.todayRequestCount }} /
          {{ formatQuotaLimit(quotaDrawer.detail.dailyRequestLimit, t('apiKeys.quotaPresets.unlimited')) }}
          （{{ t('apiKeys.quotaForm.remainingRequests') }}：
          {{ formatRemaining(quotaDrawer.detail.remainingRequests, quotaDrawer.detail.dailyRequestLimit, t('apiKeys.quotaPresets.unlimited')) }}）
        </div>
        <div>
          {{ t('apiKeys.quotaForm.todayTokens') }}：
          {{ quotaDrawer.detail.todayTokenUsage }} /
          {{ formatQuotaLimit(quotaDrawer.detail.dailyTokenLimit, t('apiKeys.quotaPresets.unlimited')) }}
          （{{ t('apiKeys.quotaForm.remainingTokens') }}：
          {{ formatRemaining(quotaDrawer.detail.remainingTokens, quotaDrawer.detail.dailyTokenLimit, t('apiKeys.quotaPresets.unlimited')) }}）
        </div>
      </el-alert>
      <el-alert
        v-else-if="quotaDrawerLoading"
        type="info"
        :closable="false"
        :title="t('common.loading')"
        style="margin-bottom: 12px"
      />

      <el-form label-width="140px">
        <el-form-item :label="t('apiKeys.quotaForm.applyPreset')">
          <el-select v-model="quotaDrawer.presetId" style="width: 100%" @change="applyQuotaPreset">
            <el-option
              v-for="p in QUOTA_PRESETS"
              :key="p.id"
              :label="t(p.labelKey)"
              :value="p.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('apiKeys.quotaForm.dailyRequestLimit')">
          <el-input-number v-model="quotaDrawer.form.dailyRequestLimit" :min="0" :step="100" style="width: 100%" />
        </el-form-item>
        <el-form-item :label="t('apiKeys.quotaForm.dailyTokenLimit')">
          <el-input-number v-model="quotaDrawer.form.dailyTokenLimit" :min="0" :step="1000" style="width: 100%" />
        </el-form-item>
        <el-form-item :label="t('apiKeys.quotaForm.rateLimitPerMinute')">
          <el-input-number v-model="quotaDrawer.form.rateLimitPerMinute" :min="0" :step="10" style="width: 100%" />
        </el-form-item>
        <el-form-item :label="t('apiKeys.quotaForm.quotaAlertThreshold')">
          <el-slider
            v-model="quotaDrawer.form.quotaAlertThreshold"
            :min="0.05"
            :max="1"
            :step="0.05"
            show-input
            :format-tooltip="(val: number) => `${Math.round(val * 100)}%`"
          />
        </el-form-item>
      </el-form>
      <div class="quota-drawer-actions">
        <el-button type="primary" :loading="quotaDrawerSaving" @click="saveQuotaFromDrawer">
          {{ t('apiKeys.quotaForm.saveQuota') }}
        </el-button>
        <el-button type="warning" @click="handleResetQuotaFromDrawer">
          {{ t('apiKeys.quotaForm.resetQuota') }}
        </el-button>
      </div>
    </div>
  </el-drawer>
</template>

<script setup lang="ts">
/**
 * 行内「配额」按钮打开的配额抽屉（v3.2.1）。
 *
 * 拆分前是 `ApiKeyManagement.vue` 里的 `el-drawer` 整块 + `openQuotaDrawer` /
 * `applyQuotaPreset` / `saveQuotaFromDrawer` / `handleResetQuotaFromDrawer` 四个函数，
 * 这里逐字搬运，只把「保存/重置成功后刷新列表与告警」抽成 `refresh` 回调
 * （父组件传入 `() => Promise.all([fetchApiKeys(), fetchQuotaAlerts()])`），
 * 因此 await 顺序、loading 生命周期与提示文案都保持不变。
 *
 * E2E 契约（e2e/specs/11-deep-security-quota.mjs）依赖：
 * - `.quota-drawer-body` / `.quota-drawer-actions` 两个类名（后者需 flex + 居中）
 * - 抽屉内第一个 `.el-select` 是预设选择器、≥3 个 `.el-input-number`
 * - 按钮文案「保存配额 / 重置计数」
 */
import { reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getErrorMessage } from '@/utils/error'
import { getApiKeyQuota, resetApiKeyQuota } from '@/api/apiKey'
import { updateApiKeyQuota } from '@/api/apiKeyQuotaOps'
import { QUOTA_PRESETS, formatQuotaLimit, formatRemaining } from '@/utils/quotaOps'
import type { ApiKeyVO, QuotaUsageDetail } from '@/types'

const props = defineProps<{
  /** 保存/重置成功后刷新父组件的列表与告警（原样替代 Promise.all([fetchApiKeys, fetchQuotaAlerts])） */
  refresh: () => Promise<void>
}>()

const { t } = useI18n()

const quotaDrawerVisible = ref(false)
const quotaDrawerLoading = ref(false)
const quotaDrawerSaving = ref(false)
const quotaDrawer = reactive({
  keyId: '',
  presetId: 'standard',
  detail: null as QuotaUsageDetail | null,
  form: {
    dailyRequestLimit: 0,
    dailyTokenLimit: 0,
    rateLimitPerMinute: 0,
    quotaAlertThreshold: 0.8
  }
})

const openQuotaDrawer = async (row: ApiKeyVO) => {
  quotaDrawer.keyId = row.keyId
  quotaDrawer.presetId = ''
  quotaDrawer.detail = null
  // 先用列表数据填表单，立即可操作；详情异步刷新，不阻塞打开
  quotaDrawer.form = {
    dailyRequestLimit: row.dailyRequestLimit || 0,
    dailyTokenLimit: row.dailyTokenLimit || 0,
    rateLimitPerMinute: row.rateLimitPerMinute || 0,
    quotaAlertThreshold: row.quotaAlertThreshold ?? 0.8
  }
  quotaDrawerVisible.value = true
  quotaDrawerLoading.value = true
  try {
    const detail = await getApiKeyQuota(row.keyId)
    quotaDrawer.detail = detail
    quotaDrawer.form = {
      dailyRequestLimit: detail.dailyRequestLimit || 0,
      dailyTokenLimit: detail.dailyTokenLimit || 0,
      rateLimitPerMinute: detail.rateLimitPerMinute || 0,
      quotaAlertThreshold: detail.quotaAlertThreshold ?? 0.8
    }
  } catch (error) {
    console.error('获取配额详情失败:', error)
  } finally {
    quotaDrawerLoading.value = false
  }
}

const applyQuotaPreset = (presetId: string) => {
  const preset = QUOTA_PRESETS.find(p => p.id === presetId)
  if (!preset) return
  quotaDrawer.form = { ...preset.limits }
}

const saveQuotaFromDrawer = async () => {
  if (!quotaDrawer.keyId) return
  quotaDrawerSaving.value = true
  try {
    const detail = await updateApiKeyQuota(quotaDrawer.keyId, { ...quotaDrawer.form })
    quotaDrawer.detail = detail
    ElMessage.success(t('apiKeys.quotaForm.saveSuccess'))
    await props.refresh()
  } catch (e) {
    ElMessage.error(getErrorMessage(e) || t('apiKeys.quotaForm.saveQuota'))
  } finally {
    quotaDrawerSaving.value = false
  }
}

// 重置配额计数器会清零今日请求数与 Token 用量 ⇒ 必须二次确认。
// 审计 #460：**同一个操作在本文件里有两套入口，只有表格行那套有确认**（:989 用了
// apiKeys.confirmations.quotaResetTitle/Message），抽屉里这套没有 —— 典型的
// 「同语义不同入口不一致」。这里复用同一组键，不新增文案。
// 守卫见 11-deep-security-quota.mjs 的 deep-quota-drawer-reset-confirm:*。
const handleResetQuotaFromDrawer = () => {
  if (!quotaDrawer.keyId) return
  const keyId = quotaDrawer.keyId
  ElMessageBox.confirm(
    t('apiKeys.confirmations.quotaResetMessage', { keyId }),
    t('apiKeys.confirmations.quotaResetTitle'),
    {
      confirmButtonText: t('apiKeys.confirmations.confirmReset'),
      cancelButtonText: t('apiKeys.actions.cancel'),
      type: 'warning'
    }
  ).then(async () => {
    try {
      await resetApiKeyQuota(keyId)
      quotaDrawer.detail = await getApiKeyQuota(keyId)
      ElMessage.success(t('apiKeys.quotaForm.resetSuccess'))
      await props.refresh()
    } catch (e) {
      ElMessage.error(getErrorMessage(e) || t('apiKeys.quotaForm.resetQuota'))
    }
  })
}

defineExpose({ open: openQuotaDrawer })
</script>

<style scoped>
.quota-drawer-body {
  display: flex;
  flex-direction: column;
}

.quota-key-id {
  font-family: monospace;
  color: var(--ja-text-secondary, #888);
  margin-bottom: 8px;
}

.quota-drawer-actions {
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 12px;
  margin-top: 20px;
  padding-top: 8px;
}

.quota-drawer-actions .el-button {
  min-width: 96px;
}
</style>
