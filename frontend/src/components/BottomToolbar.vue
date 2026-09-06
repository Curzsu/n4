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
    <ChangePasswordDialog v-model="changePasswordVisible" />
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Bell, TrendCharts, Document, Tools, Setting, Clock, Cpu, Operation, Lock } from '@element-plus/icons-vue'
import { ElMessageBox, ElMessage } from 'element-plus'
import { useAlarmWebSocket } from '@/composables/useAlarmWebSocket'
import { useAuthStore } from '@/stores/auth'
import ChangePasswordDialog from '@/components/ChangePasswordDialog.vue'

// Real-time active alarm count (sum of all severities) shared across the app
// via the WebSocket singleton. Mirrors the Active Alarms table row count.
const { activeAlarmCount } = useAlarmWebSocket()
const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const changePasswordVisible = ref(false)

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

const isAuthorityRoute = computed(() =>
  (route.path.startsWith('/system') && route.path !== '/system-deployment') || route.path === '/logs'
)

// System second-level menu
const systemItems = computed(() => [
  { label: 'Device Template', icon: Cpu, badge: null, active: route.path === '/device-template', to: '/device-template' },
  { label: 'Device Deployment', icon: Operation, badge: null, active: route.path === '/device-deployment' || route.path === '/system-deployment', to: '/device-deployment' },
  { label: 'Alarm Configuration', icon: Setting, badge: null, active: route.path === '/alarm/config', to: '/alarm/config' },
  { label: 'Authority Management', icon: Lock, badge: null, active: isAuthorityRoute.value, to: '/system/users' },
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

function onItemClick(item: { to?: string; action?: string }) {
  if (item.action === 'change-password') {
    changePasswordVisible.value = true
  } else if (item.action === 'logout') {
    handleLogout()
  } else if (item.to) {
    router.push(item.to)
  }
}

async function handleLogout() {
  try {
    await ElMessageBox.confirm('Are you sure you want to log out?', 'Confirm Logout', {
      type: 'warning',
      confirmButtonText: 'Logout',
      cancelButtonText: 'Cancel',
    })
    await authStore.logout()
    ElMessage.success('Logged out successfully')
    router.push('/login')
  } catch {
    // User cancelled
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
