import { ref } from 'vue'

// SSE连接状态
export const sseStatus = ref<'disconnected' | 'connecting' | 'connected'>('disconnected')

// 回调函数集合
const callbacks: Array<(data: any) => void> = []

// AbortController用于取消请求
let abortController: AbortController | null = null

// 显式拆毁标志：在 disconnectSSE 中置 true，在 connectSSE 入口重置
// 用于在 catch 中区分"因断开产生的预期异常"与"真正的连接失败"
let disconnecting = false

// 页面卸载监听器引用，用于在硬导航（F5/地址栏/goto）时也置拆毁标志
// Vue 的 onBeforeUnmount 在文档级卸载时不执行，需要额外监听 pagehide
let pageHideHandler: (() => void) | null = null

function addPageHideListener() {
  // 先移除旧监听，避免重复注册
  removePageHideListener()
  pageHideHandler = () => {
    disconnecting = true
  }
  // pagehide 在硬导航、关闭标签页、刷新时均触发
  window.addEventListener('pagehide', pageHideHandler)
  // beforeunload 作为兼容补充（部分旧浏览器 pagehide 可能不触发）
  window.addEventListener('beforeunload', pageHideHandler)
}

function removePageHideListener() {
  if (pageHideHandler) {
    window.removeEventListener('pagehide', pageHideHandler)
    window.removeEventListener('beforeunload', pageHideHandler)
    pageHideHandler = null
  }
}

/**
 * 判断异常是否由主动断开（拆毁）引起。
 *
 * 判据（按优先级）：
 *  1. 显式 disconnecting 标志 —— disconnectSSE() 在 abort 前置 true，
 *     覆盖 abort 与 fetch 竞态时抛出的 TypeError: Failed to fetch。
 *  2. AbortController.signal.aborted === true —— 信号已被触发，
 *     无论异常类型是 AbortError 还是 TypeError 都视为预期。
 *  3. error.name === 'AbortError' —— 浏览器原生 abort 抛出的错误，
 *     作为兜底兼容。
 *
 * 满足任一条件即认为是拆毁引起的异常，应当静默处理（不 log、不重连）。
 * 不满足时视为真正的失败，保留 console.error / console.warn 以便排查。
 */
function isDisconnectError(error: unknown): boolean {
  // 判据 1：显式拆毁标志（最可靠，覆盖竞态窗口）
  if (disconnecting) return true
  // 判据 2：信号已 abort（捕获 AbortError 和竞态 TypeError）
  if (abortController?.signal.aborted) return true
  // 判据 3：浏览器直接抛出 AbortError
  if (error instanceof Error && error.name === 'AbortError') return true
  return false
}

// 连接SSE
export function connectSSE() {
  // 如果已经连接或正在连接，则返回
  if (sseStatus.value === 'connected' || sseStatus.value === 'connecting') {
    return
  }

  // 重置拆毁标志，允许新连接正常工作
  disconnecting = false

  // 注册页面卸载监听，覆盖硬导航场景（Vue onBeforeUnmount 不会执行）
  addPageHideListener()

  sseStatus.value = 'connecting'
  
  try {
    // 获取基础URL
    const baseUrl = import.meta.env.VITE_API_BASE_URL || ''
    const sseUrl = `${baseUrl}/health-status/stream`
    
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
      // 如果在 fetch 响应到达前已经断开，静默丢弃
      if (disconnecting || abortController?.signal.aborted) {
        return
      }

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
            // 流正常结束：仅在非拆毁时 log 并重连
            if (isDisconnectError({ name: '' })) {
              return
            }
            console.log('SSE连接已关闭')
            sseStatus.value = 'disconnected'
            // 尝试重连
            setTimeout(connectSSE, 5000)
            return
          }
          
          // 如果已进入拆毁，不处理数据
          if (disconnecting || abortController?.signal.aborted) {
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
          // 拆毁引起的异常 → 静默返回，不重连
          if (isDisconnectError(error)) {
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
      // 拆毁引起的异常 → 静默返回，不重连
      if (isDisconnectError(error)) {
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

// 断开SSE连接（幂等：重复调用不报错）
export function disconnectSSE() {
  // 设置拆毁标志，在 abort 之前，确保竞态窗口内的异常也能被识别
  disconnecting = true
  // 移除页面卸载监听，SPA 拆毁时不再需要
  removePageHideListener()
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