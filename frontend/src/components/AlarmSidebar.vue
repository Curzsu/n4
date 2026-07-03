<template>
  <div :class="['alarm-sidebar', { collapsed: isCollapsed }]">
    <!-- Site selector -->
    <div class="site-selector">
      <el-icon><Location /></el-icon>
      <span v-show="!isCollapsed" class="site-name">SITE: JHB-01</span>
    </div>

    <!-- Navigation menu -->
    <div v-show="!isCollapsed" class="nav-menu">
      <div
        v-for="item in navItems"
        :key="item.label"
        :class="['nav-item', { active: item.active }]"
      >
        <span class="nav-label">{{ item.label }}</span>

        <!-- Sub-items for Alarm -->
        <div v-if="item.children && item.expanded" class="nav-children">
          <div
            v-for="child in item.children"
            :key="child.label"
            :class="['nav-child', { active: child.active }]"
          >
            <span class="nav-child-dot"></span>
            <span class="nav-child-label">{{ child.label }}</span>
          </div>
        </div>
      </div>
    </div>

    <!-- Collapse toggle -->
    <div class="collapse-btn" @click="isCollapsed = !isCollapsed">
      <el-icon>
        <Fold v-if="!isCollapsed" />
        <Expand v-else />
      </el-icon>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { Location, Fold, Expand } from '@element-plus/icons-vue'

const isCollapsed = ref(false)

interface NavChild {
  label: string
  active: boolean
}

interface NavItem {
  label: string
  active: boolean
  expanded: boolean
  children?: NavChild[]
}

const navItems = ref<NavItem[]>([
  { label: 'Overview', active: false, expanded: false },
  { label: 'Campus', active: false, expanded: false },
  { label: 'Building A', active: false, expanded: false },
  { label: 'Building B', active: false, expanded: false },
  { label: 'Utility Plant', active: false, expanded: false },
  { label: 'Electrical', active: false, expanded: false },
  { label: 'Cooling', active: false, expanded: false },
  { label: 'Mechanical', active: false, expanded: false },
  { label: 'Data Hall', active: false, expanded: false },
  {
    label: 'Alarm',
    active: true,
    expanded: true,
    children: [
      { label: 'Active Alarms', active: true },
    ],
  },
  { label: 'Trend', active: false, expanded: false },
  { label: 'Report', active: false, expanded: false },
  { label: 'System', active: false, expanded: false },
])
</script>

<style scoped lang="scss">
.alarm-sidebar {
  width: $sidebar-width;
  background-color: $bg-card;
  border-right: 1px solid $border-color;
  display: flex;
  flex-direction: column;
  flex-shrink: 0;
  transition: width 0.3s;

  &.collapsed {
    width: $sidebar-collapsed-width;
  }
}

.site-selector {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 16px;
  border-bottom: 1px solid $border-color;
  color: $accent-green;
  font-size: 13px;
  font-weight: 600;
}

.nav-menu {
  flex: 1;
  overflow-y: auto;
  padding: 8px 0;
}

.nav-item {
  padding: 0 16px;

  .nav-label {
    display: block;
    padding: 8px 0;
    font-size: 13px;
    color: $text-secondary;
    cursor: pointer;

    &:hover {
      color: $text-primary;
    }
  }

  &.active > .nav-label {
    color: $accent-green;
    font-weight: 600;
  }
}

.nav-children {
  padding-left: 20px;
}

.nav-child {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 0;
  font-size: 12px;
  color: $text-muted;
  cursor: pointer;

  &:hover {
    color: $text-secondary;
  }

  &.active {
    color: $accent-green;
  }
}

.nav-child-dot {
  width: 4px;
  height: 4px;
  border-radius: 50%;
  background-color: currentColor;
}

.collapse-btn {
  padding: 12px 16px;
  border-top: 1px solid $border-color;
  text-align: center;
  color: $text-muted;
  cursor: pointer;

  &:hover {
    color: $text-primary;
  }
}
</style>
