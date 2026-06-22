<template>
  <div class="building-placeholder">
    <!-- <img src="@/assets/building-placeholder.svg" alt="Campus Digital Twin" class="building-image" /> -->
    <div class="building-image"></div>
    <div class="building-overlay-info">
      <span class="overlay-temp">{{ temperature }}°C</span>
      <span class="overlay-time">{{ currentTime }}</span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'

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
  max-height: 300px;
  object-fit: contain;
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
