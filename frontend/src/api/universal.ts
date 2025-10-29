import request from '@/utils/request'

// ==================== Universal API 调用接口 ====================

/**
 * Universal API 请求接口
 */
export interface UniversalApiRequest {
  endpoint: string
  method: string
  headers: Record<string, string>
  body?: any
  files?: File[]
}

/**
 * Universal API 响应接口
 */
export interface UniversalApiResponse {
  status: number
  statusText: string
  headers: Record<string, string>
  data: any
  duration: number
  timestamp: string
}

/**
 * Universal API 客户端
 */
class UniversalApiClient {
  /**
   * 发送Universal API请求
   */
  async sendRequest(apiRequest: UniversalApiRequest): Promise<UniversalApiResponse> {
    const startTime = Date.now()

    try {
      // 构建请求配置
      const config: any = {
        method: apiRequest.method,
        url: apiRequest.endpoint,
        headers: { ...apiRequest.headers },
        timeout: 180000 // 3分钟超时，适合非流式的长时间响应
      }

      // 添加JWT token认证头
      const token = localStorage.getItem('admin_token')
      if (token && config.headers) {
        config.headers['Jairouter_Token'] = token
      }

      // 记录请求信息用于调试
      console.log('Universal请求:', {
        method: config.method?.toUpperCase(),
        url: config.url,
        headers: config.headers,
        hasBody: !!apiRequest.body
      })

      // 处理请求体
      if (apiRequest.body) {
        if (apiRequest.files && apiRequest.files.length > 0) {
          // 处理文件上传（multipart/form-data）
          const formData = new FormData()

          // 添加文件
          apiRequest.files.forEach((file, index) => {
            formData.append(`file${index}`, file)
          })

          // 添加其他字段
          Object.entries(apiRequest.body).forEach(([key, value]) => {
            if (value !== undefined && value !== null) {
              formData.append(key, typeof value === 'object' ? JSON.stringify(value) : String(value))
            }
          })

          config.data = formData
          // 删除Content-Type让浏览器自动设置multipart边界
          delete config.headers['Content-Type']
        } else {
          // 处理JSON请求
          config.data = apiRequest.body
        }
      }

      // 发送请求
      const response = await request(config)
      const endTime = Date.now()

      console.log('Universal响应:', {
        status: response.status,
        statusText: response.statusText,
        duration: `${endTime - startTime}ms`,
        url: config.url
      })

      return {
        status: response.status,
        statusText: response.statusText,
        headers: response.headers as Record<string, string>,
        data: response.data,
        duration: endTime - startTime,
        timestamp: new Date().toISOString()
      }
    } catch (error: any) {
      const endTime = Date.now()

      console.error('Universal响应错误:', {
        status: error.response?.status,
        statusText: error.response?.statusText,
        duration: `${endTime - startTime}ms`,
        message: error.message,
        url: apiRequest.endpoint
      })

      // 构建错误响应
      const errorResponse: UniversalApiResponse = {
        status: error.response?.status || 0,
        statusText: error.response?.statusText || 'Network Error',
        headers: error.response?.headers || {},
        data: error.response?.data || { error: error.message },
        duration: endTime - startTime,
        timestamp: new Date().toISOString()
      }

      throw errorResponse
    }
  }

  /**
   * 发送流式请求（通过fetch实现SSE）
   */
  async sendStreamRequest(
    apiRequest: UniversalApiRequest,
    onMessage: (data: any) => void,
    onError: (error: any) => void,
    onComplete: () => void
  ): Promise<void> {
    const startTime = Date.now()

    try {
      // 构建请求头，添加认证信息
      const headers = { ...apiRequest.headers }

      // 添加JWT token认证头
      const token = localStorage.getItem('admin_token')
      if (token) {
        headers['Jairouter_Token'] = token
      }

      // 构建完整URL（流式请求需要完整URL）
      let fullUrl = apiRequest.endpoint
      if (!apiRequest.endpoint.startsWith('http')) {
        // 如果是相对路径，构建完整URL
        const baseURL = import.meta.env.VITE_API_BASE_URL || '/api'
        const origin = window.location.origin
        // 确保正确拼接URL：origin + baseURL + endpoint
        fullUrl = `${origin}${baseURL}${apiRequest.endpoint.startsWith('/') ? apiRequest.endpoint : '/' + apiRequest.endpoint}`
      }

      // 确保Content-Type正确设置
      if (!headers['Content-Type'] && apiRequest.body) {
        headers['Content-Type'] = 'application/json'
      }

      // 构建fetch请求配置
      const fetchConfig: RequestInit = {
        method: apiRequest.method.toUpperCase(),
        headers: headers,
        body: apiRequest.body ? JSON.stringify(apiRequest.body) : undefined
      }

      console.log('流式请求配置:', {
        method: fetchConfig.method,
        originalUrl: apiRequest.endpoint,
        fullUrl: fullUrl,
        headers: headers,
        hasBody: !!fetchConfig.body,
        bodyContent: fetchConfig.body ? JSON.stringify(apiRequest.body).substring(0, 200) + '...' : 'no body'
      })

      const response = await fetch(fullUrl, fetchConfig)

      console.log('流式请求响应:', {
        status: response.status,
        statusText: response.statusText,
        url: fullUrl,
        headers: Object.fromEntries(response.headers.entries())
      })

      if (!response.ok) {
        const errorText = await response.text().catch(() => 'Unknown error')
        console.error('流式请求失败:', {
          status: response.status,
          statusText: response.statusText,
          body: errorText
        })
        throw new Error(`HTTP ${response.status}: ${response.statusText}`)
      }

      const reader = response.body?.getReader()
      if (!reader) {
        throw new Error('无法获取响应流')
      }

      const decoder = new TextDecoder()
      let buffer = ''

      try {
        while (true) {
          const { done, value } = await reader.read()

          if (done) {
            break
          }

          // 立即解码数据，提高实时性
          const chunk = decoder.decode(value, { stream: true })
          buffer += chunk

          // 按行分割处理
          const lines = buffer.split('\n')
          buffer = lines.pop() || '' // 保留最后一个可能不完整的行

          // 立即处理每一行，不等待更多数据
          for (const line of lines) {
            const trimmedLine = line.trim()
            if (trimmedLine) {
              try {
                // 处理SSE格式
                if (trimmedLine.startsWith('data:')) {
                  const dataStr = trimmedLine.slice(5).trim()
                  console.log('解析SSE数据:', dataStr)
                  if (dataStr === '[DONE]') {
                    console.log('流式请求完成: 收到 [DONE] 标记')
                    onComplete()
                    return
                  }
                  if (dataStr) {
                    try {
                      const data = JSON.parse(dataStr)
                      console.log('成功解析流式数据:', data)
                      // 立即触发回调，提高实时性
                      onMessage(data)
                    } catch (parseError) {
                      console.error('解析SSE数据失败:', parseError, '原始数据:', dataStr)
                    }
                  }
                } else if (trimmedLine.startsWith('event: ')) {
                  // 处理事件类型（如果需要）
                  console.log('收到事件类型:', trimmedLine.slice(7))
                } else if (trimmedLine.startsWith('id: ')) {
                  // 处理事件ID（如果需要）
                  console.log('收到事件ID:', trimmedLine.slice(4))
                } else if (trimmedLine.startsWith('retry: ')) {
                  // 处理重试间隔（如果需要）
                  console.log('收到重试间隔:', trimmedLine.slice(7))
                } else {
                  // 处理普通JSON行或者可能是没有"data: "前缀的SSE数据
                  try {
                    const data = JSON.parse(trimmedLine)
                    onMessage(data)
                  } catch (jsonError) {
                    // 可能是没有"data: "前缀的SSE数据，尝试解析
                    if (trimmedLine.includes('"choices"') && trimmedLine.includes('"delta"')) {
                      try {
                        const data = JSON.parse(trimmedLine)
                        console.log('解析到流式数据:', data)
                        onMessage(data)
                      } catch (retryError) {
                        console.log('收到非JSON数据:', trimmedLine)
                        onMessage({ content: trimmedLine })
                      }
                    } else {
                      console.log('收到非JSON数据:', trimmedLine)
                      onMessage({ content: trimmedLine })
                    }
                  }
                }
              } catch (parseError) {
                console.warn('解析流式数据失败:', parseError, '原始数据:', trimmedLine)
                // 即使解析失败，也尝试传递原始内容
                onMessage({ content: trimmedLine, parseError: true })
              }
            }
          }

          // 添加小延迟以避免过度频繁的处理，但保持实时性
          await new Promise(resolve => setTimeout(resolve, 1))
        }

        console.log('流式请求自然结束')
        onComplete()
      } finally {
        reader.releaseLock()
      }
    } catch (error: any) {
      const endTime = Date.now()
      console.error('流式请求异常:', {
        error: error.message,
        stack: error.stack,
        duration: endTime - startTime
      })

      // 构建详细的错误信息
      const errorResponse = {
        status: error.status || 0,
        statusText: error.statusText || 'Stream Error',
        headers: {},
        data: {
          error: error.message,
          type: error.name || 'StreamError',
          timestamp: new Date().toISOString()
        },
        duration: endTime - startTime,
        timestamp: new Date().toISOString()
      }

      // 立即触发错误回调
      onError(errorResponse)
    }
  }
}

/**
 * Universal API 客户端实例
 */
export const universalApi = new UniversalApiClient()

/**
 * 发送Universal API请求
 */
export const sendUniversalRequest = (request: UniversalApiRequest) => universalApi.sendRequest(request)

/**
 * 发送流式Universal API请求
 */
export const sendUniversalStreamRequest = (
  request: UniversalApiRequest,
  onMessage: (data: any) => void,
  onError: (error: any) => void,
  onComplete: () => void
) => universalApi.sendStreamRequest(request, onMessage, onError, onComplete)

// 默认导出
export default universalApi