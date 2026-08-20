<template>
  <div class="main-layout">
    <TopNavbar />
    <div class="layout-body">
      <AlarmSidebar v-if="isAlarmRoute" :default-collapsed="true" />
      <LeftSidebar v-else :mode="isNavRoute ? 'nav' : 'default'"
                   :nav-tree="store.navTree" :loading="store.navLoading"
                   :default-collapsed="!isLeftSidebarOpenByDefault"
                   @node-click="store.selectNavNode" />

      <div class="layout-content">
        <slot />
      </div>

      <div :class="['layout-right-panel', { collapsed: isRightCollapsed }]">
        <div v-if="isRightCollapsed" class="right-panel-collapsed-icons" @click="toggleRightCollapse">
          <el-icon class="collapsed-icon"><Bell /></el-icon>
          <el-icon class="collapsed-icon"><Setting /></el-icon>
        </div>
        <div class="right-panel-body" v-else>
          <div class="right-panel-header">
            <span class="panel-title">{{ isAlarmRoute ? 'Alarm Overview' : 'System Status' }}</span>
            <el-icon class="collapse-btn" @click="toggleRightCollapse">
              <Fold />
            </el-icon>
          </div>
          <template v-if="isAlarmRoute">
            <AlarmSummaryPanel />
            <AlarmSystemChart />
            <AlarmSourcesChart />
            <AlarmQuickActions />
          </template>
          <template v-else>
            <AlarmSummary />
            <SystemStatus />
          </template>
        </div>
      </div>
    </div>
    <BottomToolbar v-if="!isEventRoute" />
  </div>
</template>

<script setup lang="ts">
import { computed, watch, onMounted } from 'vue'
import { Bell, Setting, Fold } from '@element-plus/icons-vue'
import { useRoute } from 'vue-router'
import TopNavbar from '@/components/TopNavbar.vue'
import LeftSidebar from '@/components/LeftSidebar.vue'
import BottomToolbar from '@/components/BottomToolbar.vue'
import AlarmSummary from '@/components/AlarmSummary.vue'
import SystemStatus from '@/components/SystemStatus.vue'
import AlarmSidebar from '@/components/AlarmSidebar.vue'
import AlarmSummaryPanel from '@/components/AlarmSummaryPanel.vue'
import AlarmSystemChart from '@/components/charts/AlarmSystemChart.vue'
import AlarmSourcesChart from '@/components/charts/AlarmSourcesChart.vue'
import AlarmQuickActions from '@/components/AlarmQuickActions.vue'
import { useDashboardStore } from '@/stores/dashboard'

const route = useRoute()
const store = useDashboardStore()

// /alarm/config belongs to the System menu and must not use alarm layout panels.
const systemPaths = ['/device-template', '/device-deployment', '/alarm/config', '/system-deployment']
const isSystemRoute = computed(() => systemPaths.includes(route.path))
const isAlarmRoute = computed(() => route.path.startsWith('/alarm') && !isSystemRoute.value)
// The Event route is a standalone page with no second-level toolbar menu.
const isEventRoute = computed(() => route.path === '/event')
// The home page uses the new two-level navigation mode.
const isNavRoute = computed(() => route.path === '/')
// The left sidebar is collapsed (not removed) on every route except Dashboard,
// Device Template and Device Deployment, where it opens by default.
const isLeftSidebarOpenByDefault = computed(() =>
  route.path === '/' || route.path === '/device-template' || route.path === '/device-deployment')

const isRightCollapsed = computed({
  get: () => store.isRightCollapsed,
  set: (val) => {
    store.isRightCollapsed = val
  }
})

function toggleRightCollapse() {
  isRightCollapsed.value = !isRightCollapsed.value
}

// The right side panel opens by default on the Dashboard route; every other
// route defaults it to collapsed so the content area keeps its full width.
watch(() => route.path, (path) => {
  store.isRightCollapsed = path !== '/'
}, { immediate: true })

// Load the two-level nav tree.
onMounted(() => {
  if (!store.navTree.length) {
    store.loadNavTree()
  }
})
</script>

<style scoped lang="scss">
.main-layout {
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
  background-color: $bg-dark;
}

.layout-body {
  flex: 1;
  display: flex;
  overflow: hidden;
}

.layout-content {
  flex: 1;
  // Allow this flex child to shrink below the intrinsic width of its content,
  // otherwise expanding the right panel would overflow and cover the content.
  min-width: 0;
  min-height: 0;
  overflow-y: auto;
  padding: 16px;
  display: flex;
  flex-direction: column;
}

.layout-right-panel {
  width: $right-panel-width;
  background-color: $bg-card;
  border-left: 1px solid $border-color;
  display: flex;
  flex-direction: column;
  transition: width 0.3s, padding 0.3s;
  flex-shrink: 0;
  overflow-y: auto;
  overflow-x: hidden;
  box-sizing: border-box;

  &.collapsed {
    width: $right-panel-collapsed-width;
    padding: 12px 4px;
  }
}

.right-panel-collapsed-icons {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 20px;
  padding-top: 24px;
  cursor: pointer;

  .collapsed-icon {
    font-size: 20px;
    color: $text-secondary;
    transition: color 0.2s;

    &:hover {
      color: $text-primary;
    }
  }
}

.right-panel-body {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 16px 12px;
  overflow-y: auto;
}

.right-panel-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-bottom: 12px;
  border-bottom: 1px solid $border-color;
  margin-bottom: 4px;
}

.panel-title {
  font-size: 13px;
  font-weight: 600;
  color: $text-primary;
}

.collapse-btn {
  font-size: 12px;
  color: $text-secondary;
  cursor: pointer;
  transition: color 0.2s;

  &:hover {
    color: $text-primary;
  }
}
</style>
