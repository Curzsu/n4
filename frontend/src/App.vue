<template>
  <component :is="isStandalone ? 'div' : MainLayout" :class="{ 'standalone-app': isStandalone }">
    <RouterView />
  </component>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import MainLayout from '@/layouts/MainLayout.vue'

const route = useRoute()
const isStandalone = computed(() => route.meta.layout === 'blank' || route.path === '/login' || route.path === '/403')
</script>

<style scoped>
.standalone-app {
  width: 100vw;
  height: 100vh;
}
</style>
