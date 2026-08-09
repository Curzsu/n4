import request from './request'
import type { DeviceTreeNode, FuxaBinding, FuxaCabinSession, MonitorPoint } from './types'

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
<<<<<<< HEAD
  getConfiguredFuxaCabins(): Promise<string[]> {
    return request.get('/api/fuxa/cabins')
  },
  openFuxaCabinSession(cabinName: string, sessionId: string): Promise<FuxaCabinSession> {
    return request.post(`/api/fuxa/cabins/${encodeURIComponent(cabinName)}/sessions/${encodeURIComponent(sessionId)}`)
  },
  heartbeatFuxaCabinSession(cabinName: string, sessionId: string): Promise<FuxaCabinSession> {
    return request.post(
      `/api/fuxa/cabins/${encodeURIComponent(cabinName)}/sessions/${encodeURIComponent(sessionId)}/heartbeat`
    )
  },
  releaseFuxaCabinSession(cabinName: string, sessionId: string): Promise<FuxaCabinSession> {
    return request.delete(`/api/fuxa/cabins/${encodeURIComponent(cabinName)}/sessions/${encodeURIComponent(sessionId)}`)
=======
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
>>>>>>> 9bc64422fcd6a94bb47bdf1ff1cacb7eeca2182e
  },
}

