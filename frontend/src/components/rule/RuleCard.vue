<script setup lang="ts">
import type { ReviewRule } from '../../types/rule'
import StatusBadge from '../common/StatusBadge.vue'

defineProps<{ rule: ReviewRule; busy?: boolean }>()
const emit = defineEmits<{ edit: [rule: ReviewRule]; toggle: [rule: ReviewRule]; remove: [rule: ReviewRule] }>()
</script>

<template>
  <article class="glass group flex flex-col rounded-2xl p-5 transition hover:-translate-y-0.5 hover:border-white/20">
    <header class="flex items-start justify-between gap-4">
      <div class="min-w-0"><div class="flex flex-wrap items-center gap-2"><h3 class="truncate text-sm font-bold text-white">{{ rule.name }}</h3><StatusBadge :severity="rule.severity" /><span class="rounded-full border border-white/10 px-2 py-0.5 text-[10px] text-white/35">{{ rule.ruleType }}</span></div><p class="mt-1 font-mono text-[11px] text-sky-200/60">{{ rule.ruleId }}</p></div>
      <button type="button" class="relative h-6 w-11 shrink-0 rounded-full transition" :class="rule.enabled ? 'bg-emerald-400/70' : 'bg-white/10'" :disabled="busy" :aria-label="rule.enabled ? '禁用规则' : '启用规则'" @click="emit('toggle', rule)"><span class="absolute top-1 h-4 w-4 rounded-full bg-white shadow transition-all" :class="rule.enabled ? 'left-6' : 'left-1'"></span></button>
    </header>
    <p class="mt-4 min-h-[40px] text-xs leading-5 text-white/50">{{ rule.description || '暂无规则说明。' }}</p>
    <dl class="mt-4 grid grid-cols-2 gap-3 rounded-xl bg-black/15 p-3 text-[11px]"><div><dt class="text-white/30">适用语言</dt><dd class="mt-1 text-white/65">{{ rule.language || '全部语言' }}</dd></div><div><dt class="text-white/30">状态</dt><dd class="mt-1" :class="rule.enabled ? 'text-emerald-300' : 'text-white/40'">{{ rule.enabled ? '已启用' : '已停用' }}</dd></div></dl>
    <div v-if="rule.paramsJson" class="mt-3 rounded-xl border border-white/[0.06] bg-black/20 px-3 py-2 font-mono text-[10px] leading-5 text-white/40 break-all">{{ rule.paramsJson }}</div>
    <footer class="mt-auto flex justify-end gap-2 pt-4"><button type="button" class="secondary-button px-3 py-1.5 text-xs" @click="emit('edit', rule)">编辑</button><button v-if="rule.ruleType === 'CUSTOM'" type="button" class="danger-button px-3 py-1.5 text-xs" @click="emit('remove', rule)">删除</button></footer>
  </article>
</template>
