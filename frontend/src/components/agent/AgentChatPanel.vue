<script setup lang="ts">
import { nextTick, ref, watch } from 'vue'
import { useAgentChatStore } from '../../stores/agentChat'
import AgentMessage from './AgentMessage.vue'

const props = defineProps<{ open: boolean; reviewId: number | null; reviewTitle?: string }>()
const emit = defineEmits<{ close: [] }>()
const store = useAgentChatStore()
const scroller = ref<HTMLElement | null>(null)
watch(() => props.reviewId, (id) => store.setReview(id), { immediate: true })
watch(() => [store.currentMessages.length, store.sending, props.open], async () => { await nextTick(); if (scroller.value) scroller.value.scrollTop = scroller.value.scrollHeight })
</script>

<template>
  <transition name="chat-panel">
    <section v-if="open" class="agent-chat-panel liquid-glass-strong">
      <header class="flex items-center justify-between gap-4 border-b border-white/10 px-5 py-4"><div class="min-w-0"><div class="flex items-center gap-2"><span class="dot-glow-purple h-2 w-2 rounded-full"></span><h3 class="text-sm font-bold text-white">审查上下文对话</h3></div><p class="mt-1 truncate text-[10px] text-white/35">{{ reviewTitle || '请选择审查记录' }}</p></div><div class="flex items-center gap-2"><button v-if="reviewId && store.currentMessages.length" type="button" class="text-[10px] text-white/35 hover:text-white" @click="store.clear(reviewId)">清空</button><button type="button" class="icon-button !h-8 !w-8" @click="emit('close')">×</button></div></header>
      <div ref="scroller" class="min-h-0 flex-1 space-y-3 overflow-y-auto px-5 py-4 no-scrollbar"><div v-if="store.currentMessages.length === 0" class="flex h-full min-h-[220px] flex-col items-center justify-center text-center"><div class="mb-3 text-3xl opacity-60">✦</div><p class="text-sm font-semibold text-white/70">向 Agent 追问本次审查</p><p class="mt-2 max-w-[330px] text-xs leading-5 text-white/35">可以询问风险原因、修复方案、测试建议，或输入 / 使用快捷命令。</p></div><AgentMessage v-for="message in store.currentMessages" :key="message.id" :message="message" /><div v-if="store.sending" class="flex justify-start"><div class="chat-message chat-message-agent flex items-center gap-1.5 py-3"><span class="typing-dot"></span><span class="typing-dot"></span><span class="typing-dot"></span></div></div></div>
    </section>
  </transition>
</template>
