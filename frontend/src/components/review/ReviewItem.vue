<script setup lang="ts">
import type { ReviewRecord } from '../../types/review'
import { formatRelativeTime } from '../../utils/format'
import StatusBadge from '../common/StatusBadge.vue'

defineProps<{ record: ReviewRecord; selected?: boolean }>()
const emit = defineEmits<{ select: [record: ReviewRecord] }>()
</script>

<template>
  <button
    type="button"
    class="review-item w-full rounded-2xl border p-4 text-left transition"
    :class="selected ? 'mr-selected' : record.conclusion === 'BLOCK' ? 'border-rose-400/20 bg-rose-400/[0.06]' : 'border-white/[0.08] bg-white/[0.035]'"
    @click="emit('select', record)"
  >
    <div class="flex items-start justify-between gap-3">
      <div class="min-w-0 flex-1">
        <p class="truncate text-sm font-semibold text-white">{{ record.title || '(无标题)' }}</p>
        <p class="mt-1.5 truncate font-mono text-[11px] text-white/40">
          #{{ record.mrIid }} · @{{ record.authorUsername || 'unknown' }} · {{ record.sourceBranch || '未知分支' }}
        </p>
      </div>
      <StatusBadge :status="record.status" :conclusion="record.conclusion" />
    </div>
    <div class="mt-3 flex items-center justify-between gap-3 text-[11px] text-white/35">
      <span class="truncate">{{ record.repoPath || record.platform }}</span>
      <span class="shrink-0">{{ formatRelativeTime(record.triggeredAt) }}</span>
    </div>
  </button>
</template>
