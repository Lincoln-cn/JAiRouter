import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import request from '@/utils/request'
import type { ApiResponse } from '@/types'

// 用户信息接口
export interface UserInfo {
  username: string
  roles?: string[]
  permissions?: string[]
  [key: string]: unknown
}

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
  const userInfo = ref<UserInfo | null>(null)
  let refreshTimer: number | null = null

  // 从JWT token解析角色
  const parseRolesFromToken = (jwtToken: string): string[] => {
    try {
      const payload = JSON.parse(atob(jwtToken.split('.')[1]))
      return payload.roles || payload.authorities || []
    } catch {
      return []
    }
  }

  // 从JWT token解析用户名
  const parseUsernameFromToken = (jwtToken: string): string => {
    try {
      const payload = JSON.parse(atob(jwtToken.split('.')[1]))
      return payload.sub || ''
    } catch {
      return ''
    }
  }

  // 检查是否为管理员
  const isAdmin = computed(() => {
    return userInfo.value?.roles?.includes('ADMIN') || false
  })

  // 检查是否有指定角色
  const hasRole = (role: string): boolean => {
    return userInfo.value?.roles?.includes(role) || false
  }

  // 检查是否有任意一个角色
  const hasAnyRole = (roles: string[]): boolean => {
    if (!userInfo.value?.roles) return false
    return roles.some(role => userInfo.value!.roles!.includes(role))
  }

  const setToken = (newToken: string) => {
    token.value = newToken
    localStorage.setItem('admin_token', newToken)
    // 从token中提取用户信息
    const username = parseUsernameFromToken(newToken)
    const roles = parseRolesFromToken(newToken)
    userInfo.value = { username, roles }
  }

  const clearToken = () => {
    token.value = null
    localStorage.removeItem('admin_token')
    userInfo.value = null

    if (refreshTimer) {
      clearInterval(refreshTimer)
      refreshTimer = null
    }
  }

  const isAuthenticated = () => {
    return !!token.value
  }

  // 启动定时刷新令牌
  const startTokenRefresh = () => {
    if (refreshTimer) {
      clearInterval(refreshTimer)
    }

    refreshTimer = window.setInterval(async () => {
      if (token.value) {
        try {
          await refreshToken()
        } catch (error) {
          console.error('自动刷新令牌失败:', error)
          clearToken()
          import('@/router').then(({ default: router }) => {
            router.push('/login')
          })
        }
      }
    }, 30 * 60 * 1000)
  }

  // 刷新令牌方法
  const refreshToken = async () => {
    try {
      const response = await request.post<ApiResponse<LoginResponseData>>('/auth/jwt/refresh', {
        token: token.value
      })

      if (response.data.success && response.data.data) {
        const newToken = response.data.data.token
        setToken(newToken)
        return response.data
      } else {
        throw new Error(response.data.message || '令牌刷新失败')
      }
    } catch (error: any) {
      console.error('令牌刷新请求失败:', error)
      throw new Error(error.response?.data?.message || error.message || '令牌刷新失败')
    }
  }

  // 登录方法
  const login = async (username: string, password: string) => {
    try {
      const response = await request.post<ApiResponse<LoginResponseData>>('/auth/jwt/login', {
        username,
        password
      })
      
      if (response.data.success && response.data.data) {
        const jwtToken = response.data.data.token
        setToken(jwtToken)
        startTokenRefresh()
        return response.data
      } else {
        throw new Error(response.data.message || '登录失败')
      }
    } catch (error: any) {
      console.error('登录请求失败:', error)
      throw new Error(error.response?.data?.message || '登录失败')
    }
  }

  // 登出方法
  const logout = async () => {
    try {
      if (token.value) {
        const username = parseUsernameFromToken(token.value)
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
    isAdmin,
    setToken,
    clearToken,
    isAuthenticated,
    hasRole,
    hasAnyRole,
    login,
    logout,
    getUserInfo,
    refreshToken,
    startTokenRefresh,
    parseRolesFromToken,
    parseUsernameFromToken
  }
})
