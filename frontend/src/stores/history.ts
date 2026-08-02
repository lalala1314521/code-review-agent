import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { getHistory, getHistoryStats } from '../api/history'
import { useContextStore } from './context'
import type { HistoryRecord, HistoryStat } from '../types/history'
import { getErrorMessage } from '../types/api'

export const useHistoryStore = defineStore('history', () => {
  const records = ref<HistoryRecord[]>([])
  const stats = ref<HistoryStat[]>([])
  const page = ref(1)
  const size = ref(20)
  const total = ref(0)
  const repo = ref('')
  const start = ref('')
  const end = ref('')
  const days = ref(30)
  const loading = ref(false)
  const statsLoading = ref(false)
  const error = ref('')

  const summary = computed(() => stats.value.reduce((result, row) => ({
    total: result.total + Number(row.totalCount),
    approve: result.approve + Number(row.approveCount),
    needsFix: result.needsFix + Number(row.needsFixCount),
    block: result.block + Number(row.blockCount),
  }), { total: 0, approve: 0, needsFix: 0, block: 0 }))

  async function fetchRecords(nextPage = page.value) {
    loading.value = true
    error.value = ''
    try {
      // 上下文切换器：选中仓库时覆盖页内 repo 模糊筛选（精确 > 模糊）
      const contextStore = useContextStore()
      const result = await getHistory({
        page: nextPage,
        size: size.value,
        repo: contextStore.selectedRepo ?? (repo.value || undefined),
        branch: contextStore.selectedBranch ?? undefined,
        start: start.value || undefined,
        end: end.value || undefined,
      })
      records.value = result.items
      page.value = result.page
      size.value = result.size
      total.value = result.total
    } catch (cause) {
      records.value = []
      total.value = 0
      error.value = getErrorMessage(cause)
      throw cause
    } finally {
      loading.value = false
    }
  }

  async function fetchStats() {
    statsLoading.value = true
    try {
      stats.value = await getHistoryStats(days.value)
    } finally {
      statsLoading.value = false
    }
  }

  async function refresh() {
    await Promise.all([fetchRecords(1), fetchStats()])
  }

  return { records, stats, summary, page, size, total, repo, start, end, days, loading, statsLoading, error, fetchRecords, fetchStats, refresh }
})
