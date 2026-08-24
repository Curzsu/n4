import type { App, DirectiveBinding } from 'vue'
import { useAuthStore } from '@/stores/auth'

function mounted(el: HTMLElement, binding: DirectiveBinding<string | string[]>) {
  const authStore = useAuthStore()
  const value = binding.value
  const required = Array.isArray(value) ? value : [value]
  if (required.length && !required.some((code) => authStore.hasPermission(code))) {
    el.parentNode?.removeChild(el)
  }
}

export function setupPermissionDirective(app: App) {
  app.directive('permission', { mounted })
}
