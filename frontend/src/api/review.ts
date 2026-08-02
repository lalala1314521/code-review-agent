import request from './request'
import type { PageResult } from '../types/api'
import type { ReviewDetailData, ReviewQuery, ReviewRecord } from '../types/review'

export function getReviews(params: ReviewQuery = {}): Promise<PageResult<ReviewRecord>> {
  return request.get('/reviews', { params })
}

export function getReview(id: number): Promise<ReviewRecord> {
  return request.get(`/reviews/${id}`)
}

export function getReviewFindings(id: number): Promise<ReviewDetailData> {
  return request.get(`/reviews/${id}/findings`)
}

// ===== 本地文件 MR 审查（.diff/.patch 直审；源码文件自动包装为 new-file diff）=====
export interface LocalReviewRequest {
  fileName: string
  content: string
  title?: string
  /** 所属项目（repoPath），默认 local-project——同一项目的多次审查归入同一仓库 */
  project?: string
  /** 所属分支（sourceBranch），默认 main */
  branch?: string
}

export function createLocalReview(payload: LocalReviewRequest): Promise<{ recordId: number }> {
  return request.post('/reviews/local', payload)
}
