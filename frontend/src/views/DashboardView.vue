<template>
  <div class="dashboard-view" v-loading="store.loading">
    <!-- 未选导航节点：显示首页数据总览 -->
    <template v-if="!store.currentNavNode">
      <KpiCardsRow />
      <BuildingPlaceholder />
      <TrendChartsGrid />
    </template>
    <!-- 选了导航节点：显示该节点下设备列表 -->
    <template v-else>
      <DeviceListView
        :devices="store.currentDevices"
        :title="currentTitle"
        :loading="store.devicesLoading"
        @view-monitor="goMonitor"
      />
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import KpiCardsRow from '@/components/KpiCardsRow.vue'
import BuildingPlaceholder from '@/components/BuildingPlaceholder.vue'
import TrendChartsGrid from '@/components/TrendChartsGrid.vue'
import DeviceListView from '@/components/DeviceListView.vue'
import { useDashboardStore } from '@/stores/dashboard'
import type { DeviceDeployItem } from '@/api/types'

const store = useDashboardStore()
const router = useRouter()

const currentTitle = computed(() => {
  if (!store.currentNavNode) return '设备列表'
  const node = store.currentNavNode
  const isLeafGroup = !node.children || node.children.length === 0
  if (!isLeafGroup) {
    return `${node.nodeName} - 全部设备`
  }
  return node.nodeName
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
</style>
