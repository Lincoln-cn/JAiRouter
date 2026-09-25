/**
 * Issue #131 配套：useStreaming 读循环在 abort 时应视为正常停止。
 *
 * 取消按钮 / 组件卸载都会 abort AbortController，底层 reader.read() 会抛
 * AbortError。该异常不应向上冒泡成未处理 rejection 或失败路径。
 */
import { describe, expect, it, vi } from 'vitest'
import { useStreaming } from '../useStreaming'

function createControlledBody() {
  const encoder = new TextEncoder()
  let controller!: ReadableStreamDefaultController<Uint8Array>
  const body = new ReadableStream<Uint8Array>({
    start(c) {
      controller = c
    }
  })
  return {
    body,
    enqueue: (text: string) => controller.enqueue(encoder.encode(text)),
    error: (e: unknown) => controller.error(e),
    close: () => controller.close()
  }
}

const sseChunk = (content: string) =>
  `data: ${JSON.stringify({ choices: [{ delta: { content } }] })}\n`

describe('useStreaming abort 处理（issue #131）', () => {
  it('AbortError 视为正常停止，返回已累积内容且不抛出', async () => {
    const { handleStreamResponse, isStreaming } = useStreaming()
    const stream = createControlledBody()

    const pending = handleStreamResponse({ body: stream.body } as unknown as Response)
    stream.enqueue(sseChunk('hi'))
    stream.error(new DOMException('The operation was aborted.', 'AbortError'))

    await expect(pending).resolves.toBe('hi')
    expect(isStreaming.value).toBe(false)
  })

  it('非 abort 错误仍向上抛出', async () => {
    const { handleStreamResponse } = useStreaming()
    const stream = createControlledBody()

    const pending = handleStreamResponse({ body: stream.body } as unknown as Response)
    stream.error(new Error('network failed'))

    await expect(pending).rejects.toThrow('network failed')
  })

  it('正常结束时触发 onComplete', async () => {
    const { handleStreamResponse } = useStreaming()
    const stream = createControlledBody()
    const onComplete = vi.fn()

    const pending = handleStreamResponse(
      { body: stream.body } as unknown as Response,
      { onComplete }
    )
    stream.enqueue(sseChunk('hello'))
    stream.close()

    await expect(pending).resolves.toBe('hello')
    expect(onComplete).toHaveBeenCalledWith('hello')
  })

  it('cancelStream 后（信号已 aborted）读循环异常不再抛出', async () => {
    const { createStreamRequest, handleStreamResponse, cancelStream } = useStreaming()
    const stream = createControlledBody()

    vi.stubGlobal(
      'fetch',
      vi.fn().mockImplementation((_url: string, init: RequestInit) => {
        return new Promise((resolve, reject) => {
          init.signal?.addEventListener('abort', () => {
            reject(new DOMException('The operation was aborted.', 'AbortError'))
          })
          resolve({ ok: true, status: 200, body: stream.body })
        })
      })
    )

    try {
      const response = await createStreamRequest('http://test.local/v1/chat/completions', {})
      const pending = handleStreamResponse(response)
      stream.enqueue(sseChunk('partial'))
      // 主动取消：abort 信号触发；随后底层流以任意错误收尾
      cancelStream()
      stream.error(new TypeError('network error'))

      await expect(pending).resolves.toBe('partial')
    } finally {
      vi.unstubAllGlobals()
    }
  })
})
