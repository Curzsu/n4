<template>
  <section class="graphical-monitor-card">
    <header class="monitor-card-header">
      <div class="header-title">
        <el-icon><Monitor /></el-icon>
        <h3>图形监控</h3>
      </div>

      <el-tooltip content="全屏查看" placement="top">
        <el-button
          circle
          :icon="FullScreen"
          class="fullscreen-btn"
          aria-label="全屏查看图形监控"
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
      <div class="frame-stage" :style="stageStyle">
        <iframe
          :key="fuxaUrl"
          :src="fuxaUrl"
          :class="{ 'is-ready': frameReady }"
          title="FUXA 图形监控"
          allow="fullscreen"
          @load="handleFrameLoad"
        />
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { FullScreen, Monitor } from '@element-plus/icons-vue'
import { deviceApi } from '@/api/device'
import type { DeviceTreeNode } from '@/api/types'

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

function buildDefaultFuxaUrl() {
  const configured = import.meta.env.VITE_FUXA_URL
    || 'http://127.0.0.1:1881/home?viewName=H1_EMSB_A4_03_EM'
  const url = new URL(configured, window.location.origin)
  if (!url.searchParams.has('deviceName')) {
    url.searchParams.set('deviceName', 'H1_EMSB_A4_03_EM')
  }
  return url.toString()
}

const defaultFuxaUrl = buildDefaultFuxaUrl()
const fuxaUrl = ref(defaultFuxaUrl)

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
    fuxaUrl.value = defaultFuxaUrl
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
    fuxaUrl.value = binding.iframeUrl
  } catch (error) {
    // Node-RED may not have discovered this device yet; keep the template visible.
    console.warn(`FUXA binding is not ready for device ${device.id}`, error)
    const willReloadFrame = fuxaUrl.value !== defaultFuxaUrl
    fuxaUrl.value = defaultFuxaUrl
    if (!willReloadFrame) {
      frameReady.value = true
      loading.value = false
    }
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
