<template>
  <div class="system-deployment-view">
    <!-- 左栏：可编辑导航树 -->
    <aside class="nav-tree-panel" v-loading="treeLoading">
      <header class="panel-header">
        <div class="panel-title">
          <el-icon><Operation /></el-icon>
          <span>导航结构</span>
        </div>
        <el-button type="primary" size="small" :icon="Plus" @click="openCreateDialog(null)">
          新增一级
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
                <el-tooltip content="新增子节点" placement="top" v-if="canAddChild(data)">
                  <el-button link size="small" :icon="CirclePlus" @click="openCreateDialog(data)" />
                </el-tooltip>
                <el-tooltip content="编辑" placement="top">
                  <el-button link size="small" :icon="EditPen" @click="openEditDialog(data)" />
                </el-tooltip>
                <el-tooltip content="删除" placement="top">
                  <el-button link size="small" :icon="Delete" class="del-btn" @click="handleDeleteNode(data)" />
                </el-tooltip>
              </span>
            </div>
          </template>
        </el-tree>
        <el-empty v-if="!treeData.length" description="暂无导航节点" :image-size="60" />
      </div>
    </aside>

    <!-- 右栏：内容区 -->
    <section class="detail-panel" v-loading="devicesLoading">
      <template v-if="selectedNode">
        <!-- ======= 设备详情视图 ======= -->
        <template v-if="viewMode === 'detail'">
          <header class="panel-header detail-header">
            <div class="panel-title">
              <el-icon class="title-icon"><Connection /></el-icon>
              <span>已部署设备详情 - {{ deviceForm.deviceDeployName }}</span>
              <el-tag v-if="deviceForm.isMock" size="small" type="info" class="data-tag">示例数据</el-tag>
              <el-tag v-else size="small" type="success" effect="dark" class="data-tag">真实数据</el-tag>
            </div>
            <div class="header-actions">
              <el-button type="primary" plain size="small" @click="goBackToList">返回列表</el-button>
              <el-button v-if="!isEditing" type="primary" plain size="small" :icon="Edit" @click="startEdit">修改设备与点位</el-button>
              <el-button v-else type="primary" plain size="small" :icon="Check" :loading="submitting" @click="submitUpdate">保存</el-button>
              <el-button type="danger" size="small" :icon="Delete" plain @click="deleteDeviceFromDetail">删除设备</el-button>
            </div>
          </header>

          <div class="detail-body">
            <!-- 设备属性表单 -->
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
                  <el-form-item label="设备类型">
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

            <!-- 设备报警配置 -->
            <div class="extra-property-buttons">
              <el-button type="primary" plain size="small" :icon="Warning" @click="openDeviceAlarmConfig">
                配置设备本身报警
              </el-button>
            </div>

            <!-- 点位配置表格 -->
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
        </template>

        <!-- ======= 列表视图（叶子节点）/ 子节点概览（非叶子节点）======= -->
        <template v-else>
          <header class="panel-header detail-header">
            <div class="panel-title">
              <el-icon class="node-icon" :class="{ 'real-data-icon': selectedNode.hasRealData }" v-if="getNodeIcon(selectedNode)">
                <component :is="getNodeIcon(selectedNode)" />
              </el-icon>
              <span>{{ selectedNode.nodeName }}</span>
              <el-tag size="small" :type="selectedNode.nodeLevel === 1 ? 'info' : 'success'" effect="dark">
                第 {{ selectedNode.nodeLevel }} 级{{ selectedNode.nodeLevel === 1 ? '分类' : '分组' }}
              </el-tag>
            </div>
            <div class="header-actions">
              <el-button type="primary" plain size="small" :icon="EditPen" @click="openEditDialog(selectedNode)">编辑</el-button>
              <el-button type="danger" plain size="small" :icon="Delete" @click="handleDeleteNode(selectedNode)">删除</el-button>
            </div>
          </header>

          <div class="detail-body">
            <!-- 节点信息卡片 -->
            <div class="info-card">
              <div class="info-row">
                <span class="info-label">节点ID</span>
                <span class="info-value">{{ selectedNode.nodeId }}</span>
              </div>
              <div class="info-row">
                <span class="info-label">排序序号</span>
                <span class="info-value">{{ selectedNode.sortOrder }}</span>
              </div>
              <div class="info-row" v-if="selectedNode.nodeLevel === 1">
                <span class="info-label">图标</span>
                <span class="info-value">{{ selectedNode.icon || '默认' }}</span>
              </div>
            </div>

            <!-- 叶子节点：设备列表 -->
            <div v-if="isLeafNode(selectedNode)" class="device-section">
              <div class="section-header">
                <h3>已部署设备
                  <span class="section-count">({{ currentDevices.length }} 台，真实 {{ realCount }} 台)</span>
                </h3>
                <div class="header-actions">
                  <template v-if="isBatchManaging">
                    <el-button
                      type="danger"
                      plain
                      size="small"
                      :icon="Delete"
                      :disabled="selectedDevices.length === 0"
                      @click="batchDeleteDevices"
                    >
                      批量删除 (已选 {{ selectedDevices.length }} 个)
                    </el-button>
                    <el-button type="primary" plain size="small" @click="exitBatchManage">退出管理</el-button>
                  </template>
                  <template v-else>
                    <el-button type="primary" plain size="small" :icon="Edit" @click="enterBatchManage">
                      批量管理
                    </el-button>
                  </template>
                  <el-button type="primary" plain size="small" :icon="Plus" @click="openDeployDialog">
                    新增部署设备
                  </el-button>
                  <el-button type="primary" plain size="small" :icon="Link" @click="openMountDialog">
                    挂载已有设备
                  </el-button>
                </div>
              </div>

              <!-- 搜索栏 -->
              <div class="search-box-table">
                <el-input
                  v-model="searchQuery"
                  placeholder="搜索设备名称或别名..."
                  size="small"
                  :prefix-icon="Search"
                  clearable
                  class="search-bar"
                />
              </div>

              <!-- 设备表格 -->
              <el-table
                :data="filteredDevices"
                class="custom-table"
                stripe
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
                <el-table-column prop="deviceType" label="设备类型" width="110" align="center" />
                <el-table-column prop="manufacturer" label="厂商" width="110" align="center" />
                <el-table-column label="状态" width="90" align="center">
                  <template #default="{ row }">
                    <span class="status-dot" :class="getStatusClass(row.status)"></span>
                    <span class="status-text">{{ getStatusText(row.status) }}</span>
                  </template>
                </el-table-column>
                <el-table-column label="操作" width="200" align="center" fixed="right">
                  <template #default="{ row }">
                    <el-button type="primary" link size="small" @click="viewDeviceDetail(row as DeviceDeployment)">编辑</el-button>
                    <el-button type="primary" link size="small" @click="handleUnmount(row as DeviceDeployment)">卸载</el-button>
                    <el-button type="danger" link size="small" @click="deleteDeviceFromTable(row as DeviceDeployment)">删除</el-button>
                  </template>
                </el-table-column>
                <template #empty>
                  <span class="empty-text">该节点下暂无设备，点击"新增部署设备"或"挂载已有设备"添加</span>
                </template>
              </el-table>
            </div>

            <!-- 非叶子节点：子节点概览 -->
            <div v-else class="device-section">
              <div class="section-header">
                <h3>下属子节点 <span class="section-count">({{ selectedNode.children?.length || 0 }})</span></h3>
              </div>
              <el-table :data="selectedNode.children || []" class="custom-table" stripe>
                <el-table-column prop="nodeName" label="子分组名称" min-width="200" />
                <el-table-column prop="deviceCount" label="设备数" min-width="100" />
                <el-table-column prop="sortOrder" label="排序序号" min-width="100" />
                <template #empty>
                  <span class="empty-text">该节点下暂无子分组，点击左侧"新增子节点"添加</span>
                </template>
              </el-table>
            </div>
          </div>
        </template>
      </template>

      <el-empty v-else description="请从左侧选择一个导航节点" :image-size="100" class="select-prompt" />
    </section>

    <!-- ===== 弹窗：新增/编辑节点 ===== -->
    <el-dialog
      v-model="nodeDialogVisible"
      :title="nodeDialogMode === 'create' ? '新增导航节点' : '编辑导航节点'"
      width="480px"
      append-to-body
      class="dark-dialog"
    >
      <el-form :model="nodeForm" :rules="nodeRules" ref="nodeFormRef" label-width="90px">
        <el-form-item label="节点名称" prop="nodeName">
          <el-input v-model="nodeForm.nodeName" placeholder="如：电气系统 / 中压系统" maxlength="50" show-word-limit />
        </el-form-item>
        <el-form-item label="父级节点" v-if="showParentSelect">
          <el-cascader
            v-model="nodeForm.parentId"
            :options="parentCascaderOptions"
            :props="parentCascaderProps"
            placeholder="请选择父级节点"
            :clearable="false"
            popper-class="dark-cascader-dropdown"
            style="width: 100%"
          />
          <div class="form-tip">只能选择同层级的合法分组作为目标父级</div>
        </el-form-item>
        <el-form-item label="图标" v-if="nodeForm.parentId == null">
          <el-select v-model="nodeForm.icon" placeholder="选择图标（仅一级节点）" clearable style="width: 100%">
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
        <el-form-item label="排序序号" prop="sortOrder">
          <el-input-number v-model="nodeForm.sortOrder" :min="0" :max="9999" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="nodeDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitNodeForm" :loading="nodeSubmitting">确认</el-button>
      </template>
    </el-dialog>

    <!-- ===== 弹窗：挂载已有设备 ===== -->
    <el-dialog
      v-model="mountDialogVisible"
      title="挂载已有设备到当前节点"
      width="640px"
      append-to-body
      class="dark-dialog"
    >
      <el-table
        :data="mountableDevices"
        class="custom-table"
        stripe
        @selection-change="handleMountSelectionChange"
        max-height="360"
      >
        <el-table-column type="selection" width="45" />
        <el-table-column prop="deviceDeployId" label="设备ID" min-width="90" />
        <el-table-column prop="deviceDeployName" label="设备名称" min-width="220" show-overflow-tooltip />
        <el-table-column prop="deviceType" label="设备类型" min-width="100" />
        <template #empty>
          <span class="empty-text">暂无可挂载的设备（所有设备已挂载到节点）</span>
        </template>
      </el-table>
      <template #footer>
        <span class="mount-selected-tip" v-if="mountSelectedIds.length">
          已选 {{ mountSelectedIds.length }} 台
        </span>
        <el-button @click="mountDialogVisible = false">取消</el-button>
        <el-button
          type="primary"
          @click="submitMount"
          :loading="mountSubmitting"
          :disabled="!mountSelectedIds.length"
        >
          挂载选中设备
        </el-button>
      </template>
    </el-dialog>

    <!-- ===== 弹窗：部署新设备（从模板） ===== -->
    <el-dialog
      v-model="deployDialogVisible"
      title="在当前节点部署新设备"
      width="540px"
      append-to-body
      class="dark-dialog"
    >
      <el-form
        :model="deployForm"
        :rules="deployRules"
        ref="deployFormRef"
        label-width="110px"
        label-position="right"
        size="small"
        class="custom-form"
      >
        <el-form-item label="当前部署节点">
          <el-input :value="selectedNode?.nodeName" disabled />
        </el-form-item>

        <el-form-item label="选择设备模板" prop="deviceTmplId">
          <el-select v-model="deployForm.deviceTmplId" placeholder="请选择对应设备模板..." style="width: 100%">
            <el-option
              v-for="t in store.allTemplates"
              :key="t.deviceTmplId"
              :label="`${t.deviceTmplName} (${t.deviceType}${t.isReal ? '' : ' - Mock'})`"
              :value="t.deviceTmplId!"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="设备部署名称" prop="deviceDeployName">
          <el-input v-model="deployForm.deviceDeployName" placeholder="例如：A1_PTU_A1_LVP_A1_00_Collector_00" />
        </el-form-item>

        <el-form-item label="设备别名">
          <el-input v-model="deployForm.deviceAlias" placeholder="默认同部署名称" />
        </el-form-item>

        <el-form-item label="IP 地址">
          <el-input v-model="deployForm.ipAddress" placeholder="例如：192.168.1.100" />
        </el-form-item>

        <el-form-item label="MAC 地址" prop="macAddress">
          <el-input v-model="deployForm.macAddress" placeholder="例如：00:1A:2B:3C:4D:5E" />
        </el-form-item>

        <el-form-item label="安装日期">
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
        <el-button size="small" @click="deployDialogVisible = false">取消</el-button>
        <el-button size="small" type="primary" :loading="deploySubmitting" @click="submitDeploy">部署并生成点位</el-button>
      </template>
    </el-dialog>  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, ref } from 'vue'
import {
  Plus, CirclePlus, EditPen, Delete, Operation,
  Location, Picture, OfficeBuilding, Grid, Lightning, Warning,
  Cpu, Monitor, Box,
  FolderOpened,
  DataLine,
  Connection, Edit, Check, List, Search, Link,
} from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { dashboardNavApi } from '@/api/dashboardNav'
import { deploymentApi } from '@/api/deployment'
import { useDashboardStore } from '@/stores/dashboard'
import type { NavTreeNode, DeviceDeployItem, DeviceDeployment, PointDeploymentItem } from '@/api/types'

const store = useDashboardStore()

// =====================================================================
//  视图模式管理
// =====================================================================
/** 'list' = 列表/概览视图, 'detail' = 设备详情视图 */
const viewMode = ref<'list' | 'detail'>('list')

// =====================================================================
//  导航树
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
    // 保持选中态同步
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
    ElMessage.error(e.message || '加载导航树失败')
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
  isEditing.value = false
  if (isLeafNode(data)) {
    await loadNodeDevices(data.nodeId)
  } else {
    currentDevices.value = []
  }
}

// 节点下拉选项（扁平化路径显示，供设备详情里的节点选择）
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

// =====================================================================
//  图标映射
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
//  节点 CRUD 弹窗
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
  nodeName: [{ required: true, message: '请输入节点名称', trigger: 'blur' }],
}
const targetParentLevel = ref<number>(0)
const showParentSelect = computed(() => targetParentLevel.value > 0)

/** 供父级节点选择器使用的纯粹可作父节点的分组树（严格按照目标层级过滤） */
const parentCascaderOptions = computed(() => {
  const targetLevel = targetParentLevel.value
  if (targetLevel === 0) return []

  const buildTree = (nodes: NavTreeNode[]): any[] => {
    const res: any[] = []
    for (const n of nodes) {
      if (n.nodeLevel > targetLevel) continue

      if (n.nodeLevel === targetLevel) {
        // 作为目标父节点，它本身必须允许添加子节点
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
        ElMessage.success('新增成功')
      } else {
        await dashboardNavApi.updateNode(editingNodeId.value!, {
          nodeName: nodeForm.value.nodeName,
          parentId: nodeForm.value.parentId,
          icon: nodeForm.value.parentId == null ? nodeForm.value.icon : null,
          sortOrder: nodeForm.value.sortOrder,
        })
        ElMessage.success('更新成功')
      }
      nodeDialogVisible.value = false
      await loadTree()
    } catch (e: any) {
      ElMessage.error(e.message || '操作失败')
    } finally {
      nodeSubmitting.value = false
    }
  })
}

// =====================================================================
//  节点删除
// =====================================================================
async function handleDeleteNode(node: NavTreeNode) {
  try {
    const impact = await dashboardNavApi.getDeleteImpact(node.nodeId)
    const msg = !isLeafNode(node)
      ? `将删除该节点及其下 ${impact.childCount} 个子节点，并解绑 ${impact.deviceCount} 台设备。`
      : `将删除该节点，并解绑其下 ${impact.deviceCount} 台设备。`
    await ElMessageBox.confirm(
      `${msg}\n节点删除后不可恢复，是否继续？`,
      '删除确认',
      {
        confirmButtonText: '确定删除',
        cancelButtonText: '取消',
        type: 'warning',
        confirmButtonClass: 'el-button--danger',
      },
    )
  } catch {
    return
  }
  try {
    await dashboardNavApi.deleteNode(node.nodeId)
    ElMessage.success('删除成功')
    if (selectedNode.value?.nodeId === node.nodeId) {
      selectedNode.value = null
      currentDevices.value = []
      viewMode.value = 'list'
    }
    await loadTree()
  } catch (e: any) {
    ElMessage.error(e.message || '删除失败')
  }
}

// =====================================================================
//  设备列表（使用 deploymentApi 获取完整信息）
// =====================================================================
const currentDevices = ref<DeviceDeployment[]>([])
const searchQuery = ref('')
const submitting = ref(false)

const realCount = computed(() => currentDevices.value.filter(d => !d.isMock).length)

async function loadNodeDevices(nodeId: number) {
  devicesLoading.value = true
  try {
    currentDevices.value = await deploymentApi.listByNode(nodeId)
  } catch (e: any) {
    ElMessage.error(e.message || '加载设备列表失败')
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

// 批量管理
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
//  设备详情/编辑
// =====================================================================
const deviceForm = ref<any>({})
const devicePoints = ref<PointDeploymentItem[]>([])
const isEditing = ref(false)
const detailFormRef = ref<FormInstance>()
const detailRules = ref<FormRules>({
  deviceDeployName: [{ required: true, message: '请输入设备名称', trigger: 'blur' }],
  macAddress: [{ validator: validateMacAddress, trigger: 'blur' }]
})

async function viewDeviceDetail(row: DeviceDeployment) {
  devicesLoading.value = true
  try {
    const detail = await deploymentApi.getDetail(row.deviceDeployId!)
    deviceForm.value = { ...detail }
    devicePoints.value = await deploymentApi.getPoints(row.deviceDeployId!)
    viewMode.value = 'detail'
    isEditing.value = false
  } catch (e: any) {
    ElMessage.error(e.message || '加载设备详情失败')
  } finally {
    devicesLoading.value = false
  }
}

function goBackToList() {
  viewMode.value = 'list'
  isEditing.value = false
  // 刷新当前节点设备列表
  if (selectedNode.value && isLeafNode(selectedNode.value)) {
    loadNodeDevices(selectedNode.value.nodeId)
  }
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
    await store.fetchAllDashboardData()
    ElMessage.success('更新设备属性成功')
    isEditing.value = false
    await viewDeviceDetail(deviceForm.value)
  } catch (err: any) {
    ElMessage.error(err.message || '更新失败')
  } finally {
    submitting.value = false
  }
}

// =====================================================================
//  设备删除
// =====================================================================
function deleteDeviceFromDetail() {
  ElMessageBox.confirm(`确认删除已部署设备 "${deviceForm.value.deviceDeployName}" 吗？该操作将物理删除该设备及其所有部署点位。`, '删除设备确认', {
    confirmButtonText: '确定删除',
    cancelButtonText: '取消',
    type: 'warning',
    confirmButtonClass: 'el-button--danger'
  }).then(async () => {
    try {
      devicesLoading.value = true
      await deploymentApi.remove(deviceForm.value.deviceDeployId)
      ElMessage.success('删除设备成功')
      viewMode.value = 'list'
      await refreshAfterChange()
    } catch (e: any) {
      ElMessage.error(e.message || '删除设备失败')
    } finally {
      devicesLoading.value = false
    }
  }).catch(() => {})
}

function deleteDeviceFromTable(row: DeviceDeployment) {
  ElMessageBox.confirm(`确认删除已部署设备 "${row.deviceDeployName}" 吗？该操作将物理删除该设备及其所有部署点位。`, '删除设备确认', {
    confirmButtonText: '确定删除',
    cancelButtonText: '取消',
    type: 'warning',
    confirmButtonClass: 'el-button--danger'
  }).then(async () => {
    try {
      devicesLoading.value = true
      await deploymentApi.remove(row.deviceDeployId!)
      ElMessage.success('删除设备成功')
      await refreshAfterChange()
    } catch (e: any) {
      ElMessage.error(e.message || '删除设备失败')
    } finally {
      devicesLoading.value = false
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
      devicesLoading.value = true
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
      devicesLoading.value = false
    }
  }).catch(() => {})
}

async function refreshAfterChange() {
  await loadTree()
  await store.fetchAllDashboardData()
  if (selectedNode.value && isLeafNode(selectedNode.value)) {
    await loadNodeDevices(selectedNode.value.nodeId)
  }
}

// =====================================================================
//  部署新设备弹窗
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
const deployRules = ref<FormRules>({
  deviceTmplId: [{ required: true, message: '请选择设备所属模板', trigger: 'change' }],
  deviceDeployName: [{ required: true, message: '请输入部署设备名称', trigger: 'blur' }],
  macAddress: [{ validator: validateMacAddress, trigger: 'blur' }]
})

function openDeployDialog() {
  if (!selectedNode.value) {
    ElMessage.warning('请先从左侧导航树中选择具体的部署节点！')
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
        ElMessage.warning('当前选择的节点无效，请重新在左侧导航树中选择！')
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
        ElMessage.success('部署新设备并生成点位成功！')
        deployDialogVisible.value = false
        await refreshAfterChange()
      } catch (err: any) {
        ElMessage.error(err.message || '部署设备失败')
      } finally {
        deploySubmitting.value = false
      }
    }
  })
}


// =====================================================================
//  设备挂载/卸载
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
    ElMessage.error(e.message || '加载可挂载设备失败')
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
    ElMessage.success(`成功挂载 ${count} 台设备`)
    mountDialogVisible.value = false
    await refreshAfterChange()
  } catch (e: any) {
    ElMessage.error(e.message || '挂载失败')
  } finally {
    mountSubmitting.value = false
  }
}

async function handleUnmount(device: DeviceDeployment) {
  try {
    await ElMessageBox.confirm(
      `确认将设备 "${device.deviceDeployName}" 从当前节点卸载吗？卸载后该设备不再出现在任何导航分组中。`,
      '卸载确认',
      { confirmButtonText: '确定卸载', cancelButtonText: '取消', type: 'warning' },
    )
  } catch {
    return
  }
  try {
    await dashboardNavApi.unmountDevice(device.deviceDeployId!)
    ElMessage.success('已卸载')
    await refreshAfterChange()
  } catch (e: any) {
    ElMessage.error(e.message || '卸载失败')
  }
}

// =====================================================================
//  辅助函数
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
    case 1: return '在线'
    case 2: return '离线'
    case 3: return '维护'
    case 4: return '故障'
    default: return '未知'
  }
}
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

// =====================================================================
//  初始化
// =====================================================================
onMounted(async () => {
  await loadTree()
  if (!store.allTemplates.length) {
    await store.loadTemplates()
  }
})
</script>

<style scoped lang="scss">
.system-deployment-view {
  display: flex;
  gap: 16px;
  height: 100%;
  min-height: 0;
}

// ===== 左侧树面板 =====
.nav-tree-panel {
  width: 320px;
  flex-shrink: 0;
  background-color: $bg-card;
  border: 1px solid $border-color;
  border-radius: 8px;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.panel-header {
  height: 48px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 16px;
  border-bottom: 1px solid $border-color;
  flex-shrink: 0;
}

.panel-title {
  display: flex;
  align-items: center;
  gap: 8px;
  color: $text-primary;
  font-size: 14px;
  font-weight: 600;

  .el-icon {
    color: $accent-green;
    font-size: 16px;
  }
}

.title-icon {
  color: $accent-green;
  font-size: 16px;
}

.detail-header {
  .header-actions {
    display: flex;
    gap: 8px;
  }
}

.tree-body {
  flex: 1;
  overflow-y: auto;
  padding: 8px;
}

.deploy-tree {
  background: transparent !important;

  :deep(.el-tree-node__content) {
    height: 34px;
    border-radius: 4px;
    margin: 1px 0;
    &:hover {
      background-color: rgba(255, 255, 255, 0.06) !important;
    }
  }

  :deep(.el-tree-node.is-current > .el-tree-node__content) {
    background-color: rgba(0, 230, 118, 0.15) !important;
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
  gap: 6px;
  font-size: 13px;
  overflow: hidden;
  flex: 1;

  .node-icon {
    font-size: 14px;
    color: $text-secondary;
    &.real-data-icon {
      color: $accent-green;
    }
  }

  .label-text {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
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
  font-size: 11px;
  color: $accent-green;
  background-color: rgba($accent-green, 0.12);
  padding: 1px 6px;
  border-radius: 10px;
  flex-shrink: 0;
}

.node-actions {
  display: none;
  gap: 2px;
  flex-shrink: 0;
}

.tree-node-row:hover .node-actions {
  display: flex;
}

.del-btn {
  color: $accent-red !important;
}

// ===== 右侧面板 =====
.detail-panel {
  flex: 1;
  min-width: 0;
  background-color: $bg-card;
  border: 1px solid $border-color;
  border-radius: 8px;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.detail-body {
  flex: 1;
  overflow-y: auto;
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.info-card {
  background-color: $bg-dark;
  border: 1px solid $border-color;
  border-radius: 6px;
  padding: 12px 16px;
}

.info-row {
  display: flex;
  padding: 4px 0;
  font-size: 13px;

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
  gap: 12px;
  flex: 1;
  min-height: 0;
}

.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 8px;

  h3 {
    margin: 0;
    font-size: 14px;
    color: $text-primary;
    font-weight: 600;
  }
  .section-count {
    color: $accent-green;
    font-size: 13px;
    font-weight: 500;
  }
}

.header-actions {
  display: flex;
  gap: 8px;
  align-items: center;
}

.search-box-table {
  width: 320px;
  flex-shrink: 0;

  :deep(.el-input__wrapper) {
    background-color: $bg-dark !important;
  }
}

// ===== 设备详情表单 =====
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

// ===== 表格样式 =====
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

.data-tag {
  margin-left: 4px;
}

.addr-code {
  background-color: rgba($text-muted, 0.1);
  padding: 2px 6px;
  border-radius: 4px;
  font-family: monospace;
  color: $text-secondary;
  font-size: 12px;
}

// ===== 点位类型 Badge =====
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

// ===== 状态指示器 =====
.status-dot {
  display: inline-block;
  width: 7px;
  height: 7px;
  border-radius: 50%;
  margin-right: 4px;
  &.online { background-color: $accent-green; box-shadow: 0 0 6px rgba(0, 230, 118, 0.6); }
  &.offline { background-color: $text-muted; }
  &.maintenance { background-color: $accent-yellow; box-shadow: 0 0 6px rgba(255, 213, 79, 0.6); }
  &.fault { background-color: #ff5252; box-shadow: 0 0 6px rgba(255, 82, 82, 0.6); }
}
.status-text {
  font-size: 12px;
  color: $text-secondary;
}

// ===== 通用 =====
.empty-text {
  color: $text-muted;
  font-size: 13px;
}

.select-prompt {
  margin: auto;
}

.form-tip {
  font-size: 12px;
  color: $text-muted;
  margin-top: 4px;
  line-height: 1.4;
}

.mount-selected-tip {
  margin-right: auto;
  color: $accent-green;
  font-size: 13px;
}

.text-secondary {
  color: $text-secondary;
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
</style>
