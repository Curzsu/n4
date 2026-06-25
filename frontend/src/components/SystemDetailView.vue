<template>
  <div class="system-detail-view">
    <!-- Header with Breadcrumbs and Back Button -->
    <div class="view-header">
      <div class="header-left">
        <el-button 
          size="small" 
          :icon="ArrowLeft" 
          @click="goBack" 
          class="back-btn"
        >
          返回大屏
        </el-button>
        <span class="system-title">{{ currentNodeName }}</span>
        <el-tag size="small" type="info" class="system-tag">{{ currentNodeType }}</el-tag>
      </div>
      <div class="header-right">
        <span class="update-time">数据刷新时间: {{ currentTime }}</span>
      </div>
    </div>

    <!-- Status Cards Grid -->
    <div class="status-cards-row">
      <div class="status-card total">
        <div class="card-glow"></div>
        <div class="card-icon">
          <el-icon><Monitor /></el-icon>
        </div>
        <div class="card-info">
          <span class="card-label">总设备数</span>
          <span class="card-value">{{ deviceStats.total }}</span>
        </div>
      </div>
      <div class="status-card online">
        <div class="card-glow"></div>
        <div class="card-icon">
          <el-icon><CircleCheck /></el-icon>
        </div>
        <div class="card-info">
          <span class="card-label">在线设备</span>
          <span class="card-value text-green">{{ deviceStats.online }}</span>
        </div>
      </div>
      <div class="status-card offline">
        <div class="card-glow"></div>
        <div class="card-icon">
          <el-icon><CircleClose /></el-icon>
        </div>
        <div class="card-info">
          <span class="card-label">离线设备</span>
          <span class="card-value text-gray">{{ deviceStats.offline }}</span>
        </div>
      </div>
      <div class="status-card fault">
        <div class="card-glow"></div>
        <div class="card-icon">
          <el-icon><Warning /></el-icon>
        </div>
        <div class="card-info">
          <span class="card-label">异常设备</span>
          <span class="card-value text-red">{{ deviceStats.fault }}</span>
        </div>
      </div>
    </div>

    <!-- Main Content Area: Device Table -->
    <div class="content-card">
      <div class="content-card-header">
        <h3>设备列表</h3>
        <el-input
          v-model="searchQuery"
          placeholder="搜索设备名称或编码..."
          size="small"
          :prefix-icon="Search"
          class="table-search"
          clearable
        />
      </div>
      
      <el-table 
        :data="filteredDevices" 
        style="width: 100%" 
        class="device-table"
        :header-cell-style="{ background: '#111827', color: '#9ca3af', borderColor: '#1f2937' }"
        :cell-style="{ borderColor: '#1f2937' }"
      >
        <el-table-column prop="id" label="设备 ID / 编码" width="140" />
        <el-table-column prop="label" label="设备名称" min-width="180" />
        <el-table-column prop="type" label="设备类型" width="120">
          <template #default="{ row }">
            <span class="type-badge">{{ row.type }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="运行状态" width="120">
          <template #default="{ row }">
            <span :class="['status-badge', getStatusClass(row.status)]">
              <span class="dot"></span>
              {{ getStatusText(row.status) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="140" fixed="right">
          <template #default="{ row }">
            <el-button 
              type="primary" 
              size="small" 
              plain
              @click="viewMonitorPoints(row)"
              class="action-btn"
            >
              查看测点
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- Monitor Points Drawer (Prepping for Phase 2) -->
    <el-drawer
      v-model="drawerVisible"
      :title="`设备监控测点 - ${selectedDevice?.label || ''}`"
      size="45%"
      destroy-on-close
      class="custom-drawer"
    >
      <div v-loading="loadingPoints" class="drawer-content">
        <el-table 
          :data="monitorPoints" 
          style="width: 100%"
          :header-cell-style="{ background: '#111827', color: '#9ca3af', borderColor: '#1f2937' }"
          :cell-style="{ borderColor: '#1f2937' }"
        >
          <el-table-column prop="pointCode" label="测点编码" width="120" />
          <el-table-column prop="pointName" label="测点名称" min-width="140" />
          <el-table-column prop="pointValue" label="当前值" width="100">
            <template #default="{ row }">
              <span class="point-value">{{ row.pointValue }} {{ row.unit || '' }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="pointStatus" label="状态" width="90">
            <template #default="{ row }">
              <el-tag 
                size="small" 
                :type="row.pointStatus === 1 ? 'success' : 'danger'"
                effect="dark"
              >
                {{ row.pointStatus === 1 ? '正常' : '告警' }}
              </el-tag>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { 
  ArrowLeft, Monitor, CircleCheck, CircleClose, Warning, Search
} from '@element-plus/icons-vue'
import { useDashboardStore } from '@/stores/dashboard'
import type { DeviceTreeNode } from '@/api/types'
import axios from 'axios'

const store = useDashboardStore()
const searchQuery = ref('')
const drawerVisible = ref(false)
const selectedDevice = ref<DeviceTreeNode | null>(null)
const monitorPoints = ref<any[]>([])
const loadingPoints = ref(false)

const currentTime = computed(() => {
  const d = new Date()
  return `${d.getHours().toString().padStart(2, '0')}:${d.getMinutes().toString().padStart(2, '0')}:${d.getSeconds().toString().padStart(2, '0')}`
})

const currentNodeName = computed(() => store.selectedNode?.label || '系统设备')
const currentNodeType = computed(() => {
  const t = store.selectedNode?.type || ''
  switch (t) {
    case 'building': return '区域层级'
    case 'system': return '系统层级'
    case 'device': return '设备层级'
    default: return '未知层级'
  }
})

// Recursively gather all devices under the selected node
const devicesList = computed(() => {
  if (!store.selectedNode) return []
  return getDevicesRec(store.selectedNode)
})

function getDevicesRec(node: DeviceTreeNode): DeviceTreeNode[] {
  if (node.type === 'device') {
    return [node]
  }
  const list: DeviceTreeNode[] = []
  if (node.children) {
    for (const child of node.children) {
      list.push(...getDevicesRec(child))
    }
  }
  return list
}

const filteredDevices = computed(() => {
  const q = searchQuery.value.trim().toLowerCase()
  if (!q) return devicesList.value
  return devicesList.value.filter(d => 
    d.label.toLowerCase().includes(q) || 
    d.id.toString().includes(q)
  )
})

// Calculate statistics
const deviceStats = computed(() => {
  const list = devicesList.value
  let total = list.length
  let online = 0
  let offline = 0
  let fault = 0 // Including maintenance(3) and fault(4)
  
  for (const d of list) {
    if (d.status === 1) online++
    else if (d.status === 2) offline++
    else fault++
  }
  
  return { total, online, offline, fault }
})

function goBack() {
  store.selectedNode = null
}

function getStatusText(status: number): string {
  switch (status) {
    case 1: return '在线'
    case 2: return '离线'
    case 3: return '维护'
    case 4: return '故障'
    default: return '未知'
  }
}

function getStatusClass(status: number): string {
  switch (status) {
    case 1: return 'online'
    case 2: return 'offline'
    case 3: return 'maintenance'
    case 4: return 'fault'
    default: return 'unknown'
  }
}

async function viewMonitorPoints(row: DeviceTreeNode) {
  selectedDevice.value = row
  drawerVisible.value = true
  loadingPoints.value = true
  try {
    const res = await axios.get(`/api/devices/${row.id}/points`)
    if (res.data && res.data.code === 200) {
      monitorPoints.value = res.data.data
    } else {
      monitorPoints.value = []
    }
  } catch (err) {
    console.error('Failed to load points:', err)
    monitorPoints.value = []
  } finally {
    loadingPoints.value = false
  }
}
</script>

<style scoped lang="scss">
.system-detail-view {
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-height: 100%;
}

.view-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 16px;
  background: rgba($bg-card, 0.6);
  backdrop-filter: blur(10px);
  border: 1px solid $border-color;
  border-radius: 8px;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.back-btn {
  background-color: transparent;
  border-color: $border-card;
  color: $text-secondary;
  &:hover {
    color: $accent-green;
    border-color: $accent-green;
    background-color: rgba($accent-green, 0.05);
  }
}

.system-title {
  font-size: 16px;
  font-weight: 600;
  color: $text-primary;
}

.system-tag {
  background-color: rgba($text-muted, 0.15);
  border: 1px solid rgba($text-muted, 0.3);
  color: $text-secondary;
}

.update-time {
  font-size: 12px;
  color: $text-muted;
}

.status-cards-row {
  display: flex;
  gap: 12px;
}

.status-card {
  flex: 1;
  background-color: $bg-card;
  border: 1px solid $border-card;
  border-radius: 8px;
  padding: 16px;
  display: flex;
  align-items: center;
  gap: 16px;
  position: relative;
  overflow: hidden;
  transition: transform 0.2s, border-color 0.2s;
  
  &:hover {
    transform: translateY(-2px);
    border-color: rgba($text-primary, 0.2);
  }
  
  &.total:hover { border-color: $accent-blue; }
  &.online:hover { border-color: $accent-green; }
  &.offline:hover { border-color: $text-muted; }
  &.fault:hover { border-color: $accent-red; }
}

.card-icon {
  width: 40px;
  height: 40px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 20px;
  background-color: rgba($text-muted, 0.1);
  color: $text-secondary;
  
  .total & {
    background-color: rgba($accent-blue, 0.1);
    color: $accent-blue;
  }
  .online & {
    background-color: rgba($accent-green, 0.1);
    color: $accent-green;
  }
  .offline & {
    background-color: rgba($text-muted, 0.15);
    color: $text-secondary;
  }
  .fault & {
    background-color: rgba($accent-red, 0.1);
    color: $accent-red;
  }
}

.card-info {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.card-label {
  font-size: 12px;
  color: $text-secondary;
}

.card-value {
  font-size: 24px;
  font-weight: 700;
  color: $text-primary;
}

.text-green { color: $accent-green; }
.text-gray { color: $text-secondary; }
.text-red { color: $accent-red; }

.content-card {
  background-color: $bg-card;
  border: 1px solid $border-card;
  border-radius: 8px;
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.content-card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  
  h3 {
    margin: 0;
    font-size: 14px;
    font-weight: 600;
    color: $text-primary;
  }
}

.table-search {
  width: 240px;
  .el-input__wrapper {
    background-color: $bg-dark !important;
  }
}

.device-table {
  background-color: transparent !important;
  --el-table-bg-color: transparent;
  --el-table-tr-bg-color: transparent;
  --el-table-row-hover-bg-color: #1a2332;
  color: $text-primary;
}

.type-badge {
  background-color: rgba($accent-blue, 0.1);
  border: 1px solid rgba($accent-blue, 0.2);
  color: $accent-blue;
  padding: 2px 6px;
  border-radius: 4px;
  font-size: 11px;
}

.status-badge {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 2px 8px;
  border-radius: 12px;
  font-size: 11px;
  font-weight: 500;
  
  .dot {
    width: 6px;
    height: 6px;
    border-radius: 50%;
  }
  
  &.online {
    background-color: rgba($accent-green, 0.1);
    color: $accent-green;
    .dot { background-color: $accent-green; }
  }
  &.offline {
    background-color: rgba($text-muted, 0.1);
    color: $text-secondary;
    .dot { background-color: $text-secondary; }
  }
  &.maintenance {
    background-color: rgba($accent-yellow, 0.1);
    color: $accent-yellow;
    .dot { background-color: $accent-yellow; }
  }
  &.fault {
    background-color: rgba($accent-red, 0.1);
    color: $accent-red;
    .dot { background-color: $accent-red; }
  }
}

.action-btn {
  border-color: rgba($accent-green, 0.3);
  color: $accent-green;
  background-color: transparent;
  &:hover {
    background-color: $accent-green !important;
    color: #ffffff !important;
  }
}

:deep(.custom-drawer) {
  background-color: $bg-card !important;
  border-left: 1px solid $border-color;
  
  .el-drawer__header {
    margin-bottom: 20px;
    padding-bottom: 12px;
    border-bottom: 1px solid $border-color;
    
    .el-drawer__title {
      color: $text-primary;
      font-weight: 600;
      font-size: 15px;
    }
  }
  
  .el-drawer__body {
    padding: 0 20px 20px;
  }
}

.point-value {
  font-weight: 600;
  color: $text-primary;
}
</style>
