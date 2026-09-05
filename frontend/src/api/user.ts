import request from '@/api/request'
import type { Group, PageResult, User, UserForm } from '@/api/types'

export const userApi = {
  page(params: { username?: string; realName?: string; status?: number; page: number; pageSize: number }) {
    return request.get<any, PageResult<User>>('/api/users', { params })
  },
  create(data: UserForm) {
    return request.post<any, void>('/api/users', data)
  },
  update(userId: number, data: UserForm) {
    return request.put<any, void>(`/api/users/${userId}`, data)
  },
  delete(userId: number) {
    return request.delete<any, void>(`/api/users/${userId}`)
  },
  updateStatus(userId: number, status: number) {
    return request.put<any, void>(`/api/users/${userId}/status`, { status })
  },
  groups(userId: number) {
    return request.get<any, Group[]>(`/api/users/${userId}/groups`)
  },
  assignGroups(userId: number, ids: number[]) {
    return request.put<any, void>(`/api/users/${userId}/groups`, { ids })
  },
}
