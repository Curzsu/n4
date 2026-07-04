<template>
  <div class="alarm-view">
    <!-- Top: Alarm Table -->
    <AlarmTable class="alarm-table-section" />

    <!-- Bottom: Three charts side by side -->
    <div class="alarm-charts-row">
      <AlarmSeverityChart class="chart-item" />
      <AlarmTrendChart class="chart-item" />
      <AlarmHourChart class="chart-item" />
    </div>
  </div>
</template>

<script setup lang="ts">
import AlarmTable from '@/components/AlarmTable.vue'
import AlarmSeverityChart from '@/components/charts/AlarmSeverityChart.vue'
import AlarmTrendChart from '@/components/charts/AlarmTrendChart.vue'
import AlarmHourChart from '@/components/charts/AlarmHourChart.vue'
</script>

<style scoped lang="scss">
.alarm-view {
  display: flex;
  flex-direction: column;
  gap: 12px;
  flex: 1;
  min-height: 0;
  overflow: hidden;
}

.alarm-table-section {
  flex: 1;
  min-height: 0;
}

.alarm-charts-row {
  display: flex;
  gap: 12px;
  flex-shrink: 0;
}

.chart-item {
  flex: 1;
  min-width: 0;
}
</style>
