<template>
  <div class="trend-charts-grid">
    <PowerTrendChart />
    <EnergyTrendChart />
    <PueTrendChart />
    <TemperatureTrendChart />
  </div>
</template>

<script setup lang="ts">
import PowerTrendChart from './charts/PowerTrendChart.vue'
import EnergyTrendChart from './charts/EnergyTrendChart.vue'
import PueTrendChart from './charts/PueTrendChart.vue'
import TemperatureTrendChart from './charts/TemperatureTrendChart.vue'
</script>

<style scoped lang="scss">
.trend-charts-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 12px;
}
</style>
