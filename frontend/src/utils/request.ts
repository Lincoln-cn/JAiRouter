import axios, { type AxiosInstance, type AxiosResponse, type InternalAxiosRequestConfig } from 'axios'
import type { ApiResponse } from '@/types'

// Create axios instance
const request: AxiosInstance = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL,
  timeout: 60000, // 增加到60秒
  headers: {
    'Content-Type': 'application/json'
  }
})

// Request interceptor
request.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    // 记录请求信息用于调试
    console.log('发送请求:', {
      method: config.method?.toUpperCase(),
      url: config.url,
      baseURL: config.baseURL,
      fullURL: `${config.baseURL}${config.url}`
    })
    
    // Add JWT token using the correct header name for JAiRouter
    const token = localStorage.getItem('admin_token')
    
    // 检查是否是JWT token获取接口，如果不是则添加token头部
    const isTokenEndpoint = config.url && (
      config.url.includes('/auth/jwt/login') || 
      config.url.includes('/auth/jwt/refresh')
    )
    
    if (token && config.headers && !isTokenEndpoint) {
      config.headers['Jairouter_Token'] = token
    }
    return config
  },
  error => {
    return Promise.reject(error)
  }
)

// Response interceptor
request.interceptors.response.use(
  (response: AxiosResponse<ApiResponse>) => {
    return response
  },
  error => {
    if (error.response?.status === 401) {
      // 检查请求是否来自API测试场
      // 通过检查请求URL中是否包含v1 API端点来判断
      const isPlaygroundApiRequest = error.config?.url && (
        error.config.url.includes('/v1/chat/completions') ||
        error.config.url.includes('/v1/embeddings') ||
        error.config.url.includes('/v1/rerank') ||
        error.config.url.includes('/v1/audio/speech') ||
        error.config.url.includes('/v1/audio/transcriptions') ||
        error.config.url.includes('/v1/images/generations') ||
        error.config.url.includes('/v1/images/edits')
      )
      
      if (!isPlaygroundApiRequest) {
        // Handle unauthorized access for platform management requests
        localStorage.removeItem('admin_token')
        // 使用相对路径而不是硬编码路径
        window.location.href = '/login'
      }
      // API测试场的401错误不进行路由跳转，让组件自行处理和显示错误信息
    }
    return Promise.reject(error)
  }
)

export default request