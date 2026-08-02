<script setup lang="ts">
import AppHeader from './components/layout/AppHeader.vue'
import AppSidebar from './components/layout/AppSidebar.vue'
import ToastContainer from './components/common/ToastContainer.vue'
</script>

<template>
  <div class="relative flex h-full flex-col overflow-hidden bg-black">
    <div class="aurora aurora-purple"></div><div class="aurora aurora-blue"></div><div class="aurora aurora-amber"></div>
    <AppHeader />
    <!-- main 保留 overflow-y-auto 作小屏兜底；桌面端 page-shell height:100% 自身不溢出，不会触发整页滚动 -->
    <div class="relative z-10 flex min-h-0 flex-1"><AppSidebar /><main class="min-w-0 flex-1 overflow-y-auto"><router-view /></main></div>
    <ToastContainer />
  </div>
</template>
