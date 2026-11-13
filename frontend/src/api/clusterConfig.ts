import request from '@/utils/request'
import type { RouterResponse } from '@/types'

/**
 * 集群设置 API 封装
 *
 * 对应后端：
 * - GET  /api/config/cluster   获取集群运行态与配置同步快照
 * - PUT  /api/config/cluster   热改配置同步参数（仅 hot-editable 字段）
 *
 * 页面用途：多副本部署时「配置变更如何传播到其它副本」的可视化与热调参。
 * 单机部署（mode=standalone）下同样可访问，用于查看判定依据。
 */

/** 配置同步运行态 */
export interface ClusterConfigSync {
  /** 是否启用版本轮询（关闭后只有事件总线仍可触发同步，且需同时开启事件加速） */
  enabled: boolean
  /** 版本轮询间隔（毫秒） */
  pollIntervalMs: number
  /** 是否启用 Redis 事件总线加速（配置保存后即时通知其它副本） */
  eventBusEnabled: boolean
  /** 本副本已应用的配置版本（-1 = 尚未建立基线） */
  appliedConfigVersion: number
  /** 本副本已应用的 API Key 配置版本（-1 = 尚未建立基线） */
  appliedApiKeyVersion: number
}

/** 上游健康共享运行态（#1137） */
export interface ClusterHealthSharing {
  /** 是否启用（关闭时判定完全沿用本副本探测结果） */
  enabled: boolean
  /** 判定语义：local / shared / quorum */
  mode: 'local' | 'shared' | 'quorum'
  /** quorum 语义下认为健康所需的最小副本数 */
  toleranceCount: number
  /** 共享上报有效期（秒） */
  graceSeconds: number
  /** 当前是否真正生效（开关 + 集群模式 + Redis 可用） */
  active: boolean
  /** 共享视图里已跟踪的实例数 */
  trackedInstances: number
  /** 最近一次拉取共享视图的时间戳（毫秒，0 = 尚未拉取） */
  lastRefreshAt: number
  /** 本副本的上报标识 */
  reporterId: string
}

export interface ClusterConfig {
  /** cluster = 多副本形态；standalone = 单机形态 */
  mode: 'cluster' | 'standalone'
  /** 副本数（来自 jairouter.cluster.replicas，0 表示未显式配置） */
  replicas: number
  /** Redis 事件总线当前是否可用（装配期决定） */
  clusterEventsEnabled: boolean
  configSync: ClusterConfigSync
  healthSharing: ClusterHealthSharing
  /** 可热改字段路径清单 */
  hotEditableFields: string[]
  /** 需重启才生效的字段路径清单 */
  restartRequiredFields: string[]
}

/** 热改请求体（全部可空，null/省略 = 不改） */
export interface ClusterConfigPayload {
  configSyncEnabled?: boolean | null
  configSyncPollIntervalMs?: number | null
  configSyncEventBusEnabled?: boolean | null
  healthSharingEnabled?: boolean | null
  healthSharingMode?: 'local' | 'shared' | 'quorum' | null
  healthSharingToleranceCount?: number | null
  healthSharingGraceSeconds?: number | null
}

/**
 * 获取集群设置快照
 * GET /api/config/cluster → RouterResponse<ClusterConfig>
 */
export const getClusterConfig = async (): Promise<ClusterConfig | null> => {
  try {
    const response = await request.get<RouterResponse<ClusterConfig>>('/config/cluster')
    return response.data.data ?? null
  } catch {
    return null
  }
}

/**
 * 热改配置同步参数
 * PUT /api/config/cluster
 * 400 时抛出 AxiosError，调用方需从 error.response.data 提取后端 message。
 */
export const updateClusterConfig = async (
  payload: ClusterConfigPayload
): Promise<ClusterConfig> => {
  const response = await request.put<RouterResponse<ClusterConfig>>('/config/cluster', payload)
  return response.data.data as ClusterConfig
}

/** 一个集群副本的上报快照（#1136） */
export interface ClusterMember {
  /** 副本标识 */
  instanceId: string
  /** 展示名（K8s 取 POD_NAME，裸机取 hostname） */
  displayName: string
  /** 对外可访问地址 */
  advertiseUrl: string
  /** 进程启动时间（epoch 毫秒） */
  startedAt: number
  /** 本副本已应用的配置版本（-1 = 尚未建立基线） */
  configVersion: number
  /** 本副本注册的下游实例总数 */
  instanceCount: number
  /** 其中被判定为健康的实例数 */
  healthyInstances: number
  /** 应用版本号 */
  buildVersion: string
  /** 最近一次心跳时间（epoch 毫秒） */
  lastSeen: number
}

/** 集群状态汇总 */
export interface ClusterStatus {
  /** cluster = 多副本形态；standalone = 单机形态 */
  mode: 'cluster' | 'standalone'
  /** 判定依据（如 auto:replicas=2 或 explicit:cluster） */
  modeSource: string
  /** 本副本快照 */
  self: ClusterMember
  /** 成员表中最大的配置版本（-1 = 无成员） */
  clusterConfigVersion: number
  summary: {
    total: number
    online: number
    suspect: number
    staleReplicas: number
  }
  members: ClusterMember[]
}

/**
 * 获取集群成员状态
 * GET /api/cluster/status → RouterResponse<ClusterStatus>
 */
export const getClusterStatus = async (): Promise<ClusterStatus | null> => {
  try {
    const response = await request.get<RouterResponse<ClusterStatus>>('/cluster/status')
    return response.data.data ?? null
  } catch {
    return null
  }
}
