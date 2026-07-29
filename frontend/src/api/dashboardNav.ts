import request from './request'
import type { NavTreeNode, DeviceDeployItem } from './types'

export const dashboardNavApi = {
  getNavTree(): Promise<NavTreeNode[]> {
    return request.get('/api/dashboard/nav/tree')
  },
  getDevicesByNode(nodeId: number): Promise<DeviceDeployItem[]> {
    return request.get(`/api/dashboard/nav/nodes/${nodeId}/devices`)
  },
}
