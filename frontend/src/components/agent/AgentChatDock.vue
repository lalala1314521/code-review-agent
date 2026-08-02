<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { useAgentChatStore } from '../../stores/agentChat'
import { useAgentStore } from '../../stores/agent'
import { useNotificationStore } from '../../stores/notification'
import { getErrorMessage } from '../../types/api'
import AgentChatPanel from './AgentChatPanel.vue'
import AgentSwitcher from './AgentSwitcher.vue'

const props = defineProps<{ reviewId: number | null; reviewTitle?: string }>()
const agentStore = useAgentStore()
const chatStore = useAgentChatStore()
const notifications = useNotificationStore()
const input = ref('')
const textarea = ref<HTMLTextAreaElement | null>(null)
const panelOpen = ref(false)
const commandsOpen = ref(false)
const commands = [
  { command: '/summary', label: '总结本次审查', prompt: '请用简洁的要点总结本次审查结论、主要问题和下一步行动。' },
  { command: '/risk', label: '解释最高风险', prompt: '请找出本次审查中风险最高的问题，解释原因、影响范围和优先级。' },
  { command: '/fix', label: '给出修复步骤', prompt: '请按优先级给出本次审查问题的具体修复步骤，必要时提供代码示例。' },
  { command: '/test', label: '建议补充测试', prompt: '根据本次审查结果，建议需要补充的单元测试、集成测试和边界用例。' },
  { command: '/files', label: '按文件汇总', prompt: '请按照文件路径汇总本次审查发现，并标注每个文件的主要风险。' },
]
const disabled = computed(() => props.reviewId == null)

async function send(text = input.value) {
  if (!props.reviewId || !text.trim()) return
  panelOpen.value = true
  commandsOpen.value = false
  input.value = ''
  try { await chatStore.send(props.reviewId, text, agentStore.selectedAgent) }
  catch (cause) { notifications.show(getErrorMessage(cause), 'error') }
  await nextTick(); textarea.value?.focus()
}
function chooseCommand(prompt: string) { input.value = prompt; commandsOpen.value = false; void send(prompt) }
function onInput() { commandsOpen.value = input.value.trim() === '/' }
function onKeydown(event: KeyboardEvent) {
  if (event.key === 'Enter' && !event.shiftKey) { event.preventDefault(); void send() }
  if (event.key === 'Escape') { commandsOpen.value = false; if (!input.value) panelOpen.value = false }
}
function globalShortcut(event: KeyboardEvent) {
  if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 'k') { event.preventDefault(); panelOpen.value = true; textarea.value?.focus() }
}
function startVoice() {
  const SpeechRecognition = (window as unknown as { SpeechRecognition?: new () => { lang: string; start: () => void; onresult: (event: { results: ArrayLike<{ 0: { transcript: string } }> }) => void } }).SpeechRecognition
    ?? (window as unknown as { webkitSpeechRecognition?: new () => { lang: string; start: () => void; onresult: (event: { results: ArrayLike<{ 0: { transcript: string } }> }) => void } }).webkitSpeechRecognition
  if (!SpeechRecognition) { notifications.show('当前浏览器不支持语音输入', 'info'); return }
  const recognition = new SpeechRecognition(); recognition.lang = 'zh-CN'; recognition.onresult = (event) => { input.value = event.results[0]?.[0]?.transcript ?? input.value }; recognition.start()
}
onMounted(() => { window.addEventListener('keydown', globalShortcut); void agentStore.fetchAgents() })
onBeforeUnmount(() => window.removeEventListener('keydown', globalShortcut))
</script>

<template>
  <div class="agent-dock-wrap">
    <AgentChatPanel :open="panelOpen" :review-id="reviewId" :review-title="reviewTitle" @close="panelOpen = false" />
    <transition name="command-menu"><div v-if="commandsOpen" class="agent-command-menu liquid-glass-strong"><button v-for="item in commands" :key="item.command" type="button" @click="chooseCommand(item.prompt)"><span class="font-mono text-sky-200">{{ item.command }}</span><span>{{ item.label }}</span></button></div></transition>
    <div class="agent-chat-dock liquid-glass-pill">
      <AgentSwitcher />
      <textarea ref="textarea" v-model="input" rows="1" class="agent-chat-input" :disabled="disabled || chatStore.sending" :placeholder="disabled ? '请先选择一条审查记录' : `追问 ${agentStore.selectedAgent.name}，或输入 / 唤起命令…`" @focus="panelOpen = true" @input="onInput" @keydown="onKeydown"></textarea>
      <button type="button" class="command-key hidden sm:block" title="聚焦输入框">⌘K</button>
      <button type="button" class="agent-dock-action" title="语音输入" :disabled="disabled" @click="startVoice"><svg width="18" height="18" viewBox="0 0 18 18" fill="none"><rect x="6.5" y="2.5" width="5" height="8" rx="2.5" stroke="currentColor" stroke-width="1.5"/><path d="M4 8.5C4 11.3 6.2 13.5 9 13.5C11.8 13.5 14 11.3 14 8.5M9 13.5V16" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/></svg></button>
      <button type="button" class="agent-send-button" :disabled="disabled || !input.trim() || chatStore.sending" title="发送" @click="send()"><svg width="18" height="18" viewBox="0 0 18 18" fill="none"><path d="M9 3L15 9L9 15M3 9H14" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/></svg></button>
    </div>
  </div>
</template>
