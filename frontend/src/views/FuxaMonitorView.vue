<template>
  <section class="monitor-view">
    <header class="monitor-header">
      <div class="monitor-title-group">
        <el-button :icon="ArrowLeft" class="back-btn" @click="returnToDevice">
          返回设备详情
        </el-button>
        <div class="monitor-heading">
          <span class="monitor-kicker">图形监控</span>
          <h1>{{ deviceName }}</h1>
        </div>
      </div>

      <el-button :icon="FullScreen" class="fullscreen-btn" @click="openFullscreen">
        全屏
      </el-button>
    </header>

    <div ref="frameShell" class="frame-shell" v-loading="loading">
      <iframe
        :src="fuxaUrl"
        title="FUXA 图形监控"
        allow="fullscreen"
        @load="loading = false"
      />
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft, FullScreen } from '@element-plus/icons-vue'

const route = useRoute()
const router = useRouter()
const loading = ref(true)
const frameShell = ref<HTMLElement | null>(null)

const deviceName = computed(() => {
  const queryName = route.query.deviceName
  return typeof queryName === 'string' && queryName ? queryName : String(route.params.deviceId)
})

const fuxaUrl = computed(() => {
  return import.meta.env.VITE_FUXA_URL || 'http://127.0.0.1:1881/home/H1_EMSB_A4_03_EM'
})

function returnToDevice() {
  router.push({ name: 'dashboard' })
}

async function openFullscreen() {
  if (!frameShell.value) return
  await frameShell.value.requestFullscreen()
}
</script>

<style scoped lang="scss">
.monitor-view {
  height: 100%;
  min-height: 0;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.monitor-header {
  min-height: 58px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 8px 12px;
  background-color: $bg-card;
  border: 1px solid $border-card;
  border-radius: 6px;
}

.monitor-title-group {
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 14px;
}

.monitor-heading {
  min-width: 0;

  h1 {
    margin: 2px 0 0;
    overflow: hidden;
    color: $text-primary;
    font-size: 16px;
    font-weight: 600;
    letter-spacing: 0;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}

.monitor-kicker {
  display: block;
  color: $accent-green;
  font-size: 11px;
  line-height: 1;
}

.back-btn,
.fullscreen-btn {
  flex-shrink: 0;
  border-color: $border-card;
  background-color: transparent;
  color: $text-secondary;
}

.back-btn:hover,
.fullscreen-btn:hover {
  border-color: $accent-green;
  background-color: rgba($accent-green, 0.08);
  color: $accent-green;
}

.frame-shell {
  min-height: 0;
  flex: 1;
  overflow: hidden;
  background: #050505;
  border: 1px solid $border-card;
  border-radius: 6px;
}

.frame-shell iframe {
  width: 100%;
  height: 100%;
  display: block;
  border: 0;
  background: #050505;
}

.frame-shell:fullscreen {
  width: 100vw;
  height: 100vh;
  border: 0;
  border-radius: 0;
}

@media (max-width: 720px) {
  .monitor-header {
    align-items: stretch;
  }

  .monitor-title-group {
    gap: 8px;
  }

  .monitor-heading h1 {
    font-size: 14px;
  }
}
</style>
