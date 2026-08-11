<template>
  <div class="alarm-toast-stack">
    <TransitionGroup name="toast">
      <div
        v-for="(toast, index) in toasts"
        :key="toast.toastId"
        class="alarm-toast"
        :style="[toastStyle(index), { borderLeftColor: getSeverityColor(toast.severity) }]"
      >
        <span class="toast-time">{{ formatTimeOnly(toast.time) }}</span>
        <span
          class="severity-tag"
          :style="{ color: '#111827', backgroundColor: getSeverityColor(toast.severity), borderColor: '#111827' }"
        >
          {{ toast.severity }}
        </span>
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
import { getSeverityColor } from '@/config/severityColors'
import { normalizeRecord } from '@/utils/alarm'
import type { AlarmRecord } from '@/api/types'

/** Extract HH:mm:ss from a "yyyy-MM-dd HH:mm:ss" time string */
function formatTimeOnly(time: string): string {
  if (!time) return '-'
  // Take the part after the first space, or the whole string if no space
  const spaceIdx = time.indexOf(' ')
  return spaceIdx >= 0 ? time.substring(spaceIdx + 1) : time
}

/**
 * Alarm levels form two independent severity groups:
 *   High group: High-High (severe) > High (minor)
 *   Low  group: Low-Low    (severe) > Low  (minor)
 *
 * A de-escalation (no toast) is ONLY a same-group severe -> minor transition:
 *   High-High -> High, Low-Low -> Low.
 * Any cross-group transition (e.g. High-High -> Low, Low -> High-High) still
 * pops a toast, because the two groups are not directly comparable.
 */
const LEVEL_GROUP: Record<string, { group: 'high' | 'low'; rank: number }> = {
  'High-High': { group: 'high', rank: 0 },
  'High':      { group: 'high', rank: 1 },
  'Low-Low':   { group: 'low',  rank: 0 },
  'Low':       { group: 'low',  rank: 1 },
}

/** Parse a stageMessage like "Start - High-High to High" into {from, to}.
 *  The backend formats a level-transition start as "Start - {oldLevel} to {newLevel}",
 *  so strip the "Start - " prefix first, then split on " to ". Levels may contain
 *  hyphens (e.g. "High-High"). Returns null for non-transition messages. */
function parseLevelTransition(stageMessage?: string): { from: string; to: string } | null {
  if (!stageMessage) return null
  const prefix = 'Start - '
  const body = stageMessage.startsWith(prefix) ? stageMessage.slice(prefix.length) : stageMessage
  // Split on the FIRST " to " only, in case a level name contained " to"
  // (none do today, but this is safer than a greedy split).
  const idx = body.indexOf(' to ')
  if (idx < 0) return null
  const from = body.slice(0, idx).trim()
  const to = body.slice(idx + 4).trim()
  if (!from || !to) return null
  return { from, to }
}

/** A de-escalation is a same-group transition from a severe level to a minor
 *  level (rank increases within the same group). Cross-group or unknown levels
 *  are NOT de-escalations and still trigger a toast. */
function isDeEscalation(fromLevel: string, toLevel: string): boolean {
  const from = LEVEL_GROUP[fromLevel]
  const to = LEVEL_GROUP[toLevel]
  if (!from || !to) return false
  if (from.group !== to.group) return false
  return to.rank > from.rank
}

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

  // Level de-escalation (e.g. High-High -> High, Low-Low -> Low) is also a
  // stage=start event, but it should NOT trigger a new toast popup: the alarm
  // is ongoing, only its severity dropped. The stageMessage for an escalation
  // or de-escalation carries "Start - {oldLevel} to {newLevel}"; parse it and
  // compare severity weights. A de-escalation (new level is less severe) is
  // suppressed. Pure escalations (more severe) still pop so operators notice.
  const transition = parseLevelTransition(record.stageMessage)
  if (transition && isDeEscalation(transition.from, transition.to)) {
    return
  }

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
  border-left: 4px solid $text-muted;  /* overridden by inline style */
  border-radius: 8px;
  padding: 0 12px;
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.5);
  pointer-events: auto;
}

.toast-time {
  font-size: 12px;
  color: $text-muted;
  white-space: nowrap;
  flex-shrink: 0;
}

.severity-tag {
  width: 55px;
  height: 18px;
  line-height: 18px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  font-size: 12px;
  font-weight: 600;
  border: 1px solid;
  border-radius: 3px;
  /* color / backgroundColor / borderColor set via inline style */
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
