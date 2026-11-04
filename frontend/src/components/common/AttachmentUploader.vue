<script setup lang="ts">
/**
 * P1-T26 · 附件直传组件（AM 上传参考素材，AM-02）
 *
 * - 传入 orderId 时：选择文件即走 presign → PUT → confirm 三步直传
 * - 未传 orderId（创建订单页）时：文件先暂存，提交时由父组件调用 uploadAll(orderId)
 * - 前端先做扩展名白名单 + 200 MB 校验，后端仍会二次校验
 * - 单个文件失败可单独重试（会重新申请预签名 URL）
 */
import { computed, ref } from 'vue'
import {
  ALLOWED_ATTACHMENT_EXTENSIONS,
  MAX_ATTACHMENT_SIZE_BYTES,
  attachmentApi,
  validateAttachmentFile,
  type AttachmentItem,
} from '../../api/modules/attachment'
import { directUpload } from '../../composables/useDirectUpload'
import { errorMessage } from '../../api/http'

const props = withDefaults(
  defineProps<{
    /** 已存在的订单 ID；为空表示订单尚未创建（创建订单页暂存模式）*/
    orderId?: number | null
    disabled?: boolean
  }>(),
  { orderId: null, disabled: false }
)

const emit = defineEmits<{
  (e: 'uploaded', attachment: AttachmentItem): void
  (e: 'error', message: string): void
}>()

type ItemStatus = 'staged' | 'uploading' | 'done' | 'failed'

interface UploadItem {
  uid: number
  file: File
  status: ItemStatus
  progress: number
  error: string
  attachment?: AttachmentItem
}

const items = ref<UploadItem[]>([])
let nextUid = 0

const activeOrderId = ref<number | null>(props.orderId ?? null)

const acceptAttr = ALLOWED_ATTACHMENT_EXTENSIONS.map((ext) => `.${ext}`).join(',')
const maxSizeMb = Math.round(MAX_ATTACHMENT_SIZE_BYTES / 1024 / 1024)

const hint = computed(() => {
  const base = `支持 ${ALLOWED_ATTACHMENT_EXTENSIONS.join(' / ')}，单个文件 ≤ ${maxSizeMb} MB`
  return activeOrderId.value ? `${base}；选择后自动开始上传。` : `${base}；点击“提交并送审”时随订单一起上传。`
})

const pendingCount = computed(
  () => items.value.filter((item) => item.status === 'staged' || item.status === 'failed').length
)

function sizeText(bytes: number): string {
  if (bytes >= 1024 * 1024) return `${(bytes / 1024 / 1024).toFixed(1)} MB`
  if (bytes >= 1024) return `${Math.round(bytes / 1024)} KB`
  return `${bytes} B`
}

function onPick(event: Event) {
  const input = event.target as HTMLInputElement
  const picked = Array.from(input.files ?? [])
  input.value = ''
  if (!picked.length) return

  const orderId = activeOrderId.value
  for (const file of picked) {
    const invalid = validateAttachmentFile(file)
    if (invalid) {
      emit('error', invalid)
      continue
    }
    const item: UploadItem = {
      uid: (nextUid += 1),
      file,
      status: 'staged',
      progress: 0,
      error: '',
    }
    items.value.push(item)
    if (orderId) void uploadItem(item, orderId)
  }
}

async function uploadItem(item: UploadItem, orderId: number): Promise<AttachmentItem | null> {
  item.status = 'uploading'
  item.progress = 0
  item.error = ''
  try {
    const attachment = await directUpload<AttachmentItem>(item.file, {
      presign: async (file) => {
        const res = await attachmentApi.presign(orderId, file)
        const session = res.data
        if (!session?.presigned_url || !session.object_key) {
          throw new Error('后端未返回预签名上传地址')
        }
        return { presigned_url: session.presigned_url, object_key: session.object_key }
      },
      confirm: async (file, session) => {
        const res = await attachmentApi.confirm(orderId, file, session.object_key)
        return res.data as AttachmentItem
      },
      onProgress: (percent) => {
        item.progress = percent
      },
    })
    item.status = 'done'
    item.progress = 100
    item.attachment = attachment
    emit('uploaded', attachment)
    return attachment
  } catch (err) {
    item.status = 'failed'
    item.error = errorMessage(err, '上传失败，请重试')
    emit('error', item.error)
    return null
  }
}

/** 重试单个失败/暂存文件 */
async function retry(item: UploadItem) {
  if (props.disabled || item.status === 'uploading') return
  const orderId = activeOrderId.value
  if (!orderId) {
    item.error = '订单尚未创建，提交订单时会自动上传'
    return
  }
  await uploadItem(item, orderId)
}

function removeItem(item: UploadItem) {
  if (item.status === 'uploading') return
  items.value = items.value.filter((i) => i.uid !== item.uid)
}

/** 父组件在订单创建后调用：上传所有暂存/失败文件 */
async function uploadAll(orderId: number): Promise<{ uploaded: AttachmentItem[]; failed: number }> {
  activeOrderId.value = orderId
  const queue = items.value.filter((item) => item.status === 'staged' || item.status === 'failed')
  const uploaded: AttachmentItem[] = []
  let failed = 0
  await Promise.all(
    queue.map(async (item) => {
      const attachment = await uploadItem(item, orderId)
      if (attachment) uploaded.push(attachment)
      else failed += 1
    })
  )
  return { uploaded, failed }
}

defineExpose({ uploadAll, pendingCount })
</script>

<template>
  <div class="attachment-uploader">
    <div class="picker-row">
      <label class="pick-btn" :class="{ disabled: disabled }">
        <input
          type="file"
          multiple
          :accept="acceptAttr"
          :disabled="disabled"
          @change="onPick"
        />
        选择文件
      </label>
      <span class="hint">{{ hint }}</span>
    </div>

    <ul v-if="items.length" class="upload-list">
      <li v-for="item in items" :key="item.uid" class="upload-item">
        <div class="line">
          <span class="fname" :title="item.file.name">{{ item.file.name }}</span>
          <span class="size">{{ sizeText(item.file.size) }}</span>
          <span class="state" :class="item.status">
            <template v-if="item.status === 'staged'">待上传</template>
            <template v-else-if="item.status === 'uploading'">{{ item.progress }}%</template>
            <template v-else-if="item.status === 'done'">已上传</template>
            <template v-else>失败</template>
          </span>
          <button
            v-if="item.status === 'failed'"
            type="button"
            class="link"
            :disabled="disabled"
            @click="retry(item)"
          >重试</button>
          <button
            v-if="item.status === 'staged' || item.status === 'failed'"
            type="button"
            class="link muted-link"
            @click="removeItem(item)"
          >移除</button>
        </div>
        <div v-if="item.status === 'uploading'" class="bar">
          <span :style="{ width: item.progress + '%' }" />
        </div>
        <p v-if="item.error" class="err">{{ item.error }}</p>
      </li>
    </ul>
  </div>
</template>

<style scoped>
.attachment-uploader { font-size: 13px; }
.picker-row { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; }
.pick-btn {
  position: relative;
  display: inline-block;
  padding: 7px 14px;
  border: 1px dashed #409eff;
  border-radius: 4px;
  color: #409eff;
  cursor: pointer;
  background: #ecf5ff;
  font-size: 13px;
}
.pick-btn.disabled { opacity: 0.6; cursor: not-allowed; }
.pick-btn input { position: absolute; inset: 0; opacity: 0; cursor: inherit; }
.hint { color: #909399; font-size: 12px; }

.upload-list { margin: 12px 0 0; padding: 0; list-style: none; }
.upload-item {
  padding: 8px 12px;
  background: #fafbfc;
  border: 1px solid #ebeef5;
  border-radius: 4px;
  margin-bottom: 6px;
}
.line { display: flex; align-items: center; gap: 10px; }
.fname { flex: 1; min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-family: Menlo, Consolas, monospace; }
.size { color: #909399; font-size: 12px; flex-shrink: 0; }
.state { font-size: 12px; flex-shrink: 0; }
.state.staged { color: #e6a23c; }
.state.uploading { color: #409eff; }
.state.done { color: #67c23a; }
.state.failed { color: #f56c6c; }
.link {
  background: none; border: none; padding: 0 4px; color: #409eff; cursor: pointer; font-size: 12px;
}
.link:disabled { color: #c0c4cc; cursor: not-allowed; }
.muted-link { color: #909399; }
.bar { height: 4px; border-radius: 2px; background: #ebeef5; margin-top: 6px; overflow: hidden; }
.bar span { display: block; height: 100%; background: #409eff; transition: width 0.2s; }
.err { color: #f56c6c; font-size: 12px; margin: 6px 0 0; }
</style>
