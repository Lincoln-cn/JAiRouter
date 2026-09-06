/**
 * Chat 请求处理 Composable
 * 封装 Chat 对话请求的核心逻辑，包括普通请求和流式请求
 */

import { ref, type Ref } from 'vue'
import { ElMessage } from 'element-plus'
import { sendUniversalRequest, sendUniversalStreamRequest } from '@/api/universal'
import { i18n } from '@/i18n'
const { t: gt } = i18n.global as unknown as {
  t: (key: string, named?: Record<string, string | number>) => string
}

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
  const loadingText = ref(gt('chatRequest.sending'))
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
          loadingText.value = gt('chatRequest.connecting')
          updateRequestStatus('info', gt('chatRequest.connectingStatus'))
        } else if (requestProgress.value < 60) {
          loadingText.value = gt('chatRequest.sendingData')
          updateRequestStatus('info', gt('chatRequest.sendingDataStatus'))
        } else {
          loadingText.value = gt('chatRequest.awaiting')
          updateRequestStatus('info', gt('chatRequest.awaitingStatus'))
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
      message: gt('chatRequest.cancelled')
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
    updateRequestStatus('info', gt('chatRequest.processing'))

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
      updateRequestStatus('success', gt('chatRequest.successMs', { duration: response.duration }))
      ElMessage.success({
        message: gt('chatRequest.sentOk'),
        duration: 2000,
        showClose: true
      })
    } else {
      updateRequestStatus('warning', gt('chatRequest.completedStatus', { status: response.status }))
      ElMessage.warning({
        message: gt('chatRequest.completedStatus', { status: response.status }),
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

    updateRequestStatus('info', gt('chatRequest.streamConnecting'))

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
                updateRequestStatus(
                  'info',
                  gt('chatRequest.receivingStream', {
                    count: messageCount,
                    chars: streamContent.length
                  })
                )
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
            updateRequestStatus('warning', gt('chatRequest.partialParseFailed'))
          }
        },
        // onError
        (error: any) => {
          console.error('流式请求错误:', error)
          const errText = error?.message || gt('chatRequest.unknownError')
          updateRequestStatus('error', gt('chatRequest.streamError', { message: errText }))
          ElMessage.error({
            message: gt('chatRequest.streamFailed', { message: errText }),
            duration: 4000,
            showClose: true
          })
          reject(error)
        },
        // onComplete
        () => {
          if (streamResponse) {
            updateRequestStatus('success', gt('chatRequest.streamDoneCount', { count: messageCount }))
            ElMessage.success({
              message: gt('chatRequest.streamDoneTotal', { count: messageCount }),
              duration: 3000,
              showClose: true
            })
            resolve(streamResponse)
          } else {
            updateRequestStatus('warning', gt('chatRequest.streamDoneEmpty'))
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
      loadingText.value = gt('chatRequest.preparing')
      updateRequestStatus('info', gt('chatRequest.validating'))

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
      let errorMessage = gt('chatRequest.sendFailed')

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
        updateRequestStatus('warning', gt('chatRequest.cancelled'))
      } else {
        updateRequestStatus('error', gt('chatRequest.failedPrefix', { message: errorMessage }))
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
