<script setup lang="ts">
// Provider 运行时配置弹窗：直接改 base-url / model / API Key / 采样参数
// 安全：key 只用于写入新值，永不回显明文；留空 = 不修改；含 **** 拦截；敏感操作二次确认
import { computed, reactive, ref, watch } from 'vue'
import { updateProvider, resetProvider } from '../../api/agent'
import type { ProviderConfig } from '../../types/agent'
import { getErrorMessage } from '../../types/api'
import { useNotificationStore } from '../../stores/notification'
import { useAgentStore } from '../../stores/agent'
import BaseModal from '../common/BaseModal.vue'

const props = defineProps<{ modelValue: boolean; config: ProviderConfig | null }>()
const emit = defineEmits<{ 'update:modelValue': [boolean]; saved: [] }>()

const notifications = useNotificationStore()
const agentStore = useAgentStore()
const saving = ref(false)
const resetting = ref(false)
const form = reactive({ baseUrl: '', model: '', apiKey: '', maxTokens: 4096, temperature: 0.2 })

const maskedKey = computed(() => props.config?.apiKeyMasked || '')
const title = computed(() => `配置 ${props.config?.provider ?? ''}`)

watch(
  () => [props.modelValue, props.config] as const,
  ([open, config]) => {
    if (!open || !config) return
    // 打开时回填非敏感字段；key 永远不回填（placeholder 显示掩码）
    Object.assign(form, {
      baseUrl: config.baseUrl,
      model: config.model,
      apiKey: '',
      maxTokens: config.maxTokens,
      temperature: config.temperature,
    })
  },
  { immediate: true },
)

async function save() {
  if (!props.config) return
  if (form.apiKey.includes('****')) {
    notifications.show('请勿把掩码当作 API Key 提交；不修改请留空', 'error')
    return
  }
  saving.value = true
  try {
    await updateProvider(props.config.provider, {
      baseUrl: form.baseUrl || undefined,
      model: form.model || undefined,
      apiKey: form.apiKey || undefined,
      maxTokens: form.maxTokens || undefined,
      temperature: form.temperature,
    })
    notifications.show('配置已保存并即时生效（无需重启）', 'success')
    emit('saved')
    emit('update:modelValue', false)
    // 配置影响 agent 可用状态，刷新列表
    void agentStore.fetchAgents()
  } catch (cause) {
    notifications.show(getErrorMessage(cause), 'error')
  } finally {
    saving.value = false
  }
}

async function reset() {
  if (!props.config) return
  resetting.value = true
  try {
    await resetProvider(props.config.provider)
    notifications.show('已恢复为配置文件默认值', 'success')
    emit('saved')
    emit('update:modelValue', false)
    void agentStore.fetchAgents()
  } catch (cause) {
    notifications.show(getErrorMessage(cause), 'error')
  } finally {
    resetting.value = false
  }
}
</script>

<template>
  <BaseModal :model-value="modelValue" :title="title" @update:model-value="emit('update:modelValue', $event)">
    <div class="space-y-4">
      <label class="form-label">
        Base URL
        <input v-model.trim="form.baseUrl" class="form-control mt-2 font-mono text-xs" placeholder="https://api.deepseek.com/v1" />
      </label>
      <div class="grid grid-cols-2 gap-3">
        <label class="form-label">
          模型
          <input v-model.trim="form.model" class="form-control mt-2 font-mono text-xs" placeholder="deepseek-v4-flash" />
        </label>
        <label class="form-label">
          最大输出 Token
          <input v-model.number="form.maxTokens" type="number" min="256" max="32768" class="form-control mt-2 font-mono text-xs" />
        </label>
      </div>
      <label class="form-label">
        温度（0~1，越低越确定）
        <input v-model.number="form.temperature" type="number" min="0" max="1" step="0.1" class="form-control mt-2 font-mono text-xs" />
      </label>
      <label class="form-label">
        API Key
        <input
          v-model.trim="form.apiKey"
          type="password"
          autocomplete="new-password"
          class="form-control mt-2 font-mono text-xs"
          :placeholder="maskedKey ? `当前：${maskedKey}（留空不修改）` : 'sk-...（粘贴新 Key）'"
        />
      </label>

      <div class="rounded-xl border border-amber-300/20 bg-amber-300/[0.06] px-3.5 py-3 text-[11px] leading-5 text-amber-100/70">
        <p class="font-semibold text-amber-200">安全说明</p>
        <p class="mt-1">Key 将以 AES-GCM 加密后存储，接口永不回传明文；本系统暂无登录鉴权，请勿把实例暴露到公网，否则任何人可修改配置并把代码内容导向恶意端点。</p>
      </div>

      <div class="flex items-center justify-between gap-3">
        <button type="button" class="text-[11px] text-white/35 underline-offset-2 hover:text-rose-300 hover:underline" :disabled="resetting" @click="reset">
          {{ resetting ? '恢复中…' : '恢复出厂配置' }}
        </button>
        <div class="flex gap-3">
          <button type="button" class="secondary-button px-4 py-2 text-sm" @click="emit('update:modelValue', false)">取消</button>
          <button type="button" class="primary-button px-4 py-2 text-sm" :disabled="saving" @click="save">{{ saving ? '保存中…' : '保存并生效' }}</button>
        </div>
      </div>
    </div>
  </BaseModal>
</template>
