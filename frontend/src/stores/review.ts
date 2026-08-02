import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { getReviewFindings, getReviews } from '../api/review'
import { subscribeReviewStream } from '../api/sse'
import type { StreamSubscription } from '../api/sse'
import { useContextStore } from './context'
import type { FindingSeverity, ProgressEvent, ReviewConclusion, ReviewDetailData, ReviewQuery, ReviewRecord, ReviewStatus } from '../types/review'
import { getErrorMessage } from '../types/api'

export const useReviewStore = defineStore('review', () => {
  const records = ref<ReviewRecord[]>([])
  const page = ref(1)
  const size = ref(20)
  const total = ref(0)
  const statusFilter = ref<ReviewStatus | ''>('')
  const conclusionFilter = ref<ReviewConclusion | ''>('')
  const searchTerm = ref('')
  const selectedId = ref<number | null>(null)
  const detail = ref<ReviewDetailData | null>(null)
  const loading = ref(false)
  const detailLoading = ref(false)
  const error = ref('')
  const detailError = ref('')
  const progressEvents = ref<ProgressEvent[]>([])
  const streamStatus = ref<'idle' | 'connecting' | 'open' | 'closed' | 'error'>('idle')
  let stream: StreamSubscription | null = null
  let detailRequestId = 0

  const selectedRecord = computed(() => detail.value?.reviewRecord ?? records.value.find((item) => item.id === selectedId.value) ?? null)
  const filteredRecords = computed(() => {
    const keyword = searchTerm.value.trim().toLowerCase()
    if (!keyword) return records.value
    return records.value.filter((item) => [item.title, item.repoPath, item.sourceBranch, item.authorUsername, item.commitSha, String(item.mrIid)]
      .some((value) => value?.toLowerCase().includes(keyword)))
  })
  const findings = computed(() => detail.value?.findings ?? [])
  const fileCount = computed(() => new Set(findings.value.map((item) => item.filePath)).size)
  const findingCounts = computed<Record<FindingSeverity, number>>(() => ({
    ERROR: findings.value.filter((item) => item.severity === 'ERROR').length,
    WARNING: findings.value.filter((item) => item.severity === 'WARNING').length,
    INFO: findings.value.filter((item) => item.severity === 'INFO').length,
  }))

  async function fetchQueue(options: ReviewQuery = {}, selectFirst = false) {
    loading.value = true
    error.value = ''
    // 上下文切换器：默认带全局仓库/分支筛选，调用方可显式覆盖
    const contextStore = useContextStore()
    const query: ReviewQuery = {
      page: options.page ?? page.value,
      size: options.size ?? size.value,
      status: options.status ?? statusFilter.value,
      conclusion: options.conclusion ?? conclusionFilter.value,
      repoPath: options.repoPath ?? contextStore.selectedRepo ?? undefined,
      branch: options.branch ?? contextStore.selectedBranch ?? undefined,
    }
    try {
      const result = await getReviews(query)
      records.value = result.items
      page.value = result.page
      size.value = result.size
      total.value = result.total
      if (selectFirst && records.value.length > 0 && !records.value.some((item) => item.id === selectedId.value)) {
        await selectReview(records.value[0].id)

      }
      return result
    } catch (cause) {
      records.value = []
      total.value = 0
      error.value = getErrorMessage(cause)
      throw cause
    } finally {
      loading.value = false
    }
  }

  async function fetchDetail(id: number, connectStream = true) {
    const requestId = ++detailRequestId
    detailLoading.value = true
    detailError.value = ''
    try {
      const data = await getReviewFindings(id)
      if (requestId !== detailRequestId) return null
      detail.value = data
      selectedId.value = id
      const index = records.value.findIndex((item) => item.id === id)
      if (index >= 0) records.value[index] = data.reviewRecord
      if (connectStream && ['PENDING', 'REVIEWING'].includes(data.reviewRecord.status)) startStream(id)
      else stopStream()
      return data
    } catch (cause) {
      if (requestId === detailRequestId) {
        detail.value = null
        detailError.value = getErrorMessage(cause)
      }
      throw cause
    } finally {
      if (requestId === detailRequestId) detailLoading.value = false
    }
  }

  async function selectReview(recordOrId: ReviewRecord | number) {
    const id = typeof recordOrId === 'number' ? recordOrId : recordOrId.id
    selectedId.value = id
    progressEvents.value = []
    await fetchDetail(id, true)
  }

  function startStream(id: number) {
    stopStream()
    progressEvents.value = []
    streamStatus.value = 'connecting'
    stream = subscribeReviewStream(id, {
      onEvent(event) {
        streamStatus.value = 'open'
        const key = `${event.stage}:${event.at}:${event.message}`
        const exists = progressEvents.value.some((item) => `${item.stage}:${item.at}:${item.message}` === key)
        if (!exists) progressEvents.value.push(event)
      },
      onTerminal() {
        streamStatus.value = 'closed'
        void refreshAfterTerminal(id)
      },
      onError() {
        streamStatus.value = 'error'
      },
    })
  }

  async function refreshAfterTerminal(id: number) {
    try {
      await Promise.all([fetchDetail(id, false), fetchQueue({}, false)])
    } catch {
      // 详情区已经暴露错误状态，无需重复抛出。
    }
  }

  function stopStream() {
    stream?.close()
    stream = null
    if (streamStatus.value !== 'closed') streamStatus.value = 'idle'
  }

  function setPage(nextPage: number) {
    page.value = nextPage
    return fetchQueue({ page: nextPage }, false)
  }

  function clearFilters() {
    statusFilter.value = ''
    conclusionFilter.value = ''
    searchTerm.value = ''
    page.value = 1
  }

  return {
    records, filteredRecords, page, size, total, statusFilter, conclusionFilter, searchTerm,
    selectedId, selectedRecord, detail, findings, fileCount, findingCounts,
    loading, detailLoading, error, detailError, progressEvents, streamStatus,
    fetchQueue, fetchDetail, selectReview, startStream, stopStream, setPage, clearFilters,
  }
})

