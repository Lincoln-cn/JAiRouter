import type { TrendPoint } from '@/types'
import type { ErrorAnalysisAggregated } from '@/api/tracing'

/**
 * 错误趋势的取数口径（#1234）。
 *
 * <p>Dashboard 的「追踪量趋势」与「错误率趋势」两张图都画错误数/错误率，两者的数据来自
 * 同一个响应：`/tracing/query/error-analysis` 的 {@link ErrorAnalysisAggregated#errorTrend}。
 * 修复前它们读的是一个从未被赋值的 `errorAnalysis` ref，于是曲线永远贴在零线 ——
 * 后端有数据、前端读错变量（那条 ref 的来源端点已随本轮的清理下线）。</p>
 *
 * <p>收敛成一个函数是为了让「数据来源」成为可断言的东西：改回另一个 ref 会让单测变红。</p>
 */
export function errorTrendPoints(aggregated: ErrorAnalysisAggregated | null | undefined): TrendPoint[] {
  return aggregated?.errorTrend ?? []
}
