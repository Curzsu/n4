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
      <div class="user-profile">
        <el-icon><UserFilled /></el-icon>
        <span>admin</span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Search, UserFilled } from '@element-plus/icons-vue'
import { useDashboardStore } from '@/stores/dashboard'
import { useAlarmWebSocket } from '@/composables/useAlarmWebSocket'
import { alarmApi } from '@/api/alarm'
import { severityColors } from '@/config/severityColors'

const router = useRouter()
const route = useRoute()

const store = useDashboardStore()
const { summaryData } = useAlarmWebSocket()

// Live alarm counts from the WebSocket stream (same source as Active Alarms).
// Fall back to the REST snapshot so the badges are non-zero before the first
// WS frame arrives.
const alarmSummary = computed(() => {
  const live = summaryData.value
  const rest = store.alarmSummary
  return {
    critical: live.critical || rest?.critical || 0,
    major: live.major || rest?.major || 0,
    minor: live.minor || rest?.minor || 0,
    warning: live.warning || rest?.warning || 0,
  }
})

// Seed summaryData with the real-time alarm summary on mount. Without this the
// badges stay at 0 until either an alarm fires (WS push) or the right panel
// (AlarmSummaryPanel) is opened - because summaryData's initial value is all
// zeros and only AlarmSummaryPanel.onMounted fetched it before. Doing the fetch
// here guarantees correct counts immediately on page load and after refresh,
// independent of the right panel.
onMounted(async () => {
  try {
    const data = await alarmApi.getAlarmSummary()
    summaryData.value = data
  } catch (e) {
    console.error('Failed to fetch alarm summary:', e)
  }
})

// Badge order left-to-right: Critical, Major, Minor, Warning. Each badge maps
// to a canonical severity label (used both for the title tooltip and for the
// Active Alarms query param) and to an AlarmSummary count key.
const alarmBadges = [
  { key: 'critical', severity: 'Critical' },
  { key: 'major', severity: 'Major' },
  { key: 'minor', severity: 'Minor' },
  { key: 'warning', severity: 'Warning' },
] as const

const navTabs = [
  'Dashboard',
  'Alarms',
  'Trend',
  'Report',
  'System',
]

const activeTab = computed(() => {
  // System second-level routes: device template, device deployment, alarm configuration
  if (route.path === '/device-template' || route.path === '/device-deployment' || route.path === '/alarm/config') {
    return 'System'
  }
  if (route.path.startsWith('/alarm')) {
    return 'Alarms'
  }
  return 'Dashboard'
})

const routeMap: Record<string, string> = {
  'Dashboard': '/',
  'Alarms': '/alarm',
  // System opens its first second-level entry (Device Template);
  // the remaining entries are reachable via the bottom toolbar on System routes.
  'System': '/device-template',
}

function handleTabClick(tab: string) {
  const path = routeMap[tab]
  if (path) {
    router.push(path)
  }
}

// Clicking a severity badge opens Active Alarms pre-filtered to that severity.
// The query param is consumed by AlarmView -> AlarmTable.
function handleBadgeClick(severity: string) {
  router.push({ path: '/alarm', query: { severity } })
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

// Each badge color is driven by the --badge-color CSS variable, set inline from
// config/severityColors.ts so the navbar matches the Severity tags exactly.
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
  gap: 4px;
  color: $text-secondary;
  font-size: 13px;
}
</style>
