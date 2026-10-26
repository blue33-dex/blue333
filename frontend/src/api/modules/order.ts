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
  material_task_count?: number
  latest_task_status?: string
}

export interface OrderDetail extends OrderBrief {
  use_scene?: string
  remark?: string
  material_specs: { id: number; material_type: string; spec_size: string }[]
  attachments: { id: number; file_name: string; file_size: number }[]
  reject_reason?: { reject_reason_type: string; remark: string; rejected_by: string }
}

// ========== 函数 ==========
export const orderApi = {
  /** A-01 创建订单 */
  async createOrder(data: CreateOrderRequest): Promise<ApiResponse<{ orderId: number; order_no: string; status: string }>> {
    return (await http.post('/orders', data)).data
  },

  /** A-03 提交订单 */
  async submitOrder(orderId: number): Promise<ApiResponse<{ orderId: number; status: string }>> {
    return (await http.post(`/orders/${orderId}/submit`)).data
  },

  /** A-04 BO 审阅订单详情（复用 S-04）*/
  async reviewOrder(orderId: number): Promise<ApiResponse<OrderDetail>> {
    return (await http.get(`/orders/${orderId}/review`)).data
  },

  /** A-05 BO 退回订单 */
  async rejectOrder(orderId: number, reject_reason_type: string, remark: string): Promise<ApiResponse<{ orderId: number; status: string }>> {
    return (await http.post(`/orders/${orderId}/reject`, { reject_reason_type, remark })).data
  },

  /** A-06 BO 派发任务 */
  async assignTask(orderId: number, data: { assignee_id: number; assignee_name: string; task_deadline: string; priority: string; remark?: string }): Promise<ApiResponse<{ orderId: number; status: string; taskCount: number }>> {
    return (await http.post(`/orders/${orderId}/assign`, data)).data
  },

  /** A-24 / S-04 AM 我的订单列表 */
  async listMyOrders(): Promise<ApiResponse<OrderBrief[]>> {
    return (await http.get('/orders/mine')).data
  },

  /** S-04 通用订单详情 */
  async getOrderDetail(orderId: number): Promise<ApiResponse<OrderDetail>> {
    return (await http.get(`/orders/${orderId}`)).data
  },

  /** A-25 BO 看板 */
  async getKanban(): Promise<ApiResponse<{ columns: Record<string, OrderBrief[]> }>> {
    return (await http.get('/orders/kanban')).data
  },
}
