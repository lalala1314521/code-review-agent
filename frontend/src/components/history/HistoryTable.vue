<script setup lang="ts">
import type { HistoryRecord } from '../../types/history'
import { formatDateTime, formatDuration } from '../../utils/format'
import StatusBadge from '../common/StatusBadge.vue'

defineProps<{ records: HistoryRecord[] }>()
const emit = defineEmits<{ open: [id: number] }>()
</script>

<template>
  <div class="overflow-x-auto no-scrollbar">
    <table class="w-full min-w-[820px] border-separate border-spacing-y-2 text-left">
      <thead><tr class="text-[11px] uppercase tracking-wider text-white/30"><th class="px-3 pb-1 font-medium">MR / 标题</th><th class="px-3 pb-1 font-medium">仓库与分支</th><th class="px-3 pb-1 font-medium">结论</th><th class="px-3 pb-1 font-medium">问题</th><th class="px-3 pb-1 font-medium">耗时</th><th class="px-3 pb-1 font-medium">触发时间</th></tr></thead>
      <tbody><tr v-for="record in records" :key="record.id" class="cursor-pointer bg-white/[0.035] text-xs text-white/55 transition hover:bg-white/[0.07]" @click="emit('open', record.id)"><td class="rounded-l-xl px-3 py-3"><p class="max-w-[280px] truncate font-semibold text-white">#{{ record.mrIid }} · {{ record.title || '(无标题)' }}</p><p class="mt-1 font-mono text-[10px] text-white/30">@{{ record.authorUsername || 'unknown' }}</p></td><td class="px-3 py-3"><p class="max-w-[220px] truncate">{{ record.repoPath }}</p><p class="mt-1 max-w-[220px] truncate font-mono text-[10px] text-white/30">{{ record.sourceBranch || '—' }}</p></td><td class="px-3 py-3"><StatusBadge :status="record.status" :conclusion="record.conclusion" /></td><td class="px-3 py-3 font-mono"><span class="text-rose-300">{{ record.errorCount }}</span> / <span class="text-amber-200">{{ record.warningCount }}</span> / <span class="text-sky-200">{{ record.infoCount }}</span></td><td class="px-3 py-3 font-mono">{{ formatDuration(record.durationMs) }}</td><td class="rounded-r-xl px-3 py-3">{{ formatDateTime(record.triggeredAt) }}</td></tr></tbody>
    </table>
  </div>
</template>
