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
}>()

const elapsedSeconds = useElapsedSeconds(() => props.time)

const display = computed(() => {
  let totalSeconds = elapsedSeconds.value
  const baseSeconds = Number(props.duration)
  if (!Number.isNaN(baseSeconds)) {
    totalSeconds += baseSeconds
  }
  return formatDuration(totalSeconds)
})
</script>

<style scoped lang="scss">
.elapsed-duration {
  font-variant-numeric: tabular-nums;
}
</style>
