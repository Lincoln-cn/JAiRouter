<template>
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
/**
 * 批量导入：配置弹窗（含 MERGE/REPLACE 与预览）+ 导入结果弹窗。
 *
 * 拆分前是 `ApiKeyManagement.vue` 里两个内联 el-dialog 加
 * `showImportDialog` / `handleImportFileChange` / `handleImport` /
 * `copyImportedKey` / `closeImportResultDialog` 五个函数（连同它们的状态）。
 * 这些状态此前只在本页面的导入流程里使用，搬进组件后对外只剩两个入口：
 * `open()`（父组件「导入配置」按钮调用）与 `refresh`（成功后刷新列表，
 * 仍被 await，提示出现的时机与原实现一致）。
 *
 * 解析规则逐字保留：既接受「顶层数组」，也接受「含 keys 数组的导出格式」。
 */
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import type { UploadFile } from 'element-plus'
import { UploadFilled } from '@element-plus/icons-vue'
import { getErrorMessage } from '@/utils/error'
import { importApiKeys } from '@/api/apiKey'
import type {
  ApiKeyBatchImportRequest,
  ApiKeyBatchImportResult,
  ApiKeyImportItem
} from '@/types'

const props = defineProps<{
  /** 导入成功后刷新父组件列表（原 `await fetchApiKeys()`） */
  refresh: () => Promise<void>
}>()

const { t } = useI18n()

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
const handleImportFileChange = (file: UploadFile) => {
  // Element Plus 的 UploadFile.raw 是可选（仅「选择后未上传」时存在）；没有原始文件就没法读内容
  const rawFile = file.raw
  if (!rawFile) {
    ElMessage.warning(t('apiKeys.messages.importSelectFile'))
    return
  }
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
  reader.readAsText(rawFile)
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
    await props.refresh()
    ElMessage.success(t('apiKeys.messages.importCompleted', { success: result.successCount, failure: result.failureCount }))
  } catch (error) {
    ElMessage.error(t('apiKeys.messages.importFailedDetail', { message: getErrorMessage(error) || '' }))
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

defineExpose({ open: showImportDialog })
</script>

<style scoped>
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

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}

:deep(.el-dialog__header) {
  border-bottom: 1px solid var(--ja-border-light);
  padding-bottom: 16px;
}

:deep(.el-dialog__footer) {
  border-top: 1px solid var(--ja-border-light);
  padding-top: 16px;
}
</style>
