<template>
  <PageSkeleton :title="t('service.title')">
    <template #actions>
      <el-tooltip v-if="availableTypes.length === 0" :content="t('service.allTypesAdded')" placement="left">
        <el-button type="primary" @click="handleAddService" :disabled="availableTypes.length === 0" size="medium">
          <el-icon><Plus /></el-icon>
          {{ t('service.addService') }}
        </el-button>
      </el-tooltip>
      <el-button v-else type="primary" @click="handleAddService" size="medium">
        <el-icon><Plus /></el-icon>
        {{ t('service.addService') }}
      </el-button>
    </template>

    <template #stats>
      <OnboardingSteps :current-step="2" />
    </template>

    <template #toolbar>
      <el-input
        v-model="searchQuery"
        :placeholder="t('service.searchPlaceholder')"
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
        :placeholder="t('service.filters.loadBalance')"
        clearable
        size="medium"
        class="filter-select"
      >
        <el-option :label="t('service.filters.all')" value=""></el-option>
        <el-option :label="t('service.loadBalance.random')" value="random" />
        <el-option :label="t('service.loadBalance.roundRobin')" value="round-robin" />
        <el-option :label="t('service.loadBalance.leastConnections')" value="least-connections" />
      </el-select>

      <el-select
        v-model="filterAdapter"
        :placeholder="t('service.filters.adapter')"
        clearable
        size="medium"
        class="filter-select adapter-filter"
      >
        <el-option
          v-for="a in uniqueAdapters"
          :key="a"
          :label="a || t('service.unset')"
          :value="a"
        />
      </el-select>
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
          :element-loading-text="t('service.loading')"
          element-loading-background="rgba(0, 0, 0, 0.05)"
          class="service-table"
          :row-key="rowKey"
          border
          fit
        >
          <el-table-column prop="type" :label="t('service.table.serviceType')" sortable>
            <template #default="scope">
              <router-link
                :to="{ name: 'instance-management', query: { serviceType: scope.row.type } }"
                class="cell-link"
                @click.stop
              >
                <el-tag type="info" class="table-tag">
                  {{ serviceTypeLabel(scope.row.type) }}
                </el-tag>
              </router-link>
            </template>
          </el-table-column>

          <el-table-column prop="adapter" :label="t('service.table.adapter')" sortable>
            <template #default="scope">
              <el-tooltip :content="scope.row.adapter || t('service.unset')" placement="top">
                <router-link
                  v-if="scope.row.adapter"
                  :to="{ name: 'adapter-management' }"
                  class="cell-link"
                  @click.stop
                >
                  <el-tag type="success" class="table-tag">
                    {{ scope.row.adapter }}
                  </el-tag>
                </router-link>
                <el-tag v-else type="warning" class="table-tag">
                  {{ t('service.unset') }}
                </el-tag>
              </el-tooltip>
            </template>
          </el-table-column>

          <el-table-column prop="loadBalanceType" :label="t('service.table.loadBalanceType')" sortable>
            <template #default="scope">
              <el-tag :type="getLoadBalanceTagType(scope.row.loadBalanceType)" class="table-tag">
                {{ formatLoadBalanceType(scope.row.loadBalanceType) }}
              </el-tag>
            </template>
          </el-table-column>

          <el-table-column :label="t('service.table.actions')" fixed="right" width="180">
            <template #default="scope">
              <el-button size="small" @click="handleEdit(scope.row)" type="primary" plain circle :title="t('service.edit')">
                <el-icon><Edit /></el-icon>
              </el-button>

              <el-button size="small" type="danger" @click="handleDelete(scope.row)" plain circle :title="t('service.delete')">
                <el-icon><Delete /></el-icon>
              </el-button>
            </template>
          </el-table-column>
        </el-table>

        <div v-if="filteredServices.length === 0 && !loading" class="empty-wrap">
          <el-empty :description="t('service.table.notFound')">
            <template #image>
              <img src="https://static-element.eleme.cn/e/element-ui/empty.svg" alt="empty" />
            </template>
          </el-empty>
        </div>
      </template>
    </el-skeleton>

    <template #footer>
      <div class="table-footer" v-if="services.length > 0">
        <div class="footer-info">
          {{ t('service.table.summary', { count: filteredServices.length, current: currentPage, total: totalPages }) }}
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
  </PageSkeleton>

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
      <el-form-item :label="t('service.form.serviceType')" prop="type">
        <!-- 编辑时显示不可编辑的输入，添加时显示只包含未添加类型的下拉 -->
        <el-select
          v-if="!isEdit"
          v-model="form.type"
          :placeholder="t('service.form.selectServiceType')"
          size="large"
          style="width: 100%"
        >
          <el-option
            v-for="st in availableTypes"
            :key="st"
            :label="serviceTypeLabel(st)"
            :value="st"
          />
        </el-select>

        <el-input v-else v-model="form.type" disabled size="large" />
      </el-form-item>

      <el-form-item :label="t('service.form.adapter')" prop="adapter">
        <el-select
          v-model="form.adapter"
          :placeholder="t('service.form.selectAdapter')"
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

      <el-form-item :label="t('service.form.loadBalanceType')" prop="loadBalance.type">
        <el-select
          v-model="form.loadBalance.type"
          :placeholder="t('service.form.selectLoadBalance')"
          style="width: 100%"
          size="large"
        >
          <el-option :label="t('service.loadBalance.random')" value="random" />
          <el-option :label="t('service.loadBalance.roundRobin')" value="round-robin" />
          <el-option :label="t('service.loadBalance.leastConnections')" value="least-connections" />
          <el-option :label="t('service.loadBalance.ipHash')" value="ip-hash" />
        </el-select>
      </el-form-item>

      <el-form-item :label="t('service.form.description')" prop="description">
        <el-input
          type="textarea"
          v-model="form.description"
          :placeholder="t('service.form.descriptionPlaceholder')"
          rows="3"
        />
      </el-form-item>
      
      <!-- 服务级别限流配置 -->
      <el-divider content-position="left">{{ t('service.rateLimit.section') }}</el-divider>
      <el-form-item :label="t('service.rateLimit.enable')">
        <el-switch v-model="form.rateLimit.enabled" :active-text="t('service.enabled')" :inactive-text="t('service.disabled')" />
      </el-form-item>
      
      <div v-if="form.rateLimit.enabled">
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item :label="t('service.rateLimit.algorithm')">
              <el-select v-model="form.rateLimit.algorithm" :placeholder="t('service.rateLimit.selectAlgorithm')">
                <el-option :label="t('service.rateLimit.tokenBucket')" value="token-bucket" />
                <el-option :label="t('service.rateLimit.leakyBucket')" value="leaky-bucket" />
                <el-option :label="t('service.rateLimit.slidingWindow')" value="sliding-window" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="t('service.rateLimit.scope')">
              <el-select v-model="form.rateLimit.scope" :placeholder="t('service.rateLimit.selectScope')" disabled>
                <el-option :label="t('service.rateLimit.serviceScope')" value="service" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item :label="t('service.rateLimit.capacity')">
              <el-input-number v-model="form.rateLimit.capacity" :min="1" :max="10000" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="t('service.rateLimit.rate')">
              <el-input-number v-model="form.rateLimit.rate" :min="1" :max="10000" />
            </el-form-item>
          </el-col>
        </el-row>
        
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item :label="t('service.rateLimit.key')">
              <el-input v-model="form.rateLimit.key" :placeholder="t('service.rateLimit.keyPlaceholder')" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="t('service.rateLimit.clientIp')">
              <el-switch v-model="form.rateLimit.clientIpEnable" :active-text="t('service.enabled')" :inactive-text="t('service.disabled')" />
            </el-form-item>
          </el-col>
        </el-row>
      </div>
      
      <!-- 服务级别熔断器配置 -->
      <el-divider content-position="left">{{ t('service.circuitBreaker.section') }}</el-divider>
      <el-form-item :label="t('service.circuitBreaker.enable')">
        <el-switch v-model="form.circuitBreaker.enabled" :active-text="t('service.enabled')" :inactive-text="t('service.disabled')" />
      </el-form-item>
      
      <div v-if="form.circuitBreaker.enabled">
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item :label="t('service.circuitBreaker.failureThreshold')">
              <el-input-number v-model="form.circuitBreaker.failureThreshold" :min="1" :max="100" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="t('service.circuitBreaker.timeoutMs')">
              <el-input-number v-model="form.circuitBreaker.timeout" :min="1000" :max="300000" />
            </el-form-item>
          </el-col>
        </el-row>
        
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item :label="t('service.circuitBreaker.successThreshold')">
              <el-input-number v-model="form.circuitBreaker.successThreshold" :min="1" :max="100" />
            </el-form-item>
          </el-col>
        </el-row>
      </div>
    </el-form>

    <template #footer>
      <span class="dialog-footer">
        <el-button @click="handleDialogClose" size="large">{{ t('service.cancel') }}</el-button>
        <el-button type="primary" @click="handleSave" :loading="dialogLoading" size="large">{{ t('service.save') }}</el-button>
      </span>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, onMounted, reactive, computed, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox, ElNotification } from 'element-plus'
import { SERVICE_TYPE_LABELS, COMMON_SERVICE_TYPES } from '@/constants/serviceTypes'
import type { FormInstance, FormRules } from 'element-plus'
import { 
  getServiceTypes, 
  getAllServicesWithConfig, // 添加新导入
  getServiceConfig, 
  createService, 
  updateServiceConfig, 
  deleteService,
  getAdapters,
  getServiceRateLimit,
  updateServiceRateLimit
} from '@/api/service'
import PageSkeleton from '@/components/PageSkeleton.vue'
import OnboardingSteps from './adapter/OnboardingSteps.vue'

const route = useRoute()
const router = useRouter()
const { t } = useI18n()

// 支持的服务类型列表（保持原有）
const supportedTypes: string[] = [
  ...COMMON_SERVICE_TYPES
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
  // 添加服务级别的限流和熔断配置
  rateLimit: {
    enabled: boolean
    algorithm: string
    capacity: number
    rate: number
    scope: string
    key: string
    clientIpEnable: boolean
  }
  circuitBreaker: {
    enabled: boolean
    failureThreshold: number
    timeout: number
    successThreshold: number
  }
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

// v2.10.4: SERVICE_TYPE_LABELS 值 = 顶层 serviceTypes.* i18n key，渲染点用 t() 翻译；未知类型回退原值
const serviceTypeMap: Record<string, string> = SERVICE_TYPE_LABELS as Record<string, string>

// 服务类型显示名（未知类型回退原值）
const serviceTypeLabel = (type: string): string => {
  const key = serviceTypeMap[type]
  return key ? t(key) : type
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
  description: '',
  // 初始化服务级别的限流和熔断配置
  rateLimit: {
    enabled: false,
    algorithm: 'token-bucket',
    capacity: 100,
    rate: 10,
    scope: 'service', // 服务级别
    key: '',
    clientIpEnable: false
  },
  circuitBreaker: {
    enabled: false,
    failureThreshold: 5,
    timeout: 60000,
    successThreshold: 2
  }
})

// 验证规则（v2.10.3: computed 化，语言切换后校验文案即时更新）
const rules = computed<FormRules<ServiceForm>>(() => ({
  type: [
    { required: true, message: t('service.form.selectServiceType'), trigger: 'change' }
  ],
  adapter: [
    { required: true, message: t('service.form.selectAdapter'), trigger: 'change' }
  ],
  'loadBalance.type': [
    { required: true, message: t('service.form.selectLoadBalance'), trigger: 'change' }
  ]
}))

// 修复表格行键的类型问题
const rowKey = (row: Service) => row.type

const getLoadBalanceTagType = (type: string) => {
  switch (type) {
    case 'random': return 'primary'
    case 'round-robin': return 'success'
    case 'least-connections': return 'warning'
    case 'ip-hash': return 'info'
    default: return 'info'
  }
}
const formatLoadBalanceType = (type: string) => {
  switch (type) {
    case 'random': return t('service.loadBalance.random')
    case 'round-robin': return t('service.loadBalance.roundRobin')
    case 'least-connections': return t('service.loadBalance.leastConnections')
    case 'ip-hash': return t('service.loadBalance.ipHash')
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

// Cross-link: 从路由 query 中读取 serviceType，设置搜索过滤
watch(() => route.query.serviceType, (val) => {
  if (val && typeof val === 'string') {
    searchQuery.value = val
    currentPage.value = 1
  }
}, { immediate: true })

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
          title: t('service.messages.warning'), 
          message: t('service.messages.fetchAdaptersFailed', { message: adaptersResponse.data?.message || t('service.messages.unknownError') }), 
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
        const errorMsg = servicesResponse.data?.message || t('service.messages.fetchServicesFailed')
        errorMessage.value = errorMsg
        ElMessage.error(errorMsg)
      }
    } catch (error: any) {
      console.error('获取服务列表失败:', error)
      const errorMsg = error?.message || t('service.messages.networkError')
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
    ElMessage.info(t('service.allTypesAdded'))
    return
  }

  dialogTitle.value = t('service.addService')
  isEdit.value = false
  form.type = '' // 由下拉选择填入
  form.adapter = ''
  form.loadBalance.type = 'random'
  form.description = ''
  dialogVisible.value = true
}

const handleEdit = async (row: Service) => {
  dialogTitle.value = t('service.editService')
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
      // 添加服务级别的限流和熔断配置
      form.rateLimit = {
        enabled: cfg.rateLimit?.enabled || false,
        algorithm: cfg.rateLimit?.algorithm || 'token-bucket',
        capacity: cfg.rateLimit?.capacity || 100,
        rate: cfg.rateLimit?.rate || 10,
        scope: cfg.rateLimit?.scope || 'service',
        key: cfg.rateLimit?.key || '',
        clientIpEnable: cfg.rateLimit?.clientIpEnable || false
      }
      form.circuitBreaker = {
        enabled: cfg.circuitBreaker?.enabled || false,
        failureThreshold: cfg.circuitBreaker?.failureThreshold || 5,
        timeout: cfg.circuitBreaker?.timeout || 60000,
        successThreshold: cfg.circuitBreaker?.successThreshold || 2
      }
      // v2.8.8: 从 ratelimit 端点加载 canonical 格式的限流配置(覆盖通用配置里可能缺失的字段)
      const rateLimitRes = await getServiceRateLimit(row.type)
      const rl = rateLimitRes.data?.data
      if (rl && Object.keys(rl).length > 0) {
        form.rateLimit = {
          enabled: rl.enabled ?? false,
          algorithm: rl.algorithm || 'token-bucket',
          capacity: rl.capacity || 100,
          rate: rl.rate || 10,
          scope: rl.scope || 'service',
          key: rl.key || '',
          clientIpEnable: rl.clientIpEnable || false
        }
      }
      dialogVisible.value = true
    } else {
      ElMessage.error(response.data?.message || t('service.messages.fetchConfigFailed'))
    }
  } catch (error: any) {
    console.error('获取服务配置失败:', error)
    ElMessage.error(error?.message || t('service.messages.networkError'))
  } finally {
    dialogLoading.value = false
  }
}

// 删除
const handleDelete = (row: Service) => {
  ElMessageBox.confirm(
    t('service.deleteDialog.message', { type: row.type }),
    t('service.deleteDialog.title'),
    { confirmButtonText: t('service.confirm'), cancelButtonText: t('service.cancel'), type: 'warning' }
  ).then(async () => {
    try {
      const response = await deleteService(row.type)
      if (response.data?.success) {
        ElMessage.success(t('service.messages.deleteSuccess'))
        await fetchServices()
      } else {
        ElMessage.error(response.data?.message || t('service.messages.deleteFailed'))
      }
    } catch (error: any) {
      console.error('删除服务失败:', error)
      ElMessage.error(error?.message || t('service.messages.networkError'))
    }
  }).catch(() => {
    ElMessage.info(t('service.messages.deleteCancelled'))
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
          description: form.description || undefined,
          // 添加服务级别的限流和熔断配置
          rateLimit: form.rateLimit.enabled ? form.rateLimit : { enabled: false },
          circuitBreaker: form.circuitBreaker.enabled ? form.circuitBreaker : { enabled: false }
        }
        let response
        if (isEdit.value) response = await updateServiceConfig(form.type, serviceConfig)
        else response = await createService(form.type, serviceConfig)

        if (response.data?.success) {
          // v2.8.8: 服务级限流配置持久化 + 热生效(独立端点,canonical 格式)
          try {
            const rateLimitPayload = form.rateLimit.enabled
              ? {
                  enabled: true,
                  algorithm: form.rateLimit.algorithm,
                  capacity: form.rateLimit.capacity,
                  rate: form.rateLimit.rate,
                  scope: form.rateLimit.scope,
                  key: form.rateLimit.key
                }
              : { enabled: false }
            await updateServiceRateLimit(form.type, rateLimitPayload)
          } catch (rateLimitError: any) {
            console.error('保存服务限流配置失败:', rateLimitError)
            ElMessage.warning(rateLimitError?.message || t('service.messages.savedButRateLimitFailed'))
          }
          ElMessage.success(isEdit.value ? t('service.messages.editSuccess') : t('service.messages.addSuccess'))
          dialogVisible.value = false
          // 清除所有缓存，强制刷新数据
          const cacheKey = 'services_data'
          delete serviceCache.value[cacheKey]
          const serviceCacheKey = `service_config_${form.type}`
          delete serviceCache.value[serviceCacheKey]
          // 立即刷新列表，不使用防抖
          if (fetchTimer) {
            clearTimeout(fetchTimer)
            fetchTimer = null
          }
          await fetchServices()
          // 如果是编辑，刷新后高亮显示
          if (isEdit.value) {
            setTimeout(() => {
              const row = document.querySelector(`[data-type="${form.type}"]`)
              if (row) {
                row.scrollIntoView({ behavior: 'smooth', block: 'center' })
                row.classList.add('highlight-row')
                setTimeout(() => row.classList.remove('highlight-row'), 2000)
              }
            }, 500)
          }
        } else {
          ElMessage.error(response.data?.message || (isEdit.value ? t('service.messages.editFailed') : t('service.messages.addFailed')))
        }
      } catch (error: any) {
        console.error(isEdit.value ? '编辑服务失败:' : '添加服务失败:', error)
        ElMessage.error(error?.message || t('service.messages.networkError'))
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
/* 搜索/筛选工具栏宽度 */
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

.error-alert {
  margin-bottom: 16px;
}

/* 表格样式 */
.service-table {
  width: 100%;
  background: var(--ja-bg-card);
  border-radius: var(--ja-radius);
  overflow: hidden;
}

.service-table :deep(.el-table__row:hover) {
  background-color: var(--el-fill-color-light);
}

.service-table :deep(.el-table__cell) {
  font-size: 14px;
  padding: 14px 12px;
}

.service-table :deep(.el-table__header th) {
  font-size: 14px;
  font-weight: 600;
  color: var(--ja-text-primary);
  background-color: var(--ja-main-bg);
}

.table-tag {
  font-size: 13px;
  padding: 6px 10px;
}

/* Cross-link: 单元格内可点击链接 */
.cell-link {
  text-decoration: none;
  cursor: pointer;
}

/* 底栏/分页 */
.table-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  width: 100%;
}

.footer-info {
  color: var(--ja-text-secondary);
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

/* 对话框 */
.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 15px;
  padding: 10px 20px;
}

.service-dialog :deep(.el-dialog__header) {
  background-color: var(--ja-main-bg);
  border-bottom: 1px solid var(--ja-border-light);
  padding: 14px 20px;
}

.service-dialog :deep(.el-dialog__title) {
  font-weight: 700;
  color: var(--ja-text-primary);
  font-size: 18px;
}

.service-dialog :deep(.el-dialog__body) {
  padding: 20px;
}

.service-dialog :deep(.el-form-item__label) {
  font-weight: 600;
  font-size: 14px;
  color: var(--ja-text-primary);
}

.service-dialog :deep(.el-input__inner),
.service-dialog :deep(.el-select) {
  font-size: 14px;
}
</style>
