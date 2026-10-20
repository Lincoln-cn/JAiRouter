/**
 * 创建/编辑 API 密钥弹窗的表单形状。
 *
 * 拆分前它只是 `ApiKeyManagement.vue` 里 `ref({...})` 推导出的匿名类型；
 * 拆成子组件后需要跨文件引用，因此显式命名（字段与默认值都不变）。
 */
export interface ApiKeyFormModel {
  keyId: string
  description: string
  expiresAt: string
  enabled: boolean
  permissions: string[]
  allowedIpAddresses: string[]
  dailyRequestLimit: number
  dailyTokenLimit: number
  rateLimitPerMinute: number
  quotaAlertThreshold: number
  rotationPeriodDays: number
}
