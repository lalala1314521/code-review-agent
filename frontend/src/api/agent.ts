import request from './request'
import type { AgentInfo, ProviderConfig, ProviderConfigUpdateRequest } from '../types/agent'

export function getAgents(): Promise<AgentInfo[]> {
  return request.get('/agents')
}

export function getActiveAgent(): Promise<AgentInfo> {
  return request.get('/agents/active')
}

export function switchActiveAgent(provider: string): Promise<AgentInfo> {
  return request.put('/agents/active', { provider })
}

// ===== Provider 运行时配置（管理台改 LLM 配置）=====

export function getProviders(): Promise<ProviderConfig[]> {
  return request.get('/providers')
}

export function updateProvider(provider: string, payload: ProviderConfigUpdateRequest): Promise<ProviderConfig> {
  return request.put(`/providers/${provider}`, payload)
}

export function resetProvider(provider: string): Promise<null> {
  return request.delete(`/providers/${provider}`)
}
