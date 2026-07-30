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
