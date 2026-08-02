export type RuleSeverity = 'ERROR' | 'WARNING' | 'INFO'
export type RuleType = 'BUILTIN' | 'CUSTOM'

export interface ReviewRule {
  id: number
  ruleId: string
  name: string
  description: string | null
  severity: RuleSeverity
  language: string | null
  ruleType: RuleType
  paramsJson: string | null
  enabled: boolean
  createdAt: string
  updatedAt: string
}

export interface RuleRequest {
  ruleId: string
  name: string
  description: string | null
  severity: RuleSeverity
  language: string | null
  paramsJson: string | null
}

export interface RuleQuery {
  language?: string
  enabled?: boolean
}
