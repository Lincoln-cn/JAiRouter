import { defineStore } from 'pinia'
import { ref } from 'vue'
import request from '@/utils/request'
import type { ApiResponse } from '@/types'

// 定义登录响应数据类型
interface LoginResponseData {
  token: string
  tokenType: string
  expiresIn: number
  message: string
  timestamp: string
}

export const useUserStore = defineStore('user', () => {
  const token = ref<string | null>(localStorage.getItem('admin_token'))
  const userInfo = ref<any>(null)

  const setToken = (newToken: string) => {
    token.value = newToken
    localStorage.setItem('admin_token', newToken)
  }

  const clearToken = () => {
    token.value = null
    localStorage.removeItem('admin_token')
    userInfo.value = null
  }

  const isAuthenticated = () => {
    return !!token.value
  }

  // 登录方法
  const login = async (username: string, password: string) => {
    try {
      const response = await request.post<ApiResponse<LoginResponseData>>('/auth/jwt/login', {
        username,
        password
      })
      
      if (response.data.success && response.data.data) {
        // 从响应数据中提取 token
        const jwtToken = response.data.data.token
        setToken(jwtToken)
        // 可以在这里设置用户信息，如果需要的话
        // userInfo.value = response.data.data.user
        return response.data
      } else {
        throw new Error(response.data.message || '登录失败')
      }
    } catch (error: any) {
      console.error('登录请求失败:', error)
      throw new Error(error.response?.data?.message || error.message || '登录失败')
    }
  }

  // 登出方法
  const logout = async () => {
    try {
      if (token.value) {
        // 解析JWT token获取用户名
        const payload = JSON.parse(atob(token.value.split('.')[1]))
        const username = payload.sub
        
        await request.post('/auth/jwt/revoke', {
          token: token.value,
          userId: username,
          reason: '用户主动登出'
        })
      }
    } catch (error) {
      console.error('登出请求失败:', error)
    } finally {
      clearToken()
    }
  }

  // 获取用户信息
  const getUserInfo = async () => {
    try {
      const response = await request.get('/auth/jwt/validate', {
        headers: {
          'Jairouter_Token': token.value
        }
      })
      
      if (response.data.success && response.data.data) {
        userInfo.value = response.data.data.user
        return userInfo.value
      }
    } catch (error) {
      console.error('获取用户信息失败:', error)
      clearToken()
      throw error
    }
  }

  return {
    token,
    userInfo,
    setToken,
    clearToken,
    isAuthenticated,
    login,
    logout,
    getUserInfo
  }
})