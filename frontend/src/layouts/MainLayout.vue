<template>
  <div class="main-layout">
    <TopNavbar />
    <div class="layout-body">
      <LeftSidebar />
      <div class="layout-content">
        <slot />
      </div>
      <div :class="['layout-right-panel', { collapsed: isRightCollapsed }]">
        <div class="right-panel-collapsed-icons" v-if="isRightCollapsed" @click="toggleRightCollapse">
          <el-icon class="collapsed-icon"><Bell /></el-icon>
          <el-icon class="collapsed-icon"><Setting /></el-icon>
        </div>
        <div class="right-panel-body" v-else>
          <AlarmSummary />
          <SystemStatus />
        </div>
        <div class="right-panel-footer">
          <span class="collapse-btn" @click="toggleRightCollapse">
            {{ isRightCollapsed ? '<<' : 'Collapse >>' }}
          </span>
        </div>
      </div>
    </div>
    <BottomToolbar />
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { Bell, Setting } from '@element-plus/icons-vue'
import TopNavbar from '@/components/TopNavbar.vue'
import LeftSidebar from '@/components/LeftSidebar.vue'
import BottomToolbar from '@/components/BottomToolbar.vue'
import AlarmSummary from '@/components/AlarmSummary.vue'
import SystemStatus from '@/components/SystemStatus.vue'

const isRightCollapsed = ref(false)
function toggleRightCollapse() {
  isRightCollapsed.value = !isRightCollapsed.value
}
</script>

<style scoped lang="scss">
.main-layout {
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
  background-color: $bg-dark;
}

.layout-body {
  flex: 1;
  display: flex;
  overflow: hidden;
}

.layout-content {
  flex: 1;
  overflow-y: auto;
  padding: 16px;
}

.layout-right-panel {
  width: $right-panel-width;
  background-color: $bg-card;
  border-left: 1px solid $border-color;
  display: flex;
  flex-direction: column;
  transition: width 0.3s;
  flex-shrink: 0;
  overflow: hidden;

  &.collapsed {
    width: $sidebar-collapsed-width;
  }
}

.right-panel-collapsed-icons {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 20px;
  padding-top: 24px;
  cursor: pointer;
  
  .collapsed-icon {
    font-size: 20px;
    color: $text-secondary;
    transition: color 0.2s;
    
    &:hover {
      color: $text-primary;
    }
  }
}

.right-panel-body {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 16px 12px;
  overflow-y: auto;
}

.right-panel-footer {
  padding: 12px;
  border-top: 1px solid $border-color;
  text-align: center;
  background-color: $bg-card;
}

.collapse-btn {
  color: $text-secondary;
  font-size: 12px;
  cursor: pointer;
  white-space: nowrap;
  &:hover { color: $text-primary; }
}
</style>
