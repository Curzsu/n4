<template>
  <div class="bottom-toolbar">
    <div
      v-for="item in toolbarItems"
      :key="item.label"
      :class="['toolbar-item', { active: item.active }]"
      @click="onItemClick(item)"
    >
      <el-icon :size="18"><component :is="item.icon" /></el-icon>
      <span class="toolbar-label">{{ item.label }}</span>
      <span v-if="item.badge" class="toolbar-badge">{{ item.badge }}</span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Bell, TrendCharts, Document, Tools, Setting, Clock, Cpu, Connection } from '@element-plus/icons-vue'
import { useDashboardStore } from '@/stores/dashboard'

const store = useDashboardStore()
const route = useRoute()
const router = useRouter()

// System second-level routes take precedence over the /alarm prefix check,
// because /alarm/config now belongs to the System menu (not the Alarms menu).
const systemPaths = ['/device-template', '/device-deployment', '/alarm/config']
const isSystemRoute = computed(() => systemPaths.includes(route.path))
const isAlarmRoute = computed(() => route.path.startsWith('/alarm') && !isSystemRoute.value)

// Items shown on the alarm route: Active Alarms / Alarm History
const alarmItems = computed(() => [
  {
    label: 'Active Alarms',
    icon: Bell,
    badge: store.alarmSummary?.critical || 0,
    active: route.path === '/alarm',
    to: '/alarm',
  },
  {
    label: 'Alarm History',
    icon: Clock,
    badge: null,
    active: route.path === '/alarm/history',
    to: '/alarm/history',
  },
])

// System second-level menu (order as specified by the user).
const systemItems = computed(() => [
  { label: 'Device Template', icon: Cpu, badge: null, active: route.path === '/device-template', to: '/device-template' },
  { label: 'Device Deployment', icon: Connection, badge: null, active: route.path === '/device-deployment', to: '/device-deployment' },
  { label: 'Alarm Configuration', icon: Setting, badge: null, active: route.path === '/alarm/config', to: '/alarm/config' },
])

// Items shown on all other routes
const defaultItems = computed(() => [
  { label: 'Alarm Center', icon: Bell, badge: store.alarmSummary?.critical || 0, active: false, to: '/alarm' },
  { label: 'Trend Viewer', icon: TrendCharts, badge: null, active: false, to: '' },
  { label: 'Report Center', icon: Document, badge: null, active: false, to: '' },
  { label: 'Work Order', icon: Tools, badge: null, active: false, to: '' },
  { label: 'System Settings', icon: Setting, badge: null, active: false, to: '' },
])

const toolbarItems = computed(() => {
  if (isSystemRoute.value) return systemItems.value
  if (isAlarmRoute.value) return alarmItems.value
  return defaultItems.value
})

function onItemClick(item: typeof alarmItems.value[number]) {
  if (item.to) {
    router.push(item.to)
  }
}
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
  &.active {
    color: $accent-green;
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
