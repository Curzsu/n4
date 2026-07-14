import request from './request'
import type { DeviceTreeNode, MonitorPoint } from './types'

export const deviceApi = {
  getDeviceTree(): Promise<DeviceTreeNode[]> {
    return request.get('/api/devices/tree')
  },
  getDevicePoints(deviceId: number): Promise<MonitorPoint[]> {
    return request.get(`/api/devices/${deviceId}/points`)
  },
  getAllDevices(): Promise<any[]> {
    return request.get('/api/devices')
  },
  createDevice(device: any): Promise<string> {
    return request.post('/api/devices', device)
  },
  updateDevice(id: number, device: any): Promise<string> {
    return request.put(`/api/devices/${id}`, device)
  },
  deleteDevice(id: number): Promise<string> {
    return request.delete(`/api/devices/${id}`)
  },
  moveDevice(id: number, targetPath: string, newName?: string): Promise<string> {
    return request.put(`/api/devices/${id}/move`, null, {
      params: { targetPath, newName }
    })
  },
  addPoint(deviceId: number, point: any): Promise<string> {
    return request.post(`/api/devices/${deviceId}/points`, point)
  },
  updatePoint(pointId: number, point: any): Promise<string> {
    return request.put(`/api/devices/points/${pointId}`, point)
  },
  deletePoint(pointId: number): Promise<string> {
    return request.delete(`/api/devices/points/${pointId}`)
  },
}

