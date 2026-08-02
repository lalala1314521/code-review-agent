import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { sendReviewChat } from '../api/chat'
import type { AgentProfile } from '../types/agent'
import type { AgentChatMessage } from '../types/chat'
import { getErrorMessage } from '../types/api'

const STORAGE_KEY = 'codereview:agent-chat'

function loadMessages(): Record<string, AgentChatMessage[]> {
  try {
    const raw = sessionStorage.getItem(STORAGE_KEY)
    return raw ? JSON.parse(raw) as Record<string, AgentChatMessage[]> : {}
  } catch { return {} }
}

function messageId() { return `${Date.now()}-${Math.random().toString(36).slice(2, 8)}` }

export const useAgentChatStore = defineStore('agentChat', () => {
  const messagesByReview = ref<Record<string, AgentChatMessage[]>>(loadMessages())
  const activeReviewId = ref<number | null>(null)
  const sending = ref(false)
  const error = ref('')
  const currentMessages = computed(() => activeReviewId.value == null ? [] : messagesByReview.value[String(activeReviewId.value)] ?? [])

  function setReview(reviewId: number | null) { activeReviewId.value = reviewId }

  async function send(reviewId: number, text: string, agent: AgentProfile) {
    const content = text.trim()
    if (!content || sending.value) return
    activeReviewId.value = reviewId
    const key = String(reviewId)
    const list = messagesByReview.value[key] ?? []
    const userMessage: AgentChatMessage = { id: messageId(), role: 'user', content, createdAt: Date.now() }
    messagesByReview.value[key] = [...list, userMessage]
    persist()
    sending.value = true
    error.value = ''
    try {
      const history = messagesByReview.value[key].slice(-13, -1).map((item) => ({ role: item.role, content: item.content }))
      const response = await sendReviewChat(reviewId, {
        provider: agent.provider,
        agentName: agent.name,
        instruction: agent.instruction,
        message: content,
        history,
      })
      messagesByReview.value[key].push({
        id: messageId(), role: 'assistant', content: response.answer, provider: response.provider,
        model: response.model, agentName: response.agentName, createdAt: Date.now(),
      })
      persist()
    } catch (cause) {
      error.value = getErrorMessage(cause)
      messagesByReview.value[key] = messagesByReview.value[key].map((item) => item.id === userMessage.id ? { ...item, failed: true } : item)
      persist()
      throw cause
    } finally { sending.value = false }
  }

  function clear(reviewId: number) {
    delete messagesByReview.value[String(reviewId)]
    messagesByReview.value = { ...messagesByReview.value }
    persist()
  }

  function persist() { sessionStorage.setItem(STORAGE_KEY, JSON.stringify(messagesByReview.value)) }

  return { messagesByReview, activeReviewId, currentMessages, sending, error, setReview, send, clear }
})
