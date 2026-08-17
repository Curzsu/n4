<template>
  <MainLayout>
    <RouterView />
  </MainLayout>
</template>

<script setup lang="ts">
import MainLayout from '@/layouts/MainLayout.vue'
// Dashboard KPI data is fetched by DashboardView on mount; the shell only
// needs the nav tree (MainLayout) and the alarm summary (WebSocket seed).
</script>
