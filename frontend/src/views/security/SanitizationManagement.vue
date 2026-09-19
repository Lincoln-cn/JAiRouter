<template>
  <PageSkeleton :title="t('sanitization.pageTitle')">
    <template #actions>
      <el-button @click="loadAll" :loading="loading" size="default">
        <el-icon><Refresh /></el-icon>
        {{ t('sanitization.refresh') }}
      </el-button>
    </template>

    <template #stats>
      <el-row :gutter="16">
        <el-col :span="6">
          <StatCard
            icon="Document"
            :label="t('sanitization.ruleCountLabel')"
            :value="config?.ruleCount ?? '—'"
            tone="primary"
          />
        </el-col>
        <el-col :span="6">
          <StatCard
            :icon="config?.request?.enabled ? 'CircleCheck' : 'CircleClose'"
            :label="t('sanitization.requestEnabledLabel')"
            :value="config?.request?.enabled ? t('common.enabled') : t('common.disabled')"
            :tone="config?.request?.enabled ? 'success' : 'info'"
          />
        </el-col>
        <el-col :span="6">
          <StatCard
            :icon="config?.response?.enabled ? 'CircleCheck' : 'CircleClose'"
            :label="t('sanitization.responseEnabledLabel')"
            :value="config?.response?.enabled ? t('common.enabled') : t('common.disabled')"
            :tone="config?.response?.enabled ? 'success' : 'info'"
          />
        </el-col>
        <el-col :span="6">
          <StatCard
            icon="Lock"
            :label="t('sanitization.recordNote')"
            :value="t('sanitization.recordNoteValue')"
            tone="warning"
          />
        </el-col>
      </el-row>
    </template>

    <el-alert
      type="info"
      :closable="false"
      show-icon
      :title="t('sanitization.pageTitle')"
      :description="t('sanitization.primaryUseCaseHint')"
      style="margin-bottom: 16px"
    />

    <el-row :gutter="16" style="margin-bottom: 16px">
      <el-col :span="12">
        <el-card shadow="hover">
          <template #header>
            <span class="card-title">{{ t('sanitization.requestSection') }}</span>
          </template>
          <el-form label-width="140px">
            <el-form-item :label="t('sanitization.enabledLabel')">
              <el-switch v-model="form.request.enabled" />
            </el-form-item>
            <el-form-item :label="t('sanitization.maskingCharLabel')">
              <el-input v-model="form.request.maskingChar" maxlength="5" style="width: 120px" />
            </el-form-item>
            <el-form-item :label="t('sanitization.piiPatternsLabel')">
              <el-input
                v-model="form.request.piiPatternsText"
                type="textarea"
                :rows="5"
                :placeholder="t('sanitization.piiPatternsPlaceholder')"
              />
            </el-form-item>
            <el-form-item :label="t('sanitization.sensitiveWordsLabel')">
              <el-input
                v-model="form.request.sensitiveWordsText"
                type="textarea"
                :rows="4"
                :placeholder="t('sanitization.sensitiveWordsPlaceholder')"
              />
            </el-form-item>
            <el-form-item :label="t('sanitization.whitelistUsersLabel')">
              <el-input
                v-model="form.request.whitelistUsersText"
                type="textarea"
                :rows="2"
                :placeholder="t('sanitization.whitelistUsersPlaceholder')"
              />
            </el-form-item>
            <el-form-item :label="t('sanitization.logSanitizationLabel')">
              <el-switch v-model="form.request.logSanitization" />
            </el-form-item>
            <el-form-item :label="t('sanitization.failOnErrorLabel')">
              <el-switch v-model="form.request.failOnError" />
            </el-form-item>
          </el-form>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card shadow="hover">
          <template #header>
            <span class="card-title">{{ t('sanitization.responseSection') }}</span>
          </template>
          <el-form label-width="140px">
            <el-form-item :label="t('sanitization.enabledLabel')">
              <el-switch v-model="form.response.enabled" />
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

    <el-card shadow="hover" style="margin-bottom: 16px">
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
                <el-tag :type="row.enabled ? 'success' : 'info'" size="small">
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
              <el-button type="primary" :loading="testing" @click="handleTest">
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
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh, Check } from '@element-plus/icons-vue'
import { useI18n } from 'vue-i18n'
import PageSkeleton from '@/components/PageSkeleton.vue'
import StatCard from '@/components/StatCard.vue'
import {
  getSanitizationConfig,
  getSanitizationRules,
  testSanitization,
  updateSanitizationConfig,
  type SanitizationConfig,
  type SanitizationRuleItem,
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

interface SideForm {
  enabled: boolean
  maskingChar: string
  piiPatternsText: string
  sensitiveWordsText: string
  whitelistUsersText: string
  logSanitization: boolean
  failOnError: boolean
  preserveJsonStructure: boolean
}

const emptySide = (): SideForm => ({
  enabled: false,
  maskingChar: '*',
  piiPatternsText: '',
  sensitiveWordsText: '',
  whitelistUsersText: '',
  logSanitization: false,
  failOnError: false,
  preserveJsonStructure: true
})

const form = reactive<{ request: SideForm; response: SideForm }>({
  request: emptySide(),
  response: emptySide()
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
  whitelistUsersText: arrToLines(side?.whitelistUsers).join('\n'),
  logSanitization: side?.logSanitization ?? false,
  failOnError: side?.failOnError ?? false,
  preserveJsonStructure: side?.preserveJsonStructure ?? true
})

const syncForm = () => {
  if (!config.value) return
  form.request = sideToForm(config.value.request)
  form.response = sideToForm(config.value.response)
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
      request: {
        enabled: form.request.enabled,
        maskingChar: form.request.maskingChar,
        piiPatterns: linesToArr(form.request.piiPatternsText),
        sensitiveWords: linesToArr(form.request.sensitiveWordsText),
        whitelistUsers: linesToArr(form.request.whitelistUsersText),
        logSanitization: form.request.logSanitization,
        failOnError: form.request.failOnError
      },
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
  } catch (e: any) {
    ElMessage.error(e?.message || t('sanitization.saveFailed'))
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

onMounted(loadAll)
</script>

<style scoped>
.card-title {
  font-weight: 600;
}
.config-actions {
  display: flex;
  gap: 8px;
}
</style>
