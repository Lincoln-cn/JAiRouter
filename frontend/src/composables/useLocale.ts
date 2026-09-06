import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import {
  LOCALE_STORAGE_KEY,
  SUPPORTED_LOCALES,
  LOCALE_LABELS,
  applyDocumentLocale,
  type AppLocale
} from '@/utils/locale'

/**
 * 语言切换 composable（v2.10.3 双语版）
 *
 * - `locale`：当前语言（响应式，与 vue-i18n 全局 locale 联动）
 * - `setLocale`：切换语言并持久化（localStorage）+ 同步 <html lang>
 */
export function useLocale() {
  const { locale } = useI18n()

  const current = computed<AppLocale>(() => (locale.value === 'en-US' ? 'en-US' : 'zh-CN'))

  const setLocale = (next: AppLocale) => {
    locale.value = next
    localStorage.setItem(LOCALE_STORAGE_KEY, next)
    applyDocumentLocale(next)
  }

  return {
    locale: current,
    setLocale,
    options: SUPPORTED_LOCALES,
    labels: LOCALE_LABELS
  }
}
