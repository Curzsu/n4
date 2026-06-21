<template>
  <div class="dashboard-view" v-loading="store.loading">
    <KpiCardsRow />
    <BuildingPlaceholder />
    <TrendChartsGrid />
  </div>
</template>

<script setup lang="ts">
import KpiCardsRow from '@/components/KpiCardsRow.vue'
import BuildingPlaceholder from '@/components/BuildingPlaceholder.vue'
import TrendChartsGrid from '@/components/TrendChartsGrid.vue'
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
