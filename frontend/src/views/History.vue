<script setup lang="ts">
import { onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useHistoryStore } from '../stores/history'
import { useNotificationStore } from '../stores/notification'
import { getErrorMessage } from '../types/api'
import { toInputDate } from '../utils/format'
import EmptyState from '../components/common/EmptyState.vue'
import ErrorState from '../components/common/ErrorState.vue'
import HistoryTable from '../components/history/HistoryTable.vue'
import LoadingState from '../components/common/LoadingState.vue'
import PaginationBar from '../components/common/PaginationBar.vue'
import ReviewTrendChart from '../components/history/ReviewTrendChart.vue'

const store = useHistoryStore()
const router = useRouter()
const notifications = useNotificationStore()

async function applyFilters() {
  if (store.start && store.end && store.start > store.end) { notifications.show('开始日期不能晚于结束日期', 'error'); return }
  try { await store.fetchRecords(1) } catch (cause) { notifications.show(getErrorMessage(cause), 'error') }
}

async function setRange(rangeDays: number) {
  store.days = rangeDays
  const end = new Date()
  const start = new Date()
  start.setDate(end.getDate() - rangeDays + 1)
  store.start = toInputDate(start)
  store.end = toInputDate(end)
  await Promise.all([store.fetchRecords(1), store.fetchStats()]).catch((cause: unknown) => notifications.show(getErrorMessage(cause), 'error'))
}

function clearFilters() { store.repo = ''; store.start = ''; store.end = ''; void store.fetchRecords(1) }
onMounted(() => { void store.refresh().catch(() => undefined) })
</script>

<template>
  <div class="page-shell">
    <header class="page-heading"><div><h1>历史记录</h1><p>按仓库和时间范围回溯审查结果，观察质量趋势。</p></div><div class="flex gap-2"><button v-for="item in [7, 30, 90]" :key="item" type="button" class="secondary-button px-3 py-2 text-xs" :class="store.days === item ? '!border-sky-200/40 !text-sky-100' : ''" @click="setRange(item)">近 {{ item }} 天</button></div></header>
    <section class="grid grid-cols-2 gap-3 xl:grid-cols-4"><div class="metric-card"><span>总审查</span><strong>{{ store.summary.total }}</strong></div><div class="metric-card"><span>建议合并</span><strong class="text-emerald-300">{{ store.summary.approve }}</strong></div><div class="metric-card"><span>需修复</span><strong class="text-amber-200">{{ store.summary.needsFix }}</strong></div><div class="metric-card"><span>阻塞</span><strong class="text-rose-300">{{ store.summary.block }}</strong></div></section>
    <section class="glass-strong rounded-3xl p-5"><LoadingState v-if="store.statsLoading" compact /><ReviewTrendChart v-else :stats="store.stats" /></section>
    <section class="glass rounded-2xl p-4"><div class="grid gap-3 md:grid-cols-2 xl:grid-cols-[1.5fr_1fr_1fr_auto_auto]"><input v-model.trim="store.repo" class="form-control" placeholder="按仓库路径筛选，例如 backend" /><input v-model="store.start" type="date" class="form-control" /><input v-model="store.end" type="date" class="form-control" /><button type="button" class="primary-button px-4 py-2 text-sm" @click="applyFilters">查询</button><button type="button" class="secondary-button px-4 py-2 text-sm" @click="clearFilters">清空</button></div></section>
    <section class="glass-strong flex min-h-0 flex-1 flex-col rounded-3xl p-5"><div class="mb-4 flex items-center justify-between"><h2 class="text-lg font-bold text-white">审查记录</h2><span class="text-xs text-white/35">共 {{ store.total }} 条</span></div><LoadingState v-if="store.loading" compact /><ErrorState v-else-if="store.error" :message="store.error" @retry="store.fetchRecords()" /><EmptyState v-else-if="store.records.length === 0" title="暂无历史记录" description="当前筛选范围内没有已保存的审查记录。" /><div v-else class="min-h-0 flex-1 overflow-y-auto no-scrollbar"><HistoryTable :records="store.records" @open="router.push(`/reviews/${$event}`)" /></div><PaginationBar v-if="store.total > 0" class="mt-auto" :page="store.page" :size="store.size" :total="store.total" @change="store.fetchRecords" /></section>
  </div>
</template>
