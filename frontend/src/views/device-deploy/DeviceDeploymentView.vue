<template>
  <div class="device-deployment-view" v-loading="loading">
    <div class="deploy-flex">
      <!-- 左侧导航树（nav_node + device_deployment 真实数据） -->
      <div class="tree-panel">
        <div class="tree-panel-header">
          <span class="tree-title">部署导航结构</span>
          <el-button size="small" text :icon="Refresh" @click="loadTree">刷新</el-button>
        </div>
        <div class="tree-body" v-loading="treeLoading">
          <el-tree
            :data="treeNodes"
            :props="{ label: 'label', children: 'children' }"
            node-key="nodeId"
            highlight-current
            :expand-on-click-node="false"
            @node-click="handleNodeClick"
          >
            <template #default="{ data }">
              <span class="tree-node">
                <el-icon v-if="getNodeIcon(data)" :class="['node-icon', { 'node-icon-real': data.hasRealData }]">
                  <component :is="getNodeIcon(data)" />
                </el-icon>
                <span class="node-label">{{ data.label }}</span>
                <span v-if="data.deviceCount != null" class="node-count">{{ data.deviceCount }}</span>
              </span>
            </template>
          </el-tree>
        </div>
      </div>

      <div class="card-container flex-layout">
        <!-- Case 1: Device Detail View -->
        <div v-if="selectedNode && selectedNode.type === 'device'" class="detail-wrapper">
          <div class="panel-header">
            <div class="header-title">
              <el-icon class="title-icon"><Connection /></el-icon>
              <span>已部署设备详情 - {{ deviceForm.deviceDeployName }}</span>
              <el-tag v-if="deviceForm.isMock" size="small" type="info" class="data-tag">示例数据</el-tag>
              <el-tag v-else size="small" type="success" effect="dark" class="data-tag">真实数据</el-tag>
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
                  <el-form-item label="设备名称" prop="deviceDeployName">
                    <el-input v-model="deviceForm.deviceDeployName" />
                  </el-form-item>
                </el-col>
                <el-col :span="12">
                  <el-form-item label="设备别名">
                    <el-input v-model="deviceForm.deviceAlias" />
                  </el-form-item>
                </el-col>
              </el-row>
              <el-row :gutter="20">
                <el-col :span="8">
                  <el-form-item label="设备模板">
                    <el-select v-model="deviceForm.deviceTmplId" placeholder="选择设备模板" style="width: 100%">
                      <el-option
                        v-for="t in store.allTemplates"
                        :key="t.deviceTmplId"
                        :label="t.deviceTmplName"
                        :value="t.deviceTmplId!"
                      />
                    </el-select>
                  </el-form-item>
                </el-col>
                <el-col :span="8">
                  <el-form-item label="模板类型">
                    <el-input :value="deviceForm.deviceType || '-'" disabled />
                  </el-form-item>
                </el-col>
                <el-col :span="8">
                  <el-form-item label="部署节点">
                    <el-select v-model="deviceForm.navNodeId" placeholder="选择部署节点" style="width: 100%">
                      <el-option
                        v-for="n in nodeOptions"
                        :key="n.nodeId"
                        :label="n.nodeName"
                        :value="n.nodeId"
                      />
                    </el-select>
                  </el-form-item>
                </el-col>
              </el-row>
              <el-row :gutter="20">
                <el-col :span="8">
                  <el-form-item label="IP 地址">
                    <el-input v-model="deviceForm.ipAddress" />
                  </el-form-item>
                </el-col>
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
              </el-row>
              <el-row :gutter="20">
                <el-col :span="24">
                  <el-form-item label="N4 Topic">
                    <el-input :value="deviceForm.n4Address" disabled />
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
                <el-table-column prop="pointName" label="点位名称" min-width="220" show-overflow-tooltip />
                <el-table-column prop="pointType" label="点位类型" min-width="110">
                  <template #default="{ row }">
                    <span :class="['type-badge', getPointTypeClass(row.pointType)]">
                      {{ getPointTypeText(row.pointType) }}
                    </span>
                  </template>
                </el-table-column>
                <el-table-column prop="dataType" label="数据类型" width="100" align="center" />
                <el-table-column prop="unit" label="单位" width="80" align="center" />
                <el-table-column label="数据来源" width="110" align="center">
                  <template #default="{ row }">
                    <el-tag size="small" :type="row.isReal ? 'success' : 'warning'" effect="dark">
                      {{ row.isReal ? '真实数据' : 'Mock' }}
                    </el-tag>
                  </template>
                </el-table-column>
                <el-table-column prop="register" label="寄存器地址" min-width="160" align="center">
                  <template #default="{ row }">
                    <el-input 
                      v-if="isEditing" 
                      v-model="row.register" 
                      size="small"
                      placeholder="输入寄存器"
                    />
                    <code v-else class="addr-code">{{ row.register || 'N/A' }}</code>
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

        <!-- Case 2: List view -->
        <div v-else class="list-wrapper">
          <div class="panel-header">
            <div class="header-title">
              <el-icon class="title-icon"><Connection /></el-icon>
              <span>
                已部署设备列表
                <span v-if="selectedNode && selectedNode.type !== 'root'" class="path-badge">
                  {{ selectedNode.label }}
                </span>
              </span>
            </div>
            <div class="action-buttons">
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
                <el-button size="small" @click="exitBatchManage">退出管理</el-button>
              </template>
              <template v-else>
                <el-button 
                  type="primary"
                  plain
                  size="small" 
                  :icon="Edit"
                  :disabled="!selectedNode || selectedNode.type === 'root'"
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
                :disabled="!selectedNode || selectedNode.type === 'root'"
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
                placeholder="搜索已部署设备名称或别名..."
                size="small"
                :prefix-icon="Search"
                clearable
                class="search-bar"
              />
            </div>

            <div class="path-info-tip" v-if="selectedNode && selectedNode.type !== 'root'">
              当前节点: <span class="text-green">{{ selectedNode.label }}</span>
              <span class="text-muted">（{{ currentDevices.length }} 台设备，真实 {{ realCount }} 台）</span>
            </div>
            <div class="path-info-tip text-muted" v-else>
              请先在左侧导航树中选择具体的部署节点。
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
              <el-table-column prop="deviceDeployId" label="ID" width="70" align="center" />
              <el-table-column prop="deviceDeployName" label="设备名称" min-width="220" show-overflow-tooltip>
                <template #default="{ row }">
                  <el-link type="primary" class="device-link" @click="viewDeviceDetail(row as DeviceDeployment)">
                    {{ row.deviceDeployName }}
                  </el-link>
                  <el-tag v-if="row.isMock" size="small" type="info" class="data-tag">示例数据</el-tag>
                  <el-tag v-else size="small" type="success" effect="dark" class="data-tag">真实数据</el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="deviceAlias" label="设备别名" width="150" show-overflow-tooltip />
              <el-table-column prop="deviceType" label="模板类型" width="110" align="center" />
              <el-table-column prop="manufacturer" label="厂商" width="110" align="center" />
              <el-table-column label="状态" width="90" align="center">
                <template #default="{ row }">
                  <span class="status-dot" :class="getStatusClass(row.status)"></span>
                  <span class="status-text">{{ getStatusText(row.status) }}</span>
                </template>
              </el-table-column>
              <el-table-column label="操作" width="150" align="center" fixed="right">
                <template #default="{ row }">
                  <el-button type="primary" link size="small" @click="viewDeviceDetail(row as DeviceDeployment)">编辑</el-button>
                  <el-button type="warning" link size="small" @click="openMoveDialog(row as DeviceDeployment)">移动</el-button>
                  <el-button type="danger" link size="small" @click="deleteDeviceFromTable(row as DeviceDeployment)">删除</el-button>
                </template>
              </el-table-column>
            </el-table>
          </div>
        </div>
      </div>
    </div>

    <!-- Deploy Dialog -->
    <el-dialog
      v-model="dialogVisible"
      title="在当前节点部署新设备"
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
        <el-form-item label="当前部署节点">
          <el-input :value="selectedNode?.label" disabled />
        </el-form-item>

        <el-form-item label="选择设备模板" prop="deviceTmplId">
          <el-select v-model="form.deviceTmplId" placeholder="请选择对应设备模板..." style="width: 100%">
            <el-option
              v-for="t in store.allTemplates"
              :key="t.deviceTmplId"
              :label="`${t.deviceTmplName} (${t.deviceType}${t.isReal ? '' : ' - Mock'})`"
              :value="t.deviceTmplId!"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="设备部署名称" prop="deviceDeployName">
          <el-input v-model="form.deviceDeployName" placeholder="例如：A1_PTU_A1_LVP_A1_00_Collector_00" />
        </el-form-item>

        <el-form-item label="设备别名">
          <el-input v-model="form.deviceAlias" placeholder="默认同部署名称" />
        </el-form-item>

        <el-form-item label="IP 地址">
          <el-input v-model="form.ipAddress" placeholder="例如：192.168.1.100" />
        </el-form-item>

        <el-form-item label="MAC 地址" prop="macAddress">
          <el-input v-model="form.macAddress" placeholder="例如：00:1A:2B:3C:4D:5E" />
        </el-form-item>

        <el-form-item label="安装日期">
          <el-date-picker
            v-model="form.installDate"
            type="date"
            format="YYYY-MM-DD"
            value-format="YYYY-MM-DD"
            style="width: 100%"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button size="small" @click="dialogVisible = false">取消</el-button>
        <el-button size="small" type="primary" :loading="submitting" @click="submitDeploy">部署并生成点位</el-button>
      </template>
    </el-dialog>

    <!-- Move Deployed Devices Dialog -->
    <el-dialog
      v-model="moveDialogVisible"
      :title="isBatchMove ? '批量移动设备部署位置' : '移动设备部署位置'"
      width="540px"
      append-to-body
      class="dark-dialog"
    >
      <div style="margin-bottom: 12px; font-size: 13px;" class="text-secondary" v-if="!isBatchMove">
        当前设备: <strong style="color: #10b981;">{{ moveDeviceName }}</strong>
      </div>
      <div style="margin-bottom: 12px; font-size: 13px;" class="text-secondary" v-else>
        已选择设备: <span style="color: #10b981;">共 {{ selectedDevices.length }} 个设备</span>
      </div>

      <div style="border: 1px solid #1f2937; border-radius: 6px; padding: 10px; max-height: 200px; overflow-y: auto; background-color: #0b1119; margin-bottom: 16px;">
        <el-tree
          :data="treeNodes"
          :props="{ label: 'label', children: 'children' }"
          node-key="nodeId"
          @node-click="handleMoveTargetSelect"
          highlight-current
        />
      </div>

      <div style="margin-top: 16px; font-size: 13px;" v-if="moveTargetNode">
        目标节点: <strong style="color: #10b981;">{{ moveTargetNode.nodeName }}</strong>
        
        <div style="margin-top: 12px;" v-if="!isBatchMove">
          移动后设备名称:
          <el-input v-model="moveNewName" size="small" style="margin-top: 6px;" />
          <div style="margin-top: 4px; font-size: 11px; color: #64748b;">
            提示：修改名称时点位名称与 N4 Topic 地址会自动同步。
          </div>
        </div>
      </div>
      <template #footer>
        <el-button size="small" @click="moveDialogVisible = false">取消</el-button>
        <el-button size="small" type="primary" :disabled="!moveTargetNode" @click="submitMove" :loading="moveSubmitting">确认移动</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, nextTick } from 'vue'
import { Plus, Search, Connection, Edit, Check, Delete, List, Warning, Refresh, DataLine, Location, FolderOpened } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { useDashboardStore } from '@/stores/dashboard'
import { deploymentApi } from '@/api/deployment'
import { dashboardNavApi } from '@/api/dashboardNav'
import type { NavTreeNode, DeviceDeployment } from '@/api/types'

const store = useDashboardStore()
const loading = ref(false)
const submitting = ref(false)
const dialogVisible = ref(false)
const searchQuery = ref('')

// ===== 导航树 =====
const treeLoading = ref(false)
const treeData = ref<NavTreeNode[]>([])
const selectedNode = ref<any>(null)

const treeNodes = computed(() => treeData.value.map(toTreeNode))

function toTreeNode(n: NavTreeNode): any {
  return {
    nodeId: n.nodeId,
    nodeName: n.nodeName,
    label: n.nodeName,
    deviceCount: n.deviceCount,
    hasRealData: !!n.hasRealData,
    children: (n.children || []).map(toTreeNode)
  }
}

function findNodeById(nodes: NavTreeNode[], id: number): NavTreeNode | null {
  for (const n of nodes) {
    if (n.nodeId === id) return n
    if (n.children) {
      const found = findNodeById(n.children, id)
      if (found) return found
    }
  }
  return null
}

const nodeOptions = computed(() => {
  const out: { nodeId: number; nodeName: string }[] = []
  const walk = (nodes: NavTreeNode[], prefix: string) => {
    for (const n of nodes) {
      out.push({ nodeId: n.nodeId, nodeName: prefix ? `${prefix}/${n.nodeName}` : n.nodeName })
      if (n.children) walk(n.children, prefix ? `${prefix}/${n.nodeName}` : n.nodeName)
    }
  }
  walk(treeData.value, '')
  return out
})

function getNodeIcon(data: any) {
  if (data.hasRealData) return DataLine
  if (data.nodeId) return FolderOpened
  return Location
}

async function loadTree() {
  treeLoading.value = true
  try {
    treeData.value = await dashboardNavApi.getNavTree()
  } catch (e: any) {
    ElMessage.error(e.message || '加载导航树失败')
  } finally {
    treeLoading.value = false
  }
}

async function handleNodeClick(data: any) {
  selectedNode.value = { ...data, type: 'group', path: data.label }
  if (data.deviceCount != null) {
    await loadNodeDevices(data.nodeId)
  } else {
    currentDevices.value = []
  }
}

// ===== 设备列表 =====
const currentDevices = ref<DeviceDeployment[]>([])

const realCount = computed(() => currentDevices.value.filter(d => !d.isMock).length)

async function loadNodeDevices(nodeId: number) {
  loading.value = true
  try {
    currentDevices.value = await deploymentApi.listByNode(nodeId)
  } catch (e: any) {
    ElMessage.error(e.message || '加载设备列表失败')
  } finally {
    loading.value = false
  }
}

const filteredDevices = computed(() => {
  let list = currentDevices.value
  const query = searchQuery.value.trim().toLowerCase()
  if (query) {
    list = list.filter(d =>
      (d.deviceDeployName && d.deviceDeployName.toLowerCase().includes(query)) ||
      (d.deviceAlias && d.deviceAlias.toLowerCase().includes(query))
    )
  }
  return list
})

// Batch manage states
const selectedDevices = ref<DeviceDeployment[]>([])
const isBatchManaging = ref(false)

function handleDeviceSelectionChange(val: DeviceDeployment[]) {
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

// ===== 详情/编辑 =====
const deviceForm = ref<any>({})
const devicePoints = ref<any[]>([])
const isEditing = ref(false)

const detailFormRef = ref<FormInstance>()
const detailRules = ref<FormRules>({
  deviceDeployName: [{ required: true, message: '请输入设备名称', trigger: 'blur' }],
  macAddress: [{ validator: validateMacAddress, trigger: 'blur' }]
})

async function viewDeviceDetail(row: DeviceDeployment) {
  loading.value = true
  try {
    const detail = await deploymentApi.getDetail(row.deviceDeployId!)
    deviceForm.value = { ...detail }
    selectedNode.value = {
      nodeId: row.deviceDeployId,
      label: row.deviceDeployName,
      type: 'device'
    }
    devicePoints.value = await deploymentApi.getPoints(row.deviceDeployId!)
    isEditing.value = false
  } catch (e: any) {
    ElMessage.error(e.message || '加载设备详情失败')
  } finally {
    loading.value = false
  }
}

function goBackToList() {
  selectedNode.value = null
}

function startEdit() {
  isEditing.value = true
}

function validateMacAddress(_rule: any, value: any, callback: any) {
  if (value && !/^([0-9A-Fa-f]{2}[:-]){5}([0-9A-Fa-f]{2})$/.test(value)) {
    callback(new Error('MAC地址格式无效，应如 00:1A:2B:3C:4D:5E'))
    return
  }
  callback()
}

async function submitUpdate() {
  if (!deviceForm.value.deviceDeployId) {
    ElMessage.error('无法保存: 缺少设备ID')
    return
  }
  if (!detailFormRef.value) return
  try {
    await detailFormRef.value.validate()
  } catch {
    return
  }
  try {
    submitting.value = true
    const payload = {
      deviceTmplId: deviceForm.value.deviceTmplId,
      navNodeId: deviceForm.value.navNodeId,
      deviceDeployName: deviceForm.value.deviceDeployName,
      deviceAlias: deviceForm.value.deviceAlias,
      alarmCode: deviceForm.value.alarmCode,
      ipAddress: deviceForm.value.ipAddress,
      macAddress: deviceForm.value.macAddress,
      installDate: deviceForm.value.installDate,
      warrantyExpire: deviceForm.value.warrantyExpire,
      location: deviceForm.value.location,
      department: deviceForm.value.department,
      responsiblePerson: deviceForm.value.responsiblePerson,
      contactPhone: deviceForm.value.contactPhone,
      status: deviceForm.value.status
    }
    await deploymentApi.update(deviceForm.value.deviceDeployId, payload)
    await store.refreshAfterMutation()
    ElMessage.success('更新设备属性成功')
    isEditing.value = false
    await viewDeviceDetail(deviceForm.value)
  } catch (err: any) {
    ElMessage.error(err.message || '更新失败')
  } finally {
    submitting.value = false
  }
}

// ===== 删除 =====
function deleteDevice() {
  ElMessageBox.confirm(`确认删除已部署设备 “${deviceForm.value.deviceDeployName}” 吗？该操作将物理删除该设备及其所有部署点位。`, '删除设备确认', {
    confirmButtonText: '确定删除',
    cancelButtonText: '取消',
    type: 'warning',
    confirmButtonClass: 'el-button--danger'
  }).then(async () => {
    try {
      loading.value = true
      await deploymentApi.remove(deviceForm.value.deviceDeployId)
      ElMessage.success('删除设备成功')
      selectedNode.value = null
      await loadTree()
      await store.refreshAfterMutation()
    } catch (e: any) {
      ElMessage.error(e.message || '删除设备失败')
    } finally {
      loading.value = false
    }
  }).catch(() => {})
}

function deleteDeviceFromTable(row: DeviceDeployment) {
  ElMessageBox.confirm(`确认删除已部署设备 “${row.deviceDeployName}” 吗？该操作将物理删除该设备及其所有部署点位。`, '删除设备确认', {
    confirmButtonText: '确定删除',
    cancelButtonText: '取消',
    type: 'warning',
    confirmButtonClass: 'el-button--danger'
  }).then(async () => {
    try {
      loading.value = true
      await deploymentApi.remove(row.deviceDeployId!)
      ElMessage.success('删除设备成功')
      if (selectedNode.value && selectedNode.value.nodeId === row.deviceDeployId) {
        selectedNode.value = null
      }
      await refreshAfterChange()
    } catch (e: any) {
      ElMessage.error(e.message || '删除设备失败')
    } finally {
      loading.value = false
    }
  }).catch(() => {})
}

function batchDeleteDevices() {
  ElMessageBox.confirm(`确认删除选中的 ${selectedDevices.value.length} 个已部署设备吗？该操作将物理删除这些设备及其所有部署点位。`, '批量删除确认', {
    confirmButtonText: '确定删除',
    cancelButtonText: '取消',
    type: 'warning',
    confirmButtonClass: 'el-button--danger'
  }).then(async () => {
    try {
      loading.value = true
      for (const d of selectedDevices.value) {
        await deploymentApi.remove(d.deviceDeployId!)
      }
      ElMessage.success('批量删除设备成功')
    } catch (e: any) {
      ElMessage.error(e.message || '批量删除失败')
    } finally {
      selectedDevices.value = []
      isBatchManaging.value = false
      await refreshAfterChange()
      loading.value = false
    }
  }).catch(() => {})
}

async function refreshAfterChange() {
  await loadTree()
  await store.refreshAfterMutation()
  if (selectedNode.value && selectedNode.value.deviceCount != null) {
    await loadNodeDevices(selectedNode.value.nodeId)
  } else if (selectedNode.value && selectedNode.value.type === 'device') {
    selectedNode.value = null
  }
}

// ===== 部署 =====
const formRef = ref<FormInstance>()
const form = ref({
  deviceTmplId: null as number | null,
  deviceDeployName: '',
  deviceAlias: '',
  ipAddress: '',
  macAddress: '',
  installDate: ''
})

const rules = ref<FormRules>({
  deviceTmplId: [{ required: true, message: '请选择设备所属模板', trigger: 'change' }],
  deviceDeployName: [{ required: true, message: '请输入部署设备名称', trigger: 'blur' }],
  macAddress: [{ validator: validateMacAddress, trigger: 'blur' }]
})

function openDeployDialog() {
  if (!selectedNode.value || selectedNode.value.type === 'root') {
    ElMessage.warning('请先从左侧导航树中选择具体的部署节点！')
    return
  }
  form.value = {
    deviceTmplId: null,
    deviceDeployName: '',
    deviceAlias: '',
    ipAddress: '',
    macAddress: '',
    installDate: ''
  }
  dialogVisible.value = true
  nextTick(() => {
    if (formRef.value) {
      formRef.value.clearValidate()
    }
  })
}

async function submitDeploy() {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (valid) {
      if (!selectedNode.value?.nodeId) {
        ElMessage.warning('当前选择的节点无效，请重新在左侧导航树中选择！')
        return
      }
      submitting.value = true
      const payload: Partial<DeviceDeployment> = {
        deviceTmplId: form.value.deviceTmplId || undefined,
        deviceDeployName: form.value.deviceDeployName,
        deviceAlias: form.value.deviceAlias || form.value.deviceDeployName,
        ipAddress: form.value.ipAddress || undefined,
        macAddress: form.value.macAddress || undefined,
        installDate: form.value.installDate || undefined,
        navNodeId: selectedNode.value.nodeId
      }
      try {
        await deploymentApi.create(payload)
        ElMessage.success('部署新设备并生成点位成功！')
        dialogVisible.value = false
        await refreshAfterChange()
      } catch (err: any) {
        ElMessage.error(err.message || '部署设备失败')
      } finally {
        submitting.value = false
      }
    }
  })
}

// ===== 移动 =====
const moveDialogVisible = ref(false)
const moveTargetNode = ref<NavTreeNode | null>(null)
const moveNewName = ref('')
const moveSubmitting = ref(false)
const isBatchMove = ref(false)
const moveDeviceName = ref('')

function openMoveDialog(row: DeviceDeployment) {
  isBatchMove.value = false
  moveDeviceName.value = row.deviceDeployName
  moveNewName.value = row.deviceDeployName
  moveTargetNode.value = null
  moveDialogVisible.value = true
}

function handleMoveTargetSelect(node: any) {
  if (!node || !node.nodeId) return
  const nav = findNodeById(treeData.value, node.nodeId)
  if (!nav) return
  moveTargetNode.value = nav
}

async function submitMove() {
  if (!moveTargetNode.value) return
  try {
    moveSubmitting.value = true
    if (isBatchMove.value) {
      for (const d of selectedDevices.value) {
        await deploymentApi.move(d.deviceDeployId!, moveTargetNode.value.nodeId, d.deviceDeployName)
      }
      ElMessage.success(`批量移动 ${selectedDevices.value.length} 个设备成功`)
    } else {
      await deploymentApi.move(deviceForm.value.deviceDeployId, moveTargetNode.value.nodeId, moveNewName.value)
      ElMessage.success('移动设备位置成功')
    }
    moveDialogVisible.value = false
    await refreshAfterChange()
  } catch (err: any) {
    ElMessage.error(err.message || '移动设备失败')
  } finally {
    selectedDevices.value = []
    isBatchManaging.value = false
    moveSubmitting.value = false
  }
}

// ===== 点位/报警 =====
function getPointTypeText(type: string): string {
  switch (type) {
    case 'DI': return 'DI - 数字输入'
    case 'DO': return 'DO - 数字输出'
    case 'AI': return 'AI - 模拟输入'
    case 'AO': return 'AO - 模拟输出'
    default: return type || '未知'
  }
}

function getPointTypeClass(type: string): string {
  switch (type) {
    case 'DI': return 'type-di'
    case 'DO': return 'type-do'
    case 'AI': return 'type-ai'
    case 'AO': return 'type-ao'
    default: return ''
  }
}

function getStatusClass(status: number): string {
  switch (status) {
    case 1: return 'online'
    case 2: return 'offline'
    case 3: return 'maintenance'
    case 4: return 'fault'
    default: return 'offline'
  }
}

function getStatusText(status: number): string {
  switch (status) {
    case 1: return '在线'
    case 2: return '离线'
    case 3: return '维护'
    case 4: return '故障'
    default: return '未知'
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

onMounted(async () => {
  await loadTree()
  if (!store.allTemplates.length) {
    await store.loadTemplates()
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

.deploy-flex {
  height: 100%;
  display: flex;
  gap: 12px;
}

.tree-panel {
  width: 300px;
  flex-shrink: 0;
  background-color: $bg-card;
  border: 1px solid $border-color;
  border-radius: 8px;
  display: flex;
  flex-direction: column;
  overflow: hidden;

  .tree-panel-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: 12px 14px;
    border-bottom: 1px solid $border-color;
    flex-shrink: 0;

    .tree-title {
      font-size: 13px;
      font-weight: 600;
      color: $text-primary;
    }
  }

  .tree-body {
    flex: 1;
    overflow-y: auto;
    padding: 8px;

    :deep(.el-tree) {
      background-color: transparent;
      --el-tree-node-content-height: 30px;
    }

    :deep(.el-tree-node__content) {
      background-color: transparent;

      &:hover {
        background-color: $bg-card-hover;
      }
    }
  }
}

.tree-node {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: $text-secondary;

  .node-icon {
    font-size: 14px;
    color: $text-muted;
  }
  .node-icon-real {
    color: $accent-green;
  }
  .node-label {
    flex: 1;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
  .node-count {
    background-color: rgba($accent-green, 0.15);
    color: $accent-green;
    font-size: 10px;
    padding: 1px 6px;
    border-radius: 10px;
  }
}

.card-container {
  background-color: $bg-card;
  border: 1px solid $border-color;
  border-radius: 8px;
  display: flex;
  flex-direction: column;
  flex: 1;
  min-width: 0;
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

.data-tag {
  margin-left: 4px;
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

.status-dot {
  display: inline-block;
  width: 7px;
  height: 7px;
  border-radius: 50%;
  margin-right: 6px;
  &.online { background-color: $accent-green; box-shadow: 0 0 6px rgba(0,230,118,0.6); }
  &.offline { background-color: $text-muted; }
  &.maintenance { background-color: $accent-yellow; box-shadow: 0 0 6px rgba(255,213,79,0.6); }
  &.fault { background-color: #ff5252; box-shadow: 0 0 6px rgba(255,82,82,0.6); }
}
.status-text {
  font-size: 12px;
  color: $text-secondary;
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
