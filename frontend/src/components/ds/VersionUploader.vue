/**
 * P2-T13 · 设计版本上传组件（A-08 三步：申请 → 直传对象存储 → 确认生成版本）
 *
 * - 文件校验与服务端一致：≤200MB，扩展名白名单
 * - 上传进度来自 XHR，直传失败给出可读原因
 * - 成功后 emit('uploaded', version)，由页面刷新版本历史
 */
<script setup lang="ts">
import { ref } from 'vue'
import { versionApi, type VersionItem } from '../../api/modules/designVersion'
import { errorMessage } from '../../api/http'

const props = defineProps<{ taskId: number; disabled?: boolean; nextVersionNo?: string }>()
const emit = defineEmits<{ uploaded: [version: VersionItem] }>()

const MAX_FILE_SIZE = 200 * 1024 * 1024
const ALLOWED_EXTENSIONS = [
  'psd', 'ai', 'sketch', 'fig', 'png', 'jpg', 'jpeg', 'gif', 'svg', 'pdf',
  'zip', 'rar', 'doc', 'docx', 'xls', 'xlsx', 'ppt', 'pptx',
]

const fileInput = ref<HTMLInputElement | null>(null)
const file = ref<File | null>(null)
const designNote = ref('')
const changeSummary = ref('')
const progress = ref(0)
const uploading = ref(false)
const error = ref('')
const doneMessage = ref('')

function extensionOf(name: string): string {
  const index = name.lastIndexOf('.')
  return index >= 0 ? name.slice(index + 1).toLowerCase() : ''
}

function resetForm() {
  file.value = null
  designNote.value = ''
  changeSummary.value = ''
  progress.value = 0
  if (fileInput.value) fileInput.value.value = ''
}

function onFileChange(event: Event) {
  const selected = (event.target as HTMLInputElement).files?.[0] || null
  error.value = ''
  doneMessage.value = ''
  if (!selected) {
    file.value = null
    return
  }
  if (selected.size > MAX_FILE_SIZE) {
    error.value = '文件超过 200MB，请压缩后重试'
    file.value = null
    return
  }
  if (!ALLOWED_EXTENSIONS.includes(extensionOf(selected.name))) {
    error.value = `不支持的文件类型 .${extensionOf(selected.name) || '未知'}`
    file.value = null
    return
  }
  file.value = selected
}

async function submit() {
  error.value = ''
  doneMessage.value = ''
  if (!file.value) {
    error.value = '请选择设计文件'
    return
  }
  if (!designNote.value.trim()) {
    error.value = '请填写设计说明'
    return
  }
  uploading.value = true
  progress.value = 0
  try {
    const version = await versionApi.uploadVersion(
      props.taskId,
      file.value,
      designNote.value.trim(),
      changeSummary.value.trim() || undefined,
      (percent) => {
        progress.value = percent
      }
    )
    doneMessage.value = `版本 ${version.version_no} 已创建（草稿），可提交内部评审`
    resetForm()
    emit('uploaded', version)
  } catch (e) {
    error.value = errorMessage(e, '版本上传失败')
  } finally {
    uploading.value = false
  }
}
</script>

<template>
  <div class="uploader">
    <div class="uploader-head">
      <span class="uploader-title">上传新版本</span>
      <span v-if="nextVersionNo" class="next-no">预计版本号 {{ nextVersionNo }}</span>
    </div>

    <div class="field">
      <label>设计文件</label>
      <input ref="fileInput" type="file" :disabled="disabled || uploading" @change="onFileChange" />
      <p class="hint">支持 psd / ai / sketch / fig / png / jpg / pdf / zip 等，单个文件不超过 200MB</p>
    </div>

    <div class="field">
      <label>设计说明 <span class="required">*</span></label>
      <textarea v-model="designNote" rows="3" maxlength="2000" :disabled="disabled || uploading"
        placeholder="本稿的设计思路、重点说明（必填，将展示给 BO 与客户）" />
    </div>

    <div class="field">
      <label>变更要点</label>
      <input v-model="changeSummary" type="text" maxlength="200" :disabled="disabled || uploading"
        placeholder="相对上一稿改了什么（选填）" />
    </div>

    <div v-if="uploading" class="progress">
      <div class="progress-bar" :style="{ width: progress + '%' }" />
      <span class="progress-text">{{ progress }}%</span>
    </div>

    <p v-if="error" class="err">{{ error }}</p>
    <p v-if="doneMessage" class="ok">{{ doneMessage }}</p>

    <div class="actions">
      <button class="btn primary" :disabled="disabled || uploading || !file" @click="submit">
        {{ uploading ? '上传中…' : '上传并创建草稿版本' }}
      </button>
    </div>

    <p v-if="disabled" class="hint">当前任务状态不允许上传新版本（仅“待设计 / 待修改”可上传）。</p>
  </div>
</template>

<style scoped>
.uploader { border: 1px dashed #dcdfe6; border-radius: 6px; background: #f7f9fc; padding: 16px; }
.uploader-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; }
.uploader-title { font-size: 14px; font-weight: 600; color: #333; }
.next-no { font-size: 12px; color: #909399; }
.field { margin-bottom: 12px; }
.field label { display: block; font-size: 13px; color: #606266; margin-bottom: 6px; }
.field input[type='text'], .field textarea {
  width: 100%; box-sizing: border-box; padding: 8px 10px; border: 1px solid #dcdfe6;
  border-radius: 4px; font-size: 13px; font-family: inherit;
}
.field textarea { resize: vertical; }
.required { color: #f56c6c; }
.hint { margin: 6px 0 0; font-size: 12px; color: #909399; }
.progress { position: relative; height: 18px; background: #eef2f7; border-radius: 4px; overflow: hidden; margin: 8px 0; }
.progress-bar { height: 100%; background: #409eff; transition: width 0.2s ease; }
.progress-text { position: absolute; inset: 0; font-size: 11px; color: #303133; text-align: center; line-height: 18px; }
.err { color: #f56c6c; background: #fef0f0; border-radius: 4px; padding: 6px 10px; font-size: 13px; margin: 8px 0 0; }
.ok { color: #67c23a; background: #f0f9eb; border-radius: 4px; padding: 6px 10px; font-size: 13px; margin: 8px 0 0; }
.actions { margin-top: 8px; }
.btn { padding: 8px 16px; border-radius: 4px; border: 1px solid #dcdfe6; background: #fff; cursor: pointer; font-size: 14px; }
.btn.primary { background: #409eff; border-color: #409eff; color: #fff; }
.btn:disabled { opacity: 0.6; cursor: not-allowed; }
</style>
