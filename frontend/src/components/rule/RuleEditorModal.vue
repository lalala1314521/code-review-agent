<script setup lang="ts">
import { reactive, ref, watch } from 'vue'
import type { ReviewRule, RuleRequest, RuleSeverity } from '../../types/rule'
import BaseModal from '../common/BaseModal.vue'
import GlassSelect from '../common/GlassSelect.vue'

const props = defineProps<{ modelValue: boolean; rule: ReviewRule | null; saving?: boolean }>()
const emit = defineEmits<{ 'update:modelValue': [value: boolean]; save: [payload: RuleRequest, id?: number] }>()
const form = reactive<RuleRequest>({ ruleId: '', name: '', description: '', severity: 'WARNING', language: '', paramsJson: '{}' })
const validationError = ref('')
const severityOptions: Array<{ label: string; value: RuleSeverity }> = [
  { label: 'ERROR · 阻塞级错误', value: 'ERROR' },
  { label: 'WARNING · 建议修复', value: 'WARNING' },
  { label: 'INFO · 优化建议', value: 'INFO' },
]

watch(() => [props.modelValue, props.rule] as const, () => {
  if (!props.modelValue) return
  form.ruleId = props.rule?.ruleId ?? ''
  form.name = props.rule?.name ?? ''
  form.description = props.rule?.description ?? ''
  form.severity = props.rule?.severity ?? 'WARNING'
  form.language = props.rule?.language ?? ''
  form.paramsJson = props.rule?.paramsJson ?? '{}'
  validationError.value = ''
}, { immediate: true })

function submit() {
  validationError.value = ''
  if (!form.ruleId.trim() || !form.name.trim()) { validationError.value = '规则 ID 和名称不能为空。'; return }
  if (!/^[a-z][a-z0-9_\-]*$/i.test(form.ruleId.trim())) { validationError.value = '规则 ID 只能包含字母、数字、下划线和短横线。'; return }
  const params = form.paramsJson?.trim() || '{}'
  try { JSON.parse(params) } catch { validationError.value = '规则参数必须是合法 JSON。'; return }
  emit('save', {
    ruleId: form.ruleId.trim(), name: form.name.trim(), description: form.description?.trim() || null,
    severity: form.severity, language: form.language?.trim() || null, paramsJson: params,
  }, props.rule?.id)
}
</script>

<template>
  <BaseModal :model-value="modelValue" :title="rule ? '编辑审查规则' : '新建审查规则'" @update:model-value="emit('update:modelValue', $event)">
    <form class="space-y-4" @submit.prevent="submit">
      <div class="grid gap-4 sm:grid-cols-2"><label class="form-label">规则 ID<input v-model="form.ruleId" class="form-control mt-2" :disabled="Boolean(rule)" placeholder="custom_rule_id" /></label><label class="form-label">规则名称<input v-model="form.name" class="form-control mt-2" :disabled="rule?.ruleType === 'BUILTIN'" placeholder="规则显示名称" /></label></div>
      <label class="form-label">规则说明<textarea v-model="form.description" rows="3" class="form-control mt-2 resize-none" :disabled="rule?.ruleType === 'BUILTIN'" placeholder="说明该规则检查什么问题" /></label>
      <div class="grid gap-4 sm:grid-cols-2"><label class="form-label">严重度<GlassSelect v-model="form.severity" class="mt-2" :options="severityOptions" :disabled="rule?.ruleType === 'BUILTIN'" /></label><label class="form-label">适用语言<input v-model="form.language" class="form-control mt-2" :disabled="rule?.ruleType === 'BUILTIN'" placeholder="留空表示全部语言" /></label></div>
      <label class="form-label">规则参数 JSON<textarea v-model="form.paramsJson" rows="6" class="form-control mt-2 resize-y font-mono text-xs" spellcheck="false" /></label>
      <p v-if="rule?.ruleType === 'BUILTIN'" class="rounded-xl border border-amber-300/15 bg-amber-300/[0.06] px-3 py-2 text-xs text-amber-100/60">内置规则只允许修改参数和启用状态，语义字段已锁定。</p>
      <p v-if="validationError" class="text-xs text-rose-300">{{ validationError }}</p>
      <div class="flex justify-end gap-3 pt-2"><button type="button" class="secondary-button px-4 py-2 text-sm" @click="emit('update:modelValue', false)">取消</button><button type="submit" class="primary-button px-4 py-2 text-sm" :disabled="saving">{{ saving ? '保存中…' : '保存规则' }}</button></div>
    </form>
  </BaseModal>
</template>

