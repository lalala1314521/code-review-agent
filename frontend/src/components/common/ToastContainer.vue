<script setup lang="ts">
import { useNotificationStore } from '../../stores/notification'

const store = useNotificationStore()
</script>

<template>
  <div class="pointer-events-none fixed right-5 top-20 z-[100] flex w-[min(360px,calc(100vw-40px))] flex-col gap-2">
    <transition-group name="toast">
      <button
        v-for="message in store.messages"
        :key="message.id"
        type="button"
        class="pointer-events-auto flex items-center gap-3 rounded-2xl border px-4 py-3 text-left text-sm shadow-2xl backdrop-blur-xl"
        :class="{
          'border-emerald-400/30 bg-emerald-400/10 text-emerald-100': message.tone === 'success',
          'border-rose-400/30 bg-rose-400/10 text-rose-100': message.tone === 'error',
          'border-sky-300/30 bg-slate-950/90 text-white': message.tone === 'info',
        }"
        @click="store.dismiss(message.id)"
      >
        <span class="h-2 w-2 shrink-0 rounded-full" :class="message.tone === 'success' ? 'bg-emerald-400' : message.tone === 'error' ? 'bg-rose-400' : 'bg-sky-300'"></span>
        <span>{{ message.text }}</span>
      </button>
    </transition-group>
  </div>
</template>
