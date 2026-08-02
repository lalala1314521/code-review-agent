<script setup lang="ts">
import { computed, ref } from 'vue'
import type { ReviewFinding } from '../../types/review'
import StatusBadge from '../common/StatusBadge.vue'

const props = defineProps<{ finding: ReviewFinding }>()
const expanded = ref(false)
const copied = ref(false)
const location = computed(() => `${props.finding.filePath}${props.finding.lineNumber != null ? `:${props.finding.lineNumber}` : ''}`)

async function copyLocation() {
  try {
    await navigator.clipboard.writeText(location.value)
    copied.value = true
    window.setTimeout(() => { copied.value = false }, 1200)
  } catch {
    copied.value = false
  }
}
</script>

<template>
  <article class="rounded-2xl border p-3.5" :class="finding.severity === 'ERROR' ? 'border-rose-400/20 bg-rose-400/[0.055]' : finding.severity === 'WARNING' ? 'border-amber-300/20 bg-amber-300/[0.05]' : 'border-sky-200/15 bg-sky-200/[0.045]'">
    <button type="button" class="flex w-full items-start gap-3 text-left" @click="expanded = !expanded">
      <StatusBadge :severity="finding.severity" />
      <div class="min-w-0 flex-1">
        <p class="text-[13px] font-medium leading-5 text-white">{{ finding.message }}</p>
        <p class="mt-1 truncate font-mono text-[10px] text-white/35">{{ location }}</p>
      </div>
      <span class="mt-0.5 text-xs text-white/30 transition" :class="expanded ? 'rotate-180' : ''">⌄</span>
    </button>
    <div v-if="expanded" class="mt-3 border-t border-white/[0.08] pt-3">
      <div class="flex flex-wrap items-center gap-2 text-[11px] text-white/40">
        <span>规则：{{ finding.ruleId }}</span><span>·</span><span>来源：{{ finding.source }}</span><span>·</span><span>置信度：{{ Math.round(finding.confidence) }}%</span>
      </div>
      <p v-if="finding.suggestion" class="mt-2 rounded-xl bg-black/20 px-3 py-2 text-xs leading-5 text-white/60">建议：{{ finding.suggestion }}</p>
      <button type="button" class="mt-2 text-[11px] text-sky-200/70 hover:text-sky-100" @click.stop="copyLocation">{{ copied ? '已复制' : '复制文件位置' }}</button>
    </div>
  </article>
</template>
