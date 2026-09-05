<template>
  <el-dialog v-model="visible" title="Change Password" width="420px" class="dark-dialog" :close-on-click-modal="false">
    <el-form :model="form" size="small" label-width="130px" label-position="right">
      <el-form-item label="Old Password" required>
        <el-input v-model="form.oldPassword" type="password" show-password placeholder="Current password" />
      </el-form-item>
      <el-form-item label="New Password" required>
        <el-input v-model="form.newPassword" type="password" show-password placeholder="Minimum 6 characters" />
      </el-form-item>
      <el-form-item label="Confirm Password" required>
        <el-input v-model="confirmPassword" type="password" show-password placeholder="Repeat new password" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button size="small" class="btn-secondary" @click="visible = false">Cancel</el-button>
      <el-button size="small" class="btn-primary" :loading="loading" @click="submit">Save</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { authApi } from '@/api/auth'

const props = defineProps<{ modelValue: boolean }>()
const emit = defineEmits<{ 'update:modelValue': [value: boolean] }>()

const visible = computed({
  get: () => props.modelValue,
  set: (value) => emit('update:modelValue', value),
})
const loading = ref(false)
const confirmPassword = ref('')
const form = reactive({ oldPassword: '', newPassword: '' })

async function submit() {
  if (!form.oldPassword) {
    ElMessage.warning('Old password is required')
    return
  }
  if (!form.newPassword || form.newPassword.length < 6) {
    ElMessage.warning('New password must be at least 6 characters long')
    return
  }
  if (form.newPassword !== confirmPassword.value) {
    ElMessage.warning('The two new passwords do not match')
    return
  }
  loading.value = true
  try {
    await authApi.changePassword(form)
    ElMessage.success('Password changed successfully')
    visible.value = false
    form.oldPassword = ''
    form.newPassword = ''
    confirmPassword.value = ''
  } catch (e) {
    console.error('Failed to change password:', e)
    ElMessage.error('Failed to change password')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped lang="scss">
:deep(.el-dialog) {
  background-color: $bg-card;
  border: 1px solid $border-card;
  .el-dialog__title { color: $text-primary; font-size: 13px; font-weight: 600; }
  .el-dialog__body { color: $text-primary; padding: 16px 20px; font-size: 11px; }
}

:deep(.el-form-item) {
  margin-bottom: 14px;
}

:deep(.el-form-item__label) {
  color: $text-secondary;
  font-size: 11px;
}

:deep(.el-input__wrapper) {
  background-color: $bg-dark;
  box-shadow: 0 0 0 1px $border-card inset;
  color: $text-primary;
  &:hover { box-shadow: 0 0 0 1px $text-muted inset; }
}

:deep(.el-input__inner) {
  color: $text-primary;
  font-size: 11px;
  &::placeholder { color: $text-muted; }
}

/* ========================================================== */
/* BORDERLESS TRANSLUCENT CAPSULE BUTTONS                     */
/* ========================================================== */
:deep(.el-button) {
  border-radius: 9px !important;
  border: none !important;
  font-size: 10px !important;
  font-weight: 400 !important;
  padding: 2px 8px !important;
  height: 20px !important;
  line-height: 16px !important;
  transition: all 0.2s ease-in-out;

  &:hover {
    transform: translateY(-0.5px);
  }
}

.btn-primary, :deep(.el-button--primary:not(.is-link)) {
  background-color: rgba($accent-green, 0.16) !important;
  color: #34d399 !important;

  &:hover {
    background-color: rgba($accent-green, 0.28) !important;
    color: #6ee7b7 !important;
  }
}

.btn-secondary, :deep(.el-button--default:not(.is-link)), :deep(.el-button--info:not(.is-link)) {
  background-color: rgba($accent-blue, 0.16) !important;
  color: #93c5fd !important;

  &:hover {
    background-color: rgba($accent-blue, 0.28) !important;
    color: #bfdbfe !important;
  }
}
</style>
