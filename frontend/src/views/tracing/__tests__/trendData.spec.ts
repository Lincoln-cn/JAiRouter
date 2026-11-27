import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'
import { errorTrendPoints } from '../trendData'
import type { ErrorAnalysisAggregated } from '@/api/tracing'
import type { TrendPoint } from '@/types'

const points: TrendPoint[] = [
  { hour: '2026-11-27 10:00', requestCount: 40, errorCount: 3 },
  { hour: '2026-11-27 11:00', requestCount: 55, errorCount: 0 }
]

describe('errorTrendPoints', () => {
  // 回归用例（#1234）：这两张图曾读一个从未赋值的 `errorAnalysis` ref，
  // 后端有数据、前端永远画零线。下面三条守住「只认聚合响应里的 errorTrend」。
  it('取聚合响应里的 errorTrend', () => {
    const aggregated = { errorTrend: points } as unknown as ErrorAnalysisAggregated
    expect(errorTrendPoints(aggregated)).toEqual(points)
  })

  it('响应缺失时返回空数组而不是抛错或 undefined', () => {
    expect(errorTrendPoints(null)).toEqual([])
    expect(errorTrendPoints(undefined)).toEqual([])
  })

  it('响应里没有 errorTrend 字段时返回空数组', () => {
    expect(errorTrendPoints({} as ErrorAnalysisAggregated)).toEqual([])
  })
})

describe('Dashboard 的错误趋势数据来源', () => {
  const source = readFileSync(resolve(process.cwd(), 'src/views/tracing/Dashboard.vue'), 'utf-8')

  it('不再出现从未赋值的 errorAnalysis ref', () => {
    expect(source).not.toContain('errorAnalysis.value')
    expect(source).not.toMatch(/const\s+errorAnalysis\s*=/)
  })

  it('两张图与空状态判断都走同一个取数函数', () => {
    const calls = source.match(/errorTrendPoints\(errorAggregated\.value\)/g) ?? []
    // 空状态 + 追踪量趋势 + 错误率趋势
    expect(calls.length).toBe(3)
  })
})
