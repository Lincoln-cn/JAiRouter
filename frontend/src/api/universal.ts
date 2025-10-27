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
        timeout: 30000 // 30秒超时
      }
      
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
      // 构建fetch请求配置
      const fetchConfig: RequestInit = {
        method: apiRequest.method,
        headers: apiRequest.headers,
        body: apiRequest.body ? JSON.stringify(apiRequest.body) : undefined
      }
      
      const response = await fetch(apiRequest.endpoint, fetchConfig)
      
      if (!response.ok) {
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
          
          buffer += decoder.decode(value, { stream: true })
          const lines = buffer.split('\n')
          buffer = lines.pop() || ''
          
          for (const line of lines) {
            if (line.trim()) {
              try {
                // 处理SSE格式
                if (line.startsWith('data: ')) {
                  const dataStr = line.slice(6)
                  if (dataStr === '[DONE]') {
                    onComplete()
                    return
                  }
                  const data = JSON.parse(dataStr)
                  onMessage(data)
                } else {
                  // 处理普通JSON行
                  const data = JSON.parse(line)
                  onMessage(data)
                }
              } catch (parseError) {
                // 如果不是JSON，直接传递文本
                onMessage({ content: line })
              }
            }
          }
        }
        
        onComplete()
      } finally {
        reader.releaseLock()
      }
    } catch (error: any) {
      const endTime = Date.now()
      onError({
        status: 0,
        statusText: 'Stream Error',
        headers: {},
        data: { error: error.message },
        duration: endTime - startTime,
        timestamp: new Date().toISOString()
      })
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