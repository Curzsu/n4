<template>
  <div class="alarm-history-view">
    <div class="config-header">
      <span class="config-title">Alarm History</span>
      <el-button type="primary" size="small" @click="fetchData">
        <el-icon><Refresh /></el-icon>
        <span>Refresh</span>
      </el-button>
    </div>

    <div class="history-table-wrapper">
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
        <el-table-column prop="stageMessage" label="Stage" width="150" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="stagemessage-text">{{ row.stageMessage || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="severity" label="Severity" width="90">
          <template #default="{ row }">
            <span class="severity-tag" :style="severityStyle(row)">{{ row.severity }}</span>
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
        <el-table-column prop="duration" label="Duration" width="100" />
        <el-table-column prop="ackBy" label="Ack. By" width="100" show-overflow-tooltip />
      </el-table>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import { alarmApi } from '@/api/alarm'
import { normalizeRecord } from '@/utils/alarm'
import { getSeverityColor } from '@/config/severityColors'
import type { AlarmRecord } from '@/api/types'

const loading = ref(false)
const tableData = ref<AlarmRecord[]>([])
const tableHeight = ref(400)

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

async function fetchData() {
  loading.value = true
  try {
    const raw = await alarmApi.getClearedAlarms()
    tableData.value = raw.map(normalizeRecord)
  } catch (e) {
    console.error('Failed to fetch alarm history:', e)
  } finally {
    loading.value = false
  }
}

function updateTableHeight() {
  tableHeight.value = Math.max(300, window.innerHeight - 200)
}

onMounted(() => {
  updateTableHeight()
  window.addEventListener('resize', updateTableHeight)
  fetchData()
})
</script>

<style scoped lang="scss">
.alarm-history-view {
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

.config-title {
  font-size: 16px;
  font-weight: 600;
  color: $text-primary;
}

.history-table-wrapper {
  flex: 1;
  min-height: 0;
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
