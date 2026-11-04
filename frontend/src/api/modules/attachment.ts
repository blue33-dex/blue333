/**
 * 订单附件 API · P1-T26 / S-08
 *
 * 对齐后端 OrderController 与 AttachmentController：
 *   - POST /orders/{orderId}/attachments/presign  → 预签名 PUT URL（A-02 Step 1）
 *   - POST /orders/{orderId}/attachments          → 确认上传、写入附件元数据（A-02 Step 3）
 *   - GET  /attachments/{attachmentId}/download    → 302 跳转 5 分钟预签名 GET（S-08）
 */
import http from '../http'
import type { ApiResponse } from '../http'

/** 与后端 AttachmentFilePolicyValidator 保持一致 */
export const MAX_ATTACHMENT_SIZE_BYTES = 200 * 1024 * 1024
export const ALLOWED_ATTACHMENT_EXTENSIONS = [
  'psd', 'ai', 'sketch', 'fig', 'png', 'jpg', 'jpeg', 'gif', 'svg', 'pdf',
  'zip', 'rar', 'doc', 'docx', 'xls', 'xlsx', 'ppt', 'pptx',
] as const

export interface PresignedUpload {
  presigned_url: string
  object_key: string
  expires_in_minutes: number
}

export interface AttachmentItem {
  id: number
  file_name: string
  file_size: number
  created_at?: string
}

/** 浏览器侧校验：扩展名白名单 + 200 MB 上限（后端仍会二次校验）*/
export function validateAttachmentFile(file: File): string | null {
  const dot = file.name.lastIndexOf('.')
  const ext = dot > 0 ? file.name.slice(dot + 1).toLowerCase() : ''
  if (!ext) return `「${file.name}」缺少扩展名，无法判断文件类型`
  if (!(ALLOWED_ATTACHMENT_EXTENSIONS as readonly string[]).includes(ext)) {
    return `「${file.name}」格式不支持（允许：${ALLOWED_ATTACHMENT_EXTENSIONS.join('/')}）`
  }
  if (file.size <= 0) return `「${file.name}」是空文件`
  if (file.size > MAX_ATTACHMENT_SIZE_BYTES) return `「${file.name}」超过 200 MB 上限`
  return null
}

export const attachmentApi = {
  /** A-02 Step 1：申请预签名 PUT URL（仅 AM 且订单处于待确认状态）*/
  async presign(orderId: number, file: File): Promise<ApiResponse<PresignedUpload>> {
    return (await http.post(`/orders/${orderId}/attachments/presign`, {
      file_name: file.name,
      file_size: file.size,
    })).data
  },

  /** A-02 Step 3：确认对象已落库，后端写入 attachments 元数据 */
  async confirm(orderId: number, file: File, objectKey: string): Promise<ApiResponse<AttachmentItem>> {
    return (await http.post(`/orders/${orderId}/attachments`, {
      file_name: file.name,
      file_size: file.size,
      object_key: objectKey,
    })).data
  },

  /**
   * S-08：鉴权下载（后端 302 → 5 分钟预签名 GET）。
   * 与 S-07 / A-28 一致走 blob，避免浏览器直接跳转丢失 JWT。
   */
  async download(attachmentId: number, fileName: string): Promise<void> {
    const res = await http.get(`/attachments/${attachmentId}/download`, { responseType: 'blob' })
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
