<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref } from 'vue'

export interface GlassSelectOption {
  label: string
  value: string
  description?: string
  disabled?: boolean
}

const props = withDefaults(defineProps<{ modelValue: string; options: GlassSelectOption[]; placeholder?: string; disabled?: boolean }>(), {
  placeholder: '请选择', disabled: false,
})
const emit = defineEmits<{ 'update:modelValue': [value: string]; change: [value: string] }>()
const trigger = ref<HTMLElement | null>(null)
const panel = ref<HTMLElement | null>(null)
const open = ref(false)
const activeIndex = ref(0)
const panelStyle = ref<Record<string, string>>({})
const selected = computed(() => props.options.find((item) => item.value === props.modelValue))

async function toggle() {
  if (props.disabled) return
  open.value = !open.value
  if (open.value) {
    activeIndex.value = Math.max(0, props.options.findIndex((item) => item.value === props.modelValue))
    await nextTick()
    positionPanel()
    document.addEventListener('pointerdown', onOutside, true)
    window.addEventListener('resize', positionPanel)
    window.addEventListener('scroll', positionPanel, true)
  } else removeListeners()
}

function positionPanel() {
  if (!trigger.value) return
  const rect = trigger.value.getBoundingClientRect()
  const estimatedHeight = Math.min(320, props.options.length * 54 + 16)
  const openUp = window.innerHeight - rect.bottom < estimatedHeight && rect.top > estimatedHeight
  panelStyle.value = {
    left: `${rect.left}px`, width: `${Math.max(rect.width, 190)}px`,
    top: openUp ? 'auto' : `${rect.bottom + 8}px`,
    bottom: openUp ? `${window.innerHeight - rect.top + 8}px` : 'auto',
    transformOrigin: openUp ? 'bottom center' : 'top center',
  }
}

function choose(option: GlassSelectOption) {
  if (option.disabled) return
  emit('update:modelValue', option.value)
  emit('change', option.value)
  close()
}

function onKeydown(event: KeyboardEvent) {
  if (!open.value && ['Enter', ' ', 'ArrowDown'].includes(event.key)) { event.preventDefault(); void toggle(); return }
  if (!open.value) return
  if (event.key === 'Escape') { event.preventDefault(); close(); trigger.value?.focus(); return }
  if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
    event.preventDefault()
    const direction = event.key === 'ArrowDown' ? 1 : -1
    let next = activeIndex.value
    do { next = (next + direction + props.options.length) % props.options.length } while (props.options[next]?.disabled)
    activeIndex.value = next
  }
  if (event.key === 'Enter') { event.preventDefault(); const option = props.options[activeIndex.value]; if (option) choose(option) }
}

function onOutside(event: Event) {
  const target = event.target as Node
  if (!trigger.value?.contains(target) && !panel.value?.contains(target)) close()
}
function close() { open.value = false; removeListeners() }
function removeListeners() {
  document.removeEventListener('pointerdown', onOutside, true)
  window.removeEventListener('resize', positionPanel)
  window.removeEventListener('scroll', positionPanel, true)
}
onBeforeUnmount(removeListeners)
</script>

<template>
  <button ref="trigger" type="button" class="glass-select-trigger" :class="{ 'is-open': open }" :disabled="disabled" role="combobox" :aria-expanded="open" aria-haspopup="listbox" @click="toggle" @keydown="onKeydown">
    <span class="min-w-0 flex-1 truncate text-left">{{ selected?.label || placeholder }}</span>
    <svg class="glass-select-chevron" :class="{ 'rotate-180': open }" width="14" height="14" viewBox="0 0 14 14" fill="none"><path d="M3 5L7 9L11 5" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"/></svg>
  </button>
  <teleport to="body">
    <transition name="glass-dropdown">
      <div v-if="open" ref="panel" class="glass-select-panel" :style="panelStyle" role="listbox" tabindex="-1" @keydown="onKeydown">
        <button v-for="(option, index) in options" :key="option.value" type="button" class="glass-select-option" :class="{ 'is-active': index === activeIndex, 'is-selected': option.value === modelValue }" :disabled="option.disabled" role="option" :aria-selected="option.value === modelValue" :style="{ '--option-index': index }" @mouseenter="activeIndex = index" @click="choose(option)">
          <span class="min-w-0 flex-1"><span class="block truncate text-sm font-medium">{{ option.label }}</span><span v-if="option.description" class="mt-0.5 block truncate text-[10px] text-white/35">{{ option.description }}</span></span><span v-if="option.value === modelValue" class="text-sky-200">✓</span>
        </button>
      </div>
    </transition>
  </teleport>
</template>
