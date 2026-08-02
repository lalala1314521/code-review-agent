<script setup lang="ts">
import { computed } from 'vue'
import type { HistoryStat } from '../../types/history'
import { formatDuration } from '../../utils/format'

const props = defineProps<{ stats: HistoryStat[] }>()
const width = 900
const height = 220
const padding = 34
const maxValue = computed(() => Math.max(1, ...props.stats.map((item) => Number(item.totalCount))))
const points = computed(() => props.stats.map((item, index) => {
  const x = props.stats.length <= 1 ? width / 2 : padding + index * ((width - padding * 2) / (props.stats.length - 1))
  const y = height - padding - (Number(item.totalCount) / maxValue.value) * (height - padding * 2)
  return { x, y, item }
}))
const polyline = computed(() => points.value.map((point) => `${point.x},${point.y}`).join(' '))
const avgDuration = computed(() => props.stats.length ? props.stats.reduce((sum, item) => sum + Number(item.avgDurationMs), 0) / props.stats.length : 0)
</script>

<template>
  <div class="h-full">
    <div class="mb-4 flex flex-wrap items-center justify-between gap-3"><div><h2 class="text-base font-bold text-white">审查趋势</h2><p class="mt-1 text-xs text-white/35">按天聚合的已完成审查数量</p></div><span class="rounded-full bg-white/5 px-3 py-1 font-mono text-xs text-white/45">区间平均耗时 {{ formatDuration(avgDuration) }}</span></div>
    <div v-if="stats.length === 0" class="flex h-[220px] items-center justify-center text-xs text-white/35">当前时间范围暂无统计数据。</div>
    <div v-else class="overflow-x-auto no-scrollbar"><svg class="h-[220px] min-w-[680px] w-full" :viewBox="`0 0 ${width} ${height}`" role="img" aria-label="审查趋势折线图">
      <defs><linearGradient id="trendFill" x1="0" y1="0" x2="0" y2="1"><stop offset="0%" stop-color="#9381ff" stop-opacity="0.35"/><stop offset="100%" stop-color="#9381ff" stop-opacity="0"/></linearGradient></defs>
      <line v-for="index in 4" :key="index" :x1="padding" :x2="width-padding" :y1="padding + (index-1)*(height-padding*2)/3" :y2="padding + (index-1)*(height-padding*2)/3" stroke="rgba(255,255,255,.08)" />
      <polygon :points="`${padding},${height-padding} ${polyline} ${width-padding},${height-padding}`" fill="url(#trendFill)" />
      <polyline :points="polyline" fill="none" stroke="#B1E2FF" stroke-width="3" stroke-linecap="round" stroke-linejoin="round" />
      <g v-for="(point, index) in points" :key="point.item.statDate"><circle :cx="point.x" :cy="point.y" r="5" fill="#9381ff" stroke="#fff" stroke-opacity=".65" stroke-width="2"><title>{{ point.item.statDate }}：{{ point.item.totalCount }} 次</title></circle><text v-if="index === 0 || index === points.length-1 || index % Math.max(1, Math.floor(points.length/6)) === 0" :x="point.x" :y="height-10" fill="rgba(255,255,255,.35)" font-size="10" text-anchor="middle">{{ point.item.statDate.slice(5) }}</text></g>
    </svg></div>
  </div>
</template>
