import request from './request'

export type Dataset = 'active_alarms' | 'alarm_events' | 'deployments'

export interface DataAnswer {
  answer: string
  query: {
    dataset: Dataset
    operation: 'count' | 'group' | 'list'
    groupBy: string | null
    severity: string | null
    status: string | null
    searchField: string | null
    search: string | null
    startDate: string | null
    endDate: string | null
  }
  evidence: {
    matchedCount: number
    columns: string[]
    rows: Record<string, string | number>[]
    truncated: boolean
  }
  execution?: {
    presetQuery: boolean
    planningMillis: number
    queryMillis: number
    summaryMillis: number
    summaryGenerated: boolean
    dataRead?: {
      source: string
      sql: string | null
      postProcessed: boolean
    }
  }
}

export function askData(text: string, context: string[]): Promise<DataAnswer> {
  return request.post('/api/data-assistant/ask', { text, context }, { timeout: 65000 })
}
