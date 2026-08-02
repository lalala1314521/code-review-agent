import request from './request'
import type { PageResult } from '../types/api'
import type { HistoryQuery, HistoryRecord, HistoryStat } from '../types/history'

export function getHistory(params: HistoryQuery = {}): Promise<PageResult<HistoryRecord>> {
  return request.get('/history', { params })
}

export function getHistoryStats(days = 30): Promise<HistoryStat[]> {
  return request.get('/history/stats', { params: { days } })
}
