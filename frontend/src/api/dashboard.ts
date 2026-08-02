import request from './request'

export interface KpiData {
  todayReviewCount: number
  yesterdayReviewCount: number
  avgDurationMs: number
  prevAvgDurationMs: number
  passRate: number
  prevPassRate: number
  blockedMrCount: number
}

export interface WebhookStatus {
  connected: boolean
  lastWebhookAt: string
}

export function getKpi(): Promise<KpiData> {
  return request.get('/dashboard/kpi')
}

export function getWebhookStatus(): Promise<WebhookStatus> {
  return request.get('/dashboard/webhook-status')
}
