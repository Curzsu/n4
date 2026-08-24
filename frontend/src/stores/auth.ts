import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { authApi } from '@/api/auth'
import type { CurrentUser, PermissionNode } from '@/api/types'

const TOKEN_KEY = 'n4_token'

export const useAuthStore = defineStore('auth', () => {
  const token = ref(localStorage.getItem(TOKEN_KEY) || '')
  const currentUser = ref<CurrentUser | null>(null)

  const isLoggedIn = computed(() => Boolean(token.value))
  const permissions = computed(() => currentUser.value?.permissions || [])
  const menus = computed(() => currentUser.value?.menus || [])

  function hasPermission(code: string) {
    if (!code) return true
    if (permissions.value.includes('*')) return true
    if (currentUser.value?.groups?.some((g) => g.groupCode === 'SUPER_ADMIN')) return true
    if (permissions.value.includes(code)) return true
    if (permissions.value.includes(`${code}:view`)) return true
    if (code.endsWith(':view') && permissions.value.includes(code.replace(/:view$/, ''))) return true
    return false
  }

  async function login(username: string, password: string) {
    const response = await authApi.login({ username, password })
    token.value = response.token
    currentUser.value = response.user
    localStorage.setItem(TOKEN_KEY, response.token)
  }

  async function loadCurrentUser() {
    if (!token.value) return
    currentUser.value = await authApi.currentUser()
  }

  async function logout() {
    try {
      if (token.value) await authApi.logout()
    } finally {
      token.value = ''
      currentUser.value = null
      localStorage.removeItem(TOKEN_KEY)
    }
  }

  function flattenMenus(nodes: PermissionNode[] = []): PermissionNode[] {
    return nodes.flatMap((node) => [node, ...flattenMenus(node.children || [])])
  }

  return {
    token,
    currentUser,
    isLoggedIn,
    permissions,
    menus,
    hasPermission,
    login,
    loadCurrentUser,
    logout,
    flattenMenus,
  }
})
