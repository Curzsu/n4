<template>
  <div class="device-monitor-view">
    <header class="monitor-header">
      <el-button :icon="ArrowLeft" link @click="$router.back()">返回</el-button>
      <h2>{{ deviceName }}</h2>
    </header>

    <FuxaMonitorPanel :device="fuxaDeviceProxy" />

    <section class="alarm-section">
      <h3>告警列表</h3>
      <p class="placeholder" v-if="!alarms.length">暂无告警</p>
      <el-table v-else :data="alarms" size="small">
        <el-table-column prop="time" label="时间" width="160" />
        <el-table-column prop="severity" label="级别" width="80" />
        <el-table-column prop="message" label="告警描述" />
      </el-table>
    </section>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ArrowLeft } from '@element-plus/icons-vue'
import FuxaMonitorPanel from '@/components/FuxaMonitorPanel.vue'
import type { DeviceTreeNode } from '@/api/types'

const route = useRoute()
const deviceDeployId = computed(() => Number(route.params.deviceDeployId))
const deviceName = ref(`设备 ${deviceDeployId.value}`)
const alarms = ref<any[]>([])

// FuxaMonitorPanel 期望 device: DeviceTreeNode，构造代理对象触发其加载绑定逻辑
// FuxaMonitorPanel 内部调用 deviceApi.getFuxaBinding(device.id)，绑定查不到会降级到默认视图
const fuxaDeviceProxy = computed<DeviceTreeNode | null>(() => ({
  id: deviceDeployId.value,
  label: deviceName.value,
  type: 'device',
  status: 1,
  children: [],
}))

onMounted(() => {
  // 设备名暂用 id 占位；后续可调设备详情接口补全。告警列表本期留空（占位）。
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
