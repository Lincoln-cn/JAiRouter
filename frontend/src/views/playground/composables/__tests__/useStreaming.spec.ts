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

/**
 * Issue #256 配套：一条 data: 行被 TCP 分片切开时不得丢内容。
 *
 * 修复前每次 read() 直接 split('\n')：前半段因 JSON 不完整被丢弃，
 * 后半段不以 data: 开头同样被丢弃，表现为流式回答静默缺字。
 */
describe('useStreaming 跨 chunk 残行缓冲（issue #256）', () => {
  it('单条 data: 行被切成两个 chunk 时内容不丢失', async () => {
    const { handleStreamResponse } = useStreaming()
    const stream = createControlledBody()

    const line = sseChunk('Hello')
    const splitAt = Math.floor(line.length / 2)

    const pending = handleStreamResponse({ body: stream.body } as unknown as Response)
    stream.enqueue(line.slice(0, splitAt))
    stream.enqueue(line.slice(splitAt))
    stream.close()

    await expect(pending).resolves.toBe('Hello')
  })

  it('相邻行的跨 chunk 边界既不合并也不丢失', async () => {
    const { handleStreamResponse } = useStreaming()
    const stream = createControlledBody()

    const a = sseChunk('A')
    const b = sseChunk('B')
    const pending = handleStreamResponse({ body: stream.body } as unknown as Response)
    // 第一个 chunk 以 b 的前半行结尾，第二个 chunk 补完 b 的剩余部分
    stream.enqueue(a + b.slice(0, 7))
    stream.enqueue(b.slice(7))
    stream.close()

    await expect(pending).resolves.toBe('AB')
  })

  it('流未以换行收尾时最后一行仍被处理', async () => {
    const { handleStreamResponse } = useStreaming()
    const stream = createControlledBody()

    const pending = handleStreamResponse({ body: stream.body } as unknown as Response)
    stream.enqueue(sseChunk('first'))
    // 末尾不带换行符
    stream.enqueue(`data: ${JSON.stringify({ choices: [{ delta: { content: 'last' } }] })}`)
    stream.close()

    await expect(pending).resolves.toBe('firstlast')
  })

  it('分片切开后的 [DONE] 仍被正确识别为终止标记', async () => {
    const { handleStreamResponse } = useStreaming()
    const stream = createControlledBody()

    const doneLine = 'data: [DONE]\n'
    const pending = handleStreamResponse({ body: stream.body } as unknown as Response)
    stream.enqueue(sseChunk('ok'))
    stream.enqueue(doneLine.slice(0, 8))
    stream.enqueue(doneLine.slice(8))
    stream.close()

    await expect(pending).resolves.toBe('ok')
  })
})
