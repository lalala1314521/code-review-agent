import axios from 'axios'
import { ApiError } from '../types/api'
import type { ApiEnvelope } from '../types/api'

const ADMIN_TOKEN_KEY = 'codereview:admin-token'

const request = axios.create({
  baseURL: '/api/v1',
  // LLM 聊天/审查类请求响应 4-30s 都正常，15s 会误杀长回答
  timeout: 60_000,
})

// 请求拦截器：携带管理员令牌（服务端配置 ADMIN_TOKEN 后，写操作需要）
request.interceptors.request.use((config) => {
  const token = localStorage.getItem(ADMIN_TOKEN_KEY)
  if (token) {
    config.headers['X-Admin-Token'] = token
  }
  return config
})

request.interceptors.response.use(
  (response) => {
    const body = response.data as any
    if (body && typeof body === 'object' && 'code' in body) {
      const envelope = body as ApiEnvelope<unknown>
      if (envelope.code === 0) return envelope.data
      return Promise.reject(new ApiError(envelope.message || '请求失败', envelope.code, envelope.traceId))
    }
    return body
  },
  async (error: unknown) => {
    if (axios.isAxiosError(error)) {
      // 401：提示输入一次 ADMIN_TOKEN，存 localStorage 并重放原请求
      if (error.response?.status === 401 && error.config && !(error.config as any).__tokenRetried) {
        const input = window.prompt('服务端已启用管理员令牌保护，请输入 X-Admin-Token：')
        if (input && input.trim()) {
          localStorage.setItem(ADMIN_TOKEN_KEY, input.trim())
          ;(error.config as any).__tokenRetried = true
          error.config.headers['X-Admin-Token'] = input.trim()
          return request(error.config)
        }
      }
      const body = error.response?.data as Partial<ApiEnvelope<unknown>> | undefined
      const message = body?.message || (error.code === 'ECONNABORTED' ? '请求超时，请稍后重试' : error.message)
      return Promise.reject(new ApiError(message, body?.code, body?.traceId))
    }
    return Promise.reject(error)
  },
)

export default request

