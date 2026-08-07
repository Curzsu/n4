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
  id: number | string
  deviceCode?: string
  path?: string
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

// ===== Device Template Types (device_template / point_template 表) =====

export interface DeviceTemplate {
  deviceTmplId?: number
  deviceTmplName: string
  deviceType: string
  model?: string
  manufacturer?: string
  manuDate?: string
  functionalArea?: string
  /** 该模板下的点位数量 */
  pointCount?: number
  /** 是否真实数据（与远程库模板一致） */
  isReal?: boolean
}

export interface PointTemplateItem {
  pointTmplId?: number
  deviceTmplId?: number
  pointName: string
  /** DI|DO|AI|AO */
  pointType?: string
  /** numeric|enum|boolean|string */
  dataType?: string
  unit?: string
  displayPrecision?: number
  isReal?: boolean
}

// ===== Dashboard Nav Types =====

export interface NavTreeNode {
  nodeId: number
  nodeName: string
  nodeLevel: number
  icon?: string
  sortOrder: number
  /** 一级节点允许的最大层级（2/3/...），子节点继承；未配置按两级 */
  maxLevel?: number
  /** 叶子分组节点的设备数；非叶子节点为 null */
  deviceCount?: number
  /** 叶子分组节点下的真实设备数（非 MOCK_ 前缀） */
  realDeviceCount?: number
  /** 该节点子树内是否含真实设备 */
  hasRealData?: boolean
  children: NavTreeNode[]
}

export interface DeviceDeployItem {
  deviceDeployId: number
  deviceDeployName: string
  deviceAlias: string | null
  status: number
  n4Address: string
  deviceType: string | null
  manufacturer: string | null
  model: string | null
  isMock: boolean
}

// ===== Device Deployment Types (device_deployment / point_deployment 表) =====

export interface DeviceDeployment {
  deviceDeployId?: number
  deviceTmplId?: number
  navNodeId?: number
  facePlateId?: string
  alarmCode?: string
  deviceDeployName: string
  deviceAlias?: string
  n4Address?: string
  ipAddress?: string
  macAddress?: string
  status?: number
  installDate?: string
  warrantyExpire?: string
  location?: string
  department?: string
  responsiblePerson?: string
  contactPhone?: string
  extraAttrs?: string
  deviceType?: string
  manufacturer?: string
  model?: string
  isMock?: boolean
}

export interface PointDeploymentItem {
  pointDeployId?: number
  pointTmplId?: number
  deviceDeployId?: number
  alarmCode?: string
  pointName: string
  n4Address?: string
  pointCode?: string
  register?: string
  pointType?: string
  dataType?: string
  unit?: string
  displayPrecision?: number
  isReal?: boolean
}

/** 删除导航节点时的影响预览 */
export interface DeleteImpact {
  /** 将被级联删除的二级子节点数 */
  childCount: number
  /** 将被解绑的设备数 */
  deviceCount: number
}

/** 新增/编辑节点表单数据 */
export interface NavNodeForm {
  nodeName: string
  parentId: number | null
  icon: string | null
  sortOrder: number
}

