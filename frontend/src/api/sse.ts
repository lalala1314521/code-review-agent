import type { ProgressEvent } from '../types/review'

export const TERMINAL_STAGES = ['DONE', 'FAILED'] as const

export interface StreamHandlers {
  onEvent: (event: ProgressEvent) => void
  onTerminal?: (event: ProgressEvent) => void
  onError?: () => void
}

export interface StreamSubscription {
  close: () => void
}

export function subscribeReviewStream(recordId: number, handlers: StreamHandlers): StreamSubscription {
  const source = new EventSource(`/api/v1/reviews/${recordId}/stream`)
  let manuallyClosed = false
  let terminal = false

  source.addEventListener('stage', (event) => {
    try {
      const data = JSON.parse((event as MessageEvent).data) as ProgressEvent
      handlers.onEvent(data)
      if (TERMINAL_STAGES.includes(data.stage as (typeof TERMINAL_STAGES)[number])) {
        terminal = true
        source.close()
        handlers.onTerminal?.(data)
      }
    } catch {
      // 忽略单条非法事件，保留后续流。
    }
  })

  source.onerror = () => {
    source.close()
    if (!manuallyClosed && !terminal) handlers.onError?.()
  }

  return {
    close() {
      manuallyClosed = true
      source.close()
    },
  }
}
