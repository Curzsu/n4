<template>
  <div ref="tablePanelRef" class="alarm-table-panel">
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
      <div class="tab-actions">
        <el-button
          :type="isAllSelected ? 'primary' : 'default'"
          size="small"
          @click="toggleSelectAll"
        >
          {{ isAllSelected ? 'Deselect All (Current)' : 'Select All (Current)' }}
        </el-button>
        <el-button
          type="success"
          size="small"
          :disabled="selectedIds.size === 0"
          @click="ackSelected"
        >
          Ack
        </el-button>
      </div>
    </div>

    <!-- Table with fixed height for internal scrolling -->
    <el-table
      ref="tableRef"
      :data="filteredData"
      :height="tableHeight"
      style="width: 100%"
      :header-cell-style="headerCellStyle"
      :cell-style="cellStyle"
      size="small"
      border
      v-loading="loading"
    >
      <el-table-column width="48" align="center">
        <template #default="{ row }">
          <el-checkbox
            :model-value="selectedIds.has(row.id)"
            :disabled="row.status === 'Ack'"
            @change="(val: boolean) => toggleSelection(row.id, val)"
          />
        </template>
      </el-table-column>
      <el-table-column prop="time" label="Time" width="130" show-overflow-tooltip />
      <el-table-column prop="severity" label="Severity" width="90">
        <template #default="{ row }">
          <el-tag class="severity-tag" :type="severityTagType(row.severity)" size="small" effect="dark">
            {{ row.severity }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="source" label="Source" min-width="200" show-overflow-tooltip />
      <el-table-column prop="message" label="Message" min-width="200" show-overflow-tooltip />
      <el-table-column prop="location" label="Location" width="150" show-overflow-tooltip />
      <el-table-column prop="status" label="Status" width="100">
        <template #default="{ row }">
          <span :class="['status-text', row.status.toLowerCase()]">{{ row.status }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="duration" label="Duration" width="100">
        <template #default="{ row }">
          <ElapsedDuration :time="row.time" :duration="row.duration" :stage="row.stage" />
        </template>
      </el-table-column>
      <el-table-column prop="ackBy" label="Ack. By" width="100" show-overflow-tooltip />
    </el-table>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onBeforeUnmount, nextTick, watch } from 'vue'
import { alarmApi } from '@/api/alarm'
import { useAlarmWebSocket } from '@/composables/useAlarmWebSocket'
import { normalizeRecord, severityTagType } from '@/utils/alarm'
import ElapsedDuration from '@/components/ElapsedDuration.vue'
import type { AlarmRecord } from '@/api/types'

// When deviceName is provided, only alarms whose path contains that device name
// as a segment are shown. Used by DeviceDetailView to scope the alarm list.
const props = defineProps<{ deviceName?: string }>()

const loading = ref(false)
const tableData = ref<AlarmRecord[]>([])
const activeTab = ref('active')
const selectedIds = ref<Set<string>>(new Set())
const tablePanelRef = ref<HTMLDivElement>()
const tableRef = ref<any>()
const tableHeight = ref(400)
const visibleRowIds = ref<Set<string>>(new Set())

// Connect to WebSocket
const { onUpdate } = useAlarmWebSocket()

/** Check whether an alarm record belongs to the filtered device. */
function matchesDevice(record: AlarmRecord): boolean {
  if (!props.deviceName) return true
  // Path format: Drivers/ModbusTcpNetwork/{area}/points/{deviceName}/{pointName}
  // Match the device name as a path segment after "points".
  const path = record.path || ''
  if (!path) return false
  const segments = path.split('/')
  const pointsIdx = segments.indexOf('points')
  if (pointsIdx >= 0 && pointsIdx + 1 < segments.length) {
    return segments[pointsIdx + 1] === props.deviceName
  }
  // Fallback: substring match
  return path.includes(props.deviceName)
}

function updateTableHeight() {
  if (!tablePanelRef.value) return
  const tabBar = tablePanelRef.value.querySelector('.tab-bar') as HTMLElement | null
  const tabBarHeight = tabBar?.offsetHeight || 42
  tableHeight.value = Math.max(200, tablePanelRef.value.clientHeight - tabBarHeight)
}

let resizeObserver: ResizeObserver | null = null

function getScrollBody(): HTMLElement | null {
  if (!tableRef.value) return null
  const wrapper = tableRef.value.$el?.querySelector('.el-table__body-wrapper')
  if (!wrapper) return null
  // Element Plus uses el-scrollbar internally; the actual scroll element is .el-scrollbar__wrap
  return wrapper.querySelector('.el-scrollbar__wrap') ?? wrapper
}

function updateVisibleRows() {
  const scrollBody = getScrollBody()
  if (!scrollBody) {
    visibleRowIds.value = new Set()
    return
  }

  const bodyRect = scrollBody.getBoundingClientRect()
  const rows = scrollBody.querySelectorAll('.el-table__row')
  const ids = new Set<string>()

  rows.forEach((row, index) => {
    const rect = row.getBoundingClientRect()
    const overlapTop = Math.max(rect.top, bodyRect.top)
    const overlapBottom = Math.min(rect.bottom, bodyRect.bottom)
    const visibleHeight = Math.max(0, overlapBottom - overlapTop)
    const visibleRatio = rect.height > 0 ? visibleHeight / rect.height : 0
    const isMostlyVisible = visibleRatio >= 0.6
    const data = filteredData.value[index]
    if (isMostlyVisible && data && data.status !== 'Ack') {
      ids.add(data.id)
    }
  })

  visibleRowIds.value = ids
}

function handleWheel(e: WheelEvent) {
  const scrollBody = getScrollBody()
  if (!scrollBody) return

  e.preventDefault()
  const row = scrollBody.querySelector('.el-table__row') as HTMLElement | null
  const rowHeight = row?.offsetHeight ?? 32
  const direction = e.deltaY > 0 ? 1 : -1
  scrollBody.scrollTop += direction * rowHeight * 3
}

const isAllSelected = computed(() => {
  if (visibleRowIds.value.size === 0) return false
  return Array.from(visibleRowIds.value).every(id => selectedIds.value.has(id))
})

function toggleSelection(id: string, selected: boolean) {
  if (selected) {
    selectedIds.value.add(id)
  } else {
    selectedIds.value.delete(id)
  }
}

function toggleSelectAll() {
  const ids = Array.from(visibleRowIds.value)
  if (ids.length === 0) {
    selectedIds.value.clear()
    return
  }

  if (isAllSelected.value) {
    ids.forEach(id => selectedIds.value.delete(id))
  } else {
    selectedIds.value.clear()
    ids.forEach(id => selectedIds.value.add(id))
  }
}
const tabs = computed(() => [
  { key: 'active', label: 'Active Alarms', count: String(tableData.value.length) },
  { key: 'unacknowledged', label: 'Unacknowledged', count: String(tableData.value.filter(a => a.status === 'Unack').length) },
  { key: 'acknowledged', label: 'Acknowledged', count: '' },
  { key: 'cleared', label: 'Cleared', count: '' },
])

const filteredData = computed(() => {
  const deviceFiltered = props.deviceName
    ? tableData.value.filter(a => matchesDevice(a))
    : tableData.value
  switch (activeTab.value) {
    case 'unacknowledged':
      return deviceFiltered.filter(a => a.status === 'Unack')
    case 'acknowledged':
      return deviceFiltered.filter(a => a.status === 'Ack')
    case 'cleared':
      return deviceFiltered.filter(a => a.status === 'Cleared')
    default:
      return deviceFiltered
  }
})

async function ackSelected() {
  if (selectedIds.value.size === 0) return

  const selectedRows = tableData.value.filter(row => selectedIds.value.has(row.id))
  const paths = selectedRows.map(row => row.path).filter((p): p is string => !!p)
  if (paths.length === 0) return

  try {
    await alarmApi.ackAlarms(paths, 'admin')
    selectedRows.forEach(row => {
      row.status = 'Ack'
      row.ackBy = 'admin'
    })
    selectedIds.value.clear()
    // Acked rows are now excluded from selectable visible rows. Recompute the
    // visible set so stale acked ids are dropped — otherwise toggleSelectAll
    // would re-select acked (disabled) rows and re-enable the Ack button.
    nextTick(updateVisibleRows)
  } catch (e) {
    console.error('Failed to acknowledge alarms:', e)
  }
}

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

watch(filteredData, () => {
  nextTick(updateVisibleRows)
}, { flush: 'post' })

async function fetchData() {
  loading.value = true
  try {
    const raw = await alarmApi.getActiveAlarms('active')
    tableData.value = raw.map(normalizeRecord)
    nextTick(updateVisibleRows)
  } catch (e) {
    console.error('Failed to fetch alarm data:', e)
  } finally {
    loading.value = false
  }
}

function onTabChange(tab: string) {
  activeTab.value = tab
  selectedIds.value.clear()
  nextTick(updateVisibleRows)
}

// Handle real-time WebSocket updates
onUpdate((data: any) => {
  if (!data.alarm) return

  const newRecord = normalizeRecord(data.alarm)

  // When scoped to a device, skip records that don't belong to it — but still
  // process cleared signals so a device's removed alarm disappears.
  if (props.deviceName && !matchesDevice(newRecord)) {
    // Only handle removal for records that might already be in the list
    const isCleared = newRecord.stage === 'end' && newRecord.status === 'Ack'
    if (isCleared) {
      const idx = tableData.value.findIndex(r => r.source === newRecord.source)
      if (idx >= 0) {
        selectedIds.value.delete(tableData.value[idx].id)
        tableData.value.splice(idx, 1)
        nextTick(updateVisibleRows)
      }
    }
    return
  }

  // Cleared signal: an acknowledged alarm has recovered (stage=end + Ack).
  // Remove it from the list entirely.
  const isCleared = newRecord.stage === 'end' && newRecord.status === 'Ack'
  if (isCleared) {
    const idx = tableData.value.findIndex(r => r.source === newRecord.source)
    if (idx >= 0) {
      selectedIds.value.delete(tableData.value[idx].id)
      tableData.value.splice(idx, 1)
    }
    nextTick(updateVisibleRows)
    return
  }

  // Frozen recovery (unack): alarm recovered but not yet acknowledged. Update the
  // existing row in place (freeze duration via stage=end) without re-inserting at top.
  if (newRecord.stage === 'end' && newRecord.status === 'Unack') {
    const idx = tableData.value.findIndex(r => r.source === newRecord.source)
    if (idx >= 0) {
      tableData.value[idx] = newRecord
    } else {
      tableData.value.unshift(newRecord)
    }
    nextTick(updateVisibleRows)
    return
  }

  // Normal alarm update: remove existing record (to re-insert)
  const existingIndex = tableData.value.findIndex(r => r.source === newRecord.source)
  if (existingIndex >= 0) {
    const replaced = tableData.value[existingIndex]
    // Records share the same id (= source). When an alarm is replaced by a new
    // occurrence (e.g. High escalates to High-High), drop the stale selection
    // so the new record is not silently treated as the one the user picked.
    selectedIds.value.delete(replaced.id)
    tableData.value.splice(existingIndex, 1)
  }

  // Insert and re-sort by time descending (newest first)
  tableData.value.push(newRecord)
  tableData.value.sort((a, b) => b.time.localeCompare(a.time))

  // Keep max 100 rows
  if (tableData.value.length > 100) {
    tableData.value = tableData.value.slice(0, 100)
  }

  // Recompute visible rows so Select All works immediately after a WS push,
  // not only after a manual page refresh or Ack.
  nextTick(updateVisibleRows)
})

onMounted(() => {
  updateTableHeight()
  resizeObserver = new ResizeObserver(updateTableHeight)
  if (tablePanelRef.value) {
    resizeObserver.observe(tablePanelRef.value)
  }
  fetchData().then(() => {
    nextTick(() => {
      updateVisibleRows()
      const scrollBody = getScrollBody()
      if (scrollBody) {
        scrollBody.addEventListener('scroll', updateVisibleRows)
        scrollBody.addEventListener('wheel', handleWheel, { passive: false })
      }
    })
  })
})

onBeforeUnmount(() => {
  resizeObserver?.disconnect()
  const scrollBody = getScrollBody()
  if (scrollBody) {
    scrollBody.removeEventListener('scroll', updateVisibleRows)
    scrollBody.removeEventListener('wheel', handleWheel)
  }
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
  flex: 1;
  min-height: 0;
}

.tab-bar {
  display: flex;
  border-bottom: 1px solid $border-color;
  flex-shrink: 0;
}

.tab-actions {
  margin-left: auto;
  display: flex;
  align-items: center;
  padding: 0 12px;
  gap: 8px;
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

:deep(.el-table .cell) {
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.severity-tag {
  width: 70px;
  display: inline-flex;
  justify-content: center;
}

// Disabled checkboxes (Ack rows) must be visually obvious — Element Plus's
// default disabled style is nearly invisible on the dark theme (opacity dimming
// makes it look identical to a normal unchecked box).
:deep(.el-checkbox.is-disabled) {
  .el-checkbox__inner {
    background-color: #374151;
    border-color: #6b7280;
    cursor: not-allowed;
    opacity: 1;  // override Element Plus's default dimming
  }
  .el-checkbox__input.is-checked .el-checkbox__inner {
    // A checked+disabled box stays grey so it reads as "locked", not "selected"
    background-color: #4b5563;
    border-color: #6b7280;
  }
}
</style>
