<template>
  <div :class="['left-sidebar', { collapsed: isCollapsed }]">
    <!-- Sidebar Header -->
    <div class="sidebar-header">
      <div class="site-selector">
        <el-icon><Location /></el-icon>
        <span v-if="!isCollapsed" class="site-title">设备导航</span>
      </div>
      
      <div v-if="!isCollapsed" class="header-actions">
        <el-input
          v-model="searchText"
          placeholder="搜索系统或真实设备..."
          size="small"
          :prefix-icon="Search"
          clearable
          class="sidebar-search"
        />
        <div class="quick-tools">
          <el-tooltip v-if="mode !== 'nav'" content="刷新真实设备树" placement="top">
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
              <el-icon class="node-icon" v-if="getNodeIcon(data)">
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
        <span v-if="!isCollapsed">折叠导航栏</span>
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
  Monitor
} from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { useDashboardStore } from '@/stores/dashboard'
import type { DeviceTreeNode, NavTreeNode } from '@/api/types'
import type ElTree from 'element-plus/es/components/tree/index'

const route = useRoute()
const store = useDashboardStore()

const props = defineProps<{
  mode?: 'default' | 'nav'
  navTree?: NavTreeNode[]
  loading?: boolean
}>()

const emit = defineEmits<{
  (e: 'node-click', node: NavTreeNode): void
}>()

const isCollapsed = ref(false)
const searchText = ref('')
const treeRef = ref<InstanceType<typeof ElTree>>()

const treeProps = {
  children: 'children',
  label: 'label',
}

/**
 * Option A: Pure 100% Real Database Tree
 * Renders devices, systems, and product templates fetched directly from the MySQL database.
 */
const visibleTreeData = computed(() => {
  if (props.mode === 'nav') {
    return (props.navTree || []).map(n => toTreeNode(n))
  }
  if (route.path === '/device-template') {
    return [
      {
        id: 'root_tmpl',
        label: '全部设备模板 (数据库真实表)',
        type: 'site',
        status: 1,
        children: store.allProducts.map(p => ({
          id: p.productId || 0,
          label: p.productName,
          type: 'product',
          status: 1,
          children: []
        }))
      }
    ]
  }

  // Pure 100% Real Device Tree from MySQL DB via backend API
  return store.deviceTree
})

// NavTreeNode 转 el-tree 节点
function toTreeNode(n: NavTreeNode): any {
  return {
    id: n.nodeId,
    label: n.nodeLevel === 2 && n.deviceCount != null
      ? `${n.nodeName} (${n.deviceCount})`
      : n.nodeName,
    type: n.nodeLevel === 1 ? 'system-group' : 'system-sub',
    status: 1,
    nodeLevel: n.nodeLevel,
    raw: n,
    children: (n.children || []).map(toTreeNode)
  }
}

function refreshTreeData() {
  store.fetchAllDashboardData()
  ElMessage.success('已从本地数据库刷新最新设备数据树')
}

function getNodeIcon(data: any) {
  if (data.type === 'site') return Location
  if (data.type === 'device') return Monitor
  if (data.type === 'product') return Cpu
  if (data.type === 'system-group') return FolderOpened
  if (data.type === 'system-sub') return Grid
  return OfficeBuilding
}

function handleNodeClick(data: any) {
  if (props.mode === 'nav' && data.raw) {
    emit('node-click', data.raw)
    return
  }
  store.selectedNode = data
}

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

function filterNode(value: string, data: DeviceTreeNode): boolean {
  if (!value) return true
  const query = value.toLowerCase()
  const labelMatch = data.label ? data.label.toLowerCase().includes(query) : false
  const codeMatch = data.deviceCode ? data.deviceCode.toLowerCase().includes(query) : false
  return labelMatch || codeMatch
}

watch(searchText, (val) => {
  treeRef.value?.filter(val)
})
</script>

<style scoped lang="scss">
.left-sidebar {
  width: $sidebar-width;
  background-color: $bg-card;
  border-right: 1px solid $border-color;
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
  padding: 12px;
  border-bottom: 1px solid $border-color;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.site-selector {
  display: flex;
  align-items: center;
  gap: 8px;
  color: $accent-green;
  font-weight: 700;
  font-size: 14px;

  .site-title {
    letter-spacing: 0.5px;
  }
}

.header-actions {
  display: flex;
  flex-direction: column;
  gap: 8px;

  .sidebar-search {
    :deep(.el-input__wrapper) {
      background-color: $bg-dark !important;
      box-shadow: none !important;
      border: 1px solid $border-color;
      &:hover, &.is-focus {
        border-color: $accent-green;
      }
    }
  }

  .quick-tools {
    display: flex;
    justify-content: flex-end;
    gap: 6px;
  }
}

.sidebar-tree {
  flex: 1;
  overflow-y: auto;
  padding: 8px 4px;
}

/* Custom Tree Styling */
.custom-nav-tree {
  background: transparent !important;
  color: $text-primary;

  :deep(.el-tree-node__content) {
    height: 32px;
    border-radius: 4px;
    margin: 1px 0;
    &:hover {
      background-color: rgba(255, 255, 255, 0.06) !important;
    }
  }

  :deep(.el-tree-node.is-current > .el-tree-node__content) {
    background-color: rgba(0, 230, 118, 0.15) !important;
    color: $accent-green !important;
    font-weight: 600;
  }
}

.tree-node-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  padding-right: 6px;
  overflow: hidden;
}

.tree-node-label {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;

  .node-icon {
    font-size: 14px;
    color: $text-secondary;
  }

  .label-text {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}

.device-code-badge {
  font-size: 10px;
  color: $text-secondary;
  background-color: rgba(255, 255, 255, 0.05);
  padding: 1px 4px;
  border-radius: 3px;
  font-family: monospace;
}

.status-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  display: inline-block;
  flex-shrink: 0;
  &.online { background-color: $accent-green; box-shadow: 0 0 6px rgba(0,230,118,0.6); }
  &.offline { background-color: $text-muted; }
  &.maintenance { background-color: $accent-yellow; box-shadow: 0 0 6px rgba(255,213,79,0.6); }
  &.fault { background-color: $accent-red; box-shadow: 0 0 6px rgba(255,82,82,0.6); }
}

.node-system-group {
  color: #64b5f6;
  font-weight: 600;
}
.node-system-sub {
  color: $text-primary;
  font-weight: 500;
}
.node-device {
  color: $text-muted;
  font-size: 12px;
}

.sidebar-footer {
  padding: 10px;
  border-top: 1px solid $border-color;
  text-align: center;
}

.collapse-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: $text-secondary;
  font-size: 12px;
  cursor: pointer;
  transition: color 0.2s;
  &:hover { color: $text-primary; }
}
</style>
