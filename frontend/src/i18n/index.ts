import { createI18n } from 'vue-i18n'
import { resolveInitialLocale, applyDocumentLocale, type AppLocale } from '@/utils/locale'

/**
 * v2.10.3 语言包加载：按语言目录（locales/<lang>/*.json）合并全部命名空间。
 * 页面迁移时新增文件即可（如 zh-CN/serviceManagement.json），无需改动本模块。
 */
const localeModules = import.meta.glob('../locales/*/*.json', {
  eager: true,
  import: 'default'
}) as Record<string, Record<string, unknown>>

const messages = (Object.keys(localeModules) as string[]).reduce<
  Record<string, Record<string, unknown>>
>((acc, path) => {
  const lang = path.split('/').slice(-2)[0] as AppLocale
  acc[lang] ??= {}
  Object.assign(acc[lang], localeModules[path])
  return acc
}, {})

const initialLocale = resolveInitialLocale()
applyDocumentLocale(initialLocale)

// JSON 消息树为 string/number/嵌套对象，动态合并后与 vue-i18n 泛型 schema 不兼容；
// 运行期仅按 key 查找字符串，做一次性断言（消息内容由 tsconfig resolveJsonModule 校验字面量，无 any）
const i18nMessages = messages as unknown as never

/**
 * 全局 i18n 单例（v2.10.3 双语版）
 * 独立模块导出，供 main.ts / router（document.title）/ 组件 useI18n 共用同一实例。
 */
export const i18n = createI18n({
  legacy: false,
  locale: initialLocale,
  fallbackLocale: 'en-US',
  messages: i18nMessages
})

export default i18n
