import axios, { type AxiosInstance, type AxiosResponse, type AxiosRequestConfig } from 'axios'
import type { 
  PlaygroundRequest, 
  PlaygroundResponse, 
  ServiceType, 
  ApiEndpoint
} from '@/views/playground/types/playground'
import { SERVICE_ENDPOINTS } from '@/views/playground/types/playground'

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
    
    // 记录请求信息用于调试
    console.log('Playground请求:', {
      method: config.method?.toUpperCase(),
      url: config.url,
      headers: config.headers,
      data: config.data
    })
    
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
      headers: request.headers
    }
    
    // 处理文件上传
    if (request.files && request.files.length > 0) {
      const formData = new FormData()
      
      // 添加文件
      request.files.forEach((file, index) => {
        formData.append(`file${index === 0 ? '' : index}`, file)
      })
      
      // 添加其他数据
      if (request.body) {
        Object.keys(request.body).forEach(key => {
          if (request.body[key] !== undefined && request.body[key] !== null) {
            formData.append(key, request.body[key])
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
  } catch (error: any) {
    // 构造错误响应
    const errorResponse: PlaygroundResponse = {
      status: error.response?.status || 0,
      statusText: error.response?.statusText || 'Network Error',
      headers: error.response?.headers || {},
      data: error.response?.data || { error: error.message },
      duration: error.response?.duration || 0,
      timestamp: error.response?.timestamp || new Date().toISOString()
    }
    
    throw errorResponse
  }
}

/**
 * 根据服务类型和配置发送请求
 * @param serviceType 服务类型
 * @param config 请求配置
 * @param globalHeaders 全局请求头
 * @returns Promise<PlaygroundResponse>
 */
export const sendServiceRequest = async (
  serviceType: ServiceType,
  config: any,
  globalHeaders: Record<string, string> = {}
): Promise<PlaygroundResponse> => {
  const endpoint: ApiEndpoint = SERVICE_ENDPOINTS[serviceType]
  
  if (!endpoint) {
    throw new Error(`不支持的服务类型: ${serviceType}`)
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
    body: apiBody
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
 * 获取请求大小（字节）
 * @param request 请求对象
 * @returns 请求大小
 */
export const getRequestSize = (request: PlaygroundRequest): number => {
  let size = 0
  
  // 计算headers大小
  if (request.headers) {
    Object.entries(request.headers).forEach(([key, value]) => {
      size += key.length + value.length + 4 // +4 for ": " and "\r\n"
    })
  }
  
  // 计算body大小
  if (request.body) {
    if (typeof request.body === 'string') {
      size += new Blob([request.body]).size
    } else {
      size += new Blob([JSON.stringify(request.body)]).size
    }
  }
  
  // 计算文件大小
  if (request.files) {
    request.files.forEach(file => {
      size += file.size
    })
  }
  
  return size
}

/**
 * 获取响应大小（字节）
 * @param response 响应对象
 * @returns 响应大小
 */
export const getResponseSize = (response: PlaygroundResponse): number => {
  let size = 0
  
  // 计算headers大小
  Object.entries(response.headers).forEach(([key, value]) => {
    size += key.length + value.length + 4
  })
  
  // 计算data大小
  if (response.data) {
    if (typeof response.data === 'string') {
      size += new Blob([response.data]).size
    } else if (response.data instanceof Blob) {
      size += response.data.size
    } else {
      size += new Blob([JSON.stringify(response.data)]).size
    }
  }
  
  return size
}

/**
 * 格式化文件大小
 * @param bytes 字节数
 * @returns 格式化的大小字符串
 */
export const formatFileSize = (bytes: number): string => {
  if (bytes === 0) return '0 B'
  
  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  
  return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i]
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