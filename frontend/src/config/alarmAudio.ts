import { ref } from 'vue'

/**
 * Configurable alarm audio settings.
 *
 * Audio files should be placed in the public folder (e.g. public/sounds/)
 * and referenced with an absolute path from the site root.
 *
 * Example:
 *   setAlarmAudio('/sounds/alarm-urgent.wav', 0.8)
 *   toggleAlarmAudio(false)
 */
export const alarmAudioConfig = ref({
  /** Whether sound alerts are enabled */
  enabled: true,

  /** Audio file URL */
  src: '/sounds/alarm-beep.wav',

  /** Playback volume (0.0 - 1.0) */
  volume: 0.6,
})

/**
 * Change the alarm audio file and optional volume.
 */
export function setAlarmAudio(src: string, volume?: number) {
  alarmAudioConfig.value.src = src
  if (volume !== undefined) {
    alarmAudioConfig.value.volume = volume
  }
}

/**
 * Enable or disable alarm audio alerts.
 */
export function toggleAlarmAudio(enabled: boolean) {
  alarmAudioConfig.value.enabled = enabled
}
