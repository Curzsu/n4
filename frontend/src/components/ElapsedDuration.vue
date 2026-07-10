<template>
  <span class="elapsed-duration">{{ display }}</span>
</template>

<script setup lang="ts">
import { useElapsedDuration } from '@/composables/useElapsedDuration'

const props = defineProps<{
  time: string
}>()

const display = useElapsedDuration(props.time)
</script>

<style scoped lang="scss">
.elapsed-duration {
  font-variant-numeric: tabular-nums;
}
</style>
