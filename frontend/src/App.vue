<template>
  <div v-if="isStandalone" class="standalone-app">
    <RouterView />
  </div>
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

const route = useRoute()
const isStandalone = computed(() =>
  route.meta.bare === true ||
  route.meta.layout === 'blank' ||
  route.path === '/login' ||
  route.path === '/403'
)
</script>

<style scoped>
.standalone-app {
  width: 100vw;
  height: 100vh;
}
</style>
