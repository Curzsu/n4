import request from './request'
import type { DeviceTemplate, ImportSummary, PointTemplateItem } from './types'

export const templateApi = {
  listTemplates(): Promise<DeviceTemplate[]> {
    return request.get('/api/templates')
  },
  getTemplate(id: number): Promise<DeviceTemplate> {
    return request.get(`/api/templates/${id}`)
  },
  createTemplate(template: Partial<DeviceTemplate>): Promise<DeviceTemplate> {
    return request.post('/api/templates', template)
  },
  updateTemplate(id: number, template: Partial<DeviceTemplate>): Promise<string> {
    return request.put(`/api/templates/${id}`, template)
  },
  deleteTemplate(id: number): Promise<string> {
    return request.delete(`/api/templates/${id}`)
  },
  getPoints(id: number): Promise<PointTemplateItem[]> {
    return request.get(`/api/templates/${id}/points`)
  },
  addPoint(id: number, point: Partial<PointTemplateItem>): Promise<PointTemplateItem> {
    return request.post(`/api/templates/${id}/points`, point)
  },
  updatePoint(pointId: number, point: Partial<PointTemplateItem>): Promise<string> {
    return request.put(`/api/templates/points/${pointId}`, point)
  },
  deletePoint(pointId: number): Promise<string> {
    return request.delete(`/api/templates/points/${pointId}`)
  },
  importWorkbook(file: File, dryRun = false): Promise<ImportSummary> {
    const formData = new FormData()
    formData.append('file', file)
    return request.post('/api/templates/import', formData, {
      params: { dryRun },
      timeout: 120000,
    })
  },
  downloadImportTemplate(): Promise<Blob> {
    return request.get('/api/templates/import-template', {
      responseType: 'blob',
      timeout: 120000,
    })
  },
  exportSnapshot(): Promise<Blob> {
    return request.get('/api/templates/export', {
      responseType: 'blob',
      timeout: 120000,
    })
  },
}
