<script setup lang="ts">
import BaseModal from './BaseModal.vue'

withDefaults(defineProps<{ modelValue: boolean; title: string; message: string; confirmText?: string; danger?: boolean }>(), {
  confirmText: '确认',
  danger: false,
})
const emit = defineEmits<{ 'update:modelValue': [value: boolean]; confirm: [] }>()
</script>

<template>
  <BaseModal :model-value="modelValue" :title="title" width-class="max-w-md" @update:model-value="emit('update:modelValue', $event)">
    <p class="text-sm leading-6 text-white/60">{{ message }}</p>
    <div class="mt-6 flex justify-end gap-3">
      <button type="button" class="secondary-button px-4 py-2 text-sm" @click="emit('update:modelValue', false)">取消</button>
      <button type="button" class="px-4 py-2 text-sm" :class="danger ? 'danger-button' : 'primary-button'" @click="emit('confirm')">{{ confirmText }}</button>
    </div>
  </BaseModal>
</template>
