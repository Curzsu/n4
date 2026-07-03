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

    <!-- Table -->
    <el-table
      :data="tableData"
      style="width: 100%"
      :header-cell-style="headerCellStyle"
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
import { ref, onMounted } from 'vue'
import { alarmApi } from '@/api/alarm'
import type { AlarmRecord } from '@/api/types'

const loading = ref(false)
const tableData = ref<AlarmRecord[]>([])
const activeTab = ref('active')

const tabs = [
  { key: 'active', label: 'Active Alarms', count: '' },
  { key: 'unacknowledged', label: 'Unacknowledged', count: '17' },
  { key: 'acknowledged', label: 'Acknowledged', count: '' },
  { key: 'cleared', label: 'Cleared', count: '' },
]

const headerCellStyle = {
  backgroundColor: '#1a2332',
  color: '#9ca3af',
  fontSize: '12px',
  borderBottom: '1px solid #2d3748',
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

async function fetchData(tab: string) {
  loading.value = true
  try {
    tableData.value = await alarmApi.getActiveAlarms(tab)
  } catch (e) {
    console.error('Failed to fetch alarm data:', e)
  } finally {
    loading.value = false
  }
}

function onTabChange(tab: string) {
  activeTab.value = tab
  fetchData(tab)
}

onMounted(() => {
  fetchData('active')
})
</script>

<style scoped lang="scss">
.alarm-table-panel {
  background-color: $bg-card;
  border: 1px solid $border-card;
  border-radius: 8px;
  overflow: hidden;
}

.tab-bar {
  display: flex;
  border-bottom: 1px solid $border-color;
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
  background-color: transparent;
  color: $text-secondary;

  .el-table__row:hover > td {
    background-color: $bg-card-hover;
  }

  td, th {
    border-bottom: 1px solid $border-color;
  }
}
</style>
