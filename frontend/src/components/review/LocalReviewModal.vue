<script setup lang="ts">
// 本地文件 MR 审查弹窗：上传文件（.diff/.patch 直审）或粘贴文本（文件名 + diff/源码）
// 归属设计：项目/分支默认取当前上下文，也可手动指定；触发后 emit('created', recordId)
import { reactive, ref, watch } from 'vue'
import { createLocalReview } from '../../api/review'
import { getErrorMessage } from '../../types/api'
import { useNotificationStore } from '../../stores/notification'
import { useContextStore } from '../../stores/context'
import BaseModal from '../common/BaseModal.vue'

const props = defineProps<{ modelValue: boolean }>()
const emit = defineEmits<{ 'update:modelValue': [boolean]; created: [number] }>()

const notifications = useNotificationStore()
const context = useContextStore()
const submitting = ref(false)
const fileInput = ref<HTMLInputElement | null>(null)
// 归属记忆：上次输入的项目/分支（同一项目反复审查不用重填）
const LAST_PROJECT_KEY = 'codereview:local-review-project'
const LAST_BRANCH_KEY = 'codereview:local-review-branch'
const form = reactive({ fileName: '', content: '', title: '', project: '', branch: '' })

// 打开时回填归属默认值：当前上下文 > 上次输入 > 内置默认
watch(() => props.modelValue, (open) => {
  if (!open) return
  form.project = context.selectedRepo ?? localStorage.getItem(LAST_PROJECT_KEY) ?? 'local-project'
  form.branch = (context.selectedRepo ? context.selectedBranch : null) ?? localStorage.getItem(LAST_BRANCH_KEY) ?? 'main'
})

function pickFile() { fileInput.value?.click() }

function onFileChange(event: Event) {
  const file = (event.target as HTMLInputElement).files?.[0]
  if (!file) return
  if (file.size > 500 * 1024) {
    notifications.show('文件过大（>500KB），请分段提交', 'error')
    return
  }
  form.fileName = file.name
  if (!form.title) form.title = `本地审查: ${file.name}`
  const reader = new FileReader()
  reader.onload = () => { form.content = String(reader.result ?? '') }
  reader.onerror = () => notifications.show('文件读取失败', 'error')
  reader.readAsText(file)
  // 允许重复选择同一文件
  ;(event.target as HTMLInputElement).value = ''
}

async function submit() {
  if (!form.fileName.trim()) { notifications.show('请填写文件名（用于语言识别）', 'error'); return }
  if (!form.content.trim()) { notifications.show('内容不能为空', 'error'); return }
  const project = form.project.trim() || 'local-project'
  const branch = form.branch.trim() || 'main'
  submitting.value = true
  try {
    const { recordId } = await createLocalReview({
      fileName: form.fileName.trim(),
      content: form.content,
      title: form.title.trim() || undefined,
      project,
      branch,
    })
    // 记住归属，下次免填
    localStorage.setItem(LAST_PROJECT_KEY, project)
    localStorage.setItem(LAST_BRANCH_KEY, branch)
    notifications.show('本地审查已触发，正在实时审查…', 'success')
    emit('created', recordId)
    emit('update:modelValue', false)
    form.fileName = ''; form.content = ''; form.title = ''
  } catch (cause) {
    notifications.show(getErrorMessage(cause), 'error')
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <BaseModal :model-value="modelValue" title="本地文件审查" @update:model-value="emit('update:modelValue', $event)">
    <div class="space-y-4">
      <p class="text-[11px] leading-5 text-white/40">
        无需 GitLab：上传 <span class="font-mono text-sky-200/80">.diff / .patch</span> 直接审查，或上传源代码文件（.java/.ts/.py…），系统自动包装为新增文件 diff，走完整审查链路（规则引擎 + LLM + 裁决）。
      </p>

      <div class="flex items-center gap-3">
        <button type="button" class="secondary-button px-4 py-2 text-sm" @click="pickFile">选择文件…</button>
        <span class="truncate font-mono text-[11px] text-white/45">{{ form.fileName || '未选择文件（也可以直接粘贴内容）' }}</span>
        <input ref="fileInput" type="file" class="hidden" accept=".diff,.patch,.java,.kt,.go,.ts,.tsx,.js,.jsx,.py,.sql,.yml,.yaml,.xml,.vue,.txt" @change="onFileChange" />
      </div>

      <div class="grid grid-cols-2 gap-3">
        <label class="form-label">文件名<input v-model.trim="form.fileName" class="form-control mt-2 font-mono text-xs" placeholder="src/main/java/com/demo/UserDao.java" /></label>
        <label class="form-label">标题（可选）<input v-model.trim="form.title" class="form-control mt-2 text-xs" placeholder="本地审查: UserDao.java" /></label>
      </div>

      <div class="grid grid-cols-2 gap-3">
        <label class="form-label">所属项目<input v-model.trim="form.project" class="form-control mt-2 font-mono text-xs" placeholder="local-project" /></label>
        <label class="form-label">所属分支<input v-model.trim="form.branch" class="form-control mt-2 font-mono text-xs" placeholder="main" /></label>
      </div>
      <p class="-mt-2 text-[10px] leading-4 text-white/30">同一项目+分支的多次审查会归组到一起，可用顶部上下文切换器按项目/分支筛查。</p>

      <label class="form-label">
        内容（diff 原文或源代码全文）
        <textarea v-model="form.content" rows="10" class="form-control mt-2 resize-y font-mono text-[11px] leading-4" placeholder="粘贴 unified diff（@@ 开头）或源代码全文…"></textarea>
      </label>
      <p class="text-right font-mono text-[10px] text-white/25">{{ form.content.length.toLocaleString() }} 字符</p>

      <div class="flex justify-end gap-3">
        <button type="button" class="secondary-button px-4 py-2 text-sm" @click="emit('update:modelValue', false)">取消</button>
        <button type="button" class="primary-button px-4 py-2 text-sm" :disabled="submitting" @click="submit">{{ submitting ? '触发中…' : '开始审查' }}</button>
      </div>
    </div>
  </BaseModal>
</template>
