<script setup lang="ts">
/**
 * P1-T32 + P2-T14 · DS 任务详情
 *
 * - 读取 S-06 的 { task, order } 结构（含需求基线与客户参考素材）
 * - S-08 附件下载（后端 302 → 5 分钟预签名 GET）
 * - 版本历史上传人、时间、状态、下载（A-26 / S-07）
 * - 上传新版本（A-08 三步直传）、提交内部评审（A-09）、标记终稿（A-17）
 */
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { materialTaskApi, type TaskSummary } from '../../api/modules/materialTask'
import { versionApi, type VersionItem } from '../../api/modules/designVersion'
import { attachmentApi, type AttachmentItem } from '../../api/modules/attachment'
import { errorMessage } from '../../api/http'
import TaskStatusTag from '../../components/common/TaskStatusTag.vue'
import VersionStatusTag from '../../components/common/VersionStatusTag.vue'
import VersionUploader from '../../components/ds/VersionUploader.vue'
import FeedbackPanel from '../../components/ds/FeedbackPanel.vue'
import { VersionStatus } from '../../types/enums'
import type { OrderDetail } from '../../api/modules/order'

const route = useRoute()
const router = useRouter()

const taskId = computed(() => Number(route.params.taskId))
const task = ref<TaskSummary | null>(null)
const order = ref<OrderDetail | null>(null)
const versions = ref<VersionItem[]>([])
const loading = ref(true)
const error = ref('')
const actionError = ref('')
const acting = ref(false)

const canUpload = computed(
  () => !!task.value && ['PENDING_DESIGN', 'PENDING_REVISE'].includes(task.value.status)
)

const assigneeName = computed(() => {
  const matched = (order.value?.tasks || []).find((item: any) => item.id === taskId.value)
  return matched?.assignee_name || ''
})

const nextVersionNo = computed(() => {
  const max = versions.value.reduce((acc, item) => {
    const num = Number((item.version_no || '').replace(/^V/i, ''))
    return Number.isFinite(num) ? Math.max(acc, num) : acc
  }, 0)
  return `V${max + 1}`
})

const countdown = computed(() => {
  if (!task.value?.task_deadline) return ''
  const deadline = new Date(task.value.task_deadline).getTime()
  const diff = deadline - Date.now()
  if (diff <= 0) return '已过期'
  const days = Math.floor(diff / (1000 * 60 * 60 * 24))
  const hours = Math.floor((diff % (1000 * 60 * 60 * 24)) / (1000 * 60 * 60))
  if (days > 0) return `剩 ${days} 天 ${hours} 小时`
  const mins = Math.floor((diff % (1000 * 60 * 60)) / (1000 * 60))
  return `剩 ${hours} 小时 ${mins} 分钟`
})

async function load() {
  loading.value = true
  error.value = ''
  try {
    const res = await materialTaskApi.getById(taskId.value)
    task.value = (res.data?.task as TaskSummary) || null
    order.value = res.data?.order || null
    if (!task.value) {
      error.value = '任务不存在或无权查看'
      return
    }
    await loadVersions()
  } catch (e) {
    error.value = errorMessage(e, '加载任务详情失败')
  } finally {
    loading.value = false
  }
}

async function loadVersions() {
  if (!task.value) return
  const res = await versionApi.listForOrder(task.value.order_id)
  versions.value = ((res.data || []) as VersionItem[])
    .filter((item) => item.task_id === taskId.value)
    .sort((a, b) => b.id - a.id)
}

async function submitReview(version: VersionItem) {
  if (!window.confirm(`确认把 ${version.version_no} 提交内部评审？提交后不能再修改该版本。`)) return
  await runAction(async () => {
    await versionApi.submitReview(version.id)
  }, '已提交内部评审')
}

async function markFinal(version: VersionItem) {
  if (!window.confirm(`确认把 ${version.version_no} 标记为最终交付版本？同一订单只能有一个终稿。`)) return
  await runAction(async () => {
    await versionApi.markFinal(version.id)
  }, '已标记为终稿')
}

async function download(version: VersionItem) {
  try {
    await versionApi.download(version.id, version.file?.file_name || `${version.version_no}.bin`)
  } catch (e) {
    actionError.value = errorMessage(e, '下载失败，请重试')
  }
}

// --- S-08：客户参考素材（订单附件）下载 ---
const downloadingAttachmentId = ref<number | null>(null)

function formatSize(bytes: number): string {
  if (bytes >= 1024 * 1024) return `${(bytes / 1024 / 1024).toFixed(1)} MB`
  if (bytes >= 1024) return `${Math.round(bytes / 1024)} KB`
  return `${bytes} B`
}

async function downloadAttachment(attachment: AttachmentItem) {
  downloadingAttachmentId.value = attachment.id
  actionError.value = ''
  try {
    await attachmentApi.download(attachment.id, attachment.file_name)
  } catch (e) {
    actionError.value = errorMessage(e, '附件下载失败')
  } finally {
    downloadingAttachmentId.value = null
  }
}

/** 反馈标记已解决后只静默刷新版本列表，避免整页 loading */
function onFeedbackResolved() {
  loadVersions()
}

async function runAction(action: () => Promise<void>, success: string) {
  acting.value = true
  actionError.value = ''
  try {
    await action()
    await Promise.all([load(), loadVersions()])
    window.alert(success)
  } catch (e) {
    actionError.value = errorMessage(e, '操作失败')
  } finally {
    acting.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="page">
    <header class="page-header">
      <div class="crumb">
        <button class="btn ghost small" @click="router.push('/ds/tasks')">← 返回任务列表</button>
        <h1>任务详情</h1>
      </div>
      <div class="header-meta">
        <span v-if="task" class="mono">{{ task.order_no }}</span>
        <template v-if="task"><TaskStatusTag :status="task.status" /></template>
      </div>
    </header>

    <p v-if="loading" class="muted">加载中…</p>
    <p v-else-if="error" class="global-err">{{ error }}</p>

    <template v-else-if="task">
      <section class="card">
        <h2>任务信息</h2>
        <dl class="kv-grid">
          <div class="kv"><dt>订单号</dt><dd class="mono">{{ task.order_no }}</dd></div>
          <div class="kv"><dt>物料类型</dt><dd>{{ task.material_type }}</dd></div>
          <div class="kv"><dt>规格尺寸</dt><dd>{{ task.spec_size }}</dd></div>
          <div class="kv"><dt>状态</dt><dd><TaskStatusTag :status="task.status" /></dd></div>
          <div class="kv"><dt>优先级</dt><dd>{{ task.priority }}</dd></div>
          <div class="kv"><dt>指派设计师</dt><dd>{{ assigneeName || '（未指派）' }}</dd></div>
          <div class="kv"><dt>任务截止</dt><dd>{{ task.task_deadline }}</dd></div>
          <div class="kv"><dt>剩余时间</dt>
            <dd :class="{ urgent: countdown.startsWith('剩') && !countdown.includes('天') }">{{ countdown }}</dd>
          </div>
          <div class="kv"><dt>创建时间</dt><dd>{{ task.created_at }}</dd></div>
          <div class="kv" v-if="order"><dt>客户</dt><dd>{{ order.customer_name }}</dd></div>
        </dl>
      </section>

      <!-- S-08：客户参考素材（AM 上传的订单附件）-->
      <section v-if="order" class="card">
        <h2>客户参考素材（{{ order.attachments?.length || 0 }} 个）</h2>
        <ul v-if="order.attachments?.length" class="attach-list">
          <li v-for="a in order.attachments" :key="a.id">
            <button
              class="link fname"
              :disabled="downloadingAttachmentId === a.id"
              @click="downloadAttachment(a)"
            >{{ a.file_name }}</button>
            <span class="muted">{{ downloadingAttachmentId === a.id ? '下载中…' : formatSize(a.file_size) }}</span>
          </li>
        </ul>
        <p v-else class="muted">（AM 未上传参考素材）</p>
      </section>

      <section class="card">
        <h2>版本交付</h2>

        <p v-if="actionError" class="err">{{ actionError }}</p>

        <p v-if="!versions.length" class="muted empty">还没有版本，先上传第一稿。</p>

        <table v-else class="version-table">
          <thead>
            <tr>
              <th>版本</th>
              <th>状态</th>
              <th>设计说明</th>
              <th>变更要点</th>
              <th>文件</th>
              <th>提交时间</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="version in versions" :key="version.id">
              <td class="mono">{{ version.version_no }}<span v-if="version.is_final" class="final-flag">终稿</span></td>
              <td><VersionStatusTag :status="version.status" /></td>
              <td class="note">{{ version.design_note }}</td>
              <td class="note">{{ version.change_summary || '—' }}</td>
              <td>
                <button v-if="version.file" class="link" @click="download(version)">
                  {{ version.file.file_name }}
                </button>
                <span v-else>—</span>
              </td>
              <td class="muted">{{ version.submitted_at || '未提交' }}</td>
              <td class="ops">
                <button
                  v-if="version.status === VersionStatus.DRAFT && canUpload"
                  class="btn small primary"
                  :disabled="acting"
                  @click="submitReview(version)"
                >提交内部评审</button>
                <button
                  v-if="version.status === VersionStatus.CLIENT_CONFIRMED"
                  class="btn small primary"
                  :disabled="acting"
                  @click="markFinal(version)"
                >标记终稿</button>
                <span v-if="version.status === VersionStatus.RELEASED" class="muted">等待客户评审</span>
                <span v-if="version.status === VersionStatus.PENDING_INTERNAL_REVIEW" class="muted">BO 评审中</span>
              </td>
            </tr>
          </tbody>
        </table>

        <div class="uploader-wrap">
          <VersionUploader
            v-if="canUpload"
            :task-id="taskId"
            :next-version-no="nextVersionNo"
            :disabled="acting"
            @uploaded="loadVersions"
          />
          <p v-else class="muted">当前任务状态不可上传新版本（仅“待设计 / 待修改”可上传）。</p>
        </div>
      </section>

      <!-- P3-T12 · 客户反馈（按版本聚合，未解决可标记已解决，EXPIRED 只读）-->
      <section class="card">
        <FeedbackPanel :task-id="taskId" :versions="versions" @resolved="onFeedbackResolved" />
      </section>
    </template>
  </div>
</template>

<style scoped>
.page { padding: 24px; max-width: 1080px; margin: 0 auto; }
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; gap: 12px; }
.crumb { display: flex; align-items: center; gap: 12px; }
.crumb h1 { margin: 0; font-size: 20px; }
.header-meta { display: flex; align-items: center; gap: 12px; }
.mono { font-family: Menlo, Consolas, monospace; color: #409eff; font-size: 14px; }

.card { background: #fff; border-radius: 8px; box-shadow: 0 1px 4px rgba(0,0,0,0.05); padding: 20px 24px; margin-bottom: 16px; }
.card h2 { margin: 0 0 14px; font-size: 15px; padding-left: 8px; border-left: 3px solid #409eff; }

.kv-grid { margin: 0; display: grid; grid-template-columns: repeat(2, 1fr); gap: 10px 24px; }
.kv { display: flex; gap: 8px; }
.kv dt { color: #909399; font-size: 13px; min-width: 84px; flex-shrink: 0; }
.kv dd { margin: 0; font-size: 14px; color: #333; }
.urgent { color: #f56c6c; font-weight: 500; }

.version-table { width: 100%; border-collapse: collapse; font-size: 13px; }
.version-table th, .version-table td { text-align: left; padding: 8px 10px; border-bottom: 1px solid #ebeef5; vertical-align: top; }
.version-table th { color: #909399; font-weight: 500; background: #fafafa; }
.note { max-width: 240px; color: #606266; word-break: break-word; }
.ops { white-space: nowrap; }
.final-flag { margin-left: 6px; font-size: 11px; color: #303133; background: #f0f0f0; border-radius: 3px; padding: 1px 5px; }
.uploader-wrap { margin-top: 18px; }
.empty { margin: 4px 0 14px; }

.btn { padding: 8px 16px; border-radius: 4px; border: 1px solid #dcdfe6; background: #fff; cursor: pointer; font-size: 14px; }
.btn.small { padding: 6px 12px; font-size: 13px; }
.btn.primary { background: #409eff; border-color: #409eff; color: #fff; }
.btn:disabled { opacity: 0.6; cursor: not-allowed; }
.btn.ghost { background: #fff; color: #606266; }
.btn.ghost:hover:not(:disabled) { color: #409eff; border-color: #c6e2ff; background: #ecf5ff; }
.link { border: none; background: none; color: #409eff; cursor: pointer; padding: 0; font-size: 13px; text-decoration: underline; }
.link:disabled { color: #c0c4cc; cursor: not-allowed; }

.attach-list { margin: 0; padding: 0; list-style: none; }
.attach-list li {
  display: flex; justify-content: space-between; align-items: center;
  padding: 8px 12px; background: #fafbfc; border-radius: 4px; margin-bottom: 6px;
}
.fname { font-family: Menlo, Consolas, monospace; font-size: 13px; word-break: break-all; }

.err { color: #f56c6c; background: #fef0f0; border-radius: 4px; padding: 8px 10px; font-size: 13px; }
.muted { color: #999; font-size: 13px; }
.global-err { color: #f56c6c; background: #fef0f0; border-radius: 4px; padding: 10px; }
</style>
