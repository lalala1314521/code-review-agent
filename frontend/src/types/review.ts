export type ReviewStatus = 'PENDING' | 'REVIEWING' | 'DONE' | 'FAILED'
export type ReviewConclusion = 'APPROVE' | 'NEEDS_FIX' | 'BLOCK'
export type FindingSeverity = 'ERROR' | 'WARNING' | 'INFO'

export interface ReviewRecord {
  id: number
  traceId: string
  platform: string
  projectId: number
  repoPath: string
  mrIid: number
  commitSha: string
  sourceBranch: string | null
  targetBranch: string | null
  title: string | null
  authorUsername: string | null
  status: ReviewStatus
  conclusion: ReviewConclusion | null
  confidence: number | null
  errorCount: number
  warningCount: number
  infoCount: number
  durationMs: number | null
  triggeredAt: string
  startedAt: string | null
  finishedAt: string | null
  createdAt?: string
  updatedAt?: string
}

export interface ReviewFinding {
  id: number
  reviewRecordId: number
  filePath: string
  lineNumber: number | null
  severity: FindingSeverity
  ruleId: string
  message: string
  suggestion: string | null
  source: string
  confidence: number
}

export interface ReviewDetailData {
  reviewRecord: ReviewRecord
  findings: ReviewFinding[]
}

export interface ReviewQuery {
  page?: number
  size?: number
  status?: ReviewStatus | ''
  conclusion?: ReviewConclusion | ''
  /** 上下文切换器：仓库路径精确匹配 */
  repoPath?: string
  /** 上下文切换器：源分支精确匹配 */
  branch?: string
}

export interface ProgressEvent {
  recordId: number
  stage: string
  message: string
  at: number
}
