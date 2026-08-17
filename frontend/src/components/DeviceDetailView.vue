<template>
  <div class="device-detail-view" v-loading="loading">
    <!-- View Header -->
    <div class="view-header">
      <div class="header-left">
        <el-button 
          size="small" 
          :icon="ArrowLeft" 
          @click="goBack" 
          class="back-btn"
        >
          返回
        </el-button>
        <span class="device-title">{{ store.selectedNode?.label || '设备详情' }}</span>
        <el-tag size="small" type="success" class="status-tag">
          {{ getDeviceStatusText(store.selectedNode?.status) }}
        </el-tag>
      </div>
      <div class="header-right">
        <div class="refresh-control">
          <span class="refresh-label">实时轮询 (3s)</span>
          <el-switch 
            v-model="autoRefresh" 
            active-color="#10b981" 
            inactive-color="#4b5563"
            @change="handleRefreshToggle"
          />
        </div>
        <span class="update-time">更新时间: {{ lastUpdateTime }}</span>
      </div>
    </div>

    <!-- KPI Summary Row -->
    <div class="stats-row">
      <div class="stat-card total">
        <div class="card-glow"></div>
        <div class="stat-icon">
          <el-icon><Cpu /></el-icon>
        </div>
        <div class="stat-info">
          <span class="stat-label">总测点数</span>
          <span class="stat-value">{{ pointStats.total }}</span>
        </div>
      </div>
      <div class="stat-card normal">
        <div class="card-glow"></div>
        <div class="stat-icon">
          <el-icon><CircleCheck /></el-icon>
        </div>
        <div class="stat-info">
          <span class="stat-label">正常点</span>
          <span class="stat-value text-green">{{ pointStats.normal }}</span>
        </div>
      </div>
      <div class="stat-card alarm">
        <div class="card-glow"></div>
        <div class="stat-icon">
          <el-icon><Warning /></el-icon>
        </div>
        <div class="stat-info">
          <span class="stat-label">告警点</span>
          <span class="stat-value text-red">{{ pointStats.alarm }}</span>
        </div>
      </div>
    </div>

    <FuxaMonitorPanel :device="(store.selectedNode as unknown as DeviceTreeNode)" />

    <div class="device-alarm-table">
      <AlarmTable :device-name="store.selectedNode?.label" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, watch } from 'vue'
import { ArrowLeft, Cpu, CircleCheck, Warning } from '@element-plus/icons-vue'
import AlarmTable from '@/components/AlarmTable.vue'
import FuxaMonitorPanel from '@/components/FuxaMonitorPanel.vue'
import { useDashboardStore } from '@/stores/dashboard'
import { deviceApi } from '@/api/device'
import type { DeviceTreeNode, MonitorPoint } from '@/api/types'

const store = useDashboardStore()
const loading = ref(false)
const points = ref<MonitorPoint[]>([])
const autoRefresh = ref(true)
const lastUpdateTime = ref('--:--:--')
let refreshIntervalId: number | null = null

// Fetch data
async function fetchPoints() {
  if (!store.selectedNode?.id) return
  try {
    const data = await deviceApi.getDevicePoints(Number(store.selectedNode.id))
    points.value = data
    updateTimestamp()
  } catch (err) {
    console.error('Failed to load monitor points:', err)
  }
}

// Update last update timestamp
function updateTimestamp() {
  const now = new Date()
  lastUpdateTime.value = `${now.getHours().toString().padStart(2, '0')}:${now.getMinutes().toString().padStart(2, '0')}:${now.getSeconds().toString().padStart(2, '0')}`
}

// Handle Auto-Refresh toggle
function handleRefreshToggle(val: boolean | string | number) {
  if (val) {
    startPolling()
  } else {
    stopPolling()
  }
}

function startPolling() {
  stopPolling()
  refreshIntervalId = window.setInterval(fetchPoints, 3000)
}

function stopPolling() {
  if (refreshIntervalId !== null) {
    clearInterval(refreshIntervalId)
    refreshIntervalId = null
  }
}

function goBack() {
  // Clear selected node to go back to site dashboard or system level
  store.selectedNode = null
}

function getDeviceStatusText(status: number | undefined): string {
  switch (status) {
    case 1: return '在线'
    case 2: return '离线'
    case 3: return '维护'
    case 4: return '故障'
    default: return '在线'
  }
}

// Calculate statistics
const pointStats = computed(() => {
  const list = points.value
  const total = list.length
  let normal = 0
  let alarm = 0
  for (const p of list) {
    if (p.pointStatus === 1) normal++
    else alarm++
  }
  return { total, normal, alarm }
})

// Watch selected node to re-fetch
watch(() => store.selectedNode, async (newNode) => {
  if (newNode && newNode.type === 'device') {
    loading.value = true
    await fetchPoints()
    loading.value = false
    if (autoRefresh.value) {
      startPolling()
    }
  }
}, { immediate: true })

onMounted(() => {
  if (autoRefresh.value) {
    startPolling()
  }
})

onUnmounted(() => {
  stopPolling()
})
</script>

<style scoped lang="scss">
.device-detail-view {
  display: flex;
  flex-direction: column;
  flex: 0 0 auto;
  gap: 16px;
  width: 100%;
  min-width: 0;
  min-height: 100%;
  box-sizing: border-box;
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

.device-title {
  font-size: 16px;
  font-weight: 600;
  color: $text-primary;
}

.status-tag {
  border-radius: 4px;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 20px;
}

.refresh-control {
  display: flex;
  align-items: center;
  gap: 8px;
}

.refresh-label {
  font-size: 12px;
  color: $text-secondary;
}

.update-time {
  font-size: 12px;
  color: $text-muted;
}

.stats-row {
  display: flex;
  gap: 12px;
}

.stat-card {
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
  &.normal:hover { border-color: $accent-green; }
  &.alarm:hover { border-color: $accent-red; }
}

.stat-icon {
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
  .normal & {
    background-color: rgba($accent-green, 0.1);
    color: $accent-green;
  }
  .alarm & {
    background-color: rgba($accent-red, 0.1);
    color: $accent-red;
  }
}

.stat-info {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.stat-label {
  font-size: 12px;
  color: $text-secondary;
}

.stat-value {
  font-size: 24px;
  font-weight: 700;
  color: $text-primary;
}

.text-green { color: $accent-green; }
.text-red { color: $accent-red; }

.device-alarm-table {
  height: 520px;
  min-height: 360px;
  display: flex;
  flex-direction: column;
}
</style>
