<template>
  <div class="alarm-event-view">
    <div class="config-header">
      <span class="config-title">Event</span>
      <div class="header-actions">
        <el-button type="primary" size="small" @click="fetchData">
          <el-icon><Refresh /></el-icon>
          <span>Refresh</span>
        </el-button>
        <el-button size="small" class="btn-secondary" :icon="Download" @click="exportVisible = true">
          Export
        </el-button>
      </div>
    </div>

    <div ref="tableWrapperRef" class="event-table-wrapper" @scroll.capture="onTableScroll">
      <el-table
        :data="tableData"
        :height="tableHeight"
        style="width: 100%"
        :header-cell-style="headerCellStyle"
        :cell-style="cellStyle"
        size="small"
        border
        v-loading="loading"
      >
        <el-table-column prop="time" label="Time" width="160" show-overflow-tooltip />
        <el-table-column prop="value" label="Value" width="120" show-overflow-tooltip />
        <el-table-column prop="stageMessage" label="Stage" width="80" show-overflow-tooltip>
          <template #default="{ row }">
            <span v-if="row.stageMessage" class="stagemessage-text">{{ row.stageMessage }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="severity" label="Severity" width="90">
          <template #default="{ row }">
            <span v-if="row.severity" class="severity-tag" :style="severityStyle(row as AlarmRecord)">{{ row.severity }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="source" label="Source" min-width="200" show-overflow-tooltip />
        <el-table-column prop="message" label="Message" min-width="200" show-overflow-tooltip />
        <el-table-column prop="location" label="Location" width="150" show-overflow-tooltip />
        <el-table-column prop="status" label="Status" width="80">
          <template #default="{ row }">
            <span :class="['status-text', row.status.toLowerCase()]">{{ row.status }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="duration" label="Duration" width="120">
          <template #default="{ row }">
            <span class="duration-text">{{ formatDuration(Number(row.duration) || 0) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="ackBy" label="Ack. By" width="100" show-overflow-tooltip />
        <template #append>
          <div class="table-window-hint">
            <template v-if="loadingMore">Loading more...</template>
            <template v-else-if="hasMore">
              Showing {{ tableData.length }} records - scroll to bottom to load earlier ones
            </template>
            <template v-else>Showing all {{ tableData.length }} records</template>
          </div>
        </template>
      </el-table>
    </div>

    <EventExportDialog v-model:visible="exportVisible" />
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount } from 'vue'
import { Refresh, Download } from '@element-plus/icons-vue'
import { alarmApi } from '@/api/alarm'
import { normalizeRecord } from '@/utils/alarm'
import { getSeverityColor } from '@/config/severityColors'
import { formatDuration } from '@/utils/time'
import EventExportDialog from '@/components/EventExportDialog.vue'
import type { AlarmRecord } from '@/api/types'

const loading = ref(false)
const loadingMore = ref(false)
const tableData = ref<AlarmRecord[]>([])
const tableWrapperRef = ref<HTMLElement | null>(null)
const tableHeight = ref(400)
const exportVisible = ref(false)

// Scroll pagination: each request asks for EVENT_PAGE_SIZE rows. Entering the
// page or hitting Refresh pins snapshotTs to that wall-clock instant; every
// page of the session (first one included) queries ts <= snapshotTs, and later
// pages pass it back with an offset, so rows written afterwards never shift
// the ordering and pages stay stable. hasMore turns false once a page comes
// back short of the page size.
const EVENT_PAGE_SIZE = 50
const SCROLL_BOTTOM_THRESHOLD_PX = 40
const snapshotTs = ref<number | null>(null)
const hasMore = ref(true)

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

function severityStyle(row: AlarmRecord): Record<string, string> {
  const color = getSeverityColor(row.severity)
  // Normal alarm tag: solid severity-colored fill with dark text (matches the
  // Active Alarms non-reversed tag style). All rows show the tag now.
  return {
    color: '#111827',
    backgroundColor: color,
    borderColor: '#111827',
  }
}

function toRecords(raw: AlarmRecord[]): AlarmRecord[] {
  // Keep status as-is from the database (empty stays empty, no default Unack);
  // other fields go through the shared normalizer for consistent formatting.
  return raw.map((r) => {
    const record = normalizeRecord(r)
    record.status = r.status || ''
    return record
  })
}

async function fetchData() {
  loading.value = true
  try {
    // Pin the snapshot to the moment the user enters or hits Refresh: every
    // page of this browsing session queries strictly before this instant, so
    // rows written afterwards never shift the ordering under the reader.
    snapshotTs.value = Date.now()
    const raw = await alarmApi.getAlarmEvents({ topTs: snapshotTs.value, limit: EVENT_PAGE_SIZE })
    tableData.value = toRecords(raw)
    hasMore.value = raw.length >= EVENT_PAGE_SIZE
  } catch (e) {
    console.error('Failed to fetch alarm events:', e)
  } finally {
    loading.value = false
  }
}

async function loadMore() {
  if (loadingMore.value || !hasMore.value) return
  loadingMore.value = true
  try {
    const raw = await alarmApi.getAlarmEvents({
      topTs: snapshotTs.value ?? undefined,
      offset: tableData.value.length,
      limit: EVENT_PAGE_SIZE,
    })
    tableData.value.push(...toRecords(raw))
    hasMore.value = raw.length >= EVENT_PAGE_SIZE
  } catch (e) {
    console.error('Failed to load more alarm events:', e)
  } finally {
    loadingMore.value = false
  }
}

function onTableScroll(event: Event) {
  const el = event.target as HTMLElement
  if (el.scrollTop + el.clientHeight >= el.scrollHeight - SCROLL_BOTTOM_THRESHOLD_PX) {
    loadMore()
  }
}

// Size the table from the measured wrapper height instead of a magic
// window.innerHeight offset: the wrapper is the flex child that already
// absorbs the real page chrome (top navbar, paddings), so the table fills
// the viewport exactly and leaves no blank strip below it. The
// ResizeObserver keeps it in sync on window resizes.
let wrapperResizeObserver: ResizeObserver | null = null

function updateTableHeight() {
  if (tableWrapperRef.value) {
    tableHeight.value = Math.max(300, tableWrapperRef.value.clientHeight)
  }
}

onMounted(() => {
  updateTableHeight()
  if (tableWrapperRef.value) {
    wrapperResizeObserver = new ResizeObserver(updateTableHeight)
    wrapperResizeObserver.observe(tableWrapperRef.value)
  }
  fetchData()
})

onBeforeUnmount(() => {
  wrapperResizeObserver?.disconnect()
  wrapperResizeObserver = null
})
</script>

<style scoped lang="scss">
.alarm-event-view {
  display: flex;
  flex-direction: column;
  gap: 12px;
  min-height: 100%;
}

.config-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-shrink: 0;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

// Button styling copied from SystemDeploymentView.vue (Device Template /
// Device Deployment buttons) so the Event header matches that look. Scoped
// on purpose - promoting it to a global style would restyle every el-button
// in the app.
:deep(.el-button) {
  border-radius: 9px !important;
  border: none !important;
  font-size: 10px !important;
  font-weight: 400 !important;
  padding: 2px 8px !important;
  height: 20px !important;
  line-height: 16px !important;
  transition: all 0.2s ease-in-out;

  &:hover {
    transform: translateY(-0.5px);
  }
}

.btn-primary {
  background-color: rgba($accent-green, 0.16) !important;
  color: #34d399 !important;

  &:hover {
    background-color: rgba($accent-green, 0.28) !important;
    color: #6ee7b7 !important;
  }
}

.btn-secondary {
  background-color: rgba($accent-blue, 0.16) !important;
  color: #93c5fd !important;

  &:hover {
    background-color: rgba($accent-blue, 0.28) !important;
    color: #bfdbfe !important;
  }
}

.config-title {
  font-size: 16px;
  font-weight: 600;
  color: $text-primary;
}

.event-table-wrapper {
  flex: 1;
  min-height: 0;
}

// Footer inside the el-table #append slot: scroll-pagination status.
.table-window-hint {
  padding: 6px 10px;
  font-size: 10px;
  color: #6b7280;
  background-color: #111827;
  border-bottom: 1px solid #1f2937;
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
}

.stage-message-text {
  font-size: 10px;
  color: #e5e7eb;
}

.duration-text {
  font-size: 10px;
  font-variant-numeric: tabular-nums;
  color: #e5e7eb;
}

.status-text {
  font-size: 10px;
  &.unack { color: $accent-red; }
  &.ack { color: $accent-green; }
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
  .el-table__inner-wrapper { background-color: #111827; }
  .el-table__body-wrapper { background-color: #111827; }
  .el-table__header-wrapper { background-color: #0d1117; }
  .el-table__row { background-color: #111827; }
  .el-table__row:hover > td { background-color: #1a2332 !important; }
  td, th { border-bottom: 1px solid #1f2937; }
  .el-table__cell { padding: 0 4px !important; }
  .el-table__header-cell { padding: 0 4px !important; }
  // NOTE: EP table header <th> only carries the `el-table__cell` class (NOT
  // `el-table__header-cell`), so we target `th.el-table__cell` for headers.
  td.el-table__cell .cell,
  th.el-table__cell .cell {
    padding: 0 4px !important;
    line-height: 20px !important;
    height: 20px !important;
    display: flex;
    align-items: center;
  }
}

:deep(.el-loading-mask) {
  background-color: rgba(10, 14, 23, 0.7);
}
</style>
