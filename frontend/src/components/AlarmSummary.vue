<template>
  <div class="alarm-summary-panel">
    <div class="panel-header">
      <span class="panel-title">Alarm Summary</span>
      <span class="view-all-link">View All</span>
    </div>
    <div class="alarm-counts">
      <div class="alarm-count-item critical">
        <span class="count-value">{{ alarmSummary?.critical ?? 0 }}</span>
        <span class="count-label">Critical</span>
      </div>
      <div class="alarm-count-item major">
        <span class="count-value">{{ alarmSummary?.major ?? 0 }}</span>
        <span class="count-label">Major</span>
      </div>
      <div class="alarm-count-item minor">
        <span class="count-value">{{ alarmSummary?.minor ?? 0 }}</span>
        <span class="count-label">Minor</span>
      </div>
      <div class="alarm-count-item warning">
        <span class="count-value">{{ alarmSummary?.warning ?? 0 }}</span>
        <span class="count-label">Warning</span>
      </div>
    </div>
    <div class="alarm-top5">
      <div
        v-for="alarm in alarmTop5"
        :key="alarm.alarmTime + alarm.alarmDesc"
        class="alarm-item"
      >
        <span :class="['alarm-dot', alarm.alarmLevel.toLowerCase()]"></span>
        <span class="alarm-time">{{ alarm.alarmTime }}</span>
        <span class="alarm-desc">{{ alarm.alarmDesc }}</span>
        <span :class="['alarm-level-badge', alarm.alarmLevel.toLowerCase()]">{{ alarm.alarmLevel }}</span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useDashboardStore } from '@/stores/dashboard'

const store = useDashboardStore()
const alarmSummary = computed(() => store.alarmSummary)
const alarmTop5 = computed(() => store.alarmTop5)
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
  margin-bottom: 12px;
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
  font-size: 20px;
  font-weight: 700;
  display: block;
  &.critical { color: $accent-red; }
  &.major { color: $accent-orange; }
  &.minor { color: $accent-yellow; }
  &.warning { color: $accent-blue; }
}

.count-label {
  font-size: 11px;
  color: $text-muted;
}

.alarm-top5 {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.alarm-item {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 4px 0;
  font-size: 12px;
}

.alarm-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  &.critical { background-color: $accent-red; }
  &.major { background-color: $accent-orange; }
  &.minor { background-color: $accent-yellow; }
  &.warning { background-color: $accent-blue; }
}

.alarm-time {
  color: $text-muted;
  min-width: 36px;
}

.alarm-desc {
  color: $text-secondary;
  flex: 1;
}

.alarm-level-badge {
  font-size: 10px;
  padding: 1px 4px;
  border-radius: 3px;
  &.critical { color: $accent-red; background: rgba($accent-red, 0.15); }
  &.major { color: $accent-orange; background: rgba($accent-orange, 0.15); }
  &.minor { color: $accent-yellow; background: rgba($accent-yellow, 0.15); }
  &.warning { color: $accent-blue; background: rgba($accent-blue, 0.15); }
}
</style>
