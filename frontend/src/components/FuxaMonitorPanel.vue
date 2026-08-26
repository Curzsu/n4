<template>
  <section class="graphical-monitor-card">
    <header class="monitor-card-header">
      <div class="header-title">
        <el-icon><Monitor /></el-icon>
        <h3>Graphical Monitor</h3>
      </div>

      <el-tooltip content="View Fullscreen" placement="top">
        <el-button
          circle
          :icon="FullScreen"
          class="fullscreen-btn"
          aria-label="View Graphical Monitor Fullscreen"
          @click="openFullscreen"
        />
      </el-tooltip>
    </header>

    <div
      ref="frameShell"
      class="frame-shell"
      :style="{ height: `${displayHeight}px` }"
      v-loading="loading"
    >
      <div v-if="fuxaUrl" class="frame-stage" :style="stageStyle">
        <iframe
          :key="fuxaUrl"
          :src="fuxaUrl"
          :class="{ 'is-ready': frameReady }"
          title="FUXA Graphical Monitor"
          allow="fullscreen"
          @load="handleFrameLoad"
        />
      </div>
      <p v-else class="binding-empty">
        No FUXA binding is configured for this device.
      </p>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { FullScreen, Monitor } from '@element-plus/icons-vue'
import { deviceApi } from '@/api/device'
import type { DeviceTreeNode, FuxaBinding } from '@/api/types'

const props = defineProps<{
  device: DeviceTreeNode | null
}>()

const loading = ref(true)
const frameReady = ref(false)
const frameShell = ref<HTMLElement | null>(null)
const frameScale = ref(1)
const stageLeft = ref(0)
const stageTop = ref(-100)

const FUXA_CANVAS_WIDTH = 1220
const FUXA_CANVAS_HEIGHT = 680
const TOP_CROP = 100
const DATA_SETTLE_DELAY = 900
let resizeObserver: ResizeObserver | null = null
let frameReadyTimer: ReturnType<typeof setTimeout> | null = null

const displayHeight = computed(() => {
  return Math.round((FUXA_CANVAS_HEIGHT - TOP_CROP) * frameScale.value)
})

const stageStyle = computed(() => ({
  left: `${stageLeft.value}px`,
  top: `${stageTop.value}px`,
  transform: `scale(${frameScale.value})`,
}))

const fuxaUrl = ref<string | null>(null)

function buildBindingFuxaUrl(binding: FuxaBinding) {
  const url = new URL(binding.iframeUrl, window.location.origin)
  url.searchParams.set('viewName', binding.viewName)
  url.searchParams.set('sourceDeviceId', binding.fuxaDeviceId)
  return url.toString()
}

function handleFrameLoad() {
  if (frameReadyTimer) clearTimeout(frameReadyTimer)
  frameReadyTimer = setTimeout(() => {
    frameReady.value = true
    loading.value = false
    frameReadyTimer = null
  }, DATA_SETTLE_DELAY)
}

async function loadDeviceBinding(device: DeviceTreeNode | null) {
  if (!device || device.type !== 'device') {
    fuxaUrl.value = null
    loading.value = false
    return
  }

  loading.value = true
  frameReady.value = false
  if (frameReadyTimer) {
    clearTimeout(frameReadyTimer)
    frameReadyTimer = null
  }
  try {
    const binding = await deviceApi.getFuxaBinding(Number(device.id))
    fuxaUrl.value = buildBindingFuxaUrl(binding)
  } catch (error) {
    console.warn(`FUXA binding is not ready for device ${device.id}`, error)
    fuxaUrl.value = null
    loading.value = false
  }
}

async function openFullscreen() {
  if (!frameShell.value) return
  await frameShell.value.requestFullscreen()
}

function updateScale() {
  if (!frameShell.value) return
  const shellWidth = frameShell.value.clientWidth

  if (document.fullscreenElement === frameShell.value) {
    const shellHeight = frameShell.value.clientHeight
    const visibleCanvasHeight = FUXA_CANVAS_HEIGHT - TOP_CROP
    const scale = Math.min(
      shellWidth / FUXA_CANVAS_WIDTH,
      shellHeight / visibleCanvasHeight,
    )

    frameScale.value = scale
    stageLeft.value = (shellWidth - FUXA_CANVAS_WIDTH * scale) / 2
    stageTop.value = (shellHeight - visibleCanvasHeight * scale) / 2 - TOP_CROP * scale
    return
  }

  frameScale.value = Math.min(1, shellWidth / FUXA_CANVAS_WIDTH)
  stageLeft.value = 0
  stageTop.value = -TOP_CROP * frameScale.value
}

onMounted(() => {
  resizeObserver = new ResizeObserver(updateScale)
  if (frameShell.value) resizeObserver.observe(frameShell.value)
  document.addEventListener('fullscreenchange', updateScale)
  updateScale()
})

watch(() => props.device?.id, () => loadDeviceBinding(props.device), { immediate: true })

onBeforeUnmount(() => {
  if (frameReadyTimer) clearTimeout(frameReadyTimer)
  resizeObserver?.disconnect()
  document.removeEventListener('fullscreenchange', updateScale)
})
</script>

<style scoped lang="scss">
.graphical-monitor-card {
  flex-shrink: 0;
  overflow: hidden;
  background-color: $bg-card;
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
  align-items: center;
  gap: 8px;
  color: $text-primary;

  .el-icon {
    color: $accent-green;
    font-size: 16px;
  }

  h3 {
    margin: 0;
    font-size: 14px;
    font-weight: 600;
  }
}

.fullscreen-btn {
  width: 32px;
  height: 32px;
  border-color: $border-card;
  background-color: transparent;
  color: $text-secondary;
}

.fullscreen-btn:hover,
.fullscreen-btn:focus {
  border-color: $accent-green;
  background-color: rgba($accent-green, 0.08);
  color: $accent-green;
}

.frame-shell {
  position: relative;
  width: 100%;
  overflow: hidden;
  background: #050505;
  transition: height 180ms ease;
}

.frame-shell :deep(.el-loading-mask) {
  background-color: #050505;
}

.frame-shell :deep(.el-loading-spinner .path) {
  stroke: $accent-green;
}

.binding-empty {
  display: grid;
  place-items: center;
  height: 100%;
  margin: 0;
  color: $text-secondary;
  font-size: 13px;
}

.frame-stage {
  position: absolute;
  width: 1220px;
  height: 680px;
  transform-origin: top left;
}

.frame-stage iframe {
  width: 1220px;
  height: 680px;
  display: block;
  border: 0;
  background: #050505;
  opacity: 0;
  transition: opacity 160ms ease;
}

.frame-stage iframe.is-ready {
  opacity: 1;
}

.frame-shell:fullscreen {
  width: 100vw;
  height: 100vh !important;
}
</style>
