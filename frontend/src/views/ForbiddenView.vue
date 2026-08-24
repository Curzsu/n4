<template>
  <div class="forbidden">
    <div class="code">403</div>
    <div class="text">You don't have permission to access this page</div>
    <el-button type="primary" @click="$router.push('/')">Back to Home</el-button>
  </div>
</template>

<style scoped lang="scss">
.forbidden {
  height: 100vh;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 16px;
  color: $text-primary;
  background-color: $bg-dark;
}
.code {
  font-size: 72px;
  color: $accent-red;
  font-weight: 700;
}
.text {
  color: $text-secondary;
  font-size: 16px;
}
</style>
