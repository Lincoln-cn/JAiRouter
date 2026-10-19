/**
 * 熔断器实例管理 API 契约
 *
 * 对应后端 `CircuitBreakerMonitorController` 的 `/instances` 三个端点。
 * 后者把「运行时熔断器快照」与「实例库元数据」合并返回，前端不再自行
 * 从 `Map<key, state>` 里猜 instanceId / 计数（那套解析在 UUID 键下必然错）。
 */
import { beforeEach, describe, expect, it, vi } from 'vitest'

const get = vi.fn()
const post = vi.fn()

vi.mock('@/utils/request', () => ({
  default: {
    get: (...args: unknown[]) => get(...(args as [])),
    post: (...args: unknown[]) => post(...(args as []))
  }
}))

import {
  getCircuitBreakerInstances,
  resetCircuitBreakerInstance,
  resetCircuitBreakerInstances
} from '@/api/circuitBreakerMonitor'

describe('circuitBreakerMonitor API client', () => {
  beforeEach(() => {
    get.mockReset()
    post.mockReset()
  })

  it('GET /v1/circuit-breaker-monitor/instances 返回数组（非 RouterResponse 包装）', async () => {
    const instances = [
      {
        instanceId: '2f1c9c1e-7f4e-4a1e-9d3f-6b1f0a2c3d4e',
        instanceName: 'gpt-4o-primary',
        serviceType: 'chat',
        baseUrl: 'https://api.example.com',
        state: 'OPEN',
        failureCount: 5,
        successCount: 0,
        failureThreshold: 5,
        successThreshold: 2,
        timeoutMillis: 60000,
        lastFailureTime: '2026-10-19T12:00:00Z'
      }
    ]
    get.mockResolvedValue({ data: instances })

    const response = await getCircuitBreakerInstances()

    expect(get).toHaveBeenCalledWith('/v1/circuit-breaker-monitor/instances')
    expect(response.data).toHaveLength(1)
    expect(response.data[0].state).toBe('OPEN')
    expect(response.data[0].failureThreshold).toBe(5)
  })

  it('POST /instances/{id}/reset 对 UUID 做 URL 编码且默认空 body', async () => {
    post.mockResolvedValue({ data: { success: true } })

    await resetCircuitBreakerInstance('2f1c9c1e-7f4e-4a1e-9d3f-6b1f0a2c3d4e')

    expect(post).toHaveBeenCalledWith(
      '/v1/circuit-breaker-monitor/instances/2f1c9c1e-7f4e-4a1e-9d3f-6b1f0a2c3d4e/reset',
      {}
    )
  })

  it('POST /instances/{id}/reset 透传可选 reason', async () => {
    post.mockResolvedValue({ data: { success: true } })

    await resetCircuitBreakerInstance('uuid-1', '手工恢复')

    expect(post).toHaveBeenCalledWith(
      '/v1/circuit-breaker-monitor/instances/uuid-1/reset',
      { reason: '手工恢复' }
    )
  })

  it('POST /instances/reset 提交筛选范围（serviceType + states）', async () => {
    post.mockResolvedValue({ data: { success: true, data: { matched: 3, reset: [] } } })

    await resetCircuitBreakerInstances({ serviceType: 'chat', states: ['OPEN'] })

    expect(post).toHaveBeenCalledWith('/v1/circuit-breaker-monitor/instances/reset', {
      serviceType: 'chat',
      states: ['OPEN']
    })
  })

  it('POST /instances/reset 提交选中实例 ID 列表', async () => {
    post.mockResolvedValue({ data: { success: true, data: { matched: 2, reset: [] } } })

    await resetCircuitBreakerInstances({ instanceIds: ['u1', 'u2'] })

    expect(post).toHaveBeenCalledWith('/v1/circuit-breaker-monitor/instances/reset', {
      instanceIds: ['u1', 'u2']
    })
  })
})
