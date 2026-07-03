<template>
  <div class="chart-panel">
    <div class="panel-header">
      <span class="chart-title">Top Alarm Sources (Today)</span>
      <span class="view-all-link">View All</span>
    </div>
    <v-chart :option="chartOption" autoresize class="chart-instance" />
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import VChart from 'vue-echarts'
import { use } from 'echarts/core'
import { BarChart } from 'echarts/charts'
import { GridComponent, TooltipComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import { alarmApi } from '@/api/alarm'
import type { AlarmSourceData } from '@/api/types'

use([BarChart, GridComponent, TooltipComponent, CanvasRenderer])

const data = ref<AlarmSourceData | null>(null)

onMounted(async () => {
  data.value = await alarmApi.getAlarmTopSources()
})

const colors = ['#3b82f6', '#eab308', '#f97316', '#10b981', '#6b7280']

const chartOption = computed(() => {
  if (!data.value) return {}
  const items = [...data.value.items].reverse() // reverse for horizontal display
  return {
    backgroundColor: 'transparent',
    grid: { top: 10, right: 30, bottom: 10, left: 60 },
    tooltip: { trigger: 'axis' },
    xAxis: {
      type: 'value',
      axisLine: { show: false },
      splitLine: { lineStyle: { color: '#1f2937' } },
      axisLabel: { color: '#6b7280', fontSize: 10 },
    },
    yAxis: {
      type: 'category',
      data: items.map(i => i.name),
      axisLine: { lineStyle: { color: '#1f2937' } },
      axisLabel: { color: '#9ca3af', fontSize: 11 },
    },
    series: [{
      type: 'bar',
      data: items.map((item, i) => ({
        value: item.count,
        itemStyle: { color: colors[i % colors.length] },
      })),
      barWidth: '50%',
    }],
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
.panel-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 4px;
}
.chart-title {
  font-size: 13px;
  color: $text-secondary;
  font-weight: 500;
}
.view-all-link {
  font-size: 11px;
  color: $accent-blue;
  cursor: pointer;
  &:hover { text-decoration: underline; }
}
.chart-instance {
  width: 100%;
  height: 160px;
}
</style>
