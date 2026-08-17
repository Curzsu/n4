<template>
  <div class="product-manager-view" v-loading="loading">
    <div class="card-container detail-card">
      <!-- Case 1: Creating a new template -->
      <div v-if="isCreating" class="form-container-wrapper">
        <div class="panel-header">
          <div class="header-title">
            <el-icon class="title-icon"><Plus /></el-icon>
            <span>新增设备模板 (Mock)</span>
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
            <el-form-item label="模板名称" prop="deviceTmplName">
              <el-input v-model="form.deviceTmplName" placeholder="例如：大金列间空调" />
            </el-form-item>
            
            <el-form-item label="模板类型" prop="deviceType">
              <el-input v-model="form.deviceType" placeholder="例如：AC (空调), UPS, TRANSFORMER" />
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
                <span>配置初始点位表 (共 {{ tempPoints.length }} 个点位，创建模板时将同步保存，均为 Mock)</span>
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
              <el-table-column type="selection" width="40" align="center" v-if="isPointBatchManaging" :resizable="false" />
              <el-table-column prop="pointName" label="点位名称" min-width="180" show-overflow-tooltip :resizable="false" />
              <el-table-column prop="pointType" label="点位类型" min-width="100" align="center" :resizable="false">
                <template #default="{ row }">
                  <span :class="['type-badge', getPointTypeClass(row.pointType)]">
                    {{ row.pointType || '—' }}
                  </span>
                </template>
              </el-table-column>
              <el-table-column prop="dataType" label="数据类型" min-width="100" align="center" :resizable="false">
                <template #default="{ row }">
                  <span :class="['type-badge', getDataTypeClass(row.dataType)]">{{ row.dataType }}</span>
                </template>
              </el-table-column>
              <el-table-column prop="unit" label="单位" width="85" align="center" :resizable="false" />
              <el-table-column prop="displayPrecision" label="精度" width="75" align="center" :resizable="false" />
              <el-table-column label="数据来源" width="110" align="center" :resizable="false">
                <template>
                  <el-tag size="small" type="warning" effect="dark">Mock</el-tag>
                </template>
              </el-table-column>
              <el-table-column v-if="isPointBatchManaging" label="操作" width="110" fixed="right" align="center" :resizable="false">
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
      <div v-else-if="selectedTemplate" class="form-container-wrapper">
        <div class="panel-header">
          <div class="header-title">
            <el-icon class="title-icon"><Edit /></el-icon>
            <span>设备模板 - {{ selectedTemplate.deviceTmplName }}</span>
            <el-tag size="small" :type="selectedTemplate.isReal ? 'success' : 'warning'" effect="dark">
              {{ selectedTemplate.isReal ? '真实数据' : 'Mock' }}
            </el-tag>
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
              @click="confirmDelete(selectedTemplate)"
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
                <el-form-item label="模板名称" prop="deviceTmplName">
                  <el-input v-model="form.deviceTmplName" />
                </el-form-item>
              </el-col>
              <el-col :span="8">
                <el-form-item label="模板类型" prop="deviceType">
                  <el-input v-model="form.deviceType" />
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
              <el-table-column type="selection" width="40" align="center" v-if="isPointBatchManaging" :resizable="false" />
              <el-table-column prop="pointTmplId" label="点位ID" width="75" align="center" :resizable="false" />
              <el-table-column prop="pointName" label="点位名称" min-width="180" show-overflow-tooltip :resizable="false" />
              <el-table-column prop="pointType" label="点位类型" min-width="100" align="center" :resizable="false">
                <template #default="{ row }">
                  <span :class="['type-badge', getPointTypeClass(row.pointType)]">
                    {{ row.pointType || '—' }}
                  </span>
                </template>
              </el-table-column>
              <el-table-column prop="dataType" label="数据类型" min-width="100" align="center" :resizable="false">
                <template #default="{ row }">
                  <span :class="['type-badge', getDataTypeClass(row.dataType)]">{{ row.dataType }}</span>
                </template>
              </el-table-column>
              <el-table-column prop="unit" label="单位" width="85" align="center" :resizable="false" />
              <el-table-column prop="displayPrecision" label="精度" width="75" align="center" :resizable="false" />
              <el-table-column label="数据来源" width="110" align="center" :resizable="false">
                <template #default="{ row }">
                  <el-tag size="small" :type="row.isReal ? 'success' : 'warning'" effect="dark">{{ row.isReal ? '真实数据' : 'Mock' }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column v-if="isPointBatchManaging" label="操作" width="180" fixed="right" align="center" :resizable="false">
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
            <span>全部设备模板列表 (真实数据 {{ realTemplateCount }} 个 / Mock {{ mockTemplateCount }} 个)</span>
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
            :data="filteredTableTemplates" 
            style="width: 100%; flex: 1;" 
            border 
            size="small"
            class="custom-table"
            @selection-change="handleTemplateSelectionChange"
          >
            <el-table-column v-if="isTemplateBatchManaging" type="selection" width="40" align="center" :resizable="false" />
            <el-table-column prop="deviceTmplId" label="ID" width="60" align="center" :resizable="false" />
            <el-table-column prop="deviceTmplName" label="模板名称" min-width="160" show-overflow-tooltip :resizable="false" />
            <el-table-column prop="deviceType" label="类型" min-width="110" align="center" show-overflow-tooltip :resizable="false" />
            <el-table-column label="型号规格" min-width="110" align="center" show-overflow-tooltip :resizable="false">
              <template #default="{ row }">
                <span>{{ row.model || '—' }}</span>
              </template>
            </el-table-column>
            <el-table-column label="制造厂商" min-width="120" align="center" show-overflow-tooltip :resizable="false">
              <template #default="{ row }">
                <span>{{ row.manufacturer || '—' }}</span>
              </template>
            </el-table-column>
            <el-table-column label="出厂日期" width="100" align="center" :resizable="false">
              <template #default="{ row }">
                <span>{{ row.manuDate || '—' }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="pointCount" label="点位数" width="75" align="center" :resizable="false" />
            <el-table-column label="数据来源" width="110" align="center" :resizable="false">
              <template #default="{ row }">
                <el-tag size="small" :type="Boolean(row.isReal) ? 'success' : 'warning'" effect="dark">{{ Boolean(row.isReal) ? '真实数据' : 'Mock' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="170" align="center" fixed="right" :resizable="false">
              <template #default="{ row }">
                <el-button 
                  type="success" 
                  link 
                  size="small" 
                  :icon="Picture" 
                  @click="openConfigPanelWithTarget(row as DeviceTemplate)"
                >
                  组态图
                </el-button>
                <el-button 
                  type="primary" 
                  link 
                  size="small" 
                  @click="editTemplateFromTable(row as DeviceTemplate)"
                >
                  编辑
                </el-button>
                <el-button 
                  v-if="isTemplateBatchManaging"
                  type="danger" 
                  link 
                  size="small" 
                  @click="deleteTemplateFromTable(row as DeviceTemplate)"
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
        <el-form-item label="点位名称" prop="pointName">
          <el-input v-model="pointForm.pointName" placeholder="例如: 温度" />
        </el-form-item>
        <el-form-item label="点位类型" prop="pointType">
          <el-select v-model="pointForm.pointType" placeholder="选择类型" style="width: 100%">
            <el-option value="DI" label="DI - 数字输入" />
            <el-option value="DO" label="DO - 数字输出" />
            <el-option value="AI" label="AI - 模拟输入" />
            <el-option value="AO" label="AO - 模拟输出" />
          </el-select>
        </el-form-item>
        <el-form-item label="数据类型" prop="dataType">
          <el-select v-model="pointForm.dataType" placeholder="选择数据类型" style="width: 100%">
            <el-option value="numeric" label="numeric - 数值" />
            <el-option value="enum" label="enum - 枚举" />
            <el-option value="boolean" label="boolean - 布尔" />
            <el-option value="string" label="string - 字符串" />
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

    <!-- Device Diagram Modal Dialog -->
    <el-dialog
      v-model="diagramDialogVisible"
      :title="`设备组态图 - ${currentDiagramTarget?.deviceTmplName || selectedTemplate?.deviceTmplName || form.deviceTmplName || '设备模板'}`"
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
            <span>模板名称: <strong>{{ currentDiagramTarget?.deviceTmplName || selectedTemplate?.deviceTmplName || form.deviceTmplName || '—' }}</strong></span>
            <span class="meta-divider">|</span>
            <span>设备类型: <strong>{{ currentDiagramTarget?.deviceType || selectedTemplate?.deviceType || form.deviceType || '—' }}</strong></span>
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
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, nextTick } from 'vue'
import { Plus, Edit, Check, Delete, List, Odometer, Warning, Download, Upload, UploadFilled, Search, Picture, PictureRounded, View, Loading } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { templateApi } from '@/api/template'
import type { DeviceTemplate, PointTemplateItem } from '@/api/types'
import type { FormInstance, FormRules } from 'element-plus'
import { useDashboardStore } from '@/stores/dashboard'

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
const pointForm = ref<any>({
  pointTmplId: null,
  pointName: '',
  pointType: 'AI',
  dataType: 'numeric',
  unit: '无',
  displayPrecision: 0
})

const validatePointNameUnique = (_rule: any, value: any, callback: any) => {
  if (!value) {
    callback(new Error('请输入点位名称'))
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
    callback(new Error('点位名称已存在，不能重复配置'))
    return
  }
  callback()
}

const pointRules = ref<FormRules>({
  pointName: [
    { validator: validatePointNameUnique, trigger: 'blur' },
    { max: 150, message: '点位名称不能超过 150 个字符', trigger: 'blur' }
  ],
  pointType: [
    { required: true, message: '请选择点位类型', trigger: 'change' }
  ],
  dataType: [
    { required: true, message: '请选择数据类型', trigger: 'change' }
  ],
  unit: [
    { max: 50, message: '单位名称不能超过 50 个字符', trigger: 'blur' }
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
    { required: true, message: '请输入模板名称', trigger: 'blur' },
    { min: 2, max: 100, message: '长度在 2 到 100 个字符', trigger: 'blur' }
  ],
  deviceType: [
    { required: true, message: '请输入模板类型', trigger: 'blur' }
  ]
})

// Access templates list directly from store's allTemplates (device_template 表)
const templates = computed<DeviceTemplate[]>(() => store.allTemplates)

const realTemplateCount = computed(() => templates.value.filter(t => t.isReal).length)
const mockTemplateCount = computed(() => templates.value.filter(t => !t.isReal).length)

// Selected Template based on dashboard store selected node
const selectedTemplate = computed<DeviceTemplate | null>(() => {
  if (!store.selectedNode || store.selectedNode.id === 'root' || store.selectedNode.id === 'root_tmpl') {
    return null
  }
  const found = templates.value.find(t => t.deviceTmplId === store.selectedNode?.id)
  return found || null
})

const templatePoints = ref<PointTemplateItem[]>([])
const loadingPoints = ref(false)

function getPointTypeClass(type: string): string {
  switch ((type || '').toUpperCase()) {
    case 'DI': return 'type-di'
    case 'DO': return 'type-do'
    case 'AI': return 'type-ai'
    case 'AO': return 'type-ao'
    default: return ''
  }
}

function getDataTypeClass(type: string): string {
  switch ((type || '').toLowerCase()) {
    case 'numeric': return 'type-ai'
    case 'enum': return 'type-di'
    case 'boolean': return 'type-di'
    case 'string': return 'type-ao'
    default: return ''
  }
}

// Sync form values and load points when selected template changes
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

// Reactively filter table templates based on table filter query
const filteredTableTemplates = computed(() => {
  const query = tableSearchQuery.value.trim().toLowerCase()
  if (!query) return templates.value
  return templates.value.filter(t => t.deviceTmplName.toLowerCase().includes(query))
})

// Click edit action inside the table
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

// Start creation flow
function startCreate() {
  isCreating.value = true
  tempPoints.value = [] // Reset temporary points array on creation
  store.selectedNode = store.deviceTree[0] || null // back to root node representation
  form.value = {
    deviceTmplName: '',
    deviceType: '',
    model: '',
    manufacturer: '',
    manuDate: ''
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

        ElMessage.success('创建设备模板并同步配置点位成功')
        isCreating.value = false
        // Refresh templates and store state
        await store.refreshAfterMutation()

        // Highlight the newly created template node
        store.selectedNode = {
          id: createdTemplate.deviceTmplId,
          label: createdTemplate.deviceTmplName,
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
      try {
        if (form.value.deviceTmplId) {
          await templateApi.updateTemplate(form.value.deviceTmplId, form.value)
          ElMessage.success('保存模板成功')
          isEditing.value = false
          await store.refreshAfterMutation()
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
function confirmDelete(target?: DeviceTemplate | null) {
  const templateToDelete = target || selectedTemplate.value
  if (!templateToDelete?.deviceTmplId) return
  const id = templateToDelete.deviceTmplId

  ElMessageBox.confirm(
    `确认删除设备模板 “${templateToDelete.deviceTmplName}” 吗？该操作不会物理删除已关联此模板的部署设备。`,
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
      await templateApi.deleteTemplate(id)
      ElMessage.success('删除成功')
      await store.refreshAfterMutation()
      store.selectedNode = store.deviceTree[0] || null
    } catch (err: any) {
      ElMessage.error(err.message || '删除模板失败')
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
      for (const t of selectedTemplates.value) {
        await templateApi.deleteTemplate(t.deviceTmplId)
      }
      ElMessage.success('批量删除模板成功')
    } catch (err: any) {
      ElMessage.error(err.message || '批量删除失败')
    } finally {
      selectedTemplates.value = []
      isTemplateBatchManaging.value = false
      await store.refreshAfterMutation()
      store.selectedNode = store.deviceTree[0] || null
      loading.value = false
    }
  }).catch(() => {})
}

// Point operations
async function loadTemplatePoints() {
  if (selectedTemplate.value?.deviceTmplId) {
    loadingPoints.value = true
    try {
      templatePoints.value = await templateApi.getPoints(selectedTemplate.value.deviceTmplId)
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
  pointForm.value = {
    pointTmplId: null,
    pointName: '',
    pointType: 'AI',
    dataType: 'numeric',
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
        // Local temporary storage during creation mode
        if (isEditingPoint.value) {
          const index = tempPoints.value.findIndex(p => p.pointTmplId === pointForm.value.pointTmplId)
          if (index !== -1) {
            tempPoints.value[index] = { ...pointForm.value }
          }
          ElMessage.success('修改成功(暂存)')
        } else {
          // Generate a negative unique temporary ID
          const tempId = Math.floor(Math.random() * -1000000)
          tempPoints.value.push({
            ...pointForm.value,
            pointTmplId: tempId
          })
          ElMessage.success('新增点位成功(暂存)')
        }
        pointDialogVisible.value = false
        return
      }

      pointSubmitting.value = true
      try {
        const tmplId = selectedTemplate.value?.deviceTmplId
        if (!tmplId) {
          throw new Error('未找到当前模板，请刷新页面后重试')
        }

        if (isEditingPoint.value && pointForm.value.pointTmplId) {
          await templateApi.updatePoint(pointForm.value.pointTmplId, {
            pointName: pointForm.value.pointName,
            pointType: pointForm.value.pointType,
            dataType: pointForm.value.dataType,
            unit: pointForm.value.unit,
            displayPrecision: pointForm.value.displayPrecision
          })
          ElMessage.success('修改成功')
        } else {
          await templateApi.addPoint(tmplId, {
            pointName: pointForm.value.pointName,
            pointType: pointForm.value.pointType,
            dataType: pointForm.value.dataType,
            unit: pointForm.value.unit,
            displayPrecision: pointForm.value.displayPrecision
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
    tempPoints.value = tempPoints.value.filter(p => p.pointTmplId !== row.pointTmplId)
    ElMessage.success('删除成功')
    return
  }

  ElMessageBox.confirm(`确认删除点位 “${row.pointName}” 吗？`, '删除确认', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(async () => {
    try {
      await templateApi.deletePoint(row.pointTmplId)
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
        await templateApi.deletePoint(p.pointTmplId)
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

function openConfigPanel() {
  const current = selectedTemplate.value || form.value
  currentDiagramTarget.value = current
  selectedDiagramFile.value = findBestMatchingImage(current)
  diagramImageLoading.value = true
  diagramImageError.value = false
  diagramDialogVisible.value = true
}

function openConfigPanelWithTarget(target: DeviceTemplate) {
  currentDiagramTarget.value = target
  selectedDiagramFile.value = findBestMatchingImage(target)
  diagramImageLoading.value = true
  diagramImageError.value = false
  diagramDialogVisible.value = true
}

function openImageInNewTab() {
  if (currentDiagramUrl.value) {
    window.open(currentDiagramUrl.value, '_blank')
  }
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
  exportAllTemplatesAndPoints()
}

async function exportAllTemplatesAndPoints() {
  loading.value = true
  try {
    const headers = ['设备模板名称', '设备类型', '型号规格', '制造厂商', '点位名称', '点位类型', '数据类型', '单位', '精度', '数据来源']
    const rows: any[] = []

    // Fetch points for each template
    for (const tpl of store.allTemplates) {
      if (tpl.deviceTmplId === undefined) continue
      try {
        const points = await templateApi.getPoints(tpl.deviceTmplId)
        if (points.length === 0) {
          rows.push([tpl.deviceTmplName || '', tpl.deviceType || '', tpl.model || '', tpl.manufacturer || '',
            '', '', '', '', '', tpl.isReal ? '真实数据' : 'Mock'])
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
            p.displayPrecision ?? 0,
            p.isReal ? '真实数据' : 'Mock'
          ])
        }
      } catch (e) {
        console.error(`Failed to fetch points for template ${tpl.deviceTmplId}:`, e)
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
    importAllTemplatesAndPoints(rawFile)
    importDialogVisible.value = false
  }
}

function downloadImportTemplate() {
  const headers = ['设备模板名称', '设备类型', '型号规格', '制造厂商', '点位名称', '点位类型', '数据类型', '单位', '精度']
  const rows = [
    ['样例电表模板', 'EM', 'DTSD-342', '某某仪表公司', 'A相电压', 'AI', 'numeric', 'V', '1'],
    ['样例电表模板', 'EM', 'DTSD-342', '某某仪表公司', 'B相电压', 'AI', 'numeric', 'V', '1'],
    ['样例电表模板', 'EM', 'DTSD-342', '某某仪表公司', '通讯状态', 'DI', 'boolean', '无', '0']
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

// Import all templates and points from CSV
function importAllTemplatesAndPoints(file: File) {
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
      if (headerLine.length < 9) {
        throw new Error('格式错误，请重新导入。（原因：表头列数不足，应为 9 列）')
      }
      const requiredHeaders = ['设备模板名称', '点位名称', '点位类型']
      const missingHeaders = requiredHeaders.filter((h: any) => !headerLine.some((col: any) => col.includes(h)))
      if (missingHeaders.length > 0) {
        throw new Error(`格式错误，请重新导入。（原因：表头缺少必要列: ${missingHeaders.join(', ')}）`)
      }

      // Map: templateName -> { type, model, manufacturer, points: [] }
      const groups = new Map<string, { type: string, model: string, manufacturer: string, points: any[] }>()

      for (let i = 1; i < lines.length; i++) {
        const parts = lines[i].split(delimiter).map((s: any) => s.replace(/^"|"$/g, '').trim())

        // Skip blank lines
        if (parts.every((p: any) => p === '')) continue

        if (parts.length < 9) {
          throw new Error(`格式错误，请重新导入。（原因：第 ${i + 1} 行数据列数不足 9 列）`)
        }

        const tplName = parts[0]
        if (!tplName) {
          throw new Error(`格式错误，请重新导入。（原因：第 ${i + 1} 行的“设备模板名称”不能为空）`)
        }

        const type = parts[1]
        const model = parts[2]
        const manufacturer = parts[3]

        const pointName = parts[4]
        if (!pointName) {
          throw new Error(`格式错误，请重新导入。（原因：第 ${i + 1} 行的“点位名称”不能为空）`)
        }

        const typeStr = parts[5] || ''
        let pointType = ''
        if (typeStr.toUpperCase().includes('DI')) pointType = 'DI'
        else if (typeStr.toUpperCase().includes('DO')) pointType = 'DO'
        else if (typeStr.toUpperCase().includes('AI')) pointType = 'AI'
        else if (typeStr.toUpperCase().includes('AO')) pointType = 'AO'
        if (!pointType) {
          throw new Error(`格式错误，请重新导入。（原因：第 ${i + 1} 行的“点位类型”必须包含 DI/DO/AI/AO）`)
        }

        let dataType = parts[6] || ''
        if (!dataType) {
          dataType = (pointType === 'DI' || pointType === 'DO') ? 'boolean' : 'numeric'
        }

        const unit = parts[7]
        const precisionStr = parts[8]
        if (precisionStr && !/^\d+$/.test(precisionStr)) {
          throw new Error(`格式错误，请重新导入。（原因：第 ${i + 1} 行的“精度”必须为非负整数）`)
        }
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
        throw new Error('格式错误，请重新导入。（原因：未解析到任何有效数据行）')
      }

      await store.refreshAfterMutation()

      let createdCount = 0
      let updatedCount = 0
      let totalPointsCount = 0
      let addedPointsCount = 0
      let duplicatePointsCount = 0

      for (const [tplName, data] of groups.entries()) {
        let template = store.allTemplates.find((t: any) => t.deviceTmplName === tplName)
        let targetTmplId: number | null = null

        if (!template) {
          // Create template
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
          // Import points for this template
          for (const ip of data.points) {
            const res = await templateApi.addPoint(targetTmplId, ip)
            if ((res as any) === '更新点位成功') {
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
      await store.refreshAfterMutation()

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
    transition: all 0.2s ease;
    
    &:hover, &.is-focus {
      box-shadow: 0 0 0 1px rgba($accent-green, 0.5) inset !important;
    }
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
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 3px 11px;
  border-radius: 11px;
  font-size: 13px;
  font-weight: 400;
  line-height: 1.25;
  letter-spacing: 0.1px;
  border: none;
  transition: all 0.2s ease-in-out;

  &.type-di {
    background-color: rgba($accent-blue, 0.14);
    color: #7dd3fc;
  }
  &.type-do {
    background-color: rgba(168, 85, 247, 0.14);
    color: #c084fc;
  }
  &.type-ai {
    background-color: rgba($accent-green, 0.14);
    color: #4ade80;
  }
  &.type-ao {
    background-color: rgba($accent-yellow, 0.14);
    color: #facc15;
  }

  &:hover {
    filter: brightness(1.1);
    transform: translateY(-0.5px);
  }
}

:deep(.el-tag) {
  border: none !important;
  border-radius: 11px !important;
  font-size: 13px !important;
  font-weight: 400 !important;
  padding: 3px 11px !important;
  line-height: 1.25 !important;
  height: auto !important;

  &.el-tag--success {
    background-color: rgba($accent-green, 0.14) !important;
    color: #4ade80 !important;
  }
  &.el-tag--warning {
    background-color: rgba($accent-yellow, 0.14) !important;
    color: #facc15 !important;
  }
  &.el-tag--info {
    background-color: rgba(100, 116, 139, 0.18) !important;
    color: #cbd5e1 !important;
  }
}

// ===== 按钮背景与文字优雅调优 =====
// 1. 实心 Primary 按钮（深翠绿渐变背景 + 纯白高对比文字）
:deep(.el-button--primary:not(.is-plain):not(.is-link)) {
  background: linear-gradient(135deg, #10b981 0%, #059669 100%) !important;
  border-color: #059669 !important;
  color: #ffffff !important;
  font-weight: 500 !important;
  box-shadow: 0 2px 6px rgba(16, 185, 129, 0.25) !important;

  &:hover {
    background: linear-gradient(135deg, #34d399 0%, #10b981 100%) !important;
    border-color: #10b981 !important;
    color: #ffffff !important;
    box-shadow: 0 4px 10px rgba(16, 185, 129, 0.35) !important;
  }
}

// 2. 浅亮色 Plain Primary 按钮（浅靓清新绿底 + 沉稳高对比度深绿字）
:deep(.el-button--primary.is-plain) {
  background-color: #ecfdf5 !important;
  border-color: #a7f3d0 !important;
  color: #047857 !important;
  font-weight: 400 !important;

  &:hover {
    background-color: #d1fae5 !important;
    border-color: #6ee7b7 !important;
    color: #065f46 !important;
  }
}

// 3. 浅亮色 Plain Danger 按钮（浅亮浅红底 + 沉稳高对比度深红字）
:deep(.el-button--danger.is-plain) {
  background-color: #fef2f2 !important;
  border-color: #fca5a5 !important;
  color: #b91c1c !important;
  font-weight: 400 !important;

  &:hover {
    background-color: #fee2e2 !important;
    border-color: #f87171 !important;
    color: #991b1b !important;
  }
}

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
