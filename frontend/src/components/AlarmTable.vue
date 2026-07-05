<template>
  <div class="alarm-table-panel">
    <!-- Tab bar -->
    <div class="tab-bar">
      <div
        v-for="tab in tabs"
        :key="tab.key"
        :class="['tab-item', { active: activeTab === tab.key }]"
        @click="onTabChange(tab.key)"
      >
        {{ tab.label }}
        <span v-if="tab.count" class="tab-count">{{ tab.count }}</span>
      </div>
    </div>

    <!-- Table with built-in scrolling via max-height -->
    <el-table
      :data="filteredData"
      :max-height="tableMaxHeight"
      style="width: 100%"
      :header-cell-style="headerCellStyle"
      :cell-style="cellStyle"
      size="small"
      v-loading="loading"
    >
      <el-table-column prop="time" label="Time" width="180" />
      <el-table-column prop="severity" label="Severity" width="100">
        <template #default="{ row }">
          <el-tag :type="severityTagType(row.severity)" size="small" effect="dark">
            {{ row.severity }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="source" label="Source" width="120" />
      <el-table-column prop="message" label="Message" min-width="200" />
      <el-table-column prop="location" label="Location" width="150" />
      <el-table-column prop="status" label="Status" width="100">
        <template #default="{ row }">
          <span :class="['status-text', row.status.toLowerCase()]">{{ row.status }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="ackBy" label="Ack. By" width="100" />
    </el-table>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import { alarmApi } from '@/api/alarm'
import { useAlarmWebSocket } from '@/composables/useAlarmWebSocket'
import type { AlarmRecord } from '@/api/types'

const loading = ref(false)
const tableData = ref<AlarmRecord[]>([])
const activeTab = ref('active')

// Connect to WebSocket
const { latestAlarm, onUpdate } = useAlarmWebSocket()

// Calculate max height for el-table's built-in scrolling
const tableMaxHeight = ref(300)

function updateTableMaxHeight() {
  tableMaxHeight.value = Math.max(200, window.innerHeight - 460)
}

const tabs = computed(() => [
  { key: 'active', label: 'Active Alarms', count: String(tableData.value.length) },
  { key: 'unacknowledged', label: 'Unacknowledged', count: String(tableData.value.filter(a => a.status === 'Unack').length) },
  { key: 'acknowledged', label: 'Acknowledged', count: '' },
  { key: 'cleared', label: 'Cleared', count: '' },
])

const filteredData = computed(() => {
  switch (activeTab.value) {
    case 'unacknowledged':
      return tableData.value.filter(a => a.status === 'Unack')
    case 'acknowledged':
      return tableData.value.filter(a => a.status === 'Ack')
    case 'cleared':
      return tableData.value.filter(a => a.status === 'Cleared')
    default:
      return tableData.value
  }
})

const headerCellStyle = {
  backgroundColor: '#0d1117',
  color: '#e5e7eb',
  fontSize: '12px',
  fontWeight: '600',
  borderBottom: '1px solid #2d3748',
}

const cellStyle = {
  backgroundColor: '#111827',
  color: '#e5e7eb',
  fontSize: '12px',
  borderBottom: '1px solid #1f2937',
}

function severityTagType(severity: string): string {
  switch (severity) {
    case 'Critical': return 'danger'
    case 'Major': return 'warning'
    case 'Minor': return 'info'
    case 'Warning': return 'primary'
    default: return 'info'
  }
}

async function fetchData() {
  loading.value = true
  try {
    tableData.value = await alarmApi.getActiveAlarms('active')
  } catch (e) {
    console.error('Failed to fetch alarm data:', e)
  } finally {
    loading.value = false
  }
}

function onTabChange(tab: string) {
  activeTab.value = tab
}

// Handle real-time WebSocket updates
onUpdate((data: any) => {
  if (!data.alarm) return

  const alarm = data.alarm
  // Format timestamp as ISO-like string for consistent sorting
  const formatTime = (ts: number) => {
    const d = new Date(ts)
    const pad = (n: number) => String(n).padStart(2, '0')
    return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
  }

  const newRecord: AlarmRecord = {
    time: alarm.timestamp ? formatTime(alarm.timestamp) : '-',
    severity: alarm.severity || 'Normal',
    source: alarm.name || '',
    message: alarm.message || '',
    location: alarm.location || '',
    status: alarm.status || 'Unack',
    ackBy: alarm.ackBy || '-',
  }

  // Remove existing record if present (to re-insert at top)
  const existingIndex = tableData.value.findIndex(r => r.source === newRecord.source)
  if (existingIndex >= 0) {
    tableData.value.splice(existingIndex, 1)
  }

  // Always insert at the top (newest first)
  tableData.value.unshift(newRecord)

  // Keep max 100 rows
  if (tableData.value.length > 100) {
    tableData.value = tableData.value.slice(0, 100)
  }
})

onMounted(() => {
  updateTableMaxHeight()
  window.addEventListener('resize', updateTableMaxHeight)
  fetchData()
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', updateTableMaxHeight)
})
</script>

<style scoped lang="scss">
.alarm-table-panel {
  background-color: $bg-card;
  border: 1px solid $border-card;
  border-radius: 8px;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.tab-bar {
  display: flex;
  border-bottom: 1px solid $border-color;
  flex-shrink: 0;
}

.tab-item {
  padding: 10px 16px;
  font-size: 13px;
  color: $text-secondary;
  cursor: pointer;
  border-bottom: 2px solid transparent;
  transition: all 0.2s;

  &:hover {
    color: $text-primary;
    background-color: $bg-card-hover;
  }

  &.active {
    color: $accent-green;
    border-bottom-color: $accent-green;
  }
}

.tab-count {
  margin-left: 4px;
  padding: 1px 6px;
  background-color: rgba($accent-red, 0.2);
  color: $accent-red;
  border-radius: 8px;
  font-size: 11px;
  font-weight: 600;
}

.status-text {
  font-size: 12px;
  &.unack { color: $accent-red; }
  &.ack { color: $accent-green; }
  &.cleared { color: $text-muted; }
}

:deep(.el-table) {
  background-color: #111827;
  color: #e5e7eb;

  --el-table-bg-color: #111827;
  --el-table-tr-bg-color: #111827;
  --el-table-row-hover-bg-color: #1a2332;
  --el-table-border-color: #1f2937;
  --el-table-header-bg-color: #0d1117;
  --el-table-text-color: #e5e7eb;
  --el-table-header-text-color: #e5e7eb;

  .el-table__inner-wrapper {
    background-color: #111827;
  }

  .el-table__body-wrapper {
    background-color: #111827;
  }

  .el-table__header-wrapper {
    background-color: #0d1117;
  }

  .el-table__row {
    background-color: #111827;
  }

  .el-table__row:hover > td {
    background-color: #1a2332 !important;
  }

  td, th {
    border-bottom: 1px solid #1f2937;
  }
}

:deep(.el-table__empty-block) {
  background-color: #111827;
}

:deep(.el-table .el-scrollbar__bar.is-horizontal) {
  display: none;
}

:deep(.el-loading-mask) {
  background-color: rgba(10, 14, 23, 0.7);
}
</style>
