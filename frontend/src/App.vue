<template>
  <RouterView v-if="isBareRoute" />
  <template v-else>
    <MainLayout>
      <RouterView />
    </MainLayout>
    <!-- Global alarm toast stack: mounted here so alarm popups appear on every page -->
    <AlarmToastStack />
  </template>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import MainLayout from '@/layouts/MainLayout.vue'
import AlarmToastStack from '@/components/AlarmToastStack.vue'
// Dashboard KPI data is fetched by DashboardView on mount; the shell only
// needs the nav tree (MainLayout) and the alarm summary (WebSocket seed).

const route = useRoute()
const isBareRoute = computed(() => route.meta.bare === true)
</script>
