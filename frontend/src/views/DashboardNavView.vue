<template>
  <div class="dashboard-nav-view">
    <LeftSidebar mode="nav" :nav-tree="navTree" :loading="navLoading"
                 @node-click="handleNodeClick" />
    <main class="main-area">
      <DeviceListView
        :devices="currentDevices"
        :title="currentTitle"
        :loading="devicesLoading"
        @view-monitor="goMonitor"
      />
    </main>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import LeftSidebar from '@/components/LeftSidebar.vue'
import DeviceListView from '@/components/DeviceListView.vue'
import { dashboardNavApi } from '@/api/dashboardNav'
import type { NavTreeNode, DeviceDeployItem } from '@/api/types'
import { ElMessage } from 'element-plus'

const router = useRouter()
const navTree = ref<NavTreeNode[]>([])
const navLoading = ref(false)
const currentDevices = ref<DeviceDeployItem[]>([])
const devicesLoading = ref(false)
const currentNode = ref<NavTreeNode | null>(null)

const currentTitle = computed(() => {
  if (!currentNode.value) return '设备列表'
  if (currentNode.value.nodeLevel === 1) {
    return `${currentNode.value.nodeName} - 全部设备`
  }
  return currentNode.value.nodeName
})

async function loadNavTree() {
  navLoading.value = true
  try {
    navTree.value = await dashboardNavApi.getNavTree()
  } catch (e) {
    console.error('加载导航树失败', e)
  } finally {
    navLoading.value = false
  }
}

async function handleNodeClick(node: NavTreeNode) {
  currentNode.value = node
  devicesLoading.value = true
  try {
    if (node.nodeLevel === 1) {
      // 一级节点：前端聚合所有二级 children 的设备
      const childIds = node.children.map(c => c.nodeId)
      const results = await Promise.all(
        childIds.map(id => dashboardNavApi.getDevicesByNode(id))
      )
      currentDevices.value = results.flat()
    } else {
      // 二级节点：直接查
      currentDevices.value = await dashboardNavApi.getDevicesByNode(node.nodeId)
    }
  } catch (e) {
    console.error('加载设备列表失败', e)
    currentDevices.value = []
    ElMessage.error('加载设备列表失败')
  } finally {
    devicesLoading.value = false
  }
}

function goMonitor(device: DeviceDeployItem) {
  router.push(`/dashboard-nav/device/${device.deviceDeployId}`)
}

onMounted(loadNavTree)
</script>

<style scoped lang="scss">
.dashboard-nav-view {
  display: flex;
  width: 100%;
  height: 100%;
}
.main-area {
  flex: 1;
  padding: 16px;
  overflow-y: auto;
}
</style>
