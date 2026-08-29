<template>
  <div class="login-page">
    <div class="login-card">
      <div class="brand">
        <span class="brand-name">DAYONE</span>
        <span class="brand-sub">N4 Monitor</span>
      </div>
      <div class="title">Industrial Monitoring Platform</div>
      <div class="subtitle">Sign in to access real-time system monitoring</div>
      <el-form :model="form" @keyup.enter="submit">
        <el-form-item>
          <el-input v-model="form.username" placeholder="Username" size="large" />
        </el-form-item>
        <el-form-item>
          <el-input v-model="form.password" placeholder="Password" type="password" show-password size="large" />
        </el-form-item>
        <el-button class="login-button" type="primary" size="large" :loading="loading" @click="submit">Sign In</el-button>
      </el-form>
    </div>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const authStore = useAuthStore()
const loading = ref(false)
const form = reactive({ username: 'admin', password: '' })

async function submit() {
  if (!form.username || !form.password) {
    ElMessage.warning('Please enter both username and password')
    return
  }
  loading.value = true
  try {
    await authStore.login(form.username, form.password)
    ElMessage.success('Login successful')
    router.push('/')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped lang="scss">
.login-page {
  width: 100%;
  height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: radial-gradient(circle at center, #132238 0%, $bg-dark 70%);
}

.login-card {
  width: 400px;
  padding: 40px;
  background: rgba($bg-card, 0.94);
  border: 1px solid $border-card;
  border-radius: 12px;
  box-shadow: 0 18px 60px rgba(0, 0, 0, 0.36);
}

.brand {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  margin-bottom: 8px;

  .brand-name {
    font-size: 20px;
    font-weight: 800;
    color: $accent-green;
    letter-spacing: 1px;
  }

  .brand-sub {
    font-size: 16px;
    color: $text-secondary;
  }
}

.title {
  color: $text-primary;
  font-size: 20px;
  font-weight: 700;
  text-align: center;
}

.subtitle {
  color: $text-secondary;
  font-size: 13px;
  text-align: center;
  margin: 8px 0 28px;
}

.login-button {
  width: 100%;
  margin-top: 8px;
}
</style>
