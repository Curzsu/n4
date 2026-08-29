import request from '@/api/request'
import type { PermissionForm, PermissionNode } from '@/api/types'

export const permissionApi = {
  tree() {
    return request.get<any, PermissionNode[]>('/api/permissions/tree')
  },
  create(data: PermissionForm) {
    return request.post<any, void>('/api/permissions', data)
  },
  update(permId: number, data: PermissionForm) {
    return request.put<any, void>(`/api/permissions/${permId}`, data)
  },
  delete(permId: number) {
    return request.delete<any, void>(`/api/permissions/${permId}`)
  },
}
