import request from '@/api/request'
import type { LogQuery, OperationLog, PageResult } from '@/api/types'

export const logApi = {
  page(params: LogQuery) {
    return request.get<any, PageResult<OperationLog>>('/api/logs', { params })
  },
  detail(logId: number) {
    return request.get<any, OperationLog>(`/api/logs/${logId}`)
  },
  delete(ids: number[]) {
    return request.delete<any, void>('/api/logs', { data: { ids } })
  },
  clear() {
    return request.delete<any, void>('/api/logs/clear')
  },
}
