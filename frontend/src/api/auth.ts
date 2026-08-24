import request from '@/api/request'
import type { CurrentUser, LoginResponse } from '@/api/types'

export const authApi = {
  login(data: { username: string; password: string }) {
    return request.post<any, LoginResponse>('/api/auth/login', data)
  },
  logout() {
    return request.post<any, void>('/api/auth/logout')
  },
  currentUser() {
    return request.get<any, CurrentUser>('/api/auth/current-user')
  },
  changePassword(data: { oldPassword?: string; newPassword: string }) {
    return request.put<any, void>('/api/auth/password', data)
  },
}
