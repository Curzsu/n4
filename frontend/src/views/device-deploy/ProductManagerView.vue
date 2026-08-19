<template>
  <div class="product-manager-view" v-loading="loading">
    <div class="card-container detail-card">
      <!-- Case 1: Creating a new template -->
      <div v-if="isCreating" class="form-container-wrapper">
        <div class="panel-header">
          <div class="header-title">
            <el-icon class="title-icon"><Plus /></el-icon>
            <span>Add Device Template</span>
          </div>
          <el-button size="small" class="btn-secondary" @click="cancelCreate">Back to List</el-button>
        </div>
        <div class="detail-body">
          <el-form 
            :model="form" 
            :rules="rules" 
            ref="formRef" 
            label-width="120px"
            label-position="right"
            class="custom-form"
          >
            <el-form-item label="Template Name" prop="deviceTmplName">
              <el-input v-model="form.deviceTmplName" placeholder="e.g. Row Precision AC" />
            </el-form-item>
            
            <el-form-item label="Template Type" prop="deviceType">
              <el-input v-model="form.deviceType" placeholder="e.g. AC, UPS, TRANSFORMER" />
            </el-form-item>

            <el-form-item label="Model / Specs" prop="model">
              <el-input v-model="form.model" placeholder="e.g. DK-1200XT" />
            </el-form-item>

            <el-form-item label="Manufacturer" prop="manufacturer">
              <el-input v-model="form.manufacturer" placeholder="e.g. Daikin Industries" />
            </el-form-item>

            <el-form-item label="Manufacture Date" prop="manuDate">
              <el-date-picker
                v-model="form.manuDate"
                type="date"
                placeholder="Select date"
                format="YYYY-MM-DD"
                value-format="YYYY-MM-DD"
                style="width: 100%"
              />
            </el-form-item>
            
            <el-form-item>
              <el-button type="primary" class="btn-primary" @click="submitCreate" :loading="submitting">Create Template</el-button>
              <el-button class="btn-secondary" @click="cancelCreate">Cancel</el-button>
            </el-form-item>
          </el-form>

          <!-- Local Temp Points Table for New Template -->
          <div class="point-table-section" style="margin-top: 24px; border-top: 1px dashed #334155; padding-top: 16px;">
            <div class="points-header">
              <div class="section-title">
                <el-icon class="section-icon"><List /></el-icon>
                <span>Initial Points Configuration (Total: {{ tempPoints.length }} points)</span>
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
                  <el-button type="primary" size="small" class="btn-primary" :icon="Plus" @click="openAddPointDialog">Add Point</el-button>
                </template>
              </div>
            </div>
            
            <el-table 
              :data="tempPoints" 
              style="width: 100%; margin-top: 10px;" 
              border 
              size="small"
              class="custom-table"
              :header-cell-style="headerCellStyle"
              :cell-style="cellStyle"
              @selection-change="handleSelectionChange"
            >
              <el-table-column type="selection" width="30" v-if="isPointBatchManaging" :resizable="false" />
              <el-table-column prop="pointName" label="Point Name" min-width="180" show-overflow-tooltip :resizable="false" />
              <el-table-column prop="dataType" label="Data Type" width="110" :resizable="false" />
              <el-table-column prop="unit" label="Unit" width="90" :resizable="false" />
              <el-table-column prop="displayPrecision" label="Precision" width="90" :resizable="false" />
              <el-table-column label="Actions" width="120" fixed="right" :resizable="false">
                <template #default="{ row }">
                  <el-button size="small" type="primary" link :icon="Edit" @click="openEditPointDialog(row)">Edit</el-button>
                  <el-button size="small" type="danger" link :icon="Delete" @click="deletePoint(row)">Delete</el-button>
                </template>
              </el-table-column>
            </el-table>
          </div>
        </div>
      </div>

      <!-- Case 2: Editing an existing template -->
      <div v-else-if="selectedTemplate" class="form-container-wrapper">
        <div class="panel-header">
          <div class="header-title">
            <el-icon class="title-icon"><Edit /></el-icon>
            <span>Device Template - {{ selectedTemplate.deviceTmplName }}</span>
          </div>
          <div class="action-buttons">
            <el-button size="small" class="btn-secondary" :icon="Odometer" @click="openConfigPanel">
              Set Faceplate Panel
            </el-button>
            <el-button size="small" class="btn-secondary" @click="goBackToList">Back to List</el-button>
            <el-button v-if="!isEditing" size="small" class="btn-secondary" :icon="Edit" @click="startEdit">Edit Template</el-button>
            <el-button v-else size="small" class="btn-primary" :icon="Check" :loading="submitting" @click="submitUpdate">Save</el-button>
            <el-button 
              size="small" 
              class="btn-danger"
              :icon="Delete"
              @click="confirmDelete(selectedTemplate)"
            >
              Delete Template
            </el-button>
          </div>
        </div>

        <div class="detail-body">
          <el-form 
            :model="form" 
            :rules="rules" 
            ref="formRef" 
            label-width="110px"
            label-position="right"
            class="custom-form-compact"
            :disabled="!isEditing"
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
              <el-table-column type="selection" width="30" v-if="isPointBatchManaging" :resizable="false" />
              <el-table-column prop="pointTmplId" label="ID" width="55" :resizable="false" />
              <el-table-column prop="pointName" label="Point Name" min-width="180" show-overflow-tooltip :resizable="false" />
              <el-table-column prop="dataType" label="Data Type" width="110" :resizable="false" />
              <el-table-column prop="unit" label="Unit" width="90" :resizable="false" />
              <el-table-column prop="displayPrecision" label="Precision" width="90" :resizable="false" />
              <el-table-column label="Alarm Rule" min-width="160" :resizable="false">
                <template #default="{ row }">
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
                </template>
              </el-table-column>
              <el-table-column label="Actions" width="120" fixed="right" :resizable="false">
                <template #default="{ row }">
                  <el-button size="small" type="primary" link :icon="Edit" @click="openAlarmRuleEditModal(row)">Edit</el-button>
                  <el-button size="small" type="danger" link :icon="Delete" @click="deletePoint(row)">Delete</el-button>
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
                @click="startCreate"
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
            <el-table-column v-if="isTemplateBatchManaging" type="selection" width="30" :resizable="false" />
            <el-table-column prop="deviceTmplId" label="ID" width="55" :resizable="false" />
            <el-table-column prop="deviceTmplName" label="Template Name" min-width="180" show-overflow-tooltip :resizable="false" />
            <el-table-column prop="deviceType" label="Type" width="110" show-overflow-tooltip :resizable="false" />
            <el-table-column label="Model / Specs" min-width="130" show-overflow-tooltip :resizable="false">
              <template #default="{ row }">
                <span>{{ row.model || '—' }}</span>
              </template>
            </el-table-column>
            <el-table-column label="Manufacturer" min-width="140" show-overflow-tooltip :resizable="false">
              <template #default="{ row }">
                <span>{{ row.manufacturer || '—' }}</span>
              </template>
            </el-table-column>
            <el-table-column label="Manufacture Date" width="110" :resizable="false">
              <template #default="{ row }">
                <span>{{ row.manuDate || '—' }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="pointCount" label="Points" width="70" :resizable="false" />
            <el-table-column label="Actions" width="160" fixed="right" :resizable="false">
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

    <!-- Add/Edit Point Dialog -->
    <el-dialog
      v-model="pointDialogVisible"
      :title="isEditingPoint ? 'Edit Point' : 'Add Point'"
      width="500px"
      append-to-body
      class="dark-dialog"
    >
      <el-form :model="pointForm" :rules="pointRules" ref="pointFormRef" label-width="110px">
        <el-form-item label="Point Name" prop="pointName">
          <el-input v-model="pointForm.pointName" placeholder="e.g. Temperature" />
        </el-form-item>
        <el-form-item label="Point Type" prop="pointType">
          <el-select v-model="pointForm.pointType" placeholder="Select type" style="width: 100%">
            <el-option value="DI" label="DI - Digital Input" />
            <el-option value="DO" label="DO - Digital Output" />
            <el-option value="AI" label="AI - Analog Input" />
            <el-option value="AO" label="AO - Analog Output" />
          </el-select>
        </el-form-item>
        <el-form-item label="Data Type" prop="dataType">
          <el-select v-model="pointForm.dataType" placeholder="Select data type" style="width: 100%">
            <el-option value="numeric" label="numeric - Number" />
            <el-option value="enum" label="enum - Enumeration" />
            <el-option value="boolean" label="boolean - Boolean" />
            <el-option value="string" label="string - String" />
          </el-select>
        </el-form-item>
        <el-form-item label="Unit" prop="unit">
          <el-input v-model="pointForm.unit" placeholder="e.g. ℃, %, V, None" />
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
import { Plus, Edit, Check, Delete, List, Odometer, Download, Upload, UploadFilled, Search, Picture } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { templateApi } from '@/api/template'
import type { DeviceTemplate, PointTemplateItem } from '@/api/types'
import type { FormInstance, FormRules } from 'element-plus'
import { useDashboardStore } from '@/stores/dashboard'
import TemplateDiagramDialog from './dialogs/TemplateDiagramDialog.vue'
import AlarmRuleEditModal from './dialogs/AlarmRuleEditModal.vue'

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
  textAlign: 'left',
}

const cellStyle: CSSProperties = {
  backgroundColor: '#111827',
  color: '#e5e7eb',
  fontSize: '10px',
  borderBottom: '1px solid #1f2937',
  textAlign: 'left',
}

const loading = ref(false)
const submitting = ref(false)
const tableSearchQuery = ref('')
const isCreating = ref(false)
const isEditing = ref(false)
const selectedPoints = ref<any[]>([])
const isPointBatchManaging = ref(false)
const isTemplateBatchManaging = ref(false)
const selectedTemplates = ref<any[]>([])
const importDialogVisible = ref(false)
const tempPoints = ref<any[]>([])

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
  pointType: 'AI',
  dataType: 'numeric',
  unit: 'None',
  displayPrecision: 0
})

const validatePointNameUnique = (_rule: any, value: any, callback: any) => {
  if (!value) {
    callback(new Error('Please enter point name'))
    return
  }
  const list = isCreating.value ? tempPoints.value : templatePoints.value
  const exists = list.some(p => {
    if (isEditingPoint.value && p.pointTmplId === pointForm.value.pointTmplId) {
      return false
    }
    return p.pointName === value
  })
  if (exists) {
    callback(new Error('Point name already exists'))
    return
  }
  callback()
}

const pointRules = ref<FormRules>({
  pointName: [
    { validator: validatePointNameUnique, trigger: 'blur' },
    { max: 150, message: 'Point name cannot exceed 150 characters', trigger: 'blur' }
  ],
  pointType: [
    { required: true, message: 'Please select point type', trigger: 'change' }
  ],
  dataType: [
    { required: true, message: 'Please select data type', trigger: 'change' }
  ],
  unit: [
    { max: 50, message: 'Unit cannot exceed 50 characters', trigger: 'blur' }
  ]
})

const formRef = ref<FormInstance>()

const form = ref<DeviceTemplate>({
  deviceTmplName: '',
  deviceType: '',
  model: '',
  manufacturer: '',
  manuDate: ''
})

const rules = ref<FormRules>({
  deviceTmplName: [
    { required: true, message: 'Please enter template name', trigger: 'blur' },
    { min: 2, max: 100, message: 'Length must be between 2 and 100 characters', trigger: 'blur' }
  ],
  deviceType: [
    { required: true, message: 'Please enter template type', trigger: 'blur' }
  ]
})

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
    isEditing.value = false
    await loadTemplatePoints()
  } else {
    isCreating.value = false
    templatePoints.value = []
  }
}, { immediate: true })

const filteredTableTemplates = computed(() => {
  const query = tableSearchQuery.value.trim().toLowerCase()
  if (!query) return templates.value
  return templates.value.filter(t => t.deviceTmplName.toLowerCase().includes(query))
})

function editTemplateFromTable(row: DeviceTemplate) {
  isCreating.value = false
  store.selectedNode = {
    id: row.deviceTmplId,
    label: row.deviceTmplName,
    type: 'product',
    status: 1,
    children: []
  }
}

function startCreate() {
  isCreating.value = true
  tempPoints.value = []
  store.selectedNode = store.deviceTree[0] || null
  form.value = {
    deviceTmplName: '',
    deviceType: '',
    model: '',
    manufacturer: '',
    manuDate: ''
  }
}

function cancelCreate() {
  isCreating.value = false
}

function goBackToList() {
  store.selectedNode = null
}

function startEdit() {
  isEditing.value = true
}

function handleSelectionChange(val: any[]) {
  selectedPoints.value = val
}

async function submitCreate() {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (valid) {
      submitting.value = true
      try {
        const createdTemplate = await templateApi.createTemplate(form.value)

        for (const ip of tempPoints.value) {
          await templateApi.addPoint(createdTemplate.deviceTmplId!, {
            pointName: ip.pointName,
            pointType: ip.pointType,
            dataType: ip.dataType,
            unit: ip.unit,
            displayPrecision: ip.displayPrecision
          })
        }

        ElMessage.success('Template and points created successfully')
        isCreating.value = false
        await store.refreshAfterMutation()

        store.selectedNode = {
          id: createdTemplate.deviceTmplId,
          label: createdTemplate.deviceTmplName,
          type: 'product',
          status: 1,
          children: []
        }

        await nextTick()
        isEditing.value = true
      } catch (err: any) {
        ElMessage.error(err.message || 'Failed to create template')
      } finally {
        submitting.value = false
      }
    }
  })
}

async function submitUpdate() {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (valid) {
      submitting.value = true
      try {
        if (form.value.deviceTmplId) {
          await templateApi.updateTemplate(form.value.deviceTmplId, form.value)
          ElMessage.success('Template saved successfully')
          isEditing.value = false
          await store.refreshAfterMutation()
        } else {
          ElMessage.error('Missing template ID')
        }
      } catch (err: any) {
        ElMessage.error(err.message || 'Failed to update template')
      } finally {
        submitting.value = false
      }
    }
  })
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
      boxType: 'confirm'
    }
  ).then(async () => {
    loading.value = true
    try {
      for (const t of selectedTemplates.value) {
        await templateApi.deleteTemplate(t.deviceTmplId)
      }
      ElMessage.success('Batch delete completed successfully')
    } catch (err: any) {
      ElMessage.error(err.message || 'Failed to batch delete templates')
    } finally {
      selectedTemplates.value = []
      isTemplateBatchManaging.value = false
      await store.refreshAfterMutation()
      store.selectedNode = store.deviceTree[0] || null
      loading.value = false
    }
  }).catch(() => {})
}

async function loadTemplatePoints() {
  if (selectedTemplate.value?.deviceTmplId) {
    loadingPoints.value = true
    try {
      templatePoints.value = await templateApi.getPoints(selectedTemplate.value.deviceTmplId)
    } catch (e: any) {
      console.error(e)
      ElMessage.error(e.message || 'Failed to load points')
    } finally {
      loadingPoints.value = false
    }
  }
}

function openAddPointDialog() {
  isEditingPoint.value = false
  pointForm.value = {
    pointTmplId: null,
    pointName: '',
    pointType: 'AI',
    dataType: 'numeric',
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
    pointType: row.pointType || 'AI',
    dataType: row.dataType || 'numeric',
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
  await pointFormRef.value.validate(async (valid) => {
    if (valid) {
      if (isCreating.value) {
        if (isEditingPoint.value) {
          const index = tempPoints.value.findIndex(p => p.pointTmplId === pointForm.value.pointTmplId)
          if (index !== -1) {
            tempPoints.value[index] = { ...pointForm.value }
          }
          ElMessage.success('Point updated')
        } else {
          const tempId = Math.floor(Math.random() * -1000000)
          tempPoints.value.push({
            ...pointForm.value,
            pointTmplId: tempId
          })
          ElMessage.success('Point added')
        }
        pointDialogVisible.value = false
        return
      }

      pointSubmitting.value = true
      try {
        const tmplId = selectedTemplate.value?.deviceTmplId
        if (!tmplId) {
          throw new Error('Template not found, please refresh')
        }

        if (isEditingPoint.value && pointForm.value.pointTmplId) {
          await templateApi.updatePoint(pointForm.value.pointTmplId, {
            pointName: pointForm.value.pointName,
            pointType: pointForm.value.pointType,
            dataType: pointForm.value.dataType,
            unit: pointForm.value.unit,
            displayPrecision: pointForm.value.displayPrecision
          })
          ElMessage.success('Point updated successfully')
        } else {
          await templateApi.addPoint(tmplId, {
            pointName: pointForm.value.pointName,
            pointType: pointForm.value.pointType,
            dataType: pointForm.value.dataType,
            unit: pointForm.value.unit,
            displayPrecision: pointForm.value.displayPrecision
          })
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
  })
}

async function deletePoint(row: any) {
  if (isCreating.value) {
    tempPoints.value = tempPoints.value.filter(p => p.pointTmplId !== row.pointTmplId)
    ElMessage.success('Point deleted')
    return
  }

  ElMessageBox.confirm(`Are you sure you want to delete point "${row.pointName}"?`, 'Delete Confirmation', {
    confirmButtonText: 'Delete',
    cancelButtonText: 'Cancel',
    type: 'warning'
  }).then(async () => {
    try {
      await templateApi.deletePoint(row.pointTmplId)
      ElMessage.success('Point deleted successfully')
      await loadTemplatePoints()
    } catch {
      ElMessage.error('Failed to delete point')
    }
  }).catch(() => {})
}

async function batchDeletePoints() {
  if (isCreating.value) {
    tempPoints.value = tempPoints.value.filter(p => !selectedPoints.value.includes(p))
    ElMessage.success('Batch delete completed')
    selectedPoints.value = []
    isPointBatchManaging.value = false
    return
  }

  ElMessageBox.confirm('Are you sure you want to delete all selected points?', 'Batch Delete Confirmation', {
    confirmButtonText: 'Delete',
    cancelButtonText: 'Cancel',
    type: 'warning'
  }).then(async () => {
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
  }).catch(() => {})
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

      const groups = new Map<string, { type: string, model: string, manufacturer: string, points: any[] }>()

      for (let i = 1; i < lines.length; i++) {
        const parts = lines[i].split(delimiter).map((s: any) => s.replace(/^"|"$/g, '').trim())
        if (parts.every((p: any) => p === '')) continue

        if (parts.length < 9) {
          throw new Error(`Row ${i + 1} has insufficient columns`)
        }

        const tplName = parts[0]
        if (!tplName) {
          throw new Error(`Row ${i + 1}: Template name cannot be empty`)
        }

        const type = parts[1]
        const model = parts[2]
        const manufacturer = parts[3]

        const pointName = parts[4]
        if (!pointName) {
          throw new Error(`Row ${i + 1}: Point name cannot be empty`)
        }

        const typeStr = parts[5] || ''
        let pointType = 'AI'
        if (typeStr.toUpperCase().includes('DI')) pointType = 'DI'
        else if (typeStr.toUpperCase().includes('DO')) pointType = 'DO'
        else if (typeStr.toUpperCase().includes('AI')) pointType = 'AI'
        else if (typeStr.toUpperCase().includes('AO')) pointType = 'AO'

        let dataType = parts[6] || ''
        if (!dataType) {
          dataType = (pointType === 'DI' || pointType === 'DO') ? 'enum' : 'numeric'
        }

        const unit = parts[7] || ''
        const precisionStr = parts[8]
        const displayPrecision = parseInt(precisionStr) || 0

        if (!groups.has(tplName)) {
          groups.set(tplName, {
            type,
            model,
            manufacturer,
            points: []
          })
        }

        groups.get(tplName)!.points.push({
          pointName,
          pointType,
          dataType,
          unit,
          displayPrecision
        })
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
  gap: 6px;
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
  :deep(.el-table__header-cell) {
    padding: 0 4px !important;
  }
  :deep(td.el-table__cell .cell),
  :deep(th.el-table__cell .cell) {
    padding: 0 4px !important;
    line-height: 20px !important;
    height: 20px !important;
    font-size: 10px !important;
    display: flex;
    align-items: center;
    justify-content: flex-start;
    text-align: left !important;
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

/* ========================================================== */
/* BORDERLESS TRANSLUCENT CAPSULE BUTTONS                     */
/* ========================================================== */
:deep(.el-button) {
  border-radius: 9px !important;
  border: none !important;
  font-size: 10px !important;
  font-weight: 400 !important;
  padding: 2px 8px !important;
  height: 20px !important;
  line-height: 16px !important;
  transition: all 0.2s ease-in-out;

  &:hover {
    transform: translateY(-0.5px);
  }

  &.is-link {
    background-color: transparent !important;
    padding: 0 2px !important;
    border-radius: 4px !important;
    height: auto !important;
  }
}

/* Primary / Green buttons */
.btn-primary, :deep(.el-button--primary:not(.is-link)) {
  background-color: rgba($accent-green, 0.16) !important;
  color: #34d399 !important;

  &:hover {
    background-color: rgba($accent-green, 0.28) !important;
    color: #6ee7b7 !important;
  }
}

/* Secondary / Default / Blue buttons */
.btn-secondary, :deep(.el-button--default:not(.is-link)), :deep(.el-button--info:not(.is-link)) {
  background-color: rgba($accent-blue, 0.16) !important;
  color: #93c5fd !important;

  &:hover {
    background-color: rgba($accent-blue, 0.28) !important;
    color: #bfdbfe !important;
  }
}

/* Danger / Delete / Red buttons */
.btn-danger, :deep(.el-button--danger:not(.is-link)) {
  background-color: rgba($accent-red, 0.16) !important;
  color: #fca5a5 !important;

  &:hover {
    background-color: rgba($accent-red, 0.28) !important;
    color: #fecaca !important;
  }
}
</style>
