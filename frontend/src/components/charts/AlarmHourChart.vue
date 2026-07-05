<template>
  <div class="chart-panel">
    <div class="chart-title">Alarms by Hour (Today)</div>
    <v-chart :option="chartOption" autoresize class="chart-instance" />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import VChart from 'vue-echarts'
import { use } from 'echarts/core'
import { BarChart } from 'echarts/charts'
import { GridComponent, TooltipComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import { alarmApi } from '@/api/alarm'
import { useAlarmWebSocket } from '@/composables/useAlarmWebSocket'
import type { AlarmHourData } from '@/api/types'

use([BarChart, GridComponent, TooltipComponent, CanvasRenderer])

const { hourlyData } = useAlarmWebSocket()

const chartOption = computed(() => {
  const data = hourlyData.value
  if (!data || !data.hours || data.hours.length === 0) return {}
  return {
    backgroundColor: 'transparent',
    grid: { top: 20, right: 20, bottom: 30, left: 40 },
    tooltip: { trigger: 'axis' },
    xAxis: {
      type: 'category',
      data: data.hours,
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
    series: [{
      type: 'bar',
      data: data.counts,
      itemStyle: { color: '#10b981' },
      barWidth: '60%',
    }],
  }
})

// Initial fetch
onMounted(async () => {
  try {
    hourlyData.value = await alarmApi.getAlarmByHour()
  } catch (e) {
    console.error('Failed to fetch hourly data:', e)
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
