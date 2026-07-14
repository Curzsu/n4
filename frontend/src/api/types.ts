export interface ApiResponse<T> {
  code: number
  message: string
  data: T
}

export interface KpiMetrics {
  pue: number
  pueTrend: string
  itLoadPercent: number
  itLoadKw: number
  itLoadCapacityKw: number
  coolingLoadPercent: number
  coolingLoadKw: number
  coolingLoadCapacityKw: number
  facilityLoadPercent: number
  facilityLoadKw: number
  facilityLoadCapacityKw: number
  totalEnergyKwh: number
  capacityUsagePercent: number
  activeDevices: number
  totalDevices: number
}

export interface AlarmSummary {
  critical: number
  major: number
  minor: number
  warning: number
}

export interface AlarmItem {
  alarmTime: string
  alarmDesc: string
  alarmLevel: string
  sourceDevice: string
}

export interface SystemStatusItem {
  systemName: string
  status: string
  deviceCount: number
  icon: string
}

export interface TrendData {
  timeLabels: string[]
  values: number[]
  values2?: number[]
  seriesName: string
  seriesName2?: string
  unit: string
}

export interface DeviceTreeNode {
  id: number
  label: string
  type: string
  status: number
  children: DeviceTreeNode[]
}

export interface MonitorPoint {
  pointId: number
  registerId: string
  pointName: string
  pointCode: string
  pointType: number
  dataType: number
  unit: string
  displayPrecision: number
  deviceId: number
  pointValue: string
  pointStatus: number
}

export interface Product {
  productId?: number
  productName: string
  productType: string
  model?: string
  manufacturer?: string
  manuDate?: string
  isDeleted?: boolean
}


