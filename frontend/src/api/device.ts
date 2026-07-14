import request from './request'
import type { DeviceTreeNode, FuxaBinding, MonitorPoint } from './types'

export const deviceApi = {
  getDeviceTree(): Promise<DeviceTreeNode[]> {
    return request.get('/api/devices/tree')
  },
  getDevicePoints(deviceId: number): Promise<MonitorPoint[]> {
    return request.get(`/api/devices/${deviceId}/points`)
  },
  getFuxaBinding(deviceId: number): Promise<FuxaBinding> {
    return request.get(`/api/fuxa/devices/${deviceId}`)
  },
}

