<template>
  <el-dialog
    v-model="diagramDialogVisible"
    :title="`Device Diagram - ${currentDiagramTarget?.deviceTmplName || fallbackTemplate?.deviceTmplName || fallbackForm?.deviceTmplName || 'Device Template'}`"
    :width="dialogWidth"
    align-center
    append-to-body
    class="dark-dialog diagram-modal-dialog"
    destroy-on-close
  >
    <div class="diagram-dialog-container">
      <div class="diagram-toolbar">
        <div class="toolbar-left">
          <span class="toolbar-label">Current Diagram:</span>
          <el-select
            v-model="selectedDiagramFile"
            placeholder="Select diagram..."
            size="small"
            style="width: 220px;"
            @change="handleDiagramChange"
          >
            <el-option
              v-for="img in availableImages"
              :key="img.value"
              :label="img.label"
              :value="img.value"
            />
          </el-select>
          <el-tag size="small" type="success" effect="dark" class="path-tag">
            /device-images/Device-1/{{ selectedDiagramFile }}
          </el-tag>
        </div>

        <div class="toolbar-right">
          <span class="hint-text">💡 Click image to view fullscreen zoom</span>
        </div>
      </div>

      <div class="diagram-preview-box" v-loading="diagramImageLoading">
        <el-image
          v-if="currentDiagramUrl"
          :key="currentDiagramUrl"
          :src="currentDiagramUrl"
          :preview-src-list="[currentDiagramUrl]"
          :preview-teleported="true"
          fit="contain"
          class="main-diagram-image"
          :style="imageBoxStyle"
          @load="handleImageLoad"
          @error="handleDiagramImageError"
        >
          <template #placeholder>
            <div class="image-state-box">
              <el-icon class="is-loading"><Loading /></el-icon>
              <span>Loading diagram...</span>
            </div>
          </template>
          <template #error>
            <div class="image-state-box error-box">
              <el-icon :size="48" class="error-icon"><PictureRounded /></el-icon>
              <p class="error-title">Diagram image not found</p>
              <p class="error-desc">Please select an available diagram from the dropdown above, or place the corresponding image file in the public/device-images/ directory.</p>
            </div>
          </template>
        </el-image>
      </div>
    </div>

    <template #footer>
      <div class="dialog-footer-flex">
        <div class="footer-meta">
          <span>Template Name: <strong>{{ currentDiagramTarget?.deviceTmplName || fallbackTemplate?.deviceTmplName || fallbackForm?.deviceTmplName || '—' }}</strong></span>
          <span class="meta-divider">|</span>
          <span>Device Type: <strong>{{ currentDiagramTarget?.deviceType || fallbackTemplate?.deviceType || fallbackForm?.deviceType || '—' }}</strong></span>
        </div>
        <div class="footer-actions">
          <el-button class="btn-secondary" size="small" @click="diagramDialogVisible = false">Close</el-button>
          <el-button type="primary" class="btn-primary" size="small" :icon="View" @click="openImageInNewTab">
            Open Original Image in New Tab
          </el-button>
        </div>
      </div>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, type CSSProperties } from 'vue'
import { PictureRounded, View, Loading } from '@element-plus/icons-vue'
import type { DeviceTemplate } from '@/api/types'

defineProps<{
  fallbackTemplate?: DeviceTemplate | null
  fallbackForm?: DeviceTemplate | null
}>()

// Available diagram images in public/device-images/Device-1/
const availableImages = [
  { label: 'DCPmv (DC Power Distribution)', value: 'DCPmv.png', subDir: 'Device-1' },
  { label: 'DEHU (Dehumidifier)', value: 'DEHU.png', subDir: 'Device-1' },
  { label: 'DHG (Heating Dehumidifier)', value: 'DHG.png', subDir: 'Device-1' },
  { label: 'EM33b (Smart Power Meter 33b)', value: 'EM33b.png', subDir: 'Device-1' },
  { label: 'EM33c (Smart Power Meter 33c)', value: 'EM33c.png', subDir: 'Device-1' },
  { label: 'EM770 (Smart Power Meter 770)', value: 'EM770.png', subDir: 'Device-1' },
  { label: 'EM780 (Smart Power Meter 780)', value: 'EM780.png', subDir: 'Device-1' },
  { label: 'GAS (Gas Monitor)', value: 'GAS.png', subDir: 'Device-1' },
  { label: 'IRC (AC IR Remote Controller)', value: 'IRC.png', subDir: 'Device-1' },
  { label: 'NWLD (Water Leak Detector)', value: 'NWLD.png', subDir: 'Device-1' },
  { label: 'ZBmvbus (Busbar Monitoring)', value: 'ZBmvbus.png', subDir: 'Device-1' },
  { label: 'ZBmvin (Incomer Monitoring)', value: 'ZBmvin.png', subDir: 'Device-1' },
  { label: 'ZBmvout (Feeder Monitoring)', value: 'ZBmvout.png', subDir: 'Device-1' },
]

const diagramDialogVisible = ref(false)
const selectedDiagramFile = ref('EM770.png')
const currentDiagramTarget = ref<DeviceTemplate | null>(null)
const diagramImageLoading = ref(false)
const diagramImageError = ref(false)
const dialogWidth = ref('75%')

const renderedWidth = ref<number | null>(null)
const renderedHeight = ref<number | null>(null)

let currentNaturalWidth = 0
let currentNaturalHeight = 0

function findBestMatchingImage(target: DeviceTemplate | null): string {
  if (!target) return availableImages[0].value
  const candidates = [
    target.facePlateId,
    target.deviceType,
    target.model,
    target.deviceTmplName,
  ].filter(Boolean).map(s => String(s).trim().toLowerCase())

  for (const item of availableImages) {
    const rawName = item.value.replace(/\.(png|jpg|jpeg|svg)$/i, '').toLowerCase()
    for (const cand of candidates) {
      if (cand === rawName || cand.includes(rawName) || rawName.includes(cand)) {
        return item.value
      }
    }
  }
  return availableImages[0].value
}

const currentDiagramUrl = computed(() => {
  if (!selectedDiagramFile.value) return ''
  const matched = availableImages.find(i => i.value === selectedDiagramFile.value)
  if (matched?.subDir) {
    return `/device-images/${matched.subDir}/${matched.value}`
  }
  return `/device-images/${selectedDiagramFile.value}`
})

const imageBoxStyle = computed<CSSProperties>(() => {
  if (!renderedWidth.value || !renderedHeight.value) {
    return {
      maxWidth: '100%',
      maxHeight: 'calc(80vh - 130px)',
    }
  }
  return {
    width: `${renderedWidth.value}px`,
    height: `${renderedHeight.value}px`,
    maxWidth: '100%',
    maxHeight: 'calc(80vh - 130px)',
  }
})

function updateDialogSize(naturalWidth: number, naturalHeight: number) {
  if (!naturalWidth || !naturalHeight) return
  currentNaturalWidth = naturalWidth
  currentNaturalHeight = naturalHeight

  const vw = window.innerWidth || document.documentElement.clientWidth || 1920
  const vh = window.innerHeight || document.documentElement.clientHeight || 1080

  const maxDialogW = Math.min(vw * 0.94, 1680)
  const maxImageH = Math.max(200, vh * 0.78 - 140)
  const minDialogW = 680

  const aspect = naturalWidth / naturalHeight
  let targetW = naturalWidth
  let targetH = naturalHeight

  if (targetW > maxDialogW - 32) {
    targetW = maxDialogW - 32
    targetH = targetW / aspect
  }
  if (targetH > maxImageH) {
    targetH = maxImageH
    targetW = targetH * aspect
  }

  renderedWidth.value = Math.round(targetW)
  renderedHeight.value = Math.round(targetH)

  const finalW = Math.round(Math.max(minDialogW, Math.min(maxDialogW, targetW + 32)))
  dialogWidth.value = `${finalW}px`
}

function handleImageLoad(e: Event) {
  diagramImageLoading.value = false
  const target = e.target as HTMLImageElement
  if (target && target.naturalWidth && target.naturalHeight) {
    updateDialogSize(target.naturalWidth, target.naturalHeight)
  }
}

function preloadAndMeasure(url: string) {
  if (!url) return
  diagramImageLoading.value = true
  const img = new Image()
  img.onload = () => {
    updateDialogSize(img.naturalWidth, img.naturalHeight)
    diagramImageLoading.value = false
  }
  img.onerror = () => {
    handleDiagramImageError()
  }
  img.src = url
}

function handleDiagramChange() {
  diagramImageError.value = false
  preloadAndMeasure(currentDiagramUrl.value)
}

function handleDiagramImageError() {
  diagramImageLoading.value = false
  diagramImageError.value = true
}

function openImageInNewTab() {
  if (currentDiagramUrl.value) {
    window.open(currentDiagramUrl.value, '_blank')
  }
}

function handleWindowResize() {
  if (diagramDialogVisible.value && currentNaturalWidth && currentNaturalHeight) {
    updateDialogSize(currentNaturalWidth, currentNaturalHeight)
  }
}

onMounted(() => {
  window.addEventListener('resize', handleWindowResize)
})

onUnmounted(() => {
  window.removeEventListener('resize', handleWindowResize)
})

function open(target: DeviceTemplate | null) {
  currentDiagramTarget.value = target
  selectedDiagramFile.value = findBestMatchingImage(target)
  diagramImageError.value = false
  preloadAndMeasure(currentDiagramUrl.value)
  diagramDialogVisible.value = true
}

defineExpose({ open })
</script>

<style scoped lang="scss">
// ===== Diagram Modal Styles =====
.diagram-modal-dialog {
  max-width: 96vw;
  margin: 0 auto;

  :deep(.el-dialog__body) {
    padding: 10px 14px;
    background-color: #0b1118;
  }
}

.diagram-dialog-container {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.diagram-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 6px 10px;
  background-color: rgba(30, 41, 59, 0.5);
  border: 1px solid #1e293b;
  border-radius: 4px;
  flex-shrink: 0;
  gap: 8px;
  flex-wrap: wrap;

  .toolbar-left {
    display: flex;
    align-items: center;
    gap: 6px;
    flex-wrap: wrap;

    .toolbar-label {
      font-size: 10px;
      color: #94a3b8;
      white-space: nowrap;
    }

    .path-tag {
      font-size: 10px;
      height: 18px;
      line-height: 18px;
      padding: 0 6px;
      border-radius: 4px;
      white-space: nowrap;
    }
  }

  .toolbar-right {
    .hint-text {
      font-size: 10px;
      color: #64748b;
      white-space: nowrap;
    }
  }
}

.diagram-preview-box {
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  background-color: transparent;
  position: relative;
  line-height: 0;

  .main-diagram-image {
    display: block;
    cursor: zoom-in;
    border: 1px solid #1e293b;
    border-radius: 6px;
    background-color: #06090e;
    overflow: hidden;
    margin: 0 auto;
    box-shadow: 0 4px 12px rgba(0, 0, 0, 0.4);

    :deep(.el-image__inner) {
      display: block;
      width: 100%;
      height: 100%;
      object-fit: fill;
    }
  }
}

.image-state-box {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  color: #94a3b8;
  font-size: 11px;
  min-height: 240px;
  width: 100%;
  background-color: #06090e;
  border: 1px solid #1e293b;
  border-radius: 6px;

  &.error-box {
    color: #64748b;

    .error-icon {
      color: #475569;
    }

    .error-title {
      font-size: 13px;
      font-weight: 600;
      color: #e2e8f0;
      margin: 0;
    }

    .error-desc {
      font-size: 10px;
      max-width: 480px;
      text-align: center;
      line-height: 1.5;
      margin: 0;
    }
  }
}

.dialog-footer-flex {
  display: flex;
  justify-content: space-between;
  align-items: center;
  width: 100%;
  gap: 8px;
  flex-wrap: wrap;

  .footer-meta {
    font-size: 10px;
    color: #94a3b8;
    white-space: nowrap;

    strong {
      color: #38bdf8;
    }

    .meta-divider {
      margin: 0 6px;
      color: #475569;
    }
  }

  .footer-actions {
    display: flex;
    gap: 6px;
  }
}

// Button styling lives in styles/global.scss (app-wide unified look).
</style>
