/**
 * API Key 列表/卡片的共用展示格式化。
 *
 * 表格（ApiKeyTable.vue）与窄屏卡片（ApiKeyCards.vue）都要展示「剩余天数」
 * 与「Token 数量」；两边各留一份实现迟早会漂移（阈值、进位规则不一致），
 * 因此统一收在这里。行为与拆分前 `ApiKeyManagement.vue` 内的同名函数逐字一致。
 */
import { useI18n } from 'vue-i18n'
import type { ApiKeyVO } from '@/types'

export function useApiKeyDisplay(): {
  getPermissionTagType: (permission: string) => 'primary' | 'success' | 'warning' | 'info' | 'danger'
  formatPermission: (permission: string) => string
  getRemainingDaysText: (row: ApiKeyVO) => string
  getRemainingDaysType: (row: ApiKeyVO) => 'success' | 'warning' | 'danger' | 'info'
  formatTokenCount: (count: number) => string
} {
  const { t } = useI18n()

  // 获取权限标签类型
  const getPermissionTagType = (permission: string) => {
    switch (permission) {
      case 'chat':
        return 'primary'
      case 'embedding':
        return 'success'
      case 'rerank':
        return 'warning'
      case 'tts':
        return 'info'
      case 'stt':
        return 'info'
      case 'imgGen':
        return 'danger'
      case 'imgEdit':
        return 'danger'
      // 兼容旧权限格式
      case 'image':
        return 'danger'
      case 'audio':
        return 'info'
      case 'admin':
        return 'danger'
      default:
        return 'info'
    }
  }

  // 格式化权限显示（后端权限值与 i18n key 同名；未知值原样返回）
  const formatPermission = (permission: string) => {
    return t(`apiKeys.permissions.${permission}`, permission)
  }

  // 获取剩余天数文本
  const getRemainingDaysText = (row: ApiKeyVO): string => {
    if (row.remainingDays === null || row.remainingDays === undefined) return t('apiKeys.remainingDaysText.permanent')
    if (row.remainingDays < 0) return t('apiKeys.remainingDaysText.expired')
    return t('apiKeys.remainingDaysText.days', { count: row.remainingDays })
  }

  // 获取剩余天数标签类型
  const getRemainingDaysType = (row: ApiKeyVO): 'success' | 'warning' | 'danger' | 'info' => {
    if (row.remainingDays === null || row.remainingDays === undefined) return 'info'
    if (row.remainingDays < 0) return 'danger'
    if (row.remainingDays < 7) return 'danger'
    if (row.remainingDays < 30) return 'warning'
    return 'success'
  }

  // 格式化 Token 数量
  const formatTokenCount = (count: number): string => {
    if (count >= 1000000) {
      return `${(count / 1000000).toFixed(1)}M`
    }
    if (count >= 1000) {
      return `${(count / 1000).toFixed(1)}K`
    }
    return count.toString()
  }

  return {
    getPermissionTagType,
    formatPermission,
    getRemainingDaysText,
    getRemainingDaysType,
    formatTokenCount
  }
}
