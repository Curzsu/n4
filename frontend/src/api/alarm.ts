import request from './request'
import type {
  AlarmRecord,
  AlarmSummary,
  AlarmSeverityData,
  AlarmTrendData,
  AlarmHourData,
  AlarmSystemData,
  AlarmSourceData,
  BooleanRuleItem,
  NumericRuleItem,
  EnumRuleItem,
} from './types'

export const alarmApi = {
  getActiveAlarms(tab: string): Promise<AlarmRecord[]> {
    return request.get('/api/alarms/active', { params: { tab } })
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

  // ── Alarm rule CRUD (three type-specific tables) ──

  // Boolean
  getBooleanRules(): Promise<BooleanRuleItem[]> {
    return request.get('/api/alarm-rules/boolean')
  },
  createBooleanRule(data: BooleanRuleItem): Promise<BooleanRuleItem> {
    return request.post('/api/alarm-rules/boolean', data)
  },
  updateBooleanRule(id: number, data: BooleanRuleItem): Promise<BooleanRuleItem> {
    return request.put(`/api/alarm-rules/boolean/${id}`, data)
  },
  deleteBooleanRule(id: number): Promise<void> {
    return request.delete(`/api/alarm-rules/boolean/${id}`)
  },

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
