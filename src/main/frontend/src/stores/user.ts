import { defineStore } from 'pinia'
import { ref } from 'vue'
import request from '@/utils/request'

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
      const response = await request.post('/auth/jwt/login', {
        username,
        password
      })
      
      if (response.data.success) {
        const { token: jwtToken, user } = response.data.data
        setToken(jwtToken)
        userInfo.value = user
        return response.data
      } else {
        throw new Error(response.data.message || '登录失败')
      }
    } catch (error: any) {
      throw new Error(error.response?.data?.message || error.message || '登录失败')
    }
  }

  // 登出方法
  const logout = async () => {
    try {
      if (token.value) {
        await request.post('/auth/jwt/revoke', {
          token: token.value
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
      
      if (response.data.success) {
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