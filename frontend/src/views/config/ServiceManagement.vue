<template>
  <div class="service-management">
    <el-card class="service-card">
      <template #header>
        <div class="card-header">
          <div class="header-left">
            <div class="header-title">
              <el-icon><Setting /></el-icon>
              <span>服务管理</span>
            </div>

            <div class="header-tools">
              <el-input
                v-model="searchQuery"
                placeholder="搜索服务类型或适配器（回车或暂停）"
                clearable
                size="medium"
                class="search-input"
                @clear="handleSearchClear"
                @keyup.enter.native="applySearch"
              >
                <template #prefix>
                  <el-icon><Search /></el-icon>
                </template>
              </el-input>

              <el-select
                v-model="filterLoadBalance"
                placeholder="负载策略"
                clearable
                size="medium"
                class="filter-select"
              >
                <el-option label="全部" value=""></el-option>
                <el-option label="随机" value="random" />
                <el-option label="轮询" value="round-robin" />
                <el-option label="权重" value="weighted" />
              </el-select>

              <el-select
                v-model="filterAdapter"
                placeholder="适配器"
                clearable
                size="medium"
                class="filter-select adapter-filter"
              >
                <el-option
                  v-for="a in uniqueAdapters"
                  :key="a"
                  :label="a || '未设置'"
                  :value="a"
                />
              </el-select>
            </div>
          </div>

          <div class="header-actions">
            <el-tooltip v-if="availableTypes.length === 0" content="已添加所有类型服务" placement="left">
              <el-button type="primary" @click="handleAddService" :disabled="availableTypes.length === 0" size="medium">
                <el-icon><Plus /></el-icon>
                添加服务
              </el-button>
            </el-tooltip>
            <el-button v-else type="primary" @click="handleAddService" size="medium">
              <el-icon><Plus /></el-icon>
              添加服务
            </el-button>
          </div>
        </div>
      </template>

      <el-alert
        v-if="errorMessage"
        :title="errorMessage"
        type="error"
        show-icon
        closable
        @close="errorMessage = ''"
        class="error-alert"
      />

      <div class="table-container">
        <el-skeleton :loading="loading && services.length === 0" :rows="6" animated>
          <template #template>
            <el-skeleton-item variant="h2" style="width: 40%; margin-bottom: 12px;" />
            <el-skeleton-item style="height: 42px; margin-bottom: 12px;" />
            <el-skeleton-item style="height: 300px" />
          </template>

          <template #default>
            <el-table
              :data="paginatedServices"
              style="width: 100%"
              v-loading="loading"
              element-loading-text="加载中..."
              element-loading-background="rgba(0, 0, 0, 0.05)"
              class="service-table"
              :row-key="rowKey"
              border
              fit
            >
              <el-table-column prop="type" label="服务类型" sortable>
                <template #default="scope">
                  <el-tag type="info" class="table-tag">
                    {{ scope.row.type }}
                  </el-tag>
                </template>
              </el-table-column>

              <el-table-column prop="adapter" label="适配器" sortable>
                <template #default="scope">
                  <el-tooltip :content="scope.row.adapter || '未设置'" placement="top">
                    <el-tag :type="scope.row.adapter ? 'success' : 'warning'" class="table-tag">
                      {{ scope.row.adapter || '未设置' }}
                    </el-tag>
                  </el-tooltip>
                </template>
              </el-table-column>

              <el-table-column prop="loadBalanceType" label="负载均衡策略" sortable>
                <template #default="scope">
                  <el-tag :type="getLoadBalanceTagType(scope.row.loadBalanceType)" class="table-tag">
                    {{ formatLoadBalanceType(scope.row.loadBalanceType) }}
                  </el-tag>
                </template>
              </el-table-column>

              <el-table-column label="操作" fixed="right" width="180">
                <template #default="scope">
                  <el-button size="small" @click="handleEdit(scope.row)" type="primary" plain circle title="编辑">
                    <el-icon><Edit /></el-icon>
                  </el-button>

                  <el-button size="small" type="danger" @click="handleDelete(scope.row)" plain circle title="删除">
                    <el-icon><Delete /></el-icon>
                  </el-button>
                </template>
              </el-table-column>
            </el-table>

            <div v-if="filteredServices.length === 0 && !loading" class="empty-wrap">
              <el-empty description="未找到服务">
                <template #image>
                  <img src="https://static-element.eleme.cn/e/element-ui/empty.svg" alt="empty" />
                </template>
              </el-empty>
            </div>

            <div class="table-footer" v-if="services.length > 0">
              <div class="footer-info">
                共 {{ filteredServices.length }} 条（第 {{ currentPage }} / {{ totalPages }} 页）
              </div>

              <div class="footer-actions">
                <el-pagination
                  v-model:current-page="currentPage"
                  :page-size="pageSize"
                  :total="filteredServices.length"
                  layout="prev, pager, next, sizes, jumper"
                  :page-sizes="[5, 10, 20, 50]"
                  @size-change="handleSizeChange"
                  @current-change="handlePageChange"
                />
              </div>
            </div>
          </template>
        </el-skeleton>
      </div>
    </el-card>

    <!-- 添加/编辑服务对话框 -->
    <el-dialog
      v-model="dialogVisible"
      :title="dialogTitle"
      width="600px"
      :before-close="handleDialogClose"
      class="service-dialog"
    >
      <el-form
        :model="form"
        label-width="140px"
        ref="formRef"
        :rules="rules"
        v-loading="dialogLoading"
      >
        <el-form-item label="服务类型" prop="type">
          <!-- 编辑时显示不可编辑的输入，添加时显示只包含未添加类型的下拉 -->
          <el-select
            v-if="!isEdit"
            v-model="form.type"
            placeholder="请选择服务类型"
            size="large"
            style="width: 100%"
          >
            <el-option
              v-for="t in availableTypes"
              :key="t"
              :label="t"
              :value="t"
            />
          </el-select>

          <el-input v-else v-model="form.type" disabled size="large" />
        </el-form-item>

        <el-form-item label="适配器" prop="adapter">
          <el-select
            v-model="form.adapter"
            placeholder="请选择适配器"
            size="large"
            style="width: 100%"
            clearable
            filterable
          >
            <el-option
              v-for="adapter in adapters"
              :key="adapter.name"
              :label="adapter.name"
              :value="adapter.name"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="负载均衡策略" prop="loadBalance.type">
          <el-select
            v-model="form.loadBalance.type"
            placeholder="请选择负载均衡策略"
            style="width: 100%"
            size="large"
          >
            <el-option label="随机" value="random" />
            <el-option label="轮询" value="round-robin" />
            <el-option label="权重" value="weighted" />
          </el-select>
        </el-form-item>

        <el-form-item label="说明（可选）" prop="description">
          <el-input
            type="textarea"
            v-model="form.description"
            placeholder="对该服务做简短说明，方便识别（例如：用于客服对话）"
            rows="3"
          />
        </el-form-item>
      </el-form>

      <template #footer>
        <span class="dialog-footer">
          <el-button @click="handleDialogClose" size="large">取消</el-button>
          <el-button type="primary" @click="handleSave" :loading="dialogLoading" size="large">保存</el-button>
        </span>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, reactive, computed, watch } from 'vue'
import { ElMessage, ElMessageBox, ElNotification } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { 
  getServiceTypes, 
  getAllServicesWithConfig, // 添加新导入
  getServiceConfig, 
  createService, 
  updateServiceConfig, 
  deleteService,
  getAdapters
} from '@/api/service'

// 支持的服务类型列表（保持原有）
const supportedTypes = [
  'chat', 'embedding', 'rerank', 'tts', 'stt', 'imgGen', 'imgEdit'
]

// 服务定义
interface Service {
  type: string
  adapter: string
  loadBalanceType: string
  description?: string
}

// 表单定义
interface ServiceForm {
  type: string
  adapter: string
  loadBalance: {
    type: string
  }
  description: string
}

// 状态管理
const services = ref<Service[]>([])
const loading = ref(false)
const dialogLoading = ref(false)
const dialogVisible = ref(false)
const dialogTitle = ref('')
const isEdit = ref(false)
const errorMessage = ref('')
const formRef = ref<FormInstance>()
const searchQuery = ref('')
const filterLoadBalance = ref('')
const filterAdapter = ref<string | undefined>(undefined)
const currentPage = ref(1)
const pageSize = ref(10)

// 服务类型映射（保持原有）
const serviceTypeMap: Record<string, string> = {
  chat: '聊天服务',
  embedding: '嵌入服务',
  rerank: '重排序服务',
  tts: '文本转语音',
  stt: '语音转文本',
  imgGen: '图像生成',
  imgEdit: '图像编辑服务'
}

// 服务数据缓存
const serviceCache = ref<Record<string, any>>({})
// 适配器数据缓存
const adaptersCache = ref<any[]>([])
// 请求防抖定时器
let fetchTimer: ReturnType<typeof setTimeout> | null = null

// 表单数据
const form = reactive<ServiceForm>({
  type: '',
  adapter: '',
  loadBalance: { type: 'random' },
  description: ''
})

// 验证规则
const rules = reactive<FormRules<ServiceForm>>({
  type: [
    { required: true, message: '请选择服务类型', trigger: 'change' }
  ],
  adapter: [
    { required: true, message: '请选择适配器', trigger: 'change' }
  ],
  'loadBalance.type': [
    { required: true, message: '请选择负载均衡策略', trigger: 'change' }
  ]
})

// 修复表格行键的类型问题
const rowKey = (row: Service) => row.type

const getLoadBalanceTagType = (type: string) => {
  switch (type) {
    case 'random': return 'primary'
    case 'round-robin': return 'success'
    case 'weighted': return 'warning'
    default: return 'info'
  }
}
const formatLoadBalanceType = (type: string) => {
  switch (type) {
    case 'random': return '随机'
    case 'round-robin': return '轮询'
    case 'weighted': return '权重'
    default: return type
  }
}

function debounce<T extends (...args: any[]) => void>(fn: T, wait = 300) {
  let timeout: ReturnType<typeof setTimeout> | null = null
  return (...args: Parameters<T>) => {
    if (timeout) clearTimeout(timeout)
    timeout = setTimeout(() => fn(...args), wait)
  }
}

const applySearch = debounce(() => { currentPage.value = 1 }, 350)
const handleSearchClear = () => { currentPage.value = 1 }

// 添加适配器相关数据
const adapters = ref<any[]>([])

// 计算当前尚未被添加的类型
const availableTypes = computed(() => {
  const added = new Set(services.value.map(s => s.type))
  return supportedTypes.filter(t => !added.has(t))
})

const uniqueAdapters = computed(() => {
  const set = new Set<string>()
  services.value.forEach(s => set.add(s.adapter || ''))
  return Array.from(set)
})

const filteredServices = computed(() => {
  const q = (searchQuery.value || '').trim().toLowerCase()
  return services.value.filter(s => {
    if (filterLoadBalance.value && s.loadBalanceType !== filterLoadBalance.value) return false
    if (filterAdapter.value !== undefined && filterAdapter.value !== '' && s.adapter !== filterAdapter.value) return false
    if (!q) return true
    return (
      s.type.toLowerCase().includes(q) ||
      (s.adapter && s.adapter.toLowerCase().includes(q)) ||
      (s.description && s.description.toLowerCase().includes(q))
    )
  })
})

const totalPages = computed(() => Math.max(1, Math.ceil(filteredServices.value.length / pageSize.value)))
const paginatedServices = computed(() => {
  const start = (currentPage.value - 1) * pageSize.value
  return filteredServices.value.slice(start, start + pageSize.value)
})

watch([filteredServices, pageSize], () => {
  if (currentPage.value > totalPages.value) currentPage.value = totalPages.value
})

// 获取服务数据（带缓存和防抖）
const fetchServices = async () => {
  // 清除之前的定时器
  if (fetchTimer) {
    clearTimeout(fetchTimer)
  }
  
  // 设置新的定时器（防抖）
  fetchTimer = setTimeout(async () => {
    loading.value = true
    errorMessage.value = ''
    try {
      // 检查缓存
      const cacheKey = 'services_data'
      const cachedData = serviceCache.value[cacheKey]
      if (cachedData && Date.now() - cachedData.timestamp < 30000) { // 30秒内使用缓存
        services.value = [...cachedData.data]
        loading.value = false
        return
      }
      
      // 并行获取所有服务配置和适配器列表（优化：使用单个请求获取所有服务配置）
      const [servicesResponse, adaptersResponse] = await Promise.all([
        getAllServicesWithConfig(), // 使用优化接口
        getAdapters()
      ])
      
      // 处理适配器数据
      if (adaptersResponse.data?.success) {
        const adapterData = adaptersResponse.data.data || []
        adapters.value = adapterData
        // 缓存适配器数据
        adaptersCache.value = adapterData
      } else {
        // 使用缓存的适配器数据
        if (adaptersCache.value.length > 0) {
          adapters.value = [...adaptersCache.value]
        }
        ElNotification({ 
          title: '警告', 
          message: `获取适配器列表失败: ${adaptersResponse.data?.message || '未知错误'}`, 
          type: 'warning', 
          duration: 3000 
        })
      }
      
      // 处理服务数据（优化：单次请求获取所有服务配置）
      if (servicesResponse.data?.success) {
        const servicesData: Record<string, any> = servicesResponse.data.data || {}
        const serviceList: Service[] = []

        // 遍历所有服务类型并构建服务列表
        for (const [type, cfg] of Object.entries(servicesData)) {
          const serviceData = {
            type,
            adapter: cfg.adapter || '',
            loadBalanceType: cfg.loadBalance?.type || 'random',
            description: cfg.description || ''
          }
          serviceList.push(serviceData)
          
          // 缓存服务配置
          const serviceCacheKey = `service_config_${type}`
          serviceCache.value[serviceCacheKey] = {
            data: serviceData,
            timestamp: Date.now()
          }
        }

        serviceList.sort((a, b) => a.type.localeCompare(b.type))
        services.value = serviceList
        // 缓存服务列表
        serviceCache.value[cacheKey] = {
          data: [...serviceList],
          timestamp: Date.now()
        }
      } else {
        const errorMsg = servicesResponse.data?.message || '获取服务列表失败'
        errorMessage.value = errorMsg
        ElMessage.error(errorMsg)
      }
    } catch (error: any) {
      console.error('获取服务列表失败:', error)
      const errorMsg = error?.message || '网络错误，请检查网络连接'
      errorMessage.value = errorMsg
      ElMessage.error(errorMsg)
    } finally {
      loading.value = false
    }
  }, 300) // 300ms防抖延迟
}

const handleAddService = () => {
  // 如果没有可添加类型，提示并返回（防止打开空选择框）
  if (availableTypes.value.length === 0) {
    ElMessage.info('已添加所有类型服务')
    return
  }

  dialogTitle.value = '添加服务'
  isEdit.value = false
  form.type = '' // 由下拉选择填入
  form.adapter = ''
  form.loadBalance.type = 'random'
  form.description = ''
  dialogVisible.value = true
}

const handleEdit = async (row: Service) => {
  dialogTitle.value = '编辑服务'
  isEdit.value = true
  dialogLoading.value = true
  try {
    const response = await getServiceConfig(row.type)
    if (response.data?.success) {
      const cfg = response.data.data || {}
      form.type = row.type
      form.adapter = cfg.adapter || row.adapter || ''
      form.loadBalance.type = cfg.loadBalance?.type || row.loadBalanceType || 'random'
      form.description = cfg.description || ''
      dialogVisible.value = true
    } else {
      ElMessage.error(response.data?.message || '获取服务配置失败')
    }
  } catch (error: any) {
    console.error('获取服务配置失败:', error)
    ElMessage.error(error?.message || '网络错误，请检查网络连接')
  } finally {
    dialogLoading.value = false
  }
}

// 删除
const handleDelete = (row: Service) => {
  ElMessageBox.confirm(
    `确定要删除服务 "${row.type}" 吗？此操作不可恢复！`,
    '删除确认',
    { confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning' }
  ).then(async () => {
    try {
      const response = await deleteService(row.type)
      if (response.data?.success) {
        ElMessage.success('服务删除成功')
        await fetchServices()
      } else {
        ElMessage.error(response.data?.message || '删除失败')
      }
    } catch (error: any) {
      console.error('删除服务失败:', error)
      ElMessage.error(error?.message || '网络错误，请检查网络连接')
    }
  }).catch(() => {
    ElMessage.info('已取消删除')
  })
}

const handleDialogClose = () => {
  dialogVisible.value = false
  if (formRef.value) formRef.value.resetFields()
}

const handleSave = async () => {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (valid) {
      dialogLoading.value = true
      try {
        const serviceConfig: any = {
          adapter: form.adapter || undefined,
          loadBalance: form.loadBalance,
          description: form.description || undefined
        }
        let response
        if (isEdit.value) response = await updateServiceConfig(form.type, serviceConfig)
        else response = await createService(form.type, serviceConfig)

        if (response.data?.success) {
          ElMessage.success(isEdit.value ? '服务编辑成功' : '服务添加成功')
          dialogVisible.value = false
          await fetchServices()
        } else {
          ElMessage.error(response.data?.message || (isEdit.value ? '编辑失败' : '添加失败'))
        }
      } catch (error: any) {
        console.error(isEdit.value ? '编辑服务失败:' : '添加服务失败:', error)
        ElMessage.error(error?.message || '网络错误，请检查网络连接')
      } finally {
        dialogLoading.value = false
      }
    }
  })
}

const handleSizeChange = (size: number) => { pageSize.value = size; currentPage.value = 1 }
const handlePageChange = (page: number) => { currentPage.value = page }

onMounted(() => { fetchServices() })
</script>

<style scoped>
.service-management {
  padding: 24px;
  background: linear-gradient(180deg, #f7f9fc 0%, #ffffff 100%);
  min-height: calc(100vh - 80px);
  width: 100%;
  box-sizing: border-box;
}

.service-card {
  box-shadow: 0 6px 20px rgba(15, 23, 42, 0.06);
  border-radius: 12px;
  display: flex;
  flex-direction: column;
  width: 100%;
  padding: 0;
  min-height: 480px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 18px 22px;
  gap: 12px;
  flex-wrap: wrap;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 16px;
  flex: 1;
  min-width: 320px;
}

.header-title {
  display: flex;
  align-items: center;
  font-size: 20px;
  font-weight: 700;
  color: #1f2d3d;
  margin-right: 12px;
}

.header-title .el-icon {
  margin-right: 10px;
  font-size: 22px;
  color: #409eff;
}

.header-tools {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.search-input {
  width: 360px;
  min-width: 220px;
}

.filter-select {
  width: 160px;
  min-width: 120px;
}

.adapter-filter {
  width: 180px;
  min-width: 140px;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

.error-alert {
  margin: 0 22px 16px 22px;
}

.table-container {
  padding: 12px 22px 18px 22px;
  display: flex;
  flex-direction: column;
  gap: 12px;
  width: 100%;
  box-sizing: border-box;
}

.service-table {
  width: 100%;
  background: #fff;
  border-radius: 8px;
  overflow: hidden;
}

.service-table :deep(.el-table__row:hover) {
  background-color: #f5f7fa;
}

.service-table :deep(.el-table__cell) {
  font-size: 14px;
  padding: 14px 12px;
}

.service-table :deep(.el-table__header th) {
  font-size: 14px;
  font-weight: 600;
  color: #2b3a4b;
  background-color: #fbfdff;
}

.table-tag {
  font-size: 13px;
  padding: 6px 10px;
}

.table-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px solid #eef2f6;
}

.footer-info {
  color: #6b7785;
  font-size: 14px;
  font-weight: 500;
}

.footer-actions {
  display: flex;
  align-items: center;
}

.empty-wrap {
  padding: 40px 0;
  display: flex;
  justify-content: center;
  align-items: center;
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 15px;
  padding: 10px 20px;
}

.service-dialog :deep(.el-dialog__header) {
  background-color: #fbfdff;
  border-bottom: 1px solid #eef2f6;
  padding: 14px 20px;
}

.service-dialog :deep(.el-dialog__title) {
  font-weight: 700;
  color: #1f2d3d;
  font-size: 18px;
}

.service-dialog :deep(.el-dialog__body) {
  padding: 20px;
}

.service-dialog :deep(.el-form-item__label) {
  font-weight: 600;
  font-size: 14px;
  color: #213142;
}

.service-dialog :deep(.el-input__inner),
.service-dialog :deep(.el-select) {
  font-size: 14px;
}
</style>