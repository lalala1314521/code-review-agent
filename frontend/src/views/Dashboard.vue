<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useDashboardStore } from '../stores/dashboard'
import { useNotificationStore } from '../stores/notification'
import { useReviewStore } from '../stores/review'
import { getErrorMessage } from '../types/api'
import KpiCard from '../components/kpi/KpiCard.vue'
import EmptyState from '../components/common/EmptyState.vue'
import ErrorState from '../components/common/ErrorState.vue'
import LoadingState from '../components/common/LoadingState.vue'
import ReviewDetailPanel from '../components/review/ReviewDetailPanel.vue'
import ReviewItem from '../components/review/ReviewItem.vue'
import LocalReviewModal from '../components/review/LocalReviewModal.vue'
import AgentChatDock from '../components/agent/AgentChatDock.vue'

const dashboard = useDashboardStore()
const reviews = useReviewStore()
const notifications = useNotificationStore()
const router = useRouter()
const localOpen = ref(false)

const kpiList = computed(() => {
  const reviewDelta = dashboard.kpi.todayReviewCount - dashboard.kpi.yesterdayReviewCount
  const durationDelta = dashboard.kpi.prevAvgDurationMs - dashboard.kpi.avgDurationMs
  const rateDelta = Math.round((dashboard.kpi.passRate - dashboard.kpi.prevPassRate) * 100)
  return [
    { title: '今日审查', value: String(dashboard.kpi.todayReviewCount), delta: `${reviewDelta >= 0 ? '↑' : '↓'} ${Math.abs(reviewDelta)} 较昨日`, deltaUp: reviewDelta >= 0, icon: 'review' as const, danger: false },
    { title: '平均耗时', value: `${(dashboard.kpi.avgDurationMs / 1000).toFixed(1)}s`, delta: `${durationDelta >= 0 ? '↓' : '↑'} ${Math.abs(durationDelta / 1000).toFixed(1)}s ${durationDelta >= 0 ? '提速' : '变慢'}`, deltaUp: durationDelta >= 0, icon: 'duration' as const, danger: false },
    { title: '通过率', value: `${Math.round(dashboard.kpi.passRate * 100)}%`, delta: `${rateDelta >= 0 ? '↑' : '↓'} ${Math.abs(rateDelta)}% 较昨日`, deltaUp: rateDelta >= 0, icon: 'passrate' as const, danger: false },
    { title: '阻塞 MR', value: String(dashboard.kpi.blockedMrCount), delta: dashboard.kpi.blockedMrCount ? '需立即处理' : '当前无阻塞', deltaUp: false, icon: 'block' as const, danger: dashboard.kpi.blockedMrCount > 0 },
  ]
})

async function refresh(showMessage = false) {
  try {
    await Promise.all([dashboard.refresh(), reviews.fetchQueue({ page: 1, size: 20, status: '', conclusion: '' }, true)])
    if (showMessage) notifications.show('仪表盘数据已刷新', 'success')
  } catch (cause) {
    if (showMessage) notifications.show(getErrorMessage(cause), 'error')
  }
}

function createRule() {
  router.push({ path: '/rules', query: { create: '1' } })
}

/** 本地审查触发成功：刷新队列（新记录自动置顶选中并挂 SSE 思考流） */
async function onLocalCreated() {
  await refresh()
}

onMounted(() => { void refresh() })
onUnmounted(() => reviews.stopStream())
</script>

<template>
  <div class="page-shell">
    <header class="page-heading">
      <div>
        <h1>代码审查指挥中心</h1>
        <p>实时监听 GitLab Webhook · Agent 自动审查每个 Merge Request</p>
      </div>
      <div class="flex items-center gap-3">
        <button type="button" class="icon-button" title="刷新数据" :disabled="dashboard.loading || reviews.loading" @click="refresh(true)">
          <svg width="17" height="17" viewBox="0 0 18 18" fill="none"><path d="M15 8A6 6 0 1 0 13.7 12" stroke="currentColor" stroke-width="1.6" stroke-linecap="round"/><path d="M15 4V8H11" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"/></svg>
        </button>
        <button type="button" class="primary-button flex items-center gap-2 px-4 py-2.5 text-[13px]" @click="createRule"><span class="text-lg leading-none">＋</span>新建审查规则</button>
      </div>
    </header>

    <section class="grid grid-cols-2 gap-3 xl:grid-cols-4 xl:gap-5">
      <KpiCard v-for="item in kpiList" :key="item.title" v-bind="item" />
    </section>

    <div class="grid min-h-0 flex-1 gap-5 xl:grid-cols-2">
      <section class="glass-strong flex min-h-[360px] flex-col rounded-3xl p-5 xl:min-h-0">
        <div class="mb-4 flex items-center justify-between gap-3">
          <div class="flex items-center gap-2.5"><h2 class="text-lg font-bold text-white">MR 审查队列</h2><span class="rounded-full bg-sky-200/10 px-2 py-0.5 text-[11px] font-semibold text-sky-200">{{ reviews.total }} 条</span></div>
          <div class="flex items-center gap-3">
            <button type="button" class="text-xs text-sky-200/80 hover:text-sky-100" @click="localOpen = true">⇪ 本地审查</button>
            <button type="button" class="text-xs text-white/40 hover:text-white" @click="router.push('/reviews')">查看全部 →</button>
          </div>
        </div>
        <LoadingState v-if="reviews.loading && reviews.records.length === 0" compact />
        <ErrorState v-else-if="reviews.error" :message="reviews.error" @retry="refresh()" />
        <EmptyState v-else-if="reviews.records.length === 0" title="暂无审查记录" description="GitLab Webhook 触发后，记录会出现在这里。" />
        <div v-else class="flex min-h-0 flex-1 flex-col gap-2.5 overflow-y-auto no-scrollbar pr-1">
          <ReviewItem v-for="record in reviews.records" :key="record.id" :record="record" :selected="reviews.selectedId === record.id" @select="reviews.selectReview" />
        </div>
      </section>

      <section class="glass-strong min-h-[480px] rounded-3xl p-5 xl:min-h-0"><ReviewDetailPanel compact /></section>
    </div>

    <AgentChatDock :review-id="reviews.selectedRecord?.id ?? null" :review-title="reviews.selectedRecord?.title || undefined" />
    <LocalReviewModal v-model="localOpen" @created="onLocalCreated" />
  </div>
</template>


