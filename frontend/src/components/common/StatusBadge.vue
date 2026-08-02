<script setup lang="ts">
import { computed } from 'vue'
import type { ReviewConclusion, ReviewStatus } from '../../types/review'
import type { RuleSeverity } from '../../types/rule'

const props = defineProps<{ status?: ReviewStatus; conclusion?: ReviewConclusion | null; severity?: RuleSeverity }>()
const badge = computed(() => {
  if (props.severity) {
    return {
      text: props.severity,
      className: props.severity === 'ERROR' ? 'status-error' : props.severity === 'WARNING' ? 'status-warning' : 'status-info',
    }
  }
  if (props.status && props.status !== 'DONE') {
    const map = {
      PENDING: { text: '待审查', className: 'status-pending' },
      REVIEWING: { text: '审查中', className: 'status-reviewing' },
      FAILED: { text: '审查失败', className: 'status-error' },
    }
    return map[props.status]
  }
  const map = {
    APPROVE: { text: '建议合并', className: 'status-success' },
    NEEDS_FIX: { text: '需修复', className: 'status-warning' },
    BLOCK: { text: '阻塞', className: 'status-error' },
  }
  return props.conclusion ? map[props.conclusion] : { text: '已完成', className: 'status-neutral' }
})
</script>

<template><span class="status-badge" :class="badge.className">{{ badge.text }}</span></template>
