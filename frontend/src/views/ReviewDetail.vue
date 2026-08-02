<script setup lang="ts">
import { onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useReviewStore } from '../stores/review'
import ReviewDetailPanel from '../components/review/ReviewDetailPanel.vue'
import AgentChatDock from '../components/agent/AgentChatDock.vue'

const route = useRoute()
const router = useRouter()
const store = useReviewStore()

onMounted(() => {
  const id = Number(route.params.id)
  if (Number.isFinite(id)) void store.fetchDetail(id, true).catch(() => undefined)
})
onUnmounted(() => store.stopStream())
</script>

<template>
  <div class="page-shell">
    <header class="page-heading">
      <div><button type="button" class="mb-2 text-xs text-white/40 hover:text-white" @click="router.push('/reviews')">← 返回审查队列</button><h1>审查详情</h1><p>查看审查元数据、Agent 裁决、问题列表和执行进度。</p></div>
      <button v-if="store.selectedRecord" type="button" class="secondary-button px-4 py-2 text-sm" @click="store.fetchDetail(store.selectedRecord.id, true)">刷新详情</button>
    </header>
    <section class="glass-strong min-h-0 flex-1 overflow-hidden rounded-3xl p-6"><ReviewDetailPanel :show-open-button="false" /></section>
    <AgentChatDock :review-id="store.selectedRecord?.id ?? null" :review-title="store.selectedRecord?.title || undefined" />
  </div>
</template>

