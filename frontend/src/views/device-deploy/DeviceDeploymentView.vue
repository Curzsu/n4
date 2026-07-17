<template>
  <div class="device-deployment-view" v-loading="loading">
    <div class="card-container flex-layout">
      <!-- Case 1: Device Detail View is selected -> Custom read-only/editable deployment view (去监控化) -->
      <div v-if="store.selectedNode && store.selectedNode.type === 'device'" class="detail-wrapper">
        <div class="panel-header">
          <div class="header-title">
            <el-icon class="title-icon"><Connection /></el-icon>
            <span>已部署设备详情 - {{ deviceForm.deviceName }}</span>
          </div>
          <div class="action-buttons">
            <el-button type="primary" plain size="small" @click="goBackToList">返回列表</el-button>
            <el-button v-if="!isEditing" type="primary" plain size="small" :icon="Edit" @click="startEdit">修改设备与点位</el-button>
            <el-button v-else type="primary" plain size="small" :icon="Check" :loading="submitting" @click="submitUpdate">保存</el-button>
            <el-button type="danger" size="small" :icon="Delete" plain @click="deleteDevice">删除设备</el-button>
          </div>
        </div>

        <div class="detail-body">
          <!-- Device properties form -->
          <el-form 
            :model="deviceForm" 
            :rules="detailRules"
            ref="detailFormRef"
            label-width="80px"
            label-position="right"
            class="custom-form-compact"
            :disabled="!isEditing"
          >
            <el-row :gutter="20">
              <el-col :span="12">
                <el-form-item label="设备名称" prop="deviceName">
                  <el-input v-model="deviceForm.deviceName" />
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="设备编码" prop="deviceCode">
                  <el-input v-model="deviceForm.deviceCode" />
                </el-form-item>
              </el-col>
            </el-row>
            <el-row :gutter="20">
              <el-col :span="8">
                <el-form-item label="MAC 地址" prop="macAddress">
                  <el-input v-model="deviceForm.macAddress" />
                </el-form-item>
              </el-col>
              <el-col :span="8">
                <el-form-item label="安装日期">
                  <el-date-picker
                    v-model="deviceForm.installDate"
                    type="date"
                    format="YYYY-MM-DD"
                    value-format="YYYY-MM-DD"
                    style="width: 100%"
                  />
                </el-form-item>
              </el-col>
              <el-col :span="8">
                <el-form-item label="部署路径">
                  <el-input :value="deviceForm.sysPath" disabled />
                </el-form-item>
              </el-col>
            </el-row>
          </el-form>

          <!-- Extra device-level alarm configuration button -->
          <div class="extra-property-buttons">
            <el-button type="primary" plain size="small" :icon="Warning" @click="openDeviceAlarmConfig">
              配置设备本身报警
            </el-button>
          </div>

          <!-- Deployed Point list -->
          <div class="point-table-section">
            <div class="points-header">
              <div class="section-title">
                <el-icon class="section-icon"><List /></el-icon>
                <span>监测点位配置 (共 {{ devicePoints.length }} 个)</span>
              </div>
            </div>

            <el-table 
              :data="devicePoints" 
              style="width: 100%; margin-top: 12px;" 
              border 
              size="small"
              class="custom-table"
            >
              <el-table-column prop="deviceId" label="设备ID" min-width="180" show-overflow-tooltip />
              <el-table-column prop="pointName" label="点位名称" min-width="200" show-overflow-tooltip />
              <el-table-column prop="pointType" label="点位类型" min-width="140">
                <template #default="{ row }">
                  <span :class="['type-badge', getPointTypeClass(row.pointType)]">
                    {{ getPointTypeText(row.pointType) }}
                  </span>
                </template>
              </el-table-column>
              <el-table-column prop="unit" label="单位" width="90" align="center" />
              <el-table-column prop="displayPrecision" label="精度" width="80" align="center" />
              <el-table-column prop="registerId" label="寄存器地址" min-width="160" align="center">
                <template #default="{ row }">
                  <el-input 
                    v-if="isEditing" 
                    v-model="row.registerId" 
                    size="small"
                    placeholder="输入寄存器"
                  />
                  <code v-else class="addr-code">{{ row.registerId || 'N/A' }}</code>
                </template>
              </el-table-column>
              <el-table-column label="操作" width="120" fixed="right" align="center">
                <template #default="{ row }">
                  <el-button size="small" type="primary" link :icon="Warning" @click="openPointAlarmConfig(row)">报警设置</el-button>
                </template>
              </el-table-column>
            </el-table>
          </div>
        </div>
      </div>

      <!-- Case 2: System group/sub or root is selected -> Display list of deployed devices -->
      <div v-else class="list-wrapper">
        <div class="panel-header">
          <div class="header-title">
            <el-icon class="title-icon"><Connection /></el-icon>
            <span>
              已部署设备列表
              <span v-if="store.selectedNode && store.selectedNode.id !== 'root'" class="path-badge">
                {{ store.selectedNode.label }}
              </span>
            </span>
          </div>
          <div class="action-buttons">
            <!-- 批量管理状态 -->
            <template v-if="isBatchManaging">
              <el-button 
                type="danger" 
                size="small" 
                :icon="Delete"
                :disabled="selectedDevices.length === 0"
                @click="batchDeleteDevices"
              >
                批量删除 (已选 {{ selectedDevices.length }} 个)
              </el-button>
              <el-button 
                size="small"
                @click="exitBatchManage"
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
                :disabled="!store.selectedNode || store.selectedNode.id === 'root'"
                @click="enterBatchManage"
              >
                批量管理
              </el-button>
            </template>

            <el-button 
              type="primary" 
              plain
              size="small" 
              :icon="Plus"
              :disabled="!store.selectedNode || store.selectedNode.id === 'root'"
              @click="openDeployDialog"
            >
              新增部署设备
            </el-button>
          </div>
        </div>

        <div class="table-body">
          <div class="search-box-table">
            <el-input
              v-model="searchQuery"
              placeholder="搜索已部署设备名称或编码..."
              size="small"
              :prefix-icon="Search"
              clearable
              class="search-bar"
            />
          </div>

          <div class="path-info-tip" v-if="store.selectedNode && store.selectedNode.id !== 'root'">
            当前路径: <span class="text-green">{{ store.selectedNode.path }}</span>
          </div>
          <div class="path-info-tip text-muted" v-else>
            请先在左侧树结构中选择具体的系统分类或子路径节点。
          </div>

          <el-table 
            :data="filteredDevices" 
            style="width: 100%; flex: 1;" 
            border 
            size="small"
            class="custom-table"
            @selection-change="handleDeviceSelectionChange"
          >
            <el-table-column v-if="isBatchManaging" type="selection" width="45" align="center" />
            <el-table-column prop="deviceId" label="ID" width="100" align="center" />
            <el-table-column prop="deviceName" label="设备名称" min-width="160" show-overflow-tooltip>
              <template #default="{ row }">
                <el-link type="primary" class="device-link" @click="viewDeviceDetail(row)">
                  {{ row.deviceName }}
                </el-link>
              </template>
            </el-table-column>
            <el-table-column prop="deviceCode" label="设备编码 (SN)" width="150" show-overflow-tooltip />
            <el-table-column prop="macAddress" label="MAC 地址" width="150" align="center" />
            <el-table-column prop="sysPath" label="系统部署路径" min-width="180" show-overflow-tooltip />
            <el-table-column label="操作" width="120" align="center" fixed="right">
              <template #default="{ row }">
                <el-button 
                  type="primary" 
                  link 
                  size="small" 
                  @click="viewDeviceDetail(row)"
                >
                  编辑
                </el-button>
                <el-button 
                  type="danger" 
                  link 
                  size="small" 
                  @click="deleteDeviceFromTable(row)"
                >
                  删除
                </el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </div>
    </div>

    <!-- Deploy Dialog -->
    <el-dialog
      v-model="dialogVisible"
      title="在当前路径部署新设备"
      width="540px"
      append-to-body
      class="dark-dialog"
    >
      <el-form 
        :model="form" 
        :rules="rules" 
        ref="formRef" 
        label-width="110px"
        label-position="right"
        size="small"
        class="custom-form"
      >
        <el-form-item label="当前部署路径">
          <el-input :value="store.selectedNode?.path" disabled />
        </el-form-item>

        <el-form-item label="选择设备模板" prop="productId">
          <el-select v-model="form.productId" placeholder="请选择对应设备模板..." style="width: 100%">
            <el-option
              v-for="p in store.allProducts"
              :key="p.productId"
              :label="`${p.productName} (${p.productType || '未知'} - ${p.model || '无型号'})`"
              :value="p.productId!"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="设备部署名称" prop="deviceName">
          <el-input v-model="form.deviceName" placeholder="例如：J1_LVP_A1_UPS_01" />
        </el-form-item>

        <el-form-item label="设备编码 (SN)" prop="deviceCode">
          <el-input v-model="form.deviceCode" placeholder="例如：SN_UPS_001_A1" />
        </el-form-item>

        <el-form-item label="MAC 地址" prop="macAddress">
          <el-input v-model="form.macAddress" placeholder="例如：00:1A:2B:3C:4D:5E" />
        </el-form-item>

        <el-form-item label="安装日期">
          <el-date-picker
            v-model="form.installDate"
            type="date"
            placeholder="选择安装日期"
            format="YYYY-MM-DD"
            value-format="YYYY-MM-DD"
            style="width: 100%"
          />
        </el-form-item>
      </el-form>

      <template #footer>
        <span class="dialog-footer">
          <el-button size="small" @click="dialogVisible = false">取消</el-button>
          <el-button size="small" type="primary" @click="submitDeploy" :loading="submitting">立即部署</el-button>
        </span>
      </template>
    </el-dialog>

    <!-- Move Deployed Devices Dialog (Supports Single & Batch Move) -->
    <el-dialog
      v-model="moveDialogVisible"
      :title="isBatchMove ? '批量移动设备部署位置' : '移动设备部署位置'"
      width="540px"
      append-to-body
      class="dark-dialog"
    >
      <div style="margin-bottom: 12px; font-size: 13px;" class="text-secondary" v-if="!isBatchMove">
        当前设备: <strong style="color: #10b981;">{{ deviceForm.deviceName }}</strong>
      </div>
      <div style="margin-bottom: 12px; font-size: 13px;" class="text-secondary" v-else>
        已选择设备: <span style="color: #10b981;">共 {{ selectedDevices.length }} 个设备</span>
      </div>
      <div style="margin-bottom: 12px; font-size: 13px;" class="text-secondary" v-if="!isBatchMove">
        当前路径: <span class="text-muted">{{ deviceForm.sysPath }}</span>
      </div>

      <div style="border: 1px solid #1f2937; border-radius: 6px; padding: 10px; max-height: 200px; overflow-y: auto; background-color: #0b1119; margin-bottom: 16px;">
        <el-tree
          :data="store.deviceTree[0] ? store.deviceTree[0].children : []"
          :props="{ label: 'label', children: 'children' }"
          node-key="path"
          @node-click="handleMovePathSelect"
          highlight-current
        />
      </div>

      <div style="margin-top: 16px; font-size: 13px;" v-if="moveTargetPath">
        目标系统路径: <strong style="color: #10b981;">{{ moveTargetPath }}</strong>
        
        <!-- Single Move Info -->
        <div style="margin-top: 12px;" v-if="!isBatchMove">
          移动后设备名称 (根据规则自动重构):
          <el-input v-model="moveNewName" size="small" style="margin-top: 6px;" />
        </div>
        
        <!-- Batch Move Preview -->
        <div style="margin-top: 12px;" v-else>
          <div style="margin-bottom: 6px; font-weight: 600;">移动后设备更名预览:</div>
          <div style="max-height: 150px; overflow-y: auto; border: 1px solid #2d3748; padding: 8px; border-radius: 4px; background-color: #111b27;">
            <div 
              v-for="d in selectedDevices" 
              :key="d.deviceId" 
              style="font-size: 11px; margin-bottom: 6px; display: flex; justify-content: space-between; align-items: center;"
            >
              <span class="text-muted" style="text-overflow: ellipsis; overflow: hidden; white-space: nowrap; max-width: 200px;">
                {{ d.deviceName }}
              </span>
              <el-icon style="margin: 0 8px;"><Right /></el-icon>
              <span style="color: #10b981; font-weight: 500; text-overflow: ellipsis; overflow: hidden; white-space: nowrap; max-width: 200px;">
                {{ regenerateDeviceName(d.deviceName, d.sysPath, moveTargetPath) }}
              </span>
            </div>
          </div>
        </div>
      </div>
      <template #footer>
        <el-button size="small" @click="moveDialogVisible = false">取消</el-button>
        <el-button size="small" type="primary" :disabled="!moveTargetPath" @click="submitMove" :loading="moveSubmitting">确认移动</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, watch, nextTick } from 'vue'
import { Plus, Search, Connection, Edit, Check, Delete, List, Warning, Right } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { useDashboardStore } from '@/stores/dashboard'
import { deviceApi } from '@/api/device'

const store = useDashboardStore()
const loading = ref(false)
const submitting = ref(false)
const dialogVisible = ref(false)
const searchQuery = ref('')
const allDevicesList = ref<any[]>([])

// Device details & edit states
const deviceForm = ref<any>({})
const devicePoints = ref<any[]>([])
const isEditing = ref(false)

// Move device states
const moveDialogVisible = ref(false)
const moveTargetPath = ref('')
const moveNewName = ref('')
const moveSubmitting = ref(false)
const isBatchMove = ref(false)
const selectedDevices = ref<any[]>([])
const isBatchManaging = ref(false)

function handleDeviceSelectionChange(val: any[]) {
  selectedDevices.value = val
}

function enterBatchManage() {
  isBatchManaging.value = true
  selectedDevices.value = []
}

function exitBatchManage() {
  isBatchManaging.value = false
  selectedDevices.value = []
}

const formRef = ref<FormInstance>()
const form = ref({
  deviceName: '',
  deviceCode: '',
  productId: null as number | null,
  ipAddress: '',
  macAddress: '',
  installDate: '',
  sysPath: ''
})

const validateMacAddress = (_rule: any, value: any, callback: any) => {
  if (value && !/^([0-9A-Fa-f]{2}[:-]){5}([0-9A-Fa-f]{2})$/.test(value)) {
    callback(new Error('MAC地址格式无效，应如 00:1A:2B:3C:4D:5E'))
    return
  }
  callback()
}

const rules = ref<FormRules>({
  productId: [{ required: true, message: '请选择设备所属模板', trigger: 'change' }],
  deviceName: [{ required: true, message: '请输入部署设备名称', trigger: 'blur' }],
  deviceCode: [{ required: true, message: '请输入设备唯一编码/SN码', trigger: 'blur' }],
  macAddress: [{ validator: validateMacAddress, trigger: 'blur' }]
})

const detailFormRef = ref<FormInstance>()
const detailRules = ref<FormRules>({
  deviceName: [{ required: true, message: '请输入设备名称', trigger: 'blur' }],
  deviceCode: [{ required: true, message: '请输入设备编码/SN码', trigger: 'blur' }],
  macAddress: [{ validator: validateMacAddress, trigger: 'blur' }]
})

// Load all devices from backend
async function fetchDevices() {
  loading.value = true
  try {
    allDevicesList.value = await deviceApi.getAllDevices()
  } catch (err: any) {
    console.error('Failed to load deployed devices:', err)
    ElMessage.error(err.message || '加载已部署设备列表失败')
  } finally {
    loading.value = false
  }
}

// Reactively filter devices based on selected path and search query
const filteredDevices = computed(() => {
  // Filter out any hidden template devices (starts with /TEMPLATES)
  let list = allDevicesList.value.filter(d => !d.sysPath || !d.sysPath.startsWith('/TEMPLATES'))
  
  if (store.selectedNode && store.selectedNode.id !== 'root') {
    const selectedPath = store.selectedNode.path || ''
    if (selectedPath) {
      list = list.filter(d => d.sysPath && d.sysPath.startsWith(selectedPath))
    }
  }

  const query = searchQuery.value.trim().toLowerCase()
  if (query) {
    list = list.filter(d => 
      (d.deviceName && d.deviceName.toLowerCase().includes(query)) ||
      (d.deviceCode && d.deviceCode.toLowerCase().includes(query))
    )
  }
  return list
})

// Watch selectedNode to automatically fetch device properties and points
watch(() => store.selectedNode, async (newNode) => {
  isBatchManaging.value = false
  selectedDevices.value = []
  if (newNode && newNode.type === 'device') {
    isEditing.value = false
    if (allDevicesList.value.length === 0) {
      await fetchDevices()
    }
    const found = allDevicesList.value.find(d => d.deviceId === newNode.id)
    if (found) {
      deviceForm.value = { ...found }
      try {
        devicePoints.value = await deviceApi.getDevicePoints(found.deviceId)
      } catch (e: any) {
        console.error(e)
        devicePoints.value = []
        ElMessage.error(e.message || '获取设备点位列表失败')
      }
    } else {
      ElMessage.warning('未找到对应的已部署设备数据，请尝试刷新页面')
      deviceForm.value = {}
      devicePoints.value = []
    }
  } else {
    isEditing.value = false
  }
})

// Open deploy dialog
function openDeployDialog() {
  if (!store.selectedNode || store.selectedNode.id === 'root') {
    ElMessage.warning('请先从左侧的系统/位置树选择具体的路径！')
    return
  }
  
  form.value = {
    deviceName: '',
    deviceCode: '',
    productId: null,
    ipAddress: '',
    macAddress: '',
    installDate: '',
    sysPath: store.selectedNode.path || ''
  }
  
  dialogVisible.value = true
  nextTick(() => {
    if (formRef.value) {
      formRef.value.clearValidate()
    }
  })
}

// View device details on link click
function viewDeviceDetail(row: any) {
  store.selectedNode = {
    id: row.deviceId,
    label: row.deviceName,
    type: 'device',
    status: row.status,
    path: row.sysPath,
    children: []
  }
}

function goBackToList() {
  store.selectedNode = null
}

function startEdit() {
  isEditing.value = true
}

// Save device details and inline point edits
async function submitUpdate() {
  if (!deviceForm.value.deviceId) {
    ElMessage.error('无法保存: 缺少设备ID')
    return
  }

  if (!detailFormRef.value) return
  
  try {
    await detailFormRef.value.validate()
  } catch (err) {
    // Form validation failed - Element Plus already highlights the fields in the UI
    return
  }

  // Format validation for all point registerIds
  const regIds = new Set<string>()
  for (const p of devicePoints.value) {
    const regVal = p.registerId ? String(p.registerId).trim() : ''
    if (!regVal) {
      ElMessage.error({
        message: `点位 [${p.pointName}] 的寄存器地址不能为空`,
        showClose: true,
        duration: 10000
      })
      return
    }
    if (!/^(0x|0X)?[0-9a-fA-F]+$/.test(regVal)) {
      ElMessage.error({
        message: `点位 [${p.pointName}] 的寄存器地址 [${regVal}] 必须是十进制数字或十六进制数（如 40001 或 0x011A）`,
        showClose: true,
        duration: 10000
      })
      return
    }
    if (regIds.has(regVal)) {
      ElMessage.error({
        message: `寄存器地址 [${regVal}] 重复配置，请检查修改`,
        showClose: true,
        duration: 10000
      })
      return
    }
    regIds.add(regVal)
  }

  try {
    submitting.value = true
    
    // Strip audit fields to prevent Jackson deserialization issues on backend
    const payload = { ...deviceForm.value }
    delete payload.createTime
    delete payload.updateTime
    delete payload.isDeleted
    
    // 1. Update device properties
    await deviceApi.updateDevice(deviceForm.value.deviceId, payload)
    
    // 2. Batch update modified registerIds
    for (const p of devicePoints.value) {
      await deviceApi.updatePoint(p.pointId, p)
    }
    
    ElMessage.success('更新设备属性与点位寄存器成功')
    isEditing.value = false
    await fetchDevices()
    
    // Sync node label in tree
    if (store.selectedNode) {
      store.selectedNode.label = deviceForm.value.deviceName
    }
  } catch (err: any) {
    ElMessage.error(err.message || '更新失败')
  } finally {
    submitting.value = false
  }
}

// Delete device
function deleteDevice() {
  ElMessageBox.confirm(`确认删除已部署设备 “${deviceForm.value.deviceName}” 吗？该操作将物理删除该设备及其所有运行测点。`, '删除设备确认', {
    confirmButtonText: '确定删除',
    cancelButtonText: '取消',
    type: 'warning',
    confirmButtonClass: 'el-button--danger'
  }).then(async () => {
    try {
      loading.value = true
      await deviceApi.deleteDevice(deviceForm.value.deviceId)
      ElMessage.success('删除设备成功')
      store.selectedNode = null
      await store.fetchAllDashboardData()
      await fetchDevices()
    } catch (e: any) {
      ElMessage.error(e.message || '删除设备失败')
    } finally {
      loading.value = false
    }
  }).catch(() => {})
}

function deleteDeviceFromTable(row: any) {
  ElMessageBox.confirm(`确认删除已部署设备 “${row.deviceName}” 吗？该操作将物理删除该设备及其所有运行测点。`, '删除设备确认', {
    confirmButtonText: '确定删除',
    cancelButtonText: '取消',
    type: 'warning',
    confirmButtonClass: 'el-button--danger'
  }).then(async () => {
    try {
      loading.value = true
      await deviceApi.deleteDevice(row.deviceId)
      ElMessage.success('删除设备成功')
      if (store.selectedNode && store.selectedNode.id === row.deviceId) {
        store.selectedNode = null
      }
      await store.fetchAllDashboardData()
      await fetchDevices()
    } catch (e: any) {
      ElMessage.error(e.message || '删除设备失败')
    } finally {
      loading.value = false
    }
  }).catch(() => {})
}

// Move dialog helper functions deleted as requested

function handleMovePathSelect(node: any) {
  if (node && node.path) {
    moveTargetPath.value = node.path
  }
}

// Watch target path to auto-remap names
watch(moveTargetPath, (newPath) => {
  if (newPath && !isBatchMove.value) {
    moveNewName.value = regenerateDeviceName(deviceForm.value.deviceName, deviceForm.value.sysPath, newPath)
  }
})

// Automated device name remapping
function regenerateDeviceName(originalName: string, oldPath: string, newPath: string): string {
  const oldSegments = oldPath?.split('/').filter(Boolean) || []
  const newSegments = newPath?.split('/').filter(Boolean) || []
  if (oldSegments.length > 0 && newSegments.length > 0) {
    const oldLast = oldSegments[oldSegments.length - 2] || oldSegments[0]
    const newLast = newSegments[newSegments.length - 1]
    
    if (oldLast && newLast && oldLast !== newLast) {
      if (originalName.includes(oldLast)) {
        return originalName.replace(oldLast, newLast)
      }
      const oldLastUpper = oldLast.toUpperCase()
      const newLastUpper = newLast.toUpperCase()
      if (originalName.toUpperCase().includes(oldLastUpper)) {
        const regex = new RegExp(oldLastUpper, 'gi')
        return originalName.replace(regex, newLastUpper)
      }
    }
  }
  return originalName
}

// Submit move (Supports Single & Batch Move)
async function submitMove() {
  try {
    moveSubmitting.value = true
    if (isBatchMove.value) {
      for (const d of selectedDevices.value) {
        const newName = regenerateDeviceName(d.deviceName, d.sysPath, moveTargetPath.value)
        await deviceApi.moveDevice(d.deviceId, moveTargetPath.value, newName)
      }
      ElMessage.success(`批量移动 ${selectedDevices.value.length} 个设备成功`)
    } else {
      await deviceApi.moveDevice(deviceForm.value.deviceId, moveTargetPath.value, moveNewName.value)
      ElMessage.success('移动设备位置成功')
    }
    
    moveDialogVisible.value = false
    store.selectedNode = null
  } catch (err: any) {
    ElMessage.error(err.message || '移动设备失败')
  } finally {
    selectedDevices.value = []
    isBatchManaging.value = false
    await store.fetchAllDashboardData()
    await fetchDevices()
    moveSubmitting.value = false
  }
}

// Batch delete devices
function batchDeleteDevices() {
  ElMessageBox.confirm(`确认删除选中的 ${selectedDevices.value.length} 个已部署设备吗？该操作将物理删除这些设备及其所有运行测点。`, '批量删除确认', {
    confirmButtonText: '确定删除',
    cancelButtonText: '取消',
    type: 'warning',
    confirmButtonClass: 'el-button--danger'
  }).then(async () => {
    try {
      loading.value = true
      for (const d of selectedDevices.value) {
        await deviceApi.deleteDevice(d.deviceId)
      }
      ElMessage.success('批量删除设备成功')
    } catch (e: any) {
      ElMessage.error(e.message || '批量删除失败')
    } finally {
      selectedDevices.value = []
      isBatchManaging.value = false
      await store.fetchAllDashboardData()
      await fetchDevices()
      loading.value = false
    }
  }).catch(() => {})
}

// Submit deployed device
async function submitDeploy() {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (valid) {
      if (!store.selectedNode?.path) {
        ElMessage.warning('当前选择的路径无效，请重新在左侧树结构中选择！')
        return
      }
      submitting.value = true
      const payload = {
        ...form.value,
        sysPath: store.selectedNode.path + '/' + form.value.deviceName,
        locPath: store.selectedNode.path + '/' + form.value.deviceName
      }

      try {
        await deviceApi.createDevice(payload)
        ElMessage.success('部署新设备并初始化点位成功！')
        dialogVisible.value = false
        await store.fetchAllDashboardData()
        await fetchDevices()
      } catch (err: any) {
        ElMessage.error(err.message || '部署设备失败')
      } finally {
        submitting.value = false
      }
    }
  })
}

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

function openDeviceAlarmConfig() {
  ElMessageBox.alert(`当前正在为该设备配置报警规则：当设备离线或心跳失效时，下属测点全部报通信异常故障。`, '配置设备本身报警', {
    confirmButtonText: '确定'
  })
}

function openPointAlarmConfig(row: any) {
  ElMessageBox.alert(`当前正在为部署测点 [${row.pointName}] 配置报警规则：高低限阈值触发、报警推送通道等。`, '配置点位报警', {
    confirmButtonText: '确定'
  })
}

// Load data on mount
onMounted(async () => {
  await fetchDevices()
  if (store.allProducts.length === 0 || store.deviceTree.length <= 1) {
    await store.fetchAllDashboardData()
  }
})
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

.device-deployment-view {
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

.flex-layout {
  height: 100%;
}

.detail-wrapper {
  height: 100%;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
}

.list-wrapper {
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

  .path-badge {
    background-color: rgba($accent-green, 0.15);
    color: $accent-green;
    font-size: 11px;
    padding: 2px 8px;
    border-radius: 12px;
    font-weight: 500;
    margin-left: 8px;
  }
}

.detail-body {
  padding: 24px;
  flex: 1;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
}

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

.path-info-tip {
  font-size: 12px;
  color: $text-secondary;
  margin-top: 4px;
  
  .text-green {
    color: $accent-green;
    font-weight: 600;
  }
}

.text-muted {
  color: $text-muted;
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

.extra-property-buttons {
  display: flex;
  gap: 12px;
  margin-bottom: 16px;
  padding-bottom: 12px;
  border-bottom: 1px dashed rgba($border-color, 0.6);
  flex-shrink: 0;
}

.point-table-section {
  margin-top: 16px;
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.points-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
  flex-shrink: 0;
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

.device-link {
  font-weight: 500;
  cursor: pointer;
}

.addr-code {
  background-color: rgba($text-muted, 0.1);
  padding: 2px 6px;
  border-radius: 4px;
  font-family: monospace;
  color: $text-secondary;
  font-size: 12px;
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

.action-buttons {
  display: flex;
  align-items: center;
  gap: 8px;
}

.custom-form {
  :deep(.el-form-item__label) {
    color: $text-secondary;
    font-weight: 500;
  }
  :deep(.el-input__wrapper) {
    background-color: $bg-dark !important;
  }
}

:deep(.el-dialog) {
  background-color: #151d2a !important;
  border: 1px solid #1f2937;
  border-radius: 8px;
  
  .el-dialog__title {
    color: $text-primary;
    font-size: 15px;
    font-weight: 600;
  }
  
  .el-dialog__body {
    border-top: 1px solid #1f2937;
    border-bottom: 1px solid #1f2937;
    padding: 20px 24px;
  }
}
</style>
