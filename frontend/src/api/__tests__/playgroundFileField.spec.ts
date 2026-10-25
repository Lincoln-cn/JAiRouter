/**
 * Playground 文件上传字段名单测（#637）
 *
 * 图像编辑是 OpenAI 多文件契约（`image[]`），而 STT 是单文件契约（`file` / `file1`…）。
 * 这里直接拦截 axios 实例，断言实际拼进 FormData 的字段名。
 */
import { beforeEach, describe, expect, it, vi } from 'vitest'

const request = vi.fn()

vi.mock('axios', () => {
  const instance = (config: unknown) => request(config)
  ;(instance as unknown as { interceptors: unknown }).interceptors = {
    request: { use: vi.fn() },
    response: { use: vi.fn() }
  }
  return { default: { create: () => instance } }
})

import { sendPlaygroundRequest } from '@/api/playground'

const makeFile = (name: string): File => new File(['bytes'], name, { type: 'image/png' })

const lastConfig = (): { data: unknown } => request.mock.calls[0][0] as { data: unknown }

describe('sendPlaygroundRequest 文件部件命名', () => {
  beforeEach(() => {
    request.mockReset()
    request.mockResolvedValue({ status: 200, statusText: 'OK', headers: {}, data: 'ok' })
  })

  it('imageEdit 指定 fileField=image[] 时所有文件都用同一字段名', async () => {
    await sendPlaygroundRequest({
      endpoint: '/v1/images/edits',
      method: 'POST',
      headers: { 'Content-Type': 'multipart/form-data' },
      files: [makeFile('a.png'), makeFile('b.png')],
      fileField: 'image[]',
      body: { model: 'gpt-image-1', n: 2 }
    })

    const form = lastConfig().data as FormData
    expect(form.getAll('image[]')).toHaveLength(2)
    expect(form.get('model')).toBe('gpt-image-1')
    expect(form.get('n')).toBe('2')
    expect(form.get('file')).toBeNull()
  })

  it('未指定 fileField 时沿用 file / file1（STT 契约不变）', async () => {
    await sendPlaygroundRequest({
      endpoint: '/v1/audio/transcriptions',
      method: 'POST',
      headers: { 'Content-Type': 'multipart/form-data' },
      files: [makeFile('a.wav'), makeFile('b.wav')],
      body: { model: 'whisper-1' }
    })

    const form = lastConfig().data as FormData
    expect(form.getAll('file')).toHaveLength(1)
    expect(form.getAll('file1')).toHaveLength(1)
    expect(form.get('image[]')).toBeNull()
  })
})
