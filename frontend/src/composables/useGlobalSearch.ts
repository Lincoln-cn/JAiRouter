/**
 * 全局搜索（⌘K / Ctrl+K 命令面板）的数据层。
 *
 * 设计要点（P3）：
 * - **打开才请求**：静态清单（服务类型 / 实例 / API Key）在面板打开时加载，
 *   不在应用启动时预取，避免布局页承担额外请求。
 * - **30s 会话内缓存**：模块级 Map，键含查询串（异常组按关键字分键），
 *   避免每次开关面板都重新打后端；过期即删，不会无限增长。
 * - **防抖 250ms**：异常组是唯一随输入变化的远程查询，输入停顿后才发请求。
 * - **过期响应丢弃**：每次加载递增序号，`signal` 被 abort 或序号落后时一律不写回，
 *   避免「先发的慢响应覆盖后发的快响应」。
 * - **失败静默**：任何一组接口失败只让该组为空（面板不显示错误、控制台只用 console.debug），
 *   与「搜索是辅助入口」的定位一致。
 */

import { computed, onScopeDispose, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import request from '@/utils/request'
import type { ApiKeyVO, RouterResponse } from '@/types'
import type { ServiceInstance } from '@/types/common'
import type { ExceptionQueryResponse } from '@/types/exception'
import { menuGroups } from '@/config/menu'
import { usePermission } from '@/composables/usePermission'
import { SERVICE_TYPE_LABELS, ServiceType } from '@/constants/serviceTypes'

/** 每组展示上限；超出部分只在组头上写「共 N 条」 */
export const GROUP_LIMIT = 5

/** 静态清单元数据缓存时长：同一会话内 30 秒复用 */
const CACHE_TTL_MS = 30_000

/** 输入防抖（异常组） */
const DEBOUNCE_MS = 250

/** TraceID：链路追踪的 16–32 位十六进制串 */
const TRACE_ID_PATTERN = /^[0-9a-f]{16,32}$/i

export type SearchGroupKey = 'menu' | 'services' | 'instances' | 'apiKeys' | 'trace' | 'exceptions'

export interface SearchItem {
  /** 全局唯一 id（同时用作 DOM id 与键盘高亮键） */
  id: string
  /** 扁平列表中的下标（键盘导航用；由 groups 计算时统一编号） */
  index: number
  title: string
  subtitle?: string
  /** 目标路由 */
  to: string
  /** 路由 query（TraceID 组使用） */
  query?: Record<string, string>
}

export interface SearchGroupView {
  key: SearchGroupKey
  /** 组标题的 i18n key */
  titleKey: string
  /** 已截断到 GROUP_LIMIT 的可见条目 */
  items: SearchItem[]
  /** 命中总数（可能大于 items.length） */
  total: number
}

type ItemDraft = Omit<SearchItem, 'index'>

/** 带可搜索文本的候选条目；haystack 只参与匹配，不进入渲染结果 */
interface EntryDraft extends ItemDraft {
  haystack: Array<string | undefined>
}

interface RawGroup {
  key: SearchGroupKey
  titleKey: string
  items: ItemDraft[]
  total: number
}

interface LoadedInstance {
  serviceType: string
  instance: ServiceInstance
}

/* ========== 30s 缓存（模块级 = 同一页面会话） ========== */

const cache = new Map<string, { at: number; value: unknown }>()

const readCache = <T>(key: string): T | undefined => {
  const hit = cache.get(key)
  if (!hit) {
    return undefined
  }
  if (Date.now() - hit.at > CACHE_TTL_MS) {
    cache.delete(key)
    return undefined
  }
  return hit.value as T
}

const writeCache = (key: string, value: unknown): void => {
  cache.set(key, { at: Date.now(), value })
}

const debug = (message: string, error: unknown): void => {
  // 搜索是辅助入口：接口失败不打扰用户，也不污染控制台错误流
  console.debug(`[global-search] ${message}`, error)
}

const serviceTypeLabel = (type: string): string => {
  const key = SERVICE_TYPE_LABELS[type as ServiceType]
  return key ? t(key) : type
}

// t 需要在 setup 作用域内取；模块级辅助函数通过参数注入（见 useGlobalSearch）
let t: (key: string, named?: Record<string, unknown>) => string = key => key

/* ========== 远程加载 ========== */

const fetchServiceTypes = async (signal: AbortSignal): Promise<string[]> => {
  const response = await request.get<RouterResponse<string[]>>('/config/type/services', { signal })
  return Array.isArray(response.data?.data) ? response.data.data : []
}

const fetchInstances = async (
  serviceTypes: string[],
  signal: AbortSignal
): Promise<LoadedInstance[]> => {
  // 后端只有「按服务类型取实例」（GET /api/config/instance/{serviceType}），没有全量端点
  const results = await Promise.allSettled(
    serviceTypes.map(serviceType =>
      request.get<RouterResponse<ServiceInstance[]>>(
        `/config/instance/${encodeURIComponent(serviceType)}`,
        { signal }
      )
    )
  )
  const loaded: LoadedInstance[] = []
  results.forEach((result, index) => {
    if (result.status !== 'fulfilled') {
      return
    }
    const list = result.value.data?.data
    if (!Array.isArray(list)) {
      return
    }
    const serviceType = serviceTypes[index]
    list.forEach(instance => loaded.push({ serviceType, instance }))
  })
  return loaded
}

const fetchApiKeys = async (signal: AbortSignal): Promise<ApiKeyVO[]> => {
  const response = await request.get<RouterResponse<{ items?: ApiKeyVO[] }>>('/auth/api-keys', {
    signal
  })
  return Array.isArray(response.data?.data?.items) ? response.data.data.items : []
}

/* ========== 组合式函数 ========== */

export function useGlobalSearch() {
  const i18n = useI18n()
  t = (key, named) => i18n.t(key, named ?? {})

  const { filterMenuByPermission, resolveLandingPath } = usePermission()

  const query = ref('')
  const serviceTypes = ref<string[]>([])
  const instances = ref<LoadedInstance[]>([])
  const apiKeys = ref<ApiKeyVO[]>([])
  const exceptions = ref<ExceptionQueryResponse['content']>([])
  const staticLoading = ref(false)
  const exceptionLoading = ref(false)
  const loading = computed(() => staticLoading.value || exceptionLoading.value)

  /** 面板是否处于打开状态：关闭后不再接受任何迟到响应 */
  let open = false
  let staticAbort: AbortController | undefined
  let exceptionAbort: AbortController | undefined
  let staticSeq = 0
  let exceptionSeq = 0
  let debounceTimer: ReturnType<typeof setTimeout> | undefined

  /* ---------- 菜单（本地，随权限与语言变化） ---------- */

  const menuEntries = computed<EntryDraft[]>(() =>
    filterMenuByPermission(menuGroups).flatMap(group =>
      group.children.map(item => ({
        id: `menu:${item.path}`,
        title: t(item.title),
        subtitle: t(group.title),
        to: resolveLandingPath(item),
        haystack: [t(item.title), t(group.title), item.path]
      }))
    )
  )

  const serviceEntries = computed<EntryDraft[]>(() =>
    serviceTypes.value.map(type => ({
      id: `service:${type}`,
      title: serviceTypeLabel(type),
      subtitle: t('globalSearch.goServices'),
      to: '/config/services',
      haystack: [type, serviceTypeLabel(type)]
    }))
  )

  const instanceEntries = computed<EntryDraft[]>(() =>
    instances.value.map(({ serviceType, instance }) => ({
      id: `instance:${serviceType}:${instance.instanceId}`,
      title: instance.name || instance.instanceId,
      subtitle: [serviceTypeLabel(serviceType), instance.baseUrl].filter(Boolean).join(' · '),
      to: '/config/instances',
      haystack: [instance.name, instance.instanceId, instance.baseUrl, serviceType]
    }))
  )

  const apiKeyEntries = computed<EntryDraft[]>(() =>
    apiKeys.value.map(key => ({
      id: `api-key:${key.keyId}`,
      title: key.description || key.keyId,
      subtitle: key.keyId,
      to: '/security/api-keys',
      haystack: [key.description, key.keyId, key.createdBy]
    }))
  )

  const exceptionEntries = computed<EntryDraft[]>(() =>
    exceptions.value.map(event => ({
      id: `exception:${event.eventId}`,
      title: event.exceptionType || event.eventId,
      subtitle: event.sanitizedMessage || event.exceptionMessage || event.operation,
      to: '/exceptions/list',
      haystack: [
        event.exceptionType,
        event.sanitizedMessage,
        event.exceptionMessage,
        event.operation,
        event.traceId,
        event.serviceName,
        event.modelName
      ]
    }))
  )

  const includesQuery = (values: Array<string | undefined>, needle: string): boolean =>
    values.some(value => (value ?? '').toLowerCase().includes(needle))

  const traceGroup = (needle: string): RawGroup | undefined => {
    if (!TRACE_ID_PATTERN.test(needle)) {
      return undefined
    }
    return {
      key: 'trace',
      titleKey: 'globalSearch.groupTrace',
      total: 1,
      items: [
        {
          id: `trace:${needle}`,
          title: t('globalSearch.traceSearchItem', { id: needle }),
          subtitle: t('globalSearch.traceSearchHint'),
          to: '/tracing/search',
          query: { traceId: needle }
        }
      ]
    }
  }

  const toRawGroup = (
    key: SearchGroupKey,
    titleKey: string,
    matched: EntryDraft[]
  ): RawGroup => ({
    key,
    titleKey,
    total: matched.length,
    // haystack 只参与匹配，不进入渲染结果
    items: matched.slice(0, GROUP_LIMIT).map(({ id, title, subtitle, to, query }) => ({
      id,
      title,
      subtitle,
      to,
      query
    }))
  })

  /** 把原始组映射成视图组：total === 0 的组整组隐藏；其余按声明顺序连续编号（键盘上下切换用） */
  const toView = (raw: Array<RawGroup | undefined>): SearchGroupView[] => {
    const result: SearchGroupView[] = []
    let index = 0
    for (const group of raw) {
      if (!group || group.total === 0) {
        continue
      }
      result.push({
        ...group,
        items: group.items.map(item => ({ ...item, index: index++ }))
      })
    }
    return result
  }

  const groups = computed<SearchGroupView[]>(() => {
    const needle = query.value.trim().toLowerCase()
    if (!needle) {
      // 空查询也要有东西可看：给「菜单 + 服务类型」两组默认建议，
      // 否则面板刚打开是一片空白，用户只能盲打关键词。
      return toView([
        toRawGroup('menu', 'globalSearch.groupMenu', menuEntries.value),
        toRawGroup('services', 'globalSearch.groupServices', serviceEntries.value)
      ])
    }

    const raw: Array<RawGroup | undefined> = [
      toRawGroup(
        'menu',
        'globalSearch.groupMenu',
        menuEntries.value.filter(entry => includesQuery(entry.haystack, needle))
      ),
      toRawGroup(
        'services',
        'globalSearch.groupServices',
        serviceEntries.value.filter(entry => includesQuery(entry.haystack, needle))
      ),
      toRawGroup(
        'instances',
        'globalSearch.groupInstances',
        instanceEntries.value.filter(entry => includesQuery(entry.haystack, needle))
      ),
      toRawGroup(
        'apiKeys',
        'globalSearch.groupApiKeys',
        apiKeyEntries.value.filter(entry => includesQuery(entry.haystack, needle))
      ),
      traceGroup(needle),
      toRawGroup(
        'exceptions',
        'globalSearch.groupExceptions',
        exceptionEntries.value.filter(entry => includesQuery(entry.haystack, needle))
      )
    ]

    // total === 0 的组整组隐藏；其余按声明顺序编号（键盘上下切换用）
    return toView(raw)
  })

  const flatItems = computed<SearchItem[]>(() => groups.value.flatMap(group => group.items))

  /* ---------- 加载 ---------- */

  const loadStatic = async (): Promise<void> => {
    staticAbort?.abort()
    const controller = new AbortController()
    staticAbort = controller
    const seq = ++staticSeq
    staticLoading.value = true

    try {
      let types = readCache<string[]>('service-types')
      if (!types) {
        types = await fetchServiceTypes(controller.signal)
        writeCache('service-types', types)
      }
      if (seq !== staticSeq || controller.signal.aborted) {
        return
      }
      serviceTypes.value = types

      const instancesKey = `instances:${types.join(',')}`
      const cachedInstances = readCache<LoadedInstance[]>(instancesKey)
      const cachedApiKeys = readCache<ApiKeyVO[]>('api-keys')

      const [instancesResult, apiKeysResult] = await Promise.allSettled([
        cachedInstances
          ? Promise.resolve(cachedInstances)
          : fetchInstances(types, controller.signal).then(value => {
              writeCache(instancesKey, value)
              return value
            }),
        cachedApiKeys
          ? Promise.resolve(cachedApiKeys)
          : fetchApiKeys(controller.signal).then(value => {
              writeCache('api-keys', value)
              return value
            })
      ])

      if (seq !== staticSeq || controller.signal.aborted) {
        return
      }
      if (instancesResult.status === 'fulfilled') {
        instances.value = instancesResult.value
      } else {
        debug('instances failed', instancesResult.reason)
      }
      if (apiKeysResult.status === 'fulfilled') {
        apiKeys.value = apiKeysResult.value
      } else {
        debug('api keys failed', apiKeysResult.reason)
      }
    } catch (error) {
      // 服务类型失败：实例组也一并跳过，服务类型组为空
      if (seq === staticSeq) {
        debug('service types failed', error)
        serviceTypes.value = []
      }
    } finally {
      if (seq === staticSeq) {
        staticLoading.value = false
      }
    }
  }

  const loadExceptions = async (): Promise<void> => {
    const keyword = query.value.trim()
    exceptionAbort?.abort()
    if (!keyword || !open) {
      exceptions.value = []
      return
    }

    const controller = new AbortController()
    exceptionAbort = controller
    const seq = ++exceptionSeq
    const cacheKey = `exceptions:${keyword.toLowerCase()}`
    const cached = readCache<ExceptionQueryResponse['content']>(cacheKey)
    if (cached) {
      exceptions.value = cached
      return
    }

    exceptionLoading.value = true
    try {
      // 后端没有 keyword 参数：只有 exceptionType 支持模糊匹配（见 ExceptionManagementController）
      const response = await request.get<RouterResponse<ExceptionQueryResponse>>('/exceptions', {
        params: { exceptionType: keyword, page: 0, size: GROUP_LIMIT },
        signal: controller.signal
      })
      if (seq !== exceptionSeq || controller.signal.aborted) {
        return
      }
      const content = Array.isArray(response.data?.data?.content)
        ? response.data.data.content
        : []
      exceptions.value = content
      writeCache(cacheKey, content)
    } catch (error) {
      if (seq === exceptionSeq) {
        debug('exception search failed', error)
        exceptions.value = []
      }
    } finally {
      if (seq === exceptionSeq) {
        exceptionLoading.value = false
      }
    }
  }

  const scheduleExceptions = (): void => {
    if (debounceTimer) {
      clearTimeout(debounceTimer)
    }
    debounceTimer = setTimeout(() => {
      debounceTimer = undefined
      void loadExceptions()
    }, DEBOUNCE_MS)
  }

  /* ---------- 面板生命周期 ---------- */

  const onOpen = (): void => {
    open = true
    void loadStatic()
    if (query.value.trim()) {
      scheduleExceptions()
    }
  }

  const onClose = (): void => {
    open = false
    if (debounceTimer) {
      clearTimeout(debounceTimer)
      debounceTimer = undefined
    }
    staticAbort?.abort()
    exceptionAbort?.abort()
    staticSeq += 1
    exceptionSeq += 1
    staticLoading.value = false
    exceptionLoading.value = false
  }

  watch(query, () => {
    if (!open) {
      return
    }
    scheduleExceptions()
  })

  onScopeDispose(() => {
    onClose()
    cache.clear()
  })

  return {
    query,
    groups,
    flatItems,
    loading,
    onOpen,
    onClose
  }
}
