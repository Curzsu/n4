import { defineStore } from 'pinia'
import { ref } from 'vue'
import { dashboardApi } from '@/api/dashboard'
import { deviceApi } from '@/api/device'
import type { KpiMetrics, AlarmSummary, AlarmItem, SystemStatusItem, TrendData, DeviceTreeNode } from '@/api/types'

export const useDashboardStore = defineStore('dashboard', () => {
  const kpiMetrics = ref<KpiMetrics | null>(null)
  const alarmSummary = ref<AlarmSummary | null>(null)
  const alarmTop5 = ref<AlarmItem[]>([])
  const systemStatus = ref<SystemStatusItem[]>([])
  const powerTrend = ref<TrendData | null>(null)
  const energyTrend = ref<TrendData | null>(null)
  const pueTrend = ref<TrendData | null>(null)
  const temperatureTrend = ref<TrendData | null>(null)
  const deviceTree = ref<DeviceTreeNode[]>([])
  const loading = ref(false)

  async function fetchAllDashboardData() {
    loading.value = true
    try {
      const [kpi, alarm, top5, status, power, energy, pue, temp, tree] = await Promise.all([
        dashboardApi.getKpi(),
        dashboardApi.getAlarmSummary(),
        dashboardApi.getAlarmTop5(),
        dashboardApi.getSystemStatus(),
        dashboardApi.getPowerTrend(),
        dashboardApi.getEnergyTrend(),
        dashboardApi.getPueTrend(),
        dashboardApi.getTemperatureTrend(),
        deviceApi.getDeviceTree(),
      ])
      kpiMetrics.value = kpi
      alarmSummary.value = alarm
      alarmTop5.value = top5
      systemStatus.value = status
      powerTrend.value = power
      energyTrend.value = energy
      pueTrend.value = pue
      temperatureTrend.value = temp
      deviceTree.value = tree
    } catch (error) {
      console.error('Failed to fetch dashboard data:', error)
    } finally {
      loading.value = false
    }
  }

  return {
    kpiMetrics,
    alarmSummary,
    alarmTop5,
    systemStatus,
    powerTrend,
    energyTrend,
    pueTrend,
    temperatureTrend,
    deviceTree,
    loading,
    fetchAllDashboardData,
  }
})
