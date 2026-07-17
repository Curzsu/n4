<template>
  <div class="product-manager-view" v-loading="loading">
    <div class="card-container detail-card">
      <!-- Case 1: Creating a new template -->
      <div v-if="isCreating" class="form-container-wrapper">
        <div class="panel-header">
          <div class="header-title">
            <el-icon class="title-icon"><Plus /></el-icon>
            <span>新增设备模板</span>
          </div>
          <el-button size="small" @click="cancelCreate">返回列表</el-button>
        </div>
        <div class="detail-body">
          <el-form 
            :model="form" 
            :rules="rules" 
            ref="formRef" 
            label-width="100px"
            label-position="right"
            class="custom-form"
          >
            <el-form-item label="模板名称" prop="productName">
              <el-input v-model="form.productName" placeholder="例如：大金列间空调" />
            </el-form-item>
            
            <el-form-item label="模板类型" prop="productType">
              <el-input v-model="form.productType" placeholder="例如：AC (空调), UPS, TRANSFORMER" />
            </el-form-item>

            <el-form-item label="型号规格" prop="model">
              <el-input v-model="form.model" placeholder="例如：DK-1200XT" />
            </el-form-item>

            <el-form-item label="制造厂商" prop="manufacturer">
              <el-input v-model="form.manufacturer" placeholder="例如：大金工业株式会社" />
            </el-form-item>

            <el-form-item label="制造日期" prop="manuDate">
              <el-date-picker
                v-model="form.manuDate"
                type="date"
                placeholder="选择出厂日期"
                format="YYYY-MM-DD"
                value-format="YYYY-MM-DD"
                style="width: 100%"
              />
            </el-form-item>
            
            <el-form-item>
              <el-button type="primary" @click="submitCreate" :loading="submitting">立即创建</el-button>
              <el-button @click="cancelCreate">取消</el-button>
            </el-form-item>
          </el-form>

          <!-- Local Temp Points Table for New Template -->
          <div class="point-table-section" style="margin-top: 30px; border-top: 1px dashed #334155; padding-top: 20px;">
            <div class="points-header">
              <div class="section-title">
                <el-icon class="section-icon"><List /></el-icon>
                <span>配置初始点位表 (共 {{ tempPoints.length }} 个点位，创建模板时将同步保存)</span>
              </div>
              <div class="points-actions">
                <!-- 批量管理状态 -->
                <template v-if="isPointBatchManaging">
                  <el-button 
                    type="danger" 
                    size="small" 
                    :icon="Delete" 
                    :disabled="selectedPoints.length === 0" 
                    @click="batchDeletePoints"
                  >
                    批量删除 (已选 {{ selectedPoints.length }} 个)
                  </el-button>
                  <el-button 
                    size="small"
                    @click="exitPointBatchManage"
                  >
                    退出管理
                  </el-button>
                </template>
                <!-- 常规状态 -->
                <template v-else>
                  <el-button 
                    size="small" 
                    :icon="Edit"
                    @click="enterPointBatchManage"
                  >
                    批量管理
                  </el-button>
                  <el-button type="primary" size="small" :icon="Plus" @click="openAddPointDialog">新增点位</el-button>
                </template>
              </div>
            </div>
            
            <el-table 
              :data="tempPoints" 
              style="width: 100%; margin-top: 12px;" 
              border 
              size="small"
              class="custom-table"
              @selection-change="handleSelectionChange"
            >
              <el-table-column type="selection" width="45" align="center" v-if="isPointBatchManaging" />
              <el-table-column prop="registerId" label="寄存器地址" min-width="160" align="center" />
              <el-table-column prop="pointName" label="点位名称" min-width="200" show-overflow-tooltip />
              <el-table-column prop="pointType" label="点位类型" min-width="140">
                <template #default="{ row }">
                  <span :class="['type-badge', getPointTypeClass(row.pointType)]">
                    {{ getPointTypeText(row.pointType) }}
                  </span>
                </template>
              </el-table-column>
              <el-table-column prop="unit" label="单位" width="100" align="center" />
              <el-table-column prop="displayPrecision" label="精度" width="90" align="center" />
              <el-table-column label="操作" width="160" fixed="right" align="center">
                <template #default="{ row }">
                  <el-button size="small" type="primary" link :icon="Edit" @click="openEditPointDialog(row)">修改</el-button>
                  <el-button size="small" type="danger" link :icon="Delete" @click="deletePoint(row)">删除</el-button>
                </template>
              </el-table-column>
            </el-table>
          </div>
        </div>
      </div>

      <!-- Case 2: Editing an existing template -->
      <div v-else-if="selectedProduct" class="form-container-wrapper">
        <div class="panel-header">
          <div class="header-title">
            <el-icon class="title-icon"><Edit /></el-icon>
            <span>设备模板 - {{ selectedProduct?.productName }}</span>
          </div>
          <div class="action-buttons">
            <el-button type="primary" plain size="small" @click="goBackToList">返回列表</el-button>
            <el-button v-if="!isEditing" type="primary" plain size="small" :icon="Edit" @click="startEdit">修改设备模板信息</el-button>
            <el-button v-else type="primary" plain size="small" :icon="Check" :loading="submitting" @click="submitUpdate">保存</el-button>
            <el-button 
              type="danger" 
              size="small" 
              :icon="Delete"
              plain
              @click="confirmDelete(selectedProduct)"
            >
              删除模板
            </el-button>
          </div>
        </div>

        <div class="detail-body">
          <el-form 
            :model="form" 
            :rules="rules" 
            ref="formRef" 
            label-width="80px"
            label-position="right"
            class="custom-form-compact"
            :disabled="!isEditing"
          >
            <el-row :gutter="20">
              <el-col :span="8">
                <el-form-item label="模板名称" prop="productName">
                  <el-input v-model="form.productName" />
                </el-form-item>
              </el-col>
              <el-col :span="8">
                <el-form-item label="模板类型" prop="productType">
                  <el-input v-model="form.productType" />
                </el-form-item>
              </el-col>
              <el-col :span="8">
                <el-form-item label="型号规格" prop="model">
                  <el-input v-model="form.model" />
                </el-form-item>
              </el-col>
            </el-row>
            <el-row :gutter="20">
              <el-col :span="12">
                <el-form-item label="制造厂商" prop="manufacturer">
                  <el-input v-model="form.manufacturer" />
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="制造日期" prop="manuDate">
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

          <!-- Extra device-level property buttons -->
          <div class="extra-property-buttons">
            <el-button type="primary" plain size="small" :icon="Odometer" @click="openConfigPanel">
              设备组态面板设置
            </el-button>
            <el-button type="primary" plain size="small" :icon="Warning" @click="openDeviceAlarmConfig">
              设备级报警配置
            </el-button>
          </div>

          <!-- Device Template Points Table -->
          <div class="point-table-section" v-loading="loadingPoints">
            <div class="points-header">
              <div class="section-title">
                <el-icon class="section-icon"><List /></el-icon>
                <span>对应点位表 (共 {{ templatePoints.length }} 个点位)</span>
              </div>
              <div class="points-actions">
                <!-- 批量管理状态 -->
                <template v-if="isPointBatchManaging">
                  <el-button 
                    type="danger" 
                    size="small" 
                    :icon="Delete" 
                    :disabled="selectedPoints.length === 0" 
                    @click="batchDeletePoints"
                  >
                    批量删除 (已选 {{ selectedPoints.length }} 个)
                  </el-button>
                  <el-button 
                    size="small"
                    @click="exitPointBatchManage"
                  >
                    退出管理
                  </el-button>
                </template>
                <!-- 常规状态 -->
                <template v-else>
                  <el-button 
                    type="primary"
                    plain
                    size="small" 
                    :icon="Edit"
                    @click="enterPointBatchManage"
                  >
                    批量管理
                  </el-button>
                  <el-button type="primary" plain size="small" :icon="Plus" @click="openAddPointDialog">新增点位</el-button>
                </template>
              </div>
            </div>
            
            <el-table 
              :data="templatePoints" 
              style="width: 100%; margin-top: 12px;" 
              border 
              size="small"
              class="custom-table"
              @selection-change="handleSelectionChange"
            >
              <el-table-column type="selection" width="45" align="center" v-if="isPointBatchManaging" />
              <el-table-column prop="deviceId" label="设备ID" min-width="180" show-overflow-tooltip />
              <el-table-column prop="pointName" label="点位名称" min-width="200" show-overflow-tooltip />
              <el-table-column prop="pointType" label="点位类型" min-width="140">
                <template #default="{ row }">
                  <span :class="['type-badge', getPointTypeClass(row.pointType)]">
                    {{ getPointTypeText(row.pointType) }}
                  </span>
                </template>
              </el-table-column>
              <el-table-column prop="unit" label="单位" width="100" align="center" />
              <el-table-column prop="displayPrecision" label="精度" width="90" align="center" />
              <el-table-column label="操作" width="220" fixed="right" align="center">
                <template #default="{ row }">
                  <el-button size="small" type="primary" link :icon="Edit" @click="openEditPointDialog(row)">修改</el-button>
                  <el-button size="small" type="danger" link :icon="Delete" @click="deletePoint(row)">删除</el-button>
                  <el-button size="small" type="primary" link :icon="Warning" @click="openPointAlarmConfig(row)">报警设置</el-button>
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
            <span>全部设备模板列表</span>
          </div>
          <div class="action-buttons">
            <!-- 批量管理状态 -->
            <template v-if="isTemplateBatchManaging">
              <el-button 
                type="danger" 
                size="small" 
                :icon="Delete"
                :disabled="selectedTemplates.length === 0"
                @click="batchDeleteTemplates"
              >
                批量删除 (已选 {{ selectedTemplates.length }} 个)
              </el-button>
              <el-button 
                size="small"
                @click="exitTemplateBatchManage"
              >
                退出管理
              </el-button>
            </template>
            <!-- 常规状态 -->
            <template v-else>
              <el-button 
                type="primary"
                plain
                size="small" 
                :icon="Edit"
                @click="enterTemplateBatchManage"
              >
                批量管理
              </el-button>
              <el-button 
                type="primary" 
                plain
                size="small" 
                :icon="Plus"
                @click="startCreate"
              >
                新增模板
              </el-button>
              <el-button type="primary" plain size="small" :icon="Download" @click="handleImport">导入</el-button>
              <el-button type="primary" plain size="small" :icon="Upload" @click="handleExport">导出</el-button>
            </template>
          </div>
        </div>

        <div class="table-body">
          <div class="search-box-table">
            <el-input
              v-model="tableSearchQuery"
              placeholder="筛选列表中的模板名称..."
              size="small"
              :prefix-icon="Search"
              clearable
            />
          </div>

          <el-table 
            :data="filteredTableProducts" 
            style="width: 100%; flex: 1;" 
            border 
            size="small"
            class="custom-table"
            @selection-change="handleTemplateSelectionChange"
          >
            <el-table-column v-if="isTemplateBatchManaging" type="selection" width="45" align="center" />
            <el-table-column prop="productId" label="ID" width="70" align="center" />
            <el-table-column prop="productName" label="模板名称" min-width="200" show-overflow-tooltip />
            <el-table-column prop="productType" label="类型" min-width="160" align="center" show-overflow-tooltip />
            <el-table-column prop="model" label="型号规格" min-width="150" show-overflow-tooltip />
            <el-table-column prop="manufacturer" label="制造厂商" min-width="180" show-overflow-tooltip />
            <el-table-column prop="manuDate" label="出厂日期" width="110" align="center" />
            <el-table-column label="操作" width="130" align="center">
              <template #default="{ row }">
                <el-button 
                  type="primary" 
                  link 
                  size="small" 
                  @click="editProductFromTable(row)"
                >
                  编辑
                </el-button>
                <el-button 
                  type="danger" 
                  link 
                  size="small" 
                  @click="deleteProductFromTable(row)"
                >
                  删除
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
      :title="isEditingPoint ? '修改点位' : '新增点位'"
      width="500px"
      append-to-body
      class="dark-dialog"
    >
      <el-form :model="pointForm" :rules="pointRules" ref="pointFormRef" label-width="100px">
        <!-- 寄存器地址由系统自动生成，无需手动填写 -->
        <!-- <el-form-item label="寄存器地址" prop="registerId">
          <el-input v-model="pointForm.registerId" placeholder="例如: 1910002" />
        </el-form-item> -->
        <el-form-item label="点位名称" prop="pointName">
          <el-input v-model="pointForm.pointName" placeholder="例如: 温度" />
        </el-form-item>
        <el-form-item label="点位类型" prop="pointType">
          <el-select v-model="pointForm.pointType" placeholder="选择类型" style="width: 100%">
            <el-option :value="1" label="DI - 数字输入" />
            <el-option :value="2" label="DO - 数字输出" />
            <el-option :value="3" label="AI - 模拟输入" />
            <el-option :value="4" label="AO - 模拟输出" />
          </el-select>
        </el-form-item>
        <el-form-item label="单位" prop="unit">
          <el-input v-model="pointForm.unit" placeholder="例如: ℃, %, V, 无" />
        </el-form-item>
        <el-form-item label="精度" prop="displayPrecision">
          <el-input-number v-model="pointForm.displayPrecision" :min="0" :max="4" style="width: 100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="pointDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitPointForm" :loading="pointSubmitting">确认</el-button>
      </template>
    </el-dialog>

    <!-- Import CSV Drag & Drop Dialog -->
    <el-dialog
      v-model="importDialogVisible"
      title="导入点位数据"
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
          将 CSV 导入模板文件拖拽到此处，或 <em style="color: #3b82f6; font-style: normal; font-weight: bold; cursor: pointer;">点击上传</em>
        </div>
        <template #tip>
          <div class="el-upload__tip text-center" style="margin-top: 15px; color: #64748b; font-size: 12px; text-align: center;">
            仅支持 .csv 格式，导入前请确保数据格式正确。
          </div>
        </template>
      </el-upload>
      <template #footer>
        <div style="display: flex; justify-content: space-between; align-items: center; width: 100%;">
          <el-button type="primary" link :icon="Download" @click="downloadImportTemplate">
            下载导入模板
          </el-button>
          <el-button @click="importDialogVisible = false">关闭</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, nextTick } from 'vue'
import { Plus, Edit, Check, Delete, List, Odometer, Warning, Download, Upload, UploadFilled, Search } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { productApi } from '@/api/product'
import { deviceApi } from '@/api/device'
import type { Product } from '@/api/types'
import type { FormInstance, FormRules } from 'element-plus'
import { useDashboardStore } from '@/stores/dashboard'

// Extend Product interface locally for hasDrawing support
interface ExtendedProduct extends Product {
  hasDrawing?: boolean
}

const store = useDashboardStore()
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
const pointForm = ref({
  pointId: null as number | null,
  registerId: '',
  pointName: '',
  pointType: 3,
  unit: '无',
  displayPrecision: 0
})

const validateRegisterIdUnique = (_rule: any, value: any, callback: any) => {
  if (!value) {
    callback(new Error('请输入寄存器地址'))
    return
  }
  if (!/^(0x|0X)?[0-9a-fA-F]+$/.test(value)) {
    callback(new Error('寄存器地址必须是十进制数字或十六进制数（如 40001 或 0x011A）'))
    return
  }
  const list = isCreating.value ? tempPoints.value : templatePoints.value
  const exists = list.some(p => {
    if (isEditingPoint.value && p.pointId === pointForm.value.pointId) {
      return false
    }
    return String(p.registerId) === String(value)
  })
  if (exists) {
    callback(new Error('该寄存器地址已存在，不能重复配置'))
    return
  }
  callback()
}

const validatePointNameUnique = (_rule: any, value: any, callback: any) => {
  if (!value) {
    callback(new Error('请输入点位名称'))
    return
  }
  const list = isCreating.value ? tempPoints.value : templatePoints.value
  const exists = list.some(p => {
    if (isEditingPoint.value && p.pointId === pointForm.value.pointId) {
      return false
    }
    return p.pointName === value
  })
  if (exists) {
    callback(new Error('点位名称已存在，不能重复配置'))
    return
  }
  callback()
}

const pointRules = ref<FormRules>({
  registerId: [
    { validator: validateRegisterIdUnique, trigger: 'blur' },
    { max: 30, message: '寄存器地址不能超过 30 个字符', trigger: 'blur' }
  ],
  pointName: [
    { validator: validatePointNameUnique, trigger: 'blur' },
    { max: 100, message: '点位名称不能超过 100 个字符', trigger: 'blur' }
  ],
  pointType: [
    { required: true, message: '请选择点位类型', trigger: 'change' }
  ],
  unit: [
    { max: 30, message: '单位名称不能超过 30 个字符', trigger: 'blur' }
  ]
})

const formRef = ref<FormInstance>()

const form = ref<ExtendedProduct>({
  productName: '',
  productType: '',
  model: '',
  manufacturer: '',
  manuDate: '',
  hasDrawing: false
})

const rules = ref<FormRules>({
  productName: [
    { required: true, message: '请输入模板名称', trigger: 'blur' },
    { min: 2, max: 100, message: '长度在 2 到 100 个字符', trigger: 'blur' }
  ],
  productType: [
    { required: true, message: '请输入模板类型', trigger: 'blur' }
  ]
})

// Access products list directly from store's allProducts
const products = computed<ExtendedProduct[]>(() => {
  return store.allProducts.map((prod: any, index: number) => {
    return {
      ...prod,
      hasDrawing: index % 2 === 0
    }
  })
})

// Selected Product based on dashboard store selected node
const selectedProduct = computed<ExtendedProduct | null>(() => {
  if (!store.selectedNode || store.selectedNode.id === 'root') {
    return null
  }
  const found = products.value.find(p => p.productId === store.selectedNode?.id)
  return found || null
})

const templatePoints = ref<any[]>([])
const loadingPoints = ref(false)

const templateDeviceId = computed(() => {
  if (templatePoints.value.length > 0) {
    return templatePoints.value[0].deviceId
  }
  return null
})

function getPointTypeText(type: number): string {
  switch (type) {
    case 1: return 'DI - 数字输入'
    case 2: return 'DO - 数字输出'
    case 3: return 'AI - 模拟输入'
    case 4: return 'AO - 模拟输出'
    default: return '未知'
  }
}

function getPointTypeClass(type: number): string {
  switch (type) {
    case 1: return 'type-di'
    case 2: return 'type-do'
    case 3: return 'type-ai'
    case 4: return 'type-ao'
    default: return ''
  }
}

// Sync form values and load points when selected product changes
watch(selectedProduct, async (newVal) => {
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

// Reactively filter table products based on table filter query
const filteredTableProducts = computed(() => {
  const query = tableSearchQuery.value.trim().toLowerCase()
  if (!query) return products.value
  return products.value.filter(p => p.productName.toLowerCase().includes(query))
})

// Click edit action inside the table
function editProductFromTable(row: ExtendedProduct) {
  isCreating.value = false
  store.selectedNode = {
    id: row.productId,
    label: row.productName,
    type: 'product',
    status: 1,
    children: []
  }
}

// Start creation flow
function startCreate() {
  isCreating.value = true
  tempPoints.value = [] // Reset temporary points array on creation
  store.selectedNode = store.deviceTree[0] || null // back to root node representation
  form.value = {
    productName: '',
    productType: '',
    model: '',
    manufacturer: '',
    manuDate: '',
    hasDrawing: false
  }
}

// Cancel creation flow
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

// Create Submit
async function submitCreate() {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (valid) {
      submitting.value = true
      // Strip mock properties before sending to Java backend
      const payload = { ...form.value }
      delete payload.hasDrawing

      try {
        const createdProduct = await productApi.createProduct(payload)
        
        // Find the template device ID for this newly created product
        const allDevs = await deviceApi.getAllDevices()
        const found = allDevs.find((d: any) => d.deviceCode === 'TEMPLATE_' + createdProduct.productId)
        
        if (found && tempPoints.value.length > 0) {
          for (const ip of tempPoints.value) {
            await deviceApi.addPoint(found.deviceId, {
              registerId: ip.registerId,
              pointName: ip.pointName,
              pointType: ip.pointType,
              unit: ip.unit,
              displayPrecision: ip.displayPrecision
            })
          }
        }
        
        ElMessage.success('创建设备模板并同步配置点位成功')
        isCreating.value = false
        // Refresh products and store state
        await store.fetchAllDashboardData()
        
        // Highlight the newly created template node
        store.selectedNode = {
          id: createdProduct.productId,
          label: createdProduct.productName,
          type: 'product',
          status: 1,
          children: []
        }
        
        // Force enter edit mode to configure points
        await nextTick()
        isEditing.value = true
      } catch (err: any) {
        ElMessage.error(err.message || '创建模板失败')
      } finally {
        submitting.value = false
      }
    }
  })
}

// Update Submit
async function submitUpdate() {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (valid) {
      submitting.value = true
      // Strip mock properties before sending to Java backend
      const payload = { ...form.value }
      delete payload.hasDrawing

      try {
        if (form.value.productId) {
          await productApi.updateProduct(form.value.productId, payload)
          ElMessage.success('保存模板成功')
          isEditing.value = false
          await store.fetchAllDashboardData()
        } else {
          ElMessage.error('无法保存: 缺少模板ID')
        }
      } catch (err: any) {
        ElMessage.error(err.message || '保存修改失败')
      } finally {
        submitting.value = false
      }
    }
  })
}

// Common confirm deletion logic
function confirmDelete(target?: ExtendedProduct | null) {
  const productToDelete = target || selectedProduct.value
  if (!productToDelete?.productId) return
  const id = productToDelete.productId
  
  ElMessageBox.confirm(
    `确认删除设备模板 “${productToDelete.productName}” 吗？该操作不会物理删除已关联此模板的部署设备。`,
    '删除确认',
    {
      confirmButtonText: '确定删除',
      cancelButtonText: '取消',
      type: 'warning',
      confirmButtonClass: 'el-button--danger',
      boxType: 'confirm'
    }
  ).then(async () => {
    loading.value = true
    try {
      await productApi.deleteProduct(id)
      ElMessage.success('删除成功')
      await store.fetchAllDashboardData()
      store.selectedNode = store.deviceTree[0] || null
    } catch (err: any) {
      ElMessage.error(err.message || '删除模板失败')
    } finally {
      loading.value = false
    }
  }).catch(() => {})
}

function deleteProductFromTable(row: ExtendedProduct) {
  confirmDelete(row)
}

function batchDeleteTemplates() {
  ElMessageBox.confirm(
    `确认批量删除选中的 ${selectedTemplates.value.length} 个设备模板吗？该操作将物理删除选中的模板，但不会影响已使用该模板的已部署设备。`,
    '批量删除确认',
    {
      confirmButtonText: '确定删除',
      cancelButtonText: '取消',
      type: 'warning',
      confirmButtonClass: 'el-button--danger',
      boxType: 'confirm'
    }
  ).then(async () => {
    loading.value = true
    try {
      for (const p of selectedTemplates.value) {
        await productApi.deleteProduct(p.productId)
      }
      ElMessage.success('批量删除模板成功')
    } catch (err: any) {
      ElMessage.error(err.message || '批量删除失败')
    } finally {
      selectedTemplates.value = []
      isTemplateBatchManaging.value = false
      await store.fetchAllDashboardData()
      store.selectedNode = store.deviceTree[0] || null
      loading.value = false
    }
  }).catch(() => {})
}

// Point operations
async function loadTemplatePoints() {
  if (selectedProduct.value?.productId) {
    loadingPoints.value = true
    try {
      templatePoints.value = await productApi.getProductPoints(selectedProduct.value.productId)
    } catch (e: any) {
      console.error(e)
      ElMessage.error(e.message || '加载模板点位列表失败')
    } finally {
      loadingPoints.value = false
    }
  }
}

function openAddPointDialog() {
  isEditingPoint.value = false
  
  // Auto-generate registerId
  let nextId = 40001
  const list = isCreating.value ? tempPoints.value : templatePoints.value
  if (list.length > 0) {
    const numericIds = list
      .map(p => {
        const valStr = String(p.registerId)
        if (valStr.toLowerCase().startsWith('0x')) {
          return parseInt(valStr, 16)
        }
        return parseInt(valStr, 10)
      })
      .filter(id => !isNaN(id))
    if (numericIds.length > 0) {
      nextId = Math.max(...numericIds) + 1
    } else {
      nextId = list.length + 1
    }
  }
  
  // Format as decimal or hex depending on existing prefix style
  let nextIdStr = String(nextId)
  const hasHex = list.some(p => String(p.registerId).toLowerCase().startsWith('0x'))
  if (hasHex) {
    nextIdStr = '0x' + nextId.toString(16).toUpperCase().padStart(4, '0')
  }

  pointForm.value = {
    pointId: null,
    registerId: nextIdStr,
    pointName: '',
    pointType: 3,
    unit: '无',
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
    pointId: row.pointId,
    registerId: row.registerId,
    pointName: row.pointName,
    pointType: row.pointType,
    unit: row.unit,
    displayPrecision: row.displayPrecision
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
        // Local temporary storage during creation mode
        if (isEditingPoint.value) {
          const index = tempPoints.value.findIndex(p => p.pointId === pointForm.value.pointId)
          if (index !== -1) {
            tempPoints.value[index] = { ...pointForm.value }
          }
          ElMessage.success('修改成功(暂存)')
        } else {
          // Generate a negative unique temporary ID
          const tempId = Math.floor(Math.random() * -1000000)
          tempPoints.value.push({
            ...pointForm.value,
            pointId: tempId
          })
          ElMessage.success('新增点位成功(暂存)')
        }
        pointDialogVisible.value = false
        return
      }

      pointSubmitting.value = true
      try {
        let targetDeviceId = templateDeviceId.value
        const prodId = selectedProduct.value?.productId
        if (!targetDeviceId && prodId) {
          const allDevs = await deviceApi.getAllDevices()
          const found = allDevs.find((d: any) => d.deviceCode === 'TEMPLATE_' + prodId)
          if (found) {
            targetDeviceId = found.deviceId
          }
        }

        if (!targetDeviceId) {
          throw new Error('未找到该模板对应的后台设备实例，请尝试重新加载模板页面')
        }

        if (isEditingPoint.value) {
          if (pointForm.value.pointId) {
            await deviceApi.updatePoint(pointForm.value.pointId, {
              ...pointForm.value,
              deviceId: targetDeviceId,
              pointCode: targetDeviceId + "_" + pointForm.value.registerId,
              pointStatus: 1,
              pointValue: "0"
            })
            ElMessage.success('修改成功')
          }
        } else {
          await deviceApi.addPoint(targetDeviceId, {
            ...pointForm.value,
            pointStatus: 1,
            pointValue: "0"
          })
          ElMessage.success('新增点位成功')
        }
        pointDialogVisible.value = false
        await loadTemplatePoints()
      } catch (err: any) {
        ElMessage.error(err.message || '操作点位失败')
      } finally {
        pointSubmitting.value = false
      }
    }
  })
}

async function deletePoint(row: any) {
  if (isCreating.value) {
    tempPoints.value = tempPoints.value.filter(p => p.pointId !== row.pointId)
    ElMessage.success('删除成功')
    return
  }

  ElMessageBox.confirm(`确认删除点位 “${row.pointName}” 吗？`, '删除确认', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(async () => {
    try {
      await deviceApi.deletePoint(row.pointId)
      ElMessage.success('删除成功')
      await loadTemplatePoints()
    } catch (e) {
      ElMessage.error('删除点位失败')
    }
  }).catch(() => {})
}

async function batchDeletePoints() {
  if (isCreating.value) {
    tempPoints.value = tempPoints.value.filter(p => !selectedPoints.value.includes(p))
    ElMessage.success('批量删除成功')
    selectedPoints.value = []
    isPointBatchManaging.value = false
    return
  }

  ElMessageBox.confirm('确认删除选中的所有点位吗？', '批量删除确认', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(async () => {
    try {
      for (const p of selectedPoints.value) {
        await deviceApi.deletePoint(p.pointId)
      }
      ElMessage.success('批量删除成功')
    } catch (e) {
      ElMessage.error('删除部分点位失败')
    } finally {
      selectedPoints.value = []
      isPointBatchManaging.value = false
      await loadTemplatePoints()
    }
  }).catch(() => {})
}

function openConfigPanel() {
  ElMessage.success('已跳转到组态画图编辑界面')
}

function openDeviceAlarmConfig() {
  ElMessageBox.alert('当前正在为该设备模板配置报警规则：当设备发生断电、通信中断时，下属测点全部报故障状态。', '设备级报警配置', {
    confirmButtonText: '确定'
  })
}

function openPointAlarmConfig(row: any) {
  ElMessageBox.alert(`当前正在为测点 [${row.pointName}] 配置报警规则：阈值高低限设置、告警延迟及告警级别配置。`, '点位级报警配置', {
    confirmButtonText: '确定'
  })
}

function handleExport() {
  exportAllProductsAndPoints()
}

async function exportAllProductsAndPoints() {
  loading.value = true
  try {
    const headers = ['设备模板名称', '设备类型', '型号规格', '制造厂商', '点位编码', '寄存器地址', '点位名称', '点位类型', '单位', '精度']
    const rows: any[] = []
    
    // Fetch points for each product
    for (const prod of store.allProducts) {
      if (prod.productId === undefined) continue
      try {
        const points = await productApi.getProductPoints(prod.productId)
        for (const p of points) {
          rows.push([
            prod.productName || '',
            prod.productType || '',
            prod.model || '',
            prod.manufacturer || '',
            p.pointCode || '',
            p.registerId || '',
            p.pointName || '',
            getPointTypeText(p.pointType),
            p.unit || '',
            p.displayPrecision ?? 0
          ])
        }
      } catch (e) {
        console.error(`Failed to fetch points for product ${prod.productId}:`, e)
      }
    }
    
    let csvContent = "\uFEFF" // Chinese encoding BOM
    csvContent += [headers.join(','), ...rows.map(r => r.map((val: any) => {
      const escaped = String(val ?? '').replace(/"/g, '""')
      return `"${escaped}"`
    }).join(','))].join('\n')
    
    const blob = new Blob([csvContent], { type: 'text/csv;charset=utf-8;' })
    const link = document.createElement("a")
    const url = URL.createObjectURL(blob)
    link.setAttribute("href", url)
    link.setAttribute("download", `全部设备模板及点位表.csv`)
    link.style.visibility = 'hidden'
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    ElMessage.success('全部模板点位表导出成功')
  } catch (err: any) {
    ElMessage.error('导出失败: ' + err.message)
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
    importAllProductsAndPoints(rawFile)
    importDialogVisible.value = false
  }
}

function downloadImportTemplate() {
  const headers = ['设备模板名称', '设备类型', '型号规格', '制造厂商', '点位编码', '寄存器地址', '点位名称', '点位类型', '单位', '精度']
  const rows = [
    ['样例电表模板', 'EM', 'DTSD-342', '某某仪表公司', 'TEMPLATE_POINT_001', '40001', 'A相电压', 'AI - 模拟输入', 'V', '1'],
    ['样例电表模板', 'EM', 'DTSD-342', '某某仪表公司', 'TEMPLATE_POINT_002', '40002', 'B相电压', 'AI - 模拟输入', 'V', '1'],
    ['样例电表模板', 'EM', 'DTSD-342', '某某仪表公司', 'TEMPLATE_POINT_003', '40003', '通讯状态', 'DI - 数字输入', '无', '0']
  ]
  
  let csvContent = "\uFEFF" // Chinese encoding BOM
  csvContent += [headers.join(','), ...rows.map(r => r.map(val => `"${val}"`).join(','))].join('\n')
  
  const blob = new Blob([csvContent], { type: 'text/csv;charset=utf-8;' })
  const link = document.createElement("a")
  const url = URL.createObjectURL(blob)
  link.setAttribute("href", url)
  link.setAttribute("download", `设备点位导入模板.csv`)
  link.style.visibility = 'hidden'
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  ElMessage.success('导入模板下载成功')
}

// Import all products and points from CSV
function importAllProductsAndPoints(file: File) {
  const reader = new FileReader()
  reader.onload = async (e: any) => {
    loading.value = true
    try {
      const text = e.target.result
      const lines = text.split('\n').map((l: string) => l.trim()).filter(Boolean)
      if (lines.length <= 1) {
        throw new Error('格式错误，请重新导入。（原因：文件为空或没有数据行）')
      }
      
      // Detect delimiter
      let delimiter = ','
      if (lines[1] && lines[1].includes(';')) {
        delimiter = ';'
      }
      
      // 1. Header validation
      const headerLine = lines[0].split(delimiter).map((s: any) => s.replace(/^"|"$/g, '').trim())
      if (headerLine.length < 10) {
        throw new Error('格式错误，请重新导入。（原因：表头列数不足，应为 10 列）')
      }
      const requiredHeaders = ['设备模板名称', '点位编码', '寄存器地址', '点位名称', '点位类型']
      const missingHeaders = requiredHeaders.filter((h: any) => !headerLine.some((col: any) => col.includes(h)))
      if (missingHeaders.length > 0) {
        throw new Error(`格式错误，请重新导入。（原因：表头缺少必要列: ${missingHeaders.join(', ')}）`)
      }

      // Map: productName -> { type, model, manufacturer, points: [] }
      const groups = new Map<string, { type: string, model: string, manufacturer: string, points: any[] }>()
      
      for (let i = 1; i < lines.length; i++) {
        const parts = lines[i].split(delimiter).map((s: any) => s.replace(/^"|"$/g, '').trim())
        
        // Skip blank lines
        if (parts.every((p: any) => p === '')) continue

        if (parts.length < 10) {
          throw new Error(`格式错误，请重新导入。（原因：第 ${i + 1} 行数据列数不足 10 列）`)
        }
        
        const prodName = parts[0]
        if (!prodName) {
          throw new Error(`格式错误，请重新导入。（原因：第 ${i + 1} 行的“设备模板名称”不能为空）`)
        }
        
        const type = parts[1]
        const model = parts[2]
        const manufacturer = parts[3]
        
        const registerId = parts[5]
        if (!registerId) {
          throw new Error(`格式错误，请重新导入。（原因：第 ${i + 1} 行的“寄存器地址”不能为空）`)
        }
        if (!/^(0x|0X)?[0-9a-fA-F]+$/.test(registerId)) {
          throw new Error(`格式错误，请重新导入。（原因：第 ${i + 1} 行的“寄存器地址”必须为十进制数字或十六进制数，如 40001 或 0x011A）`)
        }

        const pointName = parts[6]
        if (!pointName) {
          throw new Error(`格式错误，请重新导入。（原因：第 ${i + 1} 行的“点位名称”不能为空）`)
        }

        const typeStr = parts[7] || ''
        if (!typeStr) {
          throw new Error(`格式错误，请重新导入。（原因：第 ${i + 1} 行的“点位类型”不能为空）`)
        }
        
        let pointType = 0
        if (typeStr.toUpperCase().includes('DI')) pointType = 1
        else if (typeStr.toUpperCase().includes('DO')) pointType = 2
        else if (typeStr.toUpperCase().includes('AI')) pointType = 3
        else if (typeStr.toUpperCase().includes('AO')) pointType = 4
        
        if (pointType === 0) {
          throw new Error(`格式错误，请重新导入。（原因：第 ${i + 1} 行的“点位类型”必须包含 DI/DO/AI/AO）`)
        }

        const unit = parts[8]
        const precisionStr = parts[9]
        if (precisionStr && !/^\d+$/.test(precisionStr)) {
          throw new Error(`格式错误，请重新导入。（原因：第 ${i + 1} 行的“精度”必须为非负整数）`)
        }
        const displayPrecision = parseInt(precisionStr) || 0
        
        if (!groups.has(prodName)) {
          groups.set(prodName, {
            type,
            model,
            manufacturer,
            points: []
          })
        }
        
        groups.get(prodName)!.points.push({
          registerId,
          pointName,
          pointType,
          dataType: displayPrecision > 0 ? 2 : 1,
          unit,
          displayPrecision
        })
      }
      
      if (groups.size === 0) {
        throw new Error('格式错误，请重新导入。（原因：未解析到任何有效数据行）')
      }
      
      // Fetch latest devices and products lists
      await store.fetchAllDashboardData()
      const allDevs = await deviceApi.getAllDevices()
      
      let createdCount = 0
      let updatedCount = 0
      let totalPointsCount = 0
      let addedPointsCount = 0
      let duplicatePointsCount = 0
      
      for (const [prodName, data] of groups.entries()) {
        let product = store.allProducts.find((p: any) => p.productName === prodName)
        let targetDeviceId: number | null = null
        
        if (!product) {
          // Create product
          product = await productApi.createProduct({
            productName: prodName,
            productType: data.type || 'EM',
            model: data.model || 'N/A',
            manufacturer: data.manufacturer || 'N/A',
            manuDate: new Date().toISOString().split('T')[0]
          })
          createdCount++
          
          // Re-fetch all devices to find the newly created template device
          const updatedDevs = await deviceApi.getAllDevices()
          const found = updatedDevs.find((d: any) => d.deviceCode === 'TEMPLATE_' + product!.productId)
          if (found) {
            targetDeviceId = found.deviceId
          }
        } else {
          // Product exists, find its template device
          const found = allDevs.find((d: any) => d.deviceCode === 'TEMPLATE_' + product!.productId)
          if (found) {
            targetDeviceId = found.deviceId
          }
          updatedCount++
        }
        
        if (targetDeviceId) {
          // Import points for this template
          for (const ip of data.points) {
            const res = await deviceApi.addPoint(targetDeviceId, ip)
            if (res === '更新点位成功') {
              duplicatePointsCount++
            } else {
              addedPointsCount++
            }
            totalPointsCount++
          }
        }
      }
      
      let successMsg = `导入成功: 新建 ${createdCount} 模板，更新 ${updatedCount} 模板。`
      if (duplicatePointsCount > 0) {
        successMsg += ` 覆盖了 ${duplicatePointsCount} 个重复点位。`
      } else {
        successMsg += ` 共导入 ${totalPointsCount} 个点位。`
      }
      ElMessage.success({
        message: successMsg,
        showClose: true,
        duration: 10000
      })
      await store.fetchAllDashboardData()
      
    } catch (err: any) {
      console.error(err)
      ElMessage.error({
        message: err.message || '导入失败',
        showClose: true,
        duration: 10000
      })
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
$text-primary: #f8fafc;
$text-secondary: #cbd5e1;
$text-muted: #64748b;
$accent-green: #10b981;
$accent-blue: #3b82f6;
$accent-yellow: #f59e0b;
$bg-dark: #0b1119;

.product-manager-view {
  height: 100%;
  padding: 4px;
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
  padding: 16px;
  border-bottom: 1px solid $border-color;
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-shrink: 0;
}

.header-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  font-weight: 600;
  color: $text-primary;

  .title-icon {
    color: $accent-green;
    font-size: 16px;
  }
}

.detail-card {
  height: 100%;
}

.detail-body {
  padding: 24px;
  flex: 1;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
}

.custom-form {
  max-width: 600px;

  :deep(.el-form-item__label) {
    color: $text-secondary;
    font-weight: 500;
  }
  :deep(.el-input__wrapper) {
    background-color: $bg-dark !important;
  }
}

.custom-form-compact {
  background-color: rgba($bg-card-hover, 0.3);
  border-radius: 8px;
  padding: 16px;
  border: 1px solid $border-color;
  margin-bottom: 16px;
  flex-shrink: 0;

  :deep(.el-form-item) {
    margin-bottom: 8px;
  }
  
  :deep(.el-form-item__label) {
    color: $text-secondary;
    font-weight: 500;
  }
  :deep(.el-input__wrapper) {
    background-color: $bg-dark !important;
  }
}

.action-buttons {
  display: flex;
  align-items: center;
  gap: 8px;
}

.extra-property-buttons {
  display: flex;
  gap: 12px;
  margin-bottom: 16px;
  padding-bottom: 12px;
  border-bottom: 1px dashed rgba($border-color, 0.6);
  flex-shrink: 0;
}

.points-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
  flex-shrink: 0;
}

.points-actions {
  display: flex;
  gap: 8px;
}

/* Table styles */
.table-body {
  padding: 16px;
  flex: 1;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.search-box-table {
  width: 320px;
  flex-shrink: 0;
  
  :deep(.el-input__wrapper) {
    background-color: $bg-dark !important;
  }
}

.custom-table {
  --el-table-border-color: #1f2937;
  --el-table-header-bg-color: #1a2332;
  --el-table-tr-bg-color: transparent;
  
  background-color: transparent !important;
  
  :deep(tr) {
    background-color: transparent !important;
    color: $text-primary;
    
    &:hover td {
      background-color: $bg-card-hover !important;
    }
  }
  
  :deep(th) {
    background-color: #182232 !important;
    color: $text-secondary;
    font-weight: 600;
  }
  
  :deep(td) {
    border-bottom: 1px solid #1f2937;
  }
}

.status-icon-wrapper {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  
  &.has {
    color: $accent-green;
  }
  &.none {
    color: $text-muted;
  }
}

.status-icon {
  font-size: 15px;
  
  &.active-draw {
    color: $accent-green;
  }
  &.gray-face {
    color: $text-muted;
  }
}

.status-text {
  font-size: 11px;
}

.drawing-status-section {
  display: flex;
  align-items: center;
}

.drawing-status-badge {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 2px 10px;
  border-radius: 4px;
  font-size: 12px;
  font-weight: 500;
  
  &.has {
    background-color: rgba($accent-green, 0.15);
    color: $accent-green;
  }
  
  &.none {
    background-color: rgba($text-muted, 0.15);
    color: $text-muted;
  }
}

.point-table-section {
  margin-top: 16px;
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.section-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  font-weight: 600;
  color: $text-primary;

  .section-icon {
    color: $accent-green;
    font-size: 16px;
  }
}

.type-badge {
  display: inline-block;
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 11px;
  font-weight: 500;
  
  &.type-di {
    background-color: rgba($accent-blue, 0.1);
    border: 1px solid rgba($accent-blue, 0.2);
    color: $accent-blue;
  }
  &.type-do {
    background-color: rgba(168, 85, 247, 0.1);
    border: 1px solid rgba(168, 85, 247, 0.2);
    color: #a855f7;
  }
  &.type-ai {
    background-color: rgba($accent-green, 0.1);
    border: 1px solid rgba($accent-green, 0.2);
    color: $accent-green;
  }
  &.type-ao {
    background-color: rgba($accent-yellow, 0.1);
    border: 1px solid rgba($accent-yellow, 0.2);
    color: $accent-yellow;
  }
}

.addr-code {
  background-color: rgba($text-muted, 0.1);
  padding: 2px 6px;
  border-radius: 4px;
  font-family: monospace;
  color: $text-secondary;
  font-size: 12px;
}
</style>
