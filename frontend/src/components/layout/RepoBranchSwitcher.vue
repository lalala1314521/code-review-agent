<script setup lang="ts">
// 仓库+分支上下文切换器：两级下拉（仓库 → 分支）；⟳ 勾取远程分支（失败分仓降级）；＋ 手动添加分支
// 切换后刷新 MR 队列与历史记录
import { onMounted, reactive, ref } from 'vue'
import { useContextStore } from '../../stores/context'
import { useReviewStore } from '../../stores/review'
import { useHistoryStore } from '../../stores/history'
import { useNotificationStore } from '../../stores/notification'

const context = useContextStore()
const reviews = useReviewStore()
const history = useHistoryStore()
const notifications = useNotificationStore()
const open = ref(false)
const expandedRepo = ref<string | null>(null)
const addingCustom = ref(false)
const customForm = reactive({ repoPath: '', branch: '', platform: 'LOCAL' as 'LOCAL' | 'GITLAB' })

function isActive(repoPath: string | null, branch: string | null) {
  return context.selectedRepo === repoPath && (repoPath == null || context.selectedBranch === branch)
}

async function choose(repoPath: string | null, branch: string | null) {
  context.select(repoPath, branch)
  open.value = false
  // 刷新依赖上下文的两个数据源（幂等，未加载过的 store 内部静默失败也无妨）
  await Promise.allSettled([
    reviews.fetchQueue({ page: 1, size: 20, status: '', conclusion: '' }, true),
    history.fetchRecords(1),
  ])
}

function toggleRepo(repoPath: string) {
  expandedRepo.value = expandedRepo.value === repoPath ? null : repoPath
}

/** 从 GitLab 勾取远程分支（分仓降级，失败不影响存量） */
async function refreshRemote() {
  await context.fetchContexts(true)
  const failed = context.remoteRepos.filter((r) => r.remoteFetched === false).length
  if (failed > 0) notifications.show(`${failed} 个仓库分支勾取失败（已保留已有分支）`, 'error')
  else notifications.show('远程分支已刷新', 'success')
}

function submitCustom() {
  if (!customForm.repoPath.trim()) { notifications.show('请输入仓库/项目名', 'error'); return }
  context.addCustom(customForm.repoPath, customForm.branch.trim() || null, customForm.platform)
  notifications.show(`已添加${customForm.platform === 'LOCAL' ? '本地' : '远程'}分支：${customForm.repoPath}${customForm.branch ? ' : ' + customForm.branch : ''}`, 'success')
  customForm.repoPath = ''; customForm.branch = ''
  addingCustom.value = false
}

onMounted(() => { void context.fetchContexts() })
</script>

<template>
  <div class="relative">
    <button
      type="button"
      class="flex max-w-[300px] items-center gap-2 rounded-full border px-3.5 py-2 transition"
      :class="context.selectedRepo ? 'border-sky-200/35 bg-sky-300/10' : 'border-white/10 bg-white/5 hover:border-white/20'"
      :title="context.selectedRepo ? `当前上下文：${context.selectedRepo} : ${context.selectedBranch ?? '全部分支'}` : '全部仓库（点击切换上下文）'"
      @click="open = !open"
    >
      <svg width="13" height="13" viewBox="0 0 16 16" fill="none" class="shrink-0"><circle cx="5" cy="4" r="2.2" stroke="currentColor" stroke-width="1.4" class="text-sky-200/70"/><circle cx="5" cy="12" r="2.2" stroke="currentColor" stroke-width="1.4" class="text-sky-200/70"/><circle cx="11" cy="7" r="2.2" stroke="currentColor" stroke-width="1.4" class="text-sky-200/70"/><path d="M5 6.2v3.6M5 12c0-3 6-1 6-2.8" stroke="currentColor" stroke-width="1.4" class="text-sky-200/70"/></svg>
      <span class="truncate font-mono text-[12px]" :class="context.selectedRepo ? 'text-sky-100' : 'text-white/55'">{{ context.label }}</span>
      <span class="text-[9px] text-white/30 transition" :class="{ 'rotate-180': open }">▾</span>
    </button>

    <transition name="glass-dropdown">
      <section v-if="open" class="liquid-glass-strong absolute left-0 top-full z-50 mt-2 max-h-[440px] w-[320px] overflow-y-auto rounded-2xl p-2 no-scrollbar">
        <!-- 工具行：刷新远程分支 / 添加自定义分支 -->
        <div class="mb-1 flex items-center justify-between border-b border-white/[0.07] px-2 pb-2 pt-1">
          <span class="text-[10px] text-white/30">上下文</span>
          <div class="flex items-center gap-2">
            <button type="button" class="text-[11px] text-white/40 transition hover:text-sky-200 disabled:opacity-40" :disabled="context.refreshing" :title="context.refreshing ? '勾取中…' : '从 GitLab 勾取远程仓库全量分支'" @click="refreshRemote">{{ context.refreshing ? '⟳ 勾取中…' : '⟳ 勾取远程' }}</button>
            <button type="button" class="text-[11px] text-sky-200/70 transition hover:text-sky-100" @click="addingCustom = !addingCustom">＋ 添加分支</button>
          </div>
        </div>

        <!-- 添加自定义分支表单 -->
        <div v-if="addingCustom" class="mb-2 space-y-2 rounded-xl border border-white/10 bg-white/[0.03] p-2.5">
          <div class="flex gap-1.5">
            <button type="button" class="flex-1 rounded-lg border px-2 py-1 text-[10px] transition" :class="customForm.platform === 'LOCAL' ? 'border-sky-200/40 bg-sky-300/10 text-sky-100' : 'border-white/10 text-white/40'" @click="customForm.platform = 'LOCAL'">本地分支</button>
            <button type="button" class="flex-1 rounded-lg border px-2 py-1 text-[10px] transition" :class="customForm.platform === 'GITLAB' ? 'border-sky-200/40 bg-sky-300/10 text-sky-100' : 'border-white/10 text-white/40'" @click="customForm.platform = 'GITLAB'">远程分支</button>
          </div>
          <input v-model.trim="customForm.repoPath" class="form-control !py-1.5 font-mono text-[11px]" placeholder="项目名，如 my-project" />
          <input v-model.trim="customForm.branch" class="form-control !py-1.5 font-mono text-[11px]" placeholder="分支名（可空=整仓），如 main" />
          <div class="flex justify-end gap-2">
            <button type="button" class="text-[10px] text-white/35 hover:text-white" @click="addingCustom = false">取消</button>
            <button type="button" class="rounded-lg bg-sky-400/20 px-2.5 py-1 text-[10px] font-semibold text-sky-100 hover:bg-sky-400/30" @click="submitCustom">添加</button>
          </div>
        </div>

        <!-- 全部仓库 -->
        <button type="button" class="ctx-option" :class="{ 'is-active': isActive(null, null) }" @click="choose(null, null)">
          <span class="ctx-label">全部仓库</span><span class="ctx-count">默认</span>
        </button>

        <!-- 远程仓库分组 -->
        <template v-if="context.remoteRepos.length">
          <p class="ctx-group">远程仓库</p>
          <div v-for="repo in context.remoteRepos" :key="repo.repoPath">
            <div class="ctx-option-wrap">
              <button type="button" class="ctx-option flex-1" :class="{ 'is-active': isActive(repo.repoPath, null) }" @click="choose(repo.repoPath, null)">
                <span class="ctx-label">{{ repo.repoPath }}</span><span class="ctx-count">{{ repo.branches.reduce((s, b) => s + b.mrCount, 0) }} MR</span>
              </button>
              <button type="button" class="ctx-expand" :class="{ 'rotate-90': expandedRepo === repo.repoPath }" @click.stop="toggleRepo(repo.repoPath)">▸</button>
              <button v-if="context.isCustom(repo.repoPath)" type="button" class="ctx-expand !text-rose-300/60" title="删除自定义分支" @click.stop="context.removeCustom(repo.repoPath)">×</button>
            </div>
            <div v-if="expandedRepo === repo.repoPath" class="ml-4 border-l border-white/10 pl-2">
              <button v-for="b in repo.branches" :key="b.name" type="button" class="ctx-option" :class="{ 'is-active': isActive(repo.repoPath, b.name) }" @click="choose(repo.repoPath, b.name)">
                <span class="ctx-label font-mono text-[11px]">{{ b.name }}</span><span class="ctx-count">{{ b.mrCount }}</span>
              </button>
            </div>
          </div>
        </template>

        <!-- 本地仓库分组 -->
        <template v-if="context.localRepos.length">
          <p class="ctx-group">本地仓库</p>
          <div v-for="repo in context.localRepos" :key="repo.repoPath">
            <div class="ctx-option-wrap">
              <button type="button" class="ctx-option flex-1" :class="{ 'is-active': isActive(repo.repoPath, null) }" @click="choose(repo.repoPath, null)">
                <span class="ctx-label">{{ repo.repoPath }}</span><span class="ctx-count">{{ repo.branches.reduce((s, b) => s + b.mrCount, 0) }} MR</span>
              </button>
              <button type="button" class="ctx-expand" :class="{ 'rotate-90': expandedRepo === repo.repoPath }" @click.stop="toggleRepo(repo.repoPath)">▸</button>
              <button v-if="context.isCustom(repo.repoPath)" type="button" class="ctx-expand !text-rose-300/60" title="删除自定义分支" @click.stop="context.removeCustom(repo.repoPath)">×</button>
            </div>
            <div v-if="expandedRepo === repo.repoPath" class="ml-4 border-l border-white/10 pl-2">
              <button v-for="b in repo.branches" :key="b.name" type="button" class="ctx-option" :class="{ 'is-active': isActive(repo.repoPath, b.name) }" @click="choose(repo.repoPath, b.name)">
                <span class="ctx-label font-mono text-[11px]">{{ b.name }}</span><span class="ctx-count">{{ b.mrCount }}</span>
              </button>
            </div>
          </div>
        </template>

        <div v-if="!context.repos.length && !context.loading" class="px-3 py-5 text-center text-xs text-white/30">暂无审查记录，触发一次审查后出现仓库</div>
      </section>
    </transition>

    <!-- 点击空白处关闭 -->
    <div v-if="open" class="fixed inset-0 z-40" @click="open = false"></div>
  </div>
</template>

<style scoped>
.ctx-group { padding: 8px 10px 4px; font-size: 10px; text-transform: uppercase; letter-spacing: .16em; color: rgba(255,255,255,.25); }
.ctx-option-wrap { display: flex; align-items: center; }
.ctx-option { display: flex; width: 100%; align-items: center; justify-content: space-between; gap: 8px; border-radius: 11px; padding: 8px 10px; color: rgba(255,255,255,.6); transition: background .18s ease, color .18s ease; text-align: left; }
.ctx-option:hover { color: #fff; background: rgba(177,226,255,.09); }
.ctx-option.is-active { color: #d9f2ff; background: linear-gradient(90deg, rgba(147,129,255,.14), rgba(177,226,255,.08)); }
.ctx-label { min-width: 0; flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-size: 12px; }
.ctx-count { flex-shrink: 0; font-size: 10px; color: rgba(255,255,255,.28); }
.ctx-expand { padding: 4px 6px; color: rgba(255,255,255,.3); font-size: 10px; transition: transform .2s ease; }
.ctx-expand:hover { color: #fff; }
</style>
