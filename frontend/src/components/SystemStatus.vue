<template>
  <div class="system-status-panel">
    <div class="panel-header">
      <span class="panel-title">System Status</span>
      <span class="view-all-link">View All</span>
    </div>
    <div class="system-list">
      <div
        v-for="item in systemStatus"
        :key="item.systemName"
        class="system-item"
      >
        <el-icon :size="16" class="system-icon">
          <component :is="getIcon(item.icon)" />
        </el-icon>
        <span class="system-name">{{ item.systemName }}</span>
        <span :class="['system-status-badge', item.status.toLowerCase()]">{{ item.status }}</span>
        <span class="system-count">{{ item.deviceCount }}</span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import {
  Setting, Warning, IceCream, HotWater,
  WindPower, Sunny, Share,
} from '@element-plus/icons-vue'
import { useDashboardStore } from '@/stores/dashboard'

const store = useDashboardStore()
const systemStatus = computed(() => store.systemStatus)

const iconMap: Record<string, any> = {
  'gear': Setting,
  'snowflake': IceCream,
  'battery': HotWater,
  'warning-triangle': Warning,
  'fan': WindPower,
  'thermometer': Sunny,
  'globe': Share,
}

function getIcon(iconName: string) {
  return iconMap[iconName] || Setting
}
</script>

<style scoped lang="scss">
.system-status-panel {
  background-color: $bg-card;
  border: 1px solid $border-card;
  border-radius: 8px;
  padding: 12px;
}

.panel-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.panel-title {
  font-size: 14px;
  font-weight: 600;
  color: $text-primary;
}

.view-all-link {
  font-size: 12px;
  color: $accent-blue;
  cursor: pointer;
  &:hover { text-decoration: underline; }
}

.system-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.system-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 0;
}

.system-icon {
  color: $text-muted;
}

.system-name {
  font-size: 13px;
  color: $text-secondary;
  flex: 1;
}

.system-status-badge {
  font-size: 11px;
  padding: 2px 8px;
  border-radius: 4px;
  font-weight: 500;
  &.normal { background: rgba($accent-green, 0.15); color: $accent-green; }
  &.warning { background: rgba($accent-yellow, 0.15); color: $accent-yellow; }
  &.critical { background: rgba($accent-red, 0.15); color: $accent-red; }
}

.system-count {
  font-size: 12px;
  color: $text-muted;
}
</style>
