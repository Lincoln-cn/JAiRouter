<template>
  <div class="config-manager">
    <!-- 配置管理工具栏 -->
    <div class="config-toolbar">
      <div class="config-actions">
        <el-button 
          type="primary" 
          size="small" 
          @click="showSaveDialog = true"
          :disabled="!hasCurrentConfig"
        >
          <el-icon><DocumentAdd /></el-icon>
          保存配置
        </el-button>
        
        <el-select
          v-model="selectedConfigId"
          placeholder="选择配置"
          size="small"
          style="width: 200px"
          @change="loadConfiguration"
          clearable
        >
          <el-option
            v-for="config in savedConfigs"
            :key="config.id"
            :label="config.name"
            :value="config.id"
          >
            <div class="config-option">
              <span class="config-name">{{ config.name }}</span>
              <span class="config-service">{{ getServiceTypeLabel(config.serviceType) }}</span>
            </div>
          </el-option>
        </el-select>
        
        <el-button 
          size="small" 
          @click="showManageDialog = true"
          :disabled="savedConfigs.length === 0"
        >
          <el-icon><Setting /></el-icon>
          管理配置
        </el-button>
      </div>
    </div>

    <!-- 保存配置对话框 -->
    <el-dialog
      v-model="showSaveDialog"
      title="保存配置"
      width="400px"
      :before-close="handleSaveDialogClose"
    >
      <el-form 
        ref="saveFormRef"
        :model="saveForm"
        :rules="saveFormRules"
        label-width="80px"
      >
        <el-form-item label="配置名称" prop="name">
          <el-input
            v-model="saveForm.name"
            placeholder="请输入配置名称"
            maxlength="50"
            show-word-limit
          />
        </el-form-item>
        
        <el-form-item label="服务类型">
          <el-tag type="info">{{ getServiceTypeLabel(currentServiceType) }}</el-tag>
        </el-form-item>
        
        <el-form-item label="描述" prop="description">
          <el-input
            v-model="saveForm.description"
            type="textarea"
            :rows="3"
            placeholder="可选：添加配置描述"
            maxlength="200"
            show-word-limit
          />
        </el-form-item>
      </el-form>
      
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="handleSaveDialogClose">取消</el-button>
          <el-button type="primary" @click="saveConfiguration">
            保存
          </el-button>
        </span>
      </template>
    </el-dialog>

    <!-- 配置管理对话框 -->
    <el-dialog
      v-model="showManageDialog"
      title="配置管理"
      width="700px"
    >
      <!-- 配置管理工具栏 -->
      <div class="manage-toolbar">
        <div class="toolbar-left">
          <el-checkbox 
            v-model="selectAll" 
            :indeterminate="isIndeterminate"
            @change="handleSelectAll"
          >
            全选
          </el-checkbox>
          <span class="selected-count">
            已选择 {{ selectedConfigs.length }} 个配置
          </span>
        </div>
        <div class="toolbar-right">
          <el-button 
            size="small" 
            :disabled="selectedConfigs.length === 0"
            @click="batchDelete"
          >
            批量删除
          </el-button>
          <el-button 
            size="small" 
            :disabled="selectedConfigs.length === 0"
            @click="batchExport"
          >
            导出选中
          </el-button>
        </div>
      </div>
      
      <div class="config-list">
        <el-table 
          :data="savedConfigs" 
          style="width: 100%"
          empty-text="暂无保存的配置"
          @selection-change="handleSelectionChange"
        >
          <el-table-column type="selection" width="55" />
          
          <el-table-column prop="name" label="配置名称" min-width="120">
            <template #default="{ row }">
              <div class="config-name-cell">
                <span v-if="!row.editing">{{ row.name }}</span>
                <el-input
                  v-else
                  v-model="row.editName"
                  size="small"
                  @blur="confirmRename(row)"
                  @keyup.enter="confirmRename(row)"
                  @keyup.esc="cancelRename(row)"
                />
              </div>
            </template>
          </el-table-column>
          
          <el-table-column prop="serviceType" label="服务类型" width="100">
            <template #default="{ row }">
              <el-tag size="small" type="info">
                {{ getServiceTypeLabel(row.serviceType) }}
              </el-tag>
            </template>
          </el-table-column>
          
          <el-table-column prop="createdAt" label="创建时间" width="150">
            <template #default="{ row }">
              {{ formatDate(row.createdAt) }}
            </template>
          </el-table-column>
          
          <el-table-column label="操作" width="180">
            <template #default="{ row }">
              <el-button-group size="small">
                <el-button 
                  @click="loadConfigurationById(row.id)"
                  :disabled="selectedConfigId === row.id"
                >
                  加载
                </el-button>
                <el-button @click="startRename(row)">
                  重命名
                </el-button>
                <el-button 
                  type="danger" 
                  @click="confirmDelete(row)"
                >
                  删除
                </el-button>
              </el-button-group>
            </template>
          </el-table-column>
        </el-table>
      </div>
      
      <!-- 存储信息 -->
      <div class="storage-info">
        <el-divider />
        <div class="storage-stats">
          <span class="storage-label">存储使用情况:</span>
          <el-progress 
            :percentage="storageInfo.percentage" 
            :color="getStorageColor(storageInfo.percentage)"
            :show-text="false"
            style="width: 200px; margin: 0 10px;"
          />
          <span class="storage-text">
            {{ formatBytes(storageInfo.used) }} / {{ formatBytes(storageInfo.total) }}
          </span>
          <el-button 
            size="small" 
            type="danger" 
            @click="confirmClearAll"
            style="margin-left: 10px;"
          >
            清空所有
          </el-button>
        </div>
      </div>
      
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="exportConfigs">导出全部</el-button>
          <el-button @click="triggerImport">导入配置</el-button>
          <el-button @click="refreshStorageInfo">刷新</el-button>
          <el-button type="primary" @click="showManageDialog = false">
            关闭
          </el-button>
        </span>
      </template>
    </el-dialog>

    <!-- 隐藏的文件输入用于导入 -->
    <input
      ref="fileInputRef"
      type="file"
      accept=".json"
      style="display: none"
      @change="handleImport"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, inject } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { DocumentAdd, Setting } from '@element-plus/icons-vue'
import { configService } from '../services/configService'
import type { 
  SavedConfiguration, 
  ServiceType, 
  GlobalConfig 
} from '../types/playground'

// Props
interface Props {
  currentServiceType: ServiceType
  currentConfig: any
  globalConfig: GlobalConfig
}

const props = defineProps<Props>()

// Emits
interface Emits {
  (e: 'config-loaded', config: SavedConfiguration): void
  (e: 'config-saved', config: SavedConfiguration): void
  (e: 'config-deleted', configId: string): void
}

const emit = defineEmits<Emits>()

// 响应式数据
const savedConfigs = ref<SavedConfiguration[]>([])
const selectedConfigId = ref<string>('')
const showSaveDialog = ref(false)
const showManageDialog = ref(false)

// 批量操作相关
const selectedConfigs = ref<SavedConfiguration[]>([])
const selectAll = ref(false)

// 存储信息
const storageInfo = ref({
  used: 0,
  total: 0,
  percentage: 0
})

// 表单引用
const saveFormRef = ref<FormInstance>()
const fileInputRef = ref<HTMLInputElement>()

// 保存表单数据
const saveForm = reactive({
  name: '',
  description: ''
})

// 表单验证规则
const saveFormRules: FormRules = {
  name: [
    { required: true, message: '请输入配置名称', trigger: 'blur' },
    { min: 1, max: 50, message: '配置名称长度在 1 到 50 个字符', trigger: 'blur' },
    { 
      validator: (rule, value, callback) => {
        const exists = savedConfigs.value.some(config => 
          config.name === value && config.serviceType === props.currentServiceType
        )
        if (exists) {
          callback(new Error('该服务类型下已存在同名配置'))
        } else {
          callback()
        }
      }, 
      trigger: 'blur' 
    }
  ]
}

// 计算属性
const hasCurrentConfig = computed(() => {
  return props.currentConfig && Object.keys(props.currentConfig).length > 0
})

const isIndeterminate = computed(() => {
  const selectedCount = selectedConfigs.value.length
  const totalCount = savedConfigs.value.length
  return selectedCount > 0 && selectedCount < totalCount
})

// 服务类型标签映射
const serviceTypeLabels: Record<ServiceType, string> = {
  chat: '对话',
  embedding: '嵌入',
  rerank: '重排序',
  tts: '语音合成',
  stt: '语音识别',
  imageGenerate: '图像生成',
  imageEdit: '图像编辑'
}

// 方法
const getServiceTypeLabel = (serviceType: ServiceType): string => {
  return serviceTypeLabels[serviceType] || serviceType
}

const formatDate = (dateString: string): string => {
  const date = new Date(dateString)
  return date.toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  })
}

const loadSavedConfigs = (): void => {
  try {
    savedConfigs.value = configService.loadConfigurations()
    refreshStorageInfo()
  } catch (error) {
    console.error('加载配置失败:', error)
    ElMessage.error('加载保存的配置失败')
  }
}

const saveSavedConfigs = (): boolean => {
  try {
    return configService.saveConfigurations(savedConfigs.value)
  } catch (error) {
    console.error('保存配置失败:', error)
    ElMessage.error('保存配置到本地存储失败')
    return false
  }
}

const saveConfiguration = async (): Promise<void> => {
  if (!saveFormRef.value) return
  
  try {
    const valid = await saveFormRef.value.validate()
    if (!valid) return
    
    const newConfig = configService.addConfiguration({
      name: saveForm.name,
      serviceType: props.currentServiceType,
      config: {
        ...props.currentConfig,
        globalConfig: { ...props.globalConfig }
      },
      description: saveForm.description
    })
    
    // 重新加载配置列表
    loadSavedConfigs()
    
    ElMessage.success('配置保存成功')
    emit('config-saved', newConfig)
    handleSaveDialogClose()
    
  } catch (error) {
    console.error('保存配置失败:', error)
    ElMessage.error('保存配置失败')
  }
}

const loadConfiguration = (): void => {
  if (!selectedConfigId.value) return
  
  const config = savedConfigs.value.find(c => c.id === selectedConfigId.value)
  if (config) {
    loadConfigurationById(config.id)
  }
}

const loadConfigurationById = (configId: string): void => {
  const config = savedConfigs.value.find(c => c.id === configId)
  if (!config) {
    ElMessage.error('配置不存在')
    return
  }
  
  if (config.serviceType !== props.currentServiceType) {
    ElMessage.warning('配置的服务类型与当前选择不匹配')
    return
  }
  
  selectedConfigId.value = configId
  emit('config-loaded', config)
  ElMessage.success(`已加载配置: ${config.name}`)
}

const startRename = (config: SavedConfiguration): void => {
  config.editing = true
  config.editName = config.name
}

const confirmRename = async (config: SavedConfiguration): Promise<void> => {
  if (!config.editName || config.editName.trim() === '') {
    cancelRename(config)
    return
  }
  
  const newName = config.editName.trim()
  
  // 检查重名
  const exists = savedConfigs.value.some(c => 
    c.id !== config.id && 
    c.name === newName && 
    c.serviceType === config.serviceType
  )
  
  if (exists) {
    ElMessage.error('该服务类型下已存在同名配置')
    return
  }
  
  // 使用配置服务更新
  const success = configService.updateConfiguration(config.id, { name: newName })
  
  if (success) {
    config.name = newName
    config.editing = false
    delete config.editName
    ElMessage.success('配置重命名成功')
  } else {
    ElMessage.error('配置重命名失败')
  }
}

const cancelRename = (config: SavedConfiguration): void => {
  config.editing = false
  delete config.editName
}

const confirmDelete = async (config: SavedConfiguration): Promise<void> => {
  try {
    await ElMessageBox.confirm(
      `确定要删除配置 "${config.name}" 吗？此操作不可恢复。`,
      '确认删除',
      {
        confirmButtonText: '删除',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )
    
    const success = configService.deleteConfiguration(config.id)
    
    if (success) {
      // 重新加载配置列表
      loadSavedConfigs()
      
      // 如果删除的是当前选中的配置，清除选择
      if (selectedConfigId.value === config.id) {
        selectedConfigId.value = ''
      }
      
      emit('config-deleted', config.id)
      ElMessage.success('配置删除成功')
    } else {
      ElMessage.error('配置删除失败')
    }
  } catch {
    // 用户取消删除
  }
}

const exportConfigs = (): void => {
  try {
    const exportData = configService.exportConfigurations()
    const dataBlob = new Blob([exportData], { type: 'application/json' })
    
    const link = document.createElement('a')
    link.href = URL.createObjectURL(dataBlob)
    link.download = `playground-configs-${new Date().toISOString().split('T')[0]}.json`
    link.click()
    
    URL.revokeObjectURL(link.href)
    ElMessage.success('配置导出成功')
  } catch (error) {
    console.error('导出配置失败:', error)
    ElMessage.error('导出配置失败')
  }
}

const triggerImport = (): void => {
  fileInputRef.value?.click()
}

const handleImport = (event: Event): void => {
  const target = event.target as HTMLInputElement
  const file = target.files?.[0]
  
  if (!file) return
  
  const reader = new FileReader()
  reader.onload = (e) => {
    try {
      const content = e.target?.result as string
      const result = configService.importConfigurations(content)
      
      if (result.success > 0) {
        // 重新加载配置列表
        loadSavedConfigs()
        
        let message = `成功导入 ${result.success} 个配置`
        if (result.errors.length > 0) {
          message += `，${result.errors.length} 个配置导入失败`
          console.warn('导入错误:', result.errors)
        }
        
        ElMessage.success(message)
      } else {
        const errorMessage = result.errors.length > 0 
          ? result.errors[0] 
          : '导入配置失败'
        ElMessage.error(errorMessage)
      }
      
    } catch (error) {
      console.error('导入配置失败:', error)
      ElMessage.error('导入配置失败: ' + (error as Error).message)
    }
  }
  
  reader.readAsText(file)
  
  // 清除文件输入
  target.value = ''
}

const handleSaveDialogClose = (): void => {
  showSaveDialog.value = false
  saveForm.name = ''
  saveForm.description = ''
  saveFormRef.value?.clearValidate()
}

// 批量操作方法
const handleSelectionChange = (selection: SavedConfiguration[]): void => {
  selectedConfigs.value = selection
  selectAll.value = selection.length === savedConfigs.value.length
}

const handleSelectAll = (checked: boolean): void => {
  selectedConfigs.value = checked ? [...savedConfigs.value] : []
}

const batchDelete = async (): Promise<void> => {
  if (selectedConfigs.value.length === 0) return
  
  try {
    await ElMessageBox.confirm(
      `确定要删除选中的 ${selectedConfigs.value.length} 个配置吗？此操作不可恢复。`,
      '批量删除确认',
      {
        confirmButtonText: '删除',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )
    
    let successCount = 0
    const failedConfigs: string[] = []
    
    for (const config of selectedConfigs.value) {
      const success = configService.deleteConfiguration(config.id)
      if (success) {
        successCount++
      } else {
        failedConfigs.push(config.name)
      }
    }
    
    // 重新加载配置列表
    loadSavedConfigs()
    selectedConfigs.value = []
    selectAll.value = false
    
    if (successCount > 0) {
      ElMessage.success(`成功删除 ${successCount} 个配置`)
    }
    
    if (failedConfigs.length > 0) {
      ElMessage.warning(`${failedConfigs.length} 个配置删除失败: ${failedConfigs.join(', ')}`)
    }
    
  } catch {
    // 用户取消删除
  }
}

const batchExport = (): void => {
  if (selectedConfigs.value.length === 0) return
  
  try {
    const selectedIds = selectedConfigs.value.map(config => config.id)
    const exportData = configService.exportConfigurations(selectedIds)
    const dataBlob = new Blob([exportData], { type: 'application/json' })
    
    const link = document.createElement('a')
    link.href = URL.createObjectURL(dataBlob)
    link.download = `playground-configs-selected-${new Date().toISOString().split('T')[0]}.json`
    link.click()
    
    URL.revokeObjectURL(link.href)
    ElMessage.success(`成功导出 ${selectedConfigs.value.length} 个配置`)
  } catch (error) {
    console.error('批量导出配置失败:', error)
    ElMessage.error('批量导出配置失败')
  }
}

// 存储管理方法
const refreshStorageInfo = (): void => {
  storageInfo.value = configService.getStorageInfo()
}

const getStorageColor = (percentage: number): string => {
  if (percentage < 50) return '#67c23a'
  if (percentage < 80) return '#e6a23c'
  return '#f56c6c'
}

const formatBytes = (bytes: number): string => {
  if (bytes === 0) return '0 B'
  
  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  
  return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i]
}

const confirmClearAll = async (): Promise<void> => {
  try {
    await ElMessageBox.confirm(
      '确定要清空所有配置吗？此操作将删除所有保存的配置，且不可恢复。',
      '清空所有配置',
      {
        confirmButtonText: '清空',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )
    
    const success = configService.clearAllConfigurations()
    
    if (success) {
      savedConfigs.value = []
      selectedConfigs.value = []
      selectAll.value = false
      selectedConfigId.value = ''
      refreshStorageInfo()
      ElMessage.success('所有配置已清空')
    } else {
      ElMessage.error('清空配置失败')
    }
    
  } catch {
    // 用户取消操作
  }
}

// 生命周期
onMounted(() => {
  loadSavedConfigs()
})

// 暴露方法给父组件
defineExpose({
  loadSavedConfigs,
  clearSelection: () => {
    selectedConfigId.value = ''
  }
})
</script>

<style scoped>
.config-manager {
  margin-bottom: 16px;
}

.config-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 16px;
  background-color: var(--el-bg-color-page);
  border-radius: 6px;
  border: 1px solid var(--el-border-color-light);
}

.config-actions {
  display: flex;
  gap: 12px;
  align-items: center;
}

.config-option {
  display: flex;
  justify-content: space-between;
  align-items: center;
  width: 100%;
}

.config-name {
  font-weight: 500;
}

.config-service {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.config-list {
  max-height: 400px;
  overflow-y: auto;
}

.config-name-cell {
  display: flex;
  align-items: center;
}

.dialog-footer {
  display: flex;
  gap: 8px;
}

.manage-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
  padding: 12px;
  background-color: var(--el-bg-color-page);
  border-radius: 6px;
  border: 1px solid var(--el-border-color-light);
}

.toolbar-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.toolbar-right {
  display: flex;
  gap: 8px;
}

.selected-count {
  font-size: 14px;
  color: var(--el-text-color-secondary);
}

.storage-info {
  margin-top: 16px;
}

.storage-stats {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 8px 0;
}

.storage-label {
  font-size: 14px;
  color: var(--el-text-color-regular);
  margin-right: 10px;
}

.storage-text {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

/* 响应式设计 */
@media (max-width: 768px) {
  .config-toolbar {
    flex-direction: column;
    gap: 12px;
    align-items: stretch;
  }
  
  .config-actions {
    flex-direction: column;
    align-items: stretch;
  }
  
  .config-actions .el-select {
    width: 100% !important;
  }
}

/* 深色主题适配 */
@media (prefers-color-scheme: dark) {
  .config-toolbar {
    background-color: var(--el-bg-color-overlay);
  }
}
</style>