<template>
  <div class="device-monitor-view">
    <header class="monitor-header">
      <el-button size="small" class="btn-secondary" :icon="ArrowLeft" @click="$router.back()">Back</el-button>
      <h2>{{ deviceName }}</h2>
    </header>

    <FuxaMonitorPanel :device="fuxaDeviceProxy" />

    <section class="alarm-section">
      <h3>Alarm List</h3>
      <AlarmTable v-if="deviceName" :device-name="deviceName" />
      <p v-else class="placeholder">Loading device information...</p>
    </section>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ArrowLeft } from '@element-plus/icons-vue'
import FuxaMonitorPanel from '@/components/FuxaMonitorPanel.vue'
import AlarmTable from '@/components/AlarmTable.vue'
import { dashboardNavApi } from '@/api/dashboardNav'
import type { DeviceTreeNode } from '@/api/types'

const route = useRoute()
const deviceDeployId = computed(() => Number(route.params.deviceDeployId))
const deviceName = ref('')

const fuxaDeviceProxy = computed<DeviceTreeNode | null>(() => ({
  id: deviceDeployId.value,
  label: deviceName.value || `Device ${deviceDeployId.value}`,
  type: 'device',
  status: 1,
  children: [],
}))

onMounted(async () => {
  try {
    const device = await dashboardNavApi.getDeviceById(deviceDeployId.value)
    if (device) {
      deviceName.value = device.deviceDeployName
    } else {
      deviceName.value = `Device ${deviceDeployId.value} (Not found)`
    }
  } catch (e) {
    console.error('Failed to load device info', e)
    deviceName.value = `Device ${deviceDeployId.value}`
  }
})
</script>

<style scoped lang="scss">
.device-monitor-view {
  height: 100%;
  overflow-y: auto;
  padding: 12px;
}
.monitor-header {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 12px;
  h2 {
    margin: 0;
    font-size: 14px;
    color: $text-primary;
  }
}
.alarm-section {
  margin-top: 12px;
  h3 {
    font-size: 11px;
    font-weight: 600;
    color: $text-primary;
    margin: 0 0 6px;
  }
  .placeholder {
    color: $text-secondary;
    font-size: 10px;
  }
}
</style>
