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
                    <el-table-column prop="status" label="状态" width="110" align="center">
                      <template #default="scope">
                        <el-tag :type="scope.row.status === 'active' ? 'success' : 'info'" class="table-tag">
                          {{ scope.row.status === 'active' ? '启用' : '禁用' }}
                        </el-tag>
                      </template>
                    </el-table-column>

                    <el-table-column label="操作" width="170" align="center" fixed="right">
                      <template #default="scope">
                        <el-button size="small" @click="handleEdit(scope.row)" type="primary" plain circle title="编辑">
                          <el-icon>
                            <Edit />
                          </el-icon>
                        </el-button>

                        <el-button size="small" type="danger" @click="handleDelete(scope.row)" plain circle title="删除">
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

        <el-divider content-position="left">限流配置</el-divider>
        <el-form-item label="启用限流">
          <el-switch v-model="form.rateLimit.enabled" active-text="启用" inactive-text="禁用" />
        </el-form-item>

        <div v-if="form.rateLimit.enabled">
          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item label="算法">
                <el-select v-model="form.rateLimit.algorithm" placeholder="请选择算法">
                  <el-option label="令牌桶" value="token-bucket" />
                  <el-option label="漏桶" value="leaky-bucket" />
                  <el-option label="滑动窗口" value="sliding-window" />
                </el-select>
              </el-form-item>
            </el-col>

            <el-col :span="12">
              <el-form-item label="作用域">
                <el-select v-model="form.rateLimit.scope" placeholder="请选择作用域">
                  <el-option label="实例级别" value="instance" />
                  <el-option label="客户端IP级别" value="client-ip" />
                </el-select>
              </el-form-item>
            </el-col>
          </el-row>

          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item label="容量">
                <el-input-number v-model="form.rateLimit.capacity" :min="1" :max="10000" />
              </el-form-item>
            </el-col>

            <el-col :span="12">
              <el-form-item label="速率">
                <el-input-number v-model="form.rateLimit.rate" :min="1" :max="10000" />
              </el-form-item>
            </el-col>
          </el-row>

          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item label="限流键值">
                <el-input v-model="form.rateLimit.key" placeholder="请输入限流键值（可选）" />
              </el-form-item>
            </el-col>

            <el-col :span="12">
              <el-form-item label="客户端IP限流">
                <el-switch v-model="form.rateLimit.clientIpEnable" active-text="启用" inactive-text="禁用" />
              </el-form-item>
            </el-col>
          </el-row>
        </div>

        <el-divider content-position="left">熔断器配置</el-divider>
        <el-form-item label="启用熔断器">
          <el-switch v-model="form.circuitBreaker.enabled" active-text="启用" inactive-text="禁用" />
        </el-form-item>

        <div v-if="form.circuitBreaker.enabled">
          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item label="失败阈值">
                <el-input-number v-model="form.circuitBreaker.failureThreshold" :min="1" :max="100" />
              </el-form-item>
            </el-col>

            <el-col :span="12">
              <el-form-item label="超时时间(毫秒)">
                <el-input-number v-model="form.circuitBreaker.timeout" :min="1000" :max="300000" />
              </el-form-item>
            </el-col>
          </el-row>

          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item label="成功阈值">
                <el-input-number v-model="form.circuitBreaker.successThreshold" :min="1" :max="100" />
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
  getServiceTypes,
  updateServiceInstance
} from '@/api/dashboard'
import { getAdapters, getAllConfigurations } from '@/api/service'

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
  console.log(`发送获取请求到: /api/config/instance/type/${serviceType}`)
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
          adapter: item.adapter || '' // 确保适配器字段存在
        }))
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
  console.log('添加实例，使用服务类型:', activeServiceType.value)
  dialogVisible.value = true
}

// 编辑实例
const handleEdit = (row: ServiceInstance) => {
  dialogTitle.value = '编辑实例'
  isEdit.value = true
  Object.assign(form, {
    ...row,
    // 确保使用当前激活的页签对应的服务类型，而不是实例原来的服务类型
    serviceType: activeServiceType.value,
    // 使用后端返回的实例ID而不是根据表单数据重新构建
    rateLimit: {
      enabled: row.rateLimit?.enabled || false,
      algorithm: row.rateLimit?.algorithm || 'token-bucket',
      capacity: row.rateLimit?.capacity || 100,
      rate: row.rateLimit?.rate || 10,
      scope: row.rateLimit?.scope || 'instance',
      key: row.rateLimit?.key || '',
      clientIpEnable: row.rateLimit?.clientIpEnable || false
    },
    circuitBreaker: {
      enabled: row.circuitBreaker?.enabled || false,
      failureThreshold: row.circuitBreaker?.failureThreshold || 5,
      timeout: row.circuitBreaker?.timeout || 60000,
      successThreshold: row.circuitBreaker?.successThreshold || 2
    }
  })
  console.log('编辑实例，设置originalInstanceId:', form.instanceId)
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
      console.log('删除实例，使用服务类型:', serviceType);
      console.log(`发送删除请求到: /api/config/instance/del/${serviceType}?instanceId=${row.instanceId}&createNewVersion=true`)
      const response = await deleteServiceInstance(serviceType, row.instanceId, true)
      if (response.data?.success) {
        instances.value[serviceType] = (instances.value[serviceType] || []).filter(i => i.id !== row.id)
        instancesCache.value[serviceType] = [...(instances.value[serviceType] || [])]
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

// 保存实例（新增/编辑）
const handleSave = async () => {
  if (!formRef.value) return
  saveLoading.value = true
  try {
    // 确保使用当前选项卡的服务类型
    const serviceType = activeServiceType.value;
    console.log('保存实例，使用服务类型:', serviceType);

    // 构造实例数据，包含限流和熔断配置
    const instanceData = {
      name: form.name,
      baseUrl: form.baseUrl,
      path: form.path,
      weight: form.weight,
      status: form.status,
      adapter: form.adapter || null, // 适配器配置，空值时传null使用全局配置
      rateLimit: form.rateLimit.enabled ? form.rateLimit : null, // 只有启用时才传递限流配置
      circuitBreaker: form.circuitBreaker.enabled ? form.circuitBreaker : null // 只有启用时才传递熔断配置
    }

    if (isEdit.value) {
      // 使用后端返回的实例ID
      const updateData = {
        instanceId: form.instanceId,
        instance: instanceData
      }
      console.log('更新实例请求数据:', updateData)
      // 使用正确的服务类型发送请求
      console.log(`发送更新请求到: /api/config/instance/update/${serviceType}?createNewVersion=true`)
      const response = await updateServiceInstance(serviceType, updateData, true)
      if (response.data?.success) {
        // 编辑成功后，清除缓存并重新获取实例列表以确保数据同步
        delete instancesCache.value[serviceType]
        fetchServiceInstances(serviceType)
        ElMessage.success('编辑成功')
      } else {
        ElMessage.error(response.data?.message || '编辑失败')
        saveLoading.value = false
        return
      }
    } else {
      // 使用正确的服务类型发送请求
      console.log(`发送添加请求到: /api/config/instance/add/${serviceType}?createNewVersion=true`)
      const response = await addServiceInstance(serviceType, instanceData, true)
      if (response.data?.success) {
        // 添加成功后，清除缓存并重新获取实例列表以确保数据同步
        delete instancesCache.value[serviceType]
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
</style>