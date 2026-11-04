/**
 * 用户模块 API · S-08 设计师列表（BO 派发任务时选择执行人）
 */
import http from '../http'
import type { ApiResponse } from '../http'

export interface DesignerSummary {
  id: number
  username: string
  name: string
}

export const userApi = {
  /** S-08：启用状态的 DS 列表 */
  async listDesigners(): Promise<ApiResponse<DesignerSummary[]>> {
    return (await http.get('/users/designers')).data
  },
}
