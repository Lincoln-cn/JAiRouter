<template>
  <div class="instance-management">
    <el-card class="instance-card">
      <template #header>
        <div class="card-header">
          <div class="header-left">
            <div class="header-title">
              <el-icon>
                <Management />
              </el-icon>
              <span>实例管理</span>
            </div>

            <div class="header-tools">
              <el-input v-model="searchQuery" placeholder="搜索实例名 / URL / 路径（回车或停止输入生效）" clearable size="medium"
                class="search-input" @clear="handleSearchClear" @keyup.enter.native="applySearch">
                <template #prefix>
                  <el-icon>
                    <Search />
                  </el-icon>
                </template>
              </el-input>

              <el-select v-model="statusFilter" placeholder="状态" clearable size="medium" class="filter-select">
                <el-option label="全部" value=""></el-option>
                <el-option label="启用" value="active"></el-option>
                <el-option label="禁用" value="inactive"></el-option>
              </el-select>

              <el-button type="text" class="refresh-button" @click="refreshCurrent">
                <el-icon>
                  <Refresh />
                </el-icon>
              </el-button>
            </div>
          </div>

          <div class="header-actions">
            <el-button type="primary" @click="handleAddInstance" size="medium">
              <el-icon>
                <Plus />
              </el-icon>
              添加实例
            </el-button>
          </div>
        </div>
      </template>

      <div class="tabs-wrap">
        <el-tabs v-model="activeServiceType" class="service-tabs" type="card">
          <el-tab-pane v-for="serviceType in serviceTypes" :key="serviceType"
            :label="serviceTypeMap[serviceType] || serviceType" :name="serviceType">
            <div class="table-area">
              <el-skeleton :loading="loading && !hasInstances" :rows="6" animated>
                <template #default>
                  <el-table :data="paginated" style="width: 100%" class="instance-table" row-key="id" border fit>
                    <el-table-column prop="name" label="实例名称" min-width="180" />
                    <el-table-column prop="baseUrl" label="基础URL" min-width="260">
                      <template #default="scope">
                        <el-tooltip :content="scope.row.baseUrl" placement="top">
                          <div class="ellipsis">{{ scope.row.baseUrl }}</div>
                        </el-tooltip>
                      </template>
                    </el-table-column>
                    <el-table-column prop="path" label="路径" min-width="160">
                      <template #default="scope">
                        <div class="ellipsis">{{ scope.row.path || '—' }}</div>
                      </template>
                    </el-table-column>
                    <el-table-column prop="weight" label="权重" width="90" align="center" />
                    <el-table-column prop="adapter" label="适配器" width="140" align="center">
                      <template #default="scope">
                        <el-tag :type="scope.row.adapter ? 'primary' : 'warning'" class="table-tag" size="small">
                          {{ scope.row.adapter || globalAdapter || '未配置' }}
                        </el-tag>
                        <div v-if="!scope.row.adapter && globalAdapter" class="adapter-note">
                          (全局)
                        </div>
                      </template>
                    </el-table-column>
                    <el-table-column prop="headers" label="请求头" width="120" align="center">
                      <template #default="scope">
                        <el-tooltip v-if="scope.row.headers && Object.keys(scope.row.headers).length > 0"
                          :content="Object.entries(scope.row.headers).map(([k, v]) => `${k}: ${v}`).join('\n')"
                          placement="top">
                          <el-tag type="success" class="table-tag" size="small">
                            {{ Object.keys(scope.row.headers).length }} 个
                          </el-tag>
                        </el-tooltip>
                        <el-tag v-else type="info" class="table-tag" size="small">
                          无
                        </el-tag>
                      </template>
                    </el-table-column>
                    <el-table-column prop="status" label="状态" width="110" align="center">
                      <template #default="scope">
                        <el-tag :type="scope.row.status === 'active' ? 'success' : 'info'" class="table-tag">
                          {{ scope.row.status === 'active' ? '启用' : '禁用' }}
                        </el-tag>
                      </template>
                    </el-table-column>

                    <el-table-column label="操作" width="200" align="center" fixed="right">
                      <template #default="scope">
                        <el-button size="small" @click="handleEdit(scope.row)" type="primary" plain circle title="编辑实例">
                          <el-icon>
                            <Edit />
                          </el-icon>
                        </el-button>

                        <el-button size="small" @click="openRateLimitConfig(scope.row)" type="warning" plain circle title="限流器配置">
                          <el-icon>
                            <Timer />
                          </el-icon>
                        </el-button>

                        <el-button size="small" @click="openCircuitBreakerConfig(scope.row)" type="danger" plain circle title="熔断器配置">
                          <el-icon>
                            <WarningFilled />
                          </el-icon>
                        </el-button>

                        <el-button size="small" type="info" @click="handleDelete(scope.row)" plain circle title="删除">
                          <el-icon>
                            <Delete />
                          </el-icon>
                        </el-button>
                      </template>
                    </el-table-column>
                  </el-table>

                  <div v-if="filtered.length === 0" class="empty-wrap">
                    <el-empty description="暂无实例"></el-empty>
                  </div>

                  <div class="table-footer" v-if="filtered.length > 0">
                    <div class="footer-info">
                      共 {{ filtered.length }} 条（第 {{ currentPage }} / {{ totalPages }} 页）
                    </div>
                    <div class="footer-actions">
                      <el-pagination v-model:current-page="currentPage" :page-size="pageSize" :total="filtered.length"
                        layout="prev, pager, next, sizes, jumper" :page-sizes="[5, 10, 20, 50]"
                        @size-change="handleSizeChange" @current-change="handlePageChange" />
                    </div>
                  </div>
                </template>
              </el-skeleton>
            </div>
          </el-tab-pane>
        </el-tabs>
      </div>
    </el-card>

    <!-- 限流器配置弹窗 -->
    <RateLimitConfig
      v-model="rateLimitDialogVisible"
      :title="rateLimitDialogTitle"
      :initial-data="rateLimitFormData"
      @save="handleRateLimitSave"
    />

    <!-- 熔断器配置弹窗 -->
    <CircuitBreakerConfig
      v-model="circuitBreakerDialogVisible"
      :title="circuitBreakerDialogTitle"
      :initial-data="circuitBreakerFormData"
      @save="handleCircuitBreakerSave"
    />

    <!-- 添加/编辑实例对话框 -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="820px" :before-close="handleDialogClose"
      class="instance-dialog">
      <el-form :model="form" label-width="120px" ref="formRef">
        <el-divider content-position="left">基本信息</el-divider>

        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="服务类型" prop="serviceType">
              <el-select v-model="form.serviceType" placeholder="请选择服务类型" :disabled="isEdit">
                <el-option v-for="t in serviceTypes" :key="t" :label="serviceTypeMap[t] || t" :value="t" />
              </el-select>
            </el-form-item>
          </el-col>

          <el-col :span="12">
            <el-form-item label="实例名称" prop="name">
              <el-input v-model="form.name" placeholder="请输入实例名称" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="基础URL" prop="baseUrl">
              <el-input v-model="form.baseUrl" placeholder="请输入基础URL" />
            </el-form-item>
          </el-col>

          <el-col :span="12">
            <el-form-item label="路径" prop="path">
              <el-input v-model="form.path" placeholder="请输入路径（可选）" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="权重" prop="weight">
              <el-input-number v-model="form.weight" :min="1" :max="100" />
            </el-form-item>
          </el-col>

          <el-col :span="12">
            <el-form-item label="状态" prop="status">
              <el-switch v-model="form.status" active-value="active" inactive-value="inactive" active-text="启用"
                inactive-text="禁用" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="适配器" prop="adapter">
              <el-select v-model="form.adapter" placeholder="请选择适配器（留空使用全局配置）" clearable>
                <el-option v-for="adapter in adapters" :key="adapter.name || adapter" :label="adapter.name || adapter"
                  :value="adapter.name || adapter" />
              </el-select>
              <div v-if="!form.adapter && globalAdapter" class="form-note">
                当前将使用全局适配器: {{ globalAdapter }}
              </div>
            </el-form-item>
          </el-col>
        </el-row>

        <el-divider content-position="left">请求头配置</el-divider>

        <div class="headers-section">
          <el-row :gutter="20">
            <el-col :span="24">
              <el-form-item label="请求头管理">
                <div class="headers-actions">
                  <el-button type="primary" size="small" @click="addCustomHeader">
                    <el-icon>
                      <Plus />
                    </el-icon>
                    添加请求头
                  </el-button>
                  <el-button type="success" size="small" @click="addAuthorizationHeader">
                    <el-icon>
                      <Key />
                    </el-icon>
                    添加Authorization
                  </el-button>
                  <el-button type="warning" size="small" @click="clearAllHeaders" v-if="customHeadersList.length > 0">
                    <el-icon>
                      <Delete />
                    </el-icon>
                    清除所有
                  </el-button>
                </div>
              </el-form-item>
            </el-col>
          </el-row>

          <div v-if="customHeadersList.length > 0" class="headers-list">
            <el-row v-for="(header, index) in customHeadersList" :key="index" :gutter="20" class="header-row">
              <el-col :span="10">
                <el-form-item :label="`请求头名称 ${index + 1}`">
                  <el-input v-model="header.key" placeholder="如：Authorization" @input="onCustomHeaderChange" />
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item :label="`请求头值 ${index + 1}`">
                  <el-input v-model="header.value" placeholder="如：Bearer your-token-here" @input="onCustomHeaderChange"
                    :type="header.key.toLowerCase() === 'authorization' ? 'password' : 'text'"
                    :show-password="header.key.toLowerCase() === 'authorization'" />
                </el-form-item>
              </el-col>
              <el-col :span="2">
                <div class="header-delete-wrapper">
                  <el-button type="danger" size="small" @click="removeCustomHeader(index)" circle
                    class="remove-header-btn" title="删除此请求头">
                    <el-icon>
                      <Close />
                    </el-icon>
                  </el-button>
                </div>
              </el-col>
            </el-row>
          </div>

          <el-row v-else :gutter="20">
            <el-col :span="24">
              <el-form-item>
                <el-empty description="暂无自定义请求头，点击上方按钮添加" :image-size="60" class="empty-headers" />
              </el-form-item>
            </el-col>
          </el-row>
        </div>
      </el-form>

      <template #footer>
        <span class="dialog-footer">
          <el-button @click="handleDialogClose">取消</el-button>
          <el-button type="primary" @click="handleSave" :loading="saveLoading">保存</el-button>
        </span>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import type { FormInstance } from 'element-plus'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  addServiceInstance,
  deleteServiceInstance,
  getServiceInstances,
  updateServiceInstance,
  getRateLimitConfig,
  saveRateLimitConfig,
  getCircuitBreakerConfig,
  saveCircuitBreakerConfig,
  type InstanceConfig,
  type RateLimitConfig as RateLimitConfigType,
  type CircuitBreakerConfig as CircuitBreakerConfigType
} from '@/api/instance'
import { getServiceTypes } from '@/api/dashboard'
import { getAdapters, getAllConfigurations } from '@/api/service'
import { clearCache } from '@/stores/playgroundCache'
import { Plus, Delete, Close, Key, Timer, WarningFilled, Edit } from '@element-plus/icons-vue'
import RateLimitConfig from '@/components/RateLimitConfig.vue'
import CircuitBreakerConfig from '@/components/CircuitBreakerConfig.vue'

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

// 指定显示顺序（会按此顺序排列卡片/标签）
const serviceOrder = ['chat', 'embedding', 'rerank', 'tts', 'stt', 'imgGen', 'imgEdit']

// 服务类型（按指定顺序显示）
const serviceTypes = ref<string[]>([])

// 当前激活的服务类型
const activeServiceType = ref('chat')

// 适配器相关数据
const adapters = ref<any[]>([])
const globalConfig = ref<any>({})
const globalAdapter = ref<string>('')

// 定义实例类型
interface ServiceInstance {
  id: string | number  // 修改为联合类型以支持字符串ID
  serviceType: string
  name: string
  baseUrl: string
  path: string
  weight: number
  status: 'active' | 'inactive'
  instanceId: string
  adapter?: string  // 适配器配置
  headers?: Record<string, string>  // 请求头配置
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

// 实例数据缓存和当前数据
const instancesCache = ref<Record<string, ServiceInstance[]>>({})
const instances = ref<Record<string, ServiceInstance[]>>({})

// 加载与状态
const loading = ref(false)
const saveLoading = ref(false)
const dialogVisible = ref(false)
const dialogTitle = ref('')
const isEdit = ref(false)
const formRef = ref<FormInstance>()

// 搜索/过滤/分页
const searchQuery = ref('')
const statusFilter = ref('')
const pageSize = ref(10)
const currentPage = ref(1)

// 表单数据
const form = reactive<ServiceInstance>({
  id: 0,
  serviceType: 'chat',
  name: '',
  baseUrl: '',
  path: '',
  weight: 1,
  instanceId: '',
  status: 'active',
  adapter: '',
  headers: {},
  rateLimit: {
    enabled: false,
    algorithm: 'token-bucket',
    capacity: 100,
    rate: 10,
    scope: 'instance',
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

// 请求头管理
interface HeaderItem {
  key: string
  value: string
}

const customHeadersList = ref<HeaderItem[]>([])

// 限流器配置弹窗相关
const rateLimitDialogVisible = ref(false)
const rateLimitDialogTitle = ref('')
const rateLimitFormData = ref<any>({ enabled: false })

// 熔断器配置弹窗相关
const circuitBreakerDialogVisible = ref(false)
const circuitBreakerDialogTitle = ref('')
const circuitBreakerFormData = ref<any>({ enabled: false })

// 同步请求头列表
const syncCustomHeadersList = () => {
  customHeadersList.value = Object.entries(form.headers || {}).map(([key, value]) => ({
    key,
    value
  }))
}

// 请求头变化处理
const onCustomHeaderChange = () => {
  const headers: Record<string, string> = {}
  customHeadersList.value.forEach(header => {
    if (header.key.trim() && header.value.trim()) {
      headers[header.key.trim()] = header.value.trim()
    }
  })
  form.headers = headers
  console.log('请求头变化 - customHeadersList:', customHeadersList.value)
  console.log('请求头变化 - 生成的headers:', headers)
  console.log('请求头变化 - form.headers:', form.headers)
}

// 添加请求头
const addCustomHeader = () => {
  customHeadersList.value.push({ key: '', value: '' })
}

// 移除请求头
const removeCustomHeader = (index: number) => {
  customHeadersList.value.splice(index, 1)
  onCustomHeaderChange()
}

// 清除所有请求头
const clearAllHeaders = () => {
  customHeadersList.value = []
  form.headers = {}
}

// 添加常用请求头模板
const addAuthorizationHeader = () => {
  const existingAuth = customHeadersList.value.find(h => h.key.toLowerCase() === 'authorization')
  if (!existingAuth) {
    customHeadersList.value.push({ key: 'Authorization', value: 'Bearer your-api-key-here' })
    onCustomHeaderChange()
  }
}

// 简单防抖
function debounce<T extends (...args: any[]) => void>(fn: T, wait = 300) {
  let timeout: ReturnType<typeof setTimeout> | null = null
  return (...args: Parameters<T>) => {
    if (timeout) clearTimeout(timeout)
    timeout = setTimeout(() => fn(...args), wait)
  }
}
const applySearch = debounce(() => { currentPage.value = 1 }, 350)
const handleSearchClear = () => { currentPage.value = 1 }

// 计算当前选中类型是否有数据
const hasInstances = computed(() => {
  const list = instances.value[activeServiceType.value]
  return Array.isArray(list) && list.length > 0
})

// 计算过滤后的数组（按搜索 & 状态）
const filtered = computed(() => {
  const list = instances.value[activeServiceType.value] || []
  const q = (searchQuery.value || '').trim().toLowerCase()
  return list.filter(item => {
    if (statusFilter.value && item.status !== statusFilter.value) return false
    if (!q) return true
    return (
      (item.name && item.name.toLowerCase().includes(q)) ||
      (item.baseUrl && item.baseUrl.toLowerCase().includes(q)) ||
      (item.path && item.path.toLowerCase().includes(q))
    )
  })
})

// 分页计算
const totalPages = computed(() => Math.max(1, Math.ceil(filtered.value.length / pageSize.value)))
const paginated = computed(() => {
  const start = (currentPage.value - 1) * pageSize.value
  return filtered.value.slice(start, start + pageSize.value)
})

// 监控页数、过滤变化，修正页码
watch([filtered, pageSize], () => {
  if (currentPage.value > totalPages.value) currentPage.value = totalPages.value
})

// 监听服务类型变化，使用缓存或请求
watch(activeServiceType, (newServiceType) => {
  console.log('服务类型变化，新服务类型:', newServiceType);
  currentPage.value = 1
  searchQuery.value = ''
  statusFilter.value = ''
  if (newServiceType && !instancesCache.value[newServiceType]) {
    fetchServiceInstances(newServiceType)
  } else if (newServiceType && instancesCache.value[newServiceType]) {
    instances.value[newServiceType] = [...instancesCache.value[newServiceType]]
  }
})

// 获取适配器列表
const fetchAdapters = async () => {
  try {
    const response = await getAdapters()
    if (response.data?.success) {
      adapters.value = response.data.data || []
      console.log('获取到的适配器列表:', adapters.value)
    }
  } catch (error) {
    console.error('获取适配器列表失败:', error)
  }
}

// 获取全局配置
const fetchGlobalConfig = async () => {
  try {
    const response = await getAllConfigurations()
    if (response.data?.success) {
      globalConfig.value = response.data.data || {}
      // 从全局配置中提取默认适配器
      globalAdapter.value = globalConfig.value.adapter || ''
      console.log('获取到的全局配置:', globalConfig.value)
      console.log('全局默认适配器:', globalAdapter.value)
    }
  } catch (error) {
    console.error('获取全局配置失败:', error)
  }
}

// 获取服务类型并按 serviceOrder 排序显示
const fetchServiceTypes = async () => {
  try {
    const response = await getServiceTypes()
    if (response.data?.success) {
      const types: string[] = response.data.data || []
      console.log('获取到的服务类型:', types);

      // 保持 serviceOrder 中的顺序，其他未在 serviceOrder 中的类型追加在后
      const ordered = serviceOrder.filter(t => types.includes(t)).concat(types.filter(t => !serviceOrder.includes(t)))
      serviceTypes.value = ordered

      if (serviceTypes.value.length > 0) {
        activeServiceType.value = serviceTypes.value[0]
        fetchServiceInstances(serviceTypes.value[0])
      }
    }
  } catch (error) {
    console.error('获取服务类型失败:', error)
    ElMessage.error('获取服务类型失败')
  }
}

// 获取实例（带缓存与简单防抖）
let fetchTimer: ReturnType<typeof setTimeout> | null = null
const fetchServiceInstances = (serviceType: string) => {
  console.log('获取实例列表，使用服务类型:', serviceType);
  console.log(`发送获取请求到: /api/config/instance/${serviceType}`)
  if (fetchTimer) clearTimeout(fetchTimer)
  fetchTimer = setTimeout(async () => {
    loading.value = true
    try {
      if (instancesCache.value[serviceType]) {
        instances.value[serviceType] = [...instancesCache.value[serviceType]]
        loading.value = false
        return
      }
      const response = await getServiceInstances(serviceType)
      if (response.data?.success) {
        const data: any[] = response.data.data || []
        // 确保每个实例都包含正确的服务类型和ID
        const typedData: ServiceInstance[] = data.map((item) => ({
          ...item,
          id: item.instanceId || Date.now() + Math.random(), // 使用后端返回的instanceId作为唯一标识符，如果没有则使用随机数
          serviceType: serviceType,
          adapter: item.adapter || '', // 确保适配器字段存在
          headers: item.headers || {} // 确保headers字段存在
        }))
        console.log('获取实例数据 - 原始数据:', data)
        console.log('获取实例数据 - 处理后数据:', typedData)
        instancesCache.value[serviceType] = typedData
        instances.value[serviceType] = [...typedData]
      } else {
        instances.value[serviceType] = []
      }
    } catch (error) {
      console.error(`获取${serviceType}服务实例失败:`, error)
      instances.value[serviceType] = []
      ElMessage.error(`获取${serviceType}服务实例失败`)
    } finally {
      loading.value = false
    }
  }, 200)
}

// 刷新当前 tab
const refreshCurrent = () => {
  const t = activeServiceType.value
  console.log('刷新当前选项卡，服务类型:', t);
  delete instancesCache.value[t]
  fetchServiceInstances(t)
  // 同时刷新适配器列表和全局配置
  fetchAdapters()
  fetchGlobalConfig()
  ElMessage.success('刷新中...')
}

// 添加实例
const handleAddInstance = () => {
  dialogTitle.value = '添加实例'
  isEdit.value = false
  Object.assign(form, {
    id: 0,
    serviceType: activeServiceType.value, // 确保使用当前选项卡的服务类型
    name: '',
    baseUrl: '',
    path: '',
    weight: 1,
    status: 'active',
    adapter: '',
    headers: {},
    rateLimit: {
      enabled: false,
      algorithm: 'token-bucket',
      capacity: 100,
      rate: 10,
      scope: 'instance',
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
  customHeadersList.value = []
  console.log('添加实例，使用服务类型:', activeServiceType.value)
  dialogVisible.value = true
}

// 编辑实例
const handleEdit = (row: ServiceInstance) => {
  dialogTitle.value = '编辑实例'
  isEdit.value = true
  // 使用数据库ID（row.id）作为instanceId
  const dbId = row.id
  Object.assign(form, {
    ...row,
    // 确保使用当前激活的页签对应的服务类型，而不是实例原来的服务类型
    serviceType: activeServiceType.value,
    // 确保headers字段存在
    headers: row.headers || {},
    // 确保 rateLimit 和 circuitBreaker 不为 null
    rateLimit: row.rateLimit || {
      enabled: false,
      algorithm: 'token-bucket',
      capacity: 100,
      rate: 10,
      scope: 'instance',
      key: '',
      clientIpEnable: false
    },
    circuitBreaker: row.circuitBreaker || {
      enabled: false,
      failureThreshold: 5,
      timeout: 60000,
      successThreshold: 2
    },
    // 使用数据库ID
    instanceId: String(dbId)
  })
  syncCustomHeadersList()
  console.log('编辑实例，使用数据库ID:', dbId)
  console.log('编辑实例，使用服务类型:', form.serviceType)
  dialogVisible.value = true
}

// 删除实例
const handleDelete = (row: ServiceInstance) => {
  ElMessageBox.confirm('确定要删除该实例吗？', '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(async () => {
    try {
      // 确保使用当前激活的页签对应的服务类型
      const serviceType = activeServiceType.value;
      // 使用数据库ID
      const dbId = row.id
      console.log('删除实例，使用数据库ID:', dbId)
      const response = await deleteServiceInstance(serviceType, String(dbId))
      if (response.data?.success) {
        instances.value[serviceType] = (instances.value[serviceType] || []).filter(i => i.id !== dbId)
        instancesCache.value[serviceType] = [...(instances.value[serviceType] || [])]
        // 同时清除试验场的缓存，让试验场下拉列表刷新
        clearCache(serviceType)
        ElMessage.success('删除成功')
      } else {
        ElMessage.error(response.data?.message || '删除失败')
      }
    } catch (error) {
      console.error('删除实例失败:', error)
      ElMessage.error('删除实例失败')
    }
  }).catch(() => {
    ElMessage.info('已取消删除')
  })
}

// 关闭对话框
const handleDialogClose = () => {
  dialogVisible.value = false
  if (formRef.value) formRef.value.resetFields()
}

// 保存实例（新增/编辑）- 仅保存基础属性，限流器和熔断器通过独立接口配置
const handleSave = async () => {
  if (!formRef.value) return
  saveLoading.value = true
  try {
    // 确保使用当前选项卡的服务类型
    const serviceType = activeServiceType.value;
    console.log('保存实例，使用服务类型:', serviceType);

    // 构造实例数据（仅基础属性）
    const instanceData: InstanceConfig = {
      name: form.name,
      baseUrl: form.baseUrl,
      path: form.path || '',
      weight: form.weight,
      status: form.status,
      adapter: form.adapter || undefined,
      headers: form.headers || {}
    }

    console.log('构造的实例数据:', instanceData)

    if (isEdit.value) {
      // 使用后端返回的实例ID
      const response = await updateServiceInstance(serviceType, form.instanceId, instanceData)
      if (response.data?.success) {
        // 编辑成功后，清除缓存并重新获取实例列表以确保数据同步
        delete instancesCache.value[serviceType]
        // 同时清除试验场的缓存，让试验场下拉列表刷新
        clearCache(serviceType)
        fetchServiceInstances(serviceType)
        ElMessage.success('编辑成功')
      } else {
        ElMessage.error(response.data?.message || '编辑失败')
        saveLoading.value = false
        return
      }
    } else {
      // 添加新实例
      const response = await addServiceInstance(serviceType, instanceData)
      if (response.data?.success) {
        // 添加成功后，清除缓存并重新获取实例列表以确保数据同步
        delete instancesCache.value[serviceType]
        // 同时清除试验场的缓存，让试验场下拉列表刷新
        clearCache(serviceType)
        fetchServiceInstances(serviceType)
        ElMessage.success('添加成功')
      } else {
        ElMessage.error(response.data?.message || '添加失败')
        saveLoading.value = false
        return
      }
    }

    dialogVisible.value = false
  } catch (error) {
    console.error('保存实例失败:', error)
    ElMessage.error('保存实例失败')
  } finally {
    saveLoading.value = false
  }
}

// 分页事件
const handleSizeChange = (size: number) => { pageSize.value = size; currentPage.value = 1 }
const handlePageChange = (page: number) => { currentPage.value = page }

// 当前正在编辑的实例（使用数据库ID）
const currentEditInstanceId = ref<string>('')
const currentEditServiceType = ref<string>('')

// 打开限流器配置弹窗
const openRateLimitConfig = (row: any) => {
  // 使用数据库ID
  currentEditInstanceId.value = String(row.id)
  currentEditServiceType.value = activeServiceType.value
  rateLimitDialogTitle.value = `限流器配置 - ${row.name}`
  // 从 row.rateLimit 中提取配置，转换为组件期望的嵌套格式
  const rateLimitConfig = row.rateLimit || { enabled: false }
  rateLimitFormData.value = {
    rateLimit: {
      enabled: rateLimitConfig.enabled || false,
      algorithm: rateLimitConfig.algorithm || 'token-bucket',
      capacity: rateLimitConfig.capacity || 100,
      rate: rateLimitConfig.rate || 10,
      scope: rateLimitConfig.scope || 'instance',
      key: rateLimitConfig.key || '',
      clientIpEnable: rateLimitConfig.clientIpEnable || false
    }
  }
  rateLimitDialogVisible.value = true
}

// 打开熔断器配置弹窗
const openCircuitBreakerConfig = (row: any) => {
  // 使用数据库ID
  currentEditInstanceId.value = String(row.id)
  currentEditServiceType.value = activeServiceType.value
  circuitBreakerDialogTitle.value = `熔断器配置 - ${row.name}`
  // 从 row.circuitBreaker 中提取配置，转换为组件期望的嵌套格式
  const circuitBreakerConfig = row.circuitBreaker || { enabled: false }
  circuitBreakerFormData.value = {
    circuitBreaker: {
      enabled: circuitBreakerConfig.enabled || false,
      failureThreshold: circuitBreakerConfig.failureThreshold || 5,
      timeout: circuitBreakerConfig.timeout || 60000,
      successThreshold: circuitBreakerConfig.successThreshold || 2
    }
  }
  circuitBreakerDialogVisible.value = true
}

// 保存限流器配置
const handleRateLimitSave = async (configData: any) => {
  if (!currentEditInstanceId.value) return

  saveLoading.value = true
  try {
    const serviceType = currentEditServiceType.value
    const instanceId = currentEditInstanceId.value

    // 从嵌套格式中提取限流器配置
    const rateLimitConfig = configData.rateLimit || configData

    // 构造请求数据
    const config: RateLimitConfigType = {
      enabled: rateLimitConfig.enabled || false,
      algorithm: rateLimitConfig.algorithm || 'token-bucket',
      capacity: rateLimitConfig.capacity || 100,
      rate: rateLimitConfig.rate || 10,
      scope: rateLimitConfig.scope || 'instance',
      key: rateLimitConfig.key || '',
      clientIpEnable: rateLimitConfig.clientIpEnable || false
    }

    const response = await saveRateLimitConfig(serviceType, instanceId, config)

    if (response.data?.success) {
      ElMessage.success('限流器配置保存成功')
      // 刷新缓存
      delete instancesCache.value[serviceType]
      fetchServiceInstances(serviceType)
    } else {
      ElMessage.error(response.data?.message || '保存失败')
    }
  } catch (error) {
    console.error('保存限流器配置失败:', error)
    ElMessage.error('保存限流器配置失败')
  } finally {
    saveLoading.value = false
  }
}

// 保存熔断器配置
const handleCircuitBreakerSave = async (configData: any) => {
  if (!currentEditInstanceId.value) return

  saveLoading.value = true
  try {
    const serviceType = currentEditServiceType.value
    const instanceId = currentEditInstanceId.value

    // 从嵌套格式中提取熔断器配置
    const circuitBreakerConfig = configData.circuitBreaker || configData

    // 构造请求数据
    const config: CircuitBreakerConfigType = {
      enabled: circuitBreakerConfig.enabled || false,
      failureThreshold: circuitBreakerConfig.failureThreshold || 5,
      timeout: circuitBreakerConfig.timeout || 60000,
      successThreshold: circuitBreakerConfig.successThreshold || 2
    }

    const response = await saveCircuitBreakerConfig(serviceType, instanceId, config)

    if (response.data?.success) {
      ElMessage.success('熔断器配置保存成功')
      // 刷新缓存
      delete instancesCache.value[serviceType]
      fetchServiceInstances(serviceType)
    } else {
      ElMessage.error(response.data?.message || '保存失败')
    }
  } catch (error) {
    console.error('保存熔断器配置失败:', error)
    ElMessage.error('保存熔断器配置失败')
  } finally {
    saveLoading.value = false
  }
}

// 初始化
onMounted(() => {
  fetchServiceTypes()
  fetchAdapters()
  fetchGlobalConfig()
})
</script>

<style scoped>
.instance-management {
  padding: 20px;
  background: linear-gradient(180deg, #f7f9fc 0%, #ffffff 100%);
  min-height: calc(100vh - 80px);
  box-sizing: border-box;
}

/* 卡片 */
.instance-card {
  border-radius: 12px;
  box-shadow: 0 8px 24px rgba(15, 23, 42, 0.06);
  padding: 0;
}

/* header */
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
  font-size: 18px;
  font-weight: 700;
  color: #1f2d3d;
}

.header-title .el-icon {
  margin-right: 8px;
  color: #409eff;
  font-size: 20px;
}

.header-tools {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-left: 10px;
  flex-wrap: wrap;
}

.search-input {
  width: 360px;
  min-width: 200px;
}

.filter-select {
  width: 140px;
  min-width: 120px;
}

.refresh-button {
  color: #909399;
}

/* tabs & table area */
.tabs-wrap {
  padding: 12px 20px 20px 20px;
}

.service-tabs ::v-deep(.el-tabs__content) {
  padding: 0;
}

.table-area {
  background: #fff;
  border-radius: 8px;
  padding: 12px;
  box-shadow: 0 1px 0 rgba(0, 0, 0, 0.02);
}

/* table */
.instance-table {
  width: 100%;
  border-radius: 6px;
  overflow: hidden;
}

.instance-table :deep(.el-table__cell) {
  font-size: 14px;
  padding: 12px;
}

.instance-table :deep(.el-table__header th) {
  font-size: 13px;
  font-weight: 600;
  color: #2b3a4b;
  background-color: #fbfdff;
}

.table-tag {
  font-size: 13px;
  padding: 6px 10px;
}

.ellipsis {
  max-width: 420px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

/* empty and footer */
.empty-wrap {
  padding: 30px 0;
  display: flex;
  justify-content: center;
  align-items: center;
}

.table-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 14px;
  padding-top: 12px;
  border-top: 1px solid #eef2f6;
}

.footer-info {
  color: #6b7785;
  font-size: 13px;
}

/* dialog */
.instance-dialog :deep(.el-dialog__header) {
  background-color: #fbfdff;
  border-bottom: 1px solid #eef2f6;
  padding: 14px 20px;
}

.instance-dialog :deep(.el-dialog__title) {
  font-weight: 700;
  color: #1f2d3d;
  font-size: 18px;
}

.instance-dialog :deep(.el-dialog__body) {
  padding: 20px;
}

.instance-dialog :deep(.el-form-item__label) {
  font-weight: 600;
  font-size: 14px;
  color: #213142;
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  padding: 10px 20px;
}

/* 适配器相关样式 */
.adapter-note {
  font-size: 11px;
  color: #909399;
  margin-top: 2px;
}

.form-note {
  font-size: 12px;
  color: #909399;
  margin-top: 4px;
  line-height: 1.4;
}

/* 请求头配置样式 - 与基本信息保持一致 */
.headers-section {
  margin: 0;
  padding: 0;
}

.headers-actions {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
  align-items: center;
}

.headers-list {
  margin-top: 16px;
  padding: 16px;
  background-color: #fafbfc;
  border: 1px solid #e4e7ed;
  border-radius: 6px;
}

.header-row {
  margin-bottom: 16px;
  padding-top: 16px;
  background-color: #ffffff;
  border: 1px solid #ebeef5;
  border-radius: 6px;
  transition: all 0.3s ease;
}

.header-row:hover {
  border-color: #409eff;
  box-shadow: 0 2px 8px rgba(64, 158, 255, 0.1);
}

.header-row:last-child {
  margin-bottom: 0;
}

.empty-headers {
  margin: 0;
  background-color: transparent;
  border: none;
  padding: 20px 0;
}

/* 操作按钮样式 */
.header-delete-wrapper {
  display: flex;
  align-items: center;
  justify-content: center;
}

.remove-header-btn {
  transition: all 0.3s ease;
}

.remove-header-btn:hover {
  transform: scale(1.1);
}

/* 响应式设计 */
@media (max-width: 768px) {
  .headers-actions {
    justify-content: center;
  }

  .header-row .el-col {
    margin-bottom: 8px;
  }

  .header-row .el-col:last-child {
    margin-bottom: 0;
  }

  .header-delete-wrapper {
    margin-top: 10px;
    height: auto;
  }
}

/* 滚动条样式 */
.headers-list::-webkit-scrollbar {
  width: 6px;
}

.headers-list::-webkit-scrollbar-track {
  background: var(--el-bg-color-page);
  border-radius: 3px;
}

.headers-list::-webkit-scrollbar-thumb {
  background: var(--el-border-color);
  border-radius: 3px;
}

.headers-list::-webkit-scrollbar-thumb:hover {
  background: var(--el-border-color-dark);
}

/* 表单项标签样式统一 */
.headers-section .el-form-item__label {
  font-weight: 600;
  font-size: 14px;
  color: #213142;
}

/* 请求头启用开关样式 */
.headers-section .el-switch {
  margin-left: 0;
}
</style>