import request from '@/utils/request'

/**
 * 一条控制台写操作（#1154）。
 *
 * <p>时长来自追踪（内存），操作人来自审计（数据库），两边按 traceId 关联；
 * 关联不到时 operator 为 null，界面显示「未知」。</p>
 */
export interface ConsoleOperation {
  traceId: string
  operationName: string
  startTime: string
  duration: number
  hasError: boolean
  operator?: string | null
}

export interface ConsoleOperationsResponse {
  success: boolean
  data: ConsoleOperation[]
}

/**
 * 查询最近的控制台写操作。
 *
 * @param limit 返回条数，1-200
 */
export function getConsoleOperations(limit = 50): Promise<ConsoleOperationsResponse> {
  return request.get('/tracing/query/console-operations', { params: { limit } })
}
