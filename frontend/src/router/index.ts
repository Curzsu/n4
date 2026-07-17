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
      path: '/device-template',
      name: 'device-template',
      component: () => import('@/views/device-deploy/ProductManagerView.vue'),
    },
    {
      path: '/device-deployment',
      name: 'device-deployment',
      component: () => import('@/views/device-deploy/DeviceDeploymentView.vue'),
    },
  ],
})

export default router
