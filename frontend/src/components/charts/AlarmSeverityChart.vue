<template>
  <div class="chart-panel">
    <div class="chart-header">
      <div class="chart-title">Alarms by Severity (Today)</div>
      <el-icon class="close-btn" @click="emit('close')"><Close /></el-icon>
    </div>
    <v-chart :option="chartOption" autoresize class="chart-instance" />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { Close } from '@element-plus/icons-vue'
import VChart from 'vue-echarts'
import { use } from 'echarts/core'
import { PieChart } from 'echarts/charts'
import { TooltipComponent, LegendComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import { alarmApi } from '@/api/alarm'
import { useAlarmWebSocket } from '@/composables/useAlarmWebSocket'

use([PieChart, TooltipComponent, LegendComponent, CanvasRenderer])

const emit = defineEmits<{
  (e: 'close'): void
}>()

const { severityData } = useAlarmWebSocket()

const colorMap: Record<string, string> = {
  Critical: '#ef4444',
  Major: '#f97316',
  Minor: '#eab308',
  Warning: '#3b82f6',
}

const chartOption = computed(() => {
  const data = severityData.value
  if (!data || !data.items || data.items.length === 0) return {}
  return {
    backgroundColor: 'transparent',
    tooltip: {
      trigger: 'item',
      formatter: '{b}: {c} ({d}%)',
    },
    legend: {
      orient: 'vertical',
      right: 10,
      top: 'center',
      textStyle: { color: '#9ca3af', fontSize: 11 },
    },
    series: [{
      type: 'pie',
      radius: ['40%', '70%'],
      center: ['35%', '50%'],
      avoidLabelOverlap: false,
      label: {
        show: true,
        position: 'center',
        formatter: `{a|${data.total}}\n{b|Total}`,
        rich: {
          a: { fontSize: 24, fontWeight: 'bold', color: '#e5e7eb', lineHeight: 30 },
          b: { fontSize: 11, color: '#6b7280' },
        },
      },
      data: data.items.map(item => ({
        name: item.name,
        value: item.value,
        itemStyle: { color: colorMap[item.name] || '#6b7280' },
      })),
    }],
  }
})

// Initial fetch
onMounted(async () => {
  try {
    severityData.value = await alarmApi.getAlarmBySeverity()
  } catch (e) {
    console.error('Failed to fetch severity data:', e)
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
.chart-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}

.chart-title {
  font-size: 13px;
  color: $text-secondary;
  font-weight: 500;
}

.close-btn {
  width: 22px;
  height: 22px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 4px;
  color: $text-muted;
  cursor: pointer;
  transition: color 0.2s, background-color 0.2s;

  &:hover {
    color: $text-primary;
    background-color: $bg-card-hover;
  }
}

.chart-instance {
  width: 100%;
  height: 200px;
}
</style>
