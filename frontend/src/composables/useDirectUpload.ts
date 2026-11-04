/**
 * P1-T26 · 预签名直传三步封装
 *
 * Step 1：POST 后端换取 presigned PUT URL（拿 object_key）
 * Step 2：浏览器 PUT 直传对象存储，失败按 1s → 2s → 4s 指数退避重试 3 次
 * Step 3：POST 后端确认，写入附件/版本元数据
 *
 * 字节不经过后端，因此需要对象存储对浏览器放行 CORS（部署说明见 docs/DEPLOYMENT.md）。
 */
import { ref, type Ref } from 'vue'
import { errorMessage } from '../api/http'

export interface DirectUploadSession {
  presigned_url: string
  object_key: string
}

export interface DirectUploadHandlers<T> {
  /** Step 1：申请预签名 URL */
  presign: (file: File) => Promise<DirectUploadSession>
  /** Step 3：确认上传 */
  confirm: (file: File, session: DirectUploadSession) => Promise<T>
  /** Step 2 上传进度（0~100）*/
  onProgress?: (percent: number) => void
  /** Step 2 失败后的重试次数，默认 3 次（累计 4 次尝试）*/
  retries?: number
}

/** 第 n 次重试前的等待时间：1s → 2s → 4s（后续沿用 4s）*/
const RETRY_DELAYS_MS = [1000, 2000, 4000]

function sleep(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms))
}

/** Step 2：XHR 直传对象存储，暴露上传进度（fetch 无法上报进度）*/
export function putToStorage(
  url: string,
  file: File,
  onProgress?: (percent: number) => void
): Promise<void> {
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
      else reject(new Error(`对象存储拒绝写入（HTTP ${xhr.status}）`))
    }
    xhr.onerror = () => reject(new Error('网络错误或对象存储 CORS 未放行'))
    xhr.ontimeout = () => reject(new Error('上传超时'))
    xhr.send(file)
  })
}

/** Step 2 + 指数退避重试（重试复用同一个预签名 URL，过期后由调用方重新 presign）*/
async function putWithRetry(
  url: string,
  file: File,
  onProgress: ((percent: number) => void) | undefined,
  retries: number
): Promise<void> {
  for (let attempt = 0; ; attempt += 1) {
    try {
      await putToStorage(url, file, onProgress)
      return
    } catch (err) {
      if (attempt >= retries) throw err
      onProgress?.(0)
      await sleep(RETRY_DELAYS_MS[Math.min(attempt, RETRY_DELAYS_MS.length - 1)])
    }
  }
}

/** 完整三步上传；Step 2 失败自动重试，最终失败抛出可读原因 */
export async function directUpload<T>(
  file: File,
  handlers: DirectUploadHandlers<T>
): Promise<T> {
  const retries = handlers.retries ?? RETRY_DELAYS_MS.length
  const session = await handlers.presign(file)
  try {
    await putWithRetry(session.presigned_url, file, handlers.onProgress, retries)
  } catch (err) {
    throw new Error(
      `「${file.name}」上传失败：${errorMessage(err, '网络异常')}（已自动重试 ${retries} 次，请重新上传）`
    )
  }
  return handlers.confirm(file, session)
}

/**
 * 组合式封装：单文件上传状态（uploading / progress / error）。
 * 多文件场景直接用 {@link directUpload} 逐个跟踪。
 */
export function useDirectUpload<T>(handlers: DirectUploadHandlers<T>) {
  const uploading: Ref<boolean> = ref(false)
  const progress = ref(0)
  const error = ref('')

  async function upload(file: File): Promise<T | null> {
    uploading.value = true
    progress.value = 0
    error.value = ''
    try {
      return await directUpload(file, {
        ...handlers,
        onProgress: (percent) => {
          progress.value = percent
          handlers.onProgress?.(percent)
        },
      })
    } catch (err) {
      error.value = errorMessage(err, '上传失败，请重试')
      return null
    } finally {
      uploading.value = false
    }
  }

  return { uploading, progress, error, upload }
}
