import request from '@/utils/request'
import type { RouterResponse } from '@/types'

/**
 * 权限管理 API 封装（v2.9.8 RBAC，Phase 4）
 *
 * 对应后端 PermissionManagementController：
 * - GET /api/security/permissions                全部权限码（45 码，ADMIN 超集）
 * - GET /api/security/permissions/roles          全部角色及其权限码（角色名 → 权限码列表）
 * - PUT /api/security/permissions/roles/{roleName} 整体替换角色权限码集合
 */

export interface PermissionGroup {
  /** 模块展示分组标题（i18n key，页面以 t(group.module) 渲染） */
  module: string
  /** 该模块下的权限码 */
  codes: string[]
}

/** 4 个种子角色（RolePermissionSeeder 模板） */
export const ROLES = ['ADMIN', 'OPERATOR', 'USER', 'VIEWER'] as const
export type RoleName = (typeof ROLES)[number]

/**
 * 角色说明 i18n key（UI 展示用；页面以 t(ROLE_DESCRIPTIONS[role]) 渲染文案）。
 * 文案位于 locales/{zh-CN,en-US}/permissions.json 的 roleDescriptions 节点。
 */
export const ROLE_DESCRIPTIONS: Record<RoleName, string> = {
  ADMIN: 'permissions.roleDescriptions.ADMIN',
  OPERATOR: 'permissions.roleDescriptions.OPERATOR',
  USER: 'permissions.roleDescriptions.USER',
  VIEWER: 'permissions.roleDescriptions.VIEWER'
}

/**
 * 45 权限码按模块展示分组（与后端 PermissionCodes 全量一致）。
 * 仅用于 UI 展示（权限树分组），不代表后端授权语义。
 * module 为分组标题的 i18n key（页面以 t(group.module) 渲染），
 * 文案位于 locales/{zh-CN,en-US}/permissions.json 的 groups 节点。
 */
export const PERMISSION_GROUPS: PermissionGroup[] = [
  { module: 'permissions.groups.overview', codes: ['overview:dashboard:read'] },
  {
    module: 'permissions.groups.config',
    codes: [
      'config:services:read', 'config:services:write',
      'config:instances:read', 'config:instances:write',
      'config:versions:read', 'config:versions:write',
      'config:persistence:read', 'config:persistence:write',
      'config:adapters:read', 'config:adapters:write',
      'config:rules:read', 'config:rules:write',
      'config:pools:read', 'config:pools:write',
      'config:circuitbreaker:read', 'config:circuitbreaker:write',
      'config:callhistory:read', 'config:callhistory:write',
      'config:cache:read', 'config:cache:write',
      'config:validation:read', 'config:validation:write'
    ]
  },
  {
    module: 'permissions.groups.trafficGovernance',
    codes: [
      'lb:monitoring:read', 'lb:config:write',
      'cb:monitoring:read', 'cb:history:read',
      'rl:monitoring:read'
    ]
  },
  { module: 'permissions.groups.callHistory', codes: ['callhistory:view'] },
  {
    module: 'permissions.groups.monitoring',
    codes: [
      'monitoring:metrics:read', 'monitoring:slowquery:read',
      'monitoring:tokenusage:read', 'monitoring:modelstats:read',
      'monitoring:routing:read'
    ]
  },
  {
    module: 'permissions.groups.tracing',
    codes: [
      'tracing:dashboard:read', 'tracing:search:read', 'tracing:config:manage'
    ]
  },
  {
    module: 'permissions.groups.security',
    codes: [
      'security:apikeys:manage', 'security:jwttokens:manage',
      'security:blacklist:manage', 'security:audit:read'
    ]
  },
  {
    module: 'permissions.groups.system',
    codes: [
      'system:accounts:manage', 'system:permissions:manage'
    ]
  },
  { module: 'permissions.groups.ai', codes: ['ai:playground:use'] },
  { module: 'permissions.groups.infrastructure', codes: ['actuator:admin:manage'] }
]

/** 全部权限码集合（叶子节点 key，用于 el-tree 勾选收集/校验） */
export const ALL_PERMISSION_CODES: string[] = PERMISSION_GROUPS.flatMap(group => group.codes)

/** 角色 → 权限码列表（GET /api/security/permissions/roles 返回结构） */
export interface RolePermissions {
  [roleName: string]: string[]
}

/** 获取全部权限码 */
export const getAllPermissions = async (): Promise<string[]> => {
  const response = await request.get<RouterResponse<string[]>>('/security/permissions')
  return response.data.data || []
}

/** 获取全部角色及其权限码 */
export const getRolePermissions = async (): Promise<RolePermissions> => {
  const response = await request.get<RouterResponse<RolePermissions>>('/security/permissions/roles')
  return response.data.data || {}
}

/** 更新角色权限码集合（整体替换；权限变更需重新登录生效） */
export const updateRolePermissions = async (roleName: string, permissionCodes: string[]): Promise<string[]> => {
  const response = await request.put<RouterResponse<string[]>>(
    `/security/permissions/roles/${roleName}`,
    permissionCodes
  )
  return response.data.data || []
}
