<template>
  <PageSkeleton :title="t('sanitization.pageTitle')">
    <template #actions>
      <el-button @click="loadAll" :loading="loading" size="default">
        <el-icon><Refresh /></el-icon>
        {{ t('sanitization.refresh') }}
      </el-button>
    </template>

    <template #stats>
      <!-- 脱敏指标：中性指标条；只有「未启用」属于风险状态，才给 warning -->
      <MetricStrip :items="metricItems" :loading="loading" :columns="3" />
    </template>

    <el-alert
      type="info"
      :closable="false"
      show-icon
      :title="t('sanitization.pageTitle')"
      :description="t('sanitization.primaryUseCaseHint')"
      style="margin-bottom: var(--ja-space-4)"
    />

    <el-row :gutter="16" style="margin-bottom: var(--ja-space-4)">
      <el-col :span="24">
        <el-card shadow="hover">
          <template #header>
            <span class="card-title">{{ t('sanitization.responseSection') }}</span>
          </template>
          <!-- 同一条常驻警示：响应侧在出站做正则匹配 + 文本重写 -->
          <el-alert
            v-if="form.response.enabled"
            type="warning"
            :closable="false"
            show-icon
            :title="t('sanitization.enabledPerformanceWarning')"
            style="margin-bottom: var(--ja-space-3)"
          />
          <el-form label-width="140px">
            <el-form-item :label="t('sanitization.enabledLabel')">
              <el-switch v-model="form.response.enabled" :before-change="confirmEnable" />
            </el-form-item>
            <el-form-item :label="t('sanitization.maskingCharLabel')">
              <el-input v-model="form.response.maskingChar" maxlength="5" style="width: 120px" />
            </el-form-item>
            <el-form-item :label="t('sanitization.piiPatternsLabel')">
              <el-input
                v-model="form.response.piiPatternsText"
                type="textarea"
                :rows="5"
                :placeholder="t('sanitization.piiPatternsPlaceholder')"
              />
            </el-form-item>
            <el-form-item :label="t('sanitization.sensitiveWordsLabel')">
              <el-input
                v-model="form.response.sensitiveWordsText"
                type="textarea"
                :rows="4"
                :placeholder="t('sanitization.sensitiveWordsPlaceholder')"
              />
            </el-form-item>
            <el-form-item :label="t('sanitization.preserveJsonLabel')">
              <el-switch v-model="form.response.preserveJsonStructure" />
            </el-form-item>
            <el-form-item :label="t('sanitization.logSanitizationLabel')">
              <el-switch v-model="form.response.logSanitization" />
            </el-form-item>
            <el-form-item :label="t('sanitization.failOnErrorLabel')">
              <el-switch v-model="form.response.failOnError" />
            </el-form-item>
          </el-form>
        </el-card>
      </el-col>
    </el-row>

    <el-card shadow="hover" style="margin-bottom: var(--ja-space-4)">
      <div class="config-actions">
        <el-button type="primary" :loading="saving" @click="handleSave">
          <el-icon><Check /></el-icon>
          {{ t('sanitization.save') }}
        </el-button>
        <el-button :disabled="saving" @click="syncForm">
          {{ t('sanitization.reset') }}
        </el-button>
      </div>
    </el-card>

    <el-card shadow="hover" style="margin-bottom: var(--ja-space-4)">
      <template #header>
        <div class="scope-header">
          <span class="card-title">{{ t('sanitization.scopeTitle') }}</span>
          <el-tag size="small" effect="plain" :type="scopeInfo?.mode === 'include' ? 'warning' : 'info'">
            {{ scopeInfo?.mode === 'include' ? t('sanitization.scopeModeInclude') : t('sanitization.scopeModeExclude') }}
          </el-tag>
          <el-tag size="small" effect="plain" type="info">
            {{ scopeInfo?.usingBuiltInDefaults ? t('sanitization.scopeSourceBuiltin') : t('sanitization.scopeSourceConfig') }}
          </el-tag>
        </div>
      </template>
      <p class="scope-desc">{{ t('sanitization.scopeDesc') }}</p>
      <el-alert
        v-if="scopeInfo?.warning"
        type="error"
        :closable="false"
        show-icon
        :title="scopeInfo.warning"
        style="margin-bottom: var(--ja-space-3)"
      />
      <div class="scope-patterns">
        <span class="scope-label">{{ t('sanitization.scopePatternsLabel') }}</span>
        <template v-if="scopeInfo?.patterns?.length">
          <el-tag
            v-for="pattern in scopeInfo.patterns"
            :key="pattern"
            size="small"
            effect="plain"
            class="scope-pattern"
          >{{ pattern }}</el-tag>
        </template>
        <span v-else class="scope-empty">{{ t('sanitization.scopeEmptyPatterns') }}</span>
      </div>
      <div class="scope-preview">
        <el-input
          v-model="scopePreviewPath"
          :placeholder="t('sanitization.scopePreviewPlaceholder')"
          class="scope-preview-input"
          @keyup.enter="handlePreviewScope"
        />
        <el-button :loading="previewing" @click="handlePreviewScope">
          {{ t('sanitization.scopePreviewButton') }}
        </el-button>
        <span v-if="scopePreview" class="scope-preview-result">
          <el-tag size="small" :type="scopePreview.shouldSanitize ? 'success' : 'info'" effect="plain">
            {{ scopePreview.shouldSanitize ? t('sanitization.scopePreviewSanitized') : t('sanitization.scopePreviewSkipped') }}
          </el-tag>
          <span v-if="scopePreview.matchedPattern" class="scope-preview-matched">
            {{ t('sanitization.scopePreviewMatched') }} <code>{{ scopePreview.matchedPattern }}</code>
          </span>
        </span>
      </div>
      <p class="scope-hint">{{ t('sanitization.scopeNotConfigurableHint') }}</p>
    </el-card>

    <el-row :gutter="16">
      <el-col :span="12">
        <el-card shadow="hover">
          <template #header>
            <span class="card-title">{{ t('sanitization.rulesTitle') }}</span>
          </template>
          <el-table :data="rules" stripe size="small" v-loading="loadingRules">
            <el-table-column prop="ruleId" :label="t('sanitization.ruleIdColumn')" min-width="140" show-overflow-tooltip />
            <el-table-column prop="type" :label="t('sanitization.typeColumn')" width="120" />
            <el-table-column prop="pattern" :label="t('sanitization.patternColumn')" min-width="160" show-overflow-tooltip />
            <el-table-column prop="enabled" :label="t('sanitization.enabledColumn')" width="90" align="center">
              <template #default="{ row }">
                <el-tag :type="row.enabled ? 'success' : 'info'" effect="plain" size="small">
                  {{ row.enabled ? t('common.enabled') : t('common.disabled') }}
                </el-tag>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card shadow="hover">
          <template #header>
            <span class="card-title">{{ t('sanitization.testTitle') }}</span>
          </template>
          <el-form label-width="100px">
            <el-form-item :label="t('sanitization.testSampleLabel')">
              <el-input
                v-model="testSample"
                type="textarea"
                :rows="4"
                :placeholder="t('sanitization.testSamplePlaceholder')"
              />
            </el-form-item>
            <el-form-item>
              <el-button :loading="testing" @click="handleTest">
                {{ t('sanitization.runTest') }}
              </el-button>
            </el-form-item>
            <template v-if="testResult">
              <el-form-item :label="t('sanitization.beforeLabel')">
                <el-input type="textarea" :rows="3" :model-value="testResult.before" readonly />
              </el-form-item>
              <el-form-item :label="t('sanitization.afterLabel')">
                <el-input type="textarea" :rows="3" :model-value="testResult.after" readonly />
              </el-form-item>
              <el-form-item :label="t('sanitization.matchedLabel')">
                <div>
                  <el-tag
                    v-for="id in testResult.matchedRuleIds"
                    :key="id"
                    size="small"
                    style="margin-right: 6px"
                  >{{ id }}</el-tag>
                  <span v-if="!testResult.matchedRuleIds?.length">—</span>
                </div>
              </el-form-item>
            </template>
          </el-form>
        </el-card>
      </el-col>
    </el-row>
  </PageSkeleton>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getErrorMessage } from '@/utils/error'
import { Refresh, Check } from '@element-plus/icons-vue'
import { useI18n } from 'vue-i18n'
import PageSkeleton from '@/components/PageSkeleton.vue'
import MetricStrip from '@/components/MetricStrip.vue'
import type { MetricStripItem } from '@/components/MetricStrip.vue'
import {
  getSanitizationConfig,
  getSanitizationRules,
  previewSanitizationScope,
  testSanitization,
  updateSanitizationConfig,
  type SanitizationConfig,
  type SanitizationRuleItem,
  type SanitizationScopePreview,
  type SanitizationSubConfig,
  type SanitizationTestResult
} from '@/api/sanitization'

const { t } = useI18n()

const loading = ref(false)
const loadingRules = ref(false)
const saving = ref(false)
const testing = ref(false)
const config = ref<SanitizationConfig | null>(null)
const rules = ref<SanitizationRuleItem[]>([])
const testSample = ref('联系人手机 13800138000，邮箱 user@example.com')
const testResult = ref<SanitizationTestResult | null>(null)

const scopeInfo = computed(() => config.value?.response?.scope ?? null)
const scopePreviewPath = ref('/v1/chat/completions')
const scopePreview = ref<SanitizationScopePreview | null>(null)
const previewing = ref(false)

interface SideForm {
  enabled: boolean
  maskingChar: string
  piiPatternsText: string
  sensitiveWordsText: string
  logSanitization: boolean
  failOnError: boolean
  preserveJsonStructure: boolean
}

const emptySide = (): SideForm => ({
  enabled: false,
  maskingChar: '*',
  piiPatternsText: '',
  sensitiveWordsText: '',
  logSanitization: false,
  failOnError: false,
  preserveJsonStructure: true
})

const form = reactive<{ response: SideForm }>({
  response: emptySide()
})

/** 顶部指标条：数值语义与原 KPI 卡一致，只有「未启用」是风险状态 */
const metricItems = computed<MetricStripItem[]>(() => {
  const responseEnabled = config.value?.response?.enabled ?? false
  return [
    {
      key: 'ruleCount',
      label: t('sanitization.ruleCountLabel'),
      value: config.value?.ruleCount ?? '—'
    },
    {
      key: 'responseEnabled',
      label: t('sanitization.responseEnabledLabel'),
      value: responseEnabled ? t('common.enabled') : t('common.disabled'),
      tone: responseEnabled ? 'default' : 'warning'
    },
    {
      key: 'recordNote',
      label: t('sanitization.recordNote'),
      value: t('sanitization.recordNoteValue')
    }
  ]
})

const linesToArr = (text: string): string[] =>
  text
    .split('\n')
    .map(s => s.trim())
    .filter(Boolean)

const arrToLines = (arr?: string[]): string[] => (arr || []).map(String)

const sideToForm = (side: SanitizationSubConfig | undefined): SideForm => ({
  enabled: side?.enabled ?? false,
  maskingChar: side?.maskingChar ?? '*',
  piiPatternsText: arrToLines(side?.piiPatterns).join('\n'),
  sensitiveWordsText: arrToLines(side?.sensitiveWords).join('\n'),
  logSanitization: side?.logSanitization ?? false,
  failOnError: side?.failOnError ?? false,
  preserveJsonStructure: side?.preserveJsonStructure ?? true
})

const syncForm = () => {
  if (!config.value) return
  form.response = sideToForm(config.value.response)
}

/**
 * 开启响应脱敏前的二次确认（挂到 `el-switch` 的 `before-change`）。
 * 关闭（true → false）无需确认，直接放行；只有 false → true 才弹确认框。
 * 取消时返回 false ⇒ Element Plus 不更新 v-model，开关保持关闭。
 */
const confirmEnable = async (): Promise<boolean> => {
  if (form.response.enabled) return true
  try {
    await ElMessageBox.confirm(
      t('sanitization.enableConfirmMessage'),
      t('sanitization.enableConfirmTitle'),
      {
        type: 'warning',
        confirmButtonText: t('common.confirm'),
        cancelButtonText: t('common.cancel')
      }
    )
    return true
  } catch {
    return false
  }
}

const loadAll = async () => {
  loading.value = true
  loadingRules.value = true
  try {
    const [cfg, ruleList] = await Promise.all([getSanitizationConfig(), getSanitizationRules()])
    config.value = cfg
    rules.value = ruleList
    syncForm()
  } catch (e) {
    ElMessage.error(t('sanitization.loadFailed'))
  } finally {
    loading.value = false
    loadingRules.value = false
  }
}

const handleSave = async () => {
  saving.value = true
  try {
    const updated = await updateSanitizationConfig({
      response: {
        enabled: form.response.enabled,
        maskingChar: form.response.maskingChar,
        piiPatterns: linesToArr(form.response.piiPatternsText),
        sensitiveWords: linesToArr(form.response.sensitiveWordsText),
        logSanitization: form.response.logSanitization,
        failOnError: form.response.failOnError,
        preserveJsonStructure: form.response.preserveJsonStructure
      }
    })
    config.value = updated
    rules.value = await getSanitizationRules()
    ElMessage.success(t('sanitization.saveSuccess'))
  } catch (e) {
    ElMessage.error(getErrorMessage(e) || t('sanitization.saveFailed'))
  } finally {
    saving.value = false
  }
}

const handleTest = async () => {
  if (!testSample.value.trim()) {
    ElMessage.warning(t('sanitization.testSamplePlaceholder'))
    return
  }
  testing.value = true
  try {
    testResult.value = await testSanitization(testSample.value)
  } catch (e) {
    ElMessage.error(t('sanitization.saveFailed'))
  } finally {
    testing.value = false
  }
}

/** 预览某个路径是否会被脱敏（服务端同一套匹配，避免前后端判定漂移） */
const handlePreviewScope = async () => {
  const path = scopePreviewPath.value.trim()
  if (!path) return
  previewing.value = true
  try {
    scopePreview.value = await previewSanitizationScope(path)
  } catch (e) {
    ElMessage.error(getErrorMessage(e) || t('sanitization.loadFailed'))
  } finally {
    previewing.value = false
  }
}

onMounted(loadAll)
</script>

<style scoped>
.card-title {
  font-weight: 600;
}
.config-actions {
  display: flex;
  gap: var(--ja-space-2);
}
.scope-header {
  display: flex;
  align-items: center;
  gap: var(--ja-space-2);
}
.scope-desc {
  margin: 0 0 var(--ja-space-3);
  color: var(--ja-ink-2);
  font-size: 13px;
  line-height: 1.6;
}
.scope-patterns {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--ja-space-2);
  margin-bottom: var(--ja-space-3);
}
.scope-label {
  color: var(--ja-ink-2);
  font-size: 13px;
}
.scope-pattern {
  font-family: var(--ja-font-mono, monospace);
}
.scope-empty {
  color: var(--ja-ink-3);
  font-size: 13px;
}
.scope-preview {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--ja-space-2);
}
.scope-preview-input {
  width: 320px;
  max-width: 100%;
}
.scope-preview-result {
  display: inline-flex;
  align-items: center;
  gap: var(--ja-space-2);
  font-size: 13px;
}
.scope-preview-matched code {
  font-family: var(--ja-font-mono, monospace);
  background: var(--ja-surface-2);
  border-radius: 4px;
  padding: 1px 4px;
}
.scope-hint {
  margin: var(--ja-space-3) 0 0;
  color: var(--ja-ink-3);
  font-size: 12px;
}
</style>
