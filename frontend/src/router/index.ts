import { createRouter, createWebHistory } from 'vue-router'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      name: 'dashboard',
      component: () => import('@/views/DashboardView.vue'),
    },
    {
      path: '/alarm',
      name: 'alarm',
      component: () => import('@/views/AlarmView.vue'),
    },
    {
      path: '/alarm/config',
      name: 'alarm-config',
      component: () => import('@/views/AlarmConfigView.vue'),
    },
    {
      path: '/alarm/history',
      name: 'alarm-history',
      component: () => import('@/views/AlarmHistoryView.vue'),
    },
    {
      path: '/event',
      name: 'event',
      component: () => import('@/views/AlarmEventView.vue'),
    },
    {
      path: '/device-template',
      name: 'device-template',
      component: () => import('@/views/device-deploy/ProductManagerView.vue'),
    },
    {
      path: '/device-deployment',
      name: 'device-deployment',
      component: () => import('@/views/SystemDeploymentView.vue'),
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
    },
  ],
})

export default router
