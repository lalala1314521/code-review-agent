import type { ReviewRecord } from './review'

export interface HistoryQuery {
  page?: number
  size?: number
  repo?: string
  /** 上下文切换器：源分支精确匹配 */
  branch?: string
  start?: string
  end?: string
}

export interface HistoryStat {
  statDate: string
  totalCount: number
  approveCount: number
  needsFixCount: number
  blockCount: number
  avgDurationMs: number
}

export type HistoryRecord = ReviewRecord
