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
import { Bell, TrendCharts, Document, Tools, Setting, Clock, Cpu, Operation, User, Key, Lock, Tickets } from '@element-plus/icons-vue'
import { useAlarmWebSocket } from '@/composables/useAlarmWebSocket'

// Real-time active alarm count (sum of all severities) shared across the app
// via the WebSocket singleton. Mirrors the Active Alarms table row count.
const { activeAlarmCount } = useAlarmWebSocket()
const route = useRoute()
const router = useRouter()

// System second-level routes take precedence over the /alarm prefix check
const systemPaths = [
  '/device-template',
  '/device-deployment',
  '/system-deployment',
  '/alarm/config',
  '/system/users',
  '/system/groups',
  '/system/permissions',
  '/logs',
]
const isSystemRoute = computed(() => systemPaths.some((p) => route.path.startsWith(p)))
const isAlarmRoute = computed(() => route.path.startsWith('/alarm') && !isSystemRoute.value)
// Event is its own top-level route with no second-level menu.
const isEventRoute = computed(() => route.path === '/event')

// Items shown on the alarm route: Active Alarms / Alarm History
const alarmItems = computed(() => [
  {
    label: 'Active Alarms',
    icon: Bell,
    badge: activeAlarmCount.value,
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

// System second-level menu
const systemItems = computed(() => [
  { label: 'Device Template', icon: Cpu, badge: null, active: route.path === '/device-template', to: '/device-template' },
  { label: 'Device Deployment', icon: Operation, badge: null, active: route.path === '/device-deployment' || route.path === '/system-deployment', to: '/device-deployment' },
  { label: 'Alarm Configuration', icon: Setting, badge: null, active: route.path === '/alarm/config', to: '/alarm/config' },
  { label: 'User Management', icon: User, badge: null, active: route.path === '/system/users', to: '/system/users' },
  { label: 'Role Management', icon: Key, badge: null, active: route.path === '/system/groups', to: '/system/groups' },
  { label: 'Permissions', icon: Lock, badge: null, active: route.path === '/system/permissions', to: '/system/permissions' },
  { label: 'Audit Logs', icon: Tickets, badge: null, active: route.path === '/logs', to: '/logs' },
])

// Items shown on all other routes
const defaultItems = computed(() => [
  { label: 'Alarm Center', icon: Bell, badge: activeAlarmCount.value, active: false, to: '/alarm' },
  { label: 'Trend Viewer', icon: TrendCharts, badge: null, active: false, to: '' },
  { label: 'Report Center', icon: Document, badge: null, active: false, to: '' },
  { label: 'Work Order', icon: Tools, badge: null, active: false, to: '' },
  { label: 'System Settings', icon: Setting, badge: null, active: false, to: '/device-template' },
])

const toolbarItems = computed(() => {
  if (isSystemRoute.value) return systemItems.value
  if (isAlarmRoute.value) return alarmItems.value
  // Event route has no second-level menu items.
  if (isEventRoute.value) return []
  return defaultItems.value
})

function onItemClick(item: { to?: string }) {
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
  gap: 24px;
  flex-shrink: 0;
}

.toolbar-item {
  display: flex;
  align-items: center;
  gap: 6px;
  color: $text-secondary;
  cursor: pointer;
  padding: 4px 10px;
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
