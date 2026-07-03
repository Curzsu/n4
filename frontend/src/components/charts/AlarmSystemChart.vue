<template>
  <div class="chart-panel">
    <div class="panel-header">
      <span class="chart-title">Alarms by System</span>
      <span class="view-all-link">View All</span>
    </div>
    <v-chart :option="chartOption" autoresize class="chart-instance" />
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import VChart from 'vue-echarts'
import { use } from 'echarts/core'
import { PieChart } from 'echarts/charts'
import { TooltipComponent, LegendComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import { alarmApi } from '@/api/alarm'
import type { AlarmSystemData } from '@/api/types'

use([PieChart, TooltipComponent, LegendComponent, CanvasRenderer])

const data = ref<AlarmSystemData | null>(null)

onMounted(async () => {
  data.value = await alarmApi.getAlarmBySystem()
})

const colors = ['#3b82f6', '#eab308', '#f97316', '#10b981', '#6b7280']

const chartOption = computed(() => {
  if (!data.value) return {}
  return {
    backgroundColor: 'transparent',
    tooltip: {
      trigger: 'item',
      formatter: '{b}: {c} ({d}%)',
    },
    legend: {
      orient: 'horizontal',
      bottom: 0,
      textStyle: { color: '#9ca3af', fontSize: 10 },
    },
    series: [{
      type: 'pie',
      radius: '60%',
      center: ['50%', '45%'],
      data: data.value.items.map((item, i) => ({
        name: item.name,
        value: item.value,
        itemStyle: { color: colors[i % colors.length] },
      })),
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
  height: 180px;
}
</style>
