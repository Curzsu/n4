<template>
  <div class="alarm-summary-placeholder">
    <img
      src="@/assets/alarm-summary-placeholder.png"
      alt="Alarm Summary Placeholder"
      class="placeholder-image"
    />
  </div>
</template>

<script setup lang="ts">
// Temporary static placeholder replacing the live Alarm Summary panel.
// Swap back to the real AlarmSummary component once the panel is restored.
</script>

<style scoped lang="scss">
.alarm-summary-placeholder {
  background-color: $bg-card;
  border: 1px solid $border-card;
  border-radius: 8px;
  padding: 12px;
}

.placeholder-image {
  width: 100%;
  height: auto;
  display: block;
  border-radius: 4px;
}
</style>
