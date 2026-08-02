import request from './request'

// ===== 仓库/分支上下文（GET /api/v1/contexts，上下文切换器数据源）=====
export interface RepoBranch {
  name: string
  /** 该分支已审查的 MR 数；0 = 远端勾取到但本地暂无审查记录 */
  mrCount: number
}

export interface RepoContext {
  repoPath: string
  platform: string
  branches: RepoBranch[]
  /** refresh=true 时是否成功从 GitLab 勾取（false=勾取失败降级为存量） */
  remoteFetched?: boolean
}

export function getContexts(refresh = false): Promise<RepoContext[]> {
  return request.get('/contexts', { params: { refresh } })
}
