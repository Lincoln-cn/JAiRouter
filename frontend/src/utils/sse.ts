import { ref } from 'vue'

// SSE连接状态
export const sseStatus = ref<'disconnected' | 'connecting' | 'connected'>('disconnected')

// 回调函数集合
const callbacks: Array<(data: any) => void> = []

// AbortController用于取消请求
let abortController: AbortController | null = null

// 连接SSE
export function connectSSE() {
  // 如果已经连接或正在连接，则返回
  if (sseStatus.value === 'connected' || sseStatus.value === 'connecting') {
    return
  }

  sseStatus.value = 'connecting'
  
  try {
    // 获取基础URL
    const baseUrl = import.meta.env.VITE_API_BASE_URL || ''
    let sseUrl = `${baseUrl}/health-status/stream`
    
    // 获取token
    const token = localStorage.getItem('admin_token')
    
    // 创建AbortController用于取消请求
    abortController = new AbortController()
    
    // 创建请求配置
    const config: RequestInit = {
      signal: abortController.signal
    }
    
    // 如果有token，则添加到headers中
    if (token) {
      config.headers = {
        'Jairouter_Token': token
      }
    }
    
    // 使用fetch创建带有自定义headers的请求
    fetch(sseUrl, config).then(response => {
      // 检查响应是否成功
      if (!response.ok) {
        throw new Error(`HTTP error! status: ${response.status}`)
      }
      
      // 获取响应body的ReadableStream
      const reader = response.body?.getReader()
      const decoder = new TextDecoder()
      
      if (!reader) {
        throw new Error('ReadableStream not supported')
      }
      
      console.log('SSE连接已建立')
      sseStatus.value = 'connected'
      
      // 处理流数据
      function readStream() {
        reader!.read().then(({ done, value }) => {
          if (done) {
            console.log('SSE连接已关闭')
            sseStatus.value = 'disconnected'
            // 尝试重连
            setTimeout(connectSSE, 5000)
            return
          }
          
          // 解码数据
          const chunk = decoder.decode(value, { stream: true })
          
          // 处理SSE格式的数据
          const lines = chunk.split('\n')
          lines.forEach(line => {
            if (line.startsWith('data: ')) {
              try {
                const data = JSON.parse(line.slice(6))
                console.log('收到SSE消息:', data)
                // 使用queueMicrotask将回调执行推迟到下一个微任务队列，确保在主线程中更新数据
                queueMicrotask(() => {
                  // 调用所有回调函数
                  callbacks.forEach(callback => {
                    try {
                      callback(data)
                    } catch (e) {
                      console.error('SSE消息处理错误:', e)
                    }
                  })
                })
              } catch (e) {
                console.error('解析SSE消息失败:', e)
              }
            }
          })
          
          // 继续读取流
          readStream()
        }).catch(error => {
          // 检查是否是由于取消请求导致的错误
          if (error.name === 'AbortError') {
            console.log('SSE连接被取消')
            sseStatus.value = 'disconnected'
            return
          }
          
          console.error('读取SSE流时出错:', error)
          sseStatus.value = 'disconnected'
          // 尝试重连
          setTimeout(connectSSE, 5000)
        })
      }
      
      // 开始读取流
      readStream()
    }).catch(error => {
      // 检查是否是由于取消请求导致的错误
      if (error.name === 'AbortError') {
        console.log('SSE连接被取消')
        sseStatus.value = 'disconnected'
        return
      }
      
      console.error('SSE连接失败:', error)
      sseStatus.value = 'disconnected'
      // 尝试重连
      setTimeout(connectSSE, 5000)
    })
  } catch (e) {
    console.error('SSE连接失败:', e)
    sseStatus.value = 'disconnected'
  }
}

// 断开SSE连接
export function disconnectSSE() {
  // 使用AbortController取消请求
  if (abortController) {
    abortController.abort()
    abortController = null
  }
  sseStatus.value = 'disconnected'
}

// 添加消息回调
export function addSSEListener(callback: (data: any) => void) {
  callbacks.push(callback)
}

// 移除消息回调
export function removeSSEListener(callback: (data: any) => void) {
  const index = callbacks.indexOf(callback)
  if (index > -1) {
    callbacks.splice(index, 1)
  }
}