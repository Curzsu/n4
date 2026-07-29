<template>
  <div class="device-monitor-view">
    <header class="monitor-header">
      <el-button :icon="ArrowLeft" link @click="$router.back()">返回</el-button>
      <h2>{{ deviceName }}</h2>
    </header>

    <FuxaMonitorPanel :device="fuxaDeviceProxy" />

    <section class="alarm-section">
      <h3>告警列表</h3>
      <AlarmTable v-if="deviceName" :device-name="deviceName" />
      <p v-else class="placeholder">加载设备信息中...</p>
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

// FuxaMonitorPanel 期望 device: DeviceTreeNode，构造代理对象触发其加载绑定逻辑
// FuxaMonitorPanel 内部调用 deviceApi.getFuxaBinding(device.id)，绑定查不到会降级到默认视图
const fuxaDeviceProxy = computed<DeviceTreeNode | null>(() => ({
  id: deviceDeployId.value,
  label: deviceName.value || `设备 ${deviceDeployId.value}`,
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
      deviceName.value = `设备 ${deviceDeployId.value}（未找到）`
    }
  } catch (e) {
    console.error('加载设备信息失败', e)
    deviceName.value = `设备 ${deviceDeployId.value}`
  }
})
</script>

<style scoped lang="scss">
.device-monitor-view {
  height: 100%;
  overflow-y: auto;
  padding: 16px;
}
.monitor-header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
  h2 {
    margin: 0;
    font-size: 18px;
    color: $text-primary;
  }
}
.alarm-section {
  margin-top: 16px;
  h3 {
    font-size: 14px;
    color: $text-primary;
    margin: 0 0 8px;
  }
  .placeholder {
    color: $text-secondary;
    font-size: 13px;
  }
}
</style>
