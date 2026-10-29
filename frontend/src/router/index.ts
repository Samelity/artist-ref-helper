import { createRouter, createWebHashHistory } from 'vue-router'

const router = createRouter({
  history: createWebHashHistory(),
  routes: [
    { path: '/', redirect: '/text' },
    { path: '/text', name: 'text', component: () => import('@/views/TextSearchView.vue') },
    { path: '/image', name: 'image', component: () => import('@/views/ImageSearchView.vue') },
    { path: '/remote', name: 'remote', component: () => import('@/views/RemoteSearchView.vue') },
    { path: '/upload', name: 'upload', component: () => import('@/views/UploadView.vue') },
    { path: '/boards', name: 'boards', component: () => import('@/views/BoardsView.vue') },
    { path: '/boards/:boardId', name: 'board-detail', component: () => import('@/views/BoardsView.vue') },
  ],
})

export default router
