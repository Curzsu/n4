import request from '@/api/request'
import type { Group, GroupForm } from '@/api/types'

export const groupApi = {
  list(params?: { status?: number }) {
    return request.get<any, Group[]>('/api/groups', { params })
  },
  create(data: GroupForm) {
    return request.post<any, void>('/api/groups', data)
  },
  update(groupId: number, data: GroupForm) {
    return request.put<any, void>(`/api/groups/${groupId}`, data)
  },
  delete(groupId: number) {
    return request.delete<any, void>(`/api/groups/${groupId}`)
  },
  permissionIds(groupId: number) {
    return request.get<any, number[]>(`/api/groups/${groupId}/permissions`)
  },
  assignPermissions(groupId: number, ids: number[]) {
    return request.put<any, void>(`/api/groups/${groupId}/permissions`, { ids })
  },
}
