<template>
  <div class="api-key-management">
    <!-- 统计卡片 -->
    <el-row :gutter="20" class="stats-row">
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-content">
            <div class="stat-icon" style="background: #409EFF;">
              <el-icon><Key /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-value">{{ listData.total }}</div>
              <div class="stat-label">总密钥数</div>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-content">
            <div class="stat-icon" style="background: #67C23A;">
              <el-icon><CircleCheck /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-value">{{ listData.enabledCount }}</div>
              <div class="stat-label">已启用</div>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-content">
            <div class="stat-icon" style="background: #F56C6C;">
              <el-icon><CircleClose /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-value">{{ listData.disabledCount }}</div>
              <div class="stat-label">已禁用</div>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-content">
            <div class="stat-icon" style="background: #E6A23C;">
              <el-icon><Warning /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-value">{{ listData.expiredCount }}</div>
              <div class="stat-label">已过期</div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 主卡片 -->
    <el-card class="main-card" shadow="hover">
      <template #header>
        <div class="card-header">
          <span class="main-title">
            <el-icon><Key /></el-icon>
            API密钥管理
          </span>
          <div class="header-buttons">
            <el-button icon="Download" type="success" @click="handleExport">导出配置</el-button>
            <el-button icon="Upload" type="warning" @click="showImportDialog">导入配置</el-button>
            <el-button icon="Plus" type="primary" @click="handleCreateApiKey">创建API密钥</el-button>
          </div>
        </div>
      </template>

      <div class="table-wrapper">
        <el-table v-loading="loading" :data="pagedApiKeys" border stripe style="width: 100%">
        <el-table-column label="密钥ID" prop="keyId" width="180">
          <template #default="scope">
            <el-tag effect="plain" type="info">{{ scope.row.keyId }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="描述" min-width="150" show-overflow-tooltip>
          <template #default="scope">
            <span>{{ scope.row.description || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="权限" prop="permissions" width="160">
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
        <el-table-column label="创建时间" prop="createdAt" width="160"/>
        <el-table-column label="创建者" width="150" show-overflow-tooltip>
          <template #default="scope">
            <span v-if="scope.row.createdBy">{{ scope.row.createdBy }}</span>
            <span v-else class="text-muted">-</span>
          </template>
        </el-table-column>
        <el-table-column label="过期时间" prop="expiresAt" width="160">
          <template #default="scope">
            <span :class="{ 'expired-text': scope.row.expired }">
              {{ scope.row.expiresAt || '永不过期' }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="剩余天数" width="100" align="center">
          <template #default="scope">
            <el-tag :type="getRemainingDaysType(scope.row)" size="small">
              {{ getRemainingDaysText(scope.row) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="使用统计" width="140" align="center">
          <template #default="scope">
            <el-tooltip :content="`成功: ${scope.row.successfulRequests}, 失败: ${scope.row.failedRequests}`">
              <span class="usage-stat">
                {{ scope.row.totalRequests }}
                <span class="usage-detail">({{ scope.row.successfulRequests }}/{{ scope.row.failedRequests }})</span>
              </span>
            </el-tooltip>
          </template>
        </el-table-column>
        <el-table-column label="今日Token" width="120" align="center">
          <template #default="scope">
            <span v-if="scope.row.todayTokenUsage !== undefined && scope.row.todayTokenUsage !== null">
              {{ formatTokenCount(scope.row.todayTokenUsage) }}
            </span>
            <span v-else class="text-muted">-</span>
          </template>
        </el-table-column>
        <el-table-column label="告警状态" width="100" align="center">
          <template #default="scope">
            <el-tag v-if="scope.row.quotaAlertTriggered" type="danger" size="small">
              <el-icon><Warning /></el-icon> 告警
            </el-tag>
            <el-tag v-else type="success" size="small">正常</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" prop="enabled" width="80" align="center">
          <template #default="scope">
            <el-switch
                v-model="scope.row.enabled"
                :disabled="scope.row.expired"
                active-color="#13ce66"
                inactive-color="#ff4949"
                @change="handleStatusChange(scope.row)"
            />
          </template>
        </el-table-column>
        <el-table-column fixed="right" label="操作" width="200">
          <template #default="scope">
            <div class="action-buttons">
              <el-button icon="Edit" size="small" @click="handleEdit(scope.row)">编辑</el-button>
              <el-button icon="RefreshRight" size="small" type="warning" @click="handleRotate(scope.row)"
                         :disabled="scope.row.expired">轮换</el-button>
              <el-button icon="Refresh" size="small" type="info" @click="handleReset(scope.row)">重置</el-button>
              <el-tooltip content="重置每日配额计数器" placement="top">
                <el-button icon="Timer" size="small" type="success" @click="handleResetQuota(scope.row)">配额重置</el-button>
              </el-tooltip>
              <el-button icon="Delete" size="small" type="danger" @click="handleDelete(scope.row)">删除</el-button>
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

    <!-- 创建/编辑API密钥对话框 -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" center width="680px">
      <el-form ref="formRef" :model="form" label-width="120px" status-icon>
        <el-form-item label="密钥ID" prop="keyId" :rules="keyIdRules">
          <el-input v-model="form.keyId" :disabled="isEdit" maxlength="64" placeholder="留空则自动生成"
                    show-word-limit/>
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" maxlength="128" placeholder="密钥用途描述" show-word-limit
                    type="textarea"/>
        </el-form-item>
        <el-form-item label="过期时间">
          <el-date-picker
              v-model="form.expiresAt"
              clearable
              format="YYYY-MM-DD HH:mm:ss"
              placeholder="留空则永不过期"
              style="width: 100%;"
              type="datetime"
              value-format="YYYY-MM-DD HH:mm:ss"
          />
        </el-form-item>
        <el-form-item label="权限">
          <el-checkbox-group v-model="form.permissions">
            <el-checkbox label="chat">聊天模型</el-checkbox>
            <el-checkbox label="embedding">嵌入模型</el-checkbox>
            <el-checkbox label="rerank">重排序模型</el-checkbox>
            <el-checkbox label="tts">语音合成</el-checkbox>
            <el-checkbox label="stt">语音识别</el-checkbox>
            <el-checkbox label="imgGen">图像生成</el-checkbox>
            <el-checkbox label="imgEdit">图像编辑</el-checkbox>
          </el-checkbox-group>
        </el-form-item>
        <el-form-item label="IP白名单">
          <el-select
              v-model="form.allowedIpAddresses"
              allow-create
              clearable
              filterable
              multiple
              placeholder="留空则不限制IP"
              style="width: 100%;"
          >
          </el-select>
          <div class="form-hint">允许使用此密钥的IP地址，留空表示不限制</div>
        </el-form-item>
        <el-form-item label="每日请求上限">
          <el-input-number v-model="form.dailyRequestLimit" :min="0" :step="100" placeholder="0表示不限制"/>
          <div class="form-hint">0 表示不限制</div>
        </el-form-item>
        <el-form-item label="每日Token上限">
          <el-input-number v-model="form.dailyTokenLimit" :min="0" :step="1000" placeholder="0表示不限制"/>
          <div class="form-hint">每日 Token 使用量上限，0 表示不限制</div>
        </el-form-item>
        <el-form-item label="每分钟速率限制">
          <el-input-number v-model="form.rateLimitPerMinute" :min="0" :step="10" placeholder="0表示不限制"/>
          <div class="form-hint">每分钟请求速率上限，0 表示不限制</div>
        </el-form-item>
        <el-form-item label="告警阈值">
          <el-slider v-model="form.quotaAlertThreshold" :min="0" :max="1" :step="0.05" show-input :format-tooltip="(val: number) => `${Math.round(val * 100)}%`"/>
          <div class="form-hint">配额使用达到此比例时触发告警</div>
        </el-form-item>
        <el-form-item label="轮换周期">
          <el-input-number v-model="form.rotationPeriodDays" :min="0" :step="30" placeholder="0表示不自动轮换"/>
          <div class="form-hint">设置密钥自动轮换周期（天数），0 表示不自动轮换</div>
        </el-form-item>
      </el-form>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="dialogVisible = false">取消</el-button>
          <el-button type="primary" @click="handleSave" :loading="saveLoading">保存</el-button>
        </span>
      </template>
    </el-dialog>

    <!-- 创建成功后弹窗，展示密钥值 -->
    <el-dialog v-model="showKeyValueDialog" :close-on-click-modal="false" center title="API密钥已创建" width="450px">
      <div class="key-value-dialog-content">
        <el-icon style="font-size: 48px; color: #409EFF; margin-bottom: 16px;">
          <Key />
        </el-icon>
        <p class="key-value-tip">请妥善保存以下密钥值，密钥值仅此一次显示：</p>
        <el-input v-model="createdKeyValue" readonly size="large">
          <template #append>
            <el-button icon="CopyDocument" type="primary" @click="copyKeyValue">复制</el-button>
          </template>
        </el-input>
        <el-alert :closable="false" show-icon style="margin-top: 16px;" type="warning">
          <template #title>
            <strong>重要提醒</strong>
          </template>
          密钥值只会显示一次，关闭弹窗后无法再次获取！如果丢失，请使用重置功能生成新密钥。
        </el-alert>
      </div>
      <template #footer>
        <span class="dialog-footer">
          <el-button type="primary" @click="closeKeyValueDialog" size="large">我已保存，关闭</el-button>
        </span>
      </template>
    </el-dialog>

    <!-- 导入配置对话框 -->
    <el-dialog v-model="showImportDialogVisible" :close-on-click-modal="false" center title="导入API密钥配置" width="600px">
      <div class="import-dialog-content">
        <el-alert :closable="false" show-icon style="margin-bottom: 20px;" type="info">
          <template #title>
            <strong>导入说明</strong>
          </template>
          导入时会为每个密钥生成新的密钥值，原配置中的密钥值不会被导入。
          <br/>
          MERGE模式：保留现有密钥，仅添加新密钥。
          <br/>
          REPLACE模式：删除所有现有密钥后导入新密钥。
        </el-alert>
        <el-form label-width="100px">
          <el-form-item label="导入模式">
            <el-radio-group v-model="importMode">
              <el-radio label="MERGE">合并（保留现有）</el-radio>
              <el-radio label="REPLACE">替换（删除现有）</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="配置文件">
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
                拖拽JSON文件到此处，或<em>点击选择</em>
              </div>
              <template #tip>
                <div class="el-upload__tip">仅支持 .json 格式的配置文件</div>
              </template>
            </el-upload>
          </el-form-item>
        </el-form>
        <div v-if="importPreviewKeys.length > 0" class="import-preview">
          <h4>预览导入的密钥 ({{ importPreviewKeys.length }} 个)</h4>
          <el-table :data="importPreviewKeys" max-height="300" border stripe>
            <el-table-column prop="keyId" label="密钥ID" width="150"/>
            <el-table-column prop="description" label="描述" show-overflow-tooltip/>
            <el-table-column prop="permissions" label="权限" width="150">
              <template #default="scope">
                <el-tag v-for="p in scope.row.permissions" :key="p" size="small" style="margin-right: 4px;">
                  {{ p }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="enabled" label="启用" width="80">
              <template #default="scope">
                <el-tag :type="scope.row.enabled ? 'success' : 'danger'" size="small">
                  {{ scope.row.enabled ? '是' : '否' }}
                </el-tag>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </div>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="showImportDialogVisible = false">取消</el-button>
          <el-button
            type="primary"
            @click="handleImport"
            :loading="importLoading"
            :disabled="importPreviewKeys.length === 0"
          >
            确认导入
          </el-button>
        </span>
      </template>
    </el-dialog>

    <!-- 导入结果对话框 -->
    <el-dialog v-model="showImportResultDialog" :close-on-click-modal="false" center title="导入结果" width="700px">
      <div class="import-result-content">
        <el-descriptions :column="3" border>
          <el-descriptions-item label="尝试导入">{{ importResult?.totalAttempted }}</el-descriptions-item>
          <el-descriptions-item label="成功">{{ importResult?.successCount }}</el-descriptions-item>
          <el-descriptions-item label="失败">{{ importResult?.failureCount }}</el-descriptions-item>
        </el-descriptions>
        <div v-if="importResult?.importedKeys && importResult.importedKeys.length > 0" class="imported-keys-section">
          <el-alert :closable="false" show-icon style="margin-top: 20px; margin-bottom: 16px;" type="warning">
            <template #title>
              <strong>新密钥值（仅此一次显示，请保存）</strong>
            </template>
          </el-alert>
          <el-table :data="importResult?.importedKeys" max-height="300" border stripe>
            <el-table-column prop="keyId" label="密钥ID" width="150"/>
            <el-table-column prop="keyValue" label="密钥值" width="300">
              <template #default="scope">
                <el-input v-model="scope.row.keyValue" readonly size="small">
                  <template #append>
                    <el-button icon="CopyDocument" @click="copyImportedKey(scope.row.keyValue)" size="small"/>
                  </template>
                </el-input>
              </template>
            </el-table-column>
            <el-table-column prop="description" label="描述" show-overflow-tooltip/>
          </el-table>
        </div>
        <div v-if="importResult?.errors && importResult.errors.length > 0" class="import-errors-section">
          <h4 style="margin-top: 20px; color: #F56C6C;">导入失败</h4>
          <el-table :data="importResult?.errors" border stripe>
            <el-table-column prop="keyId" label="密钥ID" width="150"/>
            <el-table-column prop="reason" label="失败原因"/>
          </el-table>
        </div>
      </div>
      <template #footer>
        <span class="dialog-footer">
          <el-button type="primary" @click="closeImportResultDialog" size="large">我已保存，关闭</el-button>
        </span>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref, reactive } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Key, CircleCheck, CircleClose, Warning, RefreshRight, Download, Upload, UploadFilled } from '@element-plus/icons-vue'
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
  getQuotaOverview
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
  QuotaUsageDetail
} from '@/types'

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

// 密钥ID验证规则
const keyIdRules = [
  { max: 64, message: '密钥ID长度不能超过64字符', trigger: 'blur' }
]

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

// 格式化权限显示
const formatPermission = (permission: string) => {
  switch (permission) {
    case 'chat':
      return '聊天模型'
    case 'embedding':
      return '嵌入模型'
    case 'rerank':
      return '重排序模型'
    case 'tts':
      return '语音合成'
    case 'stt':
      return '语音识别'
    case 'imgGen':
      return '图像生成'
    case 'imgEdit':
      return '图像编辑'
    // 兼容旧权限格式
    case 'image':
      return '图像生成'
    case 'audio':
      return '语音合成'
    case 'admin':
      return '管理员'
    case 'read':
      return '读取'
    case 'write':
      return '写入'
    case 'delete':
      return '删除'
    default:
      return permission
  }
}

// 获取剩余天数文本
const getRemainingDaysText = (row: ApiKeyVO): string => {
  if (row.remainingDays === null || row.remainingDays === undefined) return '永久'
  if (row.remainingDays < 0) return '已过期'
  return `${row.remainingDays}天`
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

// 格式化日期时间
const formatDateTime = (dateString: string): string => {
  if (!dateString) return ''
  const date = new Date(dateString)
  return `${date.getFullYear()  }-${ 
      String(date.getMonth() + 1).padStart(2, '0')  }-${ 
      String(date.getDate()).padStart(2, '0')  } ${ 
      String(date.getHours()).padStart(2, '0')  }:${ 
      String(date.getMinutes()).padStart(2, '0')  }:${ 
      String(date.getSeconds()).padStart(2, '0')}`
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
    // 格式化日期
    const formattedItems = data.items.map(key => ({
      ...key,
      createdAt: formatDateTime(key.createdAt),
      expiresAt: formatDateTime(key.expiresAt),
      lastUsedAt: formatDateTime(key.lastUsedAt || '')
    }))

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
  } catch (error) {
    ElMessage.error('获取API密钥列表失败')
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
    console.warn('获取配额概览失败:', error)
  }
}

// 创建API密钥弹窗
const handleCreateApiKey = () => {
  dialogTitle.value = '创建API密钥'
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
  dialogTitle.value = '编辑API密钥'
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
  ElMessageBox.confirm(`确定要删除API密钥 ${row.keyId} 吗？此操作不可恢复。`, '删除确认', {
    confirmButtonText: '确定删除',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(async () => {
    try {
      await deleteApiKey(row.keyId)
      await fetchApiKeys()
      ElMessage.success('删除成功')
    } catch (error) {
      ElMessage.error('删除失败')
    }
  })
}

// 重置API密钥
const handleReset = (row: ApiKeyVO) => {
  ElMessageBox.confirm(
    `确定要重置API密钥 ${row.keyId} 吗？旧的密钥值将立即失效，新的密钥值仅显示一次。`,
    '重置确认',
    {
      confirmButtonText: '确定重置',
      cancelButtonText: '取消',
      type: 'warning'
    }
  ).then(async () => {
    try {
      const response: ApiKeyCreationVO = await resetApiKey(row.keyId)
      createdKeyValue.value = response.keyValue
      showKeyValueDialog.value = true
      await fetchApiKeys()
      ElMessage.success('密钥重置成功，请保存新的密钥值！')
    } catch (error: any) {
      ElMessage.error(`重置失败: ${  error.message || ''}`)
    }
  })
}

// 强制轮换API密钥
const handleRotate = (row: ApiKeyVO) => {
  ElMessageBox.confirm(
    `确定要轮换API密钥 ${row.keyId} 吗？旧的密钥值将立即失效，新的密钥值仅显示一次。轮换后会更新 lastRotatedAt 时间戳。`,
    '轮换确认',
    {
      confirmButtonText: '确定轮换',
      cancelButtonText: '取消',
      type: 'warning'
    }
  ).then(async () => {
    try {
      const response: ApiKeyCreationVO = await rotateApiKey(row.keyId)
      createdKeyValue.value = response.keyValue
      showKeyValueDialog.value = true
      await fetchApiKeys()
      ElMessage.success('密钥轮换成功，请保存新的密钥值！')
    } catch (error: any) {
      ElMessage.error(`轮换失败: ${  error.message || ''}`)
    }
  })
}

// 重置API密钥每日配额
const handleResetQuota = (row: ApiKeyVO) => {
  ElMessageBox.confirm(
    `确定要重置API密钥 ${row.keyId} 的每日配额计数器吗？这将清零今日的请求和Token使用量。`,
    '配额重置确认',
    {
      confirmButtonText: '确定重置',
      cancelButtonText: '取消',
      type: 'warning'
    }
  ).then(async () => {
    try {
      await resetApiKeyQuota(row.keyId)
      await fetchQuotaOverview()
      ElMessage.success('配额重置成功')
    } catch (error: any) {
      ElMessage.error(`配额重置失败: ${  error.message || ''}`)
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
      ElMessage.success('编辑成功')
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
      ElMessage.success('创建成功，请妥善保存密钥值！')
    }
    dialogVisible.value = false
    await fetchApiKeys()
  } catch (error: any) {
    ElMessage.error(isEdit.value ? `编辑失败: ${  error.message || ''}` : `创建失败: ${  error.message || ''}`)
  } finally {
    saveLoading.value = false
  }
}

// 复制密钥值
const copyKeyValue = () => {
  navigator.clipboard.writeText(createdKeyValue.value)
      .then(() => ElMessage.success('密钥值已复制到剪贴板'))
      .catch(() => ElMessage.error('复制失败，请手动复制'))
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
        ElMessage.error('文件格式不正确')
        importPreviewKeys.value = []
      }
    } catch (error) {
      ElMessage.error('解析JSON文件失败')
      importPreviewKeys.value = []
    }
  }
  reader.readAsText(file.raw)
}

// 执行导入
const handleImport = async () => {
  if (importPreviewKeys.value.length === 0) {
    ElMessage.warning('请先选择要导入的配置文件')
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
    ElMessage.success(`导入完成：成功 ${result.successCount}，失败 ${result.failureCount}`)
  } catch (error: any) {
    ElMessage.error(`导入失败: ${  error.message || ''}`)
  } finally {
    importLoading.value = false
  }
}

// 复制导入的密钥值
const copyImportedKey = (keyValue: string) => {
  navigator.clipboard.writeText(keyValue)
    .then(() => ElMessage.success('密钥值已复制到剪贴板'))
    .catch(() => ElMessage.error('复制失败，请手动复制'))
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
    ElMessage.success(`已导出 ${exportData.total} 个密钥配置`)
  } catch (error: any) {
    ElMessage.error(`导出失败: ${  error.message || ''}`)
  }
}

// 状态切换
const handleStatusChange = async (row: ApiKeyVO) => {
  try {
    if (row.enabled) {
      await enableApiKey(row.keyId)
      ElMessage.success(`API密钥 ${row.keyId} 已启用`)
    } else {
      await disableApiKey(row.keyId)
      ElMessage.success(`API密钥 ${row.keyId} 已禁用`)
    }
    await fetchApiKeys()
  } catch (error) {
    row.enabled = !row.enabled
    ElMessage.error('状态变更失败')
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
}

.stat-card {
  border-radius: 8px;
}

.stat-content {
  display: flex;
  align-items: center;
  padding: 10px;
}

.stat-icon {
  width: 56px;
  height: 56px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  font-size: 24px;
  margin-right: 16px;
}

.stat-info {
  flex: 1;
}

.stat-value {
  font-size: 28px;
  font-weight: 600;
  color: #303133;
}

.stat-label {
  font-size: 14px;
  color: #909399;
  margin-top: 4px;
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

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.header-buttons {
  display: flex;
  gap: 8px;
}

.main-title {
  font-size: 18px;
  font-weight: 600;
  color: #303133;
  display: flex;
  align-items: center;
  gap: 8px;
}

.desc-text {
  color: #606266;
  font-size: 14px;
}

.pagination-wrapper {
  display: flex;
  justify-content: flex-end;
  padding: 16px;
  border-top: 1px solid #EBEEF5;
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
  color: #F56C6C;
}

.usage-stat {
  font-weight: 500;
  color: #409EFF;
}

.usage-detail {
  font-size: 12px;
  color: #909399;
  margin-left: 4px;
}

.form-hint {
  font-size: 12px;
  color: #909399;
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
  color: #303133;
  margin-bottom: 16px;
  font-weight: 500;
}

:deep(.el-table) {
  border-radius: 8px;
}

:deep(.el-dialog__header) {
  border-bottom: 1px solid #EBEEF5;
  padding-bottom: 16px;
}

:deep(.el-dialog__footer) {
  border-top: 1px solid #EBEEF5;
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
  color: #303133;
}

.imported-keys-section,
.import-errors-section {
  margin-top: 20px;
}
</style>