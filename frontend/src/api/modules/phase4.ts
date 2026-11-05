/**
 * Phase 4 API · P4-T12
 * 看板（A-25）、异常记录（A-20/A-21）、时间线（A-29）、通知中心 + SSE（P4-T09）
 */
import http from '../http'
import type { ApiResponse } from '../http'
import { USE_MOCK } from '../http'
import type { ExceptionStatus, ExceptionType, OrderStatus } from '../../types/enums'

export interface KanbanCard {
  id: number
  order_no: string
  customer_name: string
  status: OrderStatus
  am_name: string | null
  delivery_deadline: string | null
  created_at: string
  task_count: number
  open_feedback_count: number
}

export interface KanbanColumn {
  status: OrderStatus
  total: number
  orders: KanbanCard[]
}

export interface KanbanResponse {
  generated_at: string
  columns: KanbanColumn[]
}

export interface ExceptionRecordItem {
  id: number
  order_id: number
  order_no: string
  task_id: number | null
  exception_type: ExceptionType
  status: ExceptionStatus
  description: string
  resolution: string | null
  recorder_id: number | null
  recorder_name: string
  created_at: string
  updated_at: string
}

export interface ExceptionScanResult {
  scanned_at: string
  created_count: number
  created_ids: number[]
}

export interface TimelineEntry {
  id: number
  action_type: string
  action_label: string
  target_type: string
  target_id: number
  operator_id: number | null
  operator_name: string
  before_state: string | null
  after_state: string | null
  result: string
  remark: string | null
  operated_at: string
}

export interface NotificationItem {
  id: number
  notification_type: string
  resource_type: string
  resource_id: number
  title: string
  content: string
  read: boolean
  read_at: string | null
  created_at: string
}

export interface KanbanQuery {
  am_id?: number
  customer?: string
  from?: string
  to?: string
}

export const kanbanApi = {
  /** A-25：四状态列 + AM/客户/日期过滤 */
  async getKanban(params: KanbanQuery = {}): Promise<ApiResponse<KanbanResponse>> {
    return (await http.get('/orders/kanban', { params })).data
  },
}

export const exceptionApi = {
  /** A-20：订单维度异常列表 */
  async listForOrder(orderId: number): Promise<ApiResponse<ExceptionRecordItem[]>> {
    return (await http.get(`/orders/${orderId}/exceptions`)).data
  },
  /** A-20：登记异常（BO） */
  async create(
    orderId: number,
    body: { exception_type: ExceptionType; description: string }
  ): Promise<ApiResponse<ExceptionRecordItem>> {
    return (await http.post(`/orders/${orderId}/exceptions`, body)).data
  },
  /** 异常队列（按状态过滤） */
  async list(params: {
    status?: ExceptionStatus
    cursor?: string
    limit?: number
  } = {}): Promise<ApiResponse<ExceptionRecordItem[]>> {
    return (await http.get('/exceptions', { params })).data
  },
  /** A-21：关闭异常 */
  async close(id: number, resolution: string): Promise<ApiResponse<ExceptionRecordItem>> {
    return (await http.patch(`/exceptions/${id}/close`, { resolution })).data
  },
  /** P4-T05：手动触发逾期扫描 */
  async autoScan(): Promise<ApiResponse<ExceptionScanResult>> {
    return (await http.post('/exceptions/auto-scan')).data
  },
}

export const timelineApi = {
  /** A-29：订单时间线（游标升序） */
  async list(
    orderId: number,
    params: { cursor?: string; limit?: number } = {}
  ): Promise<ApiResponse<TimelineEntry[]>> {
    return (await http.get(`/orders/${orderId}/timeline`, { params })).data
  },
}

export const notificationApi = {
  async list(
    params: { unread_only?: boolean; cursor?: string; limit?: number } = {}
  ): Promise<ApiResponse<NotificationItem[]>> {
    return (await http.get('/notifications', { params })).data
  },
  async unreadCount(): Promise<ApiResponse<{ unread_count: number }>> {
    return (await http.get('/notifications/unread-count')).data
  },
  async markRead(id: number): Promise<ApiResponse<NotificationItem>> {
    return (await http.patch(`/notifications/${id}/read`)).data
  },
  async markAllRead(): Promise<ApiResponse<{ updated_count: number }>> {
    return (await http.post('/notifications/read-all')).data
  },
}

/**
 * P4-T09：SSE 订阅。
 * EventSource 无法携带 Authorization 头，因此这里用 fetch + ReadableStream 手工解析。
 * 返回取消函数；调用方在组件卸载时执行以关闭连接。
 */
export function subscribeNotifications(
  onEvent: (event: string, payload: unknown) => void,
  onError?: (message: string) => void
): () => void {
  // Mock 模式下无真实 SSE 端点；fetch 绕过 axios 适配器会打到不存在的后端，直接返回 no-op
  if (USE_MOCK) return () => {}
  const controller = new AbortController()
  const token = localStorage.getItem('access_token')

  async function run() {
    try {
      const res = await fetch('/api/v1/notifications/stream', {
        headers: { Authorization: `Bearer ${token}`, Accept: 'text/event-stream' },
        signal: controller.signal,
      })
      if (!res.ok || !res.body) {
        onError?.(`通知流连接失败（HTTP ${res.status}）`)
        return
      }
      const reader = res.body.getReader()
      const decoder = new TextDecoder()
      let buffer = ''
      let eventName = 'message'
      while (true) {
        const { done, value } = await reader.read()
        if (done) break
        buffer += decoder.decode(value, { stream: true })
        const lines = buffer.split('\n')
        buffer = lines.pop() ?? ''
        for (const rawLine of lines) {
          const line = rawLine.replace(/\r$/, '')
          if (line.startsWith(':')) continue // 心跳注释
          if (line.startsWith('event:')) {
            eventName = line.slice(6).trim()
          } else if (line.startsWith('data:')) {
            const data = line.slice(5).trim()
            try {
              onEvent(eventName, JSON.parse(data))
            } catch {
              onEvent(eventName, data)
            }
            eventName = 'message'
          } else if (line === '') {
            eventName = 'message'
          }
        }
      }
    } catch (e) {
      if (controller.signal.aborted) return
      onError?.(e instanceof Error ? e.message : '通知流已断开')
    }
  }

  void run()
  return () => controller.abort()
}
