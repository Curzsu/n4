<template>
  <div class="alarm-sidebar">
    <span>Alarm Sidebar</span>
  </div>
</template>

<script setup lang="ts">
</script>

<style scoped lang="scss">
.alarm-sidebar {
  width: $sidebar-width;
  background-color: $bg-card;
  border-right: 1px solid $border-color;
  display: flex;
  align-items: center;
  justify-content: center;
  color: $text-muted;
  flex-shrink: 0;
}
</style>
