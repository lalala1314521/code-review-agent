export interface AgentInfo {
  provider: string
  displayName: string
  model: string
  active: boolean
  available: boolean
  defaultAgent: boolean
}

export interface CustomAgentProfile {
  id: string
  name: string
  provider: string
  instruction: string
  accent: 'purple' | 'blue' | 'amber' | 'green'
  custom: true
}

export interface BuiltinAgentProfile extends AgentInfo {
  id: string
  name: string
  instruction: string
  accent: 'purple' | 'blue' | 'amber' | 'green'
  custom: false
}

export type AgentProfile = BuiltinAgentProfile | CustomAgentProfile

export interface CustomAgentForm {
  name: string
  provider: string
  instruction: string
  accent: CustomAgentProfile['accent']
}

// ===== Provider 运行时配置（对齐后端 ProviderConfigView；apiKey 只有掩码）=====
export interface ProviderConfig {
  provider: string
  baseUrl: string
  apiKeyMasked: string
  model: string
  maxTokens: number
  temperature: number
  configured: boolean
  keyFromDatabase: boolean
  active: boolean
}

export interface ProviderConfigUpdateRequest {
  baseUrl?: string
  /** 传空 / 掩码值 = 不修改 key；传新值则加密入库 */
  apiKey?: string
  model?: string
  maxTokens?: number
  temperature?: number
}
