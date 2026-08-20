<template>
  <div :class="['left-sidebar', { collapsed: isCollapsed }]">
    <!-- Sidebar Header -->
    <div class="sidebar-header">
      <div class="site-selector">
        <el-icon><Location /></el-icon>
        <span v-if="!isCollapsed" class="site-title">Device Navigation</span>
      </div>

      <div v-if="!isCollapsed" class="header-actions">
        <el-input
          v-model="searchText"
          placeholder="Search systems or devices..."
          size="small"
          :prefix-icon="Search"
          clearable
          class="sidebar-search"
        />
        <div class="quick-tools">
          <el-tooltip v-if="mode !== 'nav'" content="Refresh Device Tree" placement="top">
            <el-button type="info" size="small" circle :icon="RefreshRight" @click="refreshTreeData" />
          </el-tooltip>
        </div>
      </div>
    </div>

    <!-- Sidebar Navigation Tree -->
    <div class="sidebar-tree" v-if="!isCollapsed" v-loading="loading || false">
      <el-tree
        :data="visibleTreeData"
        :props="treeProps"
        node-key="id"
        default-expand-all
        highlight-current
        :filter-node-method="filterNode"
        ref="treeRef"
        @node-click="handleNodeClick"
        class="custom-nav-tree"
      >
        <template #default="{ data }">
          <div class="tree-node-row">
            <span :class="['tree-node-label', `node-${data.type}`]">
              <!-- Real Status Dot for Devices -->
              <span 
                v-if="data.type === 'device'" 
                :class="['status-dot', getStatusClass(data.status)]"
              ></span>

              <!-- Node Icon based on type -->
              <el-icon class="node-icon" :class="{ 'real-data-icon': data.hasRealData }" v-if="getNodeIcon(data)">
                <component :is="getNodeIcon(data)" />
              </el-icon>

              <span class="label-text" :title="data.label">{{ data.label }}</span>
            </span>

            <!-- Device Code Badge if present -->
            <span class="device-code-badge" v-if="data.deviceCode && data.type === 'device'">
              {{ data.deviceCode }}
            </span>
          </div>
        </template>
      </el-tree>
    </div>

    <!-- Sidebar Footer / Collapse Trigger -->
    <div class="sidebar-footer">
      <span class="collapse-btn" @click="toggleCollapse">
        <el-icon><Expand v-if="isCollapsed" /><Fold v-else /></el-icon>
        <span v-if="!isCollapsed">Collapse Sidebar</span>
      </span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { useRoute } from 'vue-router'
import {
  Location,
  Search,
  RefreshRight,
  Expand,
  Fold,
  OfficeBuilding,
  Cpu,
  FolderOpened,
  Grid,
  Box,
  Monitor,
  DataLine,
  DataAnalysis
} from '@element-plus/icons-vue'
import type { TreeNodeData } from 'element-plus'
import { ElMessage } from 'element-plus'
import { useDashboardStore } from '@/stores/dashboard'
import type { NavTreeNode, UiTreeNode } from '@/api/types'
import type ElTree from 'element-plus/es/components/tree/index'

const route = useRoute()
const store = useDashboardStore()

const props = defineProps<{
  mode?: 'default' | 'nav'
  navTree?: NavTreeNode[]
  loading?: boolean
  // Collapse state when this sidebar first mounts or the route changes.
  defaultCollapsed?: boolean
}>()

const emit = defineEmits<{
  (e: 'node-click', node: NavTreeNode): void
}>()

const isCollapsed = ref(props.defaultCollapsed ?? false)
const searchText = ref('')
const treeRef = ref<InstanceType<typeof ElTree>>()

// Reset to the route's default collapse state whenever it changes.
watch(() => props.defaultCollapsed, (val) => {
  isCollapsed.value = val ?? false
})

const treeProps = {
  children: 'children',
  label: 'label',
}

const isNavBasedRoute = computed(() =>
  props.mode === 'nav' || route.path === '/device-deployment' || route.path === '/system-deployment'
)

const visibleTreeData = computed(() => {
  if (isNavBasedRoute.value) {
    if (route.path === '/') {
      const overviewNode: UiTreeNode = {
        id: 'overview',
        label: 'Data Overview',
        type: 'site',
        status: 1,
        raw: null,
        children: []
      }
      return [overviewNode, ...(props.navTree || store.navTree || []).map(n => toTreeNode(n))]
    }
    return (props.navTree || store.navTree || []).map(n => toTreeNode(n))
  }
  if (route.path === '/device-template') {
    return [
      {
        id: 'root_tmpl',
        label: 'All Device Templates',
        type: 'site',
        status: 1,
        children: store.allTemplates.map(t => ({
          id: t.deviceTmplId || 0,
          label: t.deviceTmplName + (t.isReal ? '' : ' (Mock)'),
          type: 'product',
          status: 1,
          isMock: !t.isReal,
          children: []
        }))
      }
    ]
  }

  return store.deviceTree
})

// Convert NavTreeNode to el-tree node
function toTreeNode(n: NavTreeNode): UiTreeNode {
  const isLeafGroup = !n.children || n.children.length === 0
  return {
    id: n.nodeId,
    label: isLeafGroup && n.deviceCount != null
      ? `${n.nodeName} (${n.deviceCount})`
      : n.nodeName,
    type: n.nodeLevel === 1 ? 'system-group' : n.nodeLevel === 2 ? 'system-sub' : 'system-sub-deep',
    status: 1,
    nodeLevel: n.nodeLevel,
    hasRealData: !!n.hasRealData,
    raw: n,
    children: (n.children || []).map(toTreeNode)
  }
}

async function refreshTreeData() {
  await store.loadNavTree()
  ElMessage.success('Device tree refreshed successfully')
}

function getNodeIcon(data: UiTreeNode) {
  if (data.id === 'overview') return DataAnalysis
  if (data.type === 'site') return Location
  if (data.type === 'device') return Monitor
  if (data.type === 'product') return Cpu
  if (data.hasRealData) return DataLine
  if (data.type === 'system-group') return FolderOpened
  if (data.type === 'system-sub') return Grid
  if (data.type === 'system-sub-deep') return Box
  return OfficeBuilding
}

function handleNodeClick(data: UiTreeNode) {
  if (isNavBasedRoute.value) {
    // Clicking "Data Overview" virtual node: reset selection to return to home overview
    if (data.id === 'overview') {
      store.currentNavNode = null
      store.currentDevices = []
      return
    }
    if (data.raw) {
      emit('node-click', data.raw)
      store.currentNavNode = data.raw
      return
    }
  }
  store.selectedNode = data
}

watch(() => store.currentNavNode, (node) => {
  if (isNavBasedRoute.value && node && treeRef.value) {
    treeRef.value.setCurrentKey(node.nodeId)
  }
})

function toggleCollapse() {
  isCollapsed.value = !isCollapsed.value
}

function getStatusClass(status: number): string {
  switch (status) {
    case 1: return 'online'
    case 2: return 'offline'
    case 3: return 'maintenance'
    case 4: return 'fault'
    default: return 'online'
  }
}

function filterNode(value: string, data: TreeNodeData): boolean {
  if (!value) return true
  const query = value.toLowerCase()
  const node = data as UiTreeNode
  const labelMatch = node.label ? node.label.toLowerCase().includes(query) : false
  const codeMatch = node.deviceCode ? node.deviceCode.toLowerCase().includes(query) : false
  return labelMatch || codeMatch
}

watch(searchText, (val) => {
  treeRef.value?.filter(val)
})
</script>

<style scoped lang="scss">
.left-sidebar {
  width: $sidebar-width;
  background-color: #111827;
  border-right: 1px solid #1f2937;
  display: flex;
  flex-direction: column;
  transition: width 0.3s;
  flex-shrink: 0;
  overflow: hidden;

  &.collapsed {
    width: $sidebar-collapsed-width;
  }
}

.sidebar-header {
  padding: 8px 12px;
  border-bottom: 1px solid #1f2937;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.site-selector {
  display: flex;
  align-items: center;
  gap: 6px;
  color: $accent-green;
  font-weight: 600;
  font-size: 11px;

  .site-title {
    letter-spacing: 0.5px;
  }
}

.header-actions {
  display: flex;
  flex-direction: column;
  gap: 6px;

  .sidebar-search {
    :deep(.el-input__wrapper) {
      background-color: #0b1119 !important;
      box-shadow: none !important;
      border: 1px solid #1f2937;
      height: 22px !important;
      padding: 0 6px !important;

      &:hover, &.is-focus {
        border-color: $accent-green;
      }
    }

    :deep(.el-input__inner) {
      font-size: 10px !important;
      height: 22px !important;
      line-height: 22px !important;
    }
  }

  .quick-tools {
    display: flex;
    justify-content: flex-end;
    gap: 4px;

    :deep(.el-button) {
      height: 18px !important;
      width: 18px !important;
      padding: 0 !important;
      font-size: 10px !important;
    }
  }
}

.sidebar-tree {
  flex: 1;
  overflow-y: auto;
  padding: 6px;
}

/* Custom Tree Styling */
.custom-nav-tree {
  background: transparent !important;
  color: #e5e7eb;
  font-size: 10px !important;

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
  text-overflow: ellipsis;
  white-space: nowrap;

  .node-icon {
    font-size: 12px;
    color: $text-secondary;
  }

  .real-data-icon {
    color: $accent-green;
  }

  .label-text {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
    font-size: 10px;
  }
}

.device-code-badge {
  font-size: 9px;
  color: $text-secondary;
  background-color: rgba(255, 255, 255, 0.05);
  padding: 0 3px;
  border-radius: 2px;
  font-family: monospace;
  line-height: 14px;
}

.status-dot {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  display: inline-block;
  flex-shrink: 0;
  &.online { background-color: $accent-green; box-shadow: 0 0 4px rgba(0,230,118,0.6); }
  &.offline { background-color: $text-muted; }
  &.maintenance { background-color: $accent-yellow; box-shadow: 0 0 4px rgba(255,213,79,0.6); }
  &.fault { background-color: $accent-red; box-shadow: 0 0 4px rgba(255,82,82,0.6); }
}

.node-system-group {
  color: #64b5f6;
  font-weight: 600;
}
.node-system-sub {
  color: $text-primary;
  font-weight: 500;
}
.node-system-sub-deep {
  color: $text-primary;
  font-weight: 400;
  padding-left: 2px;
}
.node-device {
  color: $text-muted;
  font-size: 10px;
}

.sidebar-footer {
  padding: 6px 10px;
  border-top: 1px solid #1f2937;
  text-align: center;
}

.collapse-btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  color: $text-secondary;
  font-size: 10px;
  cursor: pointer;
  transition: color 0.2s;
  &:hover { color: $text-primary; }
}
</style>
