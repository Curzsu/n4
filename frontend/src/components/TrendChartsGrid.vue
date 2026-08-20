<template>
  <div ref="gridRef" class="trend-charts-grid">
    <PowerTrendChart ref="chartRefs" />
    <EnergyTrendChart ref="chartRefs" />
    <PueTrendChart ref="chartRefs" />
    <TemperatureTrendChart ref="chartRefs" />
  </div>
</template>

<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import PowerTrendChart from './charts/PowerTrendChart.vue'
import EnergyTrendChart from './charts/EnergyTrendChart.vue'
import PueTrendChart from './charts/PueTrendChart.vue'
import TemperatureTrendChart from './charts/TemperatureTrendChart.vue'
import type VChart from 'vue-echarts'

const gridRef = ref<HTMLElement>()
// ref="chartRefs" collects the four chart component instances in order.
const chartRefs = ref<InstanceType<typeof VChart>[]>([])

let resizeObserver: ResizeObserver | null = null

onMounted(() => {
  if (!gridRef.value || typeof ResizeObserver === 'undefined') return
  resizeObserver = new ResizeObserver(() => {
    // ResizeObserver also fires on mount; charts size themselves correctly on
    // mount, so only forward container changes that alter the size.
    chartRefs.value.forEach((chart) => chart?.resize?.())
  })
  resizeObserver.observe(gridRef.value)
})

onBeforeUnmount(() => {
  resizeObserver?.disconnect()
  resizeObserver = null
})
</script>

<style scoped lang="scss">
.trend-charts-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  grid-template-rows: repeat(2, minmax(0, 1fr));
  gap: 8px;
  flex: 1.4 1 0;
  min-height: 220px;
  // Allow the grid to shrink below its content's intrinsic width so expanding
  // the right panel compresses the charts instead of covering them.
  min-width: 0;
}
</style>
