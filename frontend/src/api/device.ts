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
  },
}

