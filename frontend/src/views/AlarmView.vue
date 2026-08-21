<script setup lang="ts">
import { ref, computed } from 'vue'
import { useRoute } from 'vue-router'
import AlarmTable from '@/components/AlarmTable.vue'
import AlarmSeverityChart from '@/components/charts/AlarmSeverityChart.vue'
import AlarmTrendChart from '@/components/charts/AlarmTrendChart.vue'
import AlarmHourChart from '@/components/charts/AlarmHourChart.vue'

const route = useRoute()

const showSeverityChart = ref(true)
const showTrendChart = ref(true)
const showHourChart = ref(true)

// Severity pre-filter coming from the top-navbar badge click (?severity=Critical).
// Passed down to AlarmTable so the Severity column filter is seeded automatically.
const initialSeverity = computed(() => {
  const s = route.query.severity
  return typeof s === 'string' ? s : ''
})
</script>

<template>
  <div class="alarm-view">
    <!-- Alarm table fills the entire visible content area -->
    <div class="alarm-table-wrapper">
      <AlarmTable :initial-severity="initialSeverity" />
    </div>

    <!-- Charts are below the fold and require scrolling to view -->
    <div v-if="showSeverityChart || showTrendChart || showHourChart" class="alarm-charts-row">
      <AlarmSeverityChart
        v-if="showSeverityChart"
        class="chart-item"
        @close="showSeverityChart = false"
      />
      <AlarmTrendChart
        v-if="showTrendChart"
        class="chart-item"
        @close="showTrendChart = false"
      />
      <AlarmHourChart
        v-if="showHourChart"
        class="chart-item"
        @close="showHourChart = false"
      />
    </div>
  </div>
</template>

<style scoped lang="scss">
.alarm-view {
  display: flex;
  flex-direction: column;
  gap: 12px;
  min-height: 100%;
}

.alarm-table-wrapper {
  flex: 0 0 calc(100vh - 128px);
  min-height: 0;
  display: flex;
  flex-direction: column;
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
