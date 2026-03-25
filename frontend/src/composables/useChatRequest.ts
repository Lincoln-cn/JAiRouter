/**
 * Chat 请求处理 Composable
 * 封装 Chat 对话请求的核心逻辑，包括普通请求和流式请求
 */

import { ref, type Ref } from 'vue'
import { ElMessage } from 'element-plus'
import { sendUniversalRequest, sendUniversalStreamRequest } from '@/api/universal'
import type { PlaygroundResponse, PlaygroundRequest } from '@/views/playground/types/playground'

export interface RequestStatus {
  type: 'success' | 'warning' | 'info' | 'error'
  message: string
}

export interface UseChatRequestReturn {
  loading: Ref<boolean>
  loadingText: Ref<string>
  requestProgress: Ref<number>
  requestStatus: Ref<RequestStatus | null>
  updateRequestStatus: (type: RequestStatus['type'], message: string) => void
  sendRequest: (request: PlaygroundRequest, isStream: boolean) => Promise<PlaygroundResponse | null>
  cancelRequest: () => void
  resetState: () => void
}

export function useChatRequest(): UseChatRequestReturn {
  const loading = ref(false)
  const loadingText = ref('发送中...')
  const requestProgress = ref(0)
  const requestStatus = ref<RequestStatus | null>(null)
  let abortController: AbortController | null = null
  let progressInterval: number | null = null

  // 更新请求状态
  const updateRequestStatus = (type: RequestStatus['type'], message: string) => {
    requestStatus.value = { type, message }

    // 成功和错误状态 3 秒后自动清除
    if (type === 'success' || type === 'error') {
      setTimeout(() => {
        if (requestStatus.value?.type === type) {
          requestStatus.value = null
        }
      }, 3000)
    }
  }

  // 启动进度模拟
  const startProgressSimulation = () => {
    progressInterval = window.setInterval(() => {
      if (requestProgress.value < 90) {
        requestProgress.value += Math.random() * 10
        if (requestProgress.value < 30) {
          loadingText.value = '建立连接中...'
          updateRequestStatus('info', '正在连接服务器...')
        } else if (requestProgress.value < 60) {
          loadingText.value = '发送请求中...'
          updateRequestStatus('info', '正在发送请求数据...')
        } else {
          loadingText.value = '等待响应中...'
          updateRequestStatus('info', '正在等待服务器响应...')
        }
      }
    }, 200)
  }

  // 停止进度模拟
  const stopProgressSimulation = () => {
    if (progressInterval) {
      clearInterval(progressInterval)
      progressInterval = null
    }
  }

  // 取消请求
  const cancelRequest = () => {
    if (abortController) {
      abortController.abort()
      abortController = null
    }
    stopProgressSimulation()
    loading.value = false
    requestProgress.value = 0
    requestStatus.value = {
      type: 'warning',
      message: '请求已取消'
    }

    // 3 秒后清除状态
    setTimeout(() => {
      if (requestStatus.value?.type === 'warning') {
        requestStatus.value = null
      }
    }, 3000)
  }

  // 重置状态
  const resetState = () => {
    loading.value = false
    requestProgress.value = 0
    abortController = null
    stopProgressSimulation()
  }

  // 处理普通请求
  const handleNormalRequest = async (request: PlaygroundRequest): Promise<PlaygroundResponse> => {
    updateRequestStatus('info', '正在处理请求...')

    const response = await sendUniversalRequest({
      endpoint: request.endpoint,
      method: request.method,
      headers: request.headers,
      body: request.body
    })

    const playgroundResponse: PlaygroundResponse = {
      status: response.status,
      statusText: response.statusText,
      headers: response.headers,
      data: response.data,
      duration: response.duration,
      timestamp: response.timestamp
    }

    if (response.status >= 200 && response.status < 300) {
      updateRequestStatus('success', `请求成功 (${response.duration}ms)`)
      ElMessage.success({
        message: '请求发送成功',
        duration: 2000,
        showClose: true
      })
    } else {
      updateRequestStatus('warning', `请求完成，状态码：${response.status}`)
      ElMessage.warning({
        message: `请求完成，状态码：${response.status}`,
        duration: 3000,
        showClose: true
      })
    }

    return playgroundResponse
  }

  // 处理流式请求
  const handleStreamRequest = async (
    request: PlaygroundRequest,
    onMessage?: (data: any) => void
  ): Promise<PlaygroundResponse | null> => {
    let streamResponse: PlaygroundResponse | null = null
    let streamContent = ''
    let messageCount = 0
    const startTime = Date.now()

    updateRequestStatus('info', '正在建立流式连接...')

    return new Promise((resolve, reject) => {
      sendUniversalStreamRequest(
        {
          endpoint: request.endpoint,
          method: request.method,
          headers: request.headers,
          body: request.body
        },
        // onMessage
        (data: any) => {
          try {
            messageCount++

            if (data.choices && data.choices[0] && data.choices[0].delta) {
              const delta = data.choices[0].delta
              if (delta.content) {
                streamContent += delta.content
              }

              // 更新流式状态
              if (messageCount % 5 === 0 || delta.content) {
                updateRequestStatus('info', `正在接收流式数据... (${messageCount} 条消息，${streamContent.length} 字符)`)
              }

              // 构建当前的响应数据
              const currentData = {
                ...data,
                choices: [{
                  ...data.choices[0],
                  message: {
                    role: 'assistant',
                    content: streamContent
                  }
                }]
              }

              streamResponse = {
                status: 200,
                statusText: 'OK',
                headers: { 'content-type': 'text/event-stream' },
                data: currentData,
                duration: Date.now() - startTime,
                timestamp: new Date().toISOString()
              }

              onMessage?.(streamResponse)
            }
          } catch (parseError) {
            console.warn('解析流式数据失败:', parseError, '原始数据:', data)
            updateRequestStatus('warning', '部分流式数据解析失败')
          }
        },
        // onError
        (error: any) => {
          console.error('流式请求错误:', error)
          updateRequestStatus('error', `流式请求错误：${error.message || '未知错误'}`)
          ElMessage.error({
            message: `流式请求失败：${error.message || '未知错误'}`,
            duration: 4000,
            showClose: true
          })
          reject(error)
        },
        // onComplete
        () => {
          if (streamResponse) {
            updateRequestStatus('success', `流式请求完成 (接收 ${messageCount} 条消息)`)
            ElMessage.success({
              message: `流式请求完成，共接收 ${messageCount} 条消息`,
              duration: 3000,
              showClose: true
            })
            resolve(streamResponse)
          } else {
            updateRequestStatus('warning', '流式请求完成但未收到有效数据')
            resolve(null)
          }
        }
      )
    })
  }

  // 发送请求
  const sendRequest = async (
    request: PlaygroundRequest,
    isStream: boolean
  ): Promise<PlaygroundResponse | null> => {
    try {
      // 创建新的取消控制器
      abortController = new AbortController()

      loading.value = true
      requestProgress.value = 0
      loadingText.value = '准备发送请求...'
      updateRequestStatus('info', '正在验证配置...')

      // 启动进度模拟
      startProgressSimulation()

      let response: PlaygroundResponse | null

      if (isStream) {
        response = await handleStreamRequest(request)
      } else {
        response = await handleNormalRequest(request)
      }

      stopProgressSimulation()
      requestProgress.value = 100

      return response
    } catch (error: any) {
      console.error('发送请求失败:', error)

      // 更好的错误处理
      let errorMessage = '发送请求失败'

      if (error instanceof Error) {
        errorMessage = error.message
      } else if (typeof error === 'object' && error !== null) {
        const errorObj = error as any
        if ('message' in errorObj && typeof errorObj.message === 'string') {
          errorMessage = errorObj.message
        } else if ('statusText' in errorObj && typeof errorObj.statusText === 'string') {
          errorMessage = `${errorObj.status || 'Unknown'}: ${errorObj.statusText}`
        } else if ('data' in errorObj && errorObj.data && typeof errorObj.data === 'object') {
          const dataObj = errorObj.data as any
          if (dataObj.message) {
            errorMessage = dataObj.message
          } else if (dataObj.error) {
            errorMessage = dataObj.error
          }
        }
      } else if (typeof error === 'string') {
        errorMessage = error
      }

      if (errorMessage.includes('aborted')) {
        updateRequestStatus('warning', '请求已取消')
      } else {
        updateRequestStatus('error', `请求失败：${errorMessage}`)
        ElMessage.error(errorMessage)
      }

      return null
    } finally {
      resetState()
    }
  }

  return {
    loading,
    loadingText,
    requestProgress,
    requestStatus,
    updateRequestStatus,
    sendRequest,
    cancelRequest,
    resetState
  }
}
