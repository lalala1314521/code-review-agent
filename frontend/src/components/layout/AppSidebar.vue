<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'

const route = useRoute()
const navItems = [
  { path: '/dashboard', label: '仪表盘', icon: 'dashboard' },
  { path: '/reviews', label: 'MR 队列', icon: 'mr' },
  { path: '/rules', label: '审查规则', icon: 'rule' },
  { path: '/history', label: '历史记录', icon: 'history' },
]
const activePath = computed(() => route.path)
function active(path: string) { return path === '/reviews' ? activePath.value.startsWith('/reviews') : activePath.value === path }
</script>

<template>
  <nav class="app-sidebar-glass relative z-10 flex w-16 shrink-0 flex-col items-center gap-4 px-2 py-5 sm:w-20 sm:px-4" aria-label="主导航">
    <router-link v-for="item in navItems" :key="item.path" :to="item.path" :title="item.label" class="group relative flex h-11 w-11 items-center justify-center rounded-full transition" :class="active(item.path) ? 'grad-accent text-slate-950' : 'border border-white/10 bg-white/5 text-white/60 hover:bg-white/10 hover:text-white'" :aria-label="item.label">
      <svg v-if="item.icon === 'dashboard'" width="20" height="20" viewBox="0 0 20 20" fill="currentColor"><rect x="3" y="3" width="6" height="6" rx="1.5"/><rect x="11" y="3" width="6" height="6" rx="1.5" opacity=".55"/><rect x="3" y="11" width="6" height="6" rx="1.5" opacity=".55"/><rect x="11" y="11" width="6" height="6" rx="1.5"/></svg>
      <svg v-else-if="item.icon === 'mr'" width="20" height="20" viewBox="0 0 20 20" fill="none"><circle cx="5" cy="4" r="2" stroke="currentColor" stroke-width="1.6"/><circle cx="5" cy="16" r="2" stroke="currentColor" stroke-width="1.6"/><circle cx="15" cy="16" r="2" stroke="currentColor" stroke-width="1.6"/><path d="M5 6V14M5 11C5 8 9 8 12 10C13.5 11 13 14 15 14" stroke="currentColor" stroke-width="1.6" stroke-linecap="round"/></svg>
      <svg v-else-if="item.icon === 'rule'" width="20" height="20" viewBox="0 0 20 20" fill="none"><path d="M10 2L17 5V10C17 13.5 14 16.5 10 18C6 16.5 3 13.5 3 10V5L10 2Z" stroke="currentColor" stroke-width="1.6" stroke-linejoin="round"/><path d="M7 10L9 12L13 8" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"/></svg>
      <svg v-else width="20" height="20" viewBox="0 0 20 20" fill="none"><circle cx="10" cy="10" r="7" stroke="currentColor" stroke-width="1.6"/><path d="M10 6V10L13 12" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"/></svg>
      <span class="pointer-events-none absolute left-14 z-30 hidden whitespace-nowrap rounded-lg border border-white/10 bg-slate-950/95 px-2.5 py-1.5 text-xs text-white/80 shadow-xl group-hover:block">{{ item.label }}</span>
    </router-link>
  </nav>
</template>

