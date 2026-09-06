import request from './request'
import type { DeviceDeployment, ImportSummary, PointDeploymentItem } from './types'

export const deploymentApi = {
  listAll(): Promise<DeviceDeployment[]> {
    return request.get('/api/deployments')
  },
  listByNode(nodeId: number): Promise<DeviceDeployment[]> {
    return request.get(`/api/deployments/nodes/${nodeId}`)
  },
  getDetail(deviceDeployId: number): Promise<DeviceDeployment> {
    return request.get(`/api/deployments/${deviceDeployId}`)
  },
  getPoints(deviceDeployId: number): Promise<PointDeploymentItem[]> {
    return request.get(`/api/deployments/${deviceDeployId}/points`)
  },
  create(payload: Partial<DeviceDeployment>): Promise<DeviceDeployment> {
    return request.post('/api/deployments', payload)
  },
  update(deviceDeployId: number, payload: Partial<DeviceDeployment>): Promise<string> {
    return request.put(`/api/deployments/${deviceDeployId}`, payload)
  },
  move(deviceDeployId: number, targetNodeId: number, newName: string): Promise<string> {
    return request.put(`/api/deployments/${deviceDeployId}/move`, { targetNodeId, newName })
  },
  updatePoint(pointDeployId: number, point: Partial<PointDeploymentItem>): Promise<string> {
    return request.put(`/api/deployments/points/${pointDeployId}`, point)
  },
  remove(deviceDeployId: number): Promise<string> {
    return request.delete(`/api/deployments/${deviceDeployId}`)
  },
  importFiles(files: File[], dryRun = false): Promise<ImportSummary> {
    const formData = new FormData()
    for (const file of files) {
      formData.append('files', file)
    }
    return request.post('/api/deployments/import', formData, {
      params: { dryRun },
      timeout: 120000,
    })
  },
  downloadImportTemplate(): Promise<Blob> {
    return request.get('/api/deployments/import-template', {
      responseType: 'blob',
      timeout: 120000,
    })
  },
  exportSnapshot(): Promise<Blob> {
    return request.get('/api/deployments/export', {
      responseType: 'blob',
      timeout: 120000,
    })
  },
}
