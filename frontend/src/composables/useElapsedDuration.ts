import { ref, computed, onMounted, onUnmounted, unref } from 'vue'
import { formatDuration, parseAlarmTime } from '@/utils/time'

// Shared reactive timestamp updated every second
const now = ref(Date.now())
let timer: number | null = null
let listenerCount = 0

function startTimer() {
  if (timer !== null) return
  now.value = Date.now()
  timer = window.setInterval(() => {
    now.value = Date.now()
  }, 1000)
}

function stopTimer() {
  if (listenerCount === 0 && timer !== null) {
    window.clearInterval(timer)
    timer = null
  }
}

type TimeInput = string | (() => string)

function resolveTime(input: TimeInput): string {
  return typeof input === 'function' ? input() : unref(input)
}

/**
 * Returns the elapsed seconds since the given alarm time. Updates automatically every second.
 *
 * The input can be a string or a getter function. Use a getter when the time value
 * may change during the component lifecycle (e.g., table rows that are reused).
 *
 * Multiple consumers share a single timer to avoid performance issues.
 */
export function useElapsedSeconds(alarmTime: TimeInput) {
  onMounted(() => {
    listenerCount++
    startTimer()
  })

  onUnmounted(() => {
    listenerCount--
    stopTimer()
  })

  return computed(() => {
    const start = parseAlarmTime(resolveTime(alarmTime))
    if (!start) return 0
    return Math.max(0, (now.value - start.getTime()) / 1000)
  })
}

/**
 * Returns a live-formatted duration string representing the elapsed time
 * since the given alarm time. Updates automatically every second.
 *
 * Multiple consumers share a single timer to avoid performance issues.
 */
export function useElapsedDuration(alarmTime: TimeInput) {
  const elapsedSeconds = useElapsedSeconds(alarmTime)
  return computed(() => formatDuration(elapsedSeconds.value))
}
