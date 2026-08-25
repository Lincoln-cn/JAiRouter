import request from '@/utils/request'
import type { RouterResponse } from '@/types'

// 条件类型
export type RuleConditionType = 'SERVICE_TYPE' | 'MODEL_NAME' | 'HEADER' | 'CLIENT_IP' | 'WEIGHT'

// 操作符
export type RuleOperator = 'EQUALS' | 'CONTAINS' | 'STARTS_WITH' | 'REGEX' | 'CIDR_MATCH'

// 动作类型
export type RuleActionType = 'TARGET_MODEL' | 'TARGET_INSTANCE' | 'TARGET_ADAPTER' | 'LB_STRATEGY'

export interface RuleCondition {
  type: RuleConditionType
  field?: string  // 仅 HEADER 用
  operator: RuleOperator
  value: string
  weight?: number  // 仅 WEIGHT 型
}

export interface RuleAction {
  type: RuleActionType
  modelName?: string
  instanceId?: string
  adapterName?: string
  lbStrategy?: string
}

export interface RuleDefinition {
  id: string
  name: string
  description?: string
  enabled: boolean
  priority: number
  matchMode?: string
  conditions: RuleCondition[]
  action: RuleAction
}

export interface PriorityUpdateItem {
  id: string
  priority: number
}

// 规则模拟测试(dry-run)
export interface RuleValidateRequest {
  serviceType?: string
  modelName: string
  clientIp?: string
  headers?: Record<string, string>
}

export interface RuleValidateResult {
  matched: boolean
  ruleId?: string
  ruleName?: string
  priority?: number
  action?: {
    type: RuleActionType
    target?: string
  }
  message: string
}

export const getRuleList = () => {
  return request.get<RouterResponse<RuleDefinition[]>>('/config/rules/list')
}

export const getRule = (id: string) => {
  return request.get<RouterResponse<RuleDefinition>>(`/config/rules/${id}`)
}

export const createRule = (data: RuleDefinition) => {
  return request.post<RouterResponse<RuleDefinition>>('/config/rules', data)
}

export const updateRule = (id: string, data: RuleDefinition) => {
  return request.put<RouterResponse<RuleDefinition>>(`/config/rules/${id}`, data)
}

export const deleteRule = (id: string) => {
  return request.delete<RouterResponse<void>>(`/config/rules/${id}`)
}

export const enableRule = (id: string) => {
  return request.put<RouterResponse<RuleDefinition>>(`/config/rules/${id}/enable`)
}

export const disableRule = (id: string) => {
  return request.put<RouterResponse<RuleDefinition>>(`/config/rules/${id}/disable`)
}

export const updateRulePriorities = (items: PriorityUpdateItem[]) => {
  return request.put<RouterResponse<void>>('/config/rules/priority', items)
}

export const validateRule = (data: RuleValidateRequest) => {
  return request.post<RouterResponse<RuleValidateResult>>('/config/rules/validate', data)
}
