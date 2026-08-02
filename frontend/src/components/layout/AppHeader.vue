<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useDashboardStore } from '../../stores/dashboard'
import { useReviewStore } from '../../stores/review'
import { formatRelativeTime } from '../../utils/format'
import RepoBranchSwitcher from './RepoBranchSwitcher.vue'

const dashboard = useDashboardStore()
const reviews = useReviewStore()
const router = useRouter()
const query = ref('')
const webhookText = computed(() => dashboard.webhook.connected ? `Webhook 已连接${dashboard.webhook.lastWebhookAt ? ` · ${formatRelativeTime(dashboard.webhook.lastWebhookAt)}` : ''}` : 'Webhook 连接异常')

function search() {
  reviews.searchTerm = query.value.trim()
  void router.push('/reviews')
}

onMounted(() => { void dashboard.fetchWebhookStatus() })
</script>

<template>
  <header class="app-header-glass relative z-20 flex h-16 shrink-0 items-center justify-between gap-4 px-4 sm:px-6">
    <button type="button" class="flex items-center gap-3" @click="router.push('/dashboard')"><div class="grad-accent flex h-9 w-9 items-center justify-center rounded-[10px]"><svg width="20" height="20" viewBox="0 0 20 20" fill="none"><path d="M7 6L3 10L7 14M13 6L17 10L13 14" stroke="#0b0b12" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/></svg></div><span class="hidden text-base font-bold text-white sm:block">CodeReview Agent</span></button>
    <div class="hidden min-w-0 flex-1 items-center justify-center gap-3 md:flex">
      <RepoBranchSwitcher />
      <form class="flex w-full max-w-[360px] items-center gap-2 rounded-full border border-white/10 bg-white/[0.045] px-3.5 py-2 focus-within:border-sky-200/35" @submit.prevent="search"><svg width="14" height="14" viewBox="0 0 14 14" fill="none"><circle cx="6" cy="6" r="4.5" stroke="white" stroke-width="1.5" opacity=".45"/><path d="M9.5 9.5L12 12" stroke="white" stroke-width="1.5" stroke-linecap="round" opacity=".45"/></svg><input v-model="query" class="min-w-0 flex-1 bg-transparent text-xs text-white outline-none placeholder:text-white/30" placeholder="搜索当前页记录，回车进入队列…" /><button v-if="query" type="button" class="text-white/35" @click="query = ''">×</button></form>
    </div>
    <div class="flex items-center gap-3"><button type="button" class="hidden max-w-[260px] items-center gap-2 rounded-full border px-3 py-1.5 lg:flex" :class="dashboard.webhook.connected ? 'border-emerald-400/30 bg-emerald-400/10' : 'border-rose-400/30 bg-rose-400/10'" :title="webhookText" @click="dashboard.fetchWebhookStatus"><span class="h-2 w-2 rounded-full" :class="dashboard.webhook.connected ? 'dot-glow-success' : 'dot-glow-error'"></span><span class="truncate text-xs font-semibold" :class="dashboard.webhook.connected ? 'text-emerald-300' : 'text-rose-300'">{{ webhookText }}</span></button><div class="grad-accent flex h-9 w-9 items-center justify-center rounded-full border border-white/20 text-sm font-bold text-slate-950">A</div></div>
  </header>
</template>

