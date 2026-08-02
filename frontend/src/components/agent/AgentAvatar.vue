<script setup lang="ts">
import type { AgentProfile } from '../../types/agent'

withDefaults(defineProps<{ agent: AgentProfile; size?: 'sm' | 'md' | 'lg' }>(), { size: 'md' })
</script>

<template>
  <div class="agent-avatar" :class="[`agent-${agent.accent}`, `agent-avatar-${size}`]">
    <svg width="55%" height="55%" viewBox="0 0 20 20" fill="none"><path d="M10 2L11.5 7.5L17 9L11.5 10.5L10 16L8.5 10.5L3 9L8.5 7.5L10 2Z" fill="currentColor"/><circle cx="15.5" cy="4.5" r="1.5" fill="currentColor" opacity=".7"/></svg>
  </div>
</template>
