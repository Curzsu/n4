<template>
  <div class="main-layout">
    <TopNavbar />
    <div class="layout-body">
      <!-- Conditional left sidebar -->
      <AlarmSidebar v-if="isAlarmRoute" :default-collapsed="isAlarmRoute" />
      <LeftSidebar v-else />

      <div class="layout-content">
        <slot />
      </div>

      <!-- Conditional right panel with collapse toggle -->
      <div :class="['layout-right-panel', { collapsed: isRightPanelCollapsed }]">
        <div class="right-panel-header">
          <span v-show="!isRightPanelCollapsed" class="right-panel-title">
            {{ isAlarmRoute ? 'Alarm Info' : 'Overview' }}
          </span>
          <div class="collapse-btn" @click="isRightPanelCollapsed = !isRightPanelCollapsed">
            <el-icon>
              <Fold v-if="!isRightPanelCollapsed" />
              <Expand v-else />
            </el-icon>
          </div>
        </div>
        <div v-show="!isRightPanelCollapsed" class="right-panel-inner">
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
    <BottomToolbar />
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { Fold, Expand } from '@element-plus/icons-vue'
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

const route = useRoute()
const isAlarmRoute = computed(() => route.path.startsWith('/alarm'))
const isRightPanelCollapsed = ref(isAlarmRoute.value)

// When entering the Alarms route, default both side panels to collapsed
watch(isAlarmRoute, (alarm) => {
  if (alarm) {
    isRightPanelCollapsed.value = true
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
  min-height: 0;
  overflow-y: auto;
  padding: 16px;
  display: flex;
  flex-direction: column;
}

.layout-right-panel {
  width: $right-panel-width;
  max-width: $right-panel-width;
  min-width: 0;
  display: flex;
  flex-direction: column;
  padding: 12px;
  flex-shrink: 0;
  overflow-y: auto;
  overflow-x: hidden;
  box-sizing: border-box;
  transition: width 0.3s, padding 0.3s;

  &.collapsed {
    width: $right-panel-collapsed-width;
    padding: 12px 4px;
  }
}

.right-panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
  min-height: 24px;

  .layout-right-panel.collapsed & {
    justify-content: center;
    margin-bottom: 8px;
  }
}

.right-panel-title {
  font-size: 13px;
  font-weight: 600;
  color: $text-secondary;
}

.collapse-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border-radius: 4px;
  color: $text-muted;
  cursor: pointer;
  transition: color 0.2s, background-color 0.2s;

  &:hover {
    color: $text-primary;
    background-color: $bg-card-hover;
  }
}

.right-panel-inner {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

@media (max-width: 1100px) {
  .layout-right-panel {
    width: $right-panel-collapsed-width;
    padding: 12px 4px;
  }

  .right-panel-header {
    justify-content: center;
  }

  .right-panel-title,
  .right-panel-inner {
    display: none;
  }
}
</style>
