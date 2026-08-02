<script setup lang="ts">
withDefaults(defineProps<{ message?: string }>(), { message: '数据加载失败' })
const emit = defineEmits<{ retry: [] }>()
</script>

<template>
  <div class="flex min-h-[180px] flex-col items-center justify-center gap-3 px-6 text-center">
    <div class="flex h-12 w-12 items-center justify-center rounded-2xl border border-rose-400/20 bg-rose-400/10 text-rose-300">!</div>
    <p class="max-w-md text-sm text-rose-100/80">{{ message }}</p>
    <button type="button" class="secondary-button px-4 py-2 text-xs" @click="emit('retry')">重新加载</button>
  </div>
</template>
