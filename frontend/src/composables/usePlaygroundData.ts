// Playground 数据管理 Composable
import { ref, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { getCachedInstances, getCachedModels } from '@/stores/playgroundCache'
import { getInstanceServiceType } from '@/api/models'

export interface ServiceInstance {
  instanceId: string
  name: string
  headers?: Record<string, string>
  status?: 'active' | 'inactive'
  [key: string]: any
}

export const usePlaygroundData = (playgroundServiceType: string) => {
  // 响应式状态
  const availableInstances = ref<ServiceInstance[]>([])
  const availableModels = ref<string[]>([])
  const instancesLoading = ref(false)
  const modelsLoading = ref(false)
  const selectedInstanceId = ref<string>('')

  // 计算属性
  const selectedInstanceInfo = computed(() => {
    return availableInstances.value.find(
      instance => instance.instanceId === selectedInstanceId.value
    )
  })

  const hasInstances = computed(() => availableInstances.value.length > 0)
  const hasModels = computed(() => availableModels.value.length > 0)

  // 获取实际的服务类型（用于API调用）
  const actualServiceType = getInstanceServiceType(playgroundServiceType)

  // 获取实例和模型数据
  const fetchData = async (forceRefresh = false, showMessage = true) => {
    // 只有在强制刷新或没有数据时才显示加载状态
    const shouldShowLoading = forceRefresh || availableInstances.value.length === 0 || availableModels.value.length === 0
    
    if (shouldShowLoading) {
      instancesLoading.value = true
      modelsLoading.value = true
    }

    try {
      // 并行获取实例和模型数据（模型数据会复用实例数据，不会重复请求）
      const [instances, models] = await Promise.all([
        getCachedInstances(actualServiceType, forceRefresh),
        getCachedModels(actualServiceType, forceRefresh)
      ])

      // 检查当前选择的实例是否还存在
      const currentInstanceExists = selectedInstanceId.value &&
        instances.some(inst => inst.instanceId === selectedInstanceId.value)

      // 更新数据
      availableInstances.value = instances
      availableModels.value = models
      
      console.log(`[${playgroundServiceType}] 数据已更新到界面:`, {
        instancesCount: availableInstances.value.length,
        modelsCount: availableModels.value.length,
        instancesLoading: instancesLoading.value,
        modelsLoading: modelsLoading.value
      })

      // 如果当前选择的实例不存在，给出提示但不清空选择
      if (selectedInstanceId.value && !currentInstanceExists) {
        ElMessage.warning(`当前选择的实例在刷新后不可用，请重新选择`)
      }

      if (showMessage) {
        ElMessage.success(
          `已刷新数据：${instances.length} 个实例，${models.length} 个模型`
        )
      }

      return { instances, models }
    } catch (error) {
      console.error(`获取${playgroundServiceType}数据失败:`, error)
      if (showMessage) {
        ElMessage.error(`获取${playgroundServiceType}数据失败`)
      }
      return { instances: [], models: [] }
    } finally {
      instancesLoading.value = false
      modelsLoading.value = false
    }
  }

  // 仅获取实例数据
  const fetchInstances = async (forceRefresh = false, showMessage = true) => {
    // 只有在强制刷新或没有缓存数据时才显示加载状态
    if (forceRefresh || availableInstances.value.length === 0) {
      instancesLoading.value = true
    }

    try {
      const instances = await getCachedInstances(actualServiceType, forceRefresh)
      
      // 检查当前选择的实例是否还存在
      const currentInstanceExists = selectedInstanceId.value &&
        instances.some(inst => inst.instanceId === selectedInstanceId.value)

      availableInstances.value = instances

      if (selectedInstanceId.value && !currentInstanceExists) {
        ElMessage.warning(`当前选择的实例在刷新后不可用，请重新选择`)
      }

      if (showMessage && forceRefresh) {
        ElMessage.success(`已刷新实例列表，找到 ${instances.length} 个可用实例`)
      }

      return instances
    } catch (error) {
      console.error(`获取${playgroundServiceType}实例失败:`, error)
      if (showMessage) {
        ElMessage.error(`获取${playgroundServiceType}实例失败`)
      }
      return []
    } finally {
      instancesLoading.value = false
    }
  }

  // 仅获取模型数据
  const fetchModels = async (forceRefresh = false, showMessage = true) => {
    // 只有在强制刷新或没有缓存数据时才显示加载状态
    if (forceRefresh || availableModels.value.length === 0) {
      modelsLoading.value = true
    }

    try {
      const models = await getCachedModels(actualServiceType, forceRefresh)
      availableModels.value = models

      if (showMessage && forceRefresh) {
        ElMessage.success(`已刷新模型列表，找到 ${models.length} 个可用模型`)
      }

      return models
    } catch (error) {
      console.error(`获取${playgroundServiceType}模型失败:`, error)
      if (showMessage) {
        ElMessage.error(`获取${playgroundServiceType}模型失败`)
      }
      return []
    } finally {
      modelsLoading.value = false
    }
  }

  // 实例选择变化处理
  const onInstanceChange = (instanceId: string) => {
    selectedInstanceId.value = instanceId
    
    // 可以在这里添加额外的逻辑，比如更新请求头等
    const instance = selectedInstanceInfo.value
    if (instance) {
      console.log(`已选择实例: ${instance.name}`)
    }
  }

  // 设置选中的实例ID（供外部调用）
  const setSelectedInstanceId = (instanceId: string) => {
    selectedInstanceId.value = instanceId
  }

  // 初始化数据（静默加载，不显示消息）
  const initializeData = async () => {
    console.log(`[${playgroundServiceType}] 开始初始化数据...`)
    
    // 确保在初始化时显示加载状态
    if (availableInstances.value.length === 0) {
      instancesLoading.value = true
    }
    if (availableModels.value.length === 0) {
      modelsLoading.value = true
    }
    
    try {
      const result = await fetchData(false, false)
      console.log(`[${playgroundServiceType}] 数据初始化完成:`, {
        instances: result.instances.length,
        models: result.models.length,
        availableInstances: availableInstances.value.length,
        availableModels: availableModels.value.length
      })
    } catch (error) {
      console.error(`[${playgroundServiceType}] 数据初始化失败:`, error)
    }
  }

  // 刷新数据（显示消息）
  const refreshData = async () => {
    await fetchData(true, true)
  }

  return {
    // 响应式状态
    availableInstances,
    availableModels,
    instancesLoading,
    modelsLoading,
    selectedInstanceId,
    
    // 计算属性
    selectedInstanceInfo,
    hasInstances,
    hasModels,
    actualServiceType,
    
    // 方法
    fetchData,
    fetchInstances,
    fetchModels,
    onInstanceChange,
    setSelectedInstanceId,
    initializeData,
    refreshData
  }
}