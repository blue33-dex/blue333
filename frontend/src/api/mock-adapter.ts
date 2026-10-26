/**
 * Mock Adapter · 让前端在无后端时独立运行
 *
 * 设计原则：
 *   - 拦截所有请求，返回 mock 数据
 *   - 模拟网络延迟（200~800ms）
 *   - 不持久化任何修改状态（刷新后回到初始）
 *   - 按 MockService 签名匹配请求
 *
 * 当 USE_MOCK=false 时（http.ts 中切换），此文件不被挂载，真实请求直打后端。
 */
import { mockService } from './mock-data'
import type { ApiResponse } from './http'

interface MockRequest {
  url: string
  method: string
  data?: unknown
  params?: unknown
}

/** 方法大小写归一化 */
function normalizeMethod(m: string): string {
  return (m || 'get').toUpperCase()
}

/** 统一返回包裹 */
function wrap<T>(data: T, _status = 200, extra?: Partial<ApiResponse>): ApiResponse<T> {
  // 注意：extra 不应覆盖 data 字段，这里通过 spread 顺序强制 data 优先
  return { ...extra, data, code: 'SUCCESS' } as ApiResponse<T>
}

/** 从 URL 中提取路径参数（简易实现）*/
function extractParam(url: string, pattern: RegExp): string | undefined {
  const m = url.match(pattern)
  return m?.[1]
}

/** Mock Adapter 主入口 */
export async function mockAdapter(config: {
  url?: string
  method?: string
  data?: unknown
  params?: unknown
  [key: string]: unknown
}): Promise<{ data: ApiResponse; status: number; statusText: string; headers: Record<string, string>; config: unknown }> {
  // 模拟延迟 200~800ms
  await new Promise((r) => setTimeout(r, 200 + Math.random() * 600))

  const req: MockRequest = {
    url: config.url || '',
    method: normalizeMethod(config.method || 'get'),
    data: config.data,
    params: config.params,
  }

  // 路由分发
  const result = route(req)
  return {
    data: result.body,
    status: result.status,
    statusText: result.status === 200 ? 'OK' : 'ERROR',
    headers: { 'content-type': 'application/json' },
    config,
  }
}

/** 简易路由表（按 method + url pattern 匹配）*/
function route(req: MockRequest): { status: number; body: ApiResponse } {
  // === 认证（Mock 直接放行，由前端 LoginView 做真实角色判定）===
  if (req.method === 'POST' && req.url === '/auth/login') {
    const loginData = (req.data ?? {}) as { username: string; password: string }
    const result = mockService.mockLogin(loginData)
    // mockLogin 成功时返回 { access_token, refresh_token, user }；失败返回 { error, message }
    if ('error' in result) {
      return { status: 401, body: { code: result.error, message: result.message } }
    }
    return { status: 200, body: wrap(result) }
  }
  if (req.method === 'POST' && req.url === '/auth/refresh') {
    return { status: 200, body: wrap({ access_token: 'mock-new-token' }) }
  }

  // === 订单 ===
  // GET /orders/mine → AM 我的订单列表
  if (req.method === 'GET' && req.url === '/orders/mine') {
    return { status: 200, body: wrap(mockService.getMyOrders()) }
  }
  // GET /orders → BO 看板
  if (req.method === 'GET' && req.url === '/orders/kanban') {
    return { status: 200, body: wrap(mockService.getKanban()) }
  }
  // POST /orders → AM 创建订单
  if (req.method === 'POST' && req.url === '/orders') {
    const created = mockService.createOrder(req.data as Record<string, unknown>)
    return { status: 201, body: wrap(created) }
  }
  // GET /orders/{id} → 通用详情
  if (req.method === 'GET') {
    const detailId = extractParam(req.url, /\/orders\/(\d+)$/)
    if (detailId) {
      const detail = mockService.getOrderDetail(detailId)
      if (!detail) return { status: 404, body: { code: 'ORDER_NOT_FOUND', message: '订单不存在' } }
      return { status: 200, body: wrap(detail) }
    }
  }
  // GET /orders/{id}/review → BO 审阅详情
  if (req.method === 'GET') {
    const reviewId = extractParam(req.url, /\/orders\/(\d+)\/review$/)
    if (reviewId) {
      return { status: 200, body: wrap(mockService.getOrderDetail(reviewId)) }
    }
  }
  // POST /orders/{id}/submit
  if (req.method === 'POST') {
    const submitId = extractParam(req.url, /\/orders\/(\d+)\/submit$/)
    if (submitId) {
      mockService.submitOrder(submitId)
      return { status: 200, body: wrap({ orderId: submitId, status: 'PENDING_CONFIRM' }) }
    }
  }
  // POST /orders/{id}/reject
  if (req.method === 'POST') {
    const rejectId = extractParam(req.url, /\/orders\/(\d+)\/reject$/)
    if (rejectId) {
      const payload = req.data as { reject_reason_type?: string; remark?: string } | undefined
      mockService.rejectOrder(rejectId, payload)
      return { status: 200, body: wrap({ orderId: rejectId, status: 'PENDING_CONFIRM' }) }
    }
  }
  // POST /orders/{id}/assign
  if (req.method === 'POST') {
    const assignId = extractParam(req.url, /\/orders\/(\d+)\/assign$/)
    if (assignId) {
      const result = mockService.assignTask(assignId, req.data as any)
      return { status: 200, body: wrap(result) }
    }
  }

  // === 物料任务 ===
  if (req.method === 'GET' && req.url === '/material-tasks/mine') {
    return { status: 200, body: wrap(mockService.getMyTasks()) }
  }
  if (req.method === 'GET') {
    const taskId = extractParam(req.url, /\/material-tasks\/(\d+)$/)
    if (taskId) return { status: 200, body: wrap(mockService.getTaskDetail(taskId)) }
  }

  // === 未匹配 → 404 ===
  return {
    status: 404,
    body: { code: 'API_NOT_IMPLEMENTED', message: `Mock 未实现：${req.method} ${req.url}` },
  }
}
