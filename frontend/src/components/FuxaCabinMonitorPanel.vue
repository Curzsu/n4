<template>
  <section class="cabin-monitor-card">
    <header class="monitor-card-header">
      <div class="header-title">
        <el-icon><Monitor /></el-icon>
        <h3>{{ cabinName }} Graphical Monitor</h3>
        <span :class="['connection-state', { active: monitoring && session?.enabled }]">
          {{ monitoring && session?.enabled ? 'MQTT connected' : 'MQTT stopped' }}
        </span>
      </div>
      <el-tooltip :content="monitoring ? 'Stop this monitor session' : 'Start this monitor session'" placement="top">
        <el-button
          circle
          :icon="monitoring ? SwitchButton : RefreshRight"
          :class="['connection-btn', { active: monitoring }]"
          :loading="changingState"
          :disabled="loading"
          aria-label="Toggle cabin MQTT client"
          @click="toggleCabin"
        />
      </el-tooltip>
    </header>

    <div class="frame-shell" v-loading="loading">
      <div v-if="errorMessage" class="monitor-error">
        {{ errorMessage }}
      </div>
      <iframe
        v-else-if="monitoring && session?.iframeUrl"
        :key="session.iframeUrl"
        :src="session.iframeUrl"
        title="FUXA cabin graphical monitor"
        @load="frameLoaded = true"
      />
      <div v-else class="monitor-idle">The cabin MQTT client is stopped for this monitor session.</div>
    </div>
  </section>
</template>

<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { Monitor, RefreshRight, SwitchButton } from '@element-plus/icons-vue'
import { deviceApi } from '@/api/device'
import type { FuxaCabinSession } from '@/api/types'

const props = defineProps<{
  cabinName: string
}>()

const loading = ref(true)
const changingState = ref(false)
const frameLoaded = ref(false)
const session = ref<FuxaCabinSession | null>(null)
const errorMessage = ref('')
const monitoring = ref(false)
const sessionId = typeof crypto !== 'undefined' && crypto.randomUUID
  ? crypto.randomUUID()
  : `cabin-${Date.now()}-${Math.random().toString(36).slice(2)}`
let heartbeatTimer: ReturnType<typeof setInterval> | undefined

async function openCabin() {
  loading.value = true
  frameLoaded.value = false
  errorMessage.value = ''
  try {
    session.value = await deviceApi.openFuxaCabinSession(props.cabinName, sessionId)
    monitoring.value = true
    startHeartbeat()
  } catch (error) {
    console.error(`Failed to open FUXA cabin ${props.cabinName}`, error)
    errorMessage.value = error instanceof Error
      ? error.message
      : 'Unable to start the cabin MQTT client. Check the FUXA cabin-client mapping.'
  } finally {
    loading.value = false
  }
}

async function toggleCabin() {
  changingState.value = true
  try {
    if (monitoring.value) {
      session.value = await deviceApi.releaseFuxaCabinSession(props.cabinName, sessionId)
      monitoring.value = false
      stopHeartbeat()
    } else {
      session.value = await deviceApi.openFuxaCabinSession(props.cabinName, sessionId)
      monitoring.value = true
      startHeartbeat()
    }
  } catch (error) {
    console.error(`Failed to toggle FUXA cabin ${props.cabinName}`, error)
  } finally {
    changingState.value = false
  }
}

function startHeartbeat() {
  stopHeartbeat()
  heartbeatTimer = setInterval(async () => {
    if (!monitoring.value) return
    try {
      session.value = await deviceApi.heartbeatFuxaCabinSession(props.cabinName, sessionId)
    } catch (error) {
      console.warn(`FUXA cabin session heartbeat failed for ${props.cabinName}`, error)
    }
  }, (session.value?.heartbeatIntervalSeconds ?? 20) * 1000)
}

function stopHeartbeat() {
  if (heartbeatTimer) clearInterval(heartbeatTimer)
  heartbeatTimer = undefined
}

onMounted(openCabin)

onBeforeUnmount(() => {
  stopHeartbeat()
  if (monitoring.value) {
    void deviceApi.releaseFuxaCabinSession(props.cabinName, sessionId)
  }
})
</script>

<style scoped lang="scss">
.cabin-monitor-card {
  flex: 0 0 auto;
  min-height: 660px;
  overflow: hidden;
  background: $bg-card;
  border: 1px solid $border-card;
  border-radius: 8px;
}

.monitor-card-header {
  height: 48px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 16px;
  border-bottom: 1px solid $border-card;
}

.header-title {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 8px;
  color: $text-primary;

  .el-icon { color: $accent-green; }
  h3 { margin: 0; font-size: 14px; font-weight: 600; }
}

.connection-state {
  color: $text-muted;
  font-size: 12px;

  &.active { color: $accent-green; }
}

.connection-btn {
  color: $text-secondary;
  background: transparent;
  border-color: $border-card;

  &.active:hover:not(:disabled) {
    color: $accent-red;
    border-color: $accent-red;
  }

  &:not(.active):hover:not(:disabled) {
    color: $accent-green;
    border-color: $accent-green;
  }
}

.frame-shell {
  position: relative;
  min-height: 610px;
  height: 610px;
  overflow: hidden;
  background: #050505;
}

.frame-shell :deep(.el-loading-mask) { background: #050505; }
.frame-shell :deep(.el-loading-spinner .path) { stroke: $accent-green; }

iframe {
  display: block;
  width: 100%;
  height: 100%;
  border: 0;
  background: #050505;
}

.monitor-error {
  display: grid;
  width: 100%;
  height: 100%;
  place-items: center;
  padding: 24px;
  box-sizing: border-box;
  color: $text-secondary;
  text-align: center;
}

.monitor-idle {
  display: grid;
  width: 100%;
  height: 100%;
  place-items: center;
  color: $text-muted;
  font-size: 13px;
}
</style>
