import { describe, expect, it, vi } from 'vitest'
import { ref } from 'vue'
import { useConfigDraft, type ConfigDraftSection } from '@/composables/useConfigDraft'

const makeSection = (
  key: string,
  options: {
    dirty?: boolean
    save?: () => Promise<void>
    commit?: () => void
    validate?: () => string | null
  } = {}
): ConfigDraftSection => {
  const dirty = ref(options.dirty ?? true)
  return {
    key,
    label: () => key,
    isDirty: () => dirty.value,
    save: options.save ?? (async () => {}),
    commit: options.commit ?? ((): void => {}),
    validate: options.validate
  }
}

describe('useConfigDraft', () => {
  it('没有脏分区时不发请求也不报成功', async () => {
    const save = vi.fn(async () => {})
    const draft = useConfigDraft([makeSection('a', { dirty: false, save })])

    expect(draft.dirtyCount.value).toBe(0)
    const result = await draft.save()
    expect(result).toEqual({ ok: true, saved: [] })
    expect(save).not.toHaveBeenCalled()
  })

  it('按声明顺序串行提交，并在每块成功后 commit', async () => {
    const order: string[] = []
    const first = makeSection('first', {
      save: async () => {
        order.push('save:first')
      },
      commit: () => order.push('commit:first')
    })
    const second = makeSection('second', {
      save: async () => {
        order.push('save:second')
      },
      commit: () => order.push('commit:second')
    })

    const draft = useConfigDraft([first, second])
    expect(draft.dirtyCount.value).toBe(2)

    const result = await draft.save()
    expect(result).toEqual({ ok: true, saved: ['first', 'second'] })
    expect(order).toEqual(['save:first', 'commit:first', 'save:second', 'commit:second'])
  })

  it('某块失败即停止：后续块不提交，错误挂在该块上', async () => {
    const laterSave = vi.fn(async () => {})
    const failing = makeSection('failing', {
      save: async () => {
        throw new Error('采样率必须在 0~1 之间')
      }
    })
    const later = makeSection('later', { save: laterSave })

    const draft = useConfigDraft([failing, later])
    const result = await draft.save()

    expect(result.ok).toBe(false)
    expect(result.saved).toEqual([])
    expect(result.failed).toEqual({ key: 'failing', message: '采样率必须在 0~1 之间' })
    expect(draft.errors.value.failing).toBe('采样率必须在 0~1 之间')
    expect(laterSave).not.toHaveBeenCalled()
  })

  it('校验不通过时不发请求，且错误来自 validate', async () => {
    const save = vi.fn(async () => {})
    const draft = useConfigDraft([
      makeSection('basic', { validate: () => '前缀不合法', save })
    ])

    const result = await draft.save()
    expect(result.ok).toBe(false)
    expect(result.failed?.message).toBe('前缀不合法')
    expect(save).not.toHaveBeenCalled()
  })

  it('提交过程中对外暴露「正在保存哪一块」', async () => {
    let observed = ''
    const draft = useConfigDraft([
      makeSection('sampling', {
        save: async () => {
          observed = draft.savingLabel.value
        }
      })
    ])

    await draft.save()
    expect(observed).toBe('sampling')
    expect(draft.savingLabel.value).toBe('')
    expect(draft.saving.value).toBe(false)
  })

  it('保存进行中重复调用直接返回失败，避免双击双写', async () => {
    let release = (): void => {}
    const gate = new Promise<void>((resolve) => {
      release = resolve
    })
    const save = vi.fn(async () => {
      await gate
    })
    const draft = useConfigDraft([makeSection('slow', { save })])

    const first = draft.save()
    const second = await draft.save()
    expect(second).toEqual({ ok: false, saved: [] })
    release()
    await first
    expect(save).toHaveBeenCalledTimes(1)
  })

  it('clearError 能清掉单块错误', async () => {
    const draft = useConfigDraft([
      makeSection('a', {
        save: async () => {
          throw new Error('boom')
        }
      })
    ])
    await draft.save()
    expect(draft.errors.value.a).toBe('boom')
    draft.clearError('a')
    expect(draft.errors.value.a).toBeUndefined()
  })
})
