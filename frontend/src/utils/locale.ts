/**
 * 应用语言工具（v2.10.3 双语版）
 *
 * - 支持 zh-CN / en-US，选择持久化于 localStorage（key: ja-locale）
 * - 首次访问按浏览器语言决定默认值（zh 系 → 中文，其余 → English）
 * - 纯函数模块，供 i18n 初始化 / useLocale / 组件复用，避免循环依赖
 */
export const LOCALE_STORAGE_KEY = 'ja-locale'

export const SUPPORTED_LOCALES = ['zh-CN', 'en-US'] as const

export type AppLocale = (typeof SUPPORTED_LOCALES)[number]

export const LOCALE_LABELS: Record<AppLocale, string> = {
  'zh-CN': '中文',
  'en-US': 'English'
}

export function isAppLocale(value: string | null): value is AppLocale {
  return value === 'zh-CN' || value === 'en-US'
}

/** 解析初始语言：localStorage 优先，其次浏览器语言（zh 系默认中文，其余英文） */
export function resolveInitialLocale(): AppLocale {
  const stored = localStorage.getItem(LOCALE_STORAGE_KEY)
  if (isAppLocale(stored)) {
    return stored
  }
  return navigator.language?.toLowerCase().startsWith('zh') ? 'zh-CN' : 'en-US'
}

/** 同步 <html lang>，便于浏览器 / 屏幕阅读器正确识别语言 */
export function applyDocumentLocale(locale: AppLocale): void {
  document.documentElement.lang = locale === 'zh-CN' ? 'zh-CN' : 'en'
}
