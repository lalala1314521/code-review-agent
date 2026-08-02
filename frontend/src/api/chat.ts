import request from './request'
import type { ReviewChatRequest, ReviewChatResponse } from '../types/chat'

export function sendReviewChat(reviewId: number, payload: ReviewChatRequest): Promise<ReviewChatResponse> {
  return request.post(`/reviews/${reviewId}/chat`, payload)
}
