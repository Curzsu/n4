<template>
  <div class="bottom-toolbar">
    <div
      v-for="item in toolbarItems"
      :key="item.label"
      class="toolbar-item"
    >
      <el-icon :size="18"><component :is="item.icon" /></el-icon>
      <span class="toolbar-label">{{ item.label }}</span>
      <span v-if="item.badge" class="toolbar-badge">{{ item.badge }}</span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { Bell, TrendCharts, Document, Tools, Setting } from '@element-plus/icons-vue'
import { useDashboardStore } from '@/stores/dashboard'

const store = useDashboardStore()

const toolbarItems = computed(() => [
  { label: 'Alarm Center', icon: Bell, badge: store.alarmSummary?.critical || 0 },
  { label: 'Trend Viewer', icon: TrendCharts, badge: null },
  { label: 'Report Center', icon: Document, badge: null },
  { label: 'Work Order', icon: Tools, badge: null },
  { label: 'System Settings', icon: Setting, badge: null },
])
</script>

<style scoped lang="scss">
.bottom-toolbar {
  height: $bottombar-height;
  background-color: $bg-card;
  border-top: 1px solid $border-color;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 32px;
  flex-shrink: 0;
}

.toolbar-item {
  display: flex;
  align-items: center;
  gap: 6px;
  color: $text-secondary;
  cursor: pointer;
  padding: 4px 12px;
  border-radius: 4px;
  transition: all 0.2s;
  &:hover {
    color: $text-primary;
    background-color: $bg-card-hover;
  }
}

.toolbar-label {
  font-size: 12px;
}

.toolbar-badge {
  background-color: $accent-red;
  color: white;
  font-size: 10px;
  font-weight: 600;
  padding: 1px 4px;
  border-radius: 8px;
  min-width: 16px;
  text-align: center;
}
</style>
