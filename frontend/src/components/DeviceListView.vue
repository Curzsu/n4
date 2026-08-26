<template>
  <div class="device-list-view">
    <div class="list-header">
      <h3>{{ title }}</h3>
      <span class="device-count">Total: {{ devices.length }} devices</span>
    </div>
    <el-table
      :data="devices"
      style="width: 100%"
      v-loading="loading"
      empty-text="No devices"
      size="small"
      border
      class="custom-table"
      :header-cell-style="headerCellStyle"
      :cell-style="cellStyle"
    >
      <el-table-column prop="deviceDeployId" label="ID" width="60" align="center" :resizable="false" />
      <el-table-column prop="deviceDeployName" label="Device Name" min-width="200" show-overflow-tooltip :resizable="false">
        <template #default="{ row }">
          <span :class="{ 'mock-name': row.isMock }">{{ row.deviceDeployName }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="deviceType" label="Device Type" min-width="120" align="center" :resizable="false" />
      <el-table-column prop="manufacturer" label="Manufacturer" min-width="120" align="center" :resizable="false" />
      <el-table-column label="Status" width="100" align="center" :resizable="false">
        <template #default="{ row }">
          <span class="status-dot" :class="getStatusClass(row.status)"></span>
          <span class="status-text">{{ getStatusText(row.status) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="Actions" width="100" fixed="right" align="center" :resizable="false">
        <template #default="{ row }">
          <el-button type="primary" size="small" link @click="$emit('view-monitor', row as DeviceDeployItem)">
            Monitor
          </el-button>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script setup lang="ts">
import type { DeviceDeployItem } from '@/api/types'

defineProps<{
  devices: DeviceDeployItem[]
  title: string
  loading?: boolean
}>()

defineEmits<{
  (e: 'view-monitor', device: DeviceDeployItem): void
}>()

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

function getStatusClass(status: number): string {
  switch (status) {
    case 1: return 'online'
    case 2: return 'offline'
    case 3: return 'maintenance'
    case 4: return 'fault'
    default: return 'offline'
  }
}

function getStatusText(status: number): string {
  switch (status) {
    case 1: return 'Online'
    case 2: return 'Offline'
    case 3: return 'Maintenance'
    case 4: return 'Fault'
    default: return 'Unknown'
  }
}
</script>

<style scoped lang="scss">
.device-list-view {
  background-color: $bg-card;
  border: 1px solid $border-color;
  border-radius: 6px;
  padding: 10px 12px;
}

.list-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;

  h3 {
    margin: 0;
    font-size: 11px;
    font-weight: 600;
    color: $text-primary;
  }
  .device-count {
    font-size: 10px;
    color: $text-secondary;
  }
}

.mock-name {
  color: $text-secondary;
}

.custom-table {
  --el-table-border-color: #1f2937;
  --el-table-header-bg-color: #0d1117;
  --el-table-text-color: #e5e7eb;
  --el-table-header-text-color: #e5e7eb;

  font-size: 10px !important;

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

  :deep(.el-table__cell) {
    padding: 0 0 !important;
  }
  :deep(.el-table__cell .cell) {
    padding: 0 4px !important;
  }
  :deep(.el-table__header-cell) {
    padding: 0 4px !important;
  }
  :deep(td.el-table__cell .cell),
  :deep(th.el-table__cell .cell) {
    padding: 0 4px !important;
    line-height: 20px !important;
    height: 20px !important;
    font-size: 10px !important;
    display: flex;
    align-items: center;
    justify-content: flex-start;
  }
  :deep(td.el-table__cell.is-center .cell) {
    justify-content: center;
  }
  :deep(th.el-table__cell.is-center .cell) {
    justify-content: center;
  }
}

.status-dot {
  display: inline-block;
  width: 5px;
  height: 5px;
  border-radius: 50%;
  margin-right: 4px;
  &.online { background-color: $accent-green; box-shadow: 0 0 4px rgba(0,230,118,0.6); }
  &.offline { background-color: $text-muted; }
  &.maintenance { background-color: $accent-yellow; box-shadow: 0 0 4px rgba(255,213,79,0.6); }
  &.fault { background-color: $accent-red; box-shadow: 0 0 4px rgba(255,82,82,0.6); }
}
.status-text {
  font-size: 10px;
  color: $text-secondary;
}
</style>
