import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { RoleType } from '../types/enums'

/**
 * 认证状态 Store · Phase 1 必须
 * 职责：JWT 管理 + 当前用户信息 + 角色路由守卫判定
 */
const USER_STORAGE_KEY = 'current_user'

type CurrentUser = { id: number; username: string; name: string; roles: RoleType[] }

/**
 * 从 localStorage 恢复当前用户。
 * 必要性：角色守卫与 Mock 数据可见性（BR-13）都依赖 primaryRole；
 * 若刷新后 currentUser 为空，primaryRole 为 null，角色守卫会被直接绕过。
 */
function readStoredUser(): CurrentUser | null {
  try {
    const raw = localStorage.getItem(USER_STORAGE_KEY)
    if (!raw) return null
    const parsed = JSON.parse(raw) as CurrentUser
    if (!parsed?.id || !Array.isArray(parsed.roles) || !parsed.roles.length) return null
    return parsed
  } catch {
    return null
  }
}

export const useAuthStore = defineStore('auth', () => {
  // 状态
  const accessToken = ref<string | null>(
    localStorage.getItem('access_token')
  )
  const refreshToken = ref<string | null>(
    localStorage.getItem('refresh_token')
  )
  const currentUser = ref<CurrentUser | null>(readStoredUser())

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
    if (user) localStorage.setItem(USER_STORAGE_KEY, JSON.stringify(user))
  }

  function clearAuth() {
    accessToken.value = null
    refreshToken.value = null
    currentUser.value = null
    localStorage.removeItem('access_token')
    localStorage.removeItem('refresh_token')
    localStorage.removeItem(USER_STORAGE_KEY)
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
