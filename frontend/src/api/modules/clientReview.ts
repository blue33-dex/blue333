/**
 * 客户评审 / 交付归档模块 API · P3-T09
 * 对齐 GLOSSARY §03：A-13 / A-14 / A-15 / A-16 / A-27 / A-19 / A-28
 */
import http from '../http'
import type { ApiResponse } from '../http'
import type { VersionItem } from './designVersion'

// ========== 响应 DTO ==========
export interface ClientReviewResponse {
  id: number
  version_id: number
  version_no: string
  order_id: number
  status: string
  initiator_id: number
  initiator_name: string
  started_at: string
  completed_at?: string | null
}

export interface FeedbackResponse {
  id: number
  feedback_no: string
  version_id: number
  version_no: string
  client_review_id: number
  content: string
  feedback_type: string
  source: string
  status: string
  created_by_id: number
  created_by_name: string
  created_at: string
  updated_at: string
}

export interface ConfirmVersionResponse {
  version: VersionItem
  confirmation_record_id: number
  confirmer_name: string
  confirmed_at: string
  unresolved_feedback_count: number
  warning?: string
}

export interface DeliveryArchiveResponse {
  id: number
  order_id: number
  archiver_id: number
  archiver_name: string
  archived_at: string
  archive_report_available: boolean
}

export interface RecordFeedbackRequest {
  feedback_type: string
  /** 缺省为 CLIENT；BO 代录时传 BUSINESS_OWNER */
  source?: string
  content: string
}

// ========== 函数 ==========
export const clientReviewApi = {
  /** A-13 发起客户评审（RELEASED → IN_CLIENT_REVIEW）*/
  async initiateClientReview(versionId: number): Promise<ApiResponse<ClientReviewResponse>> {
    return (await http.post(`/versions/${versionId}/client-review`)).data
  },

  /** A-14 记录客户反馈（绑定具体版本编号）*/
  async recordFeedback(versionId: number, data: RecordFeedbackRequest): Promise<ApiResponse<FeedbackResponse>> {
    return (await http.post(`/versions/${versionId}/feedbacks`, data)).data
  },

  /** A-27 某版本的反馈列表 */
  async listFeedbacks(versionId: number): Promise<ApiResponse<FeedbackResponse[]>> {
    return (await http.get(`/versions/${versionId}/feedbacks`)).data
  },

  /** A-15 设计师标记反馈已解决（EXPIRED 不可解决）*/
  async resolveFeedback(feedbackId: number): Promise<ApiResponse<FeedbackResponse>> {
    return (await http.patch(`/feedbacks/${feedbackId}/resolve`)).data
  },

  /** A-16 客户确认版本（同时触发 MaterialTask → APPROVED）*/
  async confirmVersion(versionId: number, confirmerName: string): Promise<ApiResponse<ConfirmVersionResponse>> {
    return (await http.post(`/versions/${versionId}/confirm`, { confirmer_name: confirmerName })).data
  },
}

export const archiveApi = {
  /** A-19 BO 归档已交付订单（生成 PDF 归档报告）*/
  async archiveOrder(orderId: number): Promise<ApiResponse<DeliveryArchiveResponse>> {
    return (await http.post(`/orders/${orderId}/archive`)).data
  },

  /**
   * A-28 下载归档报告（后端 302 → 5 分钟预签名 GET）。
   * 与 S-07 一致走 blob，避免浏览器直接跳转丢失 JWT。
   */
  async downloadReport(orderId: number, fileName: string): Promise<void> {
    const res = await http.get(`/orders/${orderId}/archive-report`, { responseType: 'blob' })
    const url = URL.createObjectURL(res.data as Blob)
    const anchor = document.createElement('a')
    anchor.href = url
    anchor.download = fileName
    document.body.appendChild(anchor)
    anchor.click()
    anchor.remove()
    URL.revokeObjectURL(url)
  },
}
