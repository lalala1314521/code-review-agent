import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { getContexts } from '../api/context'
import type { RepoContext } from '../api/context'

// ===== 仓库/分支上下文（全局：影响 MR 队列、历史查询范围）=====
// selectedRepo = null → 全部仓库；selectedBranch = null → 该仓库全部分支
// 数据两层：服务端存量聚合 + 本地自定义分支（localStorage）
const CUSTOM_KEY = 'codereview:custom-contexts'

function loadCustom(): RepoContext[] {
  try {
    const raw = localStorage.getItem(CUSTOM_KEY)
    return raw ? JSON.parse(raw) as RepoContext[] : []
  } catch { return [] }
}

export const useContextStore = defineStore('context', () => {
  const serverRepos = ref<RepoContext[]>([])
  const customContexts = ref<RepoContext[]>(loadCustom())
  const loading = ref(false)
  const refreshing = ref(false)
  const selectedRepo = ref<string | null>(null)
  const selectedBranch = ref<string | null>(null)

  /** 服务端存量 + 本地自定义合并（同 repoPath 合并分支去重，自定义在后） */
  const repos = computed<RepoContext[]>(() => {
    const merged = new Map<string, RepoContext>()
    for (const r of serverRepos.value) {
      merged.set(r.repoPath, { ...r, branches: [...r.branches] })
    }
    for (const c of customContexts.value) {
      const existing = merged.get(c.repoPath)
      if (existing) {
        for (const b of c.branches) {
          if (!existing.branches.some((x) => x.name === b.name)) existing.branches.push(b)
        }
      } else {
        merged.set(c.repoPath, { ...c, branches: [...c.branches] })
      }
    }
    return [...merged.values()]
  })

  /** 远程仓库（GITLAB/GITHUB 等平台） */
  const remoteRepos = computed(() => repos.value.filter((r) => r.platform !== 'LOCAL'))
  /** 本地仓库（本地文件审查 + 手动添加的本地分支） */
  const localRepos = computed(() => repos.value.filter((r) => r.platform === 'LOCAL'))
  /** 当前选中仓库的分支列表 */
  const currentBranches = computed(() => repos.value.find((r) => r.repoPath === selectedRepo.value)?.branches ?? [])

  /** 展示标签：全部仓库 / repo : * / repo : branch */
  const label = computed(() => {
    if (!selectedRepo.value) return '全部仓库'
    const short = selectedRepo.value.length > 24 ? '…' + selectedRepo.value.slice(-23) : selectedRepo.value
    return `${short} : ${selectedBranch.value ?? '*'}`
  })

  /** 是否自定义上下文（仅自定义的可删除） */
  function isCustom(repoPath: string) {
    return customContexts.value.some((c) => c.repoPath === repoPath)
  }

  async function fetchContexts(refresh = false) {
    if (refresh) refreshing.value = true
    else loading.value = true
    try {
      serverRepos.value = await getContexts(refresh)
    } catch {
      // 失败保留现状
    } finally {
      loading.value = false
      refreshing.value = false
    }
  }

  /** 切换上下文（repoPath 为 null 表示全部仓库；branch 为 null 表示该仓库全部分支） */
  function select(repoPath: string | null, branch: string | null) {
    selectedRepo.value = repoPath
    selectedBranch.value = repoPath == null ? null : branch
  }

  /** 添加自定义上下文（手动添加本地/远程分支；branch 为 null 表示整仓） */
  function addCustom(repoPath: string, branch: string | null, platform: 'LOCAL' | 'GITLAB') {
    const trimmed = repoPath.trim()
    if (!trimmed) return
    const existing = customContexts.value.find((c) => c.repoPath === trimmed)
    if (existing) {
      if (branch && !existing.branches.some((b) => b.name === branch)) {
        existing.branches.push({ name: branch.trim(), mrCount: 0 })
      }
    } else {
      customContexts.value.push({
        repoPath: trimmed,
        platform,
        branches: branch ? [{ name: branch.trim(), mrCount: 0 }] : [],
      })
    }
    persistCustom()
  }

  /** 删除自定义上下文（若正在选中则同时切回"全部仓库"） */
  function removeCustom(repoPath: string) {
    customContexts.value = customContexts.value.filter((c) => c.repoPath !== repoPath)
    if (selectedRepo.value === repoPath) select(null, null)
    persistCustom()
  }

  function persistCustom() {
    localStorage.setItem(CUSTOM_KEY, JSON.stringify(customContexts.value))
  }

  return {
    repos, loading, refreshing, selectedRepo, selectedBranch,
    remoteRepos, localRepos, currentBranches, label,
    fetchContexts, select, addCustom, removeCustom, isCustom,
  }
})
