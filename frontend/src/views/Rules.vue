<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useNotificationStore } from '../stores/notification'
import { useRuleStore } from '../stores/rule'
import type { ReviewRule, RuleRequest } from '../types/rule'
import { getErrorMessage } from '../types/api'
import ConfirmDialog from '../components/common/ConfirmDialog.vue'
import EmptyState from '../components/common/EmptyState.vue'
import ErrorState from '../components/common/ErrorState.vue'
import GlassSelect from '../components/common/GlassSelect.vue'
import LoadingState from '../components/common/LoadingState.vue'
import RuleCard from '../components/rule/RuleCard.vue'
import RuleEditorModal from '../components/rule/RuleEditorModal.vue'

const store = useRuleStore()
const notifications = useNotificationStore()
const route = useRoute()
const router = useRouter()
const editorOpen = ref(false)
const editingRule = ref<ReviewRule | null>(null)
const deleteTarget = ref<ReviewRule | null>(null)
const busyRuleId = ref<number | null>(null)
const languageOptions = computed(() => [{ label: '全部语言', value: '' }, ...store.languages.map((language) => ({ label: language, value: language }))])
const severityOptions = [
  { label: '全部严重度', value: 'all' }, { label: 'ERROR', value: 'ERROR' }, { label: 'WARNING', value: 'WARNING' }, { label: 'INFO', value: 'INFO' },
]
const enabledOptions = [
  { label: '全部状态', value: 'all' }, { label: '已启用', value: 'enabled' }, { label: '已停用', value: 'disabled' },
]

function openCreate() { editingRule.value = null; editorOpen.value = true }
function openEdit(rule: ReviewRule) { editingRule.value = rule; editorOpen.value = true }
async function save(payload: RuleRequest, id?: number) {
  try { await store.saveRule(payload, id); editorOpen.value = false; notifications.show(id ? '规则已更新' : '规则已创建', 'success'); if (route.query.create) await router.replace({ path: '/rules' }) }
  catch (cause) { notifications.show(getErrorMessage(cause), 'error') }
}
async function toggle(rule: ReviewRule) {
  busyRuleId.value = rule.id
  try { await store.toggleRule(rule); notifications.show(rule.enabled ? '规则已禁用' : '规则已启用', 'success') }
  catch (cause) { notifications.show(getErrorMessage(cause), 'error') }
  finally { busyRuleId.value = null }
}
async function confirmDelete() {
  if (!deleteTarget.value) return
  try { await store.removeRule(deleteTarget.value); notifications.show('自定义规则已删除', 'success'); deleteTarget.value = null }
  catch (cause) { notifications.show(getErrorMessage(cause), 'error') }
}
watch(() => route.query.create, (value) => { if (value === '1') openCreate() }, { immediate: true })
onMounted(() => { void store.fetchRules().catch(() => undefined) })
</script>

<template>
  <div class="page-shell">
    <header class="page-heading"><div><h1>审查规则</h1><p>管理规则能力、严重度、语言范围与动态参数。</p></div><button type="button" class="primary-button px-4 py-2.5 text-sm" @click="openCreate">＋ 新建规则</button></header>
    <section class="liquid-glass rounded-2xl p-4"><div class="grid gap-3 md:grid-cols-2 xl:grid-cols-[1.5fr_1fr_1fr_1fr_auto]"><input v-model.trim="store.searchTerm" class="form-control" placeholder="搜索名称或 Rule ID…" /><GlassSelect v-model="store.languageFilter" :options="languageOptions" /><GlassSelect v-model="store.severityFilter" :options="severityOptions" /><GlassSelect v-model="store.enabledFilter" :options="enabledOptions" /><button type="button" class="secondary-button px-4 py-2 text-sm" @click="store.clearFilters">清空</button></div></section>
    <LoadingState v-if="store.loading" />
    <ErrorState v-else-if="store.error" :message="store.error" @retry="store.fetchRules" />
    <EmptyState v-else-if="store.filteredRules.length === 0" title="没有匹配的规则" description="调整筛选条件，或创建一条新的自定义规则。" />
    <section v-else class="grid min-h-0 flex-1 content-start gap-4 overflow-y-auto no-scrollbar md:grid-cols-2 2xl:grid-cols-3"><RuleCard v-for="rule in store.filteredRules" :key="rule.id" :rule="rule" :busy="busyRuleId === rule.id" @edit="openEdit" @toggle="toggle" @remove="deleteTarget = $event" /></section>
    <RuleEditorModal v-model="editorOpen" :rule="editingRule" :saving="store.saving" @save="save" />
    <ConfirmDialog :model-value="Boolean(deleteTarget)" title="删除自定义规则" :message="`确认删除规则「${deleteTarget?.name ?? ''}」吗？该操作不可撤销。`" confirm-text="删除" danger @update:model-value="!$event && (deleteTarget = null)" @confirm="confirmDelete" />
  </div>
</template>
