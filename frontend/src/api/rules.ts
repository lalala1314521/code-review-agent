import request from './request'
import type { ReviewRule, RuleQuery, RuleRequest } from '../types/rule'

export function getRules(params: RuleQuery = {}): Promise<ReviewRule[]> {
  return request.get('/rules', { params })
}

export function createRule(payload: RuleRequest): Promise<ReviewRule> {
  return request.post('/rules', payload)
}

export function updateRule(id: number, payload: RuleRequest): Promise<ReviewRule> {
  return request.put(`/rules/${id}`, payload)
}

export function deleteRule(id: number): Promise<void> {
  return request.delete(`/rules/${id}`)
}

export function setRuleEnabled(id: number, enabled: boolean): Promise<ReviewRule> {
  return request.post(`/rules/${id}/${enabled ? 'enable' : 'disable'}`)
}
