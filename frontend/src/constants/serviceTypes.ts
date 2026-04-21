/**
 * 服务类型常量
 * 
 * 与后端 ServiceTypeConstants 保持一致
 * @see org.unreal.modelrouter.constants.ServiceTypeConstants
 * 
 * @author JAiRouter Team
 * @since v2.1.0
 */

/**
 * 服务类型枚举
 */
export enum ServiceType {
  CHAT = 'chat',
  EMBEDDING = 'embedding',
  RERANK = 'rerank',
  TTS = 'tts',
  STT = 'stt',
  IMG_GEN = 'imgGen',
  IMG_EDIT = 'imgEdit',
}

/**
 * 服务类型显示名称
 */
export const SERVICE_TYPE_LABELS: Record<ServiceType, string> = {
  [ServiceType.CHAT]: '聊天模型',
  [ServiceType.EMBEDDING]: '嵌入模型',
  [ServiceType.RERANK]: '重排序模型',
  [ServiceType.TTS]: '语音合成',
  [ServiceType.STT]: '语音识别',
  [ServiceType.IMG_GEN]: '图像生成',
  [ServiceType.IMG_EDIT]: '图像编辑',
}

/**
 * 所有服务类型
 */
export const ALL_SERVICE_TYPES = Object.values(ServiceType)

/**
 * 常用服务类型（用于预加载）
 */
export const COMMON_SERVICE_TYPES = [
  ServiceType.CHAT,
  ServiceType.EMBEDDING,
  ServiceType.RERANK,
  ServiceType.TTS,
  ServiceType.STT,
  ServiceType.IMG_GEN,
  ServiceType.IMG_EDIT,
]

/**
 * 检查是否是有效的服务类型
 */
export function isValidServiceType(value: string): boolean {
  return ALL_SERVICE_TYPES.includes(value as ServiceType)
}

/**
 * 从字符串转换为服务类型
 */
export function toServiceType(value: string): ServiceType | null {
  const serviceType = value as ServiceType
  return isValidServiceType(value) ? serviceType : null
}

/**
 * 获取服务类型显示名称
 */
export function getServiceTypeLabel(value: string): string {
  return SERVICE_TYPE_LABELS[value as ServiceType] || value
}
