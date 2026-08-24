<template>
  <div class="top-navbar">
    <div class="navbar-left">
      <span class="logo">DAYONE</span>
      <span class="logo-separator">|</span>
      <span class="logo-sub">N4 Monitor</span>
      <div class="nav-tabs">
        <span
          v-for="tab in navTabs"
          :key="tab"
          :class="['nav-tab', { active: tab === activeTab }]"
          @click="handleTabClick(tab)"
        >{{ tab }}</span>
      </div>
    </div>
    <div class="navbar-right">
      <div class="alert-badges">
        <span
          v-for="b in alarmBadges"
          :key="b.key"
          class="badge"
          :style="{ '--badge-color': severityColors[b.severity] }"
          :title="b.severity"
          @click.stop="handleBadgeClick(b.severity)"
        >{{ alarmSummary?.[b.key] || 0 }}</span>
      </div>
      <el-icon class="search-icon"><Search /></el-icon>
      <el-dropdown trigger="click" @command="handleUserCommand">
        <div class="user-profile">
          <el-icon><UserFilled /></el-icon>
          <span class="username">{{ displayName }}</span>
          <el-icon class="arrow-icon"><ArrowDown /></el-icon>
        </div>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item command="password">Change Password</el-dropdown-item>
            <el-dropdown-item command="logs">Audit Logs</el-dropdown-item>
            <el-dropdown-item divided command="logout">Logout</el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </div>

    <ChangePasswordDialog v-model="changePasswordVisible" />
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Search, UserFilled, ArrowDown } from '@element-plus/icons-vue'
import { ElMessageBox, ElMessage } from 'element-plus'
import { useDashboardStore } from '@/stores/dashboard'
import { useAlarmWebSocket } from '@/composables/useAlarmWebSocket'
import { useAuthStore } from '@/stores/auth'
import { severityColors } from '@/config/severityColors'
import ChangePasswordDialog from '@/components/ChangePasswordDialog.vue'

const router = useRouter()
const route = useRoute()
const store = useDashboardStore()
const authStore = useAuthStore()
const { summaryData } = useAlarmWebSocket()

const changePasswordVisible = ref(false)

const displayName = computed(() => {
  return authStore.currentUser?.realName || authStore.currentUser?.username || 'User'
})

// Live alarm counts from the WebSocket stream
const alarmSummary = computed(() => {
  const live = summaryData.value
  const rest = store.alarmSummary
  return {
    critical: live.critical ?? rest?.critical ?? 0,
    major: live.major ?? rest?.major ?? 0,
    minor: live.minor ?? rest?.minor ?? 0,
    warning: live.warning ?? rest?.warning ?? 0,
  }
})

const alarmBadges = [
  { key: 'critical', severity: 'Critical' },
  { key: 'major', severity: 'Major' },
  { key: 'minor', severity: 'Minor' },
  { key: 'warning', severity: 'Warning' },
] as const

const navTabs = [
  'Dashboard',
  'Alarms',
  'Event',
  'Trend',
  'Report',
  'System',
]

const activeTab = computed(() => {
  const systemPrefixes = [
    '/device-template',
    '/device-deployment',
    '/system-deployment',
    '/alarm/config',
    '/system',
    '/logs',
  ]
  if (systemPrefixes.some((p) => route.path.startsWith(p))) {
    return 'System'
  }
  if (route.path === '/event') {
    return 'Event'
  }
  if (route.path.startsWith('/alarm')) {
    return 'Alarms'
  }
  return 'Dashboard'
})

const routeMap: Record<string, string> = {
  'Dashboard': '/',
  'Alarms': '/alarm',
  'Event': '/event',
  'System': '/device-template',
}

function handleTabClick(tab: string) {
  const path = routeMap[tab]
  if (path) {
    router.push(path)
  }
}

function handleBadgeClick(severity: string) {
  router.push({ path: '/alarm', query: { severity } })
}

async function handleUserCommand(command: string) {
  if (command === 'password') {
    changePasswordVisible.value = true
  } else if (command === 'logs') {
    router.push('/logs')
  } else if (command === 'logout') {
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
}
</script>

<style scoped lang="scss">
.top-navbar {
  height: $topnav-height;
  background-color: $bg-card;
  border-bottom: 1px solid $border-color;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 16px;
  flex-shrink: 0;
}

.navbar-left {
  display: flex;
  align-items: center;
  gap: 8px;
}

.logo {
  font-weight: 700;
  font-size: 16px;
  color: $accent-green;
}

.logo-separator {
  color: $text-muted;
  margin: 0 4px;
}

.logo-sub {
  font-size: 14px;
  color: $text-secondary;
}

.nav-tabs {
  display: flex;
  gap: 2px;
  margin-left: 24px;
}

.nav-tab {
  padding: 6px 12px;
  font-size: 13px;
  color: $text-secondary;
  cursor: pointer;
  border-radius: 4px;
  transition: all 0.2s;
}

.nav-tab.active {
  color: $accent-green;
  border-bottom: 2px solid $accent-green;
}

.nav-tab:hover {
  color: $text-primary;
  background-color: $bg-card-hover;
}

.navbar-right {
  display: flex;
  align-items: center;
  gap: 16px;
}

.alert-badges {
  display: flex;
  gap: 8px;
  transition: opacity 0.2s;
}

.badge {
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
  color: var(--badge-color, #6b7280);
  background: color-mix(in srgb, var(--badge-color, #6b7280) 20%, transparent);
  transition: transform 0.15s, filter 0.15s;
  &:hover {
    filter: brightness(1.15);
    transform: translateY(-1px);
  }
  &:active {
    transform: translateY(0);
  }
}

.search-icon {
  color: $text-secondary;
  cursor: pointer;
  font-size: 16px;
}

.user-profile {
  display: flex;
  align-items: center;
  gap: 6px;
  color: $text-secondary;
  font-size: 13px;
  cursor: pointer;
  padding: 4px 8px;
  border-radius: 4px;
  transition: background-color 0.2s;

  &:hover {
    color: $text-primary;
    background-color: $bg-card-hover;
  }

  .username {
    font-weight: 500;
  }

  .arrow-icon {
    font-size: 11px;
  }
}
</style>
