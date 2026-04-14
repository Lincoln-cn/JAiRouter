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
    isStreaming.value = true
    streamingContent.value = ''

    try {
      while (true) {
        const { done, value } = await reader.read()
        if (done) break

        const chunk = decoder.decode(value, { stream: true })
        const lines = chunk.split('\n')

        for (const line of lines) {
          // 兼容两种 SSE 格式：'data:' 和 'data: '
          if (line.startsWith('data:')) {
            // 去掉 'data:' 前缀，并处理可能的空格
            let data = line.slice(5).trim()
            // 如果还有空格开头（格式为 'data: xxx'），再去掉
            if (data.startsWith(' ')) {
              data = data.slice(1)
            }

            if (data === '[DONE]' || data.trim() === '[DONE]') {
              continue
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
        }
      }

      options.onComplete?.(fullContent)
      return fullContent
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