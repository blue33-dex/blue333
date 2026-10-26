/**
 * 物料任务模块 API 函数 · Phase 1（只读）
 */
import http from '../http'
import type { ApiResponse } from '../http'

export interface TaskDetail {
  id: number
  order_id: number
  order_no: string
  material_type: string
  spec_size: string
  assignee_id?: number
  assignee_name?: string
  task_deadline: string
  priority: string
  status: string
  created_at: string
  updated_at: string
}

export const materialTaskApi = {
  /** S-05 DS 我的任务列表 */
  async listMine(): Promise<ApiResponse<TaskDetail[]>> {
    return (await http.get('/material-tasks/mine')).data
  },

  /** S-06 任务详情 */
  async getById(taskId: number): Promise<ApiResponse<TaskDetail>> {
    return (await http.get(`/material-tasks/${taskId}`)).data
  },
}
