<template>
  <div class="main-layout">
    <TopNavbar />
    <div class="layout-body">
      <!-- Conditional left sidebar -->
      <AlarmSidebar v-if="isAlarmRoute" />
      <LeftSidebar v-else />

      <div class="layout-content">
        <slot />
      </div>

      <!-- Conditional right panel -->
      <div v-if="isAlarmRoute" class="layout-right-panel">
        <div class="right-panel-inner">
          <AlarmSummaryPanel />
          <AlarmSystemChart />
          <AlarmSourcesChart />
          <AlarmQuickActions />
        </div>
      </div>
      <div v-else class="layout-right-panel">
        <AlarmSummary />
        <SystemStatus />
      </div>
    </div>
    <BottomToolbar />
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
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

const route = useRoute()
const isAlarmRoute = computed(() => route.path === '/alarm')
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
  padding: 16px 12px;
  flex-shrink: 0;
  overflow-y: auto;
  overflow-x: hidden;
  box-sizing: border-box;
}

.right-panel-inner {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
</style>
