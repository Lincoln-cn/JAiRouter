import { computed, ref } from 'vue'

/**
 * 配置页的「草稿 + 一次保存」编排。
 *
 * <p>背景（#1176 之后的配置页易用性改造）：一个配置页往往对应多个后端端点，
 * 于是早先的写法是「每张卡片一个保存按钮」，再后来变成多个按钮共用一个
 * `saving` 标志 —— 点一个三个一起转圈，用户看不出到底保存了什么。这里把
 * 「哪几块要提交、按什么顺序提交、失败定位到哪一块」收在一处，页面只提供
 * 每块的 `isDirty / validate / save / commit`。</p>
 *
 * <p>约定：</p>
 * <ul>
 *   <li>串行提交，不并发 —— 配置之间常有隐含顺序（例如先改批大小再改缓冲区）。</li>
 *   <li>某块校验或提交失败即停，后续块不再提交，避免「一半成功一半失败」的中间态。</li>
 *   <li>失败信息挂在块上（`errors[key]`），页面把卡片标红即可，不吞成一句 toast。</li>
 * </ul>
 */
export interface ConfigDraftSection {
  /** 分区键，用于错误定位与「正在保存」文案 */
  key: string
  /** 分区显示名（已翻译） */
  label: () => string
  /** 与服务端基线相比是否有改动 */
  isDirty: () => boolean
  /** 分区内校验，返回错误文案；通过返回 null */
  validate?: () => string | null
  /** 提交该分区（只提交脏字段） */
  save: () => Promise<void>
  /** 提交成功后把当前值记为基线 */
  commit: () => void
}

export interface ConfigSaveResult {
  ok: boolean
  saved: string[]
  failed?: { key: string; message: string }
}

const messageOf = (error: unknown, fallback: string): string => {
  if (error instanceof Error && error.message) {
    return error.message
  }
  if (typeof error === 'string' && error) {
    return error
  }
  return fallback
}

export function useConfigDraft(sections: ConfigDraftSection[], options: { fallbackError?: () => string } = {}) {
  const saving = ref(false)
  const savingKey = ref<string | null>(null)
  const errors = ref<Record<string, string>>({})

  const dirtySections = computed(() => sections.filter((section) => {
    try {
      return section.isDirty()
    } catch {
      return false
    }
  }))
  const dirtyCount = computed(() => dirtySections.value.length)
  const savingLabel = computed(() => {
    const key = savingKey.value
    if (!key) {
      return ''
    }
    return sections.find((section) => section.key === key)?.label() ?? ''
  })

  const clearError = (key?: string): void => {
    if (!key) {
      errors.value = {}
      return
    }
    const next = { ...errors.value }
    delete next[key]
    errors.value = next
  }

  const fail = (key: string, message: string, saved: string[]): ConfigSaveResult => {
    errors.value = { ...errors.value, [key]: message }
    return { ok: false, saved, failed: { key, message } }
  }

  /**
   * 串行保存所有脏分区。返回结果里 `saved` 是已成功的分区键，
   * `failed` 是第一个失败的块及其消息（页面据此标红）。
   */
  const save = async (): Promise<ConfigSaveResult> => {
    if (saving.value) {
      return { ok: false, saved: [] }
    }
    const targets = dirtySections.value
    if (targets.length === 0) {
      return { ok: true, saved: [] }
    }
    const fallback = options.fallbackError ?? ((): string => '保存失败')
    saving.value = true
    const saved: string[] = []
    try {
      for (const section of targets) {
        const invalid = section.validate?.() ?? null
        if (invalid) {
          return fail(section.key, invalid, saved)
        }
        savingKey.value = section.key
        try {
          await section.save()
        } catch (error) {
          return fail(section.key, messageOf(error, fallback()), saved)
        }
        section.commit()
        saved.push(section.key)
      }
      return { ok: true, saved }
    } finally {
      savingKey.value = null
      saving.value = false
    }
  }

  return { saving, savingKey, savingLabel, errors, dirtySections, dirtyCount, save, clearError }
}
