<template>
  <ConfigPageShell
    :title="t('sanitization.pageTitle')"
    :description="t('sanitization.primaryUseCaseHint')"
    :loading="loading"
    :saving="saving"
    :saving-label="savingLabel"
    :dirty-count="dirtyCount"
    :can-write="canWrite"
    save-permission="security:sanitization:manage"
    @refresh="loadAll"
    @save="handleSave"
    @discard="resetDraft"
  >
    <MetricStrip :items="metricItems" :loading="loading" :columns="3" class="overview-strip" />

    <el-alert
      v-if="!config && !loading"
      type="warning"
      :closable="false"
      show-icon
      :title="t('sanitization.pageTitle')"
      :description="t('sanitization.loadFailed')"
      class="load-alert"
    />

    <!-- 三类内容语义不同（可编辑配置 / 只读规则 / 样例试跑），拆 tab 避免把只读卡夹在可编辑卡之间 -->
    <el-tabs v-model="activeTab" class="sanitization-tabs">
      <el-tab-pane :label="t('sanitization.tabConfig')" name="config">
        <ConfigSectionCard :title="t('sanitization.responseSection')">
          <!-- 同一条常驻警示：响应侧在出站做正则匹配 + 文本重写 -->
          <el-alert
            v-if="form.response.enabled"
            type="warning"
            :closable="false"
            show-icon
            :title="t('sanitization.enabledPerformanceWarning')"
            class="perf-alert"
          />
          <div class="config-row">
            <span class="config-label">{{ t('sanitization.enabledLabel') }}</span>
            <SettingsSwitch
              :model-value="form.response.enabled"
              :disabled="!canWrite"
              :submit="submitEnabled"
              :confirm-message="enableConfirmMessage"
              :confirm-title="t('sanitization.enableConfirmTitle')"
              :immediate-hint="t('sanitization.immediateHint')"
              @update:model-value="form.response.enabled = $event"
            />
          </div>
        </ConfigSectionCard>

        <ConfigSectionCard
          :title="t('sanitization.maskingSection')"
          :description="t('sanitization.maskingSectionDesc')"
          :error="errors.fields"
        >
          <div class="config-grid">
            <div class="config-item">
              <span class="config-label">{{ t('sanitization.maskingCharLabel') }}</span>
              <el-input
                v-model="form.response.maskingChar"
                maxlength="5"
                :disabled="!canWrite"
                style="width: 120px"
              />
            </div>
          </div>
          <div class="config-item config-item--block">
            <span class="config-label">{{ t('sanitization.piiPatternsLabel') }}</span>
            <el-input
              v-model="form.response.piiPatternsText"
              type="textarea"
              :rows="5"
              :placeholder="t('sanitization.piiPatternsPlaceholder')"
              :disabled="!canWrite"
            />
          </div>
          <div class="config-item config-item--block">
            <span class="config-label">{{ t('sanitization.sensitiveWordsLabel') }}</span>
            <el-input
              v-model="form.response.sensitiveWordsText"
              type="textarea"
              :rows="4"
              :placeholder="t('sanitization.sensitiveWordsPlaceholder')"
              :disabled="!canWrite"
            />
          </div>
        </ConfigSectionCard>

        <ConfigSectionCard
          :title="t('sanitization.strategySection')"
          :description="t('sanitization.strategySectionDesc')"
        >
          <div class="config-grid">
            <div class="config-item">
              <span class="config-label">{{ t('sanitization.preserveJsonLabel') }}</span>
              <SettingsSwitch
                :model-value="form.response.preserveJsonStructure"
                :disabled="!canWrite"
                :submit="submitPreserveJsonStructure"
                :immediate-hint="t('sanitization.immediateHint')"
                @update:model-value="form.response.preserveJsonStructure = $event"
              />
            </div>
            <div class="config-item">
              <span class="config-label">{{ t('sanitization.logSanitizationLabel') }}</span>
              <SettingsSwitch
                :model-value="form.response.logSanitization"
                :disabled="!canWrite"
                :submit="submitLogSanitization"
                :immediate-hint="t('sanitization.immediateHint')"
                @update:model-value="form.response.logSanitization = $event"
              />
            </div>
            <div class="config-item">
              <span class="config-label">{{ t('sanitization.failOnErrorLabel') }}</span>
              <SettingsSwitch
                :model-value="form.response.failOnError"
                :disabled="!canWrite"
                :submit="submitFailOnError"
                :immediate-hint="t('sanitization.immediateHint')"
                @update:model-value="form.response.failOnError = $event"
              />
            </div>
          </div>
        </ConfigSectionCard>
      </el-tab-pane>

      <el-tab-pane :label="t('sanitization.tabRules')" name="rules">
        <ConfigSectionCard
          :title="t('sanitization.scopeTitle')"
          :description="t('sanitization.scopeDesc')"
        >
          <template #actions>
            <el-tag size="small" effect="plain" :type="scopeInfo?.mode === 'include' ? 'warning' : 'info'">
              {{ scopeInfo?.mode === 'include' ? t('sanitization.scopeModeInclude') : t('sanitization.scopeModeExclude') }}
            </el-tag>
            <el-tag size="small" effect="plain" type="info">
              {{ scopeInfo?.usingBuiltInDefaults ? t('sanitization.scopeSourceBuiltin') : t('sanitization.scopeSourceConfig') }}
            </el-tag>
            <el-tag size="small" effect="plain" type="info">{{ t('sanitization.readonlyTag') }}</el-tag>
          </template>
          <el-alert
            v-if="scopeInfo?.warning"
            type="error"
            :closable="false"
            show-icon
            :title="scopeInfo.warning"
            class="scope-warning"
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
        </ConfigSectionCard>

        <ConfigSectionCard
          :title="t('sanitization.rulesTitle')"
          :description="t('sanitization.rulesCountDesc', { count: ruleCount })"
        >
          <template #actions>
            <el-tag size="small" effect="plain" type="info">{{ t('sanitization.readonlyTag') }}</el-tag>
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
        </ConfigSectionCard>
      </el-tab-pane>

      <el-tab-pane :label="t('sanitization.tabTest')" name="test">
        <ConfigSectionCard :title="t('sanitization.testTitle')">
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
                    class="matched-rule"
                  >{{ id }}</el-tag>
                  <span v-if="!testResult.matchedRuleIds?.length">—</span>
                </div>
              </el-form-item>
            </template>
          </el-form>
        </ConfigSectionCard>
      </el-tab-pane>
    </el-tabs>
  </ConfigPageShell>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useI18n } from 'vue-i18n'
import ConfigPageShell from '@/components/config/ConfigPageShell.vue'
import ConfigSectionCard from '@/components/config/ConfigSectionCard.vue'
import SettingsSwitch from '@/components/config/SettingsSwitch.vue'
import MetricStrip from '@/components/MetricStrip.vue'
import type { MetricStripItem } from '@/components/MetricStrip.vue'
import { usePermission } from '@/composables/usePermission'
import { useConfigDraft } from '@/composables/useConfigDraft'
import { getErrorMessage } from '@/utils/error'
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
const { hasPermission } = usePermission()

/** 写操作统一走 security:sanitization:manage（模板里原本就挂在这个码上）。 */
const canWrite = computed(() => hasPermission('security:sanitization:manage'))

const loading = ref(false)
const loadingRules = ref(false)
const testing = ref(false)
const config = ref<SanitizationConfig | null>(null)
const rules = ref<SanitizationRuleItem[]>([])
const testSample = ref('联系人手机 13800138000，邮箱 user@example.com')
const testResult = ref<SanitizationTestResult | null>(null)

/** 页面拆成三个 tab：可编辑配置 / 只读规则 / 样例试跑 */
const activeTab = ref<'config' | 'rules' | 'test'>('config')

const scopeInfo = computed(() => config.value?.response?.scope ?? null)
const scopePreviewPath = ref('/v1/chat/completions')
const scopePreview = ref<SanitizationScopePreview | null>(null)
const previewing = ref(false)
const ruleCount = computed(() => config.value?.ruleCount ?? rules.value.length)

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

/**
 * 表单只有两个用途：开关的受控值（拨动 → 提交 → 回写）与三个文本字段的草稿。
 * 开关不走草稿（拨动即写库），文本字段走草稿（页脚统一提交），两者互不干扰。
 */
const form = reactive<{ response: SideForm }>({
  response: emptySide()
})

/** 文本字段的基线 = 最近一次加载/保存成功的服务端值；`放弃修改` 与 dirty 判定都以它为准。 */
const baseline = reactive({
  maskingChar: '*',
  piiPatternsText: '',
  sensitiveWordsText: ''
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

/**
 * 开启确认只在「关 → 开」时弹：SettingsSwitch 每次拨动都会看 confirmMessage，
 * 所以已启用时必须把它算成空串，否则关闭也要过一遍性能警告。
 */
const enableConfirmMessage = computed(() =>
  form.response.enabled ? '' : t('sanitization.enableConfirmMessage')
)

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

/** 全量回填：仅在加载/刷新时调用，保存后不要用它覆盖草稿（会把用户正在编辑的文本冲掉）。 */
const syncForm = () => {
  if (!config.value) return
  form.response = sideToForm(config.value.response)
  baseline.maskingChar = form.response.maskingChar
  baseline.piiPatternsText = form.response.piiPatternsText
  baseline.sensitiveWordsText = form.response.sensitiveWordsText
}

const fieldsDirty = (): boolean =>
  form.response.maskingChar !== baseline.maskingChar
  || form.response.piiPatternsText !== baseline.piiPatternsText
  || form.response.sensitiveWordsText !== baseline.sensitiveWordsText

/** 规则表由后端按当前配置热重建，刷新失败不影响已提交的保存结论。 */
const reloadRules = async (): Promise<void> => {
  loadingRules.value = true
  try {
    rules.value = await getSanitizationRules()
  } catch {
    // 规则表只是回显；保留旧表，避免把一次成功的保存显示成失败
  } finally {
    loadingRules.value = false
  }
}

const { saving, savingLabel, errors, dirtyCount, save, clearError } = useConfigDraft(
  [
    {
      key: 'fields',
      label: () => t('sanitization.maskingSection'),
      isDirty: fieldsDirty,
      validate: () => {
        const maskingChar = form.response.maskingChar.trim()
        // 后端同样校验 1-5（含非空白）；正则合法性交给 SafeRegexValidator，前端不复制
        if (maskingChar.length === 0 || maskingChar.length > 5) {
          return t('sanitization.maskingCharInvalid')
        }
        return null
      },
      save: async () => {
        const payload: Partial<SanitizationSubConfig> = {}
        if (form.response.maskingChar !== baseline.maskingChar) {
          payload.maskingChar = form.response.maskingChar
        }
        if (form.response.piiPatternsText !== baseline.piiPatternsText) {
          payload.piiPatterns = linesToArr(form.response.piiPatternsText)
        }
        if (form.response.sensitiveWordsText !== baseline.sensitiveWordsText) {
          payload.sensitiveWords = linesToArr(form.response.sensitiveWordsText)
        }
        if (Object.keys(payload).length === 0) {
          return
        }
        config.value = await updateSanitizationConfig({ response: payload })
        await reloadRules()
      },
      commit: () => {
        baseline.maskingChar = form.response.maskingChar
        baseline.piiPatternsText = form.response.piiPatternsText
        baseline.sensitiveWordsText = form.response.sensitiveWordsText
      }
    }
  ],
  { fallbackError: () => t('sanitization.saveFailed') }
)

const handleSave = async (): Promise<void> => {
  const result = await save()
  if (!result.ok) {
    if (result.failed) {
      ElMessage.error(result.failed.message)
    }
    return
  }
  if (result.saved.length > 0) {
    ElMessage.success(t('sanitization.saveSuccess'))
  }
}

const resetDraft = (): void => {
  form.response.maskingChar = baseline.maskingChar
  form.response.piiPatternsText = baseline.piiPatternsText
  form.response.sensitiveWordsText = baseline.sensitiveWordsText
  clearError()
}

/** 即时开关：失败时抛错，SettingsSwitch 会把开关拨回原位。 */
const submitEnabled = async (next: boolean): Promise<void> => {
  config.value = await updateSanitizationConfig({ response: { enabled: next } })
  ElMessage.success(t('sanitization.saveSuccess'))
}

const submitPreserveJsonStructure = async (next: boolean): Promise<void> => {
  config.value = await updateSanitizationConfig({ response: { preserveJsonStructure: next } })
  ElMessage.success(t('sanitization.saveSuccess'))
}

const submitLogSanitization = async (next: boolean): Promise<void> => {
  config.value = await updateSanitizationConfig({ response: { logSanitization: next } })
  ElMessage.success(t('sanitization.saveSuccess'))
}

const submitFailOnError = async (next: boolean): Promise<void> => {
  config.value = await updateSanitizationConfig({ response: { failOnError: next } })
  ElMessage.success(t('sanitization.saveSuccess'))
}

const loadAll = async (): Promise<void> => {
  loading.value = true
  loadingRules.value = true
  try {
    const [cfg, ruleList] = await Promise.all([getSanitizationConfig(), getSanitizationRules()])
    config.value = cfg
    rules.value = ruleList
    syncForm()
    clearError()
  } catch {
    ElMessage.error(t('sanitization.loadFailed'))
  } finally {
    loading.value = false
    loadingRules.value = false
  }
}

const handleTest = async (): Promise<void> => {
  if (!testSample.value.trim()) {
    ElMessage.warning(t('sanitization.testSamplePlaceholder'))
    return
  }
  testing.value = true
  try {
    testResult.value = await testSanitization(testSample.value)
  } catch (e) {
    ElMessage.error(getErrorMessage(e) || t('sanitization.saveFailed'))
  } finally {
    testing.value = false
  }
}

/** 预览某个路径是否会被脱敏（服务端同一套匹配，避免前后端判定漂移） */
const handlePreviewScope = async (): Promise<void> => {
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
.overview-strip {
  margin-bottom: var(--ja-space-4);
}

.load-alert,
.perf-alert,
.scope-warning {
  margin-bottom: var(--ja-space-3);
}

.config-row {
  display: flex;
  align-items: center;
  gap: var(--ja-space-3);
}

.config-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
  gap: 20px;
}

.config-item {
  display: flex;
  flex-direction: column;
  gap: 8px;
  min-width: 0;
}

.config-item--block {
  margin-top: 20px;
}

.config-label {
  font-size: 13px;
  font-weight: 500;
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
  font-family: var(--el-font-family-mono, monospace);
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
  font-family: var(--el-font-family-mono, monospace);
  background: var(--ja-surface-2);
  border-radius: 4px;
  padding: 1px 4px;
}

.scope-hint {
  margin: var(--ja-space-3) 0 0;
  color: var(--ja-ink-3);
  font-size: 12px;
}

.matched-rule {
  margin-right: 6px;
}
</style>
