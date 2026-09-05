import axios from 'axios'
import type { AxiosResponse } from 'axios'
import request from './request'
import type {
  AlarmRecord,
  AlarmSummary,
  AlarmSeverityData,
  AlarmTrendData,
  AlarmHourData,
  AlarmSystemData,
  AlarmSourceData,
  NumericRuleItem,
  EnumRuleItem,
} from './types'

export const alarmApi = {
  getActiveAlarms(tab: string): Promise<AlarmRecord[]> {
    return request.get('/api/alarms/active', { params: { tab } })
  },
  getClearedAlarms(params?: { topTs?: number; offset?: number; limit?: number }): Promise<AlarmRecord[]> {
    return request.get('/api/alarms/cleared', { params })
  },
  getAlarmEvents(params?: { topTs?: number; offset?: number; limit?: number }): Promise<AlarmRecord[]> {
    return request.get('/api/alarms/events', { params })
  },
  getAlarmSummary(): Promise<AlarmSummary> {
    return request.get('/api/alarms/summary')
  },
  getAlarmBySeverity(): Promise<AlarmSeverityData> {
    return request.get('/api/alarms/by-severity')
  },
  getAlarmTrend(): Promise<AlarmTrendData> {
    return request.get('/api/alarms/trend')
  },
  getAlarmByHour(): Promise<AlarmHourData> {
    return request.get('/api/alarms/by-hour')
  },
  getAlarmBySystem(): Promise<AlarmSystemData> {
    return request.get('/api/alarms/by-system')
  },
  getAlarmTopSources(): Promise<AlarmSourceData> {
    return request.get('/api/alarms/top-sources')
  },
  ackAlarms(paths: string[], ackBy: string): Promise<number> {
    return request.post('/api/alarms/ack', { paths, ackBy })
  },

  // ── Alarm rule CRUD (two type-specific tables: numeric + enum) ──

  // Numeric
  getNumericRules(): Promise<NumericRuleItem[]> {
    return request.get('/api/alarm-rules/numeric')
  },
  createNumericRule(data: NumericRuleItem): Promise<NumericRuleItem> {
    return request.post('/api/alarm-rules/numeric', data)
  },
  updateNumericRule(id: number, data: NumericRuleItem): Promise<NumericRuleItem> {
    return request.put(`/api/alarm-rules/numeric/${id}`, data)
  },
  deleteNumericRule(id: number): Promise<void> {
    return request.delete(`/api/alarm-rules/numeric/${id}`)
  },

  // Enum
  getEnumRules(): Promise<EnumRuleItem[]> {
    return request.get('/api/alarm-rules/enum')
  },
  createEnumRules(data: EnumRuleItem[]): Promise<EnumRuleItem[]> {
    return request.post('/api/alarm-rules/enum', data)
  },
  updateEnumRules(alarmName: string, data: EnumRuleItem[]): Promise<EnumRuleItem[]> {
    return request.put(`/api/alarm-rules/enum/${encodeURIComponent(alarmName)}`, data)
  },
  deleteEnumRules(alarmName: string): Promise<void> {
    return request.delete(`/api/alarm-rules/enum/${encodeURIComponent(alarmName)}`)
  },
}

// ── Event page Excel export ──────────────────────────────────────────────

export interface EventExportParams {
  /** Range start (inclusive), epoch millis, compared against record (write) time. */
  start: number
  /** Range end (inclusive), epoch millis, compared against record (write) time. */
  end: number
  /** Optional severity filter, e.g. ['Critical', 'Major']; empty/undefined = all severities. */
  severity?: string[]
  /** When true (default) only alarm frames are exported; false exports every stored frame. */
  alarmOnly?: boolean
}

export interface EventExportResult {
  /** The xlsx file content. */
  blob: Blob
  /** Data row count reported by the backend via the X-Export-Rows header; null when absent. */
  rows: number | null
  /** Download file name (from Content-Disposition, or a client-side fallback). */
  fileName: string
}

/**
 * Download the event export as an xlsx file. Uses a dedicated axios call (not
 * the shared `request` instance): the response is a file stream instead of the
 * standard ApiResponse JSON, and a large export can outlive the shared
 * instance's 10 second timeout.
 * <p>
 * Business errors arrive as HTTP 200 with an ApiResponse JSON body (the
 * project-wide convention - the global exception handler does not set 40x
 * statuses), so the success path must check the content type before treating
 * the blob as a workbook, otherwise the error JSON would be saved as a
 * corrupt .xlsx.
 */
export async function exportAlarmEvents(params: EventExportParams): Promise<EventExportResult> {
  let response: AxiosResponse<Blob>
  try {
    response = await axios.get('/api/alarms/events/export', {
      params: {
        start: params.start,
        end: params.end,
        // Spring binds a comma-separated value into List<String>; the
        // whitelisted severity values never contain commas.
        severity: params.severity && params.severity.length > 0 ? params.severity.join(',') : undefined,
        alarmOnly: params.alarmOnly,
      },
      responseType: 'blob',
      timeout: 300_000,
    })
  } catch (err) {
    throw new Error(await extractExportErrorMessage(err))
  }
  await assertNotJsonError(response)
  return {
    blob: response.data,
    rows: parseRowsHeader(response.headers['x-export-rows']),
    fileName: extractFileName(response) ?? buildDefaultExportFileName(),
  }
}

/**
 * Surface business errors that ride on HTTP 200: when the blob is the
 * ApiResponse JSON instead of a workbook, throw its message.
 */
async function assertNotJsonError(response: AxiosResponse<Blob>): Promise<void> {
  const contentType = String(response.headers['content-type'] ?? '')
  if (!contentType.includes('application/json')) {
    return
  }
  let message = 'Export failed'
  try {
    const parsed = JSON.parse(await response.data.text()) as { code?: number; message?: string }
    if (parsed?.message) {
      message = parsed.message
    }
  } catch {
    // Unparseable body - keep the generic message.
  }
  throw new Error(message)
}

/** Parse the backend's error JSON out of a blob-wrapped error response. */
async function extractExportErrorMessage(err: unknown): Promise<string> {
  const response = (err as { response?: { status?: number; data?: unknown } })?.response
  if (response?.data instanceof Blob) {
    try {
      const parsed = JSON.parse(await response.data.text()) as { message?: string }
      if (parsed && parsed.message) {
        return parsed.message
      }
    } catch {
      // Not JSON - fall through to the status-based message.
    }
  }
  if (response?.status) {
    return `Export failed (HTTP ${response.status})`
  }
  return 'Network connection error, please check backend service'
}

function parseRowsHeader(raw: unknown): number | null {
  const value = Number(raw)
  return raw != null && !Number.isNaN(value) ? value : null
}

/** Pull the quoted file name out of the Content-Disposition header. */
function extractFileName(response: AxiosResponse<Blob>): string | null {
  const disposition = (response.headers['content-disposition'] as string | undefined) ?? ''
  const match = /filename="?([^";]+)"?/i.exec(disposition)
  return match ? match[1] : null
}

function buildDefaultExportFileName(): string {
  const now = new Date()
  const pad = (n: number) => String(n).padStart(2, '0')
  return `n4_events_${now.getFullYear()}${pad(now.getMonth() + 1)}${pad(now.getDate())}_`
      + `${pad(now.getHours())}${pad(now.getMinutes())}${pad(now.getSeconds())}.xlsx`
}
