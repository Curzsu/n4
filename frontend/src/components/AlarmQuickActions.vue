<template>
  <div class="quick-actions-panel">
    <div class="action-buttons">
      <el-button class="action-btn" @click="noop">
        <el-icon><Bell /></el-icon>
        <span>Silence All</span>
      </el-button>
      <el-button class="action-btn" @click="noop">
        <el-icon><Setting /></el-icon>
        <span>Alarm Configuration</span>
      </el-button>
      <el-button class="action-btn" @click="noop">
        <el-icon><CircleCheck /></el-icon>
        <span>Acknowledge</span>
      </el-button>
      <el-button class="action-btn" @click="noop">
        <el-icon><Download /></el-icon>
        <span>Alarm Log Export</span>
      </el-button>
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
  padding: 12px;
}

.action-buttons {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 8px;
}

.action-btn {
  background-color: $bg-card-hover;
  border: 1px solid $border-card;
  color: $text-secondary;
  font-size: 12px;
  height: 36px;

  &:hover {
    background-color: $bg-dark;
    color: $accent-green;
    border-color: $accent-green;
  }

  .el-icon {
    margin-right: 4px;
    font-size: 14px;
  }
}
</style>
