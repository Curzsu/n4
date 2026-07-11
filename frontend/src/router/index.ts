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
      path: '/devices/:deviceId/monitor',
      name: 'fuxa-monitor',
      component: () => import('@/views/FuxaMonitorView.vue'),
      meta: { fullWidth: true },
    },
  ],
})

export default router
