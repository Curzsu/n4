import type { Ref } from 'vue'
import type { FormInstance } from 'element-plus'
import { ElMessage } from 'element-plus'

/**
 * Triggers a visual shake and error pulse animation on all invalid form items
 * and displays a toast message so the user perceives immediate feedback on every submit attempt.
 */
export function triggerFormShake(
  formRef: Ref<FormInstance | undefined> | any,
  errorMessage: string = 'Please correct the highlighted form errors'
) {
  const el = formRef?.value?.$el || formRef?.value || (formRef instanceof HTMLElement ? formRef : null)
  if (el) {
    el.classList.remove('shake-errors')
    // Force DOM reflow to restart CSS keyframe animations
    void el.offsetWidth
    el.classList.add('shake-errors')
    setTimeout(() => {
      el.classList.remove('shake-errors')
    }, 500)
  }

  // Always show or re-trigger the warning message toast
  ElMessage.closeAll()
  ElMessage.warning({
    message: errorMessage,
    duration: 2500,
    grouping: false,
  })
}
