/**
 * 物料任务模块 API · Phase 1/2
 * 后端 S-05 返回 TaskSummary 列表；S-06 返回 { task, order } 双段结构（P2-T16 对齐）
 */
import http from '../http'
import type { ApiResponse } from '../http'
import type { OrderDetail } from './order'

export interface TaskSummary {
  id: number
  order_id: number
  order_no: string
  material_type: string
  spec_size: string
  task_deadline: string
  priority: string
  status: string
  created_at: string
}

/** S-06：任务 + 其所属订单（订单内 tasks 含 assignee_name，可供 DS 页面展示派发人） */
export interface TaskDetailPayload {
  task: TaskSummary
  order: OrderDetail
}

/** A-07 重新分派请求体；截止时间与优先级不传时后端保留原值 */
export interface ReassignPayload {
  assignee_id: number
  reason: string
  task_deadline?: string
  priority?: string
}

export const materialTaskApi = {
  /** S-05 DS 我的任务列表 */
  async listMine(): Promise<ApiResponse<TaskSummary[]>> {
    return (await http.get('/material-tasks/mine')).data
  },

  /** S-06 任务详情 */
  async getById(taskId: number): Promise<ApiResponse<TaskDetailPayload>> {
    return (await http.get(`/material-tasks/${taskId}`)).data
  },

  /** A-07 BO 重新分派任务给其他设计师（原因必填） */
  async reassign(taskId: number, payload: ReassignPayload): Promise<ApiResponse<OrderDetail>> {
    return (await http.post(`/material-tasks/${taskId}/reassign`, payload)).data
  },

  /** A-23 BO 取消单个物料任务（原因必填，仅非终态任务可取消） */
  async cancel(taskId: number, reason: string): Promise<ApiResponse<OrderDetail>> {
    return (await http.post(`/material-tasks/${taskId}/cancel`, { reason })).data
  },
}
