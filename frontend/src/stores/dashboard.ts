import { defineStore } from 'pinia'
import { ref } from 'vue'
import { dashboardApi } from '@/api/dashboard'
import { deviceApi } from '@/api/device'
import { productApi } from '@/api/product'
import { dashboardNavApi } from '@/api/dashboardNav'
import { templateApi } from '@/api/template'
import type { KpiMetrics, AlarmSummary, AlarmItem, SystemStatusItem, TrendData, DeviceTreeNode, Product, NavTreeNode, DeviceDeployItem, DeviceTemplate } from '@/api/types'

export const useDashboardStore = defineStore('dashboard', () => {
  const kpiMetrics = ref<KpiMetrics | null>(null)
  const alarmSummary = ref<AlarmSummary | null>(null)
  const alarmTop5 = ref<AlarmItem[]>([])
  const systemStatus = ref<SystemStatusItem[]>([])
  const powerTrend = ref<TrendData | null>(null)
  const energyTrend = ref<TrendData | null>(null)
  const pueTrend = ref<TrendData | null>(null)
  const temperatureTrend = ref<TrendData | null>(null)
  const deviceTree = ref<DeviceTreeNode[]>([])
  const allProducts = ref<Product[]>([])
  const allTemplates = ref<DeviceTemplate[]>([])
  const selectedNode = ref<any | null>(null)
  const loading = ref(false)
  const isRightCollapsed = ref(true)

  // ===== Dashboard Nav (两级导航) =====
  const navTree = ref<NavTreeNode[]>([])
  const navLoading = ref(false)
  const currentDevices = ref<DeviceDeployItem[]>([])
  const devicesLoading = ref(false)
  const currentNavNode = ref<NavTreeNode | null>(null)

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

  /** 收集节点下所有叶子分组节点ID：叶子返回自身，非叶子递归聚合后代叶子 */
  function collectLeafGroupIds(node: NavTreeNode): number[] {
    if (!node.children || node.children.length === 0) {
      return [node.nodeId]
    }
    return node.children.flatMap(collectLeafGroupIds)
  }

  async function selectNavNode(node: NavTreeNode) {
    currentNavNode.value = node
    devicesLoading.value = true
    try {
      const leafIds = collectLeafGroupIds(node)
      const results = await Promise.all(
        leafIds.map(id => dashboardNavApi.getDevicesByNode(id))
      )
      currentDevices.value = results.flat()
    } catch (e) {
      console.error('加载设备列表失败', e)
      currentDevices.value = []
    } finally {
      devicesLoading.value = false
    }
  }

  async function fetchAllDashboardData() {
    loading.value = true
    try {
      const [kpi, alarm, top5, status, power, energy, pue, temp, productsList, devicesTreeFromBackend] = await Promise.all([
        dashboardApi.getKpi(),
        dashboardApi.getAlarmSummary(),
        dashboardApi.getAlarmTop5(),
        dashboardApi.getSystemStatus(),
        dashboardApi.getPowerTrend(),
        dashboardApi.getEnergyTrend(),
        dashboardApi.getPueTrend(),
        dashboardApi.getTemperatureTrend(),
        productApi.getAllProducts(),
        deviceApi.getDeviceTree(),
      ])
      kpiMetrics.value = kpi
      alarmSummary.value = alarm
      alarmTop5.value = top5
      systemStatus.value = status
      powerTrend.value = power
      energyTrend.value = energy
      pueTrend.value = pue
      temperatureTrend.value = temp
      allProducts.value = productsList

      deviceTree.value = devicesTreeFromBackend
      await loadTemplates()
    } catch (error) {
      console.error('Failed to fetch dashboard data:', error)
    } finally {
      loading.value = false
    }
  }

  /** 加载设备模板列表（device_template 表，含真实/Mock 标识） */
  async function loadTemplates() {
    try {
      allTemplates.value = await templateApi.listTemplates()
    } catch (e) {
      console.error('加载设备模板列表失败', e)
    }
  }

  return {
    kpiMetrics,
    alarmSummary,
    alarmTop5,
    systemStatus,
    powerTrend,
    energyTrend,
    pueTrend,
    temperatureTrend,
    deviceTree,
    allProducts,
    allTemplates,
    selectedNode,
    loading,
    isRightCollapsed,
    fetchAllDashboardData,
    loadTemplates,
    navTree,
    navLoading,
    currentDevices,
    devicesLoading,
    currentNavNode,
    loadNavTree,
    selectNavNode,
  }
})
