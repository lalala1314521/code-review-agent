<script setup lang="ts">
defineProps<{ modelValue: boolean; title: string; widthClass?: string }>()
const emit = defineEmits<{ 'update:modelValue': [value: boolean] }>()
</script>

<template>
  <teleport to="body">
    <transition name="modal">
      <div v-if="modelValue" class="fixed inset-0 z-[90] flex items-center justify-center bg-black/70 p-4 backdrop-blur-sm" @mousedown.self="emit('update:modelValue', false)">
        <section class="glass-strong max-h-[90vh] w-full overflow-y-auto rounded-3xl p-6 shadow-2xl" :class="widthClass ?? 'max-w-xl'" role="dialog" aria-modal="true" :aria-label="title">
          <header class="mb-5 flex items-center justify-between gap-4">
            <h2 class="text-xl font-bold text-white">{{ title }}</h2>
            <button type="button" class="icon-button" aria-label="关闭" @click="emit('update:modelValue', false)">×</button>
          </header>
          <slot />
        </section>
      </div>
    </transition>
  </teleport>
</template>
