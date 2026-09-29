import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  { path: '/login', name: 'login', component: () => import('../views/Login.vue') },
  {
    path: '/',
    component: () => import('../layouts/AdminLayout.vue'),
    redirect: '/dashboard',
    children: [
      { path: 'dashboard', name: 'dashboard', component: () => import('../views/Dashboard.vue') },
      { path: 'posts', name: 'posts', component: () => import('../views/Posts.vue') },
      { path: 'users', name: 'users', component: () => import('../views/Users.vue') },
      { path: 'disputes', name: 'disputes', component: () => import('../views/Disputes.vue') },
      { path: 'audit', name: 'audit', component: () => import('../views/Audit.vue') }
    ]
  }
]

const router = createRouter({ history: createWebHistory(), routes })

// 前端路由守卫（体验用；真正权限由后端逐资源校验）
router.beforeEach((to) => {
  const token = localStorage.getItem('clf_admin_token')
  if (to.name !== 'login' && !token) {
    return { name: 'login' }
  }
  return true
})

export default router
