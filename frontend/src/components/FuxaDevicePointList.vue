<template>
  <section v-if="panel" class="linked-points">
    <header class="linked-points__header">
      <h4>Active alarms</h4>
      <span class="linked-points__device">{{ panel.deviceRole || 'Unnamed device' }}</span>
    </header>

    <div class="linked-points__body">
      <table v-if="alarms.length" class="linked-points__table">
        <thead>
          <tr>
            <th>No.</th>
            <th>Time</th>
            <th>Severity</th>
            <th>Source</th>
            <th>Message</th>
            <th>Location</th>
            <th>Status</th>
            <th>Duration</th>
            <th>Ack. by</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="(alarm, index) in alarms" :key="alarm.id">
            <td>{{ alarms.length - index }}</td>
            <td class="linked-points__time">{{ alarm.time }}</td>
            <td>
              <span class="linked-points__severity" :style="{ color: severityColor(alarm.severity) }">
                {{ alarm.severity || 'Unknown' }}
              </span>
            </td>
            <td>{{ alarm.source }}</td>
            <td class="linked-points__message">{{ alarm.message || alarm.source }}</td>
            <td>{{ alarm.location }}</td>
            <td>{{ alarm.status }}</td>
            <td>{{ alarm.duration }}</td>
            <td>{{ alarm.ackBy }}</td>
          </tr>
        </tbody>
      </table>
      <p v-else class="linked-points__empty">
        {{ initialLoading ? 'Loading active alarms...' : 'No active alarms for this device.' }}
      </p>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { alarmApi } from '@/api/alarm'
import type { AlarmRecord } from '@/api/types'
import { getSeverityColor } from '@/config/severityColors'
import { normalizeRecord } from '@/utils/alarm'
import { useAlarmWebSocket } from '@/composables/useAlarmWebSocket'

export interface FuxaLinkedPoint {
  field: string
  tagId?: string
}

export interface FuxaDevicePanelContext {
  panelKey: string
  templateType?: string
  deviceRole: string
  mqttDeviceName: string
  fields: FuxaLinkedPoint[]
}

const props = defineProps<{
  panel: FuxaDevicePanelContext | null
}>()

const initialLoading = ref(true)
const records = ref<AlarmRecord[]>([])
let removeAlarmListener: (() => void) | undefined

const matchKeys = computed(() => {
  if (!props.panel) return []
  return [
    props.panel.deviceRole,
    ...props.panel.fields.map(field => field.tagId || ''),
  ].filter(Boolean)
})

const alarms = computed(() => records.value
  .filter(matchesPanel))

function matchesPanel(record: AlarmRecord) {
  const source = [record.path, record.source, record.location].filter(Boolean).join(' ')
  return matchKeys.value.some(key => source.includes(key))
}

function severityColor(severity: string) {
  return getSeverityColor(severity)
}

async function loadAlarms() {
  try {
    const raw = await alarmApi.getActiveAlarms('active')
    records.value = raw.map(normalizeRecord)
  } catch (error) {
    console.error('Failed to load device alarms for FUXA card:', error)
  } finally {
    initialLoading.value = false
  }
}

const { onUpdate } = useAlarmWebSocket()

function applyAlarmUpdate(data: { alarm?: unknown }) {
  if (!data.alarm) return

  const incoming = normalizeRecord(data.alarm)
  const existingIndex = records.value.findIndex(record => record.id === incoming.id)
  const cleared = incoming.stage === 'end' && incoming.status === 'Ack'

  if (cleared) {
    if (existingIndex >= 0) records.value.splice(existingIndex, 1)
    return
  }

  if (!matchesPanel(incoming)) return

  if (existingIndex >= 0) {
    records.value[existingIndex] = incoming
  } else {
    records.value.unshift(incoming)
  }

  records.value.sort((left, right) => right.time.localeCompare(left.time))
}

onMounted(() => {
  void loadAlarms()
  removeAlarmListener = onUpdate(applyAlarmUpdate)
})

onBeforeUnmount(() => {
  removeAlarmListener?.()
})
</script>

<style scoped lang="scss">
.linked-points {
  overflow: hidden;
  background: #090d12;
}

.linked-points__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  min-height: 34px;
  padding: 0 13px;
  background: #0d1722;
  border-bottom: 1px solid #1d3445;
}

h4 {
  margin: 0;
  color: #d2e2ed;
  font-size: 14px;
  font-weight: 600;
  white-space: nowrap;
}

.linked-points__device {
  min-width: 0;
  overflow: hidden;
  color: #93b0c3;
  font-family: Consolas, 'Courier New', monospace;
  font-size: 12px;
  text-align: right;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.linked-points__body {
  min-height: 132px;
  overflow: auto;
  background: #090d12;
}

.linked-points__table {
  min-width: 1290px;
  border-collapse: collapse;
  color: #d1dce4;
  font-size: 12px;
  line-height: 1.25;
  table-layout: fixed;

  th,
  td {
    overflow: hidden;
    padding: 6px 9px;
    border: 0;
    border-bottom: 1px solid #152633;
    text-align: left;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  th {
    color: #8fa9ba;
    background: #0b131d;
    font-size: 11px;
    font-weight: 600;
  }

  th:nth-child(1) { width: 44px; }
  th:nth-child(2) { width: 150px; }
  th:nth-child(3) { width: 90px; }
  th:nth-child(4) { width: 220px; }
  th:nth-child(5) { width: 260px; }
  th:nth-child(6) { width: 180px; }
  th:nth-child(7) { width: 90px; }
  th:nth-child(8) { width: 90px; }
  th:nth-child(9) { width: 100px; }

  tbody tr:last-child td { border-bottom: 0; }
}

.linked-points__time {
  color: #9db4c2;
  font-family: Consolas, 'Courier New', monospace;
}

.linked-points__severity { font-weight: 600; }
.linked-points__message { color: #dfe8ee; }

.linked-points__empty {
  margin: 0;
  padding: 24px 13px;
  color: #8298a8;
  font-size: 13px;
  text-align: center;
}
</style>
