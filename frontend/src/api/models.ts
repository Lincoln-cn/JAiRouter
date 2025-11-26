import request from '@/utils/request'
import type { RouterResponse } from '@/types'

// 模型实例接口
export interface ModelInstance {
  id: string | number
  serviceType: string
  name: string
  baseUrl: string
  path: string
  weight: number
  status: 'active' | 'inactive'
  instanceId: string
  adapter?: string
}

// 获取指定服务类型的模型列表（从实例管理中获取）
export const getModelsByServiceType = async (serviceType: string): Promise<string[]> => {
  try {
    const response = await request.get<RouterResponse<ModelInstance[]>>(`/config/instance/type/${serviceType}`)
    if (response.data?.success && response.data.data) {
      // 只返回启用状态的实例名称
      return response.data.data
        .filter(instance => instance.status === 'active')
        .map(instance => instance.name)
    }
    return []
  } catch (error) {
    console.error(`获取${serviceType}服务模型列表失败:`, error)
    return []
  }
}

// 获取所有服务类型的模型列表
export const getAllModels = async (): Promise<Record<string, string[]>> => {
  try {
    // 先获取所有服务类型
    const serviceTypesResponse = await request.get<RouterResponse<string[]>>('/config/type/services')
    if (!serviceTypesResponse.data?.success || !serviceTypesResponse.data.data) {
      return {}
    }

    const serviceTypes = serviceTypesResponse.data.data
    const modelsMap: Record<string, string[]> = {}

    // 为每个服务类型获取模型列表
    await Promise.all(
      serviceTypes.map(async (serviceType) => {
        modelsMap[serviceType] = await getModelsByServiceType(serviceType)
      })
    )

    return modelsMap
  } catch (error) {
    console.error('获取所有模型列表失败:', error)
    return {}
  }
}

// 服务类型到playground类型的映射
export const serviceTypeMapping: Record<string, string[]> = {
  chat: ['chat'],
  embedding: ['embedding'],
  rerank: ['rerank'],
  tts: ['tts'],
  stt: ['stt'],
  imgGen: ['imageGenerate'],
  imgEdit: ['imageEdit']
}

// 根据playground服务类型获取对应的实例管理服务类型
export const getInstanceServiceType = (playgroundType: string): string => {
  for (const [instanceType, playgroundTypes] of Object.entries(serviceTypeMapping)) {
    if (playgroundTypes.includes(playgroundType)) {
      return instanceType
    }
  }
  return playgroundType // 如果没有映射，直接返回原类型
}