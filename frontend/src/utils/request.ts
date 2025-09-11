import axios, { type AxiosInstance, type AxiosResponse, type InternalAxiosRequestConfig } from 'axios'
import type { ApiResponse } from '@/types'

// Create axios instance
const request: AxiosInstance = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL,
  timeout: 10000,
  headers: {
    'Content-Type': 'application/json'
  }
})

// Request interceptor
request.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
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
      // Handle unauthorized access
      localStorage.removeItem('admin_token')
      // 使用相对路径而不是硬编码路径
      window.location.href = '/login'
    }
    return Promise.reject(error)
  }
)

export default request