import { defineStore } from 'pinia'
import { ref } from 'vue'

export type ToastTone = 'success' | 'error' | 'info'
export interface ToastMessage { id: number; text: string; tone: ToastTone }

export const useNotificationStore = defineStore('notification', () => {
  const messages = ref<ToastMessage[]>([])
  let idSeed = 0

  function show(text: string, tone: ToastTone = 'info') {
    const id = ++idSeed
    messages.value.push({ id, text, tone })
    window.setTimeout(() => dismiss(id), 3200)
  }

  function dismiss(id: number) {
    messages.value = messages.value.filter((item) => item.id !== id)
  }

  return { messages, show, dismiss }
})
