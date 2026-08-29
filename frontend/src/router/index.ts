import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/login',
      name: 'login',
      component: () => import('@/views/LoginView.vue'),
      meta: { public: true, layout: 'blank' },
    },
    {
      path: '/403',
      name: 'forbidden',
      component: () => import('@/views/ForbiddenView.vue'),
      meta: { public: true, layout: 'blank' },
    },
    {
      path: '/',
      name: 'dashboard',
      component: () => import('@/views/DashboardView.vue'),
      meta: { permission: 'dashboard:view' },
    },
    {
      path: '/alarm',
      name: 'alarm',
      component: () => import('@/views/AlarmView.vue'),
      meta: { permission: 'alarm:view' },
    },
    {
      path: '/alarm/config',
      name: 'alarm-config',
      component: () => import('@/views/AlarmConfigView.vue'),
      meta: { permission: 'alarm:config:view' },
    },
    {
      path: '/alarm/history',
      name: 'alarm-history',
      component: () => import('@/views/AlarmHistoryView.vue'),
      meta: { permission: 'alarm:history:view' },
    },
    {
      path: '/event',
      name: 'event',
      component: () => import('@/views/AlarmEventView.vue'),
      meta: { permission: 'alarm:event:view' },
    },
    {
      path: '/device-template',
      name: 'device-template',
      component: () => import('@/views/device-deploy/ProductManagerView.vue'),
      meta: { permission: 'device:template:view' },
    },
    {
      path: '/device-deployment',
      name: 'device-deployment',
      component: () => import('@/views/SystemDeploymentView.vue'),
      meta: { permission: 'device:deploy:view' },
    },
    {
      path: '/system-deployment',
      name: 'system-deployment',
      redirect: '/device-deployment',
    },
    {
      path: '/dashboard-nav/device/:deviceDeployId',
      name: 'dashboard-nav-device',
      component: () => import('@/views/DeviceMonitorView.vue'),
      meta: { permission: 'dashboard:view' },
    },
    {
      path: '/system',
      redirect: '/device-template',
    },
    {
      path: '/system/users',
      name: 'system-users',
      component: () => import('@/views/system/UserManagementView.vue'),
      meta: { permission: 'sys:user:list' },
    },
    {
      path: '/system/groups',
      name: 'system-groups',
      component: () => import('@/views/system/GroupManagementView.vue'),
      meta: { permission: 'sys:group:list' },
    },
    {
      path: '/system/permissions',
      name: 'system-permissions',
      component: () => import('@/views/system/PermissionManagementView.vue'),
      meta: { permission: 'sys:permission:list' },
    },
    {
      path: '/logs',
      name: 'logs',
      component: () => import('@/views/LogView.vue'),
      meta: { permission: 'sys:log:list' },
    },
  ],
})

router.beforeEach(async (to) => {
  const authStore = useAuthStore()
  if (to.meta.public) {
    if (to.path === '/login' && authStore.isLoggedIn) {
      return '/'
    }
    return true
  }
  if (!authStore.isLoggedIn) {
    return '/login'
  }
  if (!authStore.currentUser) {
    try {
      await authStore.loadCurrentUser()
    } catch {
      await authStore.logout()
      return '/login'
    }
  }
  const permission = to.meta.permission as string | undefined
  if (permission && !authStore.hasPermission(permission)) {
    return '/403'
  }
  return true
})

export default router
