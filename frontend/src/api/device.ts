import request from './request'
import type { ApiResponse, DeviceTreeNode, FuxaBinding, FuxaCabinSession, MonitorPoint } from './types'

const TOKEN_KEY = 'n4_token'

function getFuxaSessionHeaders(includeJsonContentType = false): HeadersInit {
  const headers: Record<string, string> = {}
  const token = localStorage.getItem(TOKEN_KEY)
  if (token) headers.Authorization = `Bearer ${token}`
  if (includeJsonContentType) headers['Content-Type'] = 'application/json'
  return headers
}

export function getFuxaSessionAccessToken(): string {
  return localStorage.getItem(TOKEN_KEY) || ''
}

async function requestCabinSession(url: string, method: 'POST' | 'DELETE'): Promise<FuxaCabinSession> {
  const response = await fetch(url, {
    method,
    // This path intentionally uses fetch instead of Axios so the page-exit
    // request can use keepalive. Add the same JWT header as request.ts.
    headers: getFuxaSessionHeaders(method === 'POST'),
    body: method === 'POST' ? '{}' : undefined,
    credentials: 'same-origin',
  })
  const payload = await response.json() as ApiResponse<FuxaCabinSession>
  if (!response.ok || payload.code !== 200) {
    throw new Error(payload.message || `FUXA cabin request failed (${response.status})`)
  }
  return payload.data
}

export function getFuxaSessionAuthorizationHeader(): HeadersInit {
  return getFuxaSessionHeaders()
}

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
  preloadFuxaCabinRuntime(cabinName: string): Promise<FuxaCabinSession> {
    return request.post(`/api/fuxa/cabins/${encodeURIComponent(cabinName)}/runtime/preload`)
  },
  openFuxaCabinSession(cabinName: string, sessionId: string): Promise<FuxaCabinSession> {
    return requestCabinSession(
      `/api/fuxa/cabins/${encodeURIComponent(cabinName)}/sessions/${encodeURIComponent(sessionId)}`,
      'POST',
    )
  },
  releaseFuxaCabinSession(cabinName: string, sessionId: string): Promise<FuxaCabinSession> {
    return requestCabinSession(
      `/api/fuxa/cabins/${encodeURIComponent(cabinName)}/sessions/${encodeURIComponent(sessionId)}`,
      'DELETE',
    )
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

