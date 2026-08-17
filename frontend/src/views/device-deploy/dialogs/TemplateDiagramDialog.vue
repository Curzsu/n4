<template>
  <el-dialog
    v-model="diagramDialogVisible"
    :title="`设备组态图 - ${currentDiagramTarget?.deviceTmplName || fallbackTemplate?.deviceTmplName || fallbackForm?.deviceTmplName || '设备模板'}`"
    width="75%"
    top="4vh"
    append-to-body
    class="dark-dialog diagram-modal-dialog"
    destroy-on-close
  >
    <div class="diagram-dialog-container">
      <div class="diagram-toolbar">
        <div class="toolbar-left">
          <span class="toolbar-label">当前组态图：</span>
          <el-select
            v-model="selectedDiagramFile"
            placeholder="选择组态图..."
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
          <span class="hint-text">💡 支持鼠标滚轮缩放、拖拽与点击全屏大图预览</span>
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
              <span>正在加载组态图...</span>
            </div>
          </template>
          <template #error>
            <div class="image-state-box error-box">
              <el-icon :size="48" class="error-icon"><PictureRounded /></el-icon>
              <p class="error-title">暂未找到该名称对应的组态图片</p>
              <p class="error-desc">请从上方下拉框切换已有的组态图，或将图片命名为对应文件名放置于 public/device-images/ 目录中。</p>
            </div>
          </template>
        </el-image>
      </div>
    </div>

    <template #footer>
      <div class="dialog-footer-flex">
        <div class="footer-meta">
          <span>模板名称: <strong>{{ currentDiagramTarget?.deviceTmplName || fallbackTemplate?.deviceTmplName || fallbackForm?.deviceTmplName || '—' }}</strong></span>
          <span class="meta-divider">|</span>
          <span>设备类型: <strong>{{ currentDiagramTarget?.deviceType || fallbackTemplate?.deviceType || fallbackForm?.deviceType || '—' }}</strong></span>
        </div>
        <div class="footer-actions">
          <el-button @click="diagramDialogVisible = false">关闭</el-button>
          <el-button type="primary" :icon="View" @click="openImageInNewTab">
            新标签页打开原图
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

// Fallback display context (the parent's selected template / create form) used
// only for the title and footer labels when currentDiagramTarget itself has no
// value. Preserves the exact fallback chain of the original inline dialog.
defineProps<{
  fallbackTemplate?: DeviceTemplate | null
  fallbackForm?: DeviceTemplate | null
}>()

// Available diagram images in public/device-images/Device-1/
const availableImages = [
  { label: 'DCPmv (直控配电)', value: 'DCPmv.png', subDir: 'Device-1' },
  { label: 'DEHU (除湿机)', value: 'DEHU.png', subDir: 'Device-1' },
  { label: 'DHG (加热除湿器)', value: 'DHG.png', subDir: 'Device-1' },
  { label: 'EM33b (智能电表 33b)', value: 'EM33b.png', subDir: 'Device-1' },
  { label: 'EM33c (智能电表 33c)', value: 'EM33c.png', subDir: 'Device-1' },
  { label: 'EM770 (智能电表 770)', value: 'EM770.png', subDir: 'Device-1' },
  { label: 'EM780 (智能电表 780)', value: 'EM780.png', subDir: 'Device-1' },
  { label: 'GAS (气体监测器)', value: 'GAS.png', subDir: 'Device-1' },
  { label: 'IRC (红外空调遥控器)', value: 'IRC.png', subDir: 'Device-1' },
  { label: 'NWLD (定位测漏水)', value: 'NWLD.png', subDir: 'Device-1' },
  { label: 'ZBmvbus (母线组态监控)', value: 'ZBmvbus.png', subDir: 'Device-1' },
  { label: 'ZBmvin (进线组态监控)', value: 'ZBmvin.png', subDir: 'Device-1' },
  { label: 'ZBmvout (出线组态监控)', value: 'ZBmvout.png', subDir: 'Device-1' },
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

// Open the dialog for a given device template target. Mirrors the previous
// openConfigPanel (target = selectedTemplate || form) and
// openConfigPanelWithTarget (target = a specific table row) entry points.
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
    padding: 16px 20px;
    background-color: #0b1118;
  }
}

.diagram-dialog-container {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.diagram-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 16px;
  background-color: rgba(30, 41, 59, 0.5);
  border: 1px solid #1e293b;
  border-radius: 6px;

  .toolbar-left {
    display: flex;
    align-items: center;
    gap: 8px;

    .toolbar-label {
      font-size: 13px;
      color: #94a3b8;
    }

    .path-tag {
      margin-left: 8px;
    }
  }

  .toolbar-right {
    .hint-text {
      font-size: 12px;
      color: #64748b;
    }
  }
}

.diagram-preview-box {
  width: 100%;
  height: 60vh;
  min-height: 420px;
  background-color: #06090e;
  border: 1px solid #1e293b;
  border-radius: 8px;
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
  gap: 12px;
  color: #94a3b8;
  font-size: 13px;

  &.error-box {
    color: #64748b;

    .error-icon {
      color: #475569;
    }

    .error-title {
      font-size: 15px;
      font-weight: 600;
      color: #e2e8f0;
      margin: 0;
    }

    .error-desc {
      font-size: 12px;
      max-width: 480px;
      text-align: center;
      line-height: 1.6;
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
    font-size: 13px;
    color: #94a3b8;

    strong {
      color: #38bdf8;
    }

    .meta-divider {
      margin: 0 8px;
      color: #475569;
    }
  }

  .footer-actions {
    display: flex;
    gap: 8px;
  }
}
</style>
