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

/**
 * UI tree node rendered in the sidebar: the NavTreeNode transform plus the
 * synthetic "overview" node built in LeftSidebar.
 */
export interface UiTreeNode {
  /** Absent on synthetic highlight-only nodes. */
  id?: number | string
  label: string
  type: string
  status?: number
  nodeLevel?: number
  hasRealData?: boolean
  raw?: NavTreeNode | null
  children: UiTreeNode[]
  deviceCode?: string
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

export interface FuxaCabinSession {
  cabinName: string
  sessionId?: string
  fuxaDeviceId: string
  fuxaDeviceName: string
  viewName: string
  iframeUrl: string
  viewWidth?: number
  viewHeight?: number
  enabled: boolean
  sessionActive: boolean
  activeSessionCount: number
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
  /** Raw epoch-millis timestamp, kept for sub-second sorting precision.
   *  Absent on records loaded from the API (which only carry the formatted
   *  "yyyy-MM-dd HH:mm:ss" string); present on live WebSocket records. */
  timestampMs?: number
  value?: string
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
  /** Raw write timestamp (epoch millis) from the events API; cursor for scroll pagination. */
  ts?: number
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

// ===== Device Template Types (device_template / point_template tables) =====

export interface DeviceTemplate {
  deviceTmplId?: number
  deviceTmplName: string
  n4Name?: string
  deviceType: string
  model?: string
  purpose?: string
  manufacturer?: string
  manuDate?: string
  functionalArea?: string
  facePlateId?: string
  alarmCode?: string
  /** Point count under this template */
  pointCount?: number
  /** Whether matched real remote template */
  isReal?: boolean
}

export interface PointTemplateItem {
  pointTmplId?: number
  deviceTmplId?: number
  alarmCode?: string
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
  mqttServerAddresses?: string
  /** Maximum level allowed for root node (2/3/...), inherited by children */
  maxLevel?: number
  /** Number of devices in leaf group; null for non-leaf */
  deviceCount?: number
  /** Number of real devices in leaf group (without MOCK_ prefix) */
  realDeviceCount?: number
  /** Whether this subtree contains real data */
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

// ===== Device Deployment Types (device_deployment / point_deployment tables) =====

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

/** Impact preview when deleting navigation nodes */
export interface DeleteImpact {
  /** Number of cascaded child nodes to be deleted */
  childCount: number
  /** Number of devices to be unmounted */
  deviceCount: number
}

/** Form data for creating or editing navigation nodes */
export interface NavNodeForm {
  nodeName: string
  parentId: number | null
  icon: string | null
  sortOrder: number
  mqttServerAddresses?: string
}

// ===== RBAC & System Management Types =====

export interface PageResult<T> {
  total: number
  page: number
  pageSize: number
  records?: T[]
  list?: T[]
}

export interface Group {
  groupId: number
  groupName: string
  groupCode: string
  description?: string
  status: number
  userCount?: number
  permissionIds?: number[]
  createTime?: string
}

export interface PermissionNode {
  permId: number
  permName: string
  permCode: string
  permType: number
  parentId: number
  path?: string
  component?: string
  icon?: string
  sortOrder: number
  status: number
  children?: PermissionNode[]
}

export interface User {
  userId: number
  username: string
  realName?: string
  email?: string
  phone?: string
  status: number
  avatar?: string
  lastLoginTime?: string
  createTime?: string
  groups?: Group[]
}

export interface CurrentUser {
  userId: number
  username: string
  realName?: string
  avatar?: string
  groups: Group[]
  permissions: string[]
  menus: PermissionNode[]
}

export interface LoginResponse {
  token: string
  user: CurrentUser
}

export interface OperationLog {
  logId: number
  operatorId?: number
  operatorName?: string
  module?: string
  operation?: string
  businessType?: number
  method?: string
  requestMethod?: string
  requestUri?: string
  requestParams?: string
  status?: number
  errorMsg?: string
  costTime?: number
  operatorIp?: string
  createTime?: string
}

export interface UserForm {
  username?: string
  password?: string
  realName?: string
  email?: string
  phone?: string
  status: number
  avatar?: string
  groupIds?: number[]
}

export interface GroupForm {
  groupName: string
  groupCode: string
  description?: string
  status: number
  permissionIds?: number[]
}

export interface PermissionForm {
  permName: string
  permCode: string
  permType: number
  parentId: number
  path?: string
  component?: string
  icon?: string
  sortOrder: number
  status: number
}

export interface LogQuery {
  operatorName?: string
  module?: string
  operation?: string
  status?: number
  startTime?: string
  endTime?: string
  page: number
  pageSize: number
}


