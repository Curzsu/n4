<template>
  <div class="main-layout">
    <TopNavbar />
    <div class="layout-body">
      <LeftSidebar v-if="showSidebar" />
      <div class="layout-content">
        <slot />
      </div>
      <div :class="['layout-right-panel', { collapsed: isRightCollapsed }]">
        <div class="right-panel-collapsed-icons" v-if="isRightCollapsed" @click="toggleRightCollapse">
          <el-icon class="collapsed-icon"><Bell /></el-icon>
          <el-icon class="collapsed-icon"><Setting /></el-icon>
        </div>
        <div class="right-panel-body" v-else>
          <div class="right-panel-header">
            <span class="panel-title">系统状态与告警</span>
            <el-icon class="close-panel-btn" @click="closeRightPanel"><Close /></el-icon>
          </div>
          <AlarmSummary />
          <SystemStatus />
        </div>

      </div>
    </div>
    <BottomToolbar />
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { Bell, Setting, Close } from '@element-plus/icons-vue'
import TopNavbar from '@/components/TopNavbar.vue'
import LeftSidebar from '@/components/LeftSidebar.vue'
import BottomToolbar from '@/components/BottomToolbar.vue'
import AlarmSummary from '@/components/AlarmSummary.vue'
import SystemStatus from '@/components/SystemStatus.vue'
import { useRoute } from 'vue-router'
import { useDashboardStore } from '@/stores/dashboard'

const route = useRoute()
const store = useDashboardStore()

const showSidebar = computed(() => {
  return route.path !== '/'
})

const isRightCollapsed = computed({
  get: () => store.isRightCollapsed,
  set: (val) => { store.isRightCollapsed = val }
})

function toggleRightCollapse() {
  isRightCollapsed.value = !isRightCollapsed.value
}

function closeRightPanel() {
  isRightCollapsed.value = true
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
    width: 0;
    border-left: none;
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

.right-panel-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-bottom: 12px;
  border-bottom: 1px solid $border-color;
  margin-bottom: 4px;
}

.panel-title {
  font-size: 13px;
  font-weight: 600;
  color: $text-primary;
}

.close-panel-btn {
  font-size: 16px;
  color: $text-secondary;
  cursor: pointer;
  transition: color 0.2s;
  
  &:hover {
    color: $accent-red;
  }
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
