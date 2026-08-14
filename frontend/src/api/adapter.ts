import request from '@/utils/request'
import type { RouterResponse } from '@/types'

export interface AdapterCapabilities {
  chat: boolean
  embedding: boolean
  rerank: boolean
  tts: boolean
  stt: boolean
  imgGen: boolean
  imgEdit: boolean
  streaming: boolean
}

export interface AdapterAuth {
  headerName: string
  headerPrefix: string
}

export interface AdapterInfo {
  name: string
  source: 'builtin' | 'configurable'
  type: string
  capabilities: AdapterCapabilities
}

export interface AdapterDetail {
  name: string
  source: 'builtin' | 'configurable'
  type: string
  capabilities?: AdapterCapabilities
  auth?: AdapterAuth
  additionalHeaders?: Record<string, string>
}

export interface AdapterDefinitionRequest {
  name: string
  type?: string
  parent?: string  // 继承模式下的父adapter名称
  capabilities?: Record<string, boolean>
  auth?: Record<string, string>
  additionalHeaders?: Record<string, string>
}

export interface ParentAdapterInfo {
  name: string
  source: 'builtin' | 'configurable'
}

// 模板相关类型
export interface AdapterTemplateCapabilities {
  chat: boolean
  embedding: boolean
  rerank: boolean
  tts: boolean
  stt: boolean
  imgGen: boolean
  imgEdit: boolean
  streaming: boolean
}

export interface AdapterTemplateAuth {
  headerName: string
  headerPrefix: string
}

export interface AdapterTemplate {
  id: string
  name: string
  description: string
  icon: string
  category: 'domestic' | 'international' | 'local' | 'custom'
  type: string
  defaultBaseUrl: string
  capabilities: AdapterTemplateCapabilities
  auth: AdapterTemplateAuth
  additionalHeaders?: Record<string, string>
  supportedModels?: string[]
  setupGuide?: string
  sortOrder: number
}

export interface TemplateCreateRequest {
  name: string
  apiKey: string
  baseUrl?: string
  capabilities?: Record<string, boolean>
}

// 测试相关类型
export interface AdapterTestRequest {
  testType: 'PING' | 'CHAT'
  apiKey?: string
  model?: string
  baseUrl?: string
}

export interface AdapterTestResult {
  success: boolean
  status: string
  latencyMs: number
  message: string
  httpStatusCode?: number
  details?: Record<string, any>
}

export const getAdapterList = () => {
  return request.get<RouterResponse<AdapterInfo[]>>('/config/adapter/list')
}

export const getParentAdapterList = () => {
  return request.get<RouterResponse<ParentAdapterInfo[]>>('/config/adapter/parents')
}

export const getAdapterDetail = (name: string) => {
  return request.get<RouterResponse<AdapterDetail>>(`/config/adapter/${name}`)
}

export const createAdapter = (data: AdapterDefinitionRequest) => {
  return request.post<RouterResponse<AdapterDetail>>('/config/adapter', data)
}

export const updateAdapter = (name: string, data: AdapterDefinitionRequest) => {
  return request.put<RouterResponse<AdapterDetail>>(`/config/adapter/${name}`, data)
}

export const deleteAdapter = (name: string) => {
  return request.delete<RouterResponse<void>>(`/config/adapter/${name}`)
}

// 模板 API
export const getAdapterTemplates = (category?: string) => {
  return request.get<RouterResponse<AdapterTemplate[]>>('/config/adapter/templates', {
    params: category ? { category } : undefined
  })
}

export const getAdapterTemplate = (id: string) => {
  return request.get<RouterResponse<AdapterTemplate>>(`/config/adapter/templates/${id}`)
}

export const createAdapterFromTemplate = (templateId: string, data: TemplateCreateRequest) => {
  return request.post<RouterResponse<AdapterDetail>>(`/config/adapter/templates/${templateId}/create`, data)
}

// 测试 API
export const testAdapter = (name: string, data: AdapterTestRequest) => {
  return request.post<RouterResponse<AdapterTestResult>>(`/config/adapter/${name}/test`, data)
}

export const testAdapterConfig = (data: AdapterTestRequest & {
  type: string
  baseUrl: string
  authHeaderName?: string
  authHeaderPrefix?: string
}) => {
  return request.post<RouterResponse<AdapterTestResult>>('/config/adapter/test', data)
}
