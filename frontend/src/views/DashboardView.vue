<template>
  <div class="dashboard-view">
    <DeviceListView
      :devices="store.currentDevices"
      :title="currentTitle"
      :loading="store.devicesLoading"
      @view-monitor="goMonitor"
    />
    <div v-if="!store.currentNavNode" class="empty-hint">
      <el-icon><InfoFilled /></el-icon>
      <span>请从左侧导航栏选择一个分类查看设备</span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { InfoFilled } from '@element-plus/icons-vue'
import DeviceListView from '@/components/DeviceListView.vue'
import { useDashboardStore } from '@/stores/dashboard'
import type { DeviceDeployItem } from '@/api/types'

const store = useDashboardStore()
const router = useRouter()

const currentTitle = computed(() => {
  if (!store.currentNavNode) return '设备列表'
  if (store.currentNavNode.nodeLevel === 1) {
    return `${store.currentNavNode.nodeName} - 全部设备`
  }
  return store.currentNavNode.nodeName
})

function goMonitor(device: DeviceDeployItem) {
  router.push(`/dashboard-nav/device/${device.deviceDeployId}`)
}
</script>

<style lang="scss" scoped>
.dashboard-view {
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-height: 100%;
}

.empty-hint {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 60px 0;
  color: $text-secondary;
  font-size: 14px;

  .el-icon {
    font-size: 18px;
  }
}
</style>
