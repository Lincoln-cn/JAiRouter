// Playground 缓存管理
import { ref, reactive } from 'vue'
import { getServiceInstances } from '@/api/dashboard'
import { ElMessage } from 'element-plus'

// 缓存接口定义
interface CacheItem<T> {
  data: T
  timestamp: number
  loading: boolean
}

interface ServiceInstance {
  instanceId: string
  name: string
  headers?: Record<string, string>
  [key: string]: any
}

// 缓存过期时间（5分钟）
const CACHE_EXPIRE_TIME = 5 * 60 * 1000

// 全局缓存状态
const instancesCache = reactive<Record<string, CacheItem<ServiceInstance[]>>>({})
const modelsCache = reactive<Record<string, CacheItem<string[]>>>({})

// 请求去重控制
const pendingRequests = new Map<string, Promise<any>>()

// 检查缓存是否有效
const isCacheValid = <T>(cache: CacheItem<T>): boolean => {
  return Date.now() - cache.timestamp < CACHE_EXPIRE_TIME
}

// 创建空缓存项
const createEmptyCache = <T>(defaultData: T): CacheItem<T> => ({
  data: defaultData,
  timestamp: 0,
  loading: false
})

// 请求去重函数 - 确保同一时间只有一个相同的请求
const dedupeRequest = <T>(key: string, fn: () => Promise<T>): Promise<T> => {
  // 如果已经有相同的请求在进行中，直接返回该请求的 Promise
  if (pendingRequests.has(key)) {
    console.log(`[Cache] 复用进行中的请求: ${key}`)
    return pendingRequests.get(key)!
  }

  // 创建新的请求
  const promise = fn().finally(() => {
    // 请求完成后清除记录
    pendingRequests.delete(key)
  })

  // 记录请求
  pendingRequests.set(key, promise)
  return promise
}

// 获取实例列表（带缓存）
export const getCachedInstances = async (serviceType: string, forceRefresh = false): Promise<ServiceInstance[]> => {
  // 初始化缓存
  if (!instancesCache[serviceType]) {
    instancesCache[serviceType] = createEmptyCache<ServiceInstance[]>([])
  }

  const cache = instancesCache[serviceType]

  // 如果缓存有效且不强制刷新，返回缓存数据
  if (!forceRefresh && isCacheValid(cache) && cache.data.length > 0) {
    return cache.data
  }

  // 如果正在加载，等待加载完成
  if (cache.loading) {
    return new Promise((resolve) => {
      const checkLoading = () => {
        if (!cache.loading) {
          resolve(cache.data)
        } else {
          setTimeout(checkLoading, 100)
        }
      }
      checkLoading()
    })
  }

  // 使用请求去重机制避免重复请求
  return dedupeRequest(`instances-${serviceType}`, async () => {
    console.log(`[Cache] 开始获取 ${serviceType} 实例数据...`)
    cache.loading = true
    
    try {
      const response = await getServiceInstances(serviceType)
      if (response.data?.success) {
        const newInstances = response.data.data || []
        
        // 更新缓存
        cache.data = newInstances
        cache.timestamp = Date.now()
        
        console.log(`[Cache] ${serviceType} 实例数据获取成功:`, newInstances.length, '个实例')
        return newInstances
      } else {
        throw new Error('获取实例列表失败')
      }
    } catch (error) {
      console.error(`获取${serviceType}实例列表失败:`, error)
      
      // 如果有旧缓存数据，继续使用
      if (cache.data.length > 0) {
        ElMessage.warning(`刷新${serviceType}实例列表失败，使用缓存数据`)
        return cache.data
      }
      
      ElMessage.error(`获取${serviceType}实例列表失败`)
      return []
    } finally {
      cache.loading = false
    }
  })
}

// 获取模型列表（从实例缓存中提取，避免重复请求）
export const getCachedModels = async (serviceType: string, forceRefresh = false): Promise<string[]> => {
  // 初始化缓存
  if (!modelsCache[serviceType]) {
    modelsCache[serviceType] = createEmptyCache<string[]>([])
  }

  const cache = modelsCache[serviceType]

  // 如果缓存有效且不强制刷新，返回缓存数据
  if (!forceRefresh && isCacheValid(cache) && cache.data.length > 0) {
    return cache.data
  }

  // 如果正在加载，等待加载完成
  if (cache.loading) {
    return new Promise((resolve) => {
      const checkLoading = () => {
        if (!cache.loading) {
          resolve(cache.data)
        } else {
          setTimeout(checkLoading, 100)
        }
      }
      checkLoading()
    })
  }

  // 直接从实例缓存中提取模型列表，避免重复请求
  try {
    const instances = await getCachedInstances(serviceType, forceRefresh)
    
    // 从实例列表中提取启用状态的模型名称
    const models = instances
      .filter(instance => instance.status === 'active')
      .map(instance => instance.name)
    
    // 更新模型缓存
    cache.data = models
    cache.timestamp = Date.now()
    
    return models
  } catch (error) {
    console.error(`从实例缓存提取${serviceType}模型列表失败:`, error)
    
    // 如果有旧缓存数据，继续使用
    if (cache.data.length > 0) {
      ElMessage.warning(`刷新${serviceType}模型列表失败，使用缓存数据`)
      return cache.data
    }
    
    ElMessage.error(`获取${serviceType}模型列表失败`)
    return []
  }
}

// 获取缓存状态
export const getCacheStatus = () => {
  const instancesStatus = Object.entries(instancesCache).map(([serviceType, cache]) => ({
    serviceType,
    type: 'instances',
    hasData: cache.data.length > 0,
    isValid: isCacheValid(cache),
    loading: cache.loading,
    timestamp: cache.timestamp
  }))

  const modelsStatus = Object.entries(modelsCache).map(([serviceType, cache]) => ({
    serviceType,
    type: 'models',
    hasData: cache.data.length > 0,
    isValid: isCacheValid(cache),
    loading: cache.loading,
    timestamp: cache.timestamp
  }))

  return [...instancesStatus, ...modelsStatus]
}

// 清除指定服务类型的缓存
export const clearCache = (serviceType?: string) => {
  if (serviceType) {
    delete instancesCache[serviceType]
    delete modelsCache[serviceType]
  } else {
    // 清除所有缓存
    Object.keys(instancesCache).forEach(key => delete instancesCache[key])
    Object.keys(modelsCache).forEach(key => delete modelsCache[key])
  }
}

// 预加载常用服务类型的数据
export const preloadCommonData = async () => {
  const commonServiceTypes = ['chat', 'embedding', 'rerank', 'tts', 'stt', 'imgGen', 'imgEdit']
  
  // 并行预加载，但不等待结果，避免阻塞
  commonServiceTypes.forEach(serviceType => {
    getCachedInstances(serviceType).catch(() => {
      // 静默处理错误，不影响用户体验
    })
    getCachedModels(serviceType).catch(() => {
      // 静默处理错误，不影响用户体验
    })
  })
}

// 刷新所有缓存
export const refreshAllCache = async () => {
  const serviceTypes = [...Object.keys(instancesCache), ...Object.keys(modelsCache)]
  const uniqueServiceTypes = [...new Set(serviceTypes)]
  
  const promises = uniqueServiceTypes.map(async (serviceType) => {
    try {
      await Promise.all([
        getCachedInstances(serviceType, true),
        getCachedModels(serviceType, true)
      ])
    } catch (error) {
      console.error(`刷新${serviceType}缓存失败:`, error)
    }
  })
  
  await Promise.all(promises)
  ElMessage.success('缓存刷新完成')
}

// 导出响应式缓存状态供组件使用
export const usePlaygroundCache = () => {
  return {
    instancesCache: readonly(instancesCache),
    modelsCache: readonly(modelsCache),
    getCachedInstances,
    getCachedModels,
    getCacheStatus,
    clearCache,
    preloadCommonData,
    refreshAllCache
  }
}

// 只读包装函数
function readonly<T>(obj: T): Readonly<T> {
  return obj as Readonly<T>
}