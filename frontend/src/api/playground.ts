import axios, { type AxiosInstance, type AxiosRequestConfig } from 'axios'
import type { 
  PlaygroundRequest, 
  PlaygroundResponse, 
  ServiceType, 
  ApiEndpoint
} from '@/views/playground/types/playground'
import { SERVICE_ENDPOINTS } from '@/views/playground/types/playground'
import { i18n } from '@/i18n'
const { t: gt } = i18n.global as unknown as {
  t: (key: string, named?: Record<string, string | number>) => string
}

// 创建专用于playground的axios实例
const playgroundRequest: AxiosInstance = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL,
  timeout: 180000, // 3分钟超时，适合AI模型的长时间响应
  headers: {
    'Content-Type': 'application/json'
  }
})

// 请求拦截器
playgroundRequest.interceptors.request.use(
  (config) => {
    const startTime = Date.now()
    config.metadata = { startTime }
    
    // 添加JWT token
    const token = localStorage.getItem('admin_token')
    if (token && config.headers) {
      config.headers['Jairouter_Token'] = token
    }
    
    return config
  },
  error => {
    console.error('Playground请求拦截器错误:', error)
    return Promise.reject(error)
  }
)

// 响应拦截器
playgroundRequest.interceptors.response.use(
  (response) => {
    const endTime = Date.now()
    const startTime = response.config.metadata?.startTime || endTime
    const duration = endTime - startTime
    
    // 添加性能信息到响应中
    response.duration = duration
    response.timestamp = new Date().toISOString()
    
    console.log('Playground响应:', {
      status: response.status,
      statusText: response.statusText,
      duration: `${duration}ms`,
      url: response.config.url
    })
    
    return response
  },
  error => {
    const endTime = Date.now()
    const startTime = error.config?.metadata?.startTime || endTime
    const duration = endTime - startTime
    
    // 为错误响应也添加性能信息
    if (error.response) {
      error.response.duration = duration
      error.response.timestamp = new Date().toISOString()
    }
    
    console.error('Playground响应错误:', {
      status: error.response?.status,
      statusText: error.response?.statusText,
      duration: `${duration}ms`,
      message: error.message,
      url: error.config?.url
    })
    
    // API测试场的401错误不应该影响平台路由，只记录错误信息
    // 让上层组件自行处理401错误的显示和用户交互
    
    return Promise.reject(error)
  }
)

/**
 * 发送playground请求的通用函数
 * @param request 请求配置
 * @returns Promise<PlaygroundResponse>
 */
export const sendPlaygroundRequest = async (request: PlaygroundRequest): Promise<PlaygroundResponse> => {
  try {
    const config: AxiosRequestConfig = {
      url: request.endpoint,
      method: request.method,
      headers: request.headers,
      responseType: request.responseType || 'json'
    }
    
    // 处理文件上传
    if (request.files && request.files.length > 0) {
      const formData = new FormData()
      
      // 添加文件
      request.files.forEach((file, index) => {
        formData.append(`file${index === 0 ? '' : index}`, file)
      })
      
      // 添加其他数据
      const body = request.body
      if (body) {
        Object.keys(body).forEach(key => {
          const value = body[key]
          if (value !== undefined && value !== null) {
            // Blob 原样传递，其余按表单字段语义转成字符串（与原先隐式 ToString 等价）
            formData.append(key, value instanceof Blob ? value : String(value))
          }
        })
      }
      
      config.data = formData
      if (config.headers) {
        config.headers['Content-Type'] = 'multipart/form-data'
      }
    } else if (request.body) {
      config.data = request.body
    }
    
    const response = await playgroundRequest(config)
    
    return {
      status: response.status,
      statusText: response.statusText,
      headers: response.headers as Record<string, string>,
      data: response.data,
      duration: response.duration || 0,
      timestamp: response.timestamp || new Date().toISOString()
    }
  } catch (error) {
    // 构造错误响应（#520：catch 的载荷是 unknown，这里按 axios 错误形状收窄）
    const err = error as {
      response?: {
        status?: number
        statusText?: string
        headers?: Record<string, string>
        data?: unknown
        duration?: number
        timestamp?: string
      }
      message?: string
    }
    const errorResponse: PlaygroundResponse = {
      status: err.response?.status || 0,
      statusText: err.response?.statusText || 'Network Error',
      headers: err.response?.headers || {},
      data: err.response?.data || { error: err.message },
      duration: err.response?.duration || 0,
      timestamp: err.response?.timestamp || new Date().toISOString()
    }
    
    throw errorResponse
  }
}

/**
 * Playground 请求配置的**公共底座**（#520）。
 *
 * 各服务类型各有自己的 `XxxRequestConfig extends BaseRequestConfig`（`ChatRequestConfig.messages`、
 * `ImageGenerateRequestConfig.prompt`…），形状不同但都带 `model` 与这几个可选字段。
 * 这里只声明「本模块自己消费的字段」，`sendServiceRequest` 用泛型约束接收，
 * 从而既保留调用方的具体类型，又不必让它们都补一条索引签名。
 */
export interface PlaygroundRequestConfig {
  /** 模型名（所有端点公共，决定路由实例） */
  model: string
  /** 覆盖默认授权头 */
  authorization?: string
  /** 追加到请求头的自定义键值 */
  customHeaders?: Record<string, string>
  /** 语音转写等单文件端点 */
  file?: File
  /** 图片编辑等多文件端点 */
  images?: File[]
}

/**
 * 根据服务类型和配置发送请求
 * @param serviceType 服务类型
 * @param config 请求配置（调用方的具体配置类型，见 PlaygroundRequestConfig）
 * @param globalHeaders 全局请求头
 * @returns Promise<PlaygroundResponse>
 */
export const sendServiceRequest = async <T extends PlaygroundRequestConfig>(
  serviceType: ServiceType,
  config: T,
  globalHeaders: Record<string, string> = {}
): Promise<PlaygroundResponse> => {
  const endpoint: ApiEndpoint = SERVICE_ENDPOINTS[serviceType]
  
  if (!endpoint) {
    throw new Error(gt('apiErrors.unsupportedServiceType', { serviceType }))
  }
  
  // 构建请求头
  const headers: Record<string, string> = {
    ...globalHeaders
  }
  
  // 设置Content-Type
  if (endpoint.contentType && endpoint.contentType !== 'multipart/form-data') {
    headers['Content-Type'] = endpoint.contentType
  }
  
  // 处理授权头
  if (config.authorization) {
    headers['Authorization'] = config.authorization
  }
  
  // 添加自定义头
  if (config.customHeaders) {
    Object.assign(headers, config.customHeaders)
  }
  
  // 构建请求体，排除非API字段
  const { authorization, customHeaders, ...apiBody } = config
  
  // 构建请求配置
  const request: PlaygroundRequest = {
    endpoint: endpoint.path,
    method: endpoint.method,
    headers,
    body: apiBody,
    responseType: endpoint.responseType
  }
  
  // 处理文件上传类型的请求
  if (serviceType === 'stt' && config.file) {
    request.files = [config.file]
    request.body = { ...apiBody }
    delete request.body.file
  } else if (serviceType === 'imageEdit' && config.images) {
    request.files = config.images
    request.body = { ...apiBody }
    delete request.body.images
  }
  
  return sendPlaygroundRequest(request)
}

/**
 * 格式化持续时间
 * @param ms 毫秒数
 * @returns 格式化的时间字符串
 */
export const formatDuration = (ms: number): string => {
  if (ms < 1000) {
    return `${ms}ms`
  } else if (ms < 60000) {
    return `${(ms / 1000).toFixed(2)}s`
  } else {
    const minutes = Math.floor(ms / 60000)
    const seconds = ((ms % 60000) / 1000).toFixed(2)
    return `${minutes}m ${seconds}s`
  }
}

// 扩展axios配置类型以支持metadata
declare module 'axios' {
  interface AxiosRequestConfig {
    metadata?: {
      startTime: number
    }
  }
  
  interface AxiosResponse {
    duration?: number
    timestamp?: string
  }
}

export default playgroundRequest