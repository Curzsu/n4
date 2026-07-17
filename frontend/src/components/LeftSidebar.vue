<template>
  <div :class="['left-sidebar', { collapsed: isCollapsed }]">
    <div class="sidebar-header">
      <div class="site-selector">
        <el-icon><Location /></el-icon>
        <span v-if="!isCollapsed">SITE-JHB-01</span>
      </div>
      <el-input
        v-if="!isCollapsed"
        v-model="searchText"
        placeholder="Search..."
        size="small"
        prefix-icon="Search"
        class="sidebar-search"
      />
    </div>
    <div class="sidebar-tree" v-if="!isCollapsed">
      <el-tree
        :data="visibleTreeData"
        :props="treeProps"
        node-key="id"
        default-expand-all
        highlight-current
        :filter-node-method="filterNode"
        ref="treeRef"
        @node-click="handleNodeClick"
      >
        <template #default="{ data }">
          <span :class="['tree-node-label', `node-${data.type}`]">
            <span v-if="data.type === 'device'" :class="['status-dot', getStatusClass(data.status)]"></span>
            {{ data.label }}
          </span>
        </template>
      </el-tree>
    </div>
    <div class="sidebar-footer">
      <span class="collapse-btn" @click="toggleCollapse">
        {{ isCollapsed ? '>>' : '<< Collapse' }}
      </span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { useRoute } from 'vue-router'
import { Location } from '@element-plus/icons-vue'
import { useDashboardStore } from '@/stores/dashboard'
import type { DeviceTreeNode } from '@/api/types'
import type ElTree from 'element-plus/es/components/tree/index'

const route = useRoute()
const store = useDashboardStore()
const deviceTree = computed(() => store.deviceTree)
const isCollapsed = ref(false)
const searchText = ref('')
const treeRef = ref<InstanceType<typeof ElTree>>()

const treeProps = {
  children: 'children',
  label: 'label',
}

const visibleTreeData = computed(() => {
  if (route.path === '/device-template') {
    return [
      {
        id: 'root',
        label: '全部设备模板',
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
  return deviceTree.value
})

function handleNodeClick(data: any) {
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
  return data.label.toLowerCase().includes(value.toLowerCase())
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
}

.site-selector {
  display: flex;
  align-items: center;
  gap: 6px;
  color: $accent-green;
  font-weight: 600;
  font-size: 14px;
  margin-bottom: 8px;
}

.sidebar-search {
  .el-input__wrapper {
    background-color: $bg-dark !important;
  }
}

.sidebar-tree {
  flex: 1;
  overflow-y: auto;
  padding: 8px 0;
}

.tree-node-label {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
}

.status-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  display: inline-block;
  &.online { background-color: $accent-green; }
  &.offline { background-color: $text-muted; }
  &.maintenance { background-color: $accent-yellow; }
  &.fault { background-color: $accent-red; }
}

.node-system-group {
  color: $text-primary;
  font-weight: 600;
}
.node-system-sub {
  color: $text-secondary;
  font-weight: 500;
}
.node-device {
  color: $text-muted;
  font-size: 12px;
}

.sidebar-footer {
  padding: 12px;
  border-top: 1px solid $border-color;
  text-align: center;
}

.collapse-btn {
  color: $text-secondary;
  font-size: 12px;
  cursor: pointer;
  &:hover { color: $text-primary; }
}
</style>
