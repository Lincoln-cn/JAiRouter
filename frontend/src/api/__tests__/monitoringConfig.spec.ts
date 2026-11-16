/**
 * 监控配置 API 封装的回归测试（决策 D5：契约不变，前端解析/拼装 Duration）。
 *
 * <p>两类断言：
 * ① Duration 的解析/拼装是纯函数，最容易在后续重构里被改坏；
 * ② 端点常量必须落在 `/monitoring/config/*` 前缀下 —— 误写到 `/actuator/metrics/*`
 * 时 ADMIN 仍能跑通、OPERATOR 会 403，属于「本地测不出、运维才撞上」的错误。
 */
import { beforeEach, describe, expect, it, vi } from 'vitest'

const requestMock = vi.hoisted(() => ({
  get: vi.fn(),
  put: vi.fn(),
  post: vi.fn()
}))

vi.mock('@/utils/request', () => ({ default: requestMock }))

import {
  forceMonitoringRecovery,
  formatDurationSeconds,
  getMonitoringConfig,
  parseDurationSeconds,
  updateMonitoringPerformance,
  updateMonitoringPrefix,
  updateMonitoringSampling
} from '../monitoringConfig'

beforeEach(() => {
  requestMock.get.mockReset()
  requestMock.put.mockReset()
  requestMock.post.mockReset()
  requestMock.get.mockResolvedValue({ data: { success: true, data: null } })
  requestMock.put.mockResolvedValue({ data: { success: true, data: null } })
  requestMock.post.mockResolvedValue({ data: { success: true, data: null } })
})

describe('parseDurationSeconds', () => {
  it('解析后端 Duration.toString() 的常见形态', () => {
    expect(parseDurationSeconds('PT10S')).toBe(10)
    expect(parseDurationSeconds('PT1M30S')).toBe(90)
    expect(parseDurationSeconds('PT2H')).toBe(7200)
    expect(parseDurationSeconds('PT1H2M3S')).toBe(3723)
    expect(parseDurationSeconds('PT0S')).toBe(0)
    expect(parseDurationSeconds('PT1.5S')).toBe(1.5)
  })

  it('无分量、非 ISO-8601、空值一律返回 null（页面按「未提供」处理，不写坏值）', () => {
    expect(parseDurationSeconds('PT')).toBeNull()
    expect(parseDurationSeconds('10')).toBeNull()
    expect(parseDurationSeconds('')).toBeNull()
    expect(parseDurationSeconds('  ')).toBeNull()
  })
})

describe('formatDurationSeconds', () => {
  it('按 PT{n}S 拼装，并与解析器互逆', () => {
    expect(formatDurationSeconds(90)).toBe('PT90S')
    expect(formatDurationSeconds(0)).toBe('PT0S')
    expect(parseDurationSeconds(formatDurationSeconds(125))).toBe(125)
  })
})

describe('端点常量', () => {
  it('读配置走 /monitoring/config', async () => {
    await getMonitoringConfig()
    expect(requestMock.get).toHaveBeenCalledWith('/monitoring/config')
  })

  it('写端点落在 /monitoring/config/* 下（不能复用 /actuator/metrics/*）', async () => {
    await updateMonitoringPrefix('metrics')
    await updateMonitoringSampling({ requestMetrics: 0.5 })
    await updateMonitoringPerformance({ batchSize: 200 })
    expect(requestMock.put.mock.calls.map(call => call[0])).toEqual([
      '/monitoring/config/prefix',
      '/monitoring/config/sampling',
      '/monitoring/config/performance'
    ])
    expect(requestMock.put.mock.calls[1][1]).toEqual({ requestMetrics: 0.5 })
  })

  it('应急操作走 /monitoring/* 写端点', async () => {
    await forceMonitoringRecovery()
    expect(requestMock.post).toHaveBeenCalledWith('/monitoring/degradation/force-recovery')
  })
})
