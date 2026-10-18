// 基础请求配置
export interface BaseRequestConfig {
  model: string
  authorization?: string
  customHeaders?: Record<string, string>
}

// 对话消息接口
export interface ChatMessage {
  role: 'system' | 'user' | 'assistant'
  content: string
  name?: string
  timestamp?: string
}

// 对话请求配置
export interface ChatRequestConfig extends BaseRequestConfig {
  messages: ChatMessage[]
  stream?: boolean
  maxTokens?: number
  temperature?: number
  topP?: number
  topK?: number
  frequencyPenalty?: number
  presencePenalty?: number
  stop?: string | string[]
  user?: string
}

// 嵌入请求配置
export interface EmbeddingRequestConfig extends BaseRequestConfig {
  input: string | string[]
  encodingFormat?: string
  dimensions?: number
  user?: string
}

// 重排序请求配置
export interface RerankRequestConfig extends BaseRequestConfig {
  query: string
  documents: string[]
  topN?: number
  returnDocuments?: boolean
}

// TTS请求配置
export interface TtsRequestConfig extends BaseRequestConfig {
  input: string
  voice?: string
  responseFormat?: string
  speed?: number
}

// STT请求配置
export interface SttRequestConfig extends BaseRequestConfig {
  file: File
  language?: string
  prompt?: string
  responseFormat?: string
  temperature?: number
}

// 图像生成请求配置
export interface ImageGenerateRequestConfig extends BaseRequestConfig {
  prompt: string
  n?: number
  quality?: string
  responseFormat?: string
  size?: string
  style?: string
  user?: string
}

// 图像编辑请求配置
export interface ImageEditRequestConfig extends BaseRequestConfig {
  images: File[]
  prompt: string
  background?: string
  inputFidelity?: string
  mask?: string
  n?: number
  outputCompression?: number
  outputFormat?: string
  partialImages?: number
  quality?: string
  responseFormat?: string
  size?: string
  stream?: boolean
  user?: string
}

// 服务类型枚举
export type ServiceType = 'chat' | 'embedding' | 'rerank' | 'tts' | 'stt' | 'imageGenerate' | 'imageEdit'

// 保存的配置
export interface SavedConfiguration {
  id: string
  name: string
  serviceType: ServiceType
  /** 各服务类型的请求配置（形状按端点不同），保存/回填时整块透传 */
  config: Record<string, unknown>
  createdAt: string
  description?: string
  editing?: boolean
  editName?: string
}

// 响应数据
export interface PlaygroundResponse {
  status: number
  statusText: string
  headers: Record<string, string>
  /** 上游原始载荷（JSON 或 Blob），由各面板按自己的端点收窄 */
  data: unknown
  duration: number
  timestamp: string
}

// 请求配置
export interface PlaygroundRequest {
  endpoint: string
  method: string
  headers: Record<string, string>
  body?: Record<string, unknown>
  files?: File[]
  responseType?: 'json' | 'blob' | 'text'
}

// 全局配置状态
export interface GlobalConfig {
  authorization: string
  customHeaders: Record<string, string>
}

// API端点配置
export interface ApiEndpoint {
  path: string
  method: 'GET' | 'POST' | 'PUT' | 'DELETE'
  contentType?: string
  responseType?: 'json' | 'blob' | 'text'
}

// 服务端点映射
export const SERVICE_ENDPOINTS: Record<ServiceType, ApiEndpoint> = {
  chat: {
    path: '/v1/chat/completions',
    method: 'POST',
    contentType: 'application/json'
  },
  embedding: {
    path: '/v1/embeddings',
    method: 'POST',
    contentType: 'application/json'
  },
  rerank: {
    path: '/v1/rerank',
    method: 'POST',
    contentType: 'application/json'
  },
  tts: {
    path: '/v1/audio/speech',
    method: 'POST',
    contentType: 'application/json',
    responseType: 'blob'
  },
  stt: {
    path: '/v1/audio/transcriptions',
    method: 'POST',
    contentType: 'multipart/form-data'
  },
  imageGenerate: {
    path: '/v1/images/generations',
    method: 'POST',
    contentType: 'application/json'
  },
  imageEdit: {
    path: '/v1/images/edits',
    method: 'POST',
    contentType: 'multipart/form-data'
  }
}

