<template>
  <div class="alarm-summary-panel">
    <div class="panel-header">
      <span class="panel-title">Alarm Summary (Today)</span>
      <span class="view-all-link">View All</span>
    </div>
    <div class="alarm-counts">
      <div class="alarm-count-item critical">
        <span class="count-value">{{ summary?.critical || 0 }}</span>
        <span class="count-label">Critical</span>
      </div>
      <div class="alarm-count-item major">
        <span class="count-value">{{ summary?.major || 0 }}</span>
        <span class="count-label">Major</span>
      </div>
      <div class="alarm-count-item minor">
        <span class="count-value">{{ summary?.minor || 0 }}</span>
        <span class="count-label">Minor</span>
      </div>
      <div class="alarm-count-item warning">
        <span class="count-value">{{ summary?.warning || 0 }}</span>
        <span class="count-label">Warning</span>
      </div>
    </div>
    <div class="date-range">2024-05-01 00:00:00 — 2024-05-31 23:59:59</div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { alarmApi } from '@/api/alarm'
import type { AlarmSummary } from '@/api/types'

const summary = ref<AlarmSummary | null>(null)

onMounted(async () => {
  summary.value = await alarmApi.getAlarmSummary()
})
</script>

<style scoped lang="scss">
.alarm-summary-panel {
  background-color: $bg-card;
  border: 1px solid $border-card;
  border-radius: 8px;
  padding: 12px;
}

.panel-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.panel-title {
  font-size: 14px;
  font-weight: 600;
  color: $text-primary;
}

.view-all-link {
  font-size: 12px;
  color: $accent-blue;
  cursor: pointer;
  &:hover { text-decoration: underline; }
}

.alarm-counts {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 8px;
  margin-bottom: 10px;
}

.alarm-count-item {
  padding: 8px;
  border-radius: 6px;
  text-align: center;
  &.critical { background: rgba($accent-red, 0.15); }
  &.major { background: rgba($accent-orange, 0.15); }
  &.minor { background: rgba($accent-yellow, 0.15); }
  &.warning { background: rgba($accent-blue, 0.15); }
}

.count-value {
  font-size: 22px;
  font-weight: 700;
  display: block;
  &.critical, .critical & { color: $accent-red; }
  &.major, .major & { color: $accent-orange; }
  &.minor, .minor & { color: $accent-yellow; }
  &.warning, .warning & { color: $accent-blue; }
}

.critical .count-value { color: $accent-red; }
.major .count-value { color: $accent-orange; }
.minor .count-value { color: $accent-yellow; }
.warning .count-value { color: $accent-blue; }

.count-label {
  font-size: 11px;
  color: $text-muted;
}

.date-range {
  font-size: 11px;
  color: $text-muted;
  text-align: center;
}
</style>
