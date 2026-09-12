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
            filterable
            style="width: 280px;"
            @change="handleDiagramChange"
          >
            <el-option-group
              v-for="group in imageGroups"
              :key="group.label"
              :label="group.label"
            >
              <el-option
                v-for="img in group.options"
                :key="img.value"
                :label="img.label"
                :value="img.value"
              />
            </el-option-group>
          </el-select>
          <el-tag size="small" type="success" effect="dark" class="path-tag">
            {{ currentDiagramUrl }}
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
              <p class="error-desc">Please select an available diagram from the dropdown above, or place the corresponding image file in the public directory.</p>
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

export interface DiagramImageOption {
  label: string
  value: string
  url: string
  keywords?: string[]
}

export interface DiagramImageGroup {
  label: string
  options: DiagramImageOption[]
}

// Available diagram images categorized by functional domain and asset source directory
const imageGroups: DiagramImageGroup[] = [
  {
    label: 'Device-1 (Primary Devices)',
    options: [
      { label: 'DCPmv (DC Power Distribution)', value: 'DCPmv.png', url: '/device-images/Device-1/DCPmv.png', keywords: ['dcpmv', 'dcp_zhengxiang_mv', 'dcp_mv', 'dcplv', 'dcp'] },
      { label: 'DEHU (Dehumidifier)', value: 'DEHU.png', url: '/device-images/Device-1/DEHU.png', keywords: ['dehu_oulandis', 'dehu'] },
      { label: 'DHG (Heating Dehumidifier)', value: 'DHG.png', url: '/device-images/Device-1/DHG.png', keywords: ['dhg', 'fxd860sa'] },
      { label: 'EM33b (Smart Power Meter 33b)', value: 'EM33b.png', url: '/device-images/Device-1/EM33b.png', keywords: ['spm33b', 'spm33a', 'spm32rmu', 'spm32', 'em33b', 'em33a'] },
      { label: 'EM33c (Smart Power Meter 33c)', value: 'EM33c.png', url: '/device-images/Device-1/EM33c.png', keywords: ['spm33c', 'em33c'] },
      { label: 'EM770 (Smart Power Meter 770)', value: 'EM770.png', url: '/device-images/Device-1/EM770.png', keywords: ['spm770h', 'spm770', 'em770'] },
      { label: 'EM780 (Smart Power Meter 780)', value: 'EM780.png', url: '/device-images/Device-1/EM780.png', keywords: ['spm780h', 'spm781h', 'em780', 'em781'] },
      { label: 'GAS (Gas Monitor)', value: 'GAS.png', url: '/device-images/Device-1/GAS.png', keywords: ['gas', 'yaoan'] },
      { label: 'IRC (AC IR Remote Controller)', value: 'IRC.png', url: '/device-images/Device-1/IRC.png', keywords: ['irc', 'ymk'] },
      { label: 'NWLD (Water Leak Detector)', value: 'NWLD.png', url: '/device-images/Device-1/NWLD.png', keywords: ['nwld'] },
      { label: 'ZBmvbus (Busbar Monitoring)', value: 'ZBmvbus.png', url: '/device-images/Device-1/ZBmvbus.png', keywords: ['mvbustie', 'zbmvbus', 'rmu'] },
      { label: 'ZBmvin (Incomer Monitoring)', value: 'ZBmvin.png', url: '/device-images/Device-1/ZBmvin.png', keywords: ['mvincomer', 'zbmvin'] },
      { label: 'ZBmvout (Feeder Monitoring)', value: 'ZBmvout.png', url: '/device-images/Device-1/ZBmvout.png', keywords: ['mvfeeder', 'zbmvout'] },
    ],
  },
  {
    label: 'Device-2 (Vendor & Secondary Devices)',
    options: [
      { label: 'CET (CET S963 Power Meter)', value: 'CET.png', url: '/Device-2/CET.png', keywords: ['s963', 'cet'] },
      { label: 'ET903 (YADA ET903 Power Meter)', value: 'ET903.png', url: '/Device-2/ET903.png', keywords: ['et903', 'yada'] },
      { label: 'Envicool (Envicool Precision AC)', value: 'Envicool.png', url: '/Device-2/Envicool.png', keywords: ['envicool', 'fwu'] },
      { label: 'ImpowerBS (Battery / APF System)', value: 'ImpowerBS.png', url: '/Device-2/ImpowerBS.png', keywords: ['impower', 'apf', 'battery', 'vertivxwd'] },
      { label: 'LD300 (LDS3000 Locating Leak Detector)', value: 'LD300.png', url: '/Device-2/LD300.png', keywords: ['lds3000', 'pwld_lds3000', 'pwld', 'ld300'] },
      { label: 'LD3 (LDS3 Non-locating Leak Detector)', value: 'LD3.png', url: '/Device-2/LD3.png', keywords: ['lds3', 'nwld_lds3', 'ld3'] },
      { label: 'PDU_Distribution (PDU / RPP Power Distribution)', value: 'PDU_Distribution.png', url: '/Device-2/PDU_Distribution.png', keywords: ['pdu', 'rpp', 'zh16'] },
      { label: 'SOCOMEC (SOCOMEC STS / UPS System)', value: 'SOCOMEC.png', url: '/Device-2/SOCOMEC.png', keywords: ['socomec', 'sts', 'ups_vertiv', 'ups'] },
      { label: 'STAND (STAND CRAH Dehumidifier)', value: 'STAND.png', url: '/Device-2/STAND.png', keywords: ['crah', 'stand'] },
    ],
  },
  {
    label: 'Building Automation & System (BA)',
    options: [
      { label: 'BA_ATS_Dual_Power_Switch (ATS Transfer Switch)', value: 'BA_ATS_Dual_Power_Switch.png', url: '/BA/BA_ATS_Dual_Power_Switch.png', keywords: ['ats', 'dual_power', 'switch_transfer'] },
      { label: 'Schneider_PM5300 (Schneider PM5300 Meter)', value: 'Schneider_PM5300.png', url: '/BA/Schneider_PM5300.png', keywords: ['pm5300', 'schneider_pm'] },
      { label: 'ZB_Schneider_P3U30_DGincomer (Generator Protection)', value: 'ZB_Schneider_P3U30_DGincomer.png', url: '/BA/ZB_Schneider_P3U30_DGincomer.png', keywords: ['p3u30_dgincomer', 'p3u30_dg', 'dgincomer'] },
      { label: 'ZB_Schneider_P3U30_TRincomer (Transformer Protection)', value: 'ZB_Schneider_P3U30_TRincomer.png', url: '/BA/ZB_Schneider_P3U30_TRincomer.png', keywords: ['p3u30_trincomer', 'p3u30_tr', 'trincomer'] },
      { label: 'CH_A01_Chiller (Chiller Unit)', value: 'CH_A01_Chiller.png', url: '/BA/CH_A01_Chiller.png', keywords: ['chiller', 'ch_a01'] },
      { label: 'BA_Datahall_GA_CDU (CDU Cooling System)', value: 'BA_Datahall_GA_CDU.png', url: '/BA/BA_Datahall_GA_CDU.png', keywords: ['cdu', 'datahall_cdu'] },
      { label: 'BA_Dosing_Constant_Pressure (Dosing Pressure System)', value: 'BA_Dosing_Constant_Pressure.png', url: '/BA/BA_Dosing_Constant_Pressure.png', keywords: ['dosing', 'constant_pressure'] },
      { label: 'BA_Makeup_Water_System (Makeup Water System)', value: 'BA_Makeup_Water_System.png', url: '/BA/BA_Makeup_Water_System.png', keywords: ['makeup_water', 'water_system'] },
      { label: 'BA_HVAC_Multi_System (HVAC Multi System)', value: 'BA_HVAC_Multi_System.png', url: '/BA/BA_HVAC_Multi_System.png', keywords: ['hvac', 'multi_system'] },
      { label: 'BA_Water_Cooling_System_A (Water Cooling System A)', value: 'BA_Water_Cooling_System_A.png', url: '/BA/BA_Water_Cooling_System_A.png', keywords: ['water_cooling'] },
      { label: 'BA_Network_Topology_PLC (PLC Network Topology)', value: 'BA_Network_Topology_PLC.png', url: '/BA/BA_Network_Topology_PLC.png', keywords: ['topology_plc', 'network_plc'] },
      { label: 'BA_Network_Topology_Datahall_PLC (Datahall PLC Topology)', value: 'BA_Network_Topology_Datahall_PLC.png', url: '/BA/BA_Network_Topology_Datahall_PLC.png', keywords: ['datahall_plc'] },
      { label: 'BA_Unit_Parameters_Alarms (Unit Parameters & Alarms)', value: 'BA_Unit_Parameters_Alarms.png', url: '/BA/BA_Unit_Parameters_Alarms.png', keywords: ['parameters_alarms', 'unit_alarms'] },
    ],
  },
]

const availableImages = computed(() => imageGroups.flatMap(g => g.options))

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
  const allImages = availableImages.value
  if (!target) return allImages[0].value

  const candidates = [
    target.facePlateId,
    target.deviceTmplName,
    target.model,
    target.deviceType,
    target.manufacturer,
  ].filter(Boolean).map(s => String(s).trim().toLowerCase())

  const candidateCombined = candidates.join(' ')

  // 1. Direct match on keywords (longer/more specific keywords take precedence)
  const keywordEntries: { kw: string; value: string }[] = []
  for (const item of allImages) {
    if (item.keywords) {
      for (const kw of item.keywords) {
        keywordEntries.push({ kw, value: item.value })
      }
    }
  }
  keywordEntries.sort((a, b) => b.kw.length - a.kw.length)

  for (const { kw, value } of keywordEntries) {
    if (candidateCombined.includes(kw)) {
      return value
    }
  }

  // 2. Direct match on file name without extension
  for (const item of allImages) {
    const rawName = item.value.replace(/\.(png|jpg|jpeg|svg)$/i, '').toLowerCase()
    for (const cand of candidates) {
      if (cand === rawName || cand.includes(rawName) || rawName.includes(cand)) {
        return item.value
      }
    }
  }

  return allImages[0].value
}

const currentDiagramUrl = computed(() => {
  if (!selectedDiagramFile.value) return ''
  const allImages = availableImages.value
  const matched = allImages.find(i => i.value === selectedDiagramFile.value)
  return matched ? matched.url : `/device-images/Device-1/${selectedDiagramFile.value}`
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
