<template>
  <PageSkeleton :title="t('apiKeys.title')">
    <template #actions>
      <el-button icon="Download" type="success" @click="handleExport">{{ t('apiKeys.exportConfig') }}</el-button>
      <el-button icon="Upload" type="warning" @click="showImportDialog">{{ t('apiKeys.importConfig') }}</el-button>
      <el-button icon="Plus" type="primary" @click="handleCreateApiKey">{{ t('apiKeys.createApiKey') }}</el-button>
    </template>

    <!-- 统计卡片 -->
    <el-row :gutter="20" class="stats-row">
      <el-col :span="6">
        <StatCard :icon="Key" :label="t('apiKeys.stats.total')" :value="listData.total" tone="primary" />
      </el-col>
      <el-col :span="6">
        <StatCard :icon="CircleCheck" :label="t('apiKeys.stats.enabled')" :value="listData.enabledCount" tone="success" />
      </el-col>
      <el-col :span="6">
        <StatCard :icon="CircleClose" :label="t('apiKeys.stats.disabled')" :value="listData.disabledCount" tone="danger" />
      </el-col>
      <el-col :span="6">
        <StatCard :icon="Warning" :label="t('apiKeys.stats.expired')" :value="listData.expiredCount" tone="warning" />
      </el-col>
    </el-row>

    <!-- 配额告警区 -->
    <el-card class="quota-alerts-card" shadow="hover" v-loading="quotaAlertsLoading">
      <template #header>
        <div class="card-header">
          <span class="main-title">
            <el-icon><Warning /></el-icon>
            {{ t('apiKeys.quotaAlertsTitle') }}
            <el-badge
              v-if="quotaAlerts.length > 0"
              :value="quotaAlerts.length"
              type="danger"
              style="margin-left: 8px"
            />
          </span>
          <el-button
            icon="Refresh"
            size="small"
            @click="fetchQuotaAlerts"
            :loading="quotaAlertsLoading"
          >
            {{ t('apiKeys.refresh') }}
          </el-button>
        </div>
      </template>

      <template v-if="quotaAlerts.length > 0">
        <el-table :data="quotaAlerts" stripe size="small" style="width: 100%">
          <el-table-column prop="keyId" :label="t('apiKeys.columns.keyId')" width="180" show-overflow-tooltip>
            <template #default="{ row }">
              <el-tag effect="plain" type="info">{{ row.keyId }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="description" :label="t('apiKeys.columns.description')" min-width="140" show-overflow-tooltip>
            <template #default="{ row }">
              {{ row.description || '-' }}
            </template>
          </el-table-column>
          <el-table-column prop="alertType" :label="t('apiKeys.columns.alertType')" width="120" align="center">
            <template #default="{ row }">
              <el-tag :type="getAlertTypeTag(row.alertType)" size="small">
                {{ formatAlertType(row.alertType) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column :label="t('apiKeys.columns.requestUsage')" width="110" align="center">
            <template #default="{ row }">
              <span :class="{ 'alert-high': row.dailyRequestUsagePercent >= 90 }">
                {{ formatPercent(row.dailyRequestUsagePercent) }}
              </span>
            </template>
          </el-table-column>
          <el-table-column :label="t('apiKeys.columns.tokenUsage')" width="110" align="center">
            <template #default="{ row }">
              <span :class="{ 'alert-high': row.dailyTokenUsagePercent >= 90 }">
                {{ formatPercent(row.dailyTokenUsagePercent) }}
              </span>
            </template>
          </el-table-column>
          <el-table-column prop="message" :label="t('apiKeys.columns.alertDetail')" min-width="200" show-overflow-tooltip />
        </el-table>
      </template>
      <template v-else>
        <div class="no-alerts">
          <el-icon style="font-size: 32px; color: var(--ja-success); margin-bottom: 8px;">
            <CircleCheck />
          </el-icon>
          <div class="no-alerts-text">{{ t('apiKeys.noQuotaAlerts') }}</div>
        </div>
      </template>
    </el-card>

    <!-- 主卡片 -->
    <el-card class="main-card" shadow="hover">

      <div class="table-wrapper">
        <el-table v-loading="loading" :data="pagedApiKeys" border stripe style="width: 100%">
        <el-table-column :label="t('apiKeys.columns.keyId')" prop="keyId" width="180">
          <template #default="scope">
            <el-tag effect="plain" type="info">{{ scope.row.keyId }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="t('apiKeys.columns.description')" min-width="150" show-overflow-tooltip>
          <template #default="scope">
            <span>{{ scope.row.description || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column :label="t('apiKeys.columns.permissions')" prop="permissions" width="160">
          <template #default="scope">
            <div class="permission-tags">
              <el-tag
                  v-for="permission in scope.row.permissions"
                  :key="permission"
                  :type="getPermissionTagType(permission)"
                  size="small"
              >
                {{ formatPermission(permission) }}
              </el-tag>
            </div>
            <span v-if="!scope.row.permissions?.length">-</span>
          </template>
        </el-table-column>
        <el-table-column :label="t('apiKeys.columns.createdAt')" width="160">
          <template #default="scope">
            {{ formatDateTime(scope.row.createdAt) }}
          </template>
        </el-table-column>
        <el-table-column :label="t('apiKeys.columns.createdBy')" width="150" show-overflow-tooltip>
          <template #default="scope">
            <span v-if="scope.row.createdBy">{{ scope.row.createdBy }}</span>
            <span v-else class="text-muted">-</span>
          </template>
        </el-table-column>
        <el-table-column :label="t('apiKeys.columns.expiresAt')" prop="expiresAt" width="160">
          <template #default="scope">
            <span :class="{ 'expired-text': scope.row.expired }">
              {{ formatDateTime(scope.row.expiresAt) || t('apiKeys.neverExpires') }}
            </span>
          </template>
        </el-table-column>
        <el-table-column :label="t('apiKeys.columns.remainingDays')" width="100" align="center">
          <template #default="scope">
            <el-tag :type="getRemainingDaysType(scope.row)" size="small">
              {{ getRemainingDaysText(scope.row) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="t('apiKeys.columns.usage')" width="140" align="center">
          <template #default="scope">
            <el-tooltip :content="t('apiKeys.usageTooltip', { success: scope.row.successfulRequests, failed: scope.row.failedRequests })">
              <span class="usage-stat">
                {{ scope.row.totalRequests }}
                <span class="usage-detail">({{ scope.row.successfulRequests }}/{{ scope.row.failedRequests }})</span>
              </span>
            </el-tooltip>
          </template>
        </el-table-column>
        <el-table-column :label="t('apiKeys.columns.todayToken')" width="120" align="center">
          <template #default="scope">
            <span v-if="scope.row.todayTokenUsage !== undefined && scope.row.todayTokenUsage !== null">
              {{ formatTokenCount(scope.row.todayTokenUsage) }}
            </span>
            <span v-else class="text-muted">-</span>
          </template>
        </el-table-column>
        <el-table-column :label="t('apiKeys.columns.alertStatus')" width="100" align="center">
          <template #default="scope">
            <el-tag v-if="scope.row.quotaAlertTriggered" type="danger" size="small">
              <el-icon><Warning /></el-icon> {{ t('apiKeys.status.alert') }}
            </el-tag>
            <el-tag v-else type="success" size="small">{{ t('apiKeys.status.normal') }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="t('apiKeys.columns.status')" prop="enabled" width="80" align="center">
          <template #default="scope">
            <el-switch
                v-model="scope.row.enabled"
                :disabled="scope.row.expired"
                active-color="var(--ja-success)"
                inactive-color="var(--ja-danger)"
                @change="handleStatusChange(scope.row)"
            />
          </template>
        </el-table-column>
        <el-table-column fixed="right" :label="t('apiKeys.columns.actions')" width="200">
          <template #default="scope">
            <div class="action-buttons">
              <el-button icon="Edit" size="small" @click="handleEdit(scope.row)">{{ t('apiKeys.actions.edit') }}</el-button>
              <el-button icon="RefreshRight" size="small" type="warning" @click="handleRotate(scope.row)"
                         :disabled="scope.row.expired">{{ t('apiKeys.actions.rotate') }}</el-button>
              <el-button icon="Refresh" size="small" type="info" @click="handleReset(scope.row)">{{ t('apiKeys.actions.reset') }}</el-button>
              <el-tooltip :content="t('apiKeys.actions.quotaResetTooltip')" placement="top">
                <el-button icon="Timer" size="small" type="success" @click="handleResetQuota(scope.row)">{{ t('apiKeys.actions.quotaReset') }}</el-button>
              </el-tooltip>
              <el-button icon="Delete" size="small" type="danger" @click="handleDelete(scope.row)">{{ t('apiKeys.actions.delete') }}</el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>
      <!-- 分页组件 -->
      <div class="pagination-wrapper">
        <el-pagination
          v-model:current-page="pagination.currentPage"
          v-model:page-size="pagination.pageSize"
          :page-sizes="pagination.pageSizes"
          :total="pagination.total"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="handleSizeChange"
          @current-change="handleCurrentChange"
        />
      </div>
      </div>
    </el-card>
  </PageSkeleton>

    <!-- 创建/编辑API密钥对话框 -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" center width="680px">
      <el-form ref="formRef" :model="form" label-width="120px" status-icon>
        <el-form-item :label="t('apiKeys.form.keyId')" prop="keyId" :rules="keyIdRules">
          <el-input v-model="form.keyId" :disabled="isEdit" maxlength="64" :placeholder="t('apiKeys.form.keyIdPlaceholder')"
                    show-word-limit/>
        </el-form-item>
        <el-form-item :label="t('apiKeys.form.description')">
          <el-input v-model="form.description" maxlength="128" :placeholder="t('apiKeys.form.descriptionPlaceholder')" show-word-limit
                    type="textarea"/>
        </el-form-item>
        <el-form-item :label="t('apiKeys.form.expiresAt')">
          <el-date-picker
              v-model="form.expiresAt"
              clearable
              format="YYYY-MM-DD HH:mm:ss"
              :placeholder="t('apiKeys.form.expiresAtPlaceholder')"
              style="width: 100%;"
              type="datetime"
              value-format="YYYY-MM-DD HH:mm:ss"
          />
        </el-form-item>
        <el-form-item :label="t('apiKeys.form.permissions')">
          <el-checkbox-group v-model="form.permissions">
            <el-checkbox label="chat">{{ t('apiKeys.permissions.chat') }}</el-checkbox>
            <el-checkbox label="embedding">{{ t('apiKeys.permissions.embedding') }}</el-checkbox>
            <el-checkbox label="rerank">{{ t('apiKeys.permissions.rerank') }}</el-checkbox>
            <el-checkbox label="tts">{{ t('apiKeys.permissions.tts') }}</el-checkbox>
            <el-checkbox label="stt">{{ t('apiKeys.permissions.stt') }}</el-checkbox>
            <el-checkbox label="imgGen">{{ t('apiKeys.permissions.imgGen') }}</el-checkbox>
            <el-checkbox label="imgEdit">{{ t('apiKeys.permissions.imgEdit') }}</el-checkbox>
          </el-checkbox-group>
        </el-form-item>
        <el-form-item :label="t('apiKeys.form.ipWhitelist')">
          <el-select
              v-model="form.allowedIpAddresses"
              allow-create
              clearable
              filterable
              multiple
              :placeholder="t('apiKeys.form.ipWhitelistPlaceholder')"
              style="width: 100%;"
          >
          </el-select>
          <div class="form-hint">{{ t('apiKeys.form.ipWhitelistHint') }}</div>
        </el-form-item>
        <el-form-item :label="t('apiKeys.form.dailyRequestLimit')">
          <el-input-number v-model="form.dailyRequestLimit" :min="0" :step="100" :placeholder="t('apiKeys.form.zeroMeansUnlimitedPlaceholder')"/>
          <div class="form-hint">{{ t('apiKeys.form.zeroMeansUnlimited') }}</div>
        </el-form-item>
        <el-form-item :label="t('apiKeys.form.dailyTokenLimit')">
          <el-input-number v-model="form.dailyTokenLimit" :min="0" :step="1000" :placeholder="t('apiKeys.form.zeroMeansUnlimitedPlaceholder')"/>
          <div class="form-hint">{{ t('apiKeys.form.tokenLimitHint') }}</div>
        </el-form-item>
        <el-form-item :label="t('apiKeys.form.rateLimitPerMinute')">
          <el-input-number v-model="form.rateLimitPerMinute" :min="0" :step="10" :placeholder="t('apiKeys.form.zeroMeansUnlimitedPlaceholder')"/>
          <div class="form-hint">{{ t('apiKeys.form.rateLimitHint') }}</div>
        </el-form-item>
        <el-form-item :label="t('apiKeys.form.quotaAlertThreshold')">
          <el-slider v-model="form.quotaAlertThreshold" :min="0" :max="1" :step="0.05" show-input :format-tooltip="(val: number) => `${Math.round(val * 100)}%`"/>
          <div class="form-hint">{{ t('apiKeys.form.thresholdHint') }}</div>
        </el-form-item>
        <el-form-item :label="t('apiKeys.form.rotationPeriod')">
          <el-input-number v-model="form.rotationPeriodDays" :min="0" :step="30" :placeholder="t('apiKeys.form.rotationPeriodPlaceholder')"/>
          <div class="form-hint">{{ t('apiKeys.form.rotationPeriodHint') }}</div>
        </el-form-item>
      </el-form>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="dialogVisible = false">{{ t('apiKeys.actions.cancel') }}</el-button>
          <el-button type="primary" @click="handleSave" :loading="saveLoading">{{ t('apiKeys.actions.save') }}</el-button>
        </span>
      </template>
    </el-dialog>

    <!-- 创建成功后弹窗，展示密钥值 -->
    <el-dialog v-model="showKeyValueDialog" :close-on-click-modal="false" center :title="t('apiKeys.apiKeyCreatedTitle')" width="450px">
      <div class="key-value-dialog-content">
        <el-icon style="font-size: 48px; color: var(--ja-primary); margin-bottom: 16px;">
          <Key />
        </el-icon>
        <p class="key-value-tip">{{ t('apiKeys.keyValueDialog.tip') }}</p>
        <el-input v-model="createdKeyValue" readonly size="large">
          <template #append>
            <el-button icon="CopyDocument" type="primary" @click="copyKeyValue">{{ t('apiKeys.actions.copy') }}</el-button>
          </template>
        </el-input>
        <el-alert :closable="false" show-icon style="margin-top: 16px;" type="warning">
          <template #title>
            <strong>{{ t('apiKeys.keyValueDialog.important') }}</strong>
          </template>
          {{ t('apiKeys.keyValueDialog.warning') }}
        </el-alert>
      </div>
      <template #footer>
        <span class="dialog-footer">
          <el-button type="primary" @click="closeKeyValueDialog" size="large">{{ t('apiKeys.actions.savedClose') }}</el-button>
        </span>
      </template>
    </el-dialog>

    <!-- 导入配置对话框 -->
    <el-dialog v-model="showImportDialogVisible" :close-on-click-modal="false" center :title="t('apiKeys.importConfigTitle')" width="600px">
      <div class="import-dialog-content">
        <el-alert :closable="false" show-icon style="margin-bottom: 20px;" type="info">
          <template #title>
            <strong>{{ t('apiKeys.import.guideTitle') }}</strong>
          </template>
          {{ t('apiKeys.import.note1') }}
          <br/>
          {{ t('apiKeys.import.mergeNote') }}
          <br/>
          {{ t('apiKeys.import.replaceNote') }}
        </el-alert>
        <el-form label-width="100px">
          <el-form-item :label="t('apiKeys.import.modeLabel')">
            <el-radio-group v-model="importMode">
              <el-radio label="MERGE">{{ t('apiKeys.import.mergeOption') }}</el-radio>
              <el-radio label="REPLACE">{{ t('apiKeys.import.replaceOption') }}</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item :label="t('apiKeys.import.fileLabel')">
            <el-upload
              ref="uploadRef"
              :auto-upload="false"
              :limit="1"
              accept=".json"
              :on-change="handleImportFileChange"
              drag
            >
              <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
              <div class="el-upload__text">
                {{ t('apiKeys.import.uploadText') }}<em>{{ t('apiKeys.import.uploadClick') }}</em>
              </div>
              <template #tip>
                <div class="el-upload__tip">{{ t('apiKeys.import.jsonTip') }}</div>
              </template>
            </el-upload>
          </el-form-item>
        </el-form>
        <div v-if="importPreviewKeys.length > 0" class="import-preview">
          <h4>{{ t('apiKeys.import.previewTitle', { count: importPreviewKeys.length }) }}</h4>
          <el-table :data="importPreviewKeys" max-height="300" border stripe>
            <el-table-column prop="keyId" :label="t('apiKeys.columns.keyId')" width="150"/>
            <el-table-column prop="description" :label="t('apiKeys.columns.description')" show-overflow-tooltip/>
            <el-table-column prop="permissions" :label="t('apiKeys.columns.permissions')" width="150">
              <template #default="scope">
                <el-tag v-for="p in scope.row.permissions" :key="p" size="small" style="margin-right: 4px;">
                  {{ p }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="enabled" :label="t('apiKeys.columns.enabled')" width="80">
              <template #default="scope">
                <el-tag :type="scope.row.enabled ? 'success' : 'danger'" size="small">
                  {{ scope.row.enabled ? t('apiKeys.yes') : t('apiKeys.no') }}
                </el-tag>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </div>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="showImportDialogVisible = false">{{ t('apiKeys.actions.cancel') }}</el-button>
          <el-button
            type="primary"
            @click="handleImport"
            :loading="importLoading"
            :disabled="importPreviewKeys.length === 0"
          >
            {{ t('apiKeys.actions.confirmImport') }}
          </el-button>
        </span>
      </template>
    </el-dialog>

    <!-- 导入结果对话框 -->
    <el-dialog v-model="showImportResultDialog" :close-on-click-modal="false" center :title="t('apiKeys.importResultTitle')" width="700px">
      <div class="import-result-content">
        <el-descriptions :column="3" border>
          <el-descriptions-item :label="t('apiKeys.result.attempted')">{{ importResult?.totalAttempted }}</el-descriptions-item>
          <el-descriptions-item :label="t('apiKeys.result.success')">{{ importResult?.successCount }}</el-descriptions-item>
          <el-descriptions-item :label="t('apiKeys.result.failure')">{{ importResult?.failureCount }}</el-descriptions-item>
        </el-descriptions>
        <div v-if="importResult?.importedKeys && importResult.importedKeys.length > 0" class="imported-keys-section">
          <el-alert :closable="false" show-icon style="margin-top: 20px; margin-bottom: 16px;" type="warning">
            <template #title>
              <strong>{{ t('apiKeys.import.newKeysTitle') }}</strong>
            </template>
          </el-alert>
          <el-table :data="importResult?.importedKeys" max-height="300" border stripe>
            <el-table-column prop="keyId" :label="t('apiKeys.columns.keyId')" width="150"/>
            <el-table-column prop="keyValue" :label="t('apiKeys.columns.keyValue')" width="300">
              <template #default="scope">
                <el-input v-model="scope.row.keyValue" readonly size="small">
                  <template #append>
                    <el-button icon="CopyDocument" @click="copyImportedKey(scope.row.keyValue)" size="small"/>
                  </template>
                </el-input>
              </template>
            </el-table-column>
            <el-table-column prop="description" :label="t('apiKeys.columns.description')" show-overflow-tooltip/>
          </el-table>
        </div>
        <div v-if="importResult?.errors && importResult.errors.length > 0" class="import-errors-section">
          <h4 style="margin-top: 20px; color: var(--ja-danger);">{{ t('apiKeys.import.failedTitle') }}</h4>
          <el-table :data="importResult?.errors" border stripe>
            <el-table-column prop="keyId" :label="t('apiKeys.columns.keyId')" width="150"/>
            <el-table-column prop="reason" :label="t('apiKeys.columns.failureReason')"/>
          </el-table>
        </div>
      </div>
      <template #footer>
        <span class="dialog-footer">
          <el-button type="primary" @click="closeImportResultDialog" size="large">{{ t('apiKeys.actions.savedClose') }}</el-button>
        </span>
      </template>
    </el-dialog>
</template>

<script setup lang="ts">
import { onMounted, ref, reactive } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { Key, CircleCheck, CircleClose, Warning, RefreshRight, Download, Upload, UploadFilled } from '@element-plus/icons-vue'
import StatCard from '@/components/StatCard.vue'
import PageSkeleton from '@/components/PageSkeleton.vue'
import { formatDateTime } from '@/utils/format'
import {
  createApiKey,
  deleteApiKey,
  disableApiKey,
  enableApiKey,
  getApiKeys,
  updateApiKey,
  resetApiKey,
  rotateApiKey,
  exportApiKeys,
  importApiKeys,
  resetApiKeyQuota,
  getQuotaOverview,
  getQuotaAlerts
} from '@/api/apiKey'
import type {
  ApiKeyVO,
  ApiKeyListVO,
  ApiKeyCreationVO,
  ApiKeyCreateRequest,
  ApiKeyUpdateRequest,
  ApiKeyBatchImportRequest,
  ApiKeyBatchImportResult,
  ApiKeyImportItem,
  QuotaUsageDetail,
  QuotaAlertInfo
} from '@/types'

const { t } = useI18n()

// 列表数据
const listData = reactive<ApiKeyListVO>({
  items: [],
  total: 0,
  enabledCount: 0,
  disabledCount: 0,
  expiredCount: 0,
  summary: {
    todayTotalRequests: 0,
    todaySuccessfulRequests: 0,
    todayFailedRequests: 0
  }
})

const apiKeys = ref<ApiKeyVO[]>([])
const loading = ref(false)
const saveLoading = ref(false)
const dialogVisible = ref(false)
const dialogTitle = ref('')
const isEdit = ref(false)
const formRef = ref()

// 分页状态
const pagination = reactive({
  currentPage: 1,
  pageSize: 20,
  pageSizes: [10, 20, 50, 100],
  total: 0
})

// 表单数据
const form = ref({
  keyId: '',
  description: '',
  expiresAt: '',
  enabled: true,
  permissions: [] as string[],
  allowedIpAddresses: [] as string[],
  dailyRequestLimit: 0,
  dailyTokenLimit: 0,
  rateLimitPerMinute: 0,
  quotaAlertThreshold: 0.8,
  rotationPeriodDays: 0
})

// 密钥ID验证规则（computed：语言切换后校验文案即时更新）
const keyIdRules = computed(() => [
  { max: 64, message: t('apiKeys.messages.keyIdTooLong'), trigger: 'blur' }
])

// 获取权限标签类型
const getPermissionTagType = (permission: string) => {
  switch (permission) {
    case 'chat':
      return 'primary'
    case 'embedding':
      return 'success'
    case 'rerank':
      return 'warning'
    case 'tts':
      return 'info'
    case 'stt':
      return 'info'
    case 'imgGen':
      return 'danger'
    case 'imgEdit':
      return 'danger'
    // 兼容旧权限格式
    case 'image':
      return 'danger'
    case 'audio':
      return 'info'
    case 'admin':
      return 'danger'
    default:
      return 'info'
  }
}

// 格式化权限显示（后端权限值与 i18n key 同名；未知值原样返回）
const formatPermission = (permission: string) => {
  return t(`apiKeys.permissions.${permission}`, permission)
}

// 获取剩余天数文本
const getRemainingDaysText = (row: ApiKeyVO): string => {
  if (row.remainingDays === null || row.remainingDays === undefined) return t('apiKeys.remainingDaysText.permanent')
  if (row.remainingDays < 0) return t('apiKeys.remainingDaysText.expired')
  return t('apiKeys.remainingDaysText.days', { count: row.remainingDays })
}

// 获取剩余天数标签类型
const getRemainingDaysType = (row: ApiKeyVO): 'success' | 'warning' | 'danger' | 'info' => {
  if (row.remainingDays === null || row.remainingDays === undefined) return 'info'
  if (row.remainingDays < 0) return 'danger'
  if (row.remainingDays < 7) return 'danger'
  if (row.remainingDays < 30) return 'warning'
  return 'success'
}

// 格式化 Token 数量
const formatTokenCount = (count: number): string => {
  if (count >= 1000000) {
    return `${(count / 1000000).toFixed(1)  }M`
  }
  if (count >= 1000) {
    return `${(count / 1000).toFixed(1)  }K`
  }
  return count.toString()
}

// 分页数据
import { computed } from 'vue'

const pagedApiKeys = computed(() => {
  const start = (pagination.currentPage - 1) * pagination.pageSize
  const end = start + pagination.pageSize
  return apiKeys.value.slice(start, end)
})

// 分页处理
const handleSizeChange = (val: number) => {
  pagination.pageSize = val
  pagination.currentPage = 1
}

const handleCurrentChange = (val: number) => {
  pagination.currentPage = val
}

// 获取API密钥列表
const fetchApiKeys = async () => {
  loading.value = true
  try {
    const data = await getApiKeys()
    // 列表行保留后端规范日期字符串（编辑回写/日期选择器需要），表格列内再做本地化展示
    const formattedItems = data.items

    apiKeys.value = formattedItems
    listData.items = formattedItems
    listData.total = data.total
    listData.enabledCount = data.enabledCount
    listData.disabledCount = data.disabledCount
    listData.expiredCount = data.expiredCount
    listData.summary = data.summary

    // 更新分页总数
    pagination.total = data.total

    // 获取配额概览数据并合并到列表
    await fetchQuotaOverview()
    // 获取配额告警列表（独立加载，失败不影响主列表）
    await fetchQuotaAlerts()
  } catch (error) {
    ElMessage.error(t('apiKeys.messages.fetchListFailed'))
  } finally {
    loading.value = false
  }
}

// 获取配额概览数据
const fetchQuotaOverview = async () => {
  try {
    const quotaData = await getQuotaOverview()
    // 创建配额数据映射
    const quotaMap = new Map<string, QuotaUsageDetail>()
    quotaData.forEach(quota => quotaMap.set(quota.keyId, quota))

    // 将配额数据合并到列表项
    apiKeys.value = apiKeys.value.map(key => ({
      ...key,
      todayTokenUsage: quotaMap.get(key.keyId)?.todayTokenUsage ?? key.todayTokenUsage,
      todayRequestCount: quotaMap.get(key.keyId)?.todayRequestCount ?? key.todayRequestCount,
      quotaAlertTriggered: quotaMap.get(key.keyId)?.alertTriggered ?? key.quotaAlertTriggered
    }))
    listData.items = apiKeys.value
  } catch (error) {
    // 配额数据获取失败不影响列表显示
    console.error('获取配额概览失败:', error)
  }
}

// ===== 配额告警 =====
const quotaAlerts = ref<QuotaAlertInfo[]>([])
const quotaAlertsLoading = ref(false)

/**
 * 获取配额告警列表
 * 数据获取失败时优雅降级：静默记录错误，不影响页面其它功能
 */
const fetchQuotaAlerts = async () => {
  quotaAlertsLoading.value = true
  try {
    const alerts = await getQuotaAlerts()
    quotaAlerts.value = Array.isArray(alerts) ? alerts : []
  } catch (error) {
    // 告警数据获取失败不影响列表显示
    console.error('获取配额告警失败:', error)
    quotaAlerts.value = []
  } finally {
    quotaAlertsLoading.value = false
  }
}

/**
 * 格式化告警类型显示
 */
const formatAlertType = (alertType: string): string => {
  switch (alertType) {
    case 'REQUEST_QUOTA': return t('apiKeys.alertTypes.request')
    case 'TOKEN_QUOTA': return t('apiKeys.alertTypes.token')
    case 'GENERAL': return t('apiKeys.alertTypes.general')
    default: return alertType || t('apiKeys.alertTypes.unknown')
  }
}

/**
 * 获取告警类型标签类型
 */
const getAlertTypeTag = (alertType: string): 'danger' | 'warning' | 'info' => {
  switch (alertType) {
    case 'REQUEST_QUOTA': return 'danger'
    case 'TOKEN_QUOTA': return 'warning'
    default: return 'info'
  }
}

/**
 * 格式化百分比
 */
const formatPercent = (val: number): string => {
  if (val < 0) return '-'
  return `${val.toFixed(1)}%`
}

// 创建API密钥弹窗
const handleCreateApiKey = () => {
  dialogTitle.value = t('apiKeys.createApiKey')
  isEdit.value = false
  form.value = {
    keyId: '',
    description: '',
    expiresAt: '',
    enabled: true,
    permissions: [],
    allowedIpAddresses: [],
    dailyRequestLimit: 0,
    dailyTokenLimit: 0,
    rateLimitPerMinute: 0,
    quotaAlertThreshold: 0.8,
    rotationPeriodDays: 0
  }
  dialogVisible.value = true
}

// 编辑API密钥弹窗
const handleEdit = (row: ApiKeyVO) => {
  dialogTitle.value = t('apiKeys.editApiKey')
  isEdit.value = true
  form.value = {
    keyId: row.keyId,
    description: row.description || '',
    expiresAt: row.expiresAt || '',
    enabled: row.enabled,
    permissions: row.permissions || [],
    allowedIpAddresses: [],
    dailyRequestLimit: row.dailyRequestLimit || 0,
    dailyTokenLimit: row.dailyTokenLimit || 0,
    rateLimitPerMinute: row.rateLimitPerMinute || 0,
    quotaAlertThreshold: row.quotaAlertThreshold ?? 0.8,
    rotationPeriodDays: row.rotationPeriodDays || 0
  }
  dialogVisible.value = true
}

// 删除API密钥
const handleDelete = (row: ApiKeyVO) => {
  ElMessageBox.confirm(t('apiKeys.confirmations.deleteMessage', { keyId: row.keyId }), t('apiKeys.confirmations.deleteTitle'), {
    confirmButtonText: t('apiKeys.confirmations.confirmDelete'),
    cancelButtonText: t('apiKeys.actions.cancel'),
    type: 'warning'
  }).then(async () => {
    try {
      await deleteApiKey(row.keyId)
      await fetchApiKeys()
      ElMessage.success(t('apiKeys.messages.deleteSuccess'))
    } catch (error) {
      ElMessage.error(t('apiKeys.messages.deleteFailed'))
    }
  })
}

// 重置API密钥
const handleReset = (row: ApiKeyVO) => {
  ElMessageBox.confirm(
    t('apiKeys.confirmations.resetMessage', { keyId: row.keyId }),
    t('apiKeys.confirmations.resetTitle'),
    {
      confirmButtonText: t('apiKeys.confirmations.confirmReset'),
      cancelButtonText: t('apiKeys.actions.cancel'),
      type: 'warning'
    }
  ).then(async () => {
    try {
      const response: ApiKeyCreationVO = await resetApiKey(row.keyId)
      createdKeyValue.value = response.keyValue
      showKeyValueDialog.value = true
      await fetchApiKeys()
      ElMessage.success(t('apiKeys.messages.resetSuccess'))
    } catch (error: any) {
      ElMessage.error(t('apiKeys.messages.resetFailedDetail', { message: error.message || '' }))
    }
  })
}

// 强制轮换API密钥
const handleRotate = (row: ApiKeyVO) => {
  ElMessageBox.confirm(
    t('apiKeys.confirmations.rotateMessage', { keyId: row.keyId }),
    t('apiKeys.confirmations.rotateTitle'),
    {
      confirmButtonText: t('apiKeys.confirmations.confirmRotate'),
      cancelButtonText: t('apiKeys.actions.cancel'),
      type: 'warning'
    }
  ).then(async () => {
    try {
      const response: ApiKeyCreationVO = await rotateApiKey(row.keyId)
      createdKeyValue.value = response.keyValue
      showKeyValueDialog.value = true
      await fetchApiKeys()
      ElMessage.success(t('apiKeys.messages.rotateSuccess'))
    } catch (error: any) {
      ElMessage.error(t('apiKeys.messages.rotateFailedDetail', { message: error.message || '' }))
    }
  })
}

// 重置API密钥每日配额
const handleResetQuota = (row: ApiKeyVO) => {
  ElMessageBox.confirm(
    t('apiKeys.confirmations.quotaResetMessage', { keyId: row.keyId }),
    t('apiKeys.confirmations.quotaResetTitle'),
    {
      confirmButtonText: t('apiKeys.confirmations.confirmReset'),
      cancelButtonText: t('apiKeys.actions.cancel'),
      type: 'warning'
    }
  ).then(async () => {
    try {
      await resetApiKeyQuota(row.keyId)
      await fetchQuotaOverview()
      ElMessage.success(t('apiKeys.messages.quotaResetSuccess'))
    } catch (error: any) {
      ElMessage.error(t('apiKeys.messages.quotaResetFailedDetail', { message: error.message || '' }))
    }
  })
}

// 创建成功后弹窗及密钥值处理
const showKeyValueDialog = ref(false)
const createdKeyValue = ref('')

// 保存API密钥
const handleSave = async () => {
  saveLoading.value = true
  try {
    if (formRef.value) {
      await formRef.value.validate()
    }
    
    if (isEdit.value) {
      // 编辑
      const updateData: ApiKeyUpdateRequest = {
        description: form.value.description,
        expiresAt: form.value.expiresAt || undefined,
        enabled: form.value.enabled,
        permissions: form.value.permissions,
        allowedIpAddresses: form.value.allowedIpAddresses,
        dailyRequestLimit: form.value.dailyRequestLimit,
        rotationPeriodDays: form.value.rotationPeriodDays
      }
      await updateApiKey(form.value.keyId, updateData)
      ElMessage.success(t('apiKeys.messages.editSuccess'))
    } else {
      // 新增
      const createData: ApiKeyCreateRequest = {
        keyId: form.value.keyId || undefined,
        description: form.value.description,
        expiresAt: form.value.expiresAt || undefined,
        enabled: form.value.enabled,
        permissions: form.value.permissions,
        allowedIpAddresses: form.value.allowedIpAddresses,
        dailyRequestLimit: form.value.dailyRequestLimit,
        rotationPeriodDays: form.value.rotationPeriodDays
      }
      const response: ApiKeyCreationVO = await createApiKey(createData)
      createdKeyValue.value = response.keyValue
      showKeyValueDialog.value = true
      ElMessage.success(t('apiKeys.messages.createSuccess'))
    }
    dialogVisible.value = false
    await fetchApiKeys()
  } catch (error: any) {
    ElMessage.error(isEdit.value
      ? t('apiKeys.messages.editFailedDetail', { message: error.message || '' })
      : t('apiKeys.messages.createFailedDetail', { message: error.message || '' }))
  } finally {
    saveLoading.value = false
  }
}

// 复制密钥值
const copyKeyValue = () => {
  navigator.clipboard.writeText(createdKeyValue.value)
      .then(() => ElMessage.success(t('apiKeys.messages.keyCopied')))
      .catch(() => ElMessage.error(t('apiKeys.messages.copyFailed')))
}

const closeKeyValueDialog = () => {
  showKeyValueDialog.value = false
  createdKeyValue.value = ''
}

// ============ 批量导入/导出功能 ============

const showImportDialogVisible = ref(false)
const showImportResultDialog = ref(false)
const importMode = ref<'MERGE' | 'REPLACE'>('MERGE')
const importPreviewKeys = ref<ApiKeyImportItem[]>([])
const importLoading = ref(false)
const importResult = ref<ApiKeyBatchImportResult | null>(null)
const uploadRef = ref()

// 显示导入对话框
const showImportDialog = () => {
  importMode.value = 'MERGE'
  importPreviewKeys.value = []
  showImportDialogVisible.value = true
}

// 处理导入文件选择
const handleImportFileChange = (file: any) => {
  const reader = new FileReader()
  reader.onload = (e) => {
    try {
      const content = JSON.parse(e.target?.result as string)
      // 支持两种格式：直接数组或包含 keys 字段的导出格式
      if (Array.isArray(content)) {
        importPreviewKeys.value = content
      } else if (content.keys && Array.isArray(content.keys)) {
        importPreviewKeys.value = content.keys
      } else {
        ElMessage.error(t('apiKeys.messages.importBadFormat'))
        importPreviewKeys.value = []
      }
    } catch (error) {
      ElMessage.error(t('apiKeys.messages.importParseFailed'))
      importPreviewKeys.value = []
    }
  }
  reader.readAsText(file.raw)
}

// 执行导入
const handleImport = async () => {
  if (importPreviewKeys.value.length === 0) {
    ElMessage.warning(t('apiKeys.messages.importSelectFile'))
    return
  }

  importLoading.value = true
  try {
    const request: ApiKeyBatchImportRequest = {
      keys: importPreviewKeys.value,
      mode: importMode.value
    }
    const result = await importApiKeys(request)
    importResult.value = result
    showImportDialogVisible.value = false
    showImportResultDialog.value = true
    await fetchApiKeys()
    ElMessage.success(t('apiKeys.messages.importCompleted', { success: result.successCount, failure: result.failureCount }))
  } catch (error: any) {
    ElMessage.error(t('apiKeys.messages.importFailedDetail', { message: error.message || '' }))
  } finally {
    importLoading.value = false
  }
}

// 复制导入的密钥值
const copyImportedKey = (keyValue: string) => {
  navigator.clipboard.writeText(keyValue)
    .then(() => ElMessage.success(t('apiKeys.messages.keyCopied')))
    .catch(() => ElMessage.error(t('apiKeys.messages.copyFailed')))
}

// 关闭导入结果对话框
const closeImportResultDialog = () => {
  showImportResultDialog.value = false
  importResult.value = null
}

// 执行导出
const handleExport = async () => {
  try {
    const exportData = await exportApiKeys()
    // 创建 JSON 文件并下载
    const blob = new Blob([JSON.stringify(exportData, null, 2)], { type: 'application/json' })
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = `api-keys-export-${new Date().toISOString().slice(0, 10)}.json`
    link.click()
    URL.revokeObjectURL(url)
    ElMessage.success(t('apiKeys.messages.exportCompleted', { total: exportData.total }))
  } catch (error: any) {
    ElMessage.error(t('apiKeys.messages.exportFailedDetail', { message: error.message || '' }))
  }
}

// 状态切换
const handleStatusChange = async (row: ApiKeyVO) => {
  try {
    if (row.enabled) {
      await enableApiKey(row.keyId)
      ElMessage.success(t('apiKeys.messages.enabledDetail', { keyId: row.keyId }))
    } else {
      await disableApiKey(row.keyId)
      ElMessage.success(t('apiKeys.messages.disabledDetail', { keyId: row.keyId }))
    }
    await fetchApiKeys()
  } catch (error) {
    row.enabled = !row.enabled
    ElMessage.error(t('apiKeys.messages.statusChangeFailed'))
  }
}

// 挂载时加载
onMounted(() => {
  fetchApiKeys()
})
</script>

<style scoped>
.api-key-management {
  padding: 0;
  height: 100%;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.stats-row {
  flex-shrink: 0;
}

.quota-alerts-card {
  flex-shrink: 0;
}

.no-alerts {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 24px 0;
}

.no-alerts-text {
  font-size: 14px;
  color: var(--ja-success, #67c23a);
  font-weight: 500;
}

.alert-high {
  color: var(--ja-danger, #f56c6c);
  font-weight: 600;
}

.main-card {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  margin: 0;
}

.main-card :deep(.el-card__body) {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  padding: 0;
}

.card-header {
  flex-shrink: 0;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.table-wrapper {
  flex: 1;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.table-wrapper .el-table {
  flex: 1;
  overflow: auto;
}

.main-title {
  font-size: 18px;
  font-weight: 600;
  color: var(--ja-text-primary);
  display: flex;
  align-items: center;
  gap: 8px;
}

.desc-text {
  color: var(--ja-text-regular);
  font-size: 14px;
}

.pagination-wrapper {
  display: flex;
  justify-content: flex-end;
  padding: 16px;
  border-top: 1px solid var(--ja-border-light);
  flex-shrink: 0;
}

.permission-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  max-width: 140px;
  line-height: 1.8;
}

.permission-tags .el-tag {
  margin: 0;
}

.expired-text {
  color: var(--ja-danger);
}

.usage-stat {
  font-weight: 500;
  color: var(--ja-primary);
}

.usage-detail {
  font-size: 12px;
  color: var(--ja-text-secondary);
  margin-left: 4px;
}

.form-hint {
  font-size: 12px;
  color: var(--ja-text-secondary);
  margin-top: 4px;
}

.action-buttons {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  width: 140px;
}

.action-buttons .el-button {
  width: calc(50% - 2px);
  margin: 0;
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}

.key-value-dialog-content {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 20px;
  text-align: center;
}

.key-value-tip {
  font-size: 16px;
  color: var(--ja-text-primary);
  margin-bottom: 16px;
  font-weight: 500;
}

:deep(.el-table) {
  border-radius: 8px;
}

:deep(.el-dialog__header) {
  border-bottom: 1px solid var(--ja-border-light);
  padding-bottom: 16px;
}

:deep(.el-dialog__footer) {
  border-top: 1px solid var(--ja-border-light);
  padding-top: 16px;
}

.import-dialog-content,
.import-result-content {
  padding: 10px 0;
}

.import-preview {
  margin-top: 20px;
  padding-top: 10px;
}

.import-preview h4 {
  margin-bottom: 10px;
  color: var(--ja-text-primary);
}

.imported-keys-section,
.import-errors-section {
  margin-top: 20px;
}
</style>