/**
 * 服务类型常量
 * 
 * 与后端 ServiceTypeConstants 保持一致
 * @see org.unreal.modelrouter.constants.ServiceTypeConstants
 * 
 * @author JAiRouter Team
 * @since v2.1.0
 */

import { i18n } from '@/i18n'

// i18n.global.t 轻量包装（消息 schema 为动态合并，运行期按 key 查找字符串）
const gt = (key: string): string =>
  (i18n.global as unknown as { t: (key: string) => string }).t(key)

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
 * 服务类型显示名称对应的 i18n key（顶层命名空间 serviceTypes，文案见各语言 serviceTypes.json）
 * v2.10.3 双语版：渲染点用 t() / i18n.global.t(key) 翻译，未知类型回退原值
 */
export const SERVICE_TYPE_LABELS: Record<ServiceType, string> = {
  [ServiceType.CHAT]: 'serviceTypes.chat',
  [ServiceType.EMBEDDING]: 'serviceTypes.embedding',
  [ServiceType.RERANK]: 'serviceTypes.rerank',
  [ServiceType.TTS]: 'serviceTypes.tts',
  [ServiceType.STT]: 'serviceTypes.stt',
  [ServiceType.IMG_GEN]: 'serviceTypes.imgGen',
  [ServiceType.IMG_EDIT]: 'serviceTypes.imgEdit',
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
 * 获取服务类型显示名称（调用时求值，随语言切换生效）
 */
export function getServiceTypeLabel(value: string): string {
  const key = SERVICE_TYPE_LABELS[value as ServiceType]
  return key ? gt(key) : value
}
