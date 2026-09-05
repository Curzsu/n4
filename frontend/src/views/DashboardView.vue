<template>
  <div
    class="dashboard-view"
    :class="{ 'dashboard-view--overview': !store.currentNavNode }"
    v-loading="store.loading"
  >
    <template v-if="!store.currentNavNode">
      <KpiCardsRow />
      <BuildingPlaceholder />
      <TrendChartsGrid />
    </template>
    <!-- Selected navigation node: render device list under the node -->
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
import { computed, onMounted } from 'vue'
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
  if (!store.currentNavNode) return 'Device List'
  const node = store.currentNavNode
  const isLeafGroup = !node.children || node.children.length === 0
  if (!isLeafGroup) {
    return `${node.nodeName} - All Devices`
  }
  return node.nodeName
})

function goMonitor(device: DeviceDeployItem) {
  router.push(`/dashboard-nav/device/${device.deviceDeployId}`)
}

onMounted(() => {
  store.fetchAllDashboardData()
})
</script>

<style lang="scss" scoped>
.dashboard-view {
  display: flex;
  flex-direction: column;
  gap: 8px;
  min-height: 100%;
}

// Overview mode fits the whole dashboard into the viewport without scrolling:
// children share the available height via flex instead of using fixed sizes.
.dashboard-view--overview {
  height: 100%;
}
</style>
