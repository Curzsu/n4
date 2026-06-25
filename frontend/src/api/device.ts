import request from './request'
import type { DeviceTreeNode, MonitorPoint } from './types'

export const deviceApi = {
  getDeviceTree(): Promise<DeviceTreeNode[]> {
    return request.get('/api/devices/tree')
  },
  getDevicePoints(deviceId: number): Promise<MonitorPoint[]> {
    return request.get(`/api/devices/${deviceId}/points`)
  },
}

