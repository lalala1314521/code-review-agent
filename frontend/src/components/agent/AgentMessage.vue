<script setup lang="ts">
import type { AgentChatMessage } from '../../types/chat'

defineProps<{ message: AgentChatMessage }>()
</script>

<template>
  <div class="flex" :class="message.role === 'user' ? 'justify-end' : 'justify-start'">
    <div class="chat-message" :class="message.role === 'user' ? 'chat-message-user' : 'chat-message-agent'">
      <p class="whitespace-pre-wrap break-words text-[13px] leading-6">{{ message.content }}</p>
      <div class="mt-1.5 flex items-center justify-between gap-3 text-[9px] opacity-45"><span>{{ message.role === 'user' ? '你' : (message.agentName || 'Agent') }}</span><span v-if="message.model" class="font-mono">{{ message.model }}</span><span v-if="message.failed" class="text-rose-300">发送失败</span></div>
    </div>
  </div>
</template>
