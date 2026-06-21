<template>
  <div class="chart-panel">
    <div class="chart-title">PUE Trend</div>
    <v-chart :option="chartOption" autoresize class="chart-instance" />
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import VChart from 'vue-echarts'
import { use } from 'echarts/core'
import { LineChart } from 'echarts/charts'
import { GridComponent, TooltipComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import { useDashboardStore } from '@/stores/dashboard'

use([LineChart, GridComponent, TooltipComponent, CanvasRenderer])

const store = useDashboardStore()
const trendData = computed(() => store.pueTrend)

const chartOption = computed(() => {
  if (!trendData.value) return {}
  const d = trendData.value
  return {
    backgroundColor: 'transparent',
    grid: { top: 20, right: 20, bottom: 30, left: 50 },
    tooltip: { trigger: 'axis' },
    xAxis: {
      type: 'category',
      data: d.timeLabels,
      axisLine: { lineStyle: { color: '#1f2937' } },
      axisLabel: { color: '#6b7280', fontSize: 10 },
    },
    yAxis: {
      type: 'value',
      min: 0.8,
      max: 2.0,
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
        areaStyle: { color: { type: 'linear', x: 0, y: 0, x2: 0, y2: 1, colorStops: [{ offset: 0, color: 'rgba(16,185,129,0.3)' }, { offset: 1, color: 'rgba(16,185,129,0)' }] } },
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
  padding: 12px;
}

.chart-title {
  font-size: 13px;
  color: $text-secondary;
  font-weight: 500;
  margin-bottom: 8px;
}

.chart-instance {
  width: 100%;
  height: 200px;
}
</style>
