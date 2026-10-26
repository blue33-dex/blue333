/**
 * Axios 拦截器 · P1-T23
 * 职责：JWT 自动附加 + 401 自动刷新 + 统一错误处理 + Mock / Real 切换
 *
 * 设计原则：
 *   - 单一实例，所有 API 模块共享
 *   - 拦截器分层：request（加 token）→ response（401 刷新 + 错误归一化）
 *   - Mock 开关：window.__USE_MOCK__ = true 时走 mockAdapter，不打真实 API
 *   - Phase 1 后端完成后：将 useMock 切换为 false 即可接入真实后端
 */
import axios, { type AxiosInstance, type InternalAxiosRequestConfig, type AxiosResponse } from 'axios'
import { useAuthStore } from '../stores/auth'
import { mockAdapter } from './mock-adapter'

// --- Mock 开关（Phase 1 后端未就绪时默认 true）---
export const USE_MOCK = true // TODO: 后端就绪后改为 false

/** 统一后端响应包裹结构（对齐 GLOSSARY REST API 规范）*/
export interface ApiResponse<T = unknown> {
  code?: string
  message?: string
  detail?: unknown
  data?: T
  pagination?: {
    has_next: boolean
    next_cursor?: string
  }
}

/** 统一错误结构 */
export class ApiError extends Error {
  code: string
  status: number
  detail?: unknown
  constructor(code: string, status: number, message: string, detail?: unknown) {
    super(message); this.code = code; this.status = status; this.detail = detail
  }
}

// --- 实例 ---
const http: AxiosInstance = axios.create({
  baseURL: '/api/v1',
  timeout: 15000,
})

// --- 挂载 Mock adapter ---
if (USE_MOCK) {
  http.defaults.adapter = mockAdapter as unknown as typeof http.defaults.adapter
}

// --- Request 拦截器：自动附加 JWT ---
http.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    // 白名单：认证接口不需要 token
    const noAuthPaths = ['/auth/login', '/auth/refresh']
    const needsAuth = !noAuthPaths.some((p) => config.url?.startsWith(p))
    if (needsAuth) {
      const token = localStorage.getItem('access_token')
      if (token) config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  (err: unknown) => Promise.reject(err)
)

// --- Response 拦截器：统一包装 + 401 自动刷新 ---
let isRefreshing = false
let pendingRefreshQueue: Array<(token: string) => void> = []

http.interceptors.response.use(
  (res: AxiosResponse<ApiResponse>) => {
    // Mock adapter 已包装为 { data, status }；真实后端也返回 ApiResponse
    const body = res.data as ApiResponse
    // 统一校验业务 code（若后端有返回）
    if (body.code && body.code !== 'SUCCESS' && !body.data) {
      throw new ApiError(body.code, res.status, body.message || '请求失败', body.detail)
    }
    return res
  },
  async (err: unknown) => {
    // 401 → 自动 refresh（非 auth 接口）
    const axiosErr = err as { response?: { status?: number }; config?: { url?: string; headers?: Record<string, string> } }

    // 关键：认证端点（login / refresh）返回 401 是正常的业务错误（密码错 / token 失效），
    // 不应触发全局 refresh → 重定向逻辑，否则登录页输入错误密码会被强制跳 /login
    const noAuthPaths = ['/auth/login', '/auth/refresh']
    const isAuthRequest = noAuthPaths.some((p) => axiosErr.config?.url?.startsWith(p))

    if (axiosErr.response?.status === 401 && !isAuthRequest) {
      const authStore = useAuthStore()
      if (!authStore.refreshToken) {
        authStore.clearAuth()
        window.location.href = '/login?reason=expired'
        return Promise.reject(err)
      }
      if (!isRefreshing) {
        isRefreshing = true
        try {
          const { data } = await axios.post('/api/v1/auth/refresh', {
            refresh_token: authStore.refreshToken,
          })
          authStore.updateAccessToken((data as { access_token: string }).access_token)
          pendingRefreshQueue.forEach((cb) => cb((data as { access_token: string }).access_token))
          pendingRefreshQueue = []
        } catch {
          authStore.clearAuth()
          window.location.href = '/login?reason=expired'
        } finally {
          isRefreshing = false
        }
      }
      // 加入等待队列
      return new Promise((resolve) => {
        pendingRefreshQueue.push((token) => {
          if (axiosErr.config) {
            axiosErr.config.headers = axiosErr.config.headers || {}
            axiosErr.config.headers.Authorization = `Bearer ${token}`
            resolve(http(axiosErr.config as never))
          } else {
            resolve(Promise.reject(err) as never)
          }
        })
      })
    }
    return Promise.reject(err)
  }
)

export default http
