<template>
  <div class="chart-panel">
    <div class="chart-title">Temperature Trend (°C)</div>
    <v-chart :option="chartOption" autoresize class="chart-instance" />
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import VChart from 'vue-echarts'
import { use } from 'echarts/core'
import { LineChart } from 'echarts/charts'
import { GridComponent, TooltipComponent, LegendComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import { useDashboardStore } from '@/stores/dashboard'

use([LineChart, GridComponent, TooltipComponent, LegendComponent, CanvasRenderer])

const store = useDashboardStore()
const trendData = computed(() => store.temperatureTrend)

const chartOption = computed(() => {
  if (!trendData.value) return {}
  const d = trendData.value
  return {
    backgroundColor: 'transparent',
    grid: { top: 24, right: 12, bottom: 22, left: 40 },
    tooltip: { trigger: 'axis' },
    legend: {
      data: [d.seriesName, d.seriesName2!],
      textStyle: { color: '#9ca3af', fontSize: 11 },
      top: 0,
      itemWidth: 14,
      itemHeight: 8,
    },
    xAxis: {
      type: 'category',
      data: d.timeLabels,
      axisLine: { lineStyle: { color: '#1f2937' } },
      axisLabel: { color: '#6b7280', fontSize: 10 },
    },
    yAxis: {
      type: 'value',
      min: 10,
      max: 40,
      name: d.unit,
      nameTextStyle: { color: '#6b7280' },
      axisLine: { show: false },
      splitLine: { lineStyle: { color: '#1f2937' } },
      axisLabel: { color: '#6b7280', fontSize: 10 },
    },
    series: [
      {
        name: d.seriesName,
        type: 'line',
        data: d.values,
        smooth: true,
        lineStyle: { color: '#10b981', width: 2 },
        itemStyle: { color: '#10b981' },
      },
      {
        name: d.seriesName2!,
        type: 'line',
        data: d.values2!,
        smooth: true,
        lineStyle: { color: '#06b6d4', width: 2 },
        itemStyle: { color: '#06b6d4' },
      },
    ],
  }
})
</script>

<style scoped lang="scss">
.chart-panel {
  background-color: $bg-card;
  border: 1px solid $border-card;
  border-radius: 8px;
  padding: 8px 10px;
  display: flex;
  flex-direction: column;
  min-height: 0;
}

.chart-title {
  font-size: 12px;
  color: $text-secondary;
  font-weight: 500;
  margin-bottom: 4px;
}

.chart-instance {
  width: 100%;
  flex: 1;
  min-height: 0;
}
</style>
