<script setup lang="ts">
import type { ReviewConclusion, ReviewStatus } from '../../types/review'
import GlassSelect from '../common/GlassSelect.vue'

const status = defineModel<ReviewStatus | ''>('status', { required: true })
const conclusion = defineModel<ReviewConclusion | ''>('conclusion', { required: true })
const emit = defineEmits<{ apply: []; clear: [] }>()
const statusOptions = [
  { label: '全部状态', value: '' }, { label: '待审查', value: 'PENDING' }, { label: '审查中', value: 'REVIEWING' }, { label: '已完成', value: 'DONE' }, { label: '失败', value: 'FAILED' },
]
const conclusionOptions = [
  { label: '全部结论', value: '' }, { label: '建议合并', value: 'APPROVE' }, { label: '需修复', value: 'NEEDS_FIX' }, { label: '阻塞', value: 'BLOCK' },
]
</script>

<template>
  <div class="grid gap-3 sm:grid-cols-2 lg:grid-cols-[1fr_1fr_auto_auto]">
    <GlassSelect v-model="status" :options="statusOptions" />
    <GlassSelect v-model="conclusion" :options="conclusionOptions" />
    <button type="button" class="primary-button px-4 py-2 text-sm" @click="emit('apply')">应用筛选</button>
    <button type="button" class="secondary-button px-4 py-2 text-sm" @click="emit('clear')">清空</button>
  </div>
</template>
