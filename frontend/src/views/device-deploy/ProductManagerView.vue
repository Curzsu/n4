<template>
  <div class="product-manager-view" v-loading="loading">
    <div class="card-container detail-card">
      <!-- Case 1: Template detail view when template selected -->
      <div v-if="selectedTemplate" class="form-container-wrapper">
        <div class="panel-header">
          <div class="header-title">
            <el-icon class="title-icon"><Edit /></el-icon>
            <span>Device Template - {{ selectedTemplate.deviceTmplName }}</span>
          </div>
          <div class="action-buttons">
            <el-button size="small" class="btn-secondary" :icon="Odometer" @click="openConfigPanel">
              Set Faceplate Panel
            </el-button>
            <el-button size="small" class="btn-secondary" :icon="Edit" @click="openEditTemplateDialog">Edit Template</el-button>
            <el-button 
              size="small" 
              class="btn-danger"
              :icon="Delete"
              @click="confirmDelete(selectedTemplate)"
            >
              Delete Template
            </el-button>
            <el-button size="small" class="btn-secondary btn-back" @click="goBackToList">Back to List</el-button>
          </div>
        </div>

        <div class="detail-body">
          <el-form 
            :model="form" 
            label-width="110px"
            label-position="right"
            class="custom-form-compact"
            disabled
          >
            <el-row :gutter="16">
              <el-col :span="8">
                <el-form-item label="Template Name" prop="deviceTmplName">
                  <el-input v-model="form.deviceTmplName" />
                </el-form-item>
              </el-col>
              <el-col :span="8">
                <el-form-item label="Template Type" prop="deviceType">
                  <el-input v-model="form.deviceType" />
                </el-form-item>
              </el-col>
              <el-col :span="8">
                <el-form-item label="Model / Specs" prop="model">
                  <el-input v-model="form.model" />
                </el-form-item>
              </el-col>
            </el-row>
            <el-row :gutter="16">
              <el-col :span="12">
                <el-form-item label="Manufacturer" prop="manufacturer">
                  <el-input v-model="form.manufacturer" />
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="Manufacture Date" prop="manuDate">
                  <el-date-picker
                    v-model="form.manuDate"
                    type="date"
                    format="YYYY-MM-DD"
                    value-format="YYYY-MM-DD"
                    style="width: 100%"
                  />
                </el-form-item>
              </el-col>
            </el-row>
          </el-form>

          <!-- Device Template Points Table -->
          <div class="point-table-section" v-loading="loadingPoints">
            <div class="points-header">
              <div class="section-title">
                <el-icon class="section-icon"><List /></el-icon>
                <span>Points Table (Total: {{ templatePoints.length }} points)</span>
              </div>
              <div class="points-actions">
                <!-- Batch manage state -->
                <template v-if="isPointBatchManaging">
                  <el-button 
                    type="danger" 
                    size="small" 
                    class="btn-danger"
                    :icon="Delete" 
                    :disabled="selectedPoints.length === 0" 
                    @click="batchDeletePoints"
                  >
                    Batch Delete ({{ selectedPoints.length }} selected)
                  </el-button>
                  <el-button 
                    size="small"
                    class="btn-secondary"
                    @click="exitPointBatchManage"
                  >
                    Exit Manage
                  </el-button>
                </template>
                <!-- Normal state -->
                <template v-else>
                  <el-button 
                    size="small" 
                    class="btn-secondary"
                    :icon="Edit"
                    @click="enterPointBatchManage"
                  >
                    Batch Manage
                  </el-button>
                  <el-button size="small" class="btn-primary" :icon="Plus" @click="openAddPointDialog">Add Point</el-button>
                </template>
              </div>
            </div>
            
            <el-table 
              :data="templatePoints" 
              style="width: 100%; margin-top: 6px;" 
              border 
              size="small"
              class="custom-table"
              :header-cell-style="headerCellStyle"
              :cell-style="cellStyle"
              @selection-change="handleSelectionChange"
            >
              <el-table-column type="selection" width="30" v-if="isPointBatchManaging" align="center" header-align="center" :resizable="false" />
              <el-table-column prop="pointTmplId" label="ID" width="55" align="left" header-align="center" :resizable="false" />
              <el-table-column prop="pointName" label="Point Name" min-width="180" align="left" header-align="center" show-overflow-tooltip :resizable="false" />
              <el-table-column prop="dataType" label="Data Type" width="110" align="left" header-align="center" :resizable="false" />
              <el-table-column prop="unit" label="Unit" width="90" align="left" header-align="center" :resizable="false" />
              <el-table-column prop="displayPrecision" label="Precision" width="90" align="left" header-align="center" :resizable="false" />
              <el-table-column label="Alarm Rule" min-width="220" align="left" header-align="center" :resizable="false">
                <template #default="{ row }">
                  <div class="alarm-rule-cell">
                    <span
                      v-if="row.alarmCode"
                      class="alarm-code-tag"
                      title="Click to configure alarm rule"
                      @click="openPointAlarmConfig(row)"
                    >
                      {{ row.alarmCode }}
                    </span>
                    <span
                      v-else
                      class="alarm-none-tag"
                      title="Click to configure alarm rule"
                      @click="openPointAlarmConfig(row)"
                    >
                      Unconfigured
                    </span>
                    <div class="alarm-btn-group">
                      <el-button size="small" type="primary" link :icon="Edit" @click="openEditPointDialog(row)">Edit</el-button>
                      <el-button size="small" type="danger" link :icon="Delete" @click="deletePoint(row)">Delete</el-button>
                    </div>
                  </div>
                </template>
              </el-table-column>
            </el-table>
          </div>
        </div>
      </div>

      <!-- Case 3: Root node selected -> Display Table of all templates -->
      <div v-else class="table-container-wrapper">
        <div class="panel-header">
          <div class="header-title">
            <el-icon class="title-icon"><List /></el-icon>
            <span>Device Templates (Total: {{ templates.length }})</span>
          </div>
          <div class="action-buttons">
            <!-- Batch manage state -->
            <template v-if="isTemplateBatchManaging">
              <el-button 
                type="danger" 
                size="small" 
                class="btn-danger"
                :icon="Delete"
                :disabled="selectedTemplates.length === 0"
                @click="batchDeleteTemplates"
              >
                Batch Delete ({{ selectedTemplates.length }} selected)
              </el-button>
              <el-button 
                size="small"
                class="btn-secondary"
                @click="exitTemplateBatchManage"
              >
                Exit Manage
              </el-button>
            </template>
            <!-- Normal state -->
            <template v-else>
              <el-button 
                size="small" 
                class="btn-secondary"
                :icon="Edit"
                @click="enterTemplateBatchManage"
              >
                Batch Manage
              </el-button>
              <el-button 
                size="small" 
                class="btn-primary"
                :icon="Plus"
                @click="openAddTemplateDialog"
              >
                Add Template
              </el-button>
              <el-button size="small" class="btn-secondary" :icon="Download" @click="handleImport">Import</el-button>
              <el-button size="small" class="btn-secondary" :icon="Upload" @click="handleExport">Export</el-button>
            </template>
          </div>
        </div>

        <div class="table-body">
          <div class="search-box-table">
            <el-input
              v-model="tableSearchQuery"
              placeholder="Search template name..."
              size="small"
              :prefix-icon="Search"
              clearable
            />
          </div>

          <el-table 
            :data="filteredTableTemplates" 
            style="width: 100%; flex: 1;" 
            border 
            size="small"
            class="custom-table"
            :header-cell-style="headerCellStyle"
            :cell-style="cellStyle"
            @selection-change="handleTemplateSelectionChange"
          >
            <el-table-column v-if="isTemplateBatchManaging" type="selection" width="30" align="center" header-align="center" :resizable="false" />
            <el-table-column prop="deviceTmplId" label="ID" width="55" align="left" header-align="center" :resizable="false" />
            <el-table-column prop="deviceTmplName" label="Template Name" min-width="180" align="left" header-align="center" show-overflow-tooltip :resizable="false" />
            <el-table-column prop="deviceType" label="Type" width="110" align="left" header-align="center" show-overflow-tooltip :resizable="false" />
            <el-table-column label="Model / Specs" min-width="130" align="left" header-align="center" show-overflow-tooltip :resizable="false">
              <template #default="{ row }">
                <span>{{ row.model || '—' }}</span>
              </template>
            </el-table-column>
            <el-table-column label="Manufacturer" min-width="140" align="left" header-align="center" show-overflow-tooltip :resizable="false">
              <template #default="{ row }">
                <span>{{ row.manufacturer || '—' }}</span>
              </template>
            </el-table-column>
            <el-table-column label="Manufacture Date" width="110" align="left" header-align="center" :resizable="false">
              <template #default="{ row }">
                <span>{{ row.manuDate || '—' }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="pointCount" label="Points" width="70" align="left" header-align="center" :resizable="false" />
            <el-table-column label="Actions" width="160" align="center" header-align="center" fixed="right" :resizable="false">
              <template #default="{ row }">
                <el-button 
                  type="success" 
                  link 
                  size="small" 
                  :icon="Picture" 
                  @click="openConfigPanelWithTarget(row as DeviceTemplate)"
                >
                  Diagram
                </el-button>
                <el-button 
                  type="primary" 
                  link 
                  size="small" 
                  @click="editTemplateFromTable(row as DeviceTemplate)"
                >
                  Edit
                </el-button>
                <el-button 
                  v-if="isTemplateBatchManaging"
                  type="danger" 
                  link 
                  size="small" 
                  @click="deleteTemplateFromTable(row as DeviceTemplate)"
                >
                  Delete
                </el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </div>
    </div>

    <!-- ===== Dialog: Add Device Template ===== -->
    <el-dialog
      v-model="addTemplateDialogVisible"
      title="Add Device Template"
      width="520px"
      append-to-body
      class="dark-dialog"
      destroy-on-close
    >
      <el-form
        :model="addTemplateForm"
        :rules="addTemplateRules"
        ref="addTemplateFormRef"
        label-width="120px"
        label-position="right"
        size="small"
        class="custom-form"
      >
        <el-form-item label="Template Name" prop="deviceTmplName">
          <el-input v-model="addTemplateForm.deviceTmplName" maxlength="64" placeholder="e.g. Row Precision AC" />
        </el-form-item>

        <el-form-item label="Template Type" prop="deviceType">
          <el-input v-model="addTemplateForm.deviceType" maxlength="50" placeholder="e.g. AC, UPS, TRANSFORMER" />
        </el-form-item>

        <el-form-item label="Model / Specs" prop="model">
          <el-input v-model="addTemplateForm.model" maxlength="50" placeholder="e.g. DK-1200XT" />
        </el-form-item>

        <el-form-item label="Manufacturer" prop="manufacturer">
          <el-input v-model="addTemplateForm.manufacturer" maxlength="50" placeholder="e.g. Daikin Industries" />
        </el-form-item>

        <el-form-item label="Manufacture Date" prop="manuDate">
          <el-date-picker
            v-model="addTemplateForm.manuDate"
            type="date"
            placeholder="Select date"
            format="YYYY-MM-DD"
            value-format="YYYY-MM-DD"
            style="width: 100%"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button class="btn-secondary" size="small" @click="addTemplateDialogVisible = false">Cancel</el-button>
        <el-button
          type="primary"
          size="small"
          class="btn-primary"
          @click="submitAddTemplate"
          :loading="addTemplateSubmitting"
        >
          Create
        </el-button>
      </template>
    </el-dialog>

    <!-- ===== Dialog: Edit Device Template ===== -->
    <el-dialog
      v-model="editTemplateDialogVisible"
      title="Edit Device Template"
      width="520px"
      append-to-body
      class="dark-dialog"
    >
      <el-form
        :model="editTemplateForm"
        :rules="editTemplateRules"
        ref="editTemplateFormRef"
        label-width="120px"
        label-position="right"
        size="small"
        class="custom-form"
      >
        <el-form-item label="Template Name" prop="deviceTmplName">
          <el-input v-model="editTemplateForm.deviceTmplName" maxlength="64" placeholder="e.g. Row Precision AC" />
        </el-form-item>

        <el-form-item label="Template Type" prop="deviceType">
          <el-input v-model="editTemplateForm.deviceType" maxlength="50" placeholder="e.g. AC, UPS, TRANSFORMER" />
        </el-form-item>

        <el-form-item label="Model / Specs" prop="model">
          <el-input v-model="editTemplateForm.model" maxlength="50" placeholder="e.g. DK-1200XT" />
        </el-form-item>

        <el-form-item label="Manufacturer" prop="manufacturer">
          <el-input v-model="editTemplateForm.manufacturer" maxlength="50" placeholder="e.g. Daikin Industries" />
        </el-form-item>

        <el-form-item label="Manufacture Date" prop="manuDate">
          <el-date-picker
            v-model="editTemplateForm.manuDate"
            type="date"
            placeholder="Select date"
            format="YYYY-MM-DD"
            value-format="YYYY-MM-DD"
            style="width: 100%"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button class="btn-secondary" size="small" @click="editTemplateDialogVisible = false">Cancel</el-button>
        <el-button
          type="primary"
          size="small"
          class="btn-primary"
          @click="submitEditTemplate"
          :loading="editTemplateSubmitting"
        >
          Save
        </el-button>
      </template>
    </el-dialog>

    <!-- Add/Edit Point Dialog -->
    <el-dialog
      v-model="pointDialogVisible"
      :title="isEditingPoint ? 'Edit Point' : 'Add Point'"
      width="500px"
      append-to-body
      class="dark-dialog"
    >
      <el-form :model="pointForm" :rules="pointRules" ref="pointFormRef" label-width="120px">
        <el-form-item label="Point Name" prop="pointName">
          <el-input v-model="pointForm.pointName" maxlength="50" placeholder="e.g. Temperature" />
        </el-form-item>
        <el-form-item label="Point Type" prop="pointType">
          <el-select v-model="pointForm.pointType" placeholder="Select point type" style="width: 100%" clearable>
            <el-option value="DI" label="DI - Digital Input" />
            <el-option value="DO" label="DO - Digital Output" />
            <el-option value="AI" label="AI - Analog Input" />
            <el-option value="AO" label="AO - Analog Output" />
          </el-select>
        </el-form-item>
        <el-form-item label="Data Type" prop="dataType">
          <el-select v-model="pointForm.dataType" placeholder="Select data type" style="width: 100%" clearable>
            <el-option value="numeric" label="numeric - Number" />
            <el-option value="enum" label="enum - Enumeration" />
            <el-option value="boolean" label="boolean - Boolean" />
            <el-option value="string" label="string - String" />
          </el-select>
        </el-form-item>
        <el-form-item label="Unit" prop="unit">
          <el-input v-model="pointForm.unit" maxlength="20" placeholder="e.g. ℃, %, V, None" />
        </el-form-item>
        <el-form-item label="Precision" prop="displayPrecision">
          <el-input-number v-model="pointForm.displayPrecision" :min="0" :max="4" style="width: 100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button class="btn-secondary" @click="pointDialogVisible = false">Cancel</el-button>
        <el-button type="primary" class="btn-primary" @click="submitPointForm" :loading="pointSubmitting">Confirm</el-button>
      </template>
    </el-dialog>

    <!-- Import CSV Drag & Drop Dialog -->
    <el-dialog
      v-model="importDialogVisible"
      title="Import Point Data"
      width="460px"
      append-to-body
      class="dark-dialog"
    >
      <el-upload
        class="upload-drag-area"
        drag
        action="#"
        :auto-upload="false"
        :show-file-list="false"
        accept=".csv"
        :on-change="handleImportFileChange"
      >
        <el-icon class="el-icon--upload" style="font-size: 48px; color: #3b82f6; margin-bottom: 12px;"><UploadFilled /></el-icon>
        <div class="el-upload__text" style="color: #cbd5e1; font-size: 14px;">
          Drag CSV template file here, or <em style="color: #3b82f6; font-style: normal; font-weight: bold; cursor: pointer;">click to upload</em>
        </div>
        <template #tip>
          <div class="el-upload__tip text-center" style="margin-top: 15px; color: #64748b; font-size: 12px; text-align: center;">
            Only .csv format is supported. Please ensure the data format is correct before importing.
          </div>
        </template>
      </el-upload>
      <template #footer>
        <div style="display: flex; justify-content: space-between; align-items: center; width: 100%;">
          <el-button type="primary" link :icon="Download" @click="downloadImportTemplate">
            Download Template
          </el-button>
          <el-button class="btn-secondary" @click="importDialogVisible = false">Close</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- Device Diagram Modal Dialog -->
    <TemplateDiagramDialog
      ref="diagramDialogRef"
      :fallback-template="selectedTemplate"
      :fallback-form="form"
    />

    <!-- Edit Alarm Rule Modal Dialog (identical to Alarm Configuration) -->
    <AlarmRuleEditModal
      ref="alarmRuleEditModalRef"
      @saved="handleAlarmSaved"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, nextTick, onMounted, type CSSProperties } from 'vue'
import { Plus, Edit, Delete, List, Odometer, Download, Upload, UploadFilled, Search, Picture } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { templateApi } from '@/api/template'
import type { DeviceTemplate, PointTemplateItem } from '@/api/types'
import type { FormInstance, FormRules } from 'element-plus'
import { useDashboardStore } from '@/stores/dashboard'
import TemplateDiagramDialog from './dialogs/TemplateDiagramDialog.vue'
import AlarmRuleEditModal from './dialogs/AlarmRuleEditModal.vue'
import { triggerFormShake } from '@/utils/formAnimation'

const store = useDashboardStore()

onMounted(async () => {
  if (!store.allTemplates.length) {
    await store.loadTemplates()
  }
})

// Unified table styling matching AlarmTable.vue exactly
const headerCellStyle: CSSProperties = {
  backgroundColor: '#0d1117',
  color: '#e5e7eb',
  fontSize: '10px',
  fontWeight: '600',
  borderBottom: '1px solid #2d3748',
  textAlign: 'center',
}

const cellStyle: CSSProperties = {
  backgroundColor: '#111827',
  color: '#e5e7eb',
  fontSize: '10px',
  borderBottom: '1px solid #1f2937',
  textAlign: 'left',
}

const loading = ref(false)
const tableSearchQuery = ref('')
const selectedPoints = ref<any[]>([])
const isPointBatchManaging = ref(false)
const isTemplateBatchManaging = ref(false)
const selectedTemplates = ref<any[]>([])
const importDialogVisible = ref(false)

function handleTemplateSelectionChange(val: any[]) {
  selectedTemplates.value = val
}

function enterTemplateBatchManage() {
  isTemplateBatchManaging.value = true
  selectedTemplates.value = []
}

function exitTemplateBatchManage() {
  isTemplateBatchManaging.value = false
  selectedTemplates.value = []
}

function enterPointBatchManage() {
  isPointBatchManaging.value = true
  selectedPoints.value = []
}

function exitPointBatchManage() {
  isPointBatchManaging.value = false
  selectedPoints.value = []
}

// Point dialog states
const pointDialogVisible = ref(false)
const isEditingPoint = ref(false)
const pointSubmitting = ref(false)
const pointFormRef = ref<FormInstance>()
const pointForm = ref<any>({
  pointTmplId: null,
  pointName: '',
  pointType: null,
  dataType: null,
  unit: '',
  displayPrecision: 0
})

const validatePointNameUnique = (_rule: any, value: any, callback: any) => {
  const trimmed = typeof value === 'string' ? value.trim() : ''
  if (!trimmed) {
    callback(new Error('Please enter point name'))
    return
  }
  if (trimmed.length < 1 || trimmed.length > 50) {
    callback(new Error('Point name must be between 1 and 50 characters'))
    return
  }
  if (/[\u4e00-\u9fa5\u3000-\u303f\uff00-\uffef]/.test(trimmed) || /[^\x00-\x7F]/.test(trimmed)) {
    callback(new Error('Point name cannot contain Chinese characters'))
    return
  }
  if (!/^[a-zA-Z0-9_\-\.\s()]+$/.test(trimmed)) {
    callback(new Error('Point name contains invalid characters'))
    return
  }
  const list = templatePoints.value
  const exists = list.some(p => {
    if (isEditingPoint.value && p.pointTmplId === pointForm.value.pointTmplId) {
      return false
    }
    return (p.pointName || '').trim().toLowerCase() === trimmed.toLowerCase()
  })
  if (exists) {
    callback(new Error('Point name already exists in this template'))
    return
  }
  callback()
}

const validateUnit = (_rule: any, value: any, callback: any) => {
  if (value !== undefined && value !== null && value !== '') {
    const trimmed = typeof value === 'string' ? value.trim() : ''
    if (trimmed.length > 20) {
      callback(new Error('Unit cannot exceed 20 characters'))
      return
    }
    if (/[\u4e00-\u9fa5\u3000-\u303f\uff00-\uffef]/.test(trimmed) || /[^\x00-\x7F]/.test(trimmed)) {
      callback(new Error('Unit cannot contain Chinese characters'))
      return
    }
  }
  callback()
}

const pointRules = ref<FormRules>({
  pointName: [
    { required: true, message: 'Please enter point name', trigger: 'blur' },
    { validator: validatePointNameUnique, trigger: 'blur' },
    { max: 50, message: 'Point name cannot exceed 50 characters', trigger: 'blur' }
  ],
  pointType: [
    { required: true, message: 'Please select point type', trigger: 'change' }
  ],
  dataType: [
    { required: true, message: 'Please select data type', trigger: 'change' }
  ],
  unit: [
    { validator: validateUnit, trigger: 'blur' },
    { max: 20, message: 'Unit cannot exceed 20 characters', trigger: 'blur' }
  ],
  displayPrecision: [
    {
      validator: (_rule: any, value: any, callback: any) => {
        if (value !== undefined && value !== null && value !== '') {
          const num = Number(value)
          if (!Number.isInteger(num) || num < 0 || num > 4) {
            callback(new Error('Precision must be an integer between 0 and 4'))
            return
          }
        }
        callback()
      },
      trigger: 'change'
    }
  ]
})

const form = ref<DeviceTemplate>({
  deviceTmplName: '',
  deviceType: '',
  model: '',
  manufacturer: '',
  manuDate: ''
})

const validateNoChinese = (fieldName: string, maxLen: number) => (_rule: any, value: any, callback: any) => {
  if (value !== undefined && value !== null && value !== '') {
    const trimmed = typeof value === 'string' ? value.trim() : ''
    if (trimmed.length > maxLen) {
      callback(new Error(`${fieldName} cannot exceed ${maxLen} characters`))
      return
    }
    if (/[\u4e00-\u9fa5\u3000-\u303f\uff00-\uffef]/.test(trimmed) || /[^\x00-\x7F]/.test(trimmed)) {
      callback(new Error(`${fieldName} cannot contain Chinese characters`))
      return
    }
  }
  callback()
}

const validateDeviceType = (_rule: any, value: any, callback: any) => {
  const trimmed = typeof value === 'string' ? value.trim() : ''
  if (!trimmed) {
    callback(new Error('Please enter template type'))
    return
  }
  if (trimmed.length < 1 || trimmed.length > 50) {
    callback(new Error('Template type must be between 1 and 50 characters'))
    return
  }
  if (/[\u4e00-\u9fa5\u3000-\u303f\uff00-\uffef]/.test(trimmed) || /[^\x00-\x7F]/.test(trimmed)) {
    callback(new Error('Template type cannot contain Chinese characters'))
    return
  }
  if (!/^[a-zA-Z0-9_\-]+$/.test(trimmed)) {
    callback(new Error('Template type must contain only letters, numbers, hyphens or underscores'))
    return
  }
  callback()
}

// Template Add Dialog states
const addTemplateDialogVisible = ref(false)
const addTemplateSubmitting = ref(false)
const addTemplateFormRef = ref<FormInstance>()
const addTemplateForm = ref<DeviceTemplate>({
  deviceTmplName: '',
  deviceType: '',
  model: '',
  manufacturer: '',
  manuDate: ''
})

const validateAddTemplateNameUnique = (_rule: any, value: any, callback: any) => {
  const trimmed = typeof value === 'string' ? value.trim() : ''
  if (!trimmed) {
    callback(new Error('Please enter template name'))
    return
  }
  if (trimmed.length < 2 || trimmed.length > 64) {
    callback(new Error('Template name must be between 2 and 64 characters'))
    return
  }
  if (/[\u4e00-\u9fa5\u3000-\u303f\uff00-\uffef]/.test(trimmed) || /[^\x00-\x7F]/.test(trimmed)) {
    callback(new Error('Template name cannot contain Chinese characters'))
    return
  }
  if (!/^[a-zA-Z0-9_\-\.\s()]+$/.test(trimmed)) {
    callback(new Error('Template name contains invalid characters'))
    return
  }
  const exists = store.allTemplates.some(t => {
    return (t.deviceTmplName || '').trim().toLowerCase() === trimmed.toLowerCase()
  })
  if (exists) {
    callback(new Error('Template name already exists'))
    return
  }
  callback()
}

const addTemplateRules = ref<FormRules>({
  deviceTmplName: [
    { required: true, message: 'Please enter template name', trigger: 'blur' },
    { validator: validateAddTemplateNameUnique, trigger: 'blur' },
    { max: 64, message: 'Template name cannot exceed 64 characters', trigger: 'blur' }
  ],
  deviceType: [
    { required: true, message: 'Please enter template type', trigger: 'blur' },
    { validator: validateDeviceType, trigger: 'blur' },
    { max: 50, message: 'Template type cannot exceed 50 characters', trigger: 'blur' }
  ],
  model: [
    { validator: validateNoChinese('Model / specs', 50), trigger: 'blur' },
    { max: 50, message: 'Model / specs cannot exceed 50 characters', trigger: 'blur' }
  ],
  manufacturer: [
    { validator: validateNoChinese('Manufacturer', 50), trigger: 'blur' },
    { max: 50, message: 'Manufacturer cannot exceed 50 characters', trigger: 'blur' }
  ]
})

function openAddTemplateDialog() {
  addTemplateForm.value = {
    deviceTmplName: '',
    deviceType: '',
    model: '',
    manufacturer: '',
    manuDate: ''
  }
  addTemplateDialogVisible.value = true
  nextTick(() => {
    if (addTemplateFormRef.value) {
      addTemplateFormRef.value.clearValidate()
    }
  })
}

async function submitAddTemplate() {
  if (!addTemplateFormRef.value) return
  const valid = await addTemplateFormRef.value.validate().catch(() => false)
  if (!valid) {
    triggerFormShake(addTemplateFormRef, 'Please check and correct the highlighted template fields')
    return
  }

  try {
    await ElMessageBox.confirm(
      `Are you sure you want to create device template "${addTemplateForm.value.deviceTmplName}"?`,
      'Confirm Create',
      {
        confirmButtonText: 'Create',
        cancelButtonText: 'Cancel',
        type: 'warning',
        customClass: 'dark-message-box'
      }
    )
  } catch {
    return
  }

  addTemplateSubmitting.value = true
  try {
    const payload: DeviceTemplate = {
      deviceTmplName: addTemplateForm.value.deviceTmplName.trim(),
      deviceType: addTemplateForm.value.deviceType.trim(),
      model: addTemplateForm.value.model ? addTemplateForm.value.model.trim() : '',
      manufacturer: addTemplateForm.value.manufacturer ? addTemplateForm.value.manufacturer.trim() : '',
      manuDate: addTemplateForm.value.manuDate || ''
    }
    const createdTemplate = await templateApi.createTemplate(payload)
    ElMessage.success('Device template created successfully')
    addTemplateDialogVisible.value = false
    await store.refreshAfterMutation()

    store.selectedNode = {
      id: createdTemplate.deviceTmplId,
      label: createdTemplate.deviceTmplName,
      type: 'product',
      status: 1,
      children: []
    }
  } catch (err: any) {
    ElMessage.error(err.message || 'Failed to create template')
  } finally {
    addTemplateSubmitting.value = false
  }
}

// Template Edit Dialog states
const editTemplateDialogVisible = ref(false)
const editTemplateSubmitting = ref(false)
const editTemplateFormRef = ref<FormInstance>()
const editTemplateForm = ref<DeviceTemplate>({
  deviceTmplName: '',
  deviceType: '',
  model: '',
  manufacturer: '',
  manuDate: ''
})

const validateEditTemplateNameUnique = (_rule: any, value: any, callback: any) => {
  const trimmed = typeof value === 'string' ? value.trim() : ''
  if (!trimmed) {
    callback(new Error('Please enter template name'))
    return
  }
  if (trimmed.length < 2 || trimmed.length > 64) {
    callback(new Error('Template name must be between 2 and 64 characters'))
    return
  }
  if (/[\u4e00-\u9fa5\u3000-\u303f\uff00-\uffef]/.test(trimmed) || /[^\x00-\x7F]/.test(trimmed)) {
    callback(new Error('Template name cannot contain Chinese characters'))
    return
  }
  if (!/^[a-zA-Z0-9_\-\.\s()]+$/.test(trimmed)) {
    callback(new Error('Template name contains invalid characters'))
    return
  }
  const currentId = editTemplateForm.value.deviceTmplId
  const exists = store.allTemplates.some(t => {
    if (currentId && t.deviceTmplId === currentId) {
      return false
    }
    return (t.deviceTmplName || '').trim().toLowerCase() === trimmed.toLowerCase()
  })
  if (exists) {
    callback(new Error('Template name already exists'))
    return
  }
  callback()
}

const editTemplateRules = ref<FormRules>({
  deviceTmplName: [
    { required: true, message: 'Please enter template name', trigger: 'blur' },
    { validator: validateEditTemplateNameUnique, trigger: 'blur' },
    { max: 64, message: 'Template name cannot exceed 64 characters', trigger: 'blur' }
  ],
  deviceType: [
    { required: true, message: 'Please enter template type', trigger: 'blur' },
    { validator: validateDeviceType, trigger: 'blur' },
    { max: 50, message: 'Template type cannot exceed 50 characters', trigger: 'blur' }
  ],
  model: [
    { validator: validateNoChinese('Model / specs', 50), trigger: 'blur' },
    { max: 50, message: 'Model / specs cannot exceed 50 characters', trigger: 'blur' }
  ],
  manufacturer: [
    { validator: validateNoChinese('Manufacturer', 50), trigger: 'blur' },
    { max: 50, message: 'Manufacturer cannot exceed 50 characters', trigger: 'blur' }
  ]
})

function openEditTemplateDialog() {
  if (!selectedTemplate.value) return
  editTemplateForm.value = {
    deviceTmplId: selectedTemplate.value.deviceTmplId,
    deviceTmplName: selectedTemplate.value.deviceTmplName,
    deviceType: selectedTemplate.value.deviceType,
    model: selectedTemplate.value.model,
    manufacturer: selectedTemplate.value.manufacturer,
    manuDate: selectedTemplate.value.manuDate
  }
  editTemplateDialogVisible.value = true
  nextTick(() => {
    if (editTemplateFormRef.value) {
      editTemplateFormRef.value.clearValidate()
    }
  })
}

async function submitEditTemplate() {
  if (!editTemplateForm.value.deviceTmplId) {
    ElMessage.error('Missing template ID')
    return
  }
  if (!editTemplateFormRef.value) return
  const valid = await editTemplateFormRef.value.validate().catch(() => false)
  if (!valid) {
    triggerFormShake(editTemplateFormRef, 'Please check and correct the highlighted template fields')
    return
  }

  try {
    await ElMessageBox.confirm(
      `Are you sure you want to save changes to template "${editTemplateForm.value.deviceTmplName}"?`,
      'Confirm Save',
      {
        confirmButtonText: 'Save',
        cancelButtonText: 'Cancel',
        type: 'warning',
        customClass: 'dark-message-box'
      }
    )
  } catch {
    return
  }

  try {
    editTemplateSubmitting.value = true
    const payload: DeviceTemplate = {
      ...editTemplateForm.value,
      deviceTmplName: editTemplateForm.value.deviceTmplName.trim(),
      deviceType: editTemplateForm.value.deviceType.trim(),
      model: editTemplateForm.value.model ? editTemplateForm.value.model.trim() : '',
      manufacturer: editTemplateForm.value.manufacturer ? editTemplateForm.value.manufacturer.trim() : '',
      manuDate: editTemplateForm.value.manuDate || ''
    }
    await templateApi.updateTemplate(editTemplateForm.value.deviceTmplId, payload)
    ElMessage.success('Template updated successfully')
    editTemplateDialogVisible.value = false
    await store.refreshAfterMutation()
    if (selectedTemplate.value) {
      form.value = { ...selectedTemplate.value }
    }
  } catch (err: any) {
    ElMessage.error(err.message || 'Failed to update template')
  } finally {
    editTemplateSubmitting.value = false
  }
}

const templates = computed<DeviceTemplate[]>(() => store.allTemplates)

const selectedTemplate = computed<DeviceTemplate | null>(() => {
  if (!store.selectedNode || store.selectedNode.id === 'root' || store.selectedNode.id === 'root_tmpl') {
    return null
  }
  const found = templates.value.find(t => t.deviceTmplId === store.selectedNode?.id)
  return found || null
})

const templatePoints = ref<PointTemplateItem[]>([])
const loadingPoints = ref(false)

watch(selectedTemplate, async (newVal) => {
  isPointBatchManaging.value = false
  isTemplateBatchManaging.value = false
  selectedPoints.value = []
  selectedTemplates.value = []
  if (newVal) {
    form.value = { ...newVal }
    await loadTemplatePoints()
  } else {
    templatePoints.value = []
  }
}, { immediate: true })

const filteredTableTemplates = computed(() => {
  const query = tableSearchQuery.value.trim().toLowerCase()
  if (!query) return templates.value
  return templates.value.filter(t => t.deviceTmplName.toLowerCase().includes(query))
})

function editTemplateFromTable(row: DeviceTemplate) {
  store.selectedNode = {
    id: row.deviceTmplId,
    label: row.deviceTmplName,
    type: 'product',
    status: 1,
    children: []
  }
}

function goBackToList() {
  store.selectedNode = null
}

function handleSelectionChange(val: any[]) {
  selectedPoints.value = val
}

function confirmDelete(target?: DeviceTemplate | null) {
  const templateToDelete = target || selectedTemplate.value
  if (!templateToDelete?.deviceTmplId) return
  const id = templateToDelete.deviceTmplId

  ElMessageBox.confirm(
    `Are you sure you want to delete template "${templateToDelete.deviceTmplName}"?`,
    'Delete Confirmation',
    {
      confirmButtonText: 'Delete',
      cancelButtonText: 'Cancel',
      type: 'warning',
      confirmButtonClass: 'el-button--danger',
      customClass: 'dark-message-box',
      boxType: 'confirm'
    }
  ).then(async () => {
    loading.value = true
    try {
      await templateApi.deleteTemplate(id)
      ElMessage.success('Deleted successfully')
      await store.refreshAfterMutation()
      store.selectedNode = store.deviceTree[0] || null
    } catch (err: any) {
      ElMessage.error(err.message || 'Failed to delete template')
    } finally {
      loading.value = false
    }
  }).catch(() => {})
}

function deleteTemplateFromTable(row: DeviceTemplate) {
  confirmDelete(row)
}

function batchDeleteTemplates() {
  ElMessageBox.confirm(
    `Are you sure you want to delete ${selectedTemplates.value.length} selected template(s)?`,
    'Batch Delete Confirmation',
    {
      confirmButtonText: 'Delete',
      cancelButtonText: 'Cancel',
      type: 'warning',
      confirmButtonClass: 'el-button--danger',
      customClass: 'dark-message-box',
      boxType: 'confirm'
    }
  ).then(async () => {
    loading.value = true
    try {
      for (const t of selectedTemplates.value) {
        if (t.deviceTmplId) {
          await templateApi.deleteTemplate(t.deviceTmplId)
        }
      }
      ElMessage.success('Batch delete completed successfully')
      await store.refreshAfterMutation()
      selectedTemplates.value = []
      isTemplateBatchManaging.value = false
    } catch (err: any) {
      ElMessage.error(err.message || 'Batch delete failed')
    } finally {
      loading.value = false
    }
  }).catch(() => {})
}

async function loadTemplatePoints() {
  if (!selectedTemplate.value?.deviceTmplId) {
    templatePoints.value = []
    return
  }
  loadingPoints.value = true
  try {
    const list = await templateApi.getPoints(selectedTemplate.value.deviceTmplId)
    templatePoints.value = list || []
  } catch (err: any) {
    ElMessage.error(err.message || 'Failed to load template points')
    templatePoints.value = []
  } finally {
    loadingPoints.value = false
  }
}

function openAddPointDialog() {
  isEditingPoint.value = false
  pointForm.value = {
    pointTmplId: null,
    pointName: '',
    pointType: null,
    dataType: null,
    unit: '',
    displayPrecision: 0
  }
  pointDialogVisible.value = true
  nextTick(() => {
    if (pointFormRef.value) {
      pointFormRef.value.clearValidate()
    }
  })
}

function openEditPointDialog(row: any) {
  isEditingPoint.value = true
  pointForm.value = {
    pointTmplId: row.pointTmplId,
    pointName: row.pointName,
    pointType: row.pointType || null,
    dataType: row.dataType || null,
    unit: row.unit || '',
    displayPrecision: row.displayPrecision ?? 0
  }
  pointDialogVisible.value = true
  nextTick(() => {
    if (pointFormRef.value) {
      pointFormRef.value.clearValidate()
    }
  })
}

async function submitPointForm() {
  if (!pointFormRef.value) return
  const valid = await pointFormRef.value.validate().catch(() => false)
  if (!valid) {
    triggerFormShake(pointFormRef, 'Please check and correct the highlighted point fields')
    return
  }

  const actionText = isEditingPoint.value
    ? `save changes to point "${pointForm.value.pointName}"`
    : `add point "${pointForm.value.pointName}"`
  
  try {
    await ElMessageBox.confirm(
      `Are you sure you want to ${actionText}?`,
      isEditingPoint.value ? 'Confirm Save' : 'Confirm Add',
      {
        confirmButtonText: 'Confirm',
        cancelButtonText: 'Cancel',
        type: 'warning',
        customClass: 'dark-message-box'
      }
    )
  } catch {
    return
  }

  pointSubmitting.value = true
  try {
    const tmplId = selectedTemplate.value?.deviceTmplId
    if (!tmplId) {
      throw new Error('Template not found, please refresh')
    }

    const payload = {
      pointName: pointForm.value.pointName.trim(),
      pointType: pointForm.value.pointType,
      dataType: pointForm.value.dataType,
      unit: pointForm.value.unit ? pointForm.value.unit.trim() : '',
      displayPrecision: pointForm.value.displayPrecision ?? 0
    }

    if (isEditingPoint.value && pointForm.value.pointTmplId) {
      await templateApi.updatePoint(pointForm.value.pointTmplId, payload)
      ElMessage.success('Point updated successfully')
    } else {
      await templateApi.addPoint(tmplId, payload)
      ElMessage.success('Point added successfully')
    }
    pointDialogVisible.value = false
    await loadTemplatePoints()
  } catch (err: any) {
    ElMessage.error(err.message || 'Failed to save point')
  } finally {
    pointSubmitting.value = false
  }
}

async function deletePoint(row: any) {
  try {
    await ElMessageBox.confirm(
      `Are you sure you want to delete point "${row.pointName}"?`,
      'Delete Confirmation',
      {
        confirmButtonText: 'Delete',
        cancelButtonText: 'Cancel',
        type: 'warning',
        confirmButtonClass: 'el-button--danger',
        customClass: 'dark-message-box'
      }
    )
  } catch {
    return
  }

  try {
    await templateApi.deletePoint(row.pointTmplId)
    ElMessage.success('Point deleted successfully')
    await loadTemplatePoints()
  } catch {
    ElMessage.error('Failed to delete point')
  }
}

async function batchDeletePoints() {
  if (!selectedPoints.value.length) return

  try {
    await ElMessageBox.confirm(
      `Are you sure you want to delete ${selectedPoints.value.length} selected point(s)?`,
      'Batch Delete Confirmation',
      {
        confirmButtonText: 'Delete',
        cancelButtonText: 'Cancel',
        type: 'warning',
        confirmButtonClass: 'el-button--danger',
        customClass: 'dark-message-box'
      }
    )
  } catch {
    return
  }

  try {
    for (const p of selectedPoints.value) {
      await templateApi.deletePoint(p.pointTmplId)
    }
    ElMessage.success('Selected points deleted successfully')
  } catch {
    ElMessage.error('Failed to delete some points')
  } finally {
    selectedPoints.value = []
    isPointBatchManaging.value = false
    await loadTemplatePoints()
  }
}

const diagramDialogRef = ref<InstanceType<typeof TemplateDiagramDialog> | null>(null)

function openConfigPanel() {
  diagramDialogRef.value?.open(selectedTemplate.value || form.value)
}

function openConfigPanelWithTarget(target: DeviceTemplate) {
  diagramDialogRef.value?.open(target)
}

const alarmRuleEditModalRef = ref<InstanceType<typeof AlarmRuleEditModal> | null>(null)

function openAlarmRuleEditModal(row: any) {
  alarmRuleEditModalRef.value?.open(row)
}

function openPointAlarmConfig(row: any) {
  openAlarmRuleEditModal(row)
}

async function handleAlarmSaved() {
  await loadTemplatePoints()
  await store.loadTemplates()
}

function handleExport() {
  exportAllTemplatesAndPoints()
}

async function exportAllTemplatesAndPoints() {
  loading.value = true
  try {
    const headers = ['Template Name', 'Device Type', 'Model / Specs', 'Manufacturer', 'Point Name', 'Point Type', 'Data Type', 'Unit', 'Precision']
    const rows: any[] = []

    for (const tpl of store.allTemplates) {
      if (tpl.deviceTmplId === undefined) continue
      try {
        const points = await templateApi.getPoints(tpl.deviceTmplId)
        if (points.length === 0) {
          rows.push([tpl.deviceTmplName || '', tpl.deviceType || '', tpl.model || '', tpl.manufacturer || '',
            '', '', '', '', ''])
        }
        for (const p of points) {
          rows.push([
            tpl.deviceTmplName || '',
            tpl.deviceType || '',
            tpl.model || '',
            tpl.manufacturer || '',
            p.pointName || '',
            p.pointType || '',
            p.dataType || '',
            p.unit || '',
            p.displayPrecision ?? 0
          ])
        }
      } catch (e) {
        console.error(`Failed to fetch points for template ${tpl.deviceTmplId}:`, e)
      }
    }

    let csvContent = "\uFEFF"
    csvContent += [headers.join(','), ...rows.map(r => r.map((val: any) => {
      const escaped = String(val ?? '').replace(/"/g, '""')
      return `"${escaped}"`
    }).join(','))].join('\n')

    const blob = new Blob([csvContent], { type: 'text/csv;charset=utf-8;' })
    const link = document.createElement("a")
    const url = URL.createObjectURL(blob)
    link.setAttribute("href", url)
    link.setAttribute("download", `Device_Templates_and_Points.csv`)
    link.style.visibility = 'hidden'
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    ElMessage.success('Export completed successfully')
  } catch (err: any) {
    ElMessage.error('Export failed: ' + err.message)
  } finally {
    loading.value = false
  }
}

function handleImport() {
  importDialogVisible.value = true
}

function handleImportFileChange(uploadFile: any) {
  const rawFile = uploadFile.raw
  if (rawFile) {
    importAllTemplatesAndPoints(rawFile)
    importDialogVisible.value = false
  }
}

function downloadImportTemplate() {
  const headers = ['Template Name', 'Device Type', 'Model / Specs', 'Manufacturer', 'Point Name', 'Point Type', 'Data Type', 'Unit', 'Precision']
  const rows = [
    ['Sample_Power_Meter', 'EM', 'DTSD-342', 'Instrument Corp', 'Voltage_A', 'AI', 'numeric', 'V', '1'],
    ['Sample_Power_Meter', 'EM', 'DTSD-342', 'Instrument Corp', 'Voltage_B', 'AI', 'numeric', 'V', '1'],
    ['Sample_Power_Meter', 'EM', 'DTSD-342', 'Instrument Corp', 'Communication_Status', 'DI', 'enum', 'None', '0']
  ]

  let csvContent = "\uFEFF"
  csvContent += [headers.join(','), ...rows.map(r => r.map(val => `"${val}"`).join(','))].join('\n')

  const blob = new Blob([csvContent], { type: 'text/csv;charset=utf-8;' })
  const link = document.createElement("a")
  const url = URL.createObjectURL(blob)
  link.setAttribute("href", url)
  link.setAttribute("download", `Device_Point_Import_Template.csv`)
  link.style.visibility = 'hidden'
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  ElMessage.success('Import template downloaded')
}

function importAllTemplatesAndPoints(file: File) {
  const reader = new FileReader()
  reader.onload = async (e: any) => {
    loading.value = true
    try {
      const text = e.target.result
      const lines = text.split('\n').map((l: string) => l.trim()).filter(Boolean)
      if (lines.length <= 1) {
        throw new Error('Invalid format: File is empty or has no data rows')
      }

      let delimiter = ','
      if (lines[1] && lines[1].includes(';')) {
        delimiter = ';'
      }

      const headerLine = lines[0].split(delimiter).map((s: any) => s.replace(/^"|"$/g, '').trim())
      if (headerLine.length < 9) {
        throw new Error('Invalid format: Header must have at least 9 columns')
      }

      const groups = new Map<string, { type: string, model: string, manufacturer: string, points: any[], seenPointNames: Set<string> }>()
      const validationErrors: string[] = []

      for (let i = 1; i < lines.length; i++) {
        const parts = lines[i].split(delimiter).map((s: any) => s.replace(/^"|"$/g, '').trim())
        if (parts.every((p: any) => p === '')) continue

        const rowNum = i + 1
        if (parts.length < 9) {
          validationErrors.push(`Row ${rowNum}: Insufficient columns (expected 9, got ${parts.length})`)
          continue
        }

        const tplName = parts[0]
        if (!tplName || tplName.length < 2 || tplName.length > 100) {
          validationErrors.push(`Row ${rowNum}: Template name must be 2-100 characters`)
        }

        const type = parts[1] || 'EM'
        if (type.length > 50) {
          validationErrors.push(`Row ${rowNum}: Device type cannot exceed 50 characters`)
        }

        const model = (parts[2] || '').slice(0, 100)
        const manufacturer = (parts[3] || '').slice(0, 100)

        const pointName = parts[4]
        if (!pointName || pointName.length < 1 || pointName.length > 150) {
          validationErrors.push(`Row ${rowNum}: Point name must be 1-150 characters`)
        } else if (!/^[a-zA-Z0-9_\-\.\s()]+$/.test(pointName)) {
          validationErrors.push(`Row ${rowNum}: Point name "${pointName}" contains invalid characters`)
        }

        const typeStr = (parts[5] || '').toUpperCase()
        let pointType = 'AI'
        if (typeStr.includes('DI')) pointType = 'DI'
        else if (typeStr.includes('DO')) pointType = 'DO'
        else if (typeStr.includes('AI')) pointType = 'AI'
        else if (typeStr.includes('AO')) pointType = 'AO'
        else {
          validationErrors.push(`Row ${rowNum}: Invalid point type "${parts[5]}", must be DI, DO, AI, or AO`)
        }

        let dataType = (parts[6] || '').toLowerCase()
        const validDataTypes = ['numeric', 'enum', 'boolean', 'string']
        if (!dataType) {
          dataType = (pointType === 'DI' || pointType === 'DO') ? 'enum' : 'numeric'
        } else if (!validDataTypes.includes(dataType)) {
          validationErrors.push(`Row ${rowNum}: Invalid data type "${parts[6]}", must be numeric, enum, boolean, or string`)
        }

        const unit = (parts[7] || '').slice(0, 50)
        const precisionStr = parts[8]
        const displayPrecision = parseInt(precisionStr, 10)
        if (isNaN(displayPrecision) || displayPrecision < 0 || displayPrecision > 4) {
          validationErrors.push(`Row ${rowNum}: Display precision must be an integer between 0 and 4`)
        }

        if (!groups.has(tplName)) {
          groups.set(tplName, {
            type,
            model,
            manufacturer,
            points: [],
            seenPointNames: new Set<string>()
          })
        }

        const group = groups.get(tplName)!
        const lowerPointName = (pointName || '').toLowerCase()
        if (group.seenPointNames.has(lowerPointName)) {
          validationErrors.push(`Row ${rowNum}: Duplicate point name "${pointName}" under template "${tplName}"`)
        } else {
          group.seenPointNames.add(lowerPointName)
        }

        group.points.push({
          pointName,
          pointType,
          dataType,
          unit,
          displayPrecision: isNaN(displayPrecision) ? 0 : Math.max(0, Math.min(4, displayPrecision))
        })
      }

      if (validationErrors.length > 0) {
        const errorSummary = validationErrors.slice(0, 5).join('\n') + (validationErrors.length > 5 ? `\n...and ${validationErrors.length - 5} more error(s)` : '')
        ElMessageBox.alert(errorSummary, 'CSV Validation Failed', {
          type: 'error',
          confirmButtonText: 'OK'
        })
        loading.value = false
        return
      }

      if (groups.size === 0) {
        throw new Error('No valid records parsed from CSV')
      }

      let createdCount = 0
      let updatedCount = 0
      let totalPointsCount = 0

      for (const [tplName, data] of groups.entries()) {
        const template = store.allTemplates.find((t: any) => t.deviceTmplName === tplName)
        let targetTmplId: number | null = null

        if (!template) {
          const created = await templateApi.createTemplate({
            deviceTmplName: tplName,
            deviceType: data.type || 'EM',
            model: data.model || 'N/A',
            manufacturer: data.manufacturer || 'N/A',
            manuDate: new Date().toISOString().split('T')[0]
          })
          createdCount++
          targetTmplId = created.deviceTmplId!
        } else {
          targetTmplId = template.deviceTmplId!
          updatedCount++
        }

        if (targetTmplId) {
          for (const ip of data.points) {
            await templateApi.addPoint(targetTmplId, ip)
            totalPointsCount++
          }
        }
      }

      ElMessage.success(`Import complete: ${createdCount} created, ${updatedCount} updated, ${totalPointsCount} points total.`)
      await store.refreshAfterMutation()

    } catch (err: any) {
      console.error(err)
      ElMessage.error(err.message || 'Import failed')
    } finally {
      loading.value = false
    }
  }
  reader.readAsText(file, 'utf-8')
  return false
}
</script>

<style scoped lang="scss">
$bg-card: #111b27;
$bg-card-hover: #1e293b;
$border-color: #1e293b;
$border-card: #2d3748;
$text-primary: #e5e7eb;
$text-secondary: #9ca3af;
$text-muted: #64748b;
$accent-green: #10b981;
$accent-blue: #3b82f6;
$accent-yellow: #f59e0b;
$accent-red: #ef4444;
$bg-dark: #0b1119;

.product-manager-view {
  height: 100%;
  padding: 0;
}

.card-container {
  background-color: $bg-card;
  border: 1px solid $border-color;
  border-radius: 8px;
  display: flex;
  flex-direction: column;
  height: 100%;
  box-sizing: border-box;
  overflow: hidden;
}

.form-container-wrapper, .table-container-wrapper {
  height: 100%;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.panel-header {
  padding: 8px 12px;
  border-bottom: 1px solid $border-color;
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-shrink: 0;
  min-height: 38px;
}

.header-title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 11px;
  font-weight: 600;
  color: $text-primary;

  .title-icon {
    color: $accent-green;
    font-size: 13px;
  }
}

.detail-card {
  height: 100%;
}

.detail-body {
  padding: 12px;
  flex: 1;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
}

.custom-form {
  max-width: 600px;

  :deep(.el-form-item) {
    margin-bottom: 22px;

    &.is-error {
      margin-bottom: 24px;
    }
  }

  :deep(.el-form-item__label) {
    color: $text-secondary;
    font-weight: 500;
    font-size: 10px !important;
    height: 20px !important;
    line-height: 20px !important;
    padding-right: 6px !important;
  }
  
  :deep(.el-input__wrapper),
  :deep(.el-select__wrapper),
  :deep(.el-date-editor.el-input__wrapper) {
    background-color: $bg-dark !important;
    height: 20px !important;
    min-height: 20px !important;
    line-height: 20px !important;
    padding: 0 6px !important;
    border-radius: 4px !important;
    border: 1px solid $border-color !important;
    box-shadow: none !important;
  }

  :deep(.el-input.is-disabled .el-input__wrapper),
  :deep(.el-select.is-disabled .el-select__wrapper),
  :deep(.el-select__wrapper.is-disabled),
  :deep(.el-date-editor.is-disabled) {
    background-color: rgba(11, 17, 25, 0.85) !important;
    border-color: rgba(30, 41, 59, 0.8) !important;
    box-shadow: none !important;
  }

  :deep(.el-input__inner),
  :deep(.el-select__selected-item),
  :deep(.el-select__placeholder) {
    font-size: 10px !important;
    height: 20px !important;
    line-height: 20px !important;
    color: #e5e7eb !important;
  }

  :deep(.el-input.is-disabled .el-input__inner),
  :deep(.el-select.is-disabled .el-select__selected-item),
  :deep(.el-select__wrapper.is-disabled .el-select__selected-item),
  :deep(.el-date-editor.is-disabled .el-input__inner) {
    color: #94a3b8 !important;
    -webkit-text-fill-color: #94a3b8 !important;
  }

  :deep(.el-input__prefix), :deep(.el-input__suffix), :deep(.el-select__suffix) {
    font-size: 10px !important;
  }
}

.custom-form-compact {
  background-color: rgba($bg-card-hover, 0.25);
  border-radius: 6px;
  padding: 6px 10px 0;
  border: 1px solid $border-color;
  margin-bottom: 8px;
  flex-shrink: 0;

  :deep(.el-form-item) {
    margin-bottom: 6px;
  }
  
  :deep(.el-form-item__label) {
    color: $text-secondary;
    font-weight: 500;
    font-size: 10px !important;
    height: 20px !important;
    line-height: 20px !important;
    padding-right: 6px !important;
  }
  
  :deep(.el-input__wrapper),
  :deep(.el-select__wrapper),
  :deep(.el-date-editor.el-input__wrapper) {
    background-color: $bg-dark !important;
    height: 20px !important;
    min-height: 20px !important;
    line-height: 20px !important;
    padding: 0 6px !important;
    border-radius: 4px !important;
    border: 1px solid $border-color !important;
    box-shadow: none !important;
  }

  :deep(.el-input.is-disabled .el-input__wrapper),
  :deep(.el-select.is-disabled .el-select__wrapper),
  :deep(.el-select__wrapper.is-disabled),
  :deep(.el-date-editor.is-disabled) {
    background-color: rgba(11, 17, 25, 0.85) !important;
    border-color: rgba(30, 41, 59, 0.8) !important;
    box-shadow: none !important;
  }

  :deep(.el-input__inner),
  :deep(.el-select__selected-item),
  :deep(.el-select__placeholder) {
    font-size: 10px !important;
    height: 20px !important;
    line-height: 20px !important;
    color: #e5e7eb !important;
  }

  :deep(.el-input.is-disabled .el-input__inner),
  :deep(.el-select.is-disabled .el-select__selected-item),
  :deep(.el-select__wrapper.is-disabled .el-select__selected-item),
  :deep(.el-date-editor.is-disabled .el-input__inner) {
    color: #94a3b8 !important;
    -webkit-text-fill-color: #94a3b8 !important;
  }

  :deep(.el-input__prefix), :deep(.el-input__suffix), :deep(.el-select__suffix) {
    font-size: 10px !important;
  }
}

.action-buttons {
  display: flex;
  align-items: center;
  gap: 4px;
}

.extra-property-buttons {
  display: flex;
  gap: 6px;
  margin-bottom: 10px;
  padding-bottom: 8px;
  border-bottom: 1px dashed rgba($border-color, 0.6);
  flex-shrink: 0;
}

.points-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 6px;
  flex-shrink: 0;
}

.points-actions {
  display: flex;
  gap: 6px;
}

/* Table styles matching AlarmTable.vue EXACTLY */
.table-body {
  padding: 8px 12px;
  flex: 1;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.search-box-table {
  width: 240px;
  flex-shrink: 0;
  
  :deep(.el-input__wrapper) {
    background-color: $bg-dark !important;
    transition: all 0.2s ease;
    
    &:hover, &.is-focus {
      box-shadow: 0 0 0 1px rgba($accent-green, 0.5) inset !important;
    }
  }
}

.custom-table {
  --el-table-border-color: #1f2937;
  --el-table-header-bg-color: #0d1117;
  --el-table-text-color: #e5e7eb;
  --el-table-header-text-color: #e5e7eb;
  
  font-size: 10px !important;

  .el-table .cell {
    line-height: 20px;
  }

  .el-table__inner-wrapper {
    background-color: #111827;
  }

  .el-table__body-wrapper {
    background-color: #111827;
  }

  .el-table__header-wrapper {
    background-color: #0d1117;
  }

  .el-table__row {
    background-color: #111827;
  }

  .el-table__row:hover > td {
    background-color: #1a2332 !important;
  }

  td, th {
    border-bottom: 1px solid #1f2937;
  }

  // Exact AlarmTable.vue compact row heights & paddings
  :deep(.el-table__cell) {
    padding: 0 0 !important;
  }
  :deep(.el-table__cell .cell) {
    padding: 0 4px !important;
  }
  :deep(.el-table__header-cell .cell),
  :deep(th.el-table__cell .cell) {
    padding: 0 4px !important;
    line-height: 20px !important;
    height: 20px !important;
    font-size: 10px !important;
    display: flex;
    align-items: center;
    justify-content: center !important;
    text-align: center !important;
  }
  :deep(td.el-table__cell .cell) {
    padding: 0 4px !important;
    line-height: 20px !important;
    height: 20px !important;
    font-size: 10px !important;
    display: flex;
    align-items: center;
    justify-content: flex-start;
    text-align: left !important;
  }
  :deep(td.el-table__cell.is-center .cell) {
    justify-content: center !important;
    text-align: center !important;
  }
}

.btn-back {
  margin-left: 20px !important;
}

.alarm-rule-cell {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  width: 100%;
  padding: 0 4px;

  .alarm-btn-group {
    display: inline-flex;
    align-items: center;
    gap: 4px;
    margin-left: auto;
  }
}

.point-table-section {
  margin-top: 8px;
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.section-title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 11px;
  font-weight: 600;
  color: $text-primary;

  .section-icon {
    color: $accent-green;
    font-size: 13px;
  }
}

/* Compact Rounded borderless tags matching data type style */
.type-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 0 6px;
  height: 16px;
  border-radius: 8px;
  font-size: 10px;
  font-weight: 400;
  line-height: 16px;
  border: none !important;
  transition: all 0.2s ease-in-out;

  &.type-numeric {
    background-color: rgba($accent-blue, 0.18);
    color: #93c5fd;
  }
  &.type-enum {
    background-color: rgba($accent-yellow, 0.18);
    color: #fde047;
  }
  &.type-string {
    background-color: rgba(168, 85, 247, 0.18);
    color: #d8b4fe;
  }
}

.alarm-code-tag {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 0 6px;
  height: 16px;
  border-radius: 8px;
  font-size: 10px;
  font-weight: 400;
  line-height: 16px;
  border: none !important;
  background-color: rgba($accent-red, 0.22) !important;
  color: #fca5a5 !important;
  cursor: pointer;
  transition: all 0.2s ease-in-out;

  &:hover {
    background-color: rgba($accent-red, 0.35) !important;
    color: #ffffff !important;
    transform: translateY(-0.5px);
  }
}

.alarm-none-tag {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 0 6px;
  height: 16px;
  border-radius: 8px;
  font-size: 10px;
  font-weight: 400;
  line-height: 16px;
  border: none !important;
  background-color: rgba(100, 116, 139, 0.14) !important;
  color: #94a3b8 !important;
  cursor: pointer;
  transition: all 0.2s ease-in-out;

  &:hover {
    background-color: rgba($accent-blue, 0.20) !important;
    color: #93c5fd !important;
    transform: translateY(-0.5px);
  }
}

// Button styling lives in styles/global.scss (app-wide unified look).
</style>
