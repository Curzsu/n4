import request from './request'
import type { NavTreeNode, DeviceDeployItem, DeleteImpact } from './types'

export const dashboardNavApi = {
  getNavTree(): Promise<NavTreeNode[]> {
    return request.get('/api/dashboard/nav/tree')
  },
  getDevicesByNode(nodeId: number): Promise<DeviceDeployItem[]> {
    return request.get(`/api/dashboard/nav/nodes/${nodeId}/devices`)
  },
  getDeviceById(deviceDeployId: number): Promise<DeviceDeployItem> {
    return request.get(`/api/dashboard/nav/devices/${deviceDeployId}`)
  },

  // ===== System Deployment: Navigation Node CRUD =====
  createNode(payload: {
    nodeName: string
    parentId: number | null
    icon?: string | null
    sortOrder?: number
  }): Promise<{ nodeId: number }> {
    return request.post('/api/dashboard/nav/nodes', payload)
  },
  updateNode(nodeId: number, payload: {
    nodeName: string
    parentId?: number | null
    icon?: string | null
    sortOrder?: number
  }): Promise<unknown> {
    return request.put(`/api/dashboard/nav/nodes/${nodeId}`, payload)
  },
  deleteNode(nodeId: number): Promise<DeleteImpact> {
    return request.delete(`/api/dashboard/nav/nodes/${nodeId}`)
  },
  getDeleteImpact(nodeId: number): Promise<DeleteImpact> {
    return request.get(`/api/dashboard/nav/nodes/${nodeId}/impact`)
  },

  // ===== Device Mount / Unmount =====
  getMountableDevices(): Promise<DeviceDeployItem[]> {
    return request.get('/api/dashboard/nav/devices/mountable')
  },
  mountDevices(nodeId: number, deviceIds: number[]): Promise<number> {
    return request.post(`/api/dashboard/nav/nodes/${nodeId}/devices`, deviceIds)
  },
  unmountDevice(deviceDeployId: number): Promise<number> {
    return request.delete(`/api/dashboard/nav/devices/${deviceDeployId}/mount`)
  },
}
