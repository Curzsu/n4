import { ref } from 'vue'

/**
 * Configurable alarm toast (popup) settings.
 *
 * The toast stack is mounted globally in App.vue so it appears on every page.
 * maxToasts caps how many popups are shown at once; when exceeded, the oldest
 * toast is dropped. It can be overridden at runtime:
 *
 *   setAlarmToastMax(6)
 */
export const alarmToastConfig = ref({
  /** Maximum number of toasts shown at the same time */
  maxToasts: 5,
})

/**
 * Change the maximum number of simultaneously visible alarm toasts.
 */
export function setAlarmToastMax(max: number) {
  alarmToastConfig.value.maxToasts = Math.max(1, Math.floor(max))
}
