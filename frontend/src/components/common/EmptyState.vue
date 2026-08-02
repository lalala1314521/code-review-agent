<script setup lang="ts">
withDefaults(defineProps<{ title?: string; description?: string }>(), {
  title: '暂无数据',
  description: '当前筛选条件下没有可展示的内容。',
})
</script>

<template>
  <div class="flex min-h-[180px] flex-col items-center justify-center gap-3 px-6 text-center">
    <div class="flex h-12 w-12 items-center justify-center rounded-2xl border border-white/10 bg-white/5 text-xl text-white/40">∅</div>
    <div>
      <p class="text-sm font-semibold text-white/80">{{ title }}</p>
      <p class="mt-1 text-xs text-white/40">{{ description }}</p>
    </div>
  </div>
</template>
