/**
 * 订单模块 API 函数 · Phase 1
 * 严格对齐 GLOSSARY §03 端点 + 补充 S-04/S-08
 */
import http from '../http'
import type { ApiResponse } from '../http'

// ========== 请求/响应 DTO ==========
export interface CreateOrderRequest {
  customer_name: string
  use_scene: string
  delivery_deadline: string
  remark?: string
  material_specs: { material_type: string; spec_size: string }[]
}

export interface OrderBrief {
  id: number
  order_no: string
  customer_name: string
  status: string
  am_name: string
  delivery_deadline: string
  created_at: string
  attachment_count?: number
  task_count?: number
  /** 最近一次客户评审状态：IN_REVIEW / COMPLETED；未发起评审时后端不返回该字段 */
  client_review_status?: string
  /** 全订单未解决反馈数（Phase 3）*/
  open_feedback_count?: number
  /** 仅 BO 归档队列返回：订单是否已归档 */
  archived?: boolean
  archived_at?: string
  /** BO 退回记录（存在即表示该订单曾被退回，AM 端需展示退回原因并支持重新提交）*/
  reject_reason?: { reject_reason_type: string; remark: string; rejected_by: string }
}

export interface OrderTask {
  id: number
  material_type: string
  spec_size: string
  assignee_id?: number
  assignee_name?: string
  task_deadline?: string
  priority?: string
  status?: string
}

export interface OrderDetail extends OrderBrief {
  use_scene?: string
  remark?: string
  updated_at?: string
  am_id?: number
  material_specs: { id: number; material_type: string; spec_size: string }[]
  attachments: { id: number; file_name: string; file_size: number; created_at?: string }[]
  tasks?: OrderTask[]
  reject_reason?: { reject_reason_type: string; remark: string; rejected_by: string }
}

// ========== 函数 ==========
export const orderApi = {
  /** A-01 创建订单（后端返回 OrderDetail，含 id） */
  async createOrder(data: CreateOrderRequest): Promise<ApiResponse<OrderDetail>> {
    return (await http.post('/orders', data)).data
  },

  /** A-03 提交订单 */
  async submitOrder(orderId: number): Promise<ApiResponse<OrderDetail>> {
    return (await http.post(`/orders/${orderId}/submit`)).data
  },

  /** A-04 BO 审阅订单详情（复用 S-04）*/
  async reviewOrder(orderId: number): Promise<ApiResponse<OrderDetail>> {
    return (await http.get(`/orders/${orderId}/review`)).data
  },

  /** A-05 BO 退回订单 */
  async rejectOrder(orderId: number, reject_reason_type: string, remark: string): Promise<ApiResponse<OrderDetail>> {
    return (await http.post(`/orders/${orderId}/reject`, { reject_reason_type, remark })).data
  },

  /** A-06 BO 派发任务（assignee_id + LocalDateTime 格式的 task_deadline） */
  async assignTask(orderId: number, data: { assignee_id: number; task_deadline: string; priority: string; remark?: string }): Promise<ApiResponse<OrderDetail>> {
    return (await http.post(`/orders/${orderId}/assign`, data)).data
  },

  /** A-24 / S-04 AM 我的订单列表 */
  async listMyOrders(): Promise<ApiResponse<OrderBrief[]>> {
    return (await http.get('/orders/mine')).data
  },

  /** BO 待确认队列（A-04 入口） */
  async listReviewQueue(): Promise<ApiResponse<OrderBrief[]>> {
    return (await http.get('/orders/review-queue')).data
  },

  /** BO 内部评审队列（A-10/A-11/A-12 入口，M4） */
  async listInternalReviewQueue(): Promise<ApiResponse<OrderBrief[]>> {
    return (await http.get('/orders/internal-review-queue')).data
  },

  /** BO 归档队列（A-19/A-28 入口）：已交付订单 + 归档状态 */
  async listCompletedOrders(): Promise<ApiResponse<OrderBrief[]>> {
    return (await http.get('/orders/completed')).data
  },

  /** A-18 AM 标记已交付（要求全部任务 APPROVED，CONFLICT-02）*/
  async deliverOrder(orderId: number): Promise<ApiResponse<OrderDetail>> {
    return (await http.post(`/orders/${orderId}/deliver`)).data
  },

  /** S-04 通用订单详情 */
  async getOrderDetail(orderId: number): Promise<ApiResponse<OrderDetail>> {
    return (await http.get(`/orders/${orderId}`)).data
  },
}
