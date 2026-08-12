<template>
  <div class="building-placeholder">
    <img
      src="@/assets/dashboard.png"
      alt="Dashboard Overview"
      class="building-image"
      @click="goToMvpA1"
    />
    <div class="building-overlay-info">
      <span class="overlay-temp">{{ temperature }}°C</span>
      <span class="overlay-time">{{ currentTime }}</span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import { useDashboardStore } from '@/stores/dashboard'

const store = useDashboardStore()
const temperature = ref(31)
const currentTime = ref('2024-05-30 10:30:25')

let timer: number | null = null
onMounted(() => {
  timer = window.setInterval(() => {
    const now = new Date()
    currentTime.value = now.toLocaleString('en-CA', {
      year: 'numeric', month: '2-digit', day: '2-digit',
      hour: '2-digit', minute: '2-digit', second: '2-digit',
    })
  }, 1000)
})

onUnmounted(() => {
  if (timer) clearInterval(timer)
})

// Click the dashboard image to jump to the "A1_MVP_A1" nav node, the same way
// clicking it in the left sidebar would. A1_MVP_A1 is a data-driven nav node
// (no dedicated route), so we find it in the nav tree and call selectNavNode.
function findNodeByName(nodes: any[], name: string): any | null {
  for (const n of nodes) {
    if (n.nodeName === name) return n
    if (n.children?.length) {
      const found = findNodeByName(n.children, name)
      if (found) return found
    }
  }
  return null
}

function goToMvpA1() {
  const node = findNodeByName(store.navTree, 'A1_MVP_A1')
  if (node) {
    store.selectNavNode(node)
  }
}
</script>

<style scoped lang="scss">
.building-placeholder {
  background-color: $bg-card;
  border: 1px solid $border-card;
  border-radius: 8px;
  margin-bottom: 16px;
  padding: 16px;
  position: relative;
}

.building-image {
  width: 100%;
  height: 300px;
  object-fit: cover;
  border-radius: 4px;
  cursor: pointer;
  transition: opacity 0.2s;
  &:hover {
    opacity: 0.85;
  }
}

.building-overlay-info {
  position: absolute;
  top: 12px;
  right: 12px;
  display: flex;
  gap: 12px;
  font-size: 13px;
  color: $text-secondary;
}

.overlay-temp {
  color: $accent-orange;
  font-weight: 600;
}
</style>
