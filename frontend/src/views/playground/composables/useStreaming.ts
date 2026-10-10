import { ref, type Ref } from 'vue'
import type { PlaygroundResponse } from '../types/playground'

export interface StreamingOptions {
  onChunk?: (chunk: string) => void
  onComplete?: (fullContent: string) => void
  onError?: (error: Error) => void
}

export function useStreaming() {
  const isStreaming = ref(false)
  const streamingContent = ref('')
  const abortController = ref<AbortController | null>(null)

  /**
   * 处理 SSE 流式响应
   */
  const handleStreamResponse = async (
    response: Response,
    options: StreamingOptions = {}
  ): Promise<string> => {
    const reader = response.body?.getReader()
    if (!reader) {
      throw new Error('Response body is not readable')
    }

    const decoder = new TextDecoder()
    let fullContent = ''
    let buffer = ''
    isStreaming.value = true
    streamingContent.value = ''
    // 捕获当前 AbortController：cancelStream 会把 abortController 置空，
    // 异常时需用本地引用判断是否为主动中止
    const controller = abortController.value

    // 处理单条 SSE 行
    const handleLine = (line: string) => {
      // 兼容两种 SSE 格式：'data:' 和 'data: '
      if (!line.startsWith('data:')) {
        return
      }
      // 去掉 'data:' 前缀，并处理可能的空格
      let data = line.slice(5).trim()
      // 如果还有空格开头（格式为 'data: xxx'），再去掉
      if (data.startsWith(' ')) {
        data = data.slice(1)
      }

      if (data === '[DONE]' || data.trim() === '[DONE]') {
        return
      }

      try {
        const parsed = JSON.parse(data)
        const content = parsed.choices?.[0]?.delta?.content || ''

        if (content) {
          fullContent += content
          streamingContent.value = fullContent
          options.onChunk?.(content)
        }
      } catch {
        // 解析失败，跳过
      }
    }

    try {
      while (true) {
        const { done, value } = await reader.read()
        if (done) break

        // 跨 chunk 残行缓冲：一条 data: 行可能被 TCP 分片切开，若直接 split('\n')
        // 逐段处理，前半段会因 JSON 不完整被丢弃、后半段又不以 data: 开头同样被丢弃，
        // 导致流式内容静默缺字（issue #256）。这里把不完整的尾段留到下一次拼接。
        buffer += decoder.decode(value, { stream: true })
        const lines = buffer.split('\n')
        buffer = lines.pop() ?? ''

        for (const line of lines) {
          handleLine(line)
        }
      }

      // 流结束时处理未以换行收尾的最后一行
      if (buffer.length > 0) {
        handleLine(buffer)
      }

      options.onComplete?.(fullContent)
      return fullContent
    } catch (error) {
      // 主动 abort（取消按钮 / 组件卸载）视为正常停止，不向上抛出
      if (
        (error as { name?: string })?.name === 'AbortError' ||
        controller?.signal.aborted
      ) {
        return fullContent
      }
      throw error
    } finally {
      isStreaming.value = false
    }
  }

  /**
   * 取消流式响应
   */
  const cancelStream = () => {
    if (abortController.value) {
      abortController.value.abort()
      abortController.value = null
    }
    isStreaming.value = false
  }

  /**
   * 创建流式请求
   */
  const createStreamRequest = async (
    url: string,
    body: any,
    headers: Record<string, string> = {}
  ): Promise<Response> => {
    abortController.value = new AbortController()

    const response = await fetch(url, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        ...headers
      },
      body: JSON.stringify(body),
      signal: abortController.value.signal
    })

    if (!response.ok) {
      const errorText = await response.text()
      throw new Error(`HTTP ${response.status}: ${errorText}`)
    }

    return response
  }

  return {
    isStreaming,
    streamingContent,
    handleStreamResponse,
    cancelStream,
    createStreamRequest
  }
}