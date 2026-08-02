<script setup lang="ts">
import { onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { useReviewStore } from '../stores/review'
import EmptyState from '../components/common/EmptyState.vue'
import ErrorState from '../components/common/ErrorState.vue'
import LoadingState from '../components/common/LoadingState.vue'
import PaginationBar from '../components/common/PaginationBar.vue'
import ReviewDetailPanel from '../components/review/ReviewDetailPanel.vue'
import ReviewFilters from '../components/review/ReviewFilters.vue'
import ReviewItem from '../components/review/ReviewItem.vue'

const store = useReviewStore()
const router = useRouter()

async function applyFilters() {
  store.page = 1
  await store.fetchQueue({ page: 1 }, false)
}

async function clearFilters() {
  store.clearFilters()
  await store.fetchQueue({ page: 1 }, false)
}

async function selectRecord(id: number) {
  await store.selectReview(id)
}

onMounted(async () => {
  await store.fetchQueue({}, false).catch(() => undefined)
  if (!store.selectedId && store.records[0]) await selectRecord(store.records[0].id).catch(() => undefined)
})
onUnmounted(() => store.stopStream())
</script>

<template>
  <div class="page-shell">
    <header class="page-heading">
      <div><h1>MR 审查队列</h1><p>按执行状态和裁决结论筛选审查记录，并查看实时处理进度。</p></div>
      <button type="button" class="secondary-button px-4 py-2 text-sm" :disabled="store.loading" @click="store.fetchQueue({}, false)">刷新</button>
    </header>

    <section class="glass rounded-2xl p-4">
      <div class="mb-3"><input v-model.trim="store.searchTerm" class="form-control" placeholder="在当前页搜索标题、仓库、分支、作者或 Commit…" /></div>
      <ReviewFilters v-model:status="store.statusFilter" v-model:conclusion="store.conclusionFilter" @apply="applyFilters" @clear="clearFilters" />
    </section>

    <div class="grid min-h-0 flex-1 gap-5 2xl:grid-cols-[minmax(420px,0.9fr)_minmax(520px,1.1fr)]">
      <section class="glass-strong flex min-h-[420px] flex-col rounded-3xl p-5 2xl:min-h-0">
        <div class="mb-4 flex items-center justify-between"><h2 class="text-lg font-bold text-white">审查记录</h2><span class="text-xs text-white/40">当前页 {{ store.filteredRecords.length }} / 共 {{ store.total }}</span></div>
        <LoadingState v-if="store.loading && store.records.length === 0" compact />
        <ErrorState v-else-if="store.error" :message="store.error" @retry="store.fetchQueue({}, false)" />
        <EmptyState v-else-if="store.filteredRecords.length === 0" title="没有匹配的审查记录" description="尝试调整状态、结论或搜索关键字。" />
        <div v-else class="flex min-h-0 flex-1 flex-col gap-2.5 overflow-y-auto no-scrollbar pr-1">
          <ReviewItem v-for="record in store.filteredRecords" :key="record.id" :record="record" :selected="store.selectedId === record.id" @select="selectRecord(record.id)" />
        </div>
        <PaginationBar class="mt-4" :page="store.page" :size="store.size" :total="store.total" @change="store.setPage" />
      </section>
      <section class="glass-strong min-h-[520px] rounded-3xl p-5 2xl:min-h-0"><ReviewDetailPanel /></section>
    </div>

    <button v-if="store.selectedId" type="button" class="fixed bottom-5 right-5 primary-button px-4 py-2 text-xs 2xl:hidden" @click="router.push(`/reviews/${store.selectedId}`)">打开完整详情</button>
  </div>
</template>
