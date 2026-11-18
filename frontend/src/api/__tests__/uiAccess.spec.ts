/**
 * 页面访问上报的三条纪律（B4，2026-11-18）。
 *
 * 这三条一旦被改坏，症状都很隐蔽：
 * ① 路径没洗干净 ⇒ 带 token 的查询串被写进审计表；
 * ② 节流丢了 ⇒ 用户在页面里点几下就把审计表刷满；
 * ③ 失败没有静默 ⇒ 上报接口抖动时，用户在页面上看到报错。
 */
import { beforeEach, describe, expect, it, vi } from 'vitest'

const requestMock = vi.hoisted(() => ({
  get: vi.fn(),
  post: vi.fn()
}))

vi.mock('@/utils/request', () => ({ default: requestMock }))

import {
  getPageViewSummary,
  normalizeReportPath,
  reportPageView,
  resetReportThrottle,
  shouldReport
} from '../uiAccess'

beforeEach(() => {
  requestMock.get.mockReset()
  requestMock.post.mockReset()
  resetReportThrottle()
})

describe('normalizeReportPath', () => {
  it('去掉查询串与 hash', () => {
    expect(normalizeReportPath('/call-history/list?page=2')).toBe('/call-history/list')
    expect(normalizeReportPath('/call-history/list#top')).toBe('/call-history/list')
  })

  it('去掉末尾斜杠，使同一页面只有一种标识', () => {
    expect(normalizeReportPath('/call-history/list/')).toBe('/call-history/list')
    expect(normalizeReportPath('/dashboard/main')).toBe('/dashboard/main')
  })

  it('登录页、根路径与非站内地址不上报', () => {
    expect(normalizeReportPath('/login')).toBeNull()
    expect(normalizeReportPath('/')).toBeNull()
    expect(normalizeReportPath('call-history/list')).toBeNull()
    expect(normalizeReportPath('')).toBeNull()
    expect(normalizeReportPath(undefined)).toBeNull()
    expect(normalizeReportPath(null)).toBeNull()
  })
})

describe('shouldReport 节流', () => {
  it('同一路径 60 秒内只放行一次', () => {
    const t0 = 1_000_000
    expect(shouldReport('/dashboard/main', t0)).toBe(true)
    expect(shouldReport('/dashboard/main', t0 + 59_000)).toBe(false)
    expect(shouldReport('/dashboard/main', t0 + 61_000)).toBe(true)
  })

  it('不同路径各自独立计数', () => {
    const t0 = 1_000_000
    expect(shouldReport('/a', t0)).toBe(true)
    expect(shouldReport('/b', t0)).toBe(true)
    expect(shouldReport('/a', t0 + 1_000)).toBe(false)
  })
})

describe('reportPageView', () => {
  it('清洗后上报 path 与 role', () => {
    requestMock.post.mockResolvedValue({ data: { success: true } })

    reportPageView('/system/page-heat?tab=x', 'AUDITOR')

    expect(requestMock.post).toHaveBeenCalledTimes(1)
    expect(requestMock.post).toHaveBeenCalledWith('/monitoring/ui-access', {
      path: '/system/page-heat',
      role: 'AUDITOR'
    })
  })

  it('节流期内重复访问不再上报', () => {
    requestMock.post.mockResolvedValue({ data: { success: true } })

    reportPageView('/dashboard/main', 'ADMIN')
    reportPageView('/dashboard/main', 'ADMIN')

    expect(requestMock.post).toHaveBeenCalledTimes(1)
  })

  it('登录页与未知路径不上报', () => {
    reportPageView('/login', 'ADMIN')
    reportPageView(null, 'ADMIN')

    expect(requestMock.post).not.toHaveBeenCalled()
  })

  it('角色缺失时上报 null（后端会归一为空）', () => {
    requestMock.post.mockResolvedValue({ data: { success: true } })

    reportPageView('/tools/client-access', null)

    expect(requestMock.post).toHaveBeenCalledWith('/monitoring/ui-access', {
      path: '/tools/client-access',
      role: null
    })
  })

  it('上报失败不抛出（静默）', async () => {
    requestMock.post.mockRejectedValue(new Error('network down'))

    expect(() => reportPageView('/dashboard/main', 'ADMIN')).not.toThrow()
    // 让 catch 回调跑完，确认没有未处理的 rejection
    await Promise.resolve()
  })
})

describe('getPageViewSummary', () => {
  it('解包 RouterResponse.data', async () => {
    requestMock.get.mockResolvedValue({
      data: { success: true, data: [{ path: '/dashboard/main', role: 'ADMIN', views: 3, users: 1 }] }
    })

    const rows = await getPageViewSummary(7, 50)

    expect(requestMock.get).toHaveBeenCalledWith('/monitoring/ui-access/summary', {
      params: { days: 7, limit: 50 }
    })
    expect(rows).toEqual([{ path: '/dashboard/main', role: 'ADMIN', views: 3, users: 1 }])
  })

  it('data 缺失时返回空数组而不是 undefined', async () => {
    requestMock.get.mockResolvedValue({ data: { success: true } })

    await expect(getPageViewSummary()).resolves.toEqual([])
  })
})
