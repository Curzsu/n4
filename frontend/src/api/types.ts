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
  deviceCode?: string
  label: string
  type: string
  status: number
  children: DeviceTreeNode[]
}

export interface FuxaBinding {
  deviceId?: number
  deviceCode?: string
  mqttDeviceId: string
  modelCode: string
  fuxaDeviceId: string
  fuxaDeviceName: string
  viewName: string
  iframeUrl: string
  status: 'READY' | 'ERROR'
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

// ===== Alarm Page Types =====

export interface AlarmRecord {
  id: string
  time: string
  severity: string
  source: string
  message: string
  location: string
  status: string
  ackBy: string
  duration: string
  stage?: string
  stageMessage?: string
  path?: string
}

export interface SeverityItem {
  name: string
  value: number
  percentage: number
}

export interface AlarmSeverityData {
  total: number
  items: SeverityItem[]
}

export interface TrendSeries {
  name: string
  data: number[]
}

export interface AlarmTrendData {
  timeLabels: string[]
  series: TrendSeries[]
}

export interface AlarmHourData {
  hours: string[]
  counts: number[]
}

export interface SystemItem {
  name: string
  value: number
  percentage: number
}

export interface AlarmSystemData {
  items: SystemItem[]
}

export interface SourceItem {
  name: string
  count: number
}

export interface AlarmSourceData {
  items: SourceItem[]
}

// ===== Alarm Rules (two type-specific tables: numeric + enum) =====

export interface NumericRuleItem {
  id?: number
  alarmName: string
  pointName?: string
  pointType?: string
  timeDelay?: number | null
  deadband?: string
  highHighValue?: string
  highHighSeverity?: string
  highHighMessage?: string
  highValue?: string
  highSeverity?: string
  highMessage?: string
  lowValue?: string
  lowSeverity?: string
  lowMessage?: string
  lowLowValue?: string
  lowLowSeverity?: string
  lowLowMessage?: string
  createTime?: string
  updateTime?: string
}

export interface EnumRuleItem {
  id?: number
  alarmName: string
  pointName?: string
  pointType?: string
  timeDelay?: number | null
  enumValue: string
  enumMeaning?: string
  severity?: string
  message?: string
  createTime?: string
  updateTime?: string
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


