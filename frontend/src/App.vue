<template>
  <MainLayout />
</template>

<script setup lang="ts">
import MainLayout from '@/layouts/MainLayout.vue'
import { useDashboardStore } from '@/stores/dashboard'

const store = useDashboardStore()
store.fetchAllDashboardData()
</script>
