/**
 * 设计版本模块 API · P2-T12
 * 对齐 GLOSSARY §03：A-08 / S-09 / A-09 / A-10 / A-11 / A-12 / A-17 / A-26 / S-07
 */
import http from '../http'
import type { ApiResponse } from '../http'

export interface VersionFile {
  file_name: string
  file_size: number
}

export interface VersionItem {
  id: number
  task_id: number
  order_id: number
  material_type: string
  spec_size: string
  version_no: string
  status: string
  design_note: string
  change_summary?: string | null
  uploader_id: number
  uploader_name: string
  is_final: boolean
  is_overdue: boolean
  file?: VersionFile | null
  submitted_at?: string | null
  created_at: string
}

export interface UploadStart {
  upload_session_id: string
  presigned_url: string
  object_key: string
  expires_in_minutes: number
  next_version_no: string
}

export interface VersionComparison {
  order_id: number
  left: VersionItem
  right: VersionItem
  design_note_changed: boolean
  change_summary_changed: boolean
}

/** 直传对象存储（不经后端），失败时给出可读原因 */
function putToStorage(url: string, file: File, onProgress?: (percent: number) => void): Promise<void> {
  return new Promise((resolve, reject) => {
    const xhr = new XMLHttpRequest()
    xhr.open('PUT', url)
    xhr.timeout = 5 * 60 * 1000
    xhr.upload.onprogress = (event) => {
      if (event.lengthComputable && onProgress) {
        onProgress(Math.round((event.loaded / event.total) * 100))
      }
    }
    xhr.onload = () => {
      if (xhr.status >= 200 && xhr.status < 300) resolve()
      else reject(new Error(`文件上传失败（HTTP ${xhr.status}），请重试`))
    }
    xhr.onerror = () => reject(new Error('文件上传失败：网络错误或对象存储 CORS 未放行'))
    xhr.ontimeout = () => reject(new Error('文件上传超时，请重试'))
    xhr.send(file)
  })
}

export const versionApi = {
  /** A-08 Step 1：申请版本直传会话与 presigned PUT URL */
  async startUpload(taskId: number, file: File): Promise<ApiResponse<UploadStart>> {
    return (await http.post(`/material-tasks/${taskId}/versions`, {
      file_name: file.name,
      file_size: file.size,
    })).data
  },

  /** A-08 Step 3 / S-09：确认上传，生成 DRAFT 版本 */
  async confirmUpload(payload: {
    upload_session_id: string
    design_note: string
    change_summary?: string
  }): Promise<ApiResponse<VersionItem>> {
    return (await http.post('/versions/confirm-upload', payload)).data
  },

  /** 完整三步上传：申请 → 直传对象存储 → 确认生成版本 */
  async uploadVersion(
    taskId: number,
    file: File,
    designNote: string,
    changeSummary: string | undefined,
    onProgress?: (percent: number) => void
  ): Promise<VersionItem> {
    const start = (await versionApi.startUpload(taskId, file)).data as UploadStart
    await putToStorage(start.presigned_url, file, onProgress)
    const confirmed = await versionApi.confirmUpload({
      upload_session_id: start.upload_session_id,
      design_note: designNote,
      change_summary: changeSummary,
    })
    return confirmed.data as VersionItem
  },

  /** A-09：提交版本进入内部评审 */
  async submitReview(versionId: number): Promise<ApiResponse<VersionItem>> {
    return (await http.post(`/versions/${versionId}/submit-review`)).data
  },

  /** A-26：订单维度版本历史（后端按调用者可见性过滤） */
  async listForOrder(orderId: number): Promise<ApiResponse<VersionItem[]>> {
    return (await http.get(`/orders/${orderId}/versions`)).data
  },

  /** A-10：BO 并排对比两个版本 */
  async compare(orderId: number, leftVersionId: number, rightVersionId: number): Promise<ApiResponse<VersionComparison>> {
    return (await http.get(`/orders/${orderId}/versions/compare`, {
      params: { v1: leftVersionId, v2: rightVersionId },
    })).data
  },

  /** A-11：BO 放行版本（放行备注可选） */
  async release(versionId: number, releaseRemark?: string): Promise<ApiResponse<VersionItem>> {
    const body = releaseRemark ? { release_remark: releaseRemark } : {}
    return (await http.post(`/versions/${versionId}/release`, body)).data
  },

  /** A-12：BO 退回版本（退回意见必填） */
  async reject(versionId: number, reviewComment: string): Promise<ApiResponse<VersionItem>> {
    return (await http.post(`/versions/${versionId}/reject`, { review_comment: reviewComment })).data
  },

  /** A-17：DS 标记终稿 */
  async markFinal(versionId: number): Promise<ApiResponse<VersionItem>> {
    return (await http.post(`/versions/${versionId}/mark-final`)).data
  },

  /** S-07：鉴权下载（后端 302 → 5 分钟 presigned GET） */
  async download(versionId: number, fileName: string): Promise<void> {
    const res = await http.get(`/versions/${versionId}/download`, { responseType: 'blob' })
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
