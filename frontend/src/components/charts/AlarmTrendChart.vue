<template>
  <div class="chart-panel">
    <div class="chart-title">Alarms Trend (Last 24h)</div>
    <v-chart :option="chartOption" autoresize class="chart-instance" />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import VChart from 'vue-echarts'
import { use } from 'echarts/core'
import { LineChart } from 'echarts/charts'
import { GridComponent, TooltipComponent, LegendComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import { alarmApi } from '@/api/alarm'
import { useAlarmWebSocket } from '@/composables/useAlarmWebSocket'
import type { AlarmTrendData } from '@/api/types'

use([LineChart, GridComponent, TooltipComponent, LegendComponent, CanvasRenderer])

const data = ref<AlarmTrendData | null>(null)
const { onUpdate } = useAlarmWebSocket()

const colorMap: Record<string, string> = {
  Critical: '#ef4444',
  Major: '#f97316',
  Minor: '#eab308',
  Warning: '#3b82f6',
}

const chartOption = computed(() => {
  if (!data.value) return {}
  return {
    backgroundColor: 'transparent',
    grid: { top: 30, right: 20, bottom: 30, left: 40 },
    tooltip: { trigger: 'axis' },
    legend: {
      data: data.value.series.map(s => s.name),
      textStyle: { color: '#9ca3af', fontSize: 10 },
      top: 0,
    },
    xAxis: {
      type: 'category',
      data: data.value.timeLabels,
      axisLine: { lineStyle: { color: '#1f2937' } },
      axisLabel: { color: '#6b7280', fontSize: 10 },
    },
    yAxis: {
      type: 'value',
      name: 'Count',
      nameTextStyle: { color: '#6b7280' },
      axisLine: { show: false },
      splitLine: { lineStyle: { color: '#1f2937' } },
      axisLabel: { color: '#6b7280', fontSize: 10 },
    },
    series: data.value.series.map(s => ({
      name: s.name,
      type: 'line',
      data: s.data,
      smooth: true,
      lineStyle: { color: colorMap[s.name] || '#6b7280', width: 2 },
      itemStyle: { color: colorMap[s.name] || '#6b7280' },
    })),
  }
})

async function fetchTrendData() {
  try {
    data.value = await alarmApi.getAlarmTrend()
  } catch (e) {
    console.error('Failed to fetch trend data:', e)
  }
}

// Initial fetch
onMounted(() => {
  fetchTrendData()
})

// Refresh trend data when WebSocket receives updates
onUpdate(() => {
  fetchTrendData()
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
