<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useAgentStore } from '../../stores/agent'
import { useNotificationStore } from '../../stores/notification'
import type { AgentProfile, CustomAgentForm, CustomAgentProfile, ProviderConfig } from '../../types/agent'
import { getErrorMessage } from '../../types/api'
import { getProviders } from '../../api/agent'
import BaseModal from '../common/BaseModal.vue'
import GlassSelect from '../common/GlassSelect.vue'
import AgentAvatar from './AgentAvatar.vue'
import ProviderConfigModal from './ProviderConfigModal.vue'

const store = useAgentStore()
const notifications = useNotificationStore()
const menuOpen = ref(false)
const editorOpen = ref(false)
const editingId = ref<string | null>(null)
const form = reactive<CustomAgentForm>({ name: '', provider: 'deepseek', instruction: '请重点解释风险原因，并给出可直接执行的修复步骤。', accent: 'purple' })
const providerOptions = computed(() => store.builtinProfiles.map((item) => ({ label: `${item.name} · ${item.model}`, value: item.provider, description: item.available ? '已配置，可用于对话和后续审查' : '未配置 API Key', disabled: !item.available })))
const accentOptions = [
  { label: '星云紫', value: 'purple' }, { label: '极光蓝', value: 'blue' }, { label: '暖橙', value: 'amber' }, { label: '翡翠绿', value: 'green' },
]

// Provider 配置弹窗状态
const configOpen = ref(false)
const configTarget = ref<ProviderConfig | null>(null)
const providerConfigs = ref<ProviderConfig[]>([])

async function openConfig(provider: string) {
  try {
    providerConfigs.value = await getProviders()
    configTarget.value = providerConfigs.value.find((item) => item.provider === provider) ?? null
    if (!configTarget.value) { notifications.show('未找到该 Provider 配置', 'error'); return }
    configOpen.value = true
  } catch (cause) { notifications.show(getErrorMessage(cause), 'error') }
}

function available(profile: AgentProfile) { return store.agents.find((item) => item.provider === profile.provider)?.available ?? false }
async function choose(profile: AgentProfile) {
  if (!available(profile)) return
  try { await store.selectAgent(profile); menuOpen.value = false; notifications.show(`已切换到 ${profile.name}`, 'success') }
  catch (cause) { notifications.show(getErrorMessage(cause), 'error') }
}
function openCreate() {
  editingId.value = null
  Object.assign(form, { name: '', provider: store.activeProvider || 'deepseek', instruction: '请重点解释风险原因，并给出可直接执行的修复步骤。', accent: 'purple' })
  editorOpen.value = true
}
function openEdit(profile: CustomAgentProfile) {
  editingId.value = profile.id
  Object.assign(form, { name: profile.name, provider: profile.provider, instruction: profile.instruction, accent: profile.accent })
  editorOpen.value = true
}
function saveCustom() {
  if (!form.name.trim()) { notifications.show('请输入自定义 Agent 名称', 'error'); return }
  if (editingId.value) store.updateCustomAgent(editingId.value, form)
  else store.createCustomAgent(form)
  editorOpen.value = false
  notifications.show(editingId.value ? '自定义 Agent 已更新' : '自定义 Agent 已创建', 'success')
}
function remove(profile: CustomAgentProfile) { store.removeCustomAgent(profile.id); notifications.show('自定义 Agent 已删除', 'success') }
onMounted(() => { if (store.agents.length === 0) void store.fetchAgents() })
</script>

<template>
  <div class="relative shrink-0">
    <button type="button" class="agent-switch-trigger" :title="`当前 Agent：${store.selectedAgent.name}`" @click="menuOpen = !menuOpen"><AgentAvatar :agent="store.selectedAgent" /><span class="agent-online-dot" :class="available(store.selectedAgent) ? 'bg-emerald-400' : 'bg-rose-400'"></span></button>
    <transition name="agent-menu">
      <section v-if="menuOpen" class="agent-switch-menu liquid-glass-strong">
        <header class="flex items-center justify-between border-b border-white/10 px-4 py-3"><div><p class="text-sm font-bold text-white">选择 Agent</p><p class="mt-0.5 text-[10px] text-white/35">默认 DeepSeek，可切换或创建个性化 Agent</p></div><button type="button" class="text-white/35 hover:text-white" @click="menuOpen = false">×</button></header>
        <div class="max-h-[360px] overflow-y-auto p-2 no-scrollbar">
          <p class="px-2 pb-1 pt-1 text-[10px] uppercase tracking-[.16em] text-white/25">内置 Provider</p>
          <div v-for="profile in store.builtinProfiles" :key="profile.id" class="agent-option-wrap" :class="{ 'is-selected': store.selectedAgent.id === profile.id }">
            <button type="button" class="agent-option !bg-transparent" :disabled="!profile.available || store.switching" @click="choose(profile)"><AgentAvatar :agent="profile" size="sm"/><span class="min-w-0 flex-1 text-left"><span class="flex items-center gap-2 text-sm font-semibold text-white">{{ profile.name }}<span v-if="profile.defaultAgent" class="rounded-full bg-violet-300/10 px-1.5 py-0.5 text-[9px] text-violet-200">默认</span></span><span class="mt-0.5 block truncate font-mono text-[10px] text-white/35">{{ profile.model }} · {{ profile.available ? '可用' : '未配置 API Key' }}</span></span><span v-if="store.selectedAgent.id === profile.id" class="text-sky-200">✓</span></button>
            <div class="flex gap-1 pr-2"><button type="button" class="agent-mini-action" :title="`配置 ${profile.provider}（Base URL / 模型 / API Key）`" @click="openConfig(profile.provider)">⚙</button></div>
          </div>
          <div class="my-2 border-t border-white/[0.07]"></div>
          <div class="flex items-center justify-between px-2 pb-1"><p class="text-[10px] uppercase tracking-[.16em] text-white/25">我的 Agent</p><button type="button" class="text-[10px] text-sky-200/70 hover:text-sky-100" @click="openCreate">＋ 新建</button></div>
          <div v-if="store.customProfiles.length === 0" class="px-3 py-5 text-center text-xs text-white/30">还没有自定义 Agent</div>
          <div v-for="profile in store.customProfiles" :key="profile.id" class="agent-option-wrap" :class="{ 'is-selected': store.selectedAgent.id === profile.id }"><button type="button" class="agent-option !bg-transparent" :disabled="!available(profile) || store.switching" @click="choose(profile)"><AgentAvatar :agent="profile" size="sm"/><span class="min-w-0 flex-1 text-left"><span class="block truncate text-sm font-semibold text-white">{{ profile.name }}</span><span class="mt-0.5 block truncate text-[10px] text-white/35">基于 {{ profile.provider }} · 自定义偏好</span></span></button><div class="flex gap-1 pr-2"><button type="button" class="agent-mini-action" title="编辑" @click="openEdit(profile)">✎</button><button type="button" class="agent-mini-action text-rose-300/70" title="删除" @click="remove(profile)">×</button></div></div>
        </div>
      </section>
    </transition>

    <BaseModal v-model="editorOpen" :title="editingId ? '编辑自定义 Agent' : '创建自定义 Agent'">
      <div class="space-y-4"><label class="form-label">Agent 名称<input v-model="form.name" class="form-control mt-2" maxlength="80" placeholder="例如：安全审查专家" /></label><label class="form-label">基础模型<GlassSelect v-model="form.provider" class="mt-2" :options="providerOptions" /></label><label class="form-label">高光主题<GlassSelect v-model="form.accent" class="mt-2" :options="accentOptions" /></label><label class="form-label">个性指令<textarea v-model="form.instruction" rows="5" maxlength="1200" class="form-control mt-2 resize-y" placeholder="说明希望 Agent 关注什么、如何组织回答…" /></label><p class="text-[11px] leading-5 text-white/35">自定义指令只影响审查结果的追问方式；自动代码审查仍使用标准化 Prompt，保证结构化 Findings 和 Verdict 稳定。</p><div class="flex justify-end gap-3"><button type="button" class="secondary-button px-4 py-2 text-sm" @click="editorOpen = false">取消</button><button type="button" class="primary-button px-4 py-2 text-sm" @click="saveCustom">保存 Agent</button></div></div>
    </BaseModal>
    <ProviderConfigModal v-model="configOpen" :config="configTarget" @saved="() => { /* 保存后刷新 agent 列表在弹窗内已处理 */ }" />
  </div>
</template>
