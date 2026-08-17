<template>
  <div class="alarm-summary-panel">
    <div class="panel-header">
      <span class="panel-title">Alarm Summary (Today)</span>
      <span class="view-all-link">View All</span>
    </div>
    <div class="alarm-counts">
      <div class="alarm-count-item critical">
        <span class="count-value">{{ summary.critical }}</span>
        <span class="count-label">Critical</span>
      </div>
      <div class="alarm-count-item major">
        <span class="count-value">{{ summary.major }}</span>
        <span class="count-label">Major</span>
      </div>
      <div class="alarm-count-item minor">
        <span class="count-value">{{ summary.minor }}</span>
        <span class="count-label">Minor</span>
      </div>
      <div class="alarm-count-item warning">
        <span class="count-value">{{ summary.warning }}</span>
        <span class="count-label">Warning</span>
      </div>
    </div>
    <div class="date-range">{{ currentDate }}</div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useAlarmWebSocket } from '@/composables/useAlarmWebSocket'

const { summaryData } = useAlarmWebSocket()

const summary = computed(() => summaryData.value)

const currentDate = computed(() => {
  const now = new Date()
  const start = new Date(now.getFullYear(), now.getMonth(), now.getDate())
  const end = new Date(now.getFullYear(), now.getMonth(), now.getDate(), 23, 59, 59)
  const fmt = (d: Date) => d.toLocaleString('zh-CN', {
    year: 'numeric', month: '2-digit', day: '2-digit',
    hour: '2-digit', minute: '2-digit', second: '2-digit',
    hour12: false
  }).replace(/\//g, '-')
  return `${fmt(start)} — ${fmt(end)}`
})

// Initial data comes from the WebSocket composable's connect-time seed;
// live updates arrive via the alarm:notifications pushes.
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
