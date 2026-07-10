import { ref, computed, onMounted, onUnmounted } from 'vue'
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

/**
 * Returns a live-formatted duration string representing the elapsed time
 * since the given alarm time. Updates automatically every second.
 *
 * Multiple consumers share a single timer to avoid performance issues.
 */
export function useElapsedDuration(alarmTime: string) {
  onMounted(() => {
    listenerCount++
    startTimer()
  })

  onUnmounted(() => {
    listenerCount--
    stopTimer()
  })

  return computed(() => {
    const start = parseAlarmTime(alarmTime)
    if (!start) return '-'
    const elapsedSeconds = (now.value - start.getTime()) / 1000
    return formatDuration(elapsedSeconds)
  })
}
