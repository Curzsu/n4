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
      <el-table-column width="20" align="center">
        <template #default="{ row }">
          <el-checkbox
            :model-value="selectedIds.has(row.id)"
            :disabled="row.status === 'Ack'"
            @change="(val: boolean) => toggleSelection(row.id, val)"
          />
        </template>
      </el-table-column>
      <el-table-column label="No." width="40">
        <template #default="{ $index }">{{ filteredData.length - $index }}</template>
      </el-table-column>
      <el-table-column prop="time" label="Time" width="105" show-overflow-tooltip>
        <template #header>
          <div class="header-cell">
            <el-popover v-model:visible="filterPopover.time" trigger="manual"  placement="bottom" :width="320" popper-class="alarm-filter-popper" @show="syncTimeDraft">
              <template #reference>
                <span class="header-label" :class="{ 'filter-active': hasFilter('time') }" @click.stop="filterPopover.time = !filterPopover.time">Time
                  <el-icon v-if="hasFilter('time')" class="filter-icon active"><Filter /></el-icon>
                </span>
              </template>
              <div class="filter-panel" @click.stop @mousedown.stop>
                <el-date-picker :teleported="false"
                  v-model="draftTimeRange"
                  type="datetimerange"
                  single-panel
                  size="small"
                  format="YYYY-MM-DD HH:mm:ss"
                  start-placeholder="Start"
                  end-placeholder="End"
                  style="width: 100%"
                  popper-class="alarm-datepicker-popper"
                />
                <div class="filter-actions">
                  <el-button size="small" @click="resetTimeFilter">Reset</el-button>
                  <el-button size="small" @click="cancelTimeFilter">Cancel</el-button>
                  <el-button size="small" type="primary" @click="applyTimeFilter">OK</el-button>
                </div>
              </div>
            </el-popover>
            <span class="sort-icons" @click="toggleSort('time')">
              <el-icon class="sort-icon" :class="{ active: sortField === 'time' && sortOrder === 'asc' }"><CaretTop /></el-icon>
              <el-icon class="sort-icon" :class="{ active: sortField === 'time' && sortOrder === 'desc' }"><CaretBottom /></el-icon>
            </span>
          </div>
        </template>
      </el-table-column>
      <el-table-column prop="severity" label="Severity" width="65">
        <template #header>
          <div class="header-cell">
            <el-popover v-model:visible="filterPopover.severity" trigger="manual"  placement="bottom" :width="140" popper-class="alarm-filter-popper" @show="syncDraft('severity')">
              <template #reference>
                <span class="header-label" :class="{ 'filter-active': hasFilter('severity') }" @click.stop="filterPopover.severity = !filterPopover.severity">Severity
                  <el-icon v-if="hasFilter('severity')" class="filter-icon active"><Filter /></el-icon>
                </span>
              </template>
              <div class="filter-panel" @click.stop @mousedown.stop>
                <el-select v-model="draft.severity" :teleported="false" multiple placeholder="Select" size="small" style="width: 100%" popper-class="alarm-filter-select-popper">
                  <el-option v-for="s in severityOptions" :key="s" :label="s" :value="s" />
                </el-select>
                <div class="filter-actions">
                  <el-button size="small" @click="resetFilter('severity')">Reset</el-button>
                  <el-button size="small" @click="cancelFilter('severity')">Cancel</el-button>
                  <el-button size="small" type="primary" @click="applyFilter('severity')">OK</el-button>
                </div>
              </div>
            </el-popover>
            <span class="sort-icons" @click="toggleSort('severity')">
              <el-icon class="sort-icon" :class="{ active: sortField === 'severity' && sortOrder === 'asc' }"><CaretTop /></el-icon>
              <el-icon class="sort-icon" :class="{ active: sortField === 'severity' && sortOrder === 'desc' }"><CaretBottom /></el-icon>
            </span>
          </div>
        </template>
        <template #default="{ row }">
          <span
            class="severity-tag"
            :style="severityStyle(row)"
          >
            {{ row.severity }}
          </span>
        </template>
      </el-table-column>
      <el-table-column prop="source" label="Source" min-width="200" show-overflow-tooltip>
        <template #header>
          <el-popover v-model:visible="filterPopover.source" trigger="manual"  placement="bottom" :width="180" popper-class="alarm-filter-popper" @show="syncDraft('source')">
            <template #reference>
              <span class="header-label" :class="{ 'filter-active': hasFilter('source') }" @click.stop="filterPopover.source = !filterPopover.source">Source
                <el-icon v-if="hasFilter('source')" class="filter-icon active"><Filter /></el-icon>
              </span>
            </template>
            <div class="filter-panel" @click.stop @mousedown.stop>
              <el-input v-model="draft.source" placeholder="Fuzzy match" size="small" clearable style="width: 100%" />
              <div class="filter-actions">
                <el-button size="small" @click="resetFilter('source')">Reset</el-button>
                <el-button size="small" @click="cancelFilter('source')">Cancel</el-button>
                <el-button size="small" type="primary" @click="applyFilter('source')">OK</el-button>
              </div>
            </div>
          </el-popover>
        </template>
      </el-table-column>
      <el-table-column prop="message" label="Message" min-width="200" show-overflow-tooltip />
      <el-table-column prop="location" label="Location" width="150" show-overflow-tooltip>
        <template #header>
          <el-popover v-model:visible="filterPopover.location" trigger="manual"  placement="bottom" :width="180" popper-class="alarm-filter-popper" @show="syncDraft('location')">
            <template #reference>
              <span class="header-label" :class="{ 'filter-active': hasFilter('location') }" @click.stop="filterPopover.location = !filterPopover.location">Location
                <el-icon v-if="hasFilter('location')" class="filter-icon active"><Filter /></el-icon>
              </span>
            </template>
            <div class="filter-panel" @click.stop @mousedown.stop>
              <el-input v-model="draft.location" placeholder="Fuzzy match" size="small" clearable style="width: 100%" />
              <div class="filter-actions">
                <el-button size="small" @click="resetFilter('location')">Reset</el-button>
                <el-button size="small" @click="cancelFilter('location')">Cancel</el-button>
                <el-button size="small" type="primary" @click="applyFilter('location')">OK</el-button>
              </div>
            </div>
          </el-popover>
        </template>
      </el-table-column>
      <el-table-column prop="status" label="Status" width="110">
        <template #header>
          <div class="header-cell">
            <el-popover v-model:visible="filterPopover.status" trigger="manual"  placement="bottom" :width="140" popper-class="alarm-filter-popper" @show="syncDraft('status')">
              <template #reference>
                <span class="header-label" :class="{ 'filter-active': hasFilter('status') }" @click.stop="filterPopover.status = !filterPopover.status">Status
                  <el-icon v-if="hasFilter('status')" class="filter-icon active"><Filter /></el-icon>
                </span>
              </template>
              <div class="filter-panel" @click.stop @mousedown.stop>
                <el-select v-model="draft.status" :teleported="false" multiple placeholder="Select" size="small" style="width: 100%" popper-class="alarm-filter-select-popper">
                  <el-option v-for="s in statusOptions" :key="s" :label="s" :value="s" />
                </el-select>
                <div class="filter-actions">
                  <el-button size="small" @click="resetFilter('status')">Reset</el-button>
                  <el-button size="small" @click="cancelFilter('status')">Cancel</el-button>
                  <el-button size="small" type="primary" @click="applyFilter('status')">OK</el-button>
                </div>
              </div>
            </el-popover>
            <span class="sort-icons" @click="toggleSort('status')">
              <el-icon class="sort-icon" :class="{ active: sortField === 'status' && sortOrder === 'asc' }"><CaretTop /></el-icon>
              <el-icon class="sort-icon" :class="{ active: sortField === 'status' && sortOrder === 'desc' }"><CaretBottom /></el-icon>
            </span>
          </div>
        </template>
        <template #default="{ row }">
          <span :class="['status-text', row.status.toLowerCase()]">{{ row.status }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="duration" label="Duration" width="110">
        <template #header>
          <div class="header-cell">
            <span class="header-label">Duration</span>
            <span class="sort-icons" @click="toggleSort('duration')">
              <el-icon class="sort-icon" :class="{ active: sortField === 'duration' && sortOrder === 'asc' }"><CaretTop /></el-icon>
              <el-icon class="sort-icon" :class="{ active: sortField === 'duration' && sortOrder === 'desc' }"><CaretBottom /></el-icon>
            </span>
          </div>
        </template>
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
import { normalizeRecord } from '@/utils/alarm'
import { getSeverityColor } from '@/config/severityColors'
import { Filter, CaretTop, CaretBottom } from '@element-plus/icons-vue'
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

// ── Popover visibility (manual trigger to prevent closing on internal clicks) ──
const filterPopover = ref({
  time: false,
  severity: false,
  source: false,
  location: false,
  status: false,
})

// ── Column filters ──
// filters = committed (applied to filteredData); draft = in-progress in popover
const filters = ref({
  timeStart: '',
  timeEnd: '',
  severity: [] as string[],
  source: '',
  location: '',
  status: [] as string[],
})
const draft = ref({
  timeStart: '',
  timeEnd: '',
  severity: [] as string[],
  source: '',
  location: '',
  status: [] as string[],
})

function syncDraft(field: string) {
  switch (field) {
    case 'time':
      draft.value.timeStart = filters.value.timeStart
      draft.value.timeEnd = filters.value.timeEnd
      break
    case 'severity': draft.value.severity = [...filters.value.severity]; break
    case 'source': draft.value.source = filters.value.source; break
    case 'location': draft.value.location = filters.value.location; break
    case 'status': draft.value.status = [...filters.value.status]; break
  }
}

function applyFilter(field: string) {
  switch (field) {
    case 'time':
      filters.value.timeStart = draft.value.timeStart
      filters.value.timeEnd = draft.value.timeEnd
      break
    case 'severity': filters.value.severity = [...draft.value.severity]; break
    case 'source': filters.value.source = draft.value.source; break
    case 'location': filters.value.location = draft.value.location; break
    case 'status': filters.value.status = [...draft.value.status]; break
  }
  // Close the popover after applying
  filterPopover.value[field as keyof typeof filterPopover.value] = false
}

function cancelFilter(field: string) {
  filterPopover.value[field as keyof typeof filterPopover.value] = false
}

function resetFilter(field: string) {
  switch (field) {
    case 'time':
      draft.value.timeStart = ''
      draft.value.timeEnd = ''
      filters.value.timeStart = ''
      filters.value.timeEnd = ''
      break
    case 'severity':
      draft.value.severity = []
      filters.value.severity = []
      break
    case 'source':
      draft.value.source = ''
      filters.value.source = ''
      break
    case 'location':
      draft.value.location = ''
      filters.value.location = ''
      break
    case 'status':
      draft.value.status = []
      filters.value.status = []
      break
  }
}

function hasFilter(field: string): boolean {
  const f = filters.value
  switch (field) {
    case 'time': return f.timeStart !== '' || f.timeEnd !== ''
    case 'severity': return f.severity.length > 0
    case 'source': return f.source.trim() !== ''
    case 'location': return f.location.trim() !== ''
    case 'status': return f.status.length > 0
    default: return false
  }
}

const severityOptions = ['Critical', 'Major', 'Minor', 'Warning']
const statusOptions = ['Unack', 'Ack']

// ── Column sorting ──
const sortField = ref<string | null>(null)
const sortOrder = ref<'asc' | 'desc' | null>(null)

function toggleSort(field: string) {
  if (sortField.value !== field) {
    sortField.value = field
    sortOrder.value = 'desc'
  } else if (sortOrder.value === 'desc') {
    sortOrder.value = 'asc'
  } else if (sortOrder.value === 'asc') {
    sortField.value = null
    sortOrder.value = null
  } else {
    sortOrder.value = 'desc'
  }
}

function severityWeight(s: string): number {
  return { Critical: 0, Major: 1, Minor: 2, Warning: 3 }[s] ?? 99
}

function statusWeight(s: string): number {
  return { Unack: 0, Ack: 1 }[s] ?? 99
}

/** Compute actual duration in seconds for sorting.
 *  The row's time is now always the alarm start timestamp (notifications carry
 *  the historical start time), so the live duration is simply (now - alarmTime).
 *  Recovered alarms (stage=end) freeze at the stored duration value. */
function computeActualDuration(row: AlarmRecord): number {
  if (row.stage === 'end') {
    return parseFloat(row.duration || '0') || 0
  }
  try {
    const alarmDate = new Date((row.time || '').replace(' ', 'T'))
    if (!isNaN(alarmDate.getTime())) {
      return Math.max(0, (Date.now() - alarmDate.getTime()) / 1000)
    }
  } catch { /* ignore */ }
  return parseFloat(row.duration || '0') || 0
}

// ── Time picker draft values (datetimerange with single-panel) ──
const draftTimeRange = ref<[Date, Date] | null>(null)

function syncTimeDraft() {
  if (filters.value.timeStart && filters.value.timeEnd) {
    draftTimeRange.value = [new Date(filters.value.timeStart), new Date(filters.value.timeEnd)]
  } else {
    draftTimeRange.value = null
  }
}

function formatTimeStr(d: Date | null): string {
  if (!d) return ''
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

function applyTimeFilter() {
  if (draftTimeRange.value && draftTimeRange.value.length === 2) {
    draft.value.timeStart = formatTimeStr(draftTimeRange.value[0])
    draft.value.timeEnd = formatTimeStr(draftTimeRange.value[1])
  } else {
    draft.value.timeStart = ''
    draft.value.timeEnd = ''
  }
  // Commit to filters and close popover
  filters.value.timeStart = draft.value.timeStart
  filters.value.timeEnd = draft.value.timeEnd
  filterPopover.value.time = false
}

function cancelTimeFilter() {
  filterPopover.value.time = false
}

function resetTimeFilter() {
  draftTimeRange.value = null
  resetFilter('time')
}

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

/** Build inline style for the severity tag.
 *  - Normal: colored text + semi-transparent bg + colored border
 *  - Reversed (recovered unack, stage=end + Unack): background = table bg,
 *    text + border = severity color (inverted effect) */
function severityStyle(row: AlarmRecord): Record<string, string> {
  const color = getSeverityColor(row.severity)
  const isReversed = row.stage === 'end'
  return {
    color: isReversed ? color : '#111827',
    backgroundColor: isReversed ? '#111827' : color,
    borderColor: isReversed ? color : '#111827',
  }
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
])

const filteredData = computed(() => {
  const deviceFiltered = props.deviceName
    ? tableData.value.filter(a => matchesDevice(a))
    : tableData.value
  let result: AlarmRecord[]
  switch (activeTab.value) {
    case 'unacknowledged':
      result = deviceFiltered.filter(a => a.status === 'Unack')
      break
    case 'acknowledged':
      result = deviceFiltered.filter(a => a.status === 'Ack')
      break
    default:
      result = deviceFiltered
      break
  }
  // Apply column filters
  result = result.filter(row => {
    if (filters.value.timeStart && row.time < filters.value.timeStart) return false
    if (filters.value.timeEnd && row.time > filters.value.timeEnd) return false
    if (filters.value.severity.length > 0 && !filters.value.severity.includes(row.severity)) return false
    if (filters.value.source && !row.source.toLowerCase().includes(filters.value.source.toLowerCase())) return false
    if (filters.value.location && !row.location.toLowerCase().includes(filters.value.location.toLowerCase())) return false
    if (filters.value.status.length > 0 && !filters.value.status.includes(row.status)) return false
    return true
  })
  // Apply sorting
  if (sortField.value && sortOrder.value) {
    const field = sortField.value
    const order = sortOrder.value === 'asc' ? 1 : -1
    result = [...result].sort((a, b) => {
      let cmp = 0
      if (field === 'time') {
        cmp = (a.time || '').localeCompare(b.time || '')
      } else if (field === 'severity') {
        cmp = severityWeight(a.severity) - severityWeight(b.severity)
      } else if (field === 'status') {
        cmp = statusWeight(a.status) - statusWeight(b.status)
      } else if (field === 'duration') {
        // Compute actual duration: for ongoing alarms, it's (now - alarmTime + baseDuration);
        // for recovered alarms (stage=end), it's just baseDuration.
        const durA = computeActualDuration(a)
        const durB = computeActualDuration(b)
        cmp = durA - durB
      }
      return cmp * order
    })
  }
  return result
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
  fontSize: '10px',
  fontWeight: '600',
  borderBottom: '1px solid #2d3748',
}

const cellStyle = {
  backgroundColor: '#111827',
  color: '#e5e7eb',
  fontSize: '10px',
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

  //TODO: 这个代码是否多余？
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

  // All active alarms are shown; no row cap so operators see the full picture.

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
  padding: 0 8px;
  gap: 8px;

  // Transparent-background action buttons: green text + green border, light-green fill on hover.
  // Overrides Element Plus default (primary/success) styling for a consistent outline style.
  :deep(.el-button) {
    background-color: transparent;
    border: 1px solid $accent-green;
    color: $accent-green;
    font-size: 10px;
    padding: 0 8px;
    margin: 0;
    height: 20px;
    transition: background-color 0.2s, color 0.2s, border-color 0.2s, opacity 0.2s;

    &:hover,
    &:focus {
      background-color: rgba($accent-green, 0.15);
      border-color: $accent-green;
      color: $accent-green;
    }

    // Keep disabled Ack button muted (no alarm selected)
    &.is-disabled,
    &.is-disabled:hover {
      background-color: transparent;
      border-color: $border-card;
      color: $text-muted;
      cursor: not-allowed;
      opacity: 0.6;
    }
  }
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
  font-size: 10px;
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

  .el-table .cell {
    line-height: 20px;
  }

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

  // Compact row height: reduce cell padding + line-height
  .el-table__cell {
    padding: 0 0 !important;
  }
  .el-table__cell .cell {
    padding: 0 4px !important;
  }
  .el-table__header-cell {
    padding: 0 4px !important;
  }
  // .cell is the inner div - Element Plus sets line-height: 23px which
  // dominates the row height. Override with higher specificity + !important.
  // NOTE: EP table header <th> only carries the `el-table__cell` class (NOT
  // `el-table__header-cell`), so we target `th.el-table__cell` for headers.
  td.el-table__cell .cell,
  th.el-table__cell .cell {
    padding: 0 4px !important;
    line-height: 20px !important;
    height: 20px !important;
    display: flex;
    align-items: center;
    justify-content: flex-start;
  }
  // Center-align cells that use text (non-flex content)
  td.el-table__cell.is-center .cell {
    justify-content: center;
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
  width: 55px;
  height: 16px;
  line-height: 16px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 10px;
  font-weight: 600;
  border: 1px solid;
  border-radius: 3px;
  /* color / backgroundColor / borderColor set via inline style from severityStyle() */
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

// Shrink checkboxes to fit the compact 20px row height (Element Plus default is 32px)
:deep(.el-checkbox) {
  height: 16px;
  .el-checkbox__inner {
    width: 12px;
    height: 12px;
    border-radius: 2px;
    // Scale the checkmark down to match the smaller box
    // &::after {
    //   width: 3px;
    //   height: 7px;
    // }
  }
  .el-checkbox__label {
    font-size: 10px;
    padding-left: 6px;
  }
}

// ── Header filter & sort styles ──
.header-cell {
  display: inline-flex;
  align-items: center;
  gap: 2px;
}

.header-label {
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  gap: 2px;
  font-size: 10px;
  color: $text-secondary;

  &.filter-active {
    color: $accent-green;
  }
}

.filter-icon {
  font-size: 10px;
  &.active { color: $accent-green; }
}


.sort-icons {
  display: inline-flex;
  flex-direction: column;
  cursor: pointer;
  line-height: 0;
}

.sort-icon {
  font-size: 8px;
  color: $text-muted;
  height: 8px;
  &.active { color: $accent-green; }
}

.filter-panel {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.filter-row {
  display: flex;
  gap: 4px;
}

.filter-actions {
  display: flex;
  justify-content: flex-end;
  gap: 4px;
  margin-top: 2px;
}
</style>

<!-- Global styles for teleported filter popovers -->
<style lang="scss">
.alarm-filter-popper.el-popover.el-popper {
  background-color: #0a0e17;
  border: 1px solid #2d3748;
  color: #e5e7eb;
  padding: 6px !important;
  min-width: 120px;

  .el-popper__arrow::before {
    background-color: #0a0e17;
    border-color: #2d3748;
  }

  // Compact buttons - minimal size to prevent overflow
  .el-button {
    height: 18px;
    padding: 0 4px;
    margin: 0 2px;
    font-size: 10px;
    line-height: 18px;
    min-height: 18px;
    span { font-size: 10px; }
  }

  // Input dark theme
  .el-input__wrapper {
    background-color: #111827;
    box-shadow: 0 0 0 1px #2d3748 inset;
    padding: 0 4px;
    &:hover { box-shadow: 0 0 0 1px #6b7280 inset; }
  }
  .el-input__inner {
    color: #e5e7eb;
    font-size: 10px;
    height: 20px;
    line-height: 20px;
    &::placeholder { color: #6b7280; -webkit-text-fill-color: #6b7280; }
  }

  // Select dark theme
  .el-select__wrapper {
    background-color: #111827;
    box-shadow: 0 0 0 1px #2d3748 inset;
    min-height: 20px;
    padding: 0 4px;
    &:hover { box-shadow: 0 0 0 1px #6b7280 inset; }
  }
  .el-select__placeholder { color: #6b7280; font-size: 10px; }
  .el-select__selected-item { color: #e5e7eb; font-size: 10px; }
  .el-select__caret { color: #9ca3af; }
  .el-tag {
    height: 14px;
    font-size: 10px;
    padding: 0 4px;
    line-height: 12px;
  }

  // Date range picker dark theme + compact
  .el-date-editor--datetimerange {
    width: 100% !important;
    height: 20px;
    .el-input__wrapper {
      background-color: #111827;
      box-shadow: 0 0 0 1px #2d3748 inset;
      padding: 0 4px;
    }
    .el-input__inner {
      color: #e5e7eb;
      font-size: 10px;
      height: 20px;
      &::placeholder { color: #6b7280; -webkit-text-fill-color: #6b7280; }
    }
    .el-range-separator { color: #6b7280; font-size: 10px; line-height: 20px; }
    .el-range__icon { font-size: 10px; line-height: 20px; }
  }
}

// Select dropdown options for filter popovers - dark theme + compact
// Uses a unique class name to avoid leaking styles into AlarmConfigView
.alarm-filter-select-popper.el-select__popper {
  .el-select-dropdown {
    background-color: #111827;
    border: 1px solid #2d3748;
  }
  .el-select-dropdown__item {
    color: #9ca3af;
    font-size: 10px;
    height: 22px;
    line-height: 22px;
    padding: 0 8px;
    &:hover, &.is-hovering { background-color: #1a2332; color: #e5e7eb; }
    &.is-selected { color: #10b981; }
  }
  .el-popper__arrow::before { background-color: #111827; border-color: #2d3748; }
}

// Date picker dropdown panel (teleported) - dark + compact
.el-popper.el-picker__popper,
.alarm-datepicker-popper.el-popper {
  .el-date-picker, .el-time-panel, .el-picker-panel {
    background-color: #111827;
    border: 1px solid #2d3748;
    color: #e5e7eb;
  }
  .el-picker-panel__icon-btn { color: #9ca3af; }
  .el-date-picker__header-label { color: #e5e7eb; font-size: 10px; }
  .el-date-table th { color: #9ca3af; font-size: 10px; padding: 2px 0; }
  .el-date-table td { padding: 1px 0; }
  .el-date-table td .el-date-table-cell { height: 20px; padding: 0; }
  .el-date-table td .el-date-table-cell__text { color: #e5e7eb; font-size: 10px; height: 20px; line-height: 20px; }
  .el-date-table td.today .el-date-table-cell__text { color: #10b981; }
  .el-date-table td.current:not(.disabled) .el-date-table-cell__text {
    background-color: #10b981; color: #0a0e17;
  }
  .el-date-table td.disabled .el-date-table-cell__text { color: #4b5563; }

  // Time panel borders
  .el-time-panel__content::before, .el-time-panel__content::after { border-color: #2d3748; }

  // Time spinner - keep default height for proper scroll alignment, just shrink font
  // .el-time-spinner__wrapper { max-height: 120px; }
  .el-time-spinner__item { 
     color: #9ca3af; font-size: 10px;
    //  height: 28px; line-height: 28px; 
    }
  .el-time-spinner__item.is-active { color: #10b981; }
  .el-time-panel__btn { color: #9ca3af; font-size: 10px; }
  .el-time-panel__btn.confirm { color: #10b981; }

  // Footer buttons
  .el-picker-panel__footer {
    background-color: #111827;
    border-top: 1px solid #2d3748;
    .el-button {
      height: 18px;
      font-size: 10px;
      padding: 0 4px;
      background-color: #111827;
      color: #9ca3af;
      border-color: #2d3748;
      &:hover { color: #e5e7eb; border-color: #6b7280; }
      &--primary { color: #10b981; border-color: #10b981; }
    }
  }

  // Date range picker sidebar/panels background
  .el-picker-panel__sidebar { background-color: #111827; border-color: #2d3748; }
  .el-picker-panel__body { background-color: #111827; }
  .el-date-range-picker__content.is-left { border-right-color: #2d3748; }
  .el-date-range-picker__header { color: #e5e7eb; }
  .el-time-panel { background-color: #111827; border-color: #2d3748; }
  .el-time-panel__footer { border-top-color: #2d3748; }

  .el-popper__arrow::before { background-color: #111827; border-color: #2d3748; }
}
</style>
