<template>
  <div class="main-layout">
    <TopNavbar />
    <div class="layout-body">
      <LeftSidebar v-if="!isFullWidthView" />
      <div :class="['layout-content', { 'layout-content--wide': isFullWidthView }]">
        <slot />
      </div>
      <div v-if="!isFullWidthView" class="layout-right-panel">
        <AlarmSummary />
        <SystemStatus />
      </div>
    </div>
    <BottomToolbar v-if="!isFullWidthView" />
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

const route = useRoute()
const isFullWidthView = computed(() => route.meta.fullWidth === true)
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

.layout-content--wide {
  min-width: 0;
  overflow: hidden;
  padding: 12px;
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
