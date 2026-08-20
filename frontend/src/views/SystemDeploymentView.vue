<template>
  <div class="system-deployment-view">
    <!-- Left: Navigation Tree Panel -->
    <aside class="nav-tree-panel" v-loading="treeLoading">
      <header class="panel-header">
        <div class="panel-title">
          <el-icon><Operation /></el-icon>
          <span>Navigation</span>
        </div>
        <el-button type="primary" size="small" :icon="Plus" class="btn-primary" @click="openCreateDialog(null)">
          Add Root
        </el-button>
      </header>

      <div class="tree-body">
        <el-tree
          :data="treeData"
          :props="treeProps"
          node-key="nodeId"
          default-expand-all
          highlight-current
          @node-click="handleNodeClick"
          class="deploy-tree"
        >
          <template #default="{ data }">
            <div class="tree-node-row">
              <span :class="['tree-node-label', `level-${data.nodeLevel}`]">
                <el-icon class="node-icon" :class="{ 'real-data-icon': data.hasRealData }" v-if="getNodeIcon(data)">
                  <component :is="getNodeIcon(data)" />
                </el-icon>
                <span class="label-text">{{ data.nodeName }}</span>
                <span v-if="isLeafNode(data) && data.deviceCount != null" class="count-badge">
                  {{ data.deviceCount }}
                </span>
              </span>
              <span class="node-actions" @click.stop>
                <el-tooltip content="Add Child Node" placement="top" v-if="canAddChild(data)">
                  <el-button link size="small" :icon="CirclePlus" @click="openCreateDialog(data)" />
                </el-tooltip>
                <el-tooltip content="Edit" placement="top">
                  <el-button link size="small" :icon="EditPen" @click="openEditDialog(data)" />
                </el-tooltip>
                <el-tooltip content="Delete" placement="top">
                  <el-button link size="small" :icon="Delete" class="del-btn" @click="handleDeleteNode(data)" />
                </el-tooltip>
              </span>
            </div>
          </template>
        </el-tree>
        <el-empty v-if="!treeData.length" description="No navigation nodes" :image-size="60" />
      </div>
    </aside>

    <!-- Right: Content Area -->
    <section class="detail-panel" v-loading="devicesLoading">
      <template v-if="selectedNode">
        <!-- ======= Device Detail View ======= -->
        <template v-if="viewMode === 'detail'">
          <header class="panel-header detail-header">
            <div class="panel-title">
              <el-icon class="title-icon"><Connection /></el-icon>
              <span>Deployed Device Details - {{ deviceForm.deviceDeployName }}</span>
            </div>
            <div class="header-actions">
              <el-button size="small" class="btn-secondary" :icon="Warning" @click="openDeviceAlarmConfig">Set Device Alarm</el-button>
              <el-button size="small" class="btn-secondary" :icon="Edit" @click="openEditDeviceDialog">Edit Device</el-button>
              <el-button size="small" class="btn-danger" :icon="Delete" @click="deleteDeviceFromDetail">Delete Device</el-button>
              <el-button size="small" class="btn-secondary btn-back" @click="goBackToList">Back to List</el-button>
            </div>
          </header>

          <div class="detail-body">
            <!-- Device Properties Form -->
            <el-form
              :model="deviceForm"
              label-width="110px"
              label-position="right"
              class="custom-form-compact"
              disabled
            >
              <el-row :gutter="16">
                <el-col :span="12">
                  <el-form-item label="Device Name" prop="deviceDeployName">
                    <el-input v-model="deviceForm.deviceDeployName" />
                  </el-form-item>
                </el-col>
                <el-col :span="12">
                  <el-form-item label="Device Alias">
                    <el-input v-model="deviceForm.deviceAlias" />
                  </el-form-item>
                </el-col>
              </el-row>
              <el-row :gutter="16">
                <el-col :span="12">
                  <el-form-item label="Template">
                    <el-select v-model="deviceForm.deviceTmplId" placeholder="Select template" style="width: 100%">
                      <el-option
                        v-for="t in store.allTemplates"
                        :key="t.deviceTmplId"
                        :label="t.deviceTmplName"
                        :value="t.deviceTmplId!"
                      />
                    </el-select>
                  </el-form-item>
                </el-col>
                <el-col :span="12">
                  <el-form-item label="Device Type">
                    <el-input :value="deviceForm.deviceType || '-'" disabled />
                  </el-form-item>
                </el-col>
              </el-row>
              <el-row :gutter="16">
                <el-col :span="24">
                  <el-form-item label="N4 Topic">
                    <el-input :value="deviceForm.n4Address" disabled />
                  </el-form-item>
                </el-col>
              </el-row>
            </el-form>

            <!-- Points Configuration Table -->
            <div class="point-table-section">
              <div class="points-header">
                <div class="section-title">
                  <el-icon class="section-icon"><List /></el-icon>
                  <span>Points Configuration (Total: {{ devicePoints.length }})</span>
                </div>
              </div>

              <el-table
                :data="devicePoints"
                style="width: 100%; margin-top: 6px;"
                border
                size="small"
                class="custom-table"
                :header-cell-style="headerCellStyle"
                :cell-style="cellStyle"
              >
                <el-table-column prop="pointName" label="Point Name" min-width="180" align="center" header-align="center" show-overflow-tooltip :resizable="false" />
                <el-table-column prop="dataType" label="Data Type" width="110" align="center" header-align="center" :resizable="false" />
                <el-table-column prop="unit" label="Unit" width="90" align="center" header-align="center" :resizable="false" />
                <el-table-column prop="register" label="Register Address" min-width="150" align="center" header-align="center" :resizable="false">
                  <template #default="{ row }">
                    <code class="addr-code">{{ row.register || 'N/A' }}</code>
                  </template>
                </el-table-column>
                <el-table-column label="Alarm Rule" min-width="160" align="center" header-align="center" :resizable="false">
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
                    </div>
                  </template>
                </el-table-column>
              </el-table>
            </div>
          </div>
        </template>

        <!-- ======= List / Overview View ======= -->
        <template v-else>
          <header class="panel-header detail-header">
            <div class="panel-title">
              <el-icon class="node-icon" :class="{ 'real-data-icon': selectedNode.hasRealData }" v-if="getNodeIcon(selectedNode)">
                <component :is="getNodeIcon(selectedNode)" />
              </el-icon>
              <span>{{ selectedNode.nodeName }}</span>
            </div>
            <div class="header-actions">
              <el-button size="small" class="btn-secondary" :icon="EditPen" @click="openEditDialog(selectedNode)">Edit</el-button>
              <el-button size="small" class="btn-danger" :icon="Delete" @click="handleDeleteNode(selectedNode)">Delete</el-button>
            </div>
          </header>

          <div class="detail-body">
            <!-- Node Info Card -->
            <div class="info-card">
              <div class="info-row" v-if="selectedNode.nodeLevel === 1 && selectedNode.icon">
                <span class="info-label">Icon</span>
                <span class="info-value">{{ selectedNode.icon }}</span>
              </div>
              <div class="info-row" v-else>
                <span class="info-label">System Node</span>
                <span class="info-value">{{ selectedNode.nodeName }}</span>
              </div>
            </div>

            <!-- Leaf Node: Deployed Device List -->
            <div v-if="isLeafNode(selectedNode)" class="device-section">
              <div class="section-header">
                <h3>Deployed Devices
                  <span class="section-count">(Total: {{ currentDevices.length }})</span>
                </h3>
                <div class="header-actions">
                  <template v-if="isBatchManaging">
                    <el-button
                      type="danger"
                      size="small"
                      class="btn-danger"
                      :icon="Delete"
                      :disabled="selectedDevices.length === 0"
                      @click="batchDeleteDevices"
                    >
                      Batch Delete ({{ selectedDevices.length }} selected)
                    </el-button>
                    <el-button size="small" class="btn-secondary" @click="exitBatchManage">Exit Manage</el-button>
                  </template>
                  <template v-else>
                    <el-button size="small" class="btn-secondary" :icon="Edit" @click="enterBatchManage">
                      Batch Manage
                    </el-button>
                  </template>
                  <el-button size="small" class="btn-primary" :icon="Plus" @click="openDeployDialog">
                    Deploy Device
                  </el-button>
                  <el-button size="small" class="btn-secondary" :icon="Link" @click="openMountDialog">
                    Mount Device
                  </el-button>
                </div>
              </div>

              <!-- Search Bar -->
              <div class="search-box-table">
                <el-input
                  v-model="searchQuery"
                  placeholder="Search device name or alias..."
                  size="small"
                  :prefix-icon="Search"
                  clearable
                  class="search-bar"
                />
              </div>

              <!-- Device Table -->
              <el-table
                :data="filteredDevices"
                class="custom-table"
                border
                size="small"
                :header-cell-style="headerCellStyle"
                :cell-style="cellStyle"
                @selection-change="handleDeviceSelectionChange"
              >
                <el-table-column v-if="isBatchManaging" type="selection" width="30" align="center" header-align="center" :resizable="false" />
                <el-table-column prop="deviceDeployId" label="ID" width="55" align="center" header-align="center" :resizable="false" />
                <el-table-column prop="deviceDeployName" label="Device Name" min-width="180" align="center" header-align="center" show-overflow-tooltip :resizable="false">
                  <template #default="{ row }">
                    <el-link type="primary" class="device-link" @click="viewDeviceDetail(row as DeviceDeployment)">
                      {{ row.deviceDeployName }}
                    </el-link>
                  </template>
                </el-table-column>
                <el-table-column prop="deviceAlias" label="Device Alias" min-width="140" align="center" header-align="center" show-overflow-tooltip :resizable="false" />
                <el-table-column prop="deviceType" label="Device Type" width="110" align="center" header-align="center" show-overflow-tooltip :resizable="false" />
                <el-table-column prop="manufacturer" label="Manufacturer" width="120" align="center" header-align="center" show-overflow-tooltip :resizable="false" />
                <el-table-column label="Status" width="90" align="center" header-align="center" :resizable="false">
                  <template #default="{ row }">
                    <span class="status-dot" :class="getStatusClass(row.status)"></span>
                    <span class="status-text">{{ getStatusText(row.status) }}</span>
                  </template>
                </el-table-column>
                <el-table-column label="Actions" width="160" align="center" header-align="center" fixed="right" :resizable="false">
                  <template #default="{ row }">
                    <el-button type="primary" link size="small" @click="viewDeviceDetail(row as DeviceDeployment)">Edit</el-button>
                    <el-button type="primary" link size="small" @click="handleUnmount(row as DeviceDeployment)">Unmount</el-button>
                    <el-button type="danger" link size="small" @click="deleteDeviceFromTable(row as DeviceDeployment)">Delete</el-button>
                  </template>
                </el-table-column>
                <template #empty>
                  <span class="empty-text">No deployed devices under this node</span>
                </template>
              </el-table>
            </div>

            <!-- Non-leaf Node: Sub-nodes Overview -->
            <div v-else class="device-section">
              <div class="section-header">
                <h3>Child Groups <span class="section-count">({{ selectedNode.children?.length || 0 }})</span></h3>
              </div>
              <el-table :data="selectedNode.children || []" class="custom-table" border size="small" :header-cell-style="headerCellStyle" :cell-style="cellStyle">
                <el-table-column prop="nodeName" label="Group Name" min-width="200" align="center" header-align="center" :resizable="false" />
                <el-table-column prop="deviceCount" label="Devices" width="100" align="center" header-align="center" :resizable="false" />
                <el-table-column prop="sortOrder" label="Sort Order" width="100" align="center" header-align="center" :resizable="false" />
                <template #empty>
                  <span class="empty-text">No child groups under this node</span>
                </template>
              </el-table>
            </div>
          </div>
        </template>
      </template>

      <el-empty v-else description="Please select a navigation node from the left tree" :image-size="100" class="select-prompt" />
    </section>

    <!-- ===== Dialog: Create/Edit Node ===== -->
    <el-dialog
      v-model="nodeDialogVisible"
      :title="nodeDialogMode === 'create' ? 'Add Navigation Node' : 'Edit Navigation Node'"
      width="480px"
      append-to-body
      class="dark-dialog"
    >
      <el-form :model="nodeForm" :rules="nodeRules" ref="nodeFormRef" label-width="100px">
        <el-form-item label="Node Name" prop="nodeName">
          <el-input v-model="nodeForm.nodeName" placeholder="e.g. Electrical / MV System" maxlength="50" show-word-limit />
        </el-form-item>
        <el-form-item label="Parent Node" v-if="showParentSelect">
          <el-cascader
            v-model="nodeForm.parentId"
            :options="parentCascaderOptions"
            :props="parentCascaderProps"
            placeholder="Select parent node"
            :clearable="false"
            popper-class="dark-cascader-dropdown"
            style="width: 100%"
          />
          <div class="form-tip">Select an eligible group as the target parent</div>
        </el-form-item>
        <el-form-item label="Icon" v-if="nodeForm.parentId == null">
          <el-select v-model="nodeForm.icon" placeholder="Select icon (Root node only)" clearable style="width: 100%">
            <el-option
              v-for="opt in iconOptions"
              :key="opt.name"
              :label="opt.name"
              :value="opt.name"
            >
              <span style="display: flex; align-items: center; gap: 8px;">
                <el-icon><component :is="opt.comp" /></el-icon>
                <span>{{ opt.name }}</span>
              </span>
            </el-option>
          </el-select>
        </el-form-item>
        <el-form-item label="Sort Order" prop="sortOrder">
          <el-input-number v-model="nodeForm.sortOrder" :min="0" :max="9999" style="width: 100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button class="btn-secondary" @click="nodeDialogVisible = false">Cancel</el-button>
        <el-button type="primary" class="btn-primary" @click="submitNodeForm" :loading="nodeSubmitting">Confirm</el-button>
      </template>
    </el-dialog>

    <!-- ===== Dialog: Mount Existing Device ===== -->
    <el-dialog
      v-model="mountDialogVisible"
      title="Mount Existing Devices to Current Node"
      width="640px"
      append-to-body
      class="dark-dialog"
    >
      <el-table
        :data="mountableDevices"
        class="custom-table"
        border
        size="small"
        :header-cell-style="headerCellStyle"
        :cell-style="cellStyle"
        @selection-change="handleMountSelectionChange"
        max-height="360"
      >
        <el-table-column type="selection" width="35" align="center" :resizable="false" />
        <el-table-column prop="deviceDeployId" label="Device ID" min-width="90" align="center" :resizable="false" />
        <el-table-column prop="deviceDeployName" label="Device Name" min-width="220" show-overflow-tooltip :resizable="false" />
        <el-table-column prop="deviceType" label="Device Type" min-width="100" align="center" :resizable="false" />
        <template #empty>
          <span class="empty-text">No mountable devices available (all devices are mounted)</span>
        </template>
      </el-table>
      <template #footer>
        <span class="mount-selected-tip" v-if="mountSelectedIds.length">
          {{ mountSelectedIds.length }} device(s) selected
        </span>
        <el-button class="btn-secondary" @click="mountDialogVisible = false">Cancel</el-button>
        <el-button
          type="primary"
          class="btn-primary"
          @click="submitMount"
          :loading="mountSubmitting"
          :disabled="!mountSelectedIds.length"
        >
          Mount Selected Devices
        </el-button>
      </template>
    </el-dialog>

    <!-- ===== Dialog: Deploy New Device (from template) ===== -->
    <el-dialog
      v-model="deployDialogVisible"
      title="Deploy New Device to Current Node"
      width="540px"
      append-to-body
      class="dark-dialog"
    >
      <el-form
        :model="deployForm"
        :rules="deployRules"
        ref="deployFormRef"
        label-width="120px"
        label-position="right"
        size="small"
        class="custom-form"
      >
        <el-form-item label="Target Node">
          <el-input :value="selectedNode?.nodeName" disabled />
        </el-form-item>

        <el-form-item label="Device Template" prop="deviceTmplId">
          <el-select v-model="deployForm.deviceTmplId" placeholder="Select device template..." style="width: 100%">
            <el-option
              v-for="t in store.allTemplates"
              :key="t.deviceTmplId"
              :label="`${t.deviceTmplName} (${t.deviceType})`"
              :value="t.deviceTmplId!"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="Device Name" prop="deviceDeployName">
          <el-input v-model="deployForm.deviceDeployName" placeholder="e.g. A1_PTU_A1_LVP_A1_00_Collector_00" />
        </el-form-item>

        <el-form-item label="Device Alias">
          <el-input v-model="deployForm.deviceAlias" placeholder="Defaults to device name" />
        </el-form-item>

        <el-form-item label="IP Address">
          <el-input v-model="deployForm.ipAddress" placeholder="e.g. 192.168.1.100" />
        </el-form-item>

        <el-form-item label="MAC Address" prop="macAddress">
          <el-input v-model="deployForm.macAddress" placeholder="e.g. 00:1A:2B:3C:4D:5E" />
        </el-form-item>

        <el-form-item label="Install Date">
          <el-date-picker
            v-model="deployForm.installDate"
            type="date"
            format="YYYY-MM-DD"
            value-format="YYYY-MM-DD"
            style="width: 100%"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button class="btn-secondary" size="small" @click="deployDialogVisible = false">Cancel</el-button>
        <el-button class="btn-primary" size="small" type="primary" :loading="deploySubmitting" @click="submitDeploy">Deploy & Generate Points</el-button>
      </template>
    </el-dialog>

    <!-- ===== Dialog: Edit Deployed Device ===== -->
    <el-dialog
      v-model="editDeviceDialogVisible"
      title="Edit Deployed Device"
      width="520px"
      append-to-body
      class="dark-dialog"
    >
      <el-form
        :model="editDeviceForm"
        :rules="editDeviceRules"
        ref="editDeviceFormRef"
        label-width="120px"
        label-position="right"
        size="small"
        class="custom-form"
      >
        <el-form-item label="Device Name" prop="deviceDeployName">
          <el-input v-model="editDeviceForm.deviceDeployName" placeholder="Enter device name" />
        </el-form-item>

        <el-form-item label="Device Alias">
          <el-input v-model="editDeviceForm.deviceAlias" placeholder="Enter device alias" />
        </el-form-item>

        <el-form-item label="Device Template" prop="deviceTmplId">
          <el-select
            v-model="editDeviceForm.deviceTmplId"
            placeholder="Select device template"
            style="width: 100%"
            @change="handleEditDialogTemplateChange"
          >
            <el-option
              v-for="t in store.allTemplates"
              :key="t.deviceTmplId"
              :label="`${t.deviceTmplName} (${t.deviceType})`"
              :value="t.deviceTmplId!"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="Device Type">
          <el-input :value="editDeviceForm.deviceType || '-'" disabled />
        </el-form-item>

        <el-form-item label="N4 Topic">
          <el-input :value="editDeviceForm.n4Address" disabled />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button class="btn-secondary" size="small" @click="editDeviceDialogVisible = false">Cancel</el-button>
        <el-button
          type="primary"
          size="small"
          class="btn-primary"
          @click="submitEditDevice"
          :loading="editDeviceSubmitting"
        >
          Save
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, ref, type CSSProperties } from 'vue'
import {
  Plus, CirclePlus, EditPen, Delete, Operation,
  Location, Picture, OfficeBuilding, Grid, Lightning, Warning,
  Cpu, Monitor, Box,
  FolderOpened,
  DataLine,
  Connection, Edit, List, Search, Link,
} from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { dashboardNavApi } from '@/api/dashboardNav'
import { deploymentApi } from '@/api/deployment'
import { useDashboardStore } from '@/stores/dashboard'
import type { NavTreeNode, DeviceDeployItem, DeviceDeployment, PointDeploymentItem } from '@/api/types'

const store = useDashboardStore()

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
  textAlign: 'center',
}

// =====================================================================
//  View Mode Management
// =====================================================================
/** 'list' = List/overview view, 'detail' = Device detail view */
const viewMode = ref<'list' | 'detail'>('list')

// =====================================================================
//  Navigation Tree
// =====================================================================
const treeData = ref<NavTreeNode[]>([])
const treeLoading = ref(false)
const treeProps = { children: 'children', label: 'nodeName' }

const selectedNode = ref<NavTreeNode | null>(null)
const devicesLoading = ref(false)

function isLeafNode(node: NavTreeNode): boolean {
  return !node.children || node.children.length === 0
}

function canAddChild(node: NavTreeNode): boolean {
  const maxLevel = node.maxLevel ?? 2
  return node.nodeLevel < maxLevel
}

const parentCascaderProps = {
  value: 'nodeId',
  label: 'nodeName',
  children: 'children',
  checkStrictly: false,
  emitPath: false,
}

async function loadTree() {
  treeLoading.value = true
  try {
    treeData.value = await dashboardNavApi.getNavTree()
    if (selectedNode.value) {
      const refreshed = findNodeById(treeData.value, selectedNode.value.nodeId)
      selectedNode.value = refreshed
      if (refreshed && isLeafNode(refreshed)) {
        await loadNodeDevices(refreshed.nodeId)
      } else {
        currentDevices.value = []
      }
    }
  } catch (e: any) {
    ElMessage.error(e.message || 'Failed to load navigation tree')
  } finally {
    treeLoading.value = false
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

async function handleNodeClick(data: NavTreeNode) {
  selectedNode.value = data
  viewMode.value = 'list'
  if (isLeafNode(data)) {
    await loadNodeDevices(data.nodeId)
  } else {
    currentDevices.value = []
  }
}

// =====================================================================
//  Icon Mapping
// =====================================================================
const iconMap: Record<string, any> = {
  Location, Picture, OfficeBuilding, Grid, Lightning, Warning, EditPen,
  Cpu, Monitor, Box,
}
const iconOptions = [
  { name: 'Location', comp: Location },
  { name: 'Picture', comp: Picture },
  { name: 'OfficeBuilding', comp: OfficeBuilding },
  { name: 'Grid', comp: Grid },
  { name: 'Lightning', comp: Lightning },
  { name: 'Warning', comp: Warning },
  { name: 'EditPen', comp: EditPen },
  { name: 'Cpu', comp: Cpu },
  { name: 'Monitor', comp: Monitor },
  { name: 'Box', comp: Box },
]
function getNodeIcon(node: NavTreeNode) {
  if (node.hasRealData) return DataLine
  if (node.nodeLevel > 1) return null
  const iconName = node.icon
  if (!iconName) return FolderOpened
  return iconMap[iconName] || FolderOpened
}

// =====================================================================
//  Node CRUD Dialog
// =====================================================================
const nodeDialogVisible = ref(false)
const nodeDialogMode = ref<'create' | 'edit'>('create')
const nodeSubmitting = ref(false)
const nodeFormRef = ref<FormInstance>()
const editingNodeId = ref<number | null>(null)
const nodeForm = ref({
  nodeName: '',
  parentId: null as number | null,
  icon: null as string | null,
  sortOrder: 0,
})
const nodeRules = {
  nodeName: [{ required: true, message: 'Please enter node name', trigger: 'blur' }],
}
const targetParentLevel = ref<number>(0)
const showParentSelect = computed(() => targetParentLevel.value > 0)

const parentCascaderOptions = computed(() => {
  const targetLevel = targetParentLevel.value
  if (targetLevel === 0) return []

  const buildTree = (nodes: NavTreeNode[]): any[] => {
    const res: any[] = []
    for (const n of nodes) {
      if (n.nodeLevel > targetLevel) continue

      if (n.nodeLevel === targetLevel) {
        const maxL = n.maxLevel ?? 4
        if (n.nodeLevel >= maxL) continue
        
        res.push({
          nodeId: n.nodeId,
          nodeName: n.nodeName,
          nodeLevel: n.nodeLevel,
        })
      } else {
        let validChildren: any[] = []
        if (n.children && n.children.length > 0) {
          validChildren = buildTree(n.children)
        }
        if (validChildren.length > 0) {
          res.push({
            nodeId: n.nodeId,
            nodeName: n.nodeName,
            nodeLevel: n.nodeLevel,
            children: validChildren,
          })
        }
      }
    }
    return res
  }

  return buildTree(treeData.value)
})

function openCreateDialog(parent: NavTreeNode | null) {
  nodeDialogMode.value = 'create'
  editingNodeId.value = null
  targetParentLevel.value = parent ? parent.nodeLevel : 0
  
  nodeForm.value = {
    nodeName: '',
    parentId: parent ? parent.nodeId : null,
    icon: parent ? null : 'OfficeBuilding',
    sortOrder: 0,
  }
  nodeDialogVisible.value = true
}

function openEditDialog(node: NavTreeNode) {
  nodeDialogMode.value = 'edit'
  editingNodeId.value = node.nodeId
  targetParentLevel.value = node.nodeLevel - 1
  
  nodeForm.value = {
    nodeName: node.nodeName,
    parentId: node.nodeLevel === 1 ? null : (findParentId(treeData.value, node.nodeId) ?? null),
    icon: node.icon ?? null,
    sortOrder: node.sortOrder,
  }
  nodeDialogVisible.value = true
}

function findParentId(nodes: NavTreeNode[], childId: number): number | null {
  for (const n of nodes) {
    if (n.children) {
      if (n.children.some(c => c.nodeId === childId)) return n.nodeId
      const found = findParentId(n.children, childId)
      if (found) return found
    }
  }
  return null
}

async function submitNodeForm() {
  if (!nodeFormRef.value) return
  await nodeFormRef.value.validate(async (valid) => {
    if (!valid) return
    nodeSubmitting.value = true
    try {
      if (nodeDialogMode.value === 'create') {
        await dashboardNavApi.createNode({
          nodeName: nodeForm.value.nodeName,
          parentId: nodeForm.value.parentId,
          icon: nodeForm.value.parentId == null ? nodeForm.value.icon : null,
          sortOrder: nodeForm.value.sortOrder,
        })
        ElMessage.success('Node created successfully')
      } else {
        await dashboardNavApi.updateNode(editingNodeId.value!, {
          nodeName: nodeForm.value.nodeName,
          parentId: nodeForm.value.parentId,
          icon: nodeForm.value.parentId == null ? nodeForm.value.icon : null,
          sortOrder: nodeForm.value.sortOrder,
        })
        ElMessage.success('Node updated successfully')
      }
      nodeDialogVisible.value = false
      await loadTree()
    } catch (e: any) {
      ElMessage.error(e.message || 'Operation failed')
    } finally {
      nodeSubmitting.value = false
    }
  })
}

// =====================================================================
//  Node Delete
// =====================================================================
async function handleDeleteNode(node: NavTreeNode) {
  try {
    const impact = await dashboardNavApi.getDeleteImpact(node.nodeId)
    const msg = !isLeafNode(node)
      ? `This will delete this node along with its ${impact.childCount} sub-nodes, and unbind ${impact.deviceCount} devices.`
      : `This will delete this node, and unbind ${impact.deviceCount} devices.`
    await ElMessageBox.confirm(
      `${msg}\nNode deletion cannot be undone. Do you want to proceed?`,
      'Delete Confirmation',
      {
        confirmButtonText: 'Delete',
        cancelButtonText: 'Cancel',
        type: 'warning',
        confirmButtonClass: 'el-button--danger',
      },
    )
  } catch {
    return
  }
  try {
    await dashboardNavApi.deleteNode(node.nodeId)
    ElMessage.success('Deleted successfully')
    if (selectedNode.value?.nodeId === node.nodeId) {
      selectedNode.value = null
      currentDevices.value = []
      viewMode.value = 'list'
    }
    await loadTree()
  } catch (e: any) {
    ElMessage.error(e.message || 'Failed to delete node')
  }
}

// =====================================================================
//  Device List
// =====================================================================
const currentDevices = ref<DeviceDeployment[]>([])
const searchQuery = ref('')

async function loadNodeDevices(nodeId: number) {
  devicesLoading.value = true
  try {
    currentDevices.value = await deploymentApi.listByNode(nodeId)
  } catch (e: any) {
    ElMessage.error(e.message || 'Failed to load device list')
  } finally {
    devicesLoading.value = false
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

// Batch Management
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

// =====================================================================
//  Device Detail / Edit Dialog
// =====================================================================
const deviceForm = ref<any>({})
const devicePoints = ref<PointDeploymentItem[]>([])
const editDeviceDialogVisible = ref(false)
const editDeviceSubmitting = ref(false)
const editDeviceForm = ref<any>({})
const editDeviceFormRef = ref<FormInstance>()
const editDeviceRules = ref<FormRules>({
  deviceDeployName: [{ required: true, message: 'Please enter device name', trigger: 'blur' }],
  deviceTmplId: [{ required: true, message: 'Please select device template', trigger: 'change' }]
})

async function viewDeviceDetail(row: DeviceDeployment) {
  devicesLoading.value = true
  try {
    const detail = await deploymentApi.getDetail(row.deviceDeployId!)
    deviceForm.value = { ...detail }
    devicePoints.value = await deploymentApi.getPoints(row.deviceDeployId!)
    viewMode.value = 'detail'
  } catch (e: any) {
    ElMessage.error(e.message || 'Failed to load device details')
  } finally {
    devicesLoading.value = false
  }
}

function goBackToList() {
  viewMode.value = 'list'
  if (selectedNode.value && isLeafNode(selectedNode.value)) {
    loadNodeDevices(selectedNode.value.nodeId)
  }
}

function openEditDeviceDialog() {
  editDeviceForm.value = {
    deviceDeployId: deviceForm.value.deviceDeployId,
    deviceDeployName: deviceForm.value.deviceDeployName,
    deviceAlias: deviceForm.value.deviceAlias,
    deviceTmplId: deviceForm.value.deviceTmplId,
    deviceType: deviceForm.value.deviceType,
    n4Address: deviceForm.value.n4Address,
    navNodeId: deviceForm.value.navNodeId,
    alarmCode: deviceForm.value.alarmCode,
    status: deviceForm.value.status,
    ipAddress: deviceForm.value.ipAddress,
    macAddress: deviceForm.value.macAddress,
    installDate: deviceForm.value.installDate,
    warrantyExpire: deviceForm.value.warrantyExpire,
    location: deviceForm.value.location,
    department: deviceForm.value.department,
    responsiblePerson: deviceForm.value.responsiblePerson,
    contactPhone: deviceForm.value.contactPhone
  }
  editDeviceDialogVisible.value = true
}

function handleEditDialogTemplateChange(tmplId: number) {
  const tmpl = store.allTemplates.find(t => t.deviceTmplId === tmplId)
  if (tmpl) {
    editDeviceForm.value.deviceType = tmpl.deviceType
  }
}

async function submitEditDevice() {
  if (!editDeviceForm.value.deviceDeployId) {
    ElMessage.error('Cannot save: Missing device ID')
    return
  }
  if (!editDeviceFormRef.value) return
  try {
    await editDeviceFormRef.value.validate()
  } catch {
    return
  }
  try {
    editDeviceSubmitting.value = true
    const payload = {
      deviceTmplId: editDeviceForm.value.deviceTmplId,
      navNodeId: editDeviceForm.value.navNodeId,
      deviceDeployName: editDeviceForm.value.deviceDeployName,
      deviceAlias: editDeviceForm.value.deviceAlias,
      alarmCode: editDeviceForm.value.alarmCode,
      ipAddress: editDeviceForm.value.ipAddress,
      macAddress: editDeviceForm.value.macAddress,
      installDate: editDeviceForm.value.installDate,
      warrantyExpire: editDeviceForm.value.warrantyExpire,
      location: editDeviceForm.value.location,
      department: editDeviceForm.value.department,
      responsiblePerson: editDeviceForm.value.responsiblePerson,
      contactPhone: editDeviceForm.value.contactPhone,
      status: editDeviceForm.value.status
    }
    await deploymentApi.update(editDeviceForm.value.deviceDeployId, payload)
    await store.refreshAfterMutation()
    ElMessage.success('Device updated successfully')
    editDeviceDialogVisible.value = false
    await viewDeviceDetail(editDeviceForm.value)
  } catch (err: any) {
    ElMessage.error(err.message || 'Update failed')
  } finally {
    editDeviceSubmitting.value = false
  }
}

// =====================================================================
//  Device Delete
// =====================================================================
function deleteDeviceFromDetail() {
  ElMessageBox.confirm(`Are you sure you want to delete deployed device "${deviceForm.value.deviceDeployName}"? This will permanently delete the device and all its points.`, 'Delete Confirmation', {
    confirmButtonText: 'Delete',
    cancelButtonText: 'Cancel',
    type: 'warning',
    confirmButtonClass: 'el-button--danger'
  }).then(async () => {
    try {
      devicesLoading.value = true
      await deploymentApi.remove(deviceForm.value.deviceDeployId)
      ElMessage.success('Device deleted successfully')
      viewMode.value = 'list'
      await refreshAfterChange()
    } catch (e: any) {
      ElMessage.error(e.message || 'Failed to delete device')
    } finally {
      devicesLoading.value = false
    }
  }).catch(() => {})
}

function deleteDeviceFromTable(row: DeviceDeployment) {
  ElMessageBox.confirm(`Are you sure you want to delete deployed device "${row.deviceDeployName}"? This will permanently delete the device and all its points.`, 'Delete Confirmation', {
    confirmButtonText: 'Delete',
    cancelButtonText: 'Cancel',
    type: 'warning',
    confirmButtonClass: 'el-button--danger'
  }).then(async () => {
    try {
      devicesLoading.value = true
      await deploymentApi.remove(row.deviceDeployId!)
      ElMessage.success('Device deleted successfully')
      await refreshAfterChange()
    } catch (e: any) {
      ElMessage.error(e.message || 'Failed to delete device')
    } finally {
      devicesLoading.value = false
    }
  }).catch(() => {})
}

function batchDeleteDevices() {
  ElMessageBox.confirm(`Are you sure you want to delete ${selectedDevices.value.length} selected device(s)? This will permanently delete the devices and all their points.`, 'Batch Delete Confirmation', {
    confirmButtonText: 'Delete',
    cancelButtonText: 'Cancel',
    type: 'warning',
    confirmButtonClass: 'el-button--danger'
  }).then(async () => {
    try {
      devicesLoading.value = true
      for (const d of selectedDevices.value) {
        await deploymentApi.remove(d.deviceDeployId!)
      }
      ElMessage.success('Batch delete completed successfully')
    } catch (e: any) {
      ElMessage.error(e.message || 'Batch delete failed')
    } finally {
      selectedDevices.value = []
      isBatchManaging.value = false
      await refreshAfterChange()
      devicesLoading.value = false
    }
  }).catch(() => {})
}

async function refreshAfterChange() {
  await loadTree()
  await store.refreshAfterMutation()
  if (selectedNode.value && isLeafNode(selectedNode.value)) {
    await loadNodeDevices(selectedNode.value.nodeId)
  }
}

// =====================================================================
//  Deploy New Device Dialog
// =====================================================================
const deployDialogVisible = ref(false)
const deploySubmitting = ref(false)
const deployFormRef = ref<FormInstance>()
const deployForm = ref({
  deviceTmplId: null as number | null,
  deviceDeployName: '',
  deviceAlias: '',
  ipAddress: '',
  macAddress: '',
  installDate: ''
})
function validateMacAddress(_rule: any, value: any, callback: any) {
  if (value && !/^([0-9A-Fa-f]{2}[:-]){5}([0-9A-Fa-f]{2})$/.test(value)) {
    callback(new Error('Invalid MAC address format (e.g. 00:1A:2B:3C:4D:5E)'))
    return
  }
  callback()
}

const deployRules = ref<FormRules>({
  deviceTmplId: [{ required: true, message: 'Please select device template', trigger: 'change' }],
  deviceDeployName: [{ required: true, message: 'Please enter device name', trigger: 'blur' }],
  macAddress: [{ validator: validateMacAddress, trigger: 'blur' }]
})

function openDeployDialog() {
  if (!selectedNode.value) {
    ElMessage.warning('Please select a deployment node from the left tree first!')
    return
  }
  deployForm.value = {
    deviceTmplId: null,
    deviceDeployName: '',
    deviceAlias: '',
    ipAddress: '',
    macAddress: '',
    installDate: ''
  }
  deployDialogVisible.value = true
  nextTick(() => {
    if (deployFormRef.value) {
      deployFormRef.value.clearValidate()
    }
  })
}

async function submitDeploy() {
  if (!deployFormRef.value) return
  await deployFormRef.value.validate(async (valid) => {
    if (valid) {
      if (!selectedNode.value?.nodeId) {
        ElMessage.warning('Invalid target node, please select again.')
        return
      }
      deploySubmitting.value = true
      const payload: Partial<DeviceDeployment> = {
        deviceTmplId: deployForm.value.deviceTmplId || undefined,
        deviceDeployName: deployForm.value.deviceDeployName,
        deviceAlias: deployForm.value.deviceAlias || deployForm.value.deviceDeployName,
        ipAddress: deployForm.value.ipAddress || undefined,
        macAddress: deployForm.value.macAddress || undefined,
        installDate: deployForm.value.installDate || undefined,
        navNodeId: selectedNode.value.nodeId
      }
      try {
        await deploymentApi.create(payload)
        ElMessage.success('Device deployed and points generated successfully!')
        deployDialogVisible.value = false
        await refreshAfterChange()
      } catch (err: any) {
        ElMessage.error(err.message || 'Failed to deploy device')
      } finally {
        deploySubmitting.value = false
      }
    }
  })
}

// =====================================================================
//  Mount / Unmount Device
// =====================================================================
const mountDialogVisible = ref(false)
const mountSubmitting = ref(false)
const mountableDevices = ref<DeviceDeployItem[]>([])
const mountSelectedIds = ref<number[]>([])

async function openMountDialog() {
  mountSelectedIds.value = []
  mountDialogVisible.value = true
  try {
    mountableDevices.value = await dashboardNavApi.getMountableDevices()
  } catch (e: any) {
    ElMessage.error(e.message || 'Failed to load mountable devices')
  }
}

function handleMountSelectionChange(rows: DeviceDeployItem[]) {
  mountSelectedIds.value = rows.map(r => r.deviceDeployId)
}

async function submitMount() {
  if (!selectedNode.value || !mountSelectedIds.value.length) return
  mountSubmitting.value = true
  try {
    const count = await dashboardNavApi.mountDevices(
      selectedNode.value.nodeId,
      mountSelectedIds.value,
    )
    ElMessage.success(`Successfully mounted ${count} device(s)`)
    mountDialogVisible.value = false
    await refreshAfterChange()
  } catch (e: any) {
    ElMessage.error(e.message || 'Mount failed')
  } finally {
    mountSubmitting.value = false
  }
}

async function handleUnmount(device: DeviceDeployment) {
  try {
    await ElMessageBox.confirm(
      `Are you sure you want to unmount device "${device.deviceDeployName}" from the current node?`,
      'Unmount Confirmation',
      { confirmButtonText: 'Unmount', cancelButtonText: 'Cancel', type: 'warning' },
    )
  } catch {
    return
  }
  try {
    await dashboardNavApi.unmountDevice(device.deviceDeployId!)
    ElMessage.success('Unmounted successfully')
    await refreshAfterChange()
  } catch (e: any) {
    ElMessage.error(e.message || 'Unmount failed')
  }
}

// =====================================================================
//  Helper Functions
// =====================================================================
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
    case 1: return 'Online'
    case 2: return 'Offline'
    case 3: return 'Maintenance'
    case 4: return 'Fault'
    default: return 'Unknown'
  }
}

function openDeviceAlarmConfig() {
  ElMessageBox.alert(`Configuring device-level alarm rules: When the device is offline or heartbeat fails, all sub-points report communication faults.`, 'Set Device Alarm', {
    confirmButtonText: 'OK'
  })
}
function openPointAlarmConfig(row: any) {
  ElMessageBox.alert(`Configuring point alarm rules for [${row.pointName}]: Thresholds, notification channels, etc.`, 'Point Alarm Rule', {
    confirmButtonText: 'OK'
  })
}

// =====================================================================
//  Initialization
// =====================================================================
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
$text-primary: #e5e7eb;
$text-secondary: #9ca3af;
$text-muted: #64748b;
$accent-green: #10b981;
$accent-blue: #3b82f6;
$accent-yellow: #f59e0b;
$accent-red: #ef4444;
$bg-dark: #0b1119;

.system-deployment-view {
  display: flex;
  gap: 12px;
  height: 100%;
  min-height: 0;
}

// ===== Left Tree Panel =====
.nav-tree-panel {
  width: 280px;
  flex-shrink: 0;
  background-color: #111827;
  border: 1px solid #1f2937;
  border-radius: 6px;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.panel-header {
  height: 38px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 12px;
  border-bottom: 1px solid #1f2937;
  flex-shrink: 0;
}

.panel-title {
  display: flex;
  align-items: center;
  gap: 6px;
  color: $text-primary;
  font-size: 11px;
  font-weight: 600;

  .el-icon {
    color: $accent-green;
    font-size: 13px;
  }
}

.title-icon {
  color: $accent-green;
  font-size: 13px;
}

.detail-header {
  .header-actions {
    display: flex;
    gap: 6px;
  }
}

.tree-body {
  flex: 1;
  overflow-y: auto;
  padding: 6px;
}

.deploy-tree {
  background: transparent !important;

  :deep(.el-tree-node__content) {
    height: 22px !important;
    border-radius: 4px;
    margin: 1px 0;
    padding: 0 4px !important;

    &:hover {
      background-color: #1a2332 !important;
    }
  }

  :deep(.el-tree-node.is-current > .el-tree-node__content) {
    background-color: rgba(16, 185, 129, 0.16) !important;
    color: #34d399 !important;
    font-weight: 500;
  }
}

.tree-node-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  padding-right: 4px;
  overflow: hidden;
}

.tree-node-label {
  display: flex;
  align-items: center;
  gap: 5px;
  font-size: 10px;
  overflow: hidden;
  flex: 1;

  .node-icon {
    font-size: 12px;
    color: $text-secondary;
    &.real-data-icon {
      color: $accent-green;
    }
  }

  .label-text {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
    font-size: 10px;
  }

  &.level-1 {
    color: #64b5f6;
    font-weight: 600;
  }
  &.level-2 {
    color: $text-primary;
    font-weight: 500;
  }
  &.level-3 {
    color: $text-primary;
    font-weight: 400;
  }
}

.count-badge {
  font-size: 9px;
  color: $accent-green;
  background-color: rgba($accent-green, 0.12);
  padding: 0 4px;
  border-radius: 6px;
  flex-shrink: 0;
  line-height: 14px;
}

.node-actions {
  display: none;
  gap: 2px;
  flex-shrink: 0;

  :deep(.el-button) {
    height: 18px !important;
    padding: 0 2px !important;
    font-size: 10px !important;
  }
}

.tree-node-row:hover .node-actions {
  display: flex;
}

.del-btn {
  color: $accent-red !important;
}

// ===== Right Panel =====
.detail-panel {
  flex: 1;
  min-width: 0;
  background-color: $bg-card;
  border: 1px solid $border-color;
  border-radius: 6px;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.detail-body {
  flex: 1;
  overflow-y: auto;
  padding: 8px 12px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.info-card {
  background-color: $bg-dark;
  border: 1px solid $border-color;
  border-radius: 4px;
  padding: 6px 10px;
}

.info-row {
  display: flex;
  padding: 2px 0;
  font-size: 10px;

  .info-label {
    width: 80px;
    color: $text-secondary;
    flex-shrink: 0;
  }
  .info-value {
    color: $text-primary;
  }
}

.device-section {
  display: flex;
  flex-direction: column;
  gap: 6px;
  flex: 1;
  min-height: 0;
}

.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 6px;

  h3 {
    margin: 0;
    font-size: 11px;
    color: $text-primary;
    font-weight: 600;
  }
  .section-count {
    color: $accent-green;
    font-size: 11px;
    font-weight: 500;
  }
}

.header-actions {
  display: flex;
  gap: 4px;
  align-items: center;
}

.search-box-table {
  width: 240px;
  flex-shrink: 0;

  :deep(.el-input__wrapper) {
    background-color: $bg-dark !important;
    height: 22px !important;
    padding: 0 6px !important;
  }

  :deep(.el-input__inner) {
    font-size: 10px !important;
    height: 22px !important;
    line-height: 22px !important;
  }
}

// ===== Device Details Form =====
.custom-form-compact {
  background-color: rgba($bg-card-hover, 0.3);
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
  :deep(.el-date-editor.is-disabled),
  :deep(.el-date-editor.is-disabled.el-input__wrapper) {
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

.extra-property-buttons {
  display: flex;
  gap: 6px;
  margin-bottom: 8px;
  padding-bottom: 6px;
  border-bottom: 1px dashed rgba($border-color, 0.6);
  flex-shrink: 0;
}

.point-table-section {
  margin-top: 8px;
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.points-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 6px;
  flex-shrink: 0;
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

// ===== Table Style =====
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
    justify-content: center;
    text-align: center !important;
  }
}

.btn-back {
  margin-left: 20px !important;
}

.alarm-rule-cell {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  width: 100%;
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

.device-link {
  font-weight: 500;
  cursor: pointer;
  font-size: 10px;
}

.addr-code {
  background-color: rgba($text-muted, 0.1);
  padding: 1px 4px;
  border-radius: 2px;
  font-family: monospace;
  color: $text-secondary;
  font-size: 10px;
  line-height: 16px;
}

// ===== Point Type Badge =====
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

// ===== Status Dot Indicator =====
.status-dot {
  display: inline-block;
  width: 5px;
  height: 5px;
  border-radius: 50%;
  margin-right: 4px;
  &.online { background-color: $accent-green; box-shadow: 0 0 4px rgba(0, 230, 118, 0.6); }
  &.offline { background-color: $text-muted; }
  &.maintenance { background-color: $accent-yellow; box-shadow: 0 0 4px rgba(255, 213, 79, 0.6); }
  &.fault { background-color: #ff5252; box-shadow: 0 0 4px rgba(255, 82, 82, 0.6); }
}
.status-text {
  font-size: 10px;
  color: $text-secondary;
}

// ===== General =====
.empty-text {
  color: $text-muted;
  font-size: 10px;
}

.select-prompt {
  margin: auto;
  font-size: 10px;
}

.form-tip {
  font-size: 10px;
  color: $text-muted;
  margin-top: 4px;
  line-height: 1.4;
}

.mount-selected-tip {
  margin-right: auto;
  color: $accent-green;
  font-size: 10px;
}

.text-secondary {
  color: $text-secondary;
}

.custom-form {
  :deep(.el-form-item) {
    margin-bottom: 8px;
  }
  :deep(.el-form-item__label) {
    color: $text-secondary;
    font-weight: 500;
    font-size: 10px !important;
    height: 22px !important;
    line-height: 22px !important;
  }
  :deep(.el-input__wrapper),
  :deep(.el-select__wrapper),
  :deep(.el-date-editor.el-input__wrapper) {
    background-color: $bg-dark !important;
    height: 22px !important;
    min-height: 22px !important;
    line-height: 22px !important;
    font-size: 10px !important;
  }
  :deep(.el-input__inner),
  :deep(.el-select__selected-item),
  :deep(.el-select__placeholder) {
    font-size: 10px !important;
    height: 22px !important;
    line-height: 22px !important;
  }
}

:deep(.el-dialog) {
  background-color: #151d2a !important;
  border: 1px solid #1f2937;
  border-radius: 8px;

  .el-dialog__title {
    color: $text-primary;
    font-size: 13px;
    font-weight: 600;
  }

  .el-dialog__body {
    border-top: 1px solid #1f2937;
    border-bottom: 1px solid #1f2937;
    padding: 16px 20px;
  }
}

:deep(.el-tag) {
  border: none !important;
  border-radius: 8px !important;
  font-size: 10px !important;
  font-weight: 400 !important;
  padding: 0 6px !important;
  line-height: 16px !important;
  height: 16px !important;

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

.btn-primary, :deep(.el-button--primary:not(.is-link)) {
  background-color: rgba($accent-green, 0.16) !important;
  color: #34d399 !important;

  &:hover {
    background-color: rgba($accent-green, 0.28) !important;
    color: #6ee7b7 !important;
  }
}

.btn-secondary, :deep(.el-button--default:not(.is-link)), :deep(.el-button--info:not(.is-link)) {
  background-color: rgba($accent-blue, 0.16) !important;
  color: #93c5fd !important;

  &:hover {
    background-color: rgba($accent-blue, 0.28) !important;
    color: #bfdbfe !important;
  }
}

.btn-danger, :deep(.el-button--danger:not(.is-link)) {
  background-color: rgba($accent-red, 0.16) !important;
  color: #fca5a5 !important;

  &:hover {
    background-color: rgba($accent-red, 0.28) !important;
    color: #fecaca !important;
  }
}
</style>
