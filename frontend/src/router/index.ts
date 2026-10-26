import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import { RoleType } from '../types/enums'

/**
 * 路由定义 + 三角色守卫 · P1-T22 交付物
 * 守卫层级：
 *   第 1 层 - 未登录跳 /login
 *   第 2 层 - 角色不匹配跳 /403
 *
 * 仅做 UX 级门禁，不承担安全责任（真正的权限在后端 Spring Security + Repository 层强制）
 */

const routes: RouteRecordRaw[] = [
  // 公共路由
  {
    path: '/login',
    name: 'login',
    component: () => import('../views/LoginView.vue'),
    meta: { requiresAuth: false },
  },
  {
    path: '/403',
    name: 'forbidden',
    component: () => import('../views/ForbiddenView.vue'),
    meta: { requiresAuth: false },
  },

  // AM 路由
  {
    path: '/am',
    redirect: '/am/orders/create',
    meta: { requiresAuth: true, roles: [RoleType.AM] },
    children: [
      {
        path: 'orders/create',
        name: 'am-create-order',
        component: () => import('../views/am/CreateOrder.vue'),
      },
      {
        path: 'orders',
        name: 'am-my-orders',
        component: () => import('../views/am/MyOrderList.vue'),
      },
      {
        path: 'orders/:orderId',
        name: 'am-order-detail',
        component: () => import('../views/OrderDetail.vue'),
      },
    ],
  },

  // BO 路由
  {
    path: '/bo',
    redirect: '/bo/review',
    meta: { requiresAuth: true, roles: [RoleType.BO] },
    children: [
      {
        path: 'review',
        name: 'bo-pending-review',
        component: () => import('../views/bo/PendingReviewList.vue'),
      },
      {
        path: 'review/:orderId',
        name: 'bo-order-review',
        component: () => import('../views/bo/OrderReview.vue'),
      },
    ],
  },

  // DS 路由
  {
    path: '/ds',
    redirect: '/ds/tasks',
    meta: { requiresAuth: true, roles: [RoleType.DS] },
    children: [
      {
        path: 'tasks',
        name: 'ds-my-tasks',
        component: () => import('../views/ds/MyTaskList.vue'),
      },
      {
        path: 'tasks/:taskId',
        name: 'ds-task-detail',
        component: () => import('../views/ds/TaskDetail.vue'),
      },
    ],
  },

  // 根路径重定向
  { path: '/', redirect: '/login' },

  // 404
  { path: '/:pathMatch(.*)*', redirect: '/403' },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

/** beforeEach 守卫（Vue Router 5 推荐 return 风格，替代已弃用的 next()）*/
router.beforeEach((to) => {
  const authStore = useAuthStore()
  const requiresAuth = to.meta.requiresAuth !== false
  // 有效会话 = 有 token 且能确定角色；角色缺失视为无效会话（fail-closed）
  const hasValidSession = authStore.isLoggedIn && !!authStore.primaryRole

  // 未登录 → 登录页
  if (requiresAuth && !hasValidSession) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }

  // 已登录但访问登录页 → 按角色跳转首页
  if (to.path === '/login' && hasValidSession) {
    switch (authStore.primaryRole) {
      case RoleType.AM:
        return '/am/orders/create'
      case RoleType.BO:
        return '/bo/review'
      case RoleType.DS:
        return '/ds/tasks'
    }
  }

  // 角色守卫
  const allowedRoles = to.meta.roles as RoleType[] | undefined
  if (allowedRoles) {
    if (!authStore.primaryRole || !allowedRoles.includes(authStore.primaryRole)) {
      return '/403'
    }
  }

  return true
})

export default router
