export interface PageResult<T> {
  page: number
  size: number
  total: number
  items: T[]
}

export interface ApiEnvelope<T> {
  code: number
  message: string
  data: T
  traceId?: string
}

export class ApiError extends Error {
  code?: number
  traceId?: string

  constructor(message: string, code?: number, traceId?: string) {
    super(message)
    this.name = 'ApiError'
    this.code = code
    this.traceId = traceId
  }
}

export function getErrorMessage(error: unknown): string {
  if (error instanceof Error) return error.message
  return '请求失败，请稍后重试'
}
