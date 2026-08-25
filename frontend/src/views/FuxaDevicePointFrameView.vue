<template>
  <main class="device-points-frame">
    <FuxaDevicePointList :panel="panel" />
  </main>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import FuxaDevicePointList, { type FuxaDevicePanelContext, type FuxaLinkedPoint } from '@/components/FuxaDevicePointList.vue'

const route = useRoute()

function readText(name: string) {
  const value = route.query[name]
  return typeof value === 'string' ? value : ''
}

function readFields(): FuxaLinkedPoint[] {
  const raw = readText('fields')
  if (!raw) return []
  try {
    const value = JSON.parse(raw)
    return Array.isArray(value)
      ? value
        .filter(field => field && typeof field.field === 'string')
        .map(field => ({ field: field.field, tagId: typeof field.tagId === 'string' ? field.tagId : '' }))
      : []
  } catch {
    return []
  }
}

const panel = computed<FuxaDevicePanelContext>(() => ({
  panelKey: readText('panelKey'),
  templateType: readText('templateType'),
  deviceRole: readText('deviceRole'),
  mqttDeviceName: readText('mqttDeviceName'),
  fields: readFields(),
}))
</script>

<style scoped lang="scss">
.device-points-frame {
  width: 100%;
  min-height: 100%;
  background: #090d12;
}
</style>
