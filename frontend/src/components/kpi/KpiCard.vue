<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{ title: string; value: string; delta: string; deltaUp?: boolean; danger?: boolean; icon: 'review' | 'duration' | 'passrate' | 'block' }>()
const cardClass = computed(() => props.danger ? 'kpi-danger' : 'glass')
const iconColor = computed(() => props.icon === 'review' ? '#B1E2FF' : props.icon === 'duration' ? '#34D399' : props.icon === 'passrate' ? '#9381FF' : '#FB7185')
const deltaColor = computed(() => props.danger ? 'var(--warning)' : props.deltaUp ? 'var(--success)' : 'var(--error)')
</script>

<template>
  <div :class="[cardClass, 'flex min-h-[120px] flex-col justify-between rounded-[20px] p-4 sm:min-h-[140px] sm:p-5']">
    <div class="flex items-center justify-between"><span class="text-xs font-medium text-white/60">{{ title }}</span><div class="flex h-7 w-7 items-center justify-center rounded-lg" :style="{ background: `${iconColor}22`, color: iconColor }"><span v-if="icon === 'review'">ϟ</span><span v-else-if="icon === 'duration'">◷</span><span v-else-if="icon === 'passrate'">✓</span><span v-else>!</span></div></div>
    <div><span class="font-mono text-[30px] font-semibold leading-none sm:text-[40px]" :class="danger ? 'text-rose-300' : 'text-white'">{{ value }}</span><p class="mt-2 text-[11px] font-medium sm:text-xs" :style="{ color: deltaColor }">{{ delta }}</p></div>
  </div>
</template>
