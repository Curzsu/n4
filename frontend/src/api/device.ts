import request from './request'
import type { DeviceTreeNode } from './types'

export const deviceApi = {
  getDeviceTree(): Promise<DeviceTreeNode[]> {
    return request.get('/api/devices/tree')
  },
}
