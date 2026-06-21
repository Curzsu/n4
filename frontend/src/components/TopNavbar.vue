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
          :class="['nav-tab', { active: tab === 'Dashboard' }]"
        >{{ tab }}</span>
      </div>
    </div>
    <div class="navbar-right">
      <div class="alert-badges">
        <span class="badge critical">{{ alarmSummary?.critical || 0 }}</span>
        <span class="badge major">{{ alarmSummary?.major || 0 }}</span>
        <span class="badge minor">{{ alarmSummary?.minor || 0 }}</span>
        <span class="badge warning">{{ alarmSummary?.warning || 0 }}</span>
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
import { computed } from 'vue'
import { Search, UserFilled } from '@element-plus/icons-vue'
import { useDashboardStore } from '@/stores/dashboard'

const store = useDashboardStore()
const alarmSummary = computed(() => store.alarmSummary)

const navTabs = [
  'Dashboard', 'Electrical', 'Cooling', 'Mechanical',
  'Data Hall', 'Alarms', 'Trend', 'Report', 'System',
]
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
}

.badge {
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 12px;
  font-weight: 600;
  &.critical { background: rgba($accent-red, 0.2); color: $accent-red; }
  &.major { background: rgba($accent-orange, 0.2); color: $accent-orange; }
  &.minor { background: rgba($accent-yellow, 0.2); color: $accent-yellow; }
  &.warning { background: rgba($accent-blue, 0.2); color: $accent-blue; }
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
