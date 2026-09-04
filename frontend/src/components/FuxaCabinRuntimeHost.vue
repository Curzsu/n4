<template>
  <FuxaCabinMonitorPanel
    v-if="mountedCabinName"
    v-show="isConfiguredCabin"
    :cabin-name="mountedCabinName"
    :active="isConfiguredCabin"
  />
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import FuxaCabinMonitorPanel from '@/components/FuxaCabinMonitorPanel.vue'
import { deviceApi } from '@/api/device'
import { useDashboardStore } from '@/stores/dashboard'

const route = useRoute()
const store = useDashboardStore()
const configuredCabins = ref<string[]>([])
const mountedCabinName = ref('')

// The FUXA SPA is initialized once for the signed-in application. It is only
// shown on the dashboard when the selected node is a configured cabin.
const isConfiguredCabin = computed(() => {
  if (route.path !== '/') return false
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

onMounted(loadConfiguredCabins)
</script>
