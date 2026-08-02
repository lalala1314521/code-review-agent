import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { getAgents, switchActiveAgent } from '../api/agent'
import type { AgentInfo, AgentProfile, BuiltinAgentProfile, CustomAgentForm, CustomAgentProfile } from '../types/agent'
import { getErrorMessage } from '../types/api'

const CUSTOM_KEY = 'codereview:custom-agents'
const SELECTED_KEY = 'codereview:selected-agent'
const accentByProvider: Record<string, BuiltinAgentProfile['accent']> = { deepseek: 'purple', qianwen: 'amber', openai: 'blue' }
const instructionByProvider: Record<string, string> = {
  deepseek: '重视问题推理、风险解释和可执行的修复步骤。',
  qianwen: '使用清晰的中文结构化说明，兼顾工程实践与可读性。',
  openai: '优先给出简洁结论、代码示例和测试建议。',
}

function loadCustomProfiles(): CustomAgentProfile[] {
  try {
    const raw = localStorage.getItem(CUSTOM_KEY)
    return raw ? JSON.parse(raw) as CustomAgentProfile[] : []
  } catch { return [] }
}

export const useAgentStore = defineStore('agent', () => {
  const agents = ref<AgentInfo[]>([])
  const customProfiles = ref<CustomAgentProfile[]>(loadCustomProfiles())
  const selectedId = ref(localStorage.getItem(SELECTED_KEY) || 'provider:deepseek')
  const loading = ref(false)
  const switching = ref(false)
  const error = ref('')

  const builtinProfiles = computed<BuiltinAgentProfile[]>(() => agents.value.map((agent) => ({
    ...agent,
    id: `provider:${agent.provider}`,
    name: agent.displayName,
    instruction: instructionByProvider[agent.provider] ?? '解释代码审查结果并给出修复建议。',
    accent: accentByProvider[agent.provider] ?? 'green',
    custom: false,
  })))
  const profiles = computed<AgentProfile[]>(() => [...builtinProfiles.value, ...customProfiles.value])
  const activeProvider = computed(() => agents.value.find((item) => item.active)?.provider ?? 'deepseek')
  const selectedAgent = computed<AgentProfile>(() => profiles.value.find((item) => item.id === selectedId.value)
    ?? builtinProfiles.value.find((item) => item.provider === activeProvider.value)
    ?? {
      id: 'provider:deepseek', provider: 'deepseek', name: 'DeepSeek', displayName: 'DeepSeek', model: 'deepseek-chat',
      active: true, available: true, defaultAgent: true, instruction: instructionByProvider.deepseek, accent: 'purple', custom: false,
    })

  async function fetchAgents() {
    loading.value = true
    error.value = ''
    try {
      agents.value = await getAgents()
      const selected = selectedAgent.value
      if (!selected.custom && !agents.value.some((item) => item.provider === selected.provider)) {
        selectLocally(`provider:${activeProvider.value}`)
      }
    } catch (cause) {
      error.value = getErrorMessage(cause)
      if (agents.value.length === 0) {
        agents.value = [
          { provider: 'deepseek', displayName: 'DeepSeek', model: 'deepseek-chat', active: true, available: false, defaultAgent: true },
          { provider: 'qianwen', displayName: '通义千问', model: 'qwen-plus', active: false, available: false, defaultAgent: false },
          { provider: 'openai', displayName: 'OpenAI', model: 'gpt-4o-mini', active: false, available: false, defaultAgent: false },
        ]
      }
    } finally { loading.value = false }
  }

  async function selectAgent(profile: AgentProfile) {
    switching.value = true
    try {
      const updated = await switchActiveAgent(profile.provider)
      agents.value = agents.value.map((item) => ({ ...item, active: item.provider === updated.provider }))
      selectLocally(profile.id)
    } finally { switching.value = false }
  }

  function createCustomAgent(form: CustomAgentForm): CustomAgentProfile {
    const profile: CustomAgentProfile = {
      id: `custom:${Date.now()}-${Math.random().toString(36).slice(2, 7)}`,
      name: form.name.trim(), provider: form.provider, instruction: form.instruction.trim(), accent: form.accent, custom: true,
    }
    customProfiles.value.push(profile)
    persistCustomProfiles()
    return profile
  }

  function updateCustomAgent(id: string, form: CustomAgentForm) {
    const index = customProfiles.value.findIndex((item) => item.id === id)
    if (index < 0) return
    customProfiles.value[index] = { ...customProfiles.value[index], ...form, name: form.name.trim(), instruction: form.instruction.trim() }
    persistCustomProfiles()
  }

  function removeCustomAgent(id: string) {
    customProfiles.value = customProfiles.value.filter((item) => item.id !== id)
    if (selectedId.value === id) selectLocally(`provider:${activeProvider.value}`)
    persistCustomProfiles()
  }

  function selectLocally(id: string) {
    selectedId.value = id
    localStorage.setItem(SELECTED_KEY, id)
  }

  function persistCustomProfiles() {
    localStorage.setItem(CUSTOM_KEY, JSON.stringify(customProfiles.value))
  }

  return { agents, builtinProfiles, customProfiles, profiles, selectedId, selectedAgent, activeProvider, loading, switching, error, fetchAgents, selectAgent, createCustomAgent, updateCustomAgent, removeCustomAgent }
})
