export type ChatRole = 'user' | 'assistant'

export interface AgentChatMessage {
  id: string
  role: ChatRole
  content: string
  provider?: string
  model?: string
  agentName?: string
  createdAt: number
  failed?: boolean
}

export interface ReviewChatRequest {
  provider?: string
  agentName?: string
  instruction?: string
  message: string
  history: Array<{ role: ChatRole; content: string }>
}

export interface ReviewChatResponse {
  answer: string
  provider: string
  model: string
  agentName: string
}
