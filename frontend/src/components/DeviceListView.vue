<template>
  <div class="device-list-view">
    <div class="list-header">
      <h3>{{ title }}</h3>
      <span class="device-count">共 {{ devices.length }} 台设备</span>
    </div>
    <el-table :data="devices" style="width: 100%" v-loading="loading" empty-text="暂无设备">
      <el-table-column prop="deviceDeployId" label="设备ID" min-width="90" />
      <el-table-column prop="deviceDeployName" label="设备名称" min-width="200">
        <template #default="{ row }">
          <span :class="{ 'mock-name': row.isMock }">{{ row.deviceDeployName }}</span>
          <el-tag v-if="row.isMock" size="small" type="info" class="mock-tag">示例数据</el-tag>
          <el-tag v-else size="small" type="success" effect="dark" class="real-tag">真实数据</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="deviceType" label="设备类型" min-width="120" />
      <el-table-column prop="manufacturer" label="厂商" min-width="120" />
      <el-table-column label="状态" min-width="100">
        <template #default="{ row }">
          <span class="status-dot" :class="getStatusClass(row.status)"></span>
          <span class="status-text">{{ getStatusText(row.status) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="120" fixed="right">
        <template #default="{ row }">
          <el-button type="primary" size="small" link @click="$emit('view-monitor', row)">
            查看监控
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
    case 1: return '在线'
    case 2: return '离线'
    case 3: return '维护'
    case 4: return '故障'
    default: return '未知'
  }
}
</script>

<style scoped lang="scss">
.device-list-view {
  background-color: $bg-card;
  border: 1px solid $border-color;
  border-radius: 8px;
  padding: 16px;
}

.list-header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;

  h3 {
    margin: 0;
    font-size: 16px;
    color: $text-primary;
  }
  .device-count {
    font-size: 12px;
    color: $text-secondary;
  }
}

.mock-name {
  color: $text-secondary;
}
.mock-tag {
  margin-left: 8px;
  background-color: $bg-card-hover !important;
  border-color: $border-color !important;
  color: $text-secondary !important;
}
.real-tag {
  margin-left: 8px;
}

.status-dot {
  display: inline-block;
  width: 7px;
  height: 7px;
  border-radius: 50%;
  margin-right: 6px;
  &.online { background-color: $accent-green; box-shadow: 0 0 6px rgba(0,230,118,0.6); }
  &.offline { background-color: $text-muted; }
  &.maintenance { background-color: $accent-yellow; box-shadow: 0 0 6px rgba(255,213,79,0.6); }
  &.fault { background-color: $accent-red; box-shadow: 0 0 6px rgba(255,82,82,0.6); }
}
.status-text {
  font-size: 12px;
  color: $text-secondary;
}
</style>
