<template>
  <el-dialog
    v-model="diagramDialogVisible"
    :title="`Device Diagram - ${currentDiagramTarget?.deviceTmplName || fallbackTemplate?.deviceTmplName || fallbackForm?.deviceTmplName || 'Device Template'}`"
    width="75%"
    top="4vh"
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
            style="width: 260px;"
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
          <span class="hint-text">💡 Supports mouse wheel zoom, pan, and click to view fullscreen image</span>
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
          @load="diagramImageLoading = false"
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
import { ref, computed } from 'vue'
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

function handleDiagramChange() {
  diagramImageError.value = false
  diagramImageLoading.value = true
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

function open(target: DeviceTemplate | null) {
  currentDiagramTarget.value = target
  selectedDiagramFile.value = findBestMatchingImage(target)
  diagramImageLoading.value = true
  diagramImageError.value = false
  diagramDialogVisible.value = true
}

defineExpose({ open })
</script>

<style scoped lang="scss">
// ===== Diagram Modal Styles =====
.diagram-modal-dialog {
  :deep(.el-dialog__body) {
    padding: 12px 16px;
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
  padding: 6px 12px;
  background-color: rgba(30, 41, 59, 0.5);
  border: 1px solid #1e293b;
  border-radius: 4px;

  .toolbar-left {
    display: flex;
    align-items: center;
    gap: 6px;

    .toolbar-label {
      font-size: 10px;
      color: #94a3b8;
    }

    .path-tag {
      margin-left: 6px;
      font-size: 10px;
      height: 18px;
      line-height: 18px;
      padding: 0 6px;
      border-radius: 4px;
    }
  }

  .toolbar-right {
    .hint-text {
      font-size: 10px;
      color: #64748b;
    }
  }
}

.diagram-preview-box {
  width: 100%;
  height: 60vh;
  min-height: 400px;
  background-color: #06090e;
  border: 1px solid #1e293b;
  border-radius: 6px;
  overflow: hidden;
  display: flex;
  align-items: center;
  justify-content: center;
  position: relative;

  .main-diagram-image {
    width: 100%;
    height: 100%;
    display: flex;
    align-items: center;
    justify-content: center;
    cursor: zoom-in;
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

  .footer-meta {
    font-size: 10px;
    color: #94a3b8;

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

.btn-primary {
  background-color: rgba(16, 185, 129, 0.16) !important;
  color: #34d399 !important;
  border: none !important;
  border-radius: 9px !important;
  font-size: 10px !important;
  height: 20px !important;
  line-height: 16px !important;
  padding: 2px 8px !important;
}

.btn-secondary {
  background-color: rgba(59, 130, 246, 0.16) !important;
  color: #93c5fd !important;
  border: none !important;
  border-radius: 9px !important;
  font-size: 10px !important;
  height: 20px !important;
  line-height: 16px !important;
  padding: 2px 8px !important;
}
</style>
