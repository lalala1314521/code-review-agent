<script setup lang="ts">
withDefaults(defineProps<{ label?: string; compact?: boolean }>(), { label: '正在加载数据…', compact: false })
</script>

<template>
  <div class="flex items-center justify-center gap-3" :class="compact ? 'py-6' : 'min-h-[180px]'">
    <span class="h-5 w-5 animate-spin rounded-full border-2 border-white/20 border-t-sky-200"></span>
    <span class="text-sm text-white/60">{{ label }}</span>
  </div>
</template>
