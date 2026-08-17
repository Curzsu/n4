<template>
  <section class="cabin-monitor-card">
    <header class="monitor-card-header">
      <div class="header-title">
        <el-icon><Monitor /></el-icon>
        <h3>{{ cabinName }} Graphical Monitor</h3>
        <span :class="['connection-state', { active: monitoring && session?.enabled }]">
          {{ monitoring && session?.enabled ? 'MQTT connected' : 'MQTT stopped' }}
          <template v-if="session"> · {{ session.activeSessionCount }} viewer(s)</template>
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

    <div
      ref="frameShell"
      class="frame-shell"
      :style="{ height: `${frameHeight}px` }"
      v-loading="loading"
    >
      <div v-if="errorMessage" class="monitor-error">
        {{ errorMessage }}
      </div>
      <iframe
        v-else-if="monitoring && session?.iframeUrl"
        :key="session.iframeUrl"
        :src="session.iframeUrl"
        :style="frameStyle"
        title="FUXA cabin graphical monitor"
        @load="frameLoaded = true"
      />
      <div v-else class="monitor-idle">The cabin MQTT client is stopped for this monitor session.</div>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
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
const frameShell = ref<HTMLElement | null>(null)
const frameScale = ref(1)
const frameOffsetX = ref(0)
const sessionId = typeof crypto !== 'undefined' && crypto.randomUUID
  ? crypto.randomUUID()
  : `cabin-${Date.now()}-${Math.random().toString(36).slice(2)}`
let releaseRequested = false
let resizeObserver: ResizeObserver | null = null

const viewWidth = computed(() => Math.max(1, session.value?.viewWidth || 1780))
const viewHeight = computed(() => Math.max(1, session.value?.viewHeight || 980))
const frameHeight = computed(() => monitoring.value && session.value?.iframeUrl
  ? Math.ceil(viewHeight.value * frameScale.value)
  : 320)
const frameStyle = computed(() => ({
  width: `${viewWidth.value}px`,
  height: `${viewHeight.value}px`,
  left: `${frameOffsetX.value}px`,
  transform: `scale(${frameScale.value})`,
}))

function updateFrameScale() {
  const availableWidth = frameShell.value?.clientWidth || viewWidth.value
  const scale = Math.min(1, availableWidth / viewWidth.value)
  frameScale.value = Math.max(0.1, scale)
  frameOffsetX.value = Math.max(0, (availableWidth - viewWidth.value * frameScale.value) / 2)
}

async function openCabin() {
  loading.value = true
  frameLoaded.value = false
  errorMessage.value = ''
  try {
    session.value = await deviceApi.openFuxaCabinSession(props.cabinName, sessionId)
    monitoring.value = true
    releaseRequested = false
    await nextTick()
    updateFrameScale()
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
      await releaseCabin()
    } else {
      session.value = await deviceApi.openFuxaCabinSession(props.cabinName, sessionId)
      monitoring.value = true
      releaseRequested = false
      await nextTick()
      updateFrameScale()
    }
  } catch (error) {
    console.error(`Failed to toggle FUXA cabin ${props.cabinName}`, error)
  } finally {
    changingState.value = false
  }
}

async function releaseCabin() {
  if (!monitoring.value || releaseRequested) return
  releaseRequested = true
  try {
    session.value = await deviceApi.releaseFuxaCabinSession(props.cabinName, sessionId)
    monitoring.value = false
  } finally {
    releaseRequested = false
  }
}

function releaseOnPageExit() {
  if (!monitoring.value || releaseRequested) return
  releaseRequested = true
  monitoring.value = false
  const cabin = encodeURIComponent(props.cabinName)
  const viewer = encodeURIComponent(sessionId)
  // Deliberately a bare fetch: keepalive survives page unload (axios does
  // not support keepalive); nothing can be done about failures here anyway.
  void fetch(`/api/fuxa/cabins/${cabin}/sessions/${viewer}`, {
    method: 'DELETE',
    credentials: 'same-origin',
    keepalive: true,
  }).catch(() => { /* page is unloading; nothing to recover */ })
}

onMounted(() => {
  window.addEventListener('pagehide', releaseOnPageExit)
  resizeObserver = new ResizeObserver(updateFrameScale)
  if (frameShell.value) resizeObserver.observe(frameShell.value)
  updateFrameScale()
  void openCabin()
})

onBeforeUnmount(() => {
  window.removeEventListener('pagehide', releaseOnPageExit)
  resizeObserver?.disconnect()
  releaseOnPageExit()
})
</script>

<style scoped lang="scss">
.cabin-monitor-card {
  flex: 0 0 auto;
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
  overflow: hidden;
  background: #050505;
  transition: height 160ms ease;
}

.frame-shell :deep(.el-loading-mask) { background: #050505; }
.frame-shell :deep(.el-loading-spinner .path) { stroke: $accent-green; }

iframe {
  position: absolute;
  top: 0;
  display: block;
  border: 0;
  background: #050505;
  transform-origin: top left;
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
