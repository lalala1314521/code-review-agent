<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useReviewStore } from '../../stores/review'
import type { FindingSeverity } from '../../types/review'
import { formatDateTime } from '../../utils/format'
import EmptyState from '../common/EmptyState.vue'
import ErrorState from '../common/ErrorState.vue'
import LoadingState from '../common/LoadingState.vue'
import FindingItem from './FindingItem.vue'
import ThinkingStream from './ThinkingStream.vue'
import VerdictStage from './VerdictStage.vue'

withDefaults(defineProps<{ compact?: boolean; showOpenButton?: boolean }>(), { compact: false, showOpenButton: true })
const store = useReviewStore()
const router = useRouter()
const severity = ref<FindingSeverity | 'ALL'>('ALL')
const record = computed(() => store.selectedRecord)
const filteredFindings = computed(() => severity.value === 'ALL' ? store.findings : store.findings.filter((item) => item.severity === severity.value))
const active = computed(() => ['PENDING', 'REVIEWING'].includes(record.value?.status ?? ''))
</script>

<template>
  <div class="flex h-full min-h-0 flex-col">
    <LoadingState v-if="store.detailLoading" label="正在加载审查详情…" />
    <ErrorState v-else-if="store.detailError" :message="store.detailError" @retry="record && store.fetchDetail(record.id)" />
    <EmptyState v-else-if="!record" title="请选择一条审查记录" description="选择左侧记录后，这里会展示结论、问题和进度。" />
    <div v-else class="flex min-h-0 flex-1 flex-col gap-4 overflow-y-auto no-scrollbar pr-1">
      <header class="flex items-start justify-between gap-4">
        <div class="min-w-0">
          <p class="font-mono text-[11px] text-white/35">{{ record.repoPath }} · #{{ record.mrIid }} · {{ record.sourceBranch || '未知分支' }} → {{ record.targetBranch || '未知目标' }}</p>
          <h2 class="mt-1 truncate text-base font-bold text-white">{{ record.title || '(无标题)' }}</h2>
          <p class="mt-1 text-[11px] text-white/35">触发于 {{ formatDateTime(record.triggeredAt) }} · Trace {{ record.traceId }}</p>
        </div>
        <button v-if="showOpenButton" type="button" class="secondary-button shrink-0 px-3 py-2 text-xs" @click="router.push(`/reviews/${record.id}`)">完整详情</button>
      </header>

      <VerdictStage v-if="record.status === 'DONE' && record.conclusion" :conclusion="record.conclusion" :file-count="store.fileCount" :finding-count="store.findings.length" :duration-ms="record.durationMs" :confidence="record.confidence" />
      <div v-else class="rounded-[20px] border px-5 py-6 text-center" :class="record.status === 'FAILED' ? 'border-rose-400/25 bg-rose-400/[0.08]' : 'border-violet-300/20 bg-violet-300/[0.07]'">
        <div class="mx-auto mb-3 h-3 w-3 rounded-full" :class="record.status === 'FAILED' ? 'dot-glow-error' : 'dot-glow-purple pulse'"></div>
        <h3 class="text-xl font-bold text-white">{{ record.status === 'FAILED' ? '审查执行失败' : record.status === 'PENDING' ? '等待审查任务执行' : 'Agent 正在审查代码' }}</h3>
        <p class="mt-2 text-xs text-white/45">{{ record.status === 'FAILED' ? '请结合 TraceId 查询后端日志，或重新触发该 MR。' : '规则扫描、LLM 分析与裁决完成后会自动刷新结果。' }}</p>
      </div>

      <section>
        <div class="mb-2 flex flex-wrap items-center justify-between gap-2">
          <h3 class="text-xs font-semibold text-white/60">审查发现</h3>
          <div class="flex gap-1">
            <button v-for="item in ['ALL', 'ERROR', 'WARNING', 'INFO'] as const" :key="item" type="button" class="rounded-full px-2 py-1 text-[10px] transition" :class="severity === item ? 'bg-white/15 text-white' : 'text-white/35 hover:bg-white/5'" @click="severity = item">{{ item === 'ALL' ? `全部 ${store.findings.length}` : `${item} ${store.findingCounts[item]}` }}</button>
          </div>
        </div>
        <div v-if="filteredFindings.length" class="space-y-2"><FindingItem v-for="finding in filteredFindings" :key="finding.id" :finding="finding" /></div>
        <div v-else class="rounded-2xl border border-dashed border-white/10 px-4 py-6 text-center text-xs text-white/35">当前分类没有审查发现。</div>
      </section>

      <section>
        <h3 class="mb-2 text-xs font-semibold text-white/60">Agent 进度流</h3>
        <ThinkingStream :events="store.progressEvents" :status="store.streamStatus" :active="active" />
      </section>
    </div>
  </div>
</template>

