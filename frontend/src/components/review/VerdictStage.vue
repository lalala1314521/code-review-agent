<script setup lang="ts">
import { computed } from 'vue'
import type { ReviewConclusion } from '../../types/review'
import { formatDuration } from '../../utils/format'

const props = defineProps<{ conclusion: ReviewConclusion; fileCount: number; findingCount: number; durationMs: number | null; confidence: number | null }>()
const config = computed(() => ({
  APPROVE: { text: '建议合并', className: 'neon-success', color: '#34D399', icon: '✓' },
  NEEDS_FIX: { text: '需修复', className: 'neon-warning', color: '#FBBF24', icon: '!' },
  BLOCK: { text: '阻塞', className: 'neon-error', color: '#FB7185', icon: '×' },
})[props.conclusion])
const confidenceLabel = computed(() => props.confidence == null ? '未知' : props.confidence >= 85 ? '高把握' : props.confidence >= 60 ? '中等把握' : '低把握')
</script>

<template>
  <div class="stage-aura flex flex-col items-center gap-2.5 rounded-[20px] px-6 py-5" :style="{ '--stage-color': config.color }">
    <div class="flex h-14 w-14 items-center justify-center rounded-full text-2xl font-bold" :style="{ color: config.color, background: `${config.color}22`, boxShadow: `inset 0 0 10px ${config.color}66` }">{{ config.icon }}</div>
    <h3 class="text-center text-[40px] font-bold leading-none" :class="config.className">{{ config.text }}</h3>
    <p class="text-center text-[13px] text-white/60">Agent 已审查 {{ fileCount }} 个文件 · 发现 {{ findingCount }} 处建议</p>
    <div class="flex flex-wrap justify-center gap-5 font-mono text-xs text-white/40">
      <span>耗时 {{ formatDuration(durationMs) }}</span>
      <span>置信度 {{ confidenceLabel }} {{ confidence == null ? '—' : Math.round(confidence) + '%' }}</span>
    </div>
  </div>
</template>

