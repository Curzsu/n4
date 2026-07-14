import request from './request'
import type {
  AlarmRecord,
  AlarmSummary,
  AlarmSeverityData,
  AlarmTrendData,
  AlarmHourData,
  AlarmSystemData,
  AlarmSourceData,
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
}
