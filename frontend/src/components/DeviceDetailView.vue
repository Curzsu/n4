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

    <!-- Monitor Points Table Card -->
    <div class="table-card">
      <div class="table-card-header">
        <div class="header-title">
          <el-icon><List /></el-icon>
          <h3>监控测点列表</h3>
        </div>
        <el-input
          v-model="searchQuery"
          placeholder="搜索测点名称或编码..."
          size="small"
          :prefix-icon="Search"
          class="table-search"
          clearable
        />
      </div>

      <el-table 
        :data="filteredPoints" 
        style="width: 100%" 
        class="points-table"
        :header-cell-style="{ background: '#111827', color: '#9ca3af', borderColor: '#1f2937' }"
        :cell-style="{ borderColor: '#1f2937' }"
      >
        <el-table-column prop="pointCode" label="测点编码" width="160" show-overflow-tooltip />
        <el-table-column prop="registerId" label="寄存器地址" width="120">
          <template #default="{ row }">
            <code class="addr-code">{{ row.registerId || 'N/A' }}</code>
          </template>
        </el-table-column>
        <el-table-column prop="pointName" label="测点名称" min-width="180" />
        <el-table-column prop="pointType" label="测点类型" width="130">
          <template #default="{ row }">
            <span :class="['type-badge', getPointTypeClass(row.pointType)]">
              {{ getPointTypeText(row.pointType) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="pointValue" label="实时读数" width="160">
          <template #default="{ row }">
            <span class="point-value">
              <span class="val-num">{{ row.pointValue !== null ? row.pointValue : '--' }}</span>
              <span class="val-unit" v-if="row.unit">&nbsp;{{ row.unit }}</span>
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="pointStatus" label="通信状态" width="120" fixed="right">
          <template #default="{ row }">
            <span :class="['status-badge', row.pointStatus === 1 ? 'status-ok' : 'status-err']">
              <span class="dot"></span>
              {{ row.pointStatus === 1 ? '正常' : '告警' }}
            </span>
          </template>
        </el-table-column>
      </el-table>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, watch } from 'vue'
import { ArrowLeft, Cpu, CircleCheck, Warning, List, Search } from '@element-plus/icons-vue'
import { useDashboardStore } from '@/stores/dashboard'
import { deviceApi } from '@/api/device'
import type { MonitorPoint } from '@/api/types'

const store = useDashboardStore()
const loading = ref(false)
const points = ref<MonitorPoint[]>([])
const searchQuery = ref('')
const autoRefresh = ref(true)
const lastUpdateTime = ref('--:--:--')
let refreshIntervalId: number | null = null

// Fetch data
async function fetchPoints() {
  if (!store.selectedNode?.id) return
  try {
    const data = await deviceApi.getDevicePoints(store.selectedNode.id)
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

function getPointTypeText(type: number): string {
  switch (type) {
    case 1: return 'DI - 数字输入'
    case 2: return 'DO - 数字输出'
    case 3: return 'AI - 模拟输入'
    case 4: return 'AO - 模拟输出'
    default: return '未知'
  }
}

function getPointTypeClass(type: number): string {
  switch (type) {
    case 1: return 'type-di'
    case 2: return 'type-do'
    case 3: return 'type-ai'
    case 4: return 'type-ao'
    default: return ''
  }
}

// Filtered points search
const filteredPoints = computed(() => {
  const q = searchQuery.value.trim().toLowerCase()
  if (!q) return points.value
  return points.value.filter(p => 
    (p.pointName && p.pointName.toLowerCase().includes(q)) ||
    (p.pointCode && p.pointCode.toLowerCase().includes(q))
  )
})

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
$accent-purple: #a855f7;

.device-detail-view {
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

.table-card {
  background-color: $bg-card;
  border: 1px solid $border-card;
  border-radius: 8px;
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.table-card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.header-title {
  display: flex;
  align-items: center;
  gap: 8px;
  color: $text-primary;
  
  .el-icon {
    font-size: 16px;
    color: $accent-green;
  }

  h3 {
    margin: 0;
    font-size: 14px;
    font-weight: 600;
  }
}

.table-search {
  width: 240px;
  .el-input__wrapper {
    background-color: $bg-dark !important;
  }
}

.points-table {
  background-color: transparent !important;
  --el-table-bg-color: transparent;
  --el-table-tr-bg-color: transparent;
  --el-table-row-hover-bg-color: #1a2332;
  color: $text-primary;
}

.addr-code {
  background-color: rgba($text-muted, 0.1);
  padding: 2px 6px;
  border-radius: 4px;
  font-family: monospace;
  color: $text-secondary;
  font-size: 12px;
}

.type-badge {
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 11px;
  font-weight: 500;
  
  &.type-di {
    background-color: rgba($accent-blue, 0.1);
    border: 1px solid rgba($accent-blue, 0.2);
    color: $accent-blue;
  }
  &.type-do {
    background-color: rgba($accent-purple, 0.1);
    border: 1px solid rgba($accent-purple, 0.2);
    color: $accent-purple;
  }
  &.type-ai {
    background-color: rgba($accent-green, 0.1);
    border: 1px solid rgba($accent-green, 0.2);
    color: $accent-green;
  }
  &.type-ao {
    background-color: rgba($accent-yellow, 0.1);
    border: 1px solid rgba($accent-yellow, 0.2);
    color: $accent-yellow;
  }
}

.point-value {
  display: inline-flex;
  align-items: baseline;
  
  .val-num {
    font-weight: 600;
    font-size: 14px;
    color: $text-primary;
    font-family: monospace;
  }
  .val-unit {
    font-size: 11px;
    color: $text-muted;
  }
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
  
  &.status-ok {
    background-color: rgba($accent-green, 0.1);
    color: $accent-green;
    .dot { background-color: $accent-green; }
  }
  &.status-err {
    background-color: rgba($accent-red, 0.1);
    color: $accent-red;
    .dot { 
      background-color: $accent-red; 
      box-shadow: 0 0 6px $accent-red;
    }
  }
}
</style>
