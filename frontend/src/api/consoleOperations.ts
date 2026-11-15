import request from '@/utils/request'

/** 控制台管理操作记录（#1160：以变更审计为主表，追踪补耗时与状态） */
export interface ConsoleRecord {
  id?: number
  operation?: string | null
  path?: string | null
  operator?: string | null
  timestamp?: number | null
  status?: number | null
  duration?: number | null
  traceId?: string | null
}

export interface ConsoleRecordsResponse {
  success?: boolean
  data?: ConsoleRecord[]
}

export function getConsoleRecords(limit = 50) {
  return request.get('/tracing/query/console-records', { params: { limit } })
}
