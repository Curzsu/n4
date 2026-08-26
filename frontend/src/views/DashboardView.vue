<template>
  <div class="dashboard-view" v-loading="store.loading">
    <!-- 未选导航节点：显示首页数据总览 -->
    <FuxaCabinMonitorPanel
      v-if="mountedCabinName"
      v-show="isConfiguredCabin"
      :cabin-name="mountedCabinName"
      :active="isConfiguredCabin"
    />
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
import { computed, onMounted, ref, watch } from 'vue'
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
const mountedCabinName = ref('')

const currentTitle = computed(() => {
  if (!store.currentNavNode) return '设备列表'
  const node = store.currentNavNode
  const isLeafGroup = !node.children || node.children.length === 0
  if (!isLeafGroup) {
    return `${node.nodeName} - 全部设备`
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
  if (!mountedCabinName.value && configuredCabins.value.length) {
    mountedCabinName.value = configuredCabins.value[0]
  }
}

watch(
  [() => store.currentNavNode?.nodeName, configuredCabins],
  ([nodeName]) => {
    const candidate = nodeName?.trim()
    if (!candidate) return
    if (configuredCabins.value.some(cabin => cabin.trim().toLowerCase() === candidate.toLowerCase())) {
      mountedCabinName.value = candidate
    }
  },
  { immediate: true },
)

function goMonitor(device: DeviceDeployItem) {
  router.push(`/dashboard-nav/device/${device.deviceDeployId}`)
}

onMounted(loadConfiguredCabins)
</script>

<style lang="scss" scoped>
.dashboard-view {
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-height: 100%;
}
</style>
