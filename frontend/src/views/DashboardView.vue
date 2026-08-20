<template>
  <div
    class="dashboard-view"
    :class="{ 'dashboard-view--overview': !store.currentNavNode }"
    v-loading="store.loading"
  >
    <!-- Unselected navigation node: render dashboard overview -->
    <template v-if="!store.currentNavNode">
      <KpiCardsRow />
      <BuildingPlaceholder />
      <TrendChartsGrid />
    </template>
    <!-- Selected navigation node: render device list under the node -->
    <template v-else>
      <FuxaCabinMonitorPanel
        v-if="isConfiguredCabin"
        :key="store.currentNavNode.nodeName"
        :cabin-name="store.currentNavNode.nodeName"
      />
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
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import KpiCardsRow from '@/components/KpiCardsRow.vue'
import BuildingPlaceholder from '@/components/BuildingPlaceholder.vue'
import TrendChartsGrid from '@/components/TrendChartsGrid.vue'
import DeviceListView from '@/components/DeviceListView.vue'
import FuxaCabinMonitorPanel from '@/components/FuxaCabinMonitorPanel.vue'
import { deviceApi } from '@/api/device'
import { useDashboardStore } from '@/stores/dashboard'
import type { DeviceDeployItem } from '@/api/types'

const store = useDashboardStore()
const router = useRouter()
const configuredCabins = ref<string[]>([])

const currentTitle = computed(() => {
  if (!store.currentNavNode) return 'Device List'
  const node = store.currentNavNode
  const isLeafGroup = !node.children || node.children.length === 0
  if (!isLeafGroup) {
    return `${node.nodeName} - All Devices`
  }
  return node.nodeName
})

const isConfiguredCabin = computed(() => {
  const nodeName = store.currentNavNode?.nodeName.trim().toLowerCase()
  return !!nodeName && configuredCabins.value.some(
    cabinName => cabinName.trim().toLowerCase() === nodeName,
  )
})

async function loadConfiguredCabins() {
  try {
    configuredCabins.value = await deviceApi.getConfiguredFuxaCabins()
  } catch (error) {
    console.warn('Failed to load FUXA cabin-client mappings', error)
    configuredCabins.value = []
  }
}

function goMonitor(device: DeviceDeployItem) {
  router.push(`/dashboard-nav/device/${device.deviceDeployId}`)
}

onMounted(() => {
  store.fetchAllDashboardData()
  loadConfiguredCabins()
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
