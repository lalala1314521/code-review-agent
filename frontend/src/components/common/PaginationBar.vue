<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{ page: number; size: number; total: number }>()
const emit = defineEmits<{ change: [page: number] }>()
const pageCount = computed(() => Math.max(1, Math.ceil(props.total / props.size)))
</script>

<template>
  <div class="flex flex-wrap items-center justify-between gap-3 border-t border-white/10 pt-4">
    <span class="text-xs text-white/45">共 {{ total }} 条 · 第 {{ page }} / {{ pageCount }} 页</span>
    <div class="flex items-center gap-2">
      <button type="button" class="secondary-button px-3 py-1.5 text-xs" :disabled="page <= 1" @click="emit('change', page - 1)">上一页</button>
      <button type="button" class="secondary-button px-3 py-1.5 text-xs" :disabled="page >= pageCount" @click="emit('change', page + 1)">下一页</button>
    </div>
  </div>
</template>
