import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  { path: '/', redirect: '/dashboard' },
  { path: '/dashboard', name: 'Dashboard', component: () => import('../views/Dashboard.vue') },
  { path: '/reviews', name: 'ReviewQueue', component: () => import('../views/ReviewQueue.vue') },
  { path: '/reviews/:id', name: 'ReviewDetail', component: () => import('../views/ReviewDetail.vue') },
  { path: '/rules', name: 'Rules', component: () => import('../views/Rules.vue') },
  { path: '/history', name: 'History', component: () => import('../views/History.vue') },
]

export default createRouter({ history: createWebHistory(), routes })
