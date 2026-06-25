<template>
  <div class="dashboard-view" v-loading="store.loading">
    <template v-if="!store.selectedNode || store.selectedNode.type === 'site'">
      <KpiCardsRow />
      <BuildingPlaceholder />
      <TrendChartsGrid />
    </template>
    <template v-else-if="store.selectedNode.type === 'device'">
      <DeviceDetailView />
    </template>
    <template v-else>
      <SystemDetailView />
    </template>
  </div>
</template>

<script setup lang="ts">
import KpiCardsRow from '@/components/KpiCardsRow.vue'
import BuildingPlaceholder from '@/components/BuildingPlaceholder.vue'
import TrendChartsGrid from '@/components/TrendChartsGrid.vue'
import SystemDetailView from '@/components/SystemDetailView.vue'
import DeviceDetailView from '@/components/DeviceDetailView.vue'
import { useDashboardStore } from '@/stores/dashboard'

const store = useDashboardStore()
</script>


<style lang="scss">
.dashboard-view {
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-height: 100%;
}
</style>
