import request from './request'
import type {
  AlarmRecord,
  AlarmSummary,
  AlarmSeverityData,
  AlarmTrendData,
  AlarmHourData,
  AlarmSystemData,
  AlarmSourceData,
  AlarmRuleItem,
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

  // ── Alarm rule CRUD (alarm_rule table) ──
  getAlarmRules(): Promise<AlarmRuleItem[]> {
    return request.get('/api/alarm-rules')
  },
  createAlarmRule(data: AlarmRuleItem): Promise<AlarmRuleItem> {
    return request.post('/api/alarm-rules', data)
  },
  updateAlarmRule(id: number, data: AlarmRuleItem): Promise<AlarmRuleItem> {
    return request.put(`/api/alarm-rules/${id}`, data)
  },
  deleteAlarmRule(id: number): Promise<void> {
    return request.delete(`/api/alarm-rules/${id}`)
  },
}
