<script setup lang="ts">
import type { ProgressEvent } from '../../types/review'

defineProps<{ events: ProgressEvent[]; status: 'idle' | 'connecting' | 'open' | 'closed' | 'error'; active: boolean }>()
</script>

<template>
  <div class="space-y-2">
    <div v-for="(event, index) in events" :key="`${event.stage}-${event.at}-${index}`" class="flex items-start gap-2.5 rounded-xl bg-white/[0.025] px-3 py-2">
      <span class="mt-1 h-2 w-2 shrink-0 rounded-full" :class="event.stage === 'FAILED' ? 'dot-glow-error' : index === events.length - 1 && active ? 'dot-glow-purple pulse' : 'bg-emerald-400/70'"></span>
      <div class="min-w-0">
        <p class="text-xs font-medium leading-5 text-white/80">{{ event.message }}</p>
        <p class="font-mono text-[10px] uppercase text-white/25">{{ event.stage }}</p>
      </div>
    </div>
    <div v-if="events.length === 0" class="flex items-center gap-2.5 rounded-xl bg-white/[0.025] px-3 py-3 text-xs text-white/40">
      <span class="h-2 w-2 rounded-full" :class="active ? 'dot-glow-purple pulse' : 'bg-white/20'"></span>
      <span v-if="status === 'error'">实时连接已断开，可刷新详情查看最终结果。</span>
      <span v-else-if="active">等待审查进度推送…</span>
      <span v-else>该记录当前没有实时进度。</span>
    </div>
  </div>
</template>
