<template>
  <div class="main-layout">
    <TopNavbar />
    <div class="layout-body">
      <LeftSidebar />
      <div class="layout-content">
        <slot />
      </div>
      <div class="layout-right-panel">
        <AlarmSummary />
        <SystemStatus />
      </div>
    </div>
    <BottomToolbar />
  </div>
</template>

<script setup lang="ts">
import TopNavbar from '@/components/TopNavbar.vue'
import LeftSidebar from '@/components/LeftSidebar.vue'
import BottomToolbar from '@/components/BottomToolbar.vue'
import AlarmSummary from '@/components/AlarmSummary.vue'
import SystemStatus from '@/components/SystemStatus.vue'
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
  overflow-y: auto;
  padding: 16px;
}

.layout-right-panel {
  width: $right-panel-width;
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 16px 12px;
  flex-shrink: 0;
  overflow-y: auto;
}
</style>
