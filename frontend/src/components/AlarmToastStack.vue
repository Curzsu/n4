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
        <span class="toast-time">{{ toast.time }}</span>
        <el-tag class="severity-tag" :type="severityTagType(toast.severity)" size="small" effect="dark">
          {{ toast.severity }}
        </el-tag>
        <span class="toast-source" :title="toast.source">{{ toast.source }}</span>
        <el-icon class="close-btn" @click="removeToast(toast.toastId)"><Close /></el-icon>
      </div>
    </TransitionGroup>
  </div>
</template>

<script setup lang="ts">
import { ref, watch, onMounted, onBeforeUnmount } from 'vue'
import { Close } from '@element-plus/icons-vue'
import { useAlarmWebSocket } from '@/composables/useAlarmWebSocket'
import { alarmAudioConfig } from '@/config/alarmAudio'
import { normalizeRecord, severityTagType } from '@/utils/alarm'
import type { AlarmRecord } from '@/api/types'

interface ToastItem extends AlarmRecord {
  toastId: string
}

const toasts = ref<ToastItem[]>([])
const { onUpdate } = useAlarmWebSocket()

// Layout constants for the single-line toast stack.
// Each toast occupies TOAST_HEIGHT px of vertical space; the stack grows upward
// from BOTTOM_OFFSET so the newest toast (last in the array) sits at the top.
const TOAST_HEIGHT = 48
const TOAST_GAP = 8
const BOTTOM_OFFSET = 64
const TOP_MARGIN = 16

// Maximum number of toasts that fit on screen at the current viewport height.
const maxToasts = ref(computeMaxToasts())

function computeMaxToasts() {
  const available = window.innerHeight - BOTTOM_OFFSET - TOP_MARGIN
  return Math.max(1, Math.floor(available / (TOAST_HEIGHT + TOAST_GAP)))
}

function handleResize() {
  maxToasts.value = computeMaxToasts()
  // Shrink the stack if the viewport got smaller
  while (toasts.value.length > maxToasts.value) {
    toasts.value.shift()
  }
}

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

  // Only toast the first occurrence of an alarm: stage=start + Unack.
  // Continue/end/ack/recovery updates do not pop a toast.
  if (record.stage !== 'start' || record.status !== 'Unack') return

  const toast: ToastItem = {
    ...record,
    toastId: `${record.id}-${Date.now()}`,
  }

  toasts.value.push(toast)

  // Keep the stack within the on-screen capacity: drop the oldest (bottom) toast.
  if (toasts.value.length > maxToasts.value) {
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
  // index 0 is the oldest toast (bottom of the stack); the last index is the
  // newest (top). Each toast is offset upward by its position in the array so
  // newer toasts appear above older ones instead of overlapping them.
  return {
    bottom: `${BOTTOM_OFFSET + index * (TOAST_HEIGHT + TOAST_GAP)}px`,
    right: '16px',
  }
}

onMounted(() => {
  window.addEventListener('resize', handleResize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
})
</script>

<style scoped lang="scss">
$toast-height: 48px;

.alarm-toast-stack {
  position: fixed;
  bottom: 0;
  right: 0;
  pointer-events: none;
  z-index: 1000;
}

.alarm-toast {
  position: fixed;
  width: 420px;
  height: $toast-height;
  box-sizing: border-box;
  display: flex;
  align-items: center;
  gap: 10px;
  background-color: $bg-card;
  border: 1px solid $border-card;
  border-left: 4px solid $text-muted;
  border-radius: 8px;
  padding: 0 12px;
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.5);
  pointer-events: auto;

  &.critical { border-left-color: $accent-red; }
  &.major { border-left-color: $accent-orange; }
  &.minor { border-left-color: $accent-yellow; }
  &.warning { border-left-color: $accent-blue; }
}

.toast-time {
  font-size: 12px;
  color: $text-muted;
  white-space: nowrap;
  flex-shrink: 0;
}

.severity-tag {
  width: 70px;
  display: inline-flex;
  justify-content: center;
  flex-shrink: 0;
}

.toast-source {
  font-size: 12px;
  color: $text-primary;
  flex: 1;
  min-width: 0;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.close-btn {
  width: 22px;
  height: 22px;
  flex-shrink: 0;
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
