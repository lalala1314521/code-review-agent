import { defineStore } from 'pinia'
import { ref } from 'vue'
import { getKpi, getWebhookStatus } from '../api/dashboard'
import type { KpiData, WebhookStatus } from '../api/dashboard'
import { getErrorMessage } from '../types/api'

const emptyKpi: KpiData = {
  todayReviewCount: 0,
  yesterdayReviewCount: 0,
  avgDurationMs: 0,
  prevAvgDurationMs: 0,
  passRate: 0,
  prevPassRate: 0,
  blockedMrCount: 0,
}

export const useDashboardStore = defineStore('dashboard', () => {
  const kpi = ref<KpiData>({ ...emptyKpi })
  const webhook = ref<WebhookStatus>({ connected: false, lastWebhookAt: '' })
  const loading = ref(false)
  const error = ref('')

  async function fetchKpi() {
    try {
      kpi.value = await getKpi()
    } catch (cause) {
      error.value = getErrorMessage(cause)
      throw cause
    }
  }

  async function fetchWebhookStatus() {
    try {
      webhook.value = await getWebhookStatus()
    } catch {
      webhook.value = { connected: false, lastWebhookAt: '' }
    }
  }

  async function refresh() {
    loading.value = true
    error.value = ''
    try {
      await Promise.all([fetchKpi(), fetchWebhookStatus()])
    } finally {
      loading.value = false
    }
  }

  return { kpi, webhook, loading, error, fetchKpi, fetchWebhookStatus, refresh }
})
