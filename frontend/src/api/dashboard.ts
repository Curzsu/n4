import request from './request'
import type { KpiMetrics, AlarmSummary, AlarmItem, SystemStatusItem, TrendData } from './types'

export const dashboardApi = {
  getKpi(): Promise<KpiMetrics> {
    return request.get('/api/dashboard/kpi')
  },
  getAlarmSummary(): Promise<AlarmSummary> {
    return request.get('/api/dashboard/alarm-summary')
  },
  getAlarmTop5(): Promise<AlarmItem[]> {
    return request.get('/api/dashboard/alarm-top5')
  },
  getSystemStatus(): Promise<SystemStatusItem[]> {
    return request.get('/api/dashboard/system-status')
  },
  getPowerTrend(): Promise<TrendData> {
    return request.get('/api/dashboard/power-trend')
  },
  getEnergyTrend(): Promise<TrendData> {
    return request.get('/api/dashboard/energy-trend')
  },
  getPueTrend(): Promise<TrendData> {
    return request.get('/api/dashboard/pue-trend')
  },
  getTemperatureTrend(): Promise<TrendData> {
    return request.get('/api/dashboard/temperature-trend')
  },
}
