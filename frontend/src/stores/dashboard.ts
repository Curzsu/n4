import { defineStore } from 'pinia'
import { ref } from 'vue'
import { dashboardApi } from '@/api/dashboard'
import { dashboardNavApi } from '@/api/dashboardNav'
import { templateApi } from '@/api/template'
import type { KpiMetrics, AlarmSummary, AlarmItem, SystemStatusItem, TrendData, DeviceTreeNode, Product, NavTreeNode, DeviceDeployItem, DeviceTemplate, UiTreeNode } from '@/api/types'

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
  const selectedNode = ref<UiTreeNode | null>(null)
  const loading = ref(false)
  const isRightCollapsed = ref(true)

  // ===== Dashboard Nav (Two-level Navigation) =====
  const navTree = ref<NavTreeNode[]>([])
  const navLoading = ref(false)
  const currentDevices = ref<DeviceDeployItem[]>([])
  const devicesLoading = ref(false)
  const currentNavNode = ref<NavTreeNode | null>(null)

  function findNavNodeById(nodes: NavTreeNode[], id: number): NavTreeNode | null {
    for (const n of nodes) {
      if (n.nodeId === id) return n
      if (n.children) {
        const found = findNavNodeById(n.children, id)
        if (found) return found
      }
    }
    return null
  }

  async function loadNavTree() {
    navLoading.value = true
    try {
      navTree.value = await dashboardNavApi.getNavTree()
      if (currentNavNode.value) {
        const refreshed = findNavNodeById(navTree.value, currentNavNode.value.nodeId)
        currentNavNode.value = refreshed
      }
    } catch (e) {
      console.error('Failed to load navigation tree', e)
    } finally {
      navLoading.value = false
    }
  }

  /** Collect all leaf group node IDs: leaf returns itself, non-leaf recursively aggregates */
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
      console.error('Failed to load device list', e)
      currentDevices.value = []
    } finally {
      devicesLoading.value = false
    }
  }

  async function fetchAllDashboardData() {
    loading.value = true
    try {
      const [kpi, alarm, top5, status, power, energy, pue, temp] = await Promise.all([
        dashboardApi.getKpi(),
        dashboardApi.getAlarmSummary(),
        dashboardApi.getAlarmTop5(),
        dashboardApi.getSystemStatus(),
        dashboardApi.getPowerTrend(),
        dashboardApi.getEnergyTrend(),
        dashboardApi.getPueTrend(),
        dashboardApi.getTemperatureTrend(),
      ])
      kpiMetrics.value = kpi
      alarmSummary.value = alarm
      alarmTop5.value = top5
      systemStatus.value = status
      powerTrend.value = power
      energyTrend.value = energy
      pueTrend.value = pue
      temperatureTrend.value = temp
      await loadTemplates()
    } catch (error) {
      console.error('Failed to fetch dashboard data:', error)
    } finally {
      loading.value = false
    }
  }

  /**
   * Narrow refresh after deploy-view mutations (create/update/delete of
   * products, templates, devices): only template list and nav tree change,
   * so skip the 8 dashboard KPI/trend calls the old full refresh made.
   */
  async function refreshAfterMutation() {
    await Promise.all([loadTemplates(), loadNavTree()])
  }

  /** Load device template list from device_template table */
  async function loadTemplates() {
    try {
      allTemplates.value = await templateApi.listTemplates()
    } catch (e) {
      console.error('Failed to load device templates', e)
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
    refreshAfterMutation,
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
