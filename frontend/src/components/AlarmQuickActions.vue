<template>
  <div class="quick-actions-panel">
    <div class="btn" @click="noop">
      <el-icon><Bell /></el-icon>
      <span>Silence All</span>
    </div>
    <div class="btn" @click="noop">
      <el-icon><Setting /></el-icon>
      <span>Alarm Configuration</span>
    </div>
    <div class="btn" @click="noop">
      <el-icon><CircleCheck /></el-icon>
      <span>Acknowledge</span>
    </div>
    <div class="btn" @click="noop">
      <el-icon><Download /></el-icon>
      <span>Alarm Log Export</span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { Bell, Setting, CircleCheck, Download } from '@element-plus/icons-vue'

function noop() {
  // Placeholder — buttons are display-only for now
}
</script>

<style scoped lang="scss">
.quick-actions-panel {
  background-color: $bg-card;
  border: 1px solid $border-card;
  border-radius: 8px;
  padding: 8px;
  box-sizing: border-box;
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 4px;
}

.btn {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  min-width: 0;
  min-height: 34px;
  padding: 4px 4px;
  background-color: $bg-card-hover;
  border: 1px solid $border-card;
  border-radius: 4px;
  color: $text-secondary;
  font-size: 10px;
  cursor: pointer;
  overflow: hidden;
  box-sizing: border-box;

  .el-icon {
    font-size: 12px;
    flex-shrink: 0;
  }

  span {
    white-space: normal;
    word-break: break-word;
    text-align: center;
    line-height: 1.2;
  }

  &:hover {
    background-color: $bg-dark;
    color: $accent-green;
    border-color: $accent-green;
  }
}
</style>
