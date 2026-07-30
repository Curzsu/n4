<template>
  <span class="elapsed-duration">{{ display }}</span>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useElapsedSeconds } from '@/composables/useElapsedDuration'
import { formatDuration } from '@/utils/time'

const props = defineProps<{
  time: string
  duration?: string
  stage?: string
}>()

const elapsedSeconds = useElapsedSeconds(() => props.time)

const display = computed(() => {
  // stage === 'end' means the alarm has recovered: freeze the duration at the
  // final value passed in, stop the live elapsed-time accumulation.
  if (props.stage === 'end') {
    const baseSeconds = Number(props.duration)
    return formatDuration(Number.isNaN(baseSeconds) ? 0 : baseSeconds)
  }
  // Ongoing alarm: the row's time is the alarm start timestamp (notifications now
  // always carry the alarm start time), so the live duration is simply the elapsed
  // seconds since that start time. No baseDuration accumulation is needed.
  return formatDuration(elapsedSeconds.value)
})
</script>

<style scoped lang="scss">
.elapsed-duration {
  font-variant-numeric: tabular-nums;
}
</style>
