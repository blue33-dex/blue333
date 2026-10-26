import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { RoleType } from '../types/enums'

/**
 * 认证状态 Store · Phase 1 必须
 * 职责：JWT 管理 + 当前用户信息 + 角色路由守卫判定
 */
export const useAuthStore = defineStore('auth', () => {
  // 状态
  const accessToken = ref<string | null>(
    localStorage.getItem('access_token')
  )
  const refreshToken = ref<string | null>(
    localStorage.getItem('refresh_token')
  )
  const currentUser = ref<{
    id: number
    username: string
    name: string
    roles: RoleType[]
  } | null>(null)

  // 计算属性
  const isLoggedIn = computed(() => !!accessToken.value)

  const primaryRole = computed<RoleType | null>(() => {
    return currentUser.value?.roles?.[0] ?? null
  })

  const canAccessAm = computed(() =>
    currentUser.value?.roles?.includes('AM' as RoleType) ?? false
  )
  const canAccessBo = computed(() =>
    currentUser.value?.roles?.includes('BO' as RoleType) ?? false
  )
  const canAccessDs = computed(() =>
    currentUser.value?.roles?.includes('DS' as RoleType) ?? false
  )

  // Actions
  function setAuth(
    access: string,
    refresh: string,
    user: typeof currentUser.value
  ) {
    accessToken.value = access
    refreshToken.value = refresh
    currentUser.value = user
    localStorage.setItem('access_token', access)
    localStorage.setItem('refresh_token', refresh)
  }

  function clearAuth() {
    accessToken.value = null
    refreshToken.value = null
    currentUser.value = null
    localStorage.removeItem('access_token')
    localStorage.removeItem('refresh_token')
  }

  function updateAccessToken(newToken: string) {
    accessToken.value = newToken
    localStorage.setItem('access_token', newToken)
  }

  return {
    accessToken,
    refreshToken,
    currentUser,
    isLoggedIn,
    primaryRole,
    canAccessAm,
    canAccessBo,
    canAccessDs,
    setAuth,
    clearAuth,
    updateAccessToken,
  }
})
