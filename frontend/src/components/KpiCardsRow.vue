<template>
  <div class="kpi-cards-row">
    <div
      v-for="card in kpiCards"
      :key="card.label"
      class="kpi-card"
    >
      <div class="kpi-card-header">
        <el-icon :size="14"><component :is="card.icon" /></el-icon>
        <span class="kpi-label">{{ card.label }}</span>
      </div>
      <div :class="['kpi-value', card.valueClass]">{{ card.displayValue }}</div>
      <div class="kpi-detail">{{ card.detail }}</div>
      <div v-if="card.progress" class="kpi-progress">
        <el-progress
          :percentage="card.progress"
          :stroke-width="3"
          :color="card.progressColor"
          :show-text="false"
        />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import {
  TrendCharts, Lightning, Refrigerator,
  OfficeBuilding, Sunrise, Monitor,
} from '@element-plus/icons-vue'
import { useDashboardStore } from '@/stores/dashboard'

const store = useDashboardStore()
const metrics = computed(() => store.kpiMetrics)

const kpiCards = computed(() => {
  if (!metrics.value) return []
  const m = metrics.value
  return [
    {
      label: 'PUE',
      displayValue: m.pue.toFixed(2),
      detail: m.pueTrend,
      valueClass: m.pueTrend.startsWith('+') ? 'value-warn' : 'value-good',
      icon: TrendCharts,
      progress: null,
      progressColor: '#10b981',
    },
    {
      label: 'IT Load',
      displayValue: `${m.itLoadPercent}%`,
      detail: `${m.itLoadKw.toLocaleString()} / ${m.itLoadCapacityKw.toLocaleString()} kW`,
      valueClass: 'value-good',
      icon: Lightning,
      progress: m.itLoadPercent,
      progressColor: '#10b981',
    },
    {
      label: 'Cooling Load',
      displayValue: `${m.coolingLoadPercent}%`,
      detail: `${m.coolingLoadKw.toLocaleString()} / ${m.coolingLoadCapacityKw.toLocaleString()} kW`,
      valueClass: 'value-good',
      icon: Refrigerator,
      progress: m.coolingLoadPercent,
      progressColor: '#10b981',
    },
    {
      label: 'Facility Load',
      displayValue: `${m.facilityLoadPercent}%`,
      detail: `${m.facilityLoadKw.toLocaleString()} / ${m.facilityLoadCapacityKw.toLocaleString()} kW`,
      valueClass: 'value-good',
      icon: OfficeBuilding,
      progress: m.facilityLoadPercent,
      progressColor: '#10b981',
    },
    {
      label: 'Total Energy',
      displayValue: `${m.totalEnergyKwh.toLocaleString()} kWh`,
      detail: 'Today',
      valueClass: 'value-good',
      icon: Sunrise,
      progress: null,
      progressColor: '#10b981',
    },
    {
      label: 'Capacity Usage',
      displayValue: `${m.capacityUsagePercent}%`,
      detail: `${m.activeDevices.toLocaleString()} / ${m.totalDevices.toLocaleString()} devices online`,
      valueClass: 'value-good',
      icon: Monitor,
      progress: m.capacityUsagePercent,
      progressColor: '#10b981',
    },
  ]
})
</script>

<style scoped lang="scss">
.kpi-cards-row {
  display: flex;
  gap: 10px;
  flex-shrink: 0;
}

.kpi-card {
  background-color: $bg-card;
  border: 1px solid $border-card;
  border-radius: 8px;
  padding: 8px 8px;
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
  transition: border-color 0.2s;
  &:hover { border-color: $accent-green; }
}

.kpi-card-header {
  display: flex;
  align-items: center;
  gap: 6px;
  color: $text-muted;
}

.kpi-label {
  font-size: 12px;
  color: $text-secondary;
}

.kpi-value {
  font-size: 20px;
  font-weight: 700;
  line-height: 1.2;
  &.value-good { color: $accent-green; }
  &.value-warn { color: $accent-orange; }
}

.kpi-detail {
  font-size: 11px;
  color: $text-muted;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.kpi-progress {
  margin-top: 2px;
}
</style>
