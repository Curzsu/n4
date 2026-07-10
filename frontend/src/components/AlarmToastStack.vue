<template>
  <div class="alarm-toast-stack">
    <TransitionGroup name="toast">
      <div
        v-for="(toast, index) in toasts"
        :key="toast.toastId"
        class="alarm-toast"
        :class="toast.severity.toLowerCase()"
        :style="toastStyle(index)"
      >
        <div class="toast-header">
          <el-tag :type="severityTagType(toast.severity)" size="small" effect="dark">
            {{ toast.severity }}
          </el-tag>
          <el-icon class="close-btn" @click="removeToast(toast.toastId)"><Close /></el-icon>
        </div>
        <div class="toast-body">
          <div class="toast-row">
            <span class="toast-label">Time:</span>
            <span class="toast-value">{{ toast.time }}</span>
          </div>
          <div class="toast-row">
            <span class="toast-label">Source:</span>
            <span class="toast-value">{{ toast.source }}</span>
          </div>
          <div class="toast-row">
            <span class="toast-label">Message:</span>
            <span class="toast-value">{{ toast.message }}</span>
          </div>
          <div class="toast-row">
            <span class="toast-label">Location:</span>
            <span class="toast-value">{{ toast.location }}</span>
          </div>
          <div class="toast-row">
            <span class="toast-label">Status:</span>
            <span :class="['toast-value', 'status-' + toast.status.toLowerCase()]">{{ toast.status }}</span>
          </div>
          <div class="toast-row">
            <span class="toast-label">Ack. By:</span>
            <span class="toast-value">{{ toast.ackBy }}</span>
          </div>
        </div>
      </div>
    </TransitionGroup>
  </div>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { Close } from '@element-plus/icons-vue'
import { useAlarmWebSocket } from '@/composables/useAlarmWebSocket'
import { alarmAudioConfig } from '@/config/alarmAudio'
import type { AlarmRecord } from '@/api/types'

interface ToastItem extends AlarmRecord {
  toastId: string
}

const toasts = ref<ToastItem[]>([])
const { onUpdate } = useAlarmWebSocket()

const MAX_TOASTS = 5

// Shared Audio instance for alarm sound alerts
const alarmAudio = new Audio(alarmAudioConfig.value.src)
alarmAudio.volume = alarmAudioConfig.value.volume

// Update Audio source when configuration changes
watch(
  alarmAudioConfig,
  (config) => {
    alarmAudio.src = config.src
    alarmAudio.volume = config.volume
  },
  { deep: true }
)

onUpdate((data: any) => {
  if (!data.alarm) return

  const record = normalizeRecord(data.alarm)
  const toast: ToastItem = {
    ...record,
    toastId: `${record.id}-${Date.now()}`,
  }

  toasts.value.push(toast)

  // Keep only the newest toasts to avoid excessive stacking
  if (toasts.value.length > MAX_TOASTS) {
    toasts.value.shift()
  }

  playAlarmSound()
})

function removeToast(toastId: string) {
  toasts.value = toasts.value.filter(t => t.toastId !== toastId)
}

function playAlarmSound() {
  if (!alarmAudioConfig.value.enabled) return

  alarmAudio.currentTime = 0
  alarmAudio.play().catch((err) => {
    console.warn('[AlarmToast] Audio playback was blocked or failed:', err)
  })
}

function toastStyle(index: number) {
  // All toasts share the same fixed position; only z-index varies so the newest is on top
  return {
    bottom: '64px',
    right: '16px',
    zIndex: 1000 + index,
  }
}

function severityTagType(severity: string): string {
  switch (severity) {
    case 'Critical': return 'danger'
    case 'Major': return 'warning'
    case 'Minor': return 'info'
    case 'Warning': return 'primary'
    default: return 'info'
  }
}

/**
 * Normalize a raw alarm record from WebSocket payload.
 * Mirrors the logic in AlarmTable.vue to keep displayed fields consistent.
 */
function normalizeRecord(alarm: any): AlarmRecord {
  const extractLocation = (path?: string) => {
    if (!path) return ''
    const parts = path.split('/')
    return parts.length > 2 ? parts[2] : ''
  }

  const formatTime = (ts: number) => {
    const d = new Date(ts)
    const pad = (n: number) => String(n).padStart(2, '0')
    return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
  }

  const source = alarm.name || alarm.source || ''

  return {
    id: source || `${Date.now()}-${Math.random()}`,
    time: alarm.timestamp ? formatTime(alarm.timestamp) : (alarm.time || '-'),
    severity: alarm.severity || 'Normal',
    source: source,
    message: alarm.message || '',
    location: extractLocation(alarm.path) || alarm.location || '',
    status: alarm.status || 'Unack',
    ackBy: alarm.ackBy || '-',
  }
}
</script>

<style scoped lang="scss">
.alarm-toast-stack {
  position: fixed;
  bottom: 0;
  right: 0;
  pointer-events: none;
  z-index: 1000;
}

.alarm-toast {
  position: fixed;
  width: 320px;
  background-color: $bg-card;
  border: 1px solid $border-card;
  border-left: 4px solid $text-muted;
  border-radius: 8px;
  padding: 12px;
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.5);
  pointer-events: auto;

  &.critical { border-left-color: $accent-red; }
  &.major { border-left-color: $accent-orange; }
  &.minor { border-left-color: $accent-yellow; }
  &.warning { border-left-color: $accent-blue; }
}

.toast-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
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

.toast-body {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.toast-row {
  display: flex;
  gap: 8px;
  font-size: 12px;
  line-height: 1.4;
}

.toast-label {
  color: $text-muted;
  flex-shrink: 0;
  width: 64px;
}

.toast-value {
  color: $text-primary;
  flex: 1;
  word-break: break-word;

  &.status-unack { color: $accent-red; }
  &.status-ack { color: $accent-green; }
  &.status-cleared { color: $text-muted; }
}

.toast-enter-active,
.toast-leave-active {
  transition: all 0.3s ease;
}

.toast-enter-from {
  opacity: 0;
  transform: translateY(20px) scale(0.95);
}

.toast-leave-to {
  opacity: 0;
  transform: translateX(20px) scale(0.95);
}

.toast-leave-active {
  position: fixed;
}
</style>
