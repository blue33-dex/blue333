<script setup lang="ts">
/**
 * P3-T10 · AM 客户评审页
 *
 * 链路：BO 放行（RELEASED）→ AM 发起客户评审（A-13）→ 记录反馈（A-14）
 *      → 客户确认（A-16，同时触发 MaterialTask → APPROVED）→ AM 标记已交付（A-18）
 *
 * 关键约束：
 *   - 反馈必须绑定具体版本编号（BR-09）
 *   - 修改类/拒绝类反馈会让对应设计任务回到“待修改”（与后端联动一致）
 *   - 客户确认时未解决反馈不阻塞，但会给出 warning
 */
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { orderApi, type OrderDetail, type OrderTask } from '../../api/modules/order'
import { versionApi, type VersionItem } from '../../api/modules/designVersion'
import {
  clientReviewApi,
  type ConfirmVersionResponse,
  type FeedbackResponse,
} from '../../api/modules/clientReview'
import { errorMessage } from '../../api/http'
import OrderStatusTag from '../../components/common/OrderStatusTag.vue'
import VersionStatusTag from '../../components/common/VersionStatusTag.vue'
import FeedbackStatusTag from '../../components/common/FeedbackStatusTag.vue'
import {
  FeedbackSource,
  FeedbackStatus,
  FeedbackType,
  MaterialTaskStatus,
  OrderStatus,
  VersionStatus,
} from '../../types/enums'

const route = useRoute()
const router = useRouter()
const orderId = computed(() => Number(route.params.orderId))

const order = ref<OrderDetail | null>(null)
const versions = ref<VersionItem[]>([])
const feedbacks = ref<Record<number, FeedbackResponse[]>>({})
const loading = ref(true)
const error = ref('')
const actionError = ref('')
const notice = ref('')
const acting = ref(false)

/** 记录反馈表单（同一时刻只展开一个版本）*/
const feedbackOpenFor = ref<number | null>(null)
const feedbackType = ref<string>(FeedbackType.MODIFICATION_SUGGESTION)
const feedbackSource = ref<string>(FeedbackSource.CLIENT)
const feedbackContent = ref('')

/** 客户确认表单 */
const confirmOpenFor = ref<number | null>(null)
const confirmerName = ref('')

const feedbackTypeOptions: { value: string; label: string; hint: string }[] = [
  {
    value: FeedbackType.MODIFICATION_SUGGESTION,
    label: '修改建议',
    hint: '记录后对应设计任务回到“待修改”，设计师需上传新版本',
  },
  { value: FeedbackType.REJECTION, label: '退回', hint: '记录后对应设计任务回到“待修改”' },
  { value: FeedbackType.APPROVAL, label: '认可', hint: '仅记录，不改变任务状态' },
]

const feedbackSourceOptions: { value: string; label: string }[] = [
  { value: FeedbackSource.CLIENT, label: '客户反馈' },
  { value: FeedbackSource.BUSINESS_OWNER, label: '业务负责人代录' },
]

const sortedVersions = computed(() => [...versions.value].sort((a, b) => b.id - a.id))

const taskById = computed(() => {
  const map = new Map<number, OrderTask>()
  for (const task of order.value?.tasks || []) map.set(task.id, task)
  return map
})

/** 客户已确认或已终稿的版本存在，才允许交付（对应 CONFLICT-02 的前置条件之一）*/
const hasConfirmedVersion = computed(() =>
  versions.value.some(
    (version) =>
      version.status === VersionStatus.CLIENT_CONFIRMED || version.status === VersionStatus.FINAL
  )
)

const allTasksApproved = computed(() => {
  const tasks = order.value?.tasks || []
  return tasks.length > 0 && tasks.every((task) => task.status === MaterialTaskStatus.APPROVED)
})

const approvedCount = computed(
  () => (order.value?.tasks || []).filter((task) => task.status === MaterialTaskStatus.APPROVED).length
)

const taskCount = computed(() => (order.value?.tasks || []).length)

const canDeliver = computed(
  () =>
    order.value?.status === OrderStatus.IN_PRODUCTION &&
    allTasksApproved.value &&
    hasConfirmedVersion.value
)

const currentFeedbackTypeHint = computed(
  () => feedbackTypeOptions.find((option) => option.value === feedbackType.value)?.hint || ''
)

function feedbackFor(version: VersionItem): FeedbackResponse[] {
  return feedbacks.value[version.id] || []
}

function openFeedbackCount(version: VersionItem): number {
  return feedbackFor(version).filter((item) => item.status === FeedbackStatus.UNRESOLVED).length
}

function formatSize(bytes?: number): string {
  if (!bytes) return '—'
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
}

function taskLabel(version: VersionItem): string {
  const task = taskById.value.get(version.task_id)
  if (!task) return `${version.material_type} ${version.spec_size}`
  return `${task.material_type} ${task.spec_size}`
}

async function loadFeedbacks() {
  const targets = versions.value.filter((version) =>
    [
      VersionStatus.IN_CLIENT_REVIEW,
      VersionStatus.CLIENT_CONFIRMED,
      VersionStatus.FINAL,
    ].includes(version.status as VersionStatus)
  )
  const results = await Promise.all(
    targets.map(async (version) => {
      try {
        const res = await clientReviewApi.listFeedbacks(version.id)
        return { versionId: version.id, items: (res.data || []) as FeedbackResponse[] }
      } catch {
        return { versionId: version.id, items: [] as FeedbackResponse[] }
      }
    })
  )
  const map: Record<number, FeedbackResponse[]> = {}
  for (const result of results) map[result.versionId] = result.items
  feedbacks.value = map
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    const [detailRes, versionRes] = await Promise.all([
      orderApi.getOrderDetail(orderId.value),
      versionApi.listForOrder(orderId.value),
    ])
    order.value = (detailRes.data as OrderDetail) || null
    versions.value = ((versionRes.data || []) as VersionItem[]) || []
    await loadFeedbacks()
  } catch (e) {
    error.value = errorMessage(e, '加载客户评审数据失败')
  } finally {
    loading.value = false
  }
}

async function runAction(action: () => Promise<string>, success: string) {
  acting.value = true
  actionError.value = ''
  notice.value = ''
  try {
    const detail = await action()
    notice.value = detail || success
    await load()
  } catch (e) {
    actionError.value = errorMessage(e, '操作失败')
  } finally {
    acting.value = false
  }
}

async function initiateReview(version: VersionItem) {
  if (!window.confirm(`确认对 ${version.version_no} 发起客户评审？发起后该版本进入客户评审中。`)) return
  await runAction(async () => {
    await clientReviewApi.initiateClientReview(version.id)
    return `已对 ${version.version_no} 发起客户评审，请记录客户反馈。`
  }, '已发起客户评审')
}

function openFeedback(version: VersionItem) {
  feedbackOpenFor.value = version.id
  feedbackType.value = FeedbackType.MODIFICATION_SUGGESTION
  feedbackSource.value = FeedbackSource.CLIENT
  feedbackContent.value = ''
  actionError.value = ''
  notice.value = ''
}

function closeFeedback() {
  feedbackOpenFor.value = null
  feedbackContent.value = ''
}

async function submitFeedback(version: VersionItem) {
  const content = feedbackContent.value.trim()
  if (!content) {
    actionError.value = '反馈内容必填'
    return
  }
  await runAction(async () => {
    const res = await clientReviewApi.recordFeedback(version.id, {
      feedback_type: feedbackType.value,
      source: feedbackSource.value,
      content,
    })
    const created = res.data as FeedbackResponse
    closeFeedback()
    return `已记录反馈 ${created.feedback_no}（绑定版本 ${version.version_no}）。`
  }, '已记录反馈')
}

function openConfirm(version: VersionItem) {
  confirmOpenFor.value = version.id
  confirmerName.value = order.value?.customer_name || ''
  actionError.value = ''
  notice.value = ''
}

function closeConfirm() {
  confirmOpenFor.value = null
  confirmerName.value = ''
}

async function submitConfirm(version: VersionItem) {
  const name = confirmerName.value.trim()
  if (!name) {
    actionError.value = '请填写客户确认人姓名'
    return
  }
  await runAction(async () => {
    const res = await clientReviewApi.confirmVersion(version.id, name)
    const result = res.data as ConfirmVersionResponse
    closeConfirm()
    const base = `客户 ${result.confirmer_name} 已确认 ${version.version_no}，对应设计任务已转为“已批准”。`
    return result.warning ? `${base} ${result.warning}` : base
  }, '客户确认完成')
}

async function deliver() {
  if (!canDeliver.value) return
  if (!window.confirm('确认该订单已交付给客户？订单将进入“已完成”终态且不可回退。')) return
  await runAction(async () => {
    await orderApi.deliverOrder(orderId.value)
    return '订单已标记为已交付，等待业务负责人归档。'
  }, '订单已标记为已交付')
}

onMounted(load)
</script>

<template>
  <div class="page">
    <header class="page-header">
      <div class="crumb">
        <button class="btn ghost small" @click="router.push('/am/orders')">← 返回我的订单</button>
        <h1>客户评审</h1>
      </div>
      <div class="header-meta">
        <span v-if="order" class="mono">{{ order.order_no }}</span>
        <template v-if="order"><OrderStatusTag :status="order.status" /></template>
      </div>
    </header>

    <p v-if="loading" class="muted">加载中…</p>
    <p v-else-if="error" class="global-err">{{ error }}</p>

    <template v-else-if="order">
      <p v-if="notice" class="ok">{{ notice }}</p>
      <p v-if="actionError" class="err">{{ actionError }}</p>

      <section class="card">
        <h2>订单概览</h2>
        <dl class="kv-grid">
          <div class="kv"><dt>客户名称</dt><dd>{{ order.customer_name }}</dd></div>
          <div class="kv"><dt>交付截止</dt><dd>{{ order.delivery_deadline }}</dd></div>
          <div class="kv"><dt>使用场景</dt><dd>{{ order.use_scene || '—' }}</dd></div>
          <div class="kv"><dt>业务负责人</dt><dd>{{ order.am_name }}</dd></div>
          <div class="kv"><dt>设计任务</dt>
            <dd>{{ taskCount }} 个（已批准 {{ approvedCount }} 个）</dd>
          </div>
          <div class="kv"><dt>版本数量</dt><dd>{{ versions.length }} 个</dd></div>
        </dl>
        <p class="muted tiny">
          客户评审仅在“生产中”的订单上可发起；BO 放行后版本状态为“已放行”，此时才出现“发起客户评审”按钮。
        </p>
      </section>

      <section class="card">
        <h2>版本与客户评审（{{ versions.length }}）</h2>
        <p v-if="!versions.length" class="muted">该订单暂无设计版本，等待设计师上传。</p>

        <template v-else>
        <article v-for="version in sortedVersions" :key="version.id" class="version-card">
          <div class="version-head">
            <div class="version-title">
              <span class="mono">{{ version.version_no }}</span>
              <VersionStatusTag :status="version.status" />
              <span v-if="version.is_final" class="final-flag">终稿</span>
              <span class="task-ref">{{ taskLabel(version) }}</span>
            </div>
            <div class="version-ops">
              <button
                v-if="version.status === VersionStatus.RELEASED"
                class="btn small primary"
                :disabled="acting"
                @click="initiateReview(version)"
              >发起客户评审</button>
              <button
                v-if="version.status === VersionStatus.IN_CLIENT_REVIEW"
                class="btn small"
                :disabled="acting"
                @click="feedbackOpenFor === version.id ? closeFeedback() : openFeedback(version)"
              >{{ feedbackOpenFor === version.id ? '收起反馈录入' : '记录客户反馈' }}</button>
              <button
                v-if="version.status === VersionStatus.IN_CLIENT_REVIEW"
                class="btn small primary"
                :disabled="acting"
                @click="openConfirm(version)"
              >客户确认</button>
              <span v-if="version.status === VersionStatus.PENDING_INTERNAL_REVIEW" class="muted">BO 评审中，暂不可发起</span>
              <span v-else-if="version.status === VersionStatus.DRAFT" class="muted">草稿，先由设计师提交评审</span>
              <span v-else-if="version.status === VersionStatus.CLIENT_CONFIRMED" class="muted">已确认，等待设计师标记终稿</span>
              <span v-else-if="version.status === VersionStatus.FINAL" class="muted">终稿已定，可交付</span>
            </div>
          </div>

          <div class="version-meta">
            <span v-if="version.file">文件：{{ version.file.file_name }}（{{ formatSize(version.file.file_size) }}）</span>
            <span>设计说明：{{ version.design_note || '—' }}</span>
            <span v-if="version.change_summary">变更要点：{{ version.change_summary }}</span>
            <span>上传：{{ version.uploader_name }} · {{ version.submitted_at || version.created_at }}</span>
          </div>

          <!-- 反馈录入 -->
          <div v-if="feedbackOpenFor === version.id" class="feedback-form">
            <div class="row">
              <label class="field">
                <span>反馈类型 <em>*</em></span>
                <select v-model="feedbackType">
                  <option v-for="o in feedbackTypeOptions" :key="o.value" :value="o.value">{{ o.label }}</option>
                </select>
              </label>
              <label class="field">
                <span>反馈来源</span>
                <select v-model="feedbackSource">
                  <option v-for="o in feedbackSourceOptions" :key="o.value" :value="o.value">{{ o.label }}</option>
                </select>
              </label>
            </div>
            <label class="field">
              <span>反馈内容 <em>*</em></span>
              <textarea v-model="feedbackContent" rows="3" placeholder="请记录客户的具体修改意见（会绑定到当前版本编号）" />
            </label>
            <p class="muted tiny">{{ currentFeedbackTypeHint }}</p>
            <div class="btn-row">
              <button class="btn ghost" :disabled="acting" @click="closeFeedback">取消</button>
              <button class="btn primary" :disabled="acting" @click="submitFeedback(version)">
                {{ acting ? '提交中…' : '记录反馈' }}
              </button>
            </div>
          </div>

          <!-- 客户确认 -->
          <div v-if="confirmOpenFor === version.id" class="feedback-form confirm-form">
            <h3>客户确认 {{ version.version_no }}</h3>
            <p class="muted tiny">
              确认后将生成不可篡改的确认留痕，并把对应设计任务置为“已批准”。
              {{ openFeedbackCount(version) > 0 ? `当前仍有 ${openFeedbackCount(version)} 条未解决反馈，确认不会阻塞但请先与设计师核对。` : '' }}
            </p>
            <label class="field">
              <span>客户确认人 <em>*</em></span>
              <input v-model="confirmerName" type="text" maxlength="255" placeholder="客户姓名或对接人" />
            </label>
            <div class="btn-row">
              <button class="btn ghost" :disabled="acting" @click="closeConfirm">取消</button>
              <button class="btn primary" :disabled="acting" @click="submitConfirm(version)">
                {{ acting ? '确认中…' : '确认客户已认可' }}
              </button>
            </div>
          </div>

          <!-- 反馈列表 -->
          <div v-if="feedbackFor(version).length" class="feedback-list">
            <div class="feedback-title">
              反馈（{{ feedbackFor(version).length }}）
              <span v-if="openFeedbackCount(version)" class="warn-flag">未解决 {{ openFeedbackCount(version) }}</span>
            </div>
            <table class="feedback-table">
              <thead>
                <tr>
                  <th style="width: 88px">编号</th>
                  <th style="width: 92px">类型</th>
                  <th style="width: 100px">来源</th>
                  <th>内容</th>
                  <th style="width: 84px">状态</th>
                  <th style="width: 170px">记录</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="item in feedbackFor(version)" :key="item.id">
                  <td class="mono">{{ item.feedback_no }}</td>
                  <td>{{ feedbackTypeOptions.find((o) => o.value === item.feedback_type)?.label || item.feedback_type }}</td>
                  <td>{{ feedbackSourceOptions.find((o) => o.value === item.source)?.label || item.source }}</td>
                  <td class="content">{{ item.content }}</td>
                  <td><FeedbackStatusTag :status="item.status" /></td>
                  <td class="muted">{{ item.created_by_name }}<br />{{ item.created_at }}</td>
                </tr>
              </tbody>
            </table>
          </div>
          <p v-else-if="version.status === VersionStatus.IN_CLIENT_REVIEW" class="muted tiny">该版本暂无反馈记录。</p>
        </article>
        </template>
      </section>

      <section class="card">
        <h2>交付</h2>
        <p class="muted">
          全部设计任务“已批准”且存在客户已确认版本后，AM 可标记整单已交付（订单进入“已完成”）。
        </p>
        <div class="deliver-status">
          <span :class="allTasksApproved ? 'ok' : 'err'">
            设计任务批准情况：{{ approvedCount }}/{{ taskCount }}
          </span>
          <span :class="hasConfirmedVersion ? 'ok' : 'muted'">
            客户确认版本：{{ hasConfirmedVersion ? '已存在' : '尚未确认' }}
          </span>
        </div>
        <p v-if="order.status === OrderStatus.COMPLETED" class="ok">订单已交付完成，等待业务负责人归档。</p>
        <div class="btn-row">
          <button class="btn primary" :disabled="!canDeliver || acting" @click="deliver">
            {{ acting ? '提交中…' : '标记已交付' }}
          </button>
        </div>
        <p v-if="!canDeliver && order.status === OrderStatus.IN_PRODUCTION" class="muted tiny">
          存在未批准任务或未确认版本，暂不可交付。
        </p>
      </section>
    </template>
  </div>
</template>

<style scoped>
.page { padding: 24px; max-width: 1100px; margin: 0 auto; }
.page-header {
  display: flex; justify-content: space-between; align-items: center;
  margin-bottom: 16px; gap: 12px; flex-wrap: wrap;
}
.crumb { display: flex; align-items: center; gap: 12px; }
.crumb h1 { margin: 0; font-size: 20px; }
.header-meta { display: flex; align-items: center; gap: 12px; }
.mono { font-family: Menlo, Consolas, monospace; color: var(--primary); font-size: 14px; }

.card {
  background: #fff; border-radius: 8px; box-shadow: 0 1px 4px rgba(0, 0, 0, 0.05);
  padding: 20px 24px; margin-bottom: 16px;
}
.card h2 { margin: 0 0 14px; font-size: 15px; padding-left: 8px; border-left: 3px solid var(--primary); }

.kv-grid { margin: 0; display: grid; grid-template-columns: repeat(2, 1fr); gap: 10px 24px; }
.kv { display: flex; gap: 8px; }
.kv dt { color: #909399; font-size: 13px; min-width: 84px; flex-shrink: 0; }
.kv dd { margin: 0; font-size: 14px; color: #333; }

.version-card {
  border: 1px solid #ebeef5; border-radius: 6px; padding: 14px 16px; margin-bottom: 14px;
  background: #fcfcfd;
}
.version-head { display: flex; justify-content: space-between; align-items: center; gap: 12px; flex-wrap: wrap; }
.version-title { display: flex; align-items: center; gap: 8px; font-size: 14px; }
.task-ref { color: #909399; font-size: 12px; }
.version-ops { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.version-meta { display: flex; flex-direction: column; gap: 2px; margin-top: 8px; color: #606266; font-size: 13px; }

.feedback-form { margin-top: 12px; padding: 14px 16px; background: #fdf6ec; border-radius: 6px; }
.confirm-form { background: var(--primary-soft); }
.feedback-form h3 { margin: 0 0 8px; font-size: 14px; }
.field { display: block; margin-bottom: 10px; }
.field > span { display: block; font-size: 12px; color: #606266; margin-bottom: 4px; }
.field em { color: #f56c6c; font-style: normal; }
.field input, .field select, .field textarea {
  width: 100%; box-sizing: border-box; padding: 7px 10px; border: 1px solid #dcdfe6;
  border-radius: 4px; font-family: inherit; font-size: 13px;
}
.field textarea { resize: vertical; }
.row { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }
.btn-row { display: flex; justify-content: flex-end; gap: 10px; margin-top: 6px; }

.feedback-list { margin-top: 12px; }
.feedback-title { font-size: 13px; color: #606266; margin-bottom: 6px; }
.warn-flag {
  margin-left: 8px; padding: 1px 6px; font-size: 12px; color: #e6a23c;
  background: #fdf6ec; border: 1px solid #f5dab1; border-radius: 3px;
}
.feedback-table { width: 100%; border-collapse: collapse; font-size: 13px; }
.feedback-table th, .feedback-table td {
  text-align: left; padding: 8px 10px; border-bottom: 1px solid #ebeef5; vertical-align: top;
}
.feedback-table th { color: #909399; font-weight: 500; background: #fafafa; }
.content { max-width: 320px; word-break: break-word; color: #303133; }

.deliver-status { display: flex; gap: 24px; flex-wrap: wrap; font-size: 13px; margin: 10px 0; }
.final-flag {
  font-size: 11px; color: #303133; background: #f0f0f0; border-radius: 3px; padding: 1px 5px;
}

.btn { padding: 8px 16px; border-radius: 4px; border: 1px solid #dcdfe6; background: #fff; cursor: pointer; font-size: 14px; }
.btn.small { padding: 5px 12px; font-size: 13px; }
.btn.primary { background: var(--primary); border-color: var(--primary); color: #fff; }
.btn.primary:hover:not(:disabled) { background: var(--primary-hover); border-color: var(--primary-hover); }
.btn.ghost { background: #fff; color: #606266; }
.btn:disabled { opacity: 0.6; cursor: not-allowed; }

.err { color: #f56c6c; background: #fef0f0; border-radius: 4px; padding: 8px 10px; font-size: 13px; }
.ok { color: #67c23a; background: #f0f9eb; border-radius: 4px; padding: 8px 10px; font-size: 13px; }
.muted { color: #999; font-size: 13px; }
.muted.tiny { font-size: 12px; margin-top: 8px; }
.global-err { color: #f56c6c; background: #fef0f0; border-radius: 4px; padding: 10px; }
</style>
