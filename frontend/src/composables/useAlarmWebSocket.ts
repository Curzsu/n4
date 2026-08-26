import { ref, computed, onMounted } from 'vue'
import { alarmApi } from '@/api/alarm'
import type { AlarmRecord, AlarmSummary, AlarmSeverityData, AlarmHourData } from '@/api/types'

/**
 * WebSocket composable for real-time alarm data.
 * Connects to backend WebSocket and provides reactive alarm data.
 */

// Shared state (singleton across components)
const isConnected = ref(false)
const latestAlarm = ref<AlarmRecord | null>(null)
const summaryData = ref<AlarmSummary>({ critical: 0, major: 0, minor: 0, warning: 0 })
const severityData = ref<AlarmSeverityData>({ total: 0, items: [] })
const hourlyData = ref<AlarmHourData>({ hours: [], counts: [] })

// Total count of currently active alarms (all severities). This mirrors the
// row count of the Active Alarms table: alarm:active only holds isAlarm=true
// records, every one of which has a non-empty severity (Critical/Major/Minor/
// Warning), so the four summary fields sum to the active record count. Kept as
// a computed so every WS push to summaryData automatically updates it.
const activeAlarmCount = computed(() =>
  summaryData.value.critical + summaryData.value.major +
  summaryData.value.minor + summaryData.value.warning
)

// Listeners for individual components to react to updates
type UpdateCallback = (data: any) => void
const listeners: Set<UpdateCallback> = new Set()

let ws: WebSocket | null = null
let reconnectTimer: ReturnType<typeof setTimeout> | null = null
let reconnectAttempts = 0
const MAX_RECONNECT_DELAY = 30000

function connect() {
  if (ws && (ws.readyState === WebSocket.OPEN || ws.readyState === WebSocket.CONNECTING)) {
    return
  }

  const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
  const wsUrl = `${protocol}//${window.location.host}/ws/alarm`

  try {
    ws = new WebSocket(wsUrl)
  } catch (e) {
    console.error('[WebSocket] Failed to create connection:', e)
    scheduleReconnect()
    return
  }

  ws.onopen = () => {
    console.log('[WebSocket] Connected to alarm stream')
    isConnected.value = true
    reconnectAttempts = 0
    // Seed the shared summary once per connection so badges/panels show real
    // counts immediately instead of zeros until the first alarm push.
    alarmApi.getAlarmSummary()
      .then(data => { summaryData.value = data })
      .catch(() => { /* keep zeros; next alarm push refreshes */ })
  }

  ws.onmessage = (event) => {
    try {
      const data = JSON.parse(event.data)

      if (data.type === 'alarm_update') {
        // Update shared state
        if (data.alarm) {
          latestAlarm.value = data.alarm
        }
        if (data.summary) {
          summaryData.value = data.summary
        }
        if (data.severityData) {
          severityData.value = data.severityData
        }
        if (data.hourlyData) {
          hourlyData.value = data.hourlyData
        }

        // Notify all listeners
        listeners.forEach(cb => cb(data))
      }
    } catch (e) {
      console.error('[WebSocket] Failed to parse message:', e)
    }
  }

  ws.onclose = (event) => {
    console.log('[WebSocket] Disconnected:', event.code, event.reason)
    isConnected.value = false
    ws = null
    scheduleReconnect()
  }

  ws.onerror = (event) => {
    console.error('[WebSocket] Error:', event)
    isConnected.value = false
  }
}

function scheduleReconnect() {
  if (reconnectTimer) {
    clearTimeout(reconnectTimer)
  }
  // Exponential backoff: 1s, 2s, 4s, 8s, ... max 30s
  const delay = Math.min(1000 * Math.pow(2, reconnectAttempts), MAX_RECONNECT_DELAY)
  reconnectAttempts++
  console.log(`[WebSocket] Reconnecting in ${delay}ms (attempt ${reconnectAttempts})...`)
  reconnectTimer = setTimeout(connect, delay)
}

function disconnect() {
  if (reconnectTimer) {
    clearTimeout(reconnectTimer)
    reconnectTimer = null
  }
  if (ws) {
    ws.close()
    ws = null
  }
  isConnected.value = false
}

/**
 * Register a callback to be called when WebSocket receives new data.
 */
function onUpdate(callback: UpdateCallback) {
  listeners.add(callback)
  return () => listeners.delete(callback)
}

/**
 * Composable for using alarm WebSocket in components.
 * Call this in any component that needs real-time alarm data.
 */
export function useAlarmWebSocket() {
  onMounted(() => {
    connect()
  })

  return {
    isConnected,
    latestAlarm,
    summaryData,
    activeAlarmCount,
    severityData,
    hourlyData,
    onUpdate,
    connect,
    disconnect,
  }
}
