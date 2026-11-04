<script setup lang="ts">
/**
 * P2-T15 · BO 内部评审页
 *
 * - 左栏：生产中订单（GET /orders/internal-review-queue）
 * - 右栏：设计任务（A-07 重新分派 / A-23 取消任务）+ 全部版本（A-26）
 * - 勾选两个版本并排对比（A-10）；待内部评审版本可放行（A-11）/ 退回（A-12，意见必填）
 */
import { ref, computed, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { orderApi, type OrderBrief, type OrderDetail, type OrderTask } from '../../api/modules/order'
import { versionApi, type VersionItem, type VersionComparison } from '../../api/modules/designVersion'
import { materialTaskApi } from '../../api/modules/materialTask'
import { userApi, type DesignerSummary } from '../../api/modules/user'
import { errorMessage } from '../../api/http'
import VersionStatusTag from '../../components/common/VersionStatusTag.vue'
import TaskStatusTag from '../../components/common/TaskStatusTag.vue'
import { MaterialTaskStatus, Priority, VersionStatus } from '../../types/enums'

const route = useRoute()
const router = useRouter()

const orders = ref<OrderBrief[]>([])
const orderDetail = ref<OrderDetail | null>(null)
const versions = ref<VersionItem[]>([])
const selectedOrderId = ref<number | null>(null)
const selectedVersionIds = ref<number[]>([])
const comparison = ref<VersionComparison | null>(null)

const loadingOrders = ref(true)
const loadingVersions = ref(false)
const error = ref('')
const actionError = ref('')
const acting = ref(false)

const dialogMode = ref<'RELEASE' | 'REJECT' | null>(null)
const dialogVersion = ref<VersionItem | null>(null)
const dialogRemark = ref('')

// A-07 / A-23 任务操作
const designers = ref<DesignerSummary[]>([])
const taskDialogMode = ref<'REASSIGN' | 'CANCEL' | null>(null)
const dialogTask = ref<OrderTask | null>(null)
const reassignAssigneeId = ref<number | null>(null)
const reassignDeadline = ref('')
const reassignPriority = ref<string>(Priority.MEDIUM)
const taskReason = ref('')
const taskActing = ref(false)
const taskError = ref('')

/** 只有非终态任务能交接或取消（与后端状态机一致） */
const OPERABLE_TASK_STATUSES: string[] = [
  MaterialTaskStatus.PENDING_DESIGN,
  MaterialTaskStatus.PENDING_APPROVAL,
  MaterialTaskStatus.PENDING_REVISE,
]
const priorityOptions: { value: string; label: string }[] = [
  { value: Priority.HIGH, label: '高' },
  { value: Priority.MEDIUM, label: '中' },
  { value: Priority.LOW, label: '低' },
]

const orderTasks = computed(() => orderDetail.value?.tasks || [])
/** 交接候选人：排除当前设计师（后端同样拒绝“分派给原设计师”） */
const reassignCandidates = computed(() =>
  designers.value.filter((designer) => designer.id !== dialogTask.value?.assignee_id)
)

function canOperateTask(task: OrderTask): boolean {
  return OPERABLE_TASK_STATUSES.includes(task.status || '')
}

function priorityLabel(value?: string): string {
  return priorityOptions.find((option) => option.value === value)?.label || value || '—'
}

/** ISO 时间（2026-11-10T18:00:00）→ datetime-local 需要的 2026-11-10T18:00 */
function toDateTimeInput(value?: string): string {
  return value ? value.slice(0, 16) : ''
}

async function loadDesigners() {
  try {
    const res = await userApi.listDesigners()
    designers.value = (res.data as DesignerSummary[]) || []
  } catch (e) {
    actionError.value = errorMessage(e, '加载设计师列表失败')
  }
}

function openReassign(task: OrderTask) {
  taskDialogMode.value = 'REASSIGN'
  dialogTask.value = task
  reassignAssigneeId.value = null
  reassignDeadline.value = toDateTimeInput(task.task_deadline)
  reassignPriority.value = task.priority || Priority.MEDIUM
  taskReason.value = ''
  taskError.value = ''
}

function openCancel(task: OrderTask) {
  taskDialogMode.value = 'CANCEL'
  dialogTask.value = task
  taskReason.value = ''
  taskError.value = ''
}

function closeTaskDialog() {
  taskDialogMode.value = null
  dialogTask.value = null
  taskReason.value = ''
  taskError.value = ''
}

async function confirmTaskDialog() {
  if (!taskDialogMode.value || !dialogTask.value) return
  if (!taskReason.value.trim()) {
    taskError.value = taskDialogMode.value === 'REASSIGN'
      ? '请填写重新分派原因（用于交接留痕）'
      : '请填写取消原因'
    return
  }
  if (taskDialogMode.value === 'REASSIGN' && !reassignAssigneeId.value) {
    taskError.value = '请选择新的设计师'
    return
  }
  taskActing.value = true
  taskError.value = ''
  try {
    if (taskDialogMode.value === 'REASSIGN') {
      await materialTaskApi.reassign(dialogTask.value.id, {
        assignee_id: reassignAssigneeId.value as number,
        reason: taskReason.value.trim(),
        // 留空则后端保持原截止时间
        task_deadline: reassignDeadline.value || undefined,
        priority: reassignPriority.value,
      })
    } else {
      await materialTaskApi.cancel(dialogTask.value.id, taskReason.value.trim())
    }
    closeTaskDialog()
    if (selectedOrderId.value) await selectOrder(selectedOrderId.value)
    await refreshQueue()
  } catch (e) {
    taskError.value = errorMessage(e, '任务操作失败')
  } finally {
    taskActing.value = false
  }
}

const pendingVersions = computed(() =>
  versions.value.filter((version) => version.status === VersionStatus.PENDING_INTERNAL_REVIEW)
)

function toggleVersion(version: VersionItem) {
  const index = selectedVersionIds.value.indexOf(version.id)
  if (index >= 0) {
    selectedVersionIds.value.splice(index, 1)
    comparison.value = null
    return
  }
  if (selectedVersionIds.value.length >= 2) {
    selectedVersionIds.value.shift()
  }
  selectedVersionIds.value.push(version.id)
  comparison.value = null
}

function isSelected(version: VersionItem) {
  return selectedVersionIds.value.includes(version.id)
}

async function loadOrders() {
  loadingOrders.value = true
  error.value = ''
  try {
    const res = await orderApi.listInternalReviewQueue()
    orders.value = (res.data || []) as OrderBrief[]
    const fromRoute = Number(route.params.orderId)
    const target = Number.isFinite(fromRoute) && fromRoute > 0 ? fromRoute : selectedOrderId.value
    if (target && orders.value.some((order) => order.id === target)) {
      await selectOrder(target)
    } else if (orders.value.length) {
      await selectOrder(orders.value[0].id)
    }
  } catch (e) {
    error.value = errorMessage(e, '加载内部评审订单失败')
  } finally {
    loadingOrders.value = false
  }
}

/** 只刷新左栏队列（不重置右栏的版本勾选） */
async function refreshQueue() {
  try {
    const res = await orderApi.listInternalReviewQueue()
    orders.value = (res.data || []) as OrderBrief[]
  } catch {
    // 队列刷新失败不覆盖当前详情页
  }
}

async function selectOrder(orderId: number) {
  selectedOrderId.value = orderId
  selectedVersionIds.value = []
  comparison.value = null
  actionError.value = ''
  loadingVersions.value = true
  try {
    const [detail, list] = await Promise.all([
      orderApi.getOrderDetail(orderId),
      versionApi.listForOrder(orderId),
    ])
    orderDetail.value = (detail.data as OrderDetail) || null
    versions.value = ((list.data || []) as VersionItem[]).sort((a, b) => b.id - a.id)
  } catch (e) {
    actionError.value = errorMessage(e, '加载版本历史失败')
  } finally {
    loadingVersions.value = false
  }
}

async function compareSelected() {
  if (selectedVersionIds.value.length !== 2 || !selectedOrderId.value) return
  actionError.value = ''
  try {
    const res = await versionApi.compare(
      selectedOrderId.value,
      selectedVersionIds.value[0],
      selectedVersionIds.value[1]
    )
    comparison.value = (res.data as VersionComparison) || null
  } catch (e) {
    actionError.value = errorMessage(e, '版本对比失败')
  }
}

function openDialog(mode: 'RELEASE' | 'REJECT', version: VersionItem) {
  dialogMode.value = mode
  dialogVersion.value = version
  dialogRemark.value = ''
  actionError.value = ''
}

function closeDialog() {
  dialogMode.value = null
  dialogVersion.value = null
  dialogRemark.value = ''
}

async function confirmDialog() {
  if (!dialogMode.value || !dialogVersion.value) return
  if (dialogMode.value === 'REJECT' && !dialogRemark.value.trim()) {
    actionError.value = '退回必须填写评审意见'
    return
  }
  acting.value = true
  actionError.value = ''
  try {
    if (dialogMode.value === 'RELEASE') {
      await versionApi.release(dialogVersion.value.id, dialogRemark.value.trim() || undefined)
    } else {
      await versionApi.reject(dialogVersion.value.id, dialogRemark.value.trim())
    }
    closeDialog()
    await selectOrder(selectedOrderId.value as number)
  } catch (e) {
    actionError.value = errorMessage(e, '操作失败')
  } finally {
    acting.value = false
  }
}

async function download(version: VersionItem) {
  try {
    await versionApi.download(version.id, version.file?.file_name || `${version.version_no}.bin`)
  } catch (e) {
    actionError.value = errorMessage(e, '下载失败，请重试')
  }
}

watch(
  () => route.params.orderId,
  (value) => {
    const orderId = Number(value)
    if (Number.isFinite(orderId) && orderId > 0 && orderId !== selectedOrderId.value) selectOrder(orderId)
  }
)

onMounted(() => {
  loadOrders()
  loadDesigners()
})
</script>

<template>
  <div class="page">
    <header class="page-header">
      <div class="crumb">
        <button class="btn ghost small" @click="router.push('/bo/review')">← 返回待确认订单</button>
        <h1>内部评审</h1>
      </div>
      <span class="muted">生产中的订单：对比版本、放行或退回</span>
    </header>

    <p v-if="error" class="global-err">{{ error }}</p>

    <div class="layout">
      <aside class="card orders">
        <h2>生产中订单</h2>
        <p v-if="loadingOrders" class="muted">加载中…</p>
        <p v-else-if="!orders.length" class="muted">暂无生产中订单。先在“待确认订单”完成派发。</p>
        <ul v-else class="order-list">
          <li
            v-for="order in orders"
            :key="order.id"
            :class="{ active: order.id === selectedOrderId }"
            @click="selectOrder(order.id)"
          >
            <span class="mono">{{ order.order_no }}</span>
            <span class="customer">{{ order.customer_name }}</span>
            <span class="task-count">{{ order.task_count ?? 0 }} 个任务</span>
          </li>
        </ul>
      </aside>

      <main class="card main">
        <h2>订单评审</h2>
        <p v-if="!selectedOrderId" class="muted">请选择左侧订单。</p>

        <template v-else>
          <div class="order-head">
            <div>
              <span class="mono">{{ orderDetail?.order_no || '' }}</span>
              <span class="muted"> · {{ orderDetail?.customer_name }} · 截止 {{ orderDetail?.delivery_deadline }}</span>
            </div>
            <div class="ops">
              <button class="btn small" :disabled="selectedVersionIds.length !== 2" @click="compareSelected">
                对比选中版本（{{ selectedVersionIds.length }}/2）
              </button>
            </div>
          </div>

          <!-- 设计任务：A-07 重新分派 / A-23 取消 -->
          <section class="task-block">
            <div class="task-title">
              <h3>设计任务（{{ orderTasks.length }}）</h3>
              <span class="muted">重新分派后，任务立即从原设计师的任务列表移除</span>
            </div>
            <p v-if="!orderTasks.length" class="muted">该订单暂无设计任务。</p>
            <table v-else class="task-table">
              <thead>
                <tr>
                  <th>物料</th>
                  <th>规格</th>
                  <th>当前设计师</th>
                  <th>截止时间</th>
                  <th>优先级</th>
                  <th>状态</th>
                  <th>操作</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="task in orderTasks" :key="task.id">
                  <td>{{ task.material_type }}</td>
                  <td>{{ task.spec_size }}</td>
                  <td>{{ task.assignee_name || '（未指派）' }}</td>
                  <td class="muted">{{ task.task_deadline || '—' }}</td>
                  <td>{{ priorityLabel(task.priority) }}</td>
                  <td><TaskStatusTag :status="task.status || ''" /></td>
                  <td class="ops">
                    <template v-if="canOperateTask(task)">
                      <button class="btn small" @click="openReassign(task)">重新分派</button>
                      <button class="btn small danger" @click="openCancel(task)">取消任务</button>
                    </template>
                    <span v-else class="muted">已结束</span>
                  </td>
                </tr>
              </tbody>
            </table>
          </section>

          <p v-if="actionError" class="err">{{ actionError }}</p>

          <div class="task-title">
            <h3>设计版本（{{ versions.length }}）</h3>
            <span class="muted">勾选任意两个版本可并排对比</span>
          </div>

          <p v-if="loadingVersions" class="muted">版本加载中…</p>
          <p v-else-if="!versions.length" class="muted">该订单暂无设计版本。</p>

          <table v-else class="version-table">
            <thead>
              <tr>
                <th class="pick"></th>
                <th>版本</th>
                <th>所属任务</th>
                <th>状态</th>
                <th>设计说明</th>
                <th>变更要点</th>
                <th>上传人 / 时间</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="version in versions" :key="version.id" :class="{ picked: isSelected(version) }">
                <td class="pick">
                  <input
                    type="checkbox"
                    :checked="isSelected(version)"
                    @change="toggleVersion(version)"
                  />
                </td>
                <td class="mono">{{ version.version_no }}<span v-if="version.is_final" class="final-flag">终稿</span></td>
                <td class="muted">{{ version.material_type }} {{ version.spec_size }}</td>
                <td><VersionStatusTag :status="version.status" /></td>
                <td class="note">{{ version.design_note }}</td>
                <td class="note">{{ version.change_summary || '—' }}</td>
                <td class="muted">{{ version.uploader_name }}<br />{{ version.submitted_at || version.created_at }}</td>
                <td class="ops">
                  <button v-if="version.file" class="link" @click="download(version)">下载</button>
                  <template v-if="version.status === VersionStatus.PENDING_INTERNAL_REVIEW">
                    <button class="btn small primary" @click="openDialog('RELEASE', version)">放行</button>
                    <button class="btn small danger" @click="openDialog('REJECT', version)">退回</button>
                  </template>
                  <span v-else-if="version.status === VersionStatus.DRAFT" class="muted">草稿</span>
                </td>
              </tr>
            </tbody>
          </table>

          <p v-if="pendingVersions.length" class="muted hint">
            提示：勾选任意两个版本查看并排差异；只有“待内部评审”的版本可以放行或退回。
          </p>

          <section v-if="comparison" class="compare">
            <h3>
              版本对比
              <span class="change-flag" :class="{ changed: comparison.design_note_changed }">
                设计说明{{ comparison.design_note_changed ? '有差异' : '相同' }}
              </span>
              <span class="change-flag" :class="{ changed: comparison.change_summary_changed }">
                变更要点{{ comparison.change_summary_changed ? '有差异' : '相同' }}
              </span>
            </h3>
            <div class="compare-grid">
              <div class="compare-col">
                <h4>{{ comparison.left.version_no }} <VersionStatusTag :status="comparison.left.status" /></h4>
                <label>设计说明</label>
                <p>{{ comparison.left.design_note }}</p>
                <label>变更要点</label>
                <p>{{ comparison.left.change_summary || '—' }}</p>
                <label>文件</label>
                <p>
                  <button v-if="comparison.left.file" class="link" @click="download(comparison.left)">
                    {{ comparison.left.file.file_name }}
                  </button>
                  <span v-else>—</span>
                  <span class="muted"> · {{ comparison.left.submitted_at || comparison.left.created_at }}</span>
                </p>
              </div>
              <div class="compare-col">
                <h4>{{ comparison.right.version_no }} <VersionStatusTag :status="comparison.right.status" /></h4>
                <label>设计说明</label>
                <p :class="{ diff: comparison.design_note_changed }">{{ comparison.right.design_note }}</p>
                <label>变更要点</label>
                <p :class="{ diff: comparison.change_summary_changed }">{{ comparison.right.change_summary || '—' }}</p>
                <label>文件</label>
                <p>
                  <button v-if="comparison.right.file" class="link" @click="download(comparison.right)">
                    {{ comparison.right.file.file_name }}
                  </button>
                  <span v-else>—</span>
                  <span class="muted"> · {{ comparison.right.submitted_at || comparison.right.created_at }}</span>
                </p>
              </div>
            </div>
          </section>
        </template>
      </main>
    </div>

    <div v-if="dialogMode" class="mask" @click.self="closeDialog">
      <div class="dialog">
        <h3>{{ dialogMode === 'RELEASE' ? '放行版本' : '退回版本' }} {{ dialogVersion?.version_no }}</h3>
        <p class="muted">
          {{ dialogMode === 'RELEASE'
            ? '放行后版本进入客户评审，任务将保持待审批直到客户确认。'
            : '退回后版本状态保持不变（历史留痕），任务回到“待修改”，设计师需上传新版本。' }}
        </p>
        <textarea
          v-model="dialogRemark"
          rows="3"
          :placeholder="dialogMode === 'RELEASE' ? '放行备注（选填）' : '评审意见（必填）'"
        />
        <p v-if="actionError" class="err">{{ actionError }}</p>
        <div class="dialog-actions">
          <button class="btn" :disabled="acting" @click="closeDialog">取消</button>
          <button class="btn primary" :disabled="acting" @click="confirmDialog">
            {{ acting ? '提交中…' : '确认' }}
          </button>
        </div>
      </div>
    </div>

    <!-- A-07 / A-23 任务操作对话框 -->
    <div v-if="taskDialogMode" class="mask" @click.self="closeTaskDialog">
      <div class="dialog">
        <h3>{{ taskDialogMode === 'REASSIGN' ? '重新分派任务' : '取消任务' }}</h3>
        <p class="muted">
          {{ dialogTask?.material_type }} {{ dialogTask?.spec_size }} ·
          当前设计师 {{ dialogTask?.assignee_name || '（未指派）' }}
        </p>

        <template v-if="taskDialogMode === 'REASSIGN'">
          <label class="field">
            <span>新设计师 <em>*</em></span>
            <select v-model="reassignAssigneeId">
              <option :value="null" disabled>请选择设计师</option>
              <option
                v-for="designer in reassignCandidates"
                :key="designer.id"
                :value="designer.id"
              >{{ designer.name }}（{{ designer.username }}）</option>
            </select>
          </label>
          <div class="row">
            <label class="field">
              <span>任务截止时间</span>
              <input v-model="reassignDeadline" type="datetime-local" />
            </label>
            <label class="field">
              <span>优先级</span>
              <select v-model="reassignPriority">
                <option v-for="o in priorityOptions" :key="o.value" :value="o.value">{{ o.label }}</option>
              </select>
            </label>
          </div>
          <p class="muted tip">留空截止时间表示保持原截止时间。</p>
        </template>

        <label class="field">
          <span>{{ taskDialogMode === 'REASSIGN' ? '重新分派原因' : '取消原因' }} <em>*</em></span>
          <textarea
            v-model="taskReason"
            rows="3"
            :placeholder="taskDialogMode === 'REASSIGN'
              ? '如：原设计师请假，交接给同组设计师'
              : '如：客户取消该物料，无需继续设计'"
          />
        </label>
        <p v-if="taskError" class="err">{{ taskError }}</p>
        <div class="dialog-actions">
          <button class="btn" :disabled="taskActing" @click="closeTaskDialog">取消</button>
          <button
            class="btn"
            :class="taskDialogMode === 'CANCEL' ? 'danger' : 'primary'"
            :disabled="taskActing"
            @click="confirmTaskDialog"
          >{{ taskActing ? '提交中…' : '确认' }}</button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.page { padding: 24px; max-width: 1240px; margin: 0 auto; }
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; gap: 12px; }
.crumb { display: flex; align-items: center; gap: 12px; }
.crumb h1 { margin: 0; font-size: 20px; }
.layout { display: grid; grid-template-columns: 280px 1fr; gap: 16px; align-items: start; }
.card { background: #fff; border-radius: 8px; box-shadow: 0 1px 4px rgba(0,0,0,0.05); padding: 18px 20px; }
.card h2 { margin: 0 0 14px; font-size: 15px; padding-left: 8px; border-left: 3px solid #409eff; }
.order-list { list-style: none; margin: 0; padding: 0; }
.order-list li { padding: 10px 8px; border-radius: 4px; cursor: pointer; border-bottom: 1px solid #f2f3f5; display: flex; flex-direction: column; gap: 2px; }
.order-list li:hover { background: #f5f7fa; }
.order-list li.active { background: #ecf5ff; }
.customer { font-size: 13px; color: #606266; }
.task-count { font-size: 12px; color: #909399; }
.mono { font-family: Menlo, Consolas, monospace; color: #409eff; font-size: 13px; }
.order-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; gap: 12px; flex-wrap: wrap; }
.task-block { margin-bottom: 18px; }
.task-title { display: flex; justify-content: space-between; align-items: baseline; gap: 12px; margin-bottom: 8px; }
.task-title h3 { margin: 0; font-size: 14px; }
.task-table { width: 100%; border-collapse: collapse; font-size: 13px; }
.task-table th, .task-table td { text-align: left; padding: 8px 10px; border-bottom: 1px solid #ebeef5; vertical-align: middle; }
.task-table th { color: #909399; font-weight: 500; background: #fafafa; }
.field { display: block; margin-top: 10px; }
.field span { display: block; font-size: 12px; color: #606266; margin-bottom: 4px; }
.field em { color: #f56c6c; font-style: normal; }
.field select, .field input, .field textarea { width: 100%; box-sizing: border-box; padding: 7px 10px; border: 1px solid #dcdfe6; border-radius: 4px; font-family: inherit; font-size: 13px; }
.field textarea { resize: vertical; }
.row { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }
.tip { margin: 6px 0 0; font-size: 12px; }
.version-table { width: 100%; border-collapse: collapse; font-size: 13px; }
.version-table th, .version-table td { text-align: left; padding: 8px 10px; border-bottom: 1px solid #ebeef5; vertical-align: top; }
.version-table th { color: #909399; font-weight: 500; background: #fafafa; }
.version-table tr.picked { background: #f0f7ff; }
.pick { width: 28px; }
.note { max-width: 220px; word-break: break-word; color: #606266; }
.ops { white-space: nowrap; }
.final-flag { margin-left: 6px; font-size: 11px; color: #303133; background: #f0f0f0; border-radius: 3px; padding: 1px 5px; }
.hint { margin-top: 10px; }
.compare { margin-top: 18px; border-top: 1px solid #ebeef5; padding-top: 14px; }
.compare h3 { font-size: 14px; margin: 0 0 12px; display: flex; align-items: center; gap: 8px; }
.change-flag { font-size: 11px; padding: 2px 6px; border-radius: 3px; background: #f4f4f5; color: #909399; font-weight: 400; }
.change-flag.changed { background: #fdf6ec; color: #e6a23c; }
.compare-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; }
.compare-col { background: #fafbfc; border: 1px solid #ebeef5; border-radius: 6px; padding: 12px; }
.compare-col h4 { margin: 0 0 8px; font-size: 13px; display: flex; align-items: center; gap: 6px; }
.compare-col label { display: block; font-size: 12px; color: #909399; margin-top: 8px; }
.compare-col p { margin: 2px 0 0; font-size: 13px; color: #303133; word-break: break-word; }
.diff { background: #fff7e6; }
.btn { padding: 8px 16px; border-radius: 4px; border: 1px solid #dcdfe6; background: #fff; cursor: pointer; font-size: 14px; }
.btn.small { padding: 4px 10px; font-size: 13px; }
.btn.primary { background: #409eff; border-color: #409eff; color: #fff; }
.btn.danger { background: #fff; border-color: #f56c6c; color: #f56c6c; }
.btn.ghost { background: #fff; color: #606266; }
.btn:disabled { opacity: 0.6; cursor: not-allowed; }
.link { border: none; background: none; color: #409eff; cursor: pointer; padding: 0; font-size: 13px; text-decoration: underline; margin-right: 8px; }
.err { color: #f56c6c; background: #fef0f0; border-radius: 4px; padding: 8px 10px; font-size: 13px; }
.muted { color: #999; font-size: 13px; }
.global-err { color: #f56c6c; background: #fef0f0; border-radius: 4px; padding: 10px; }
.mask { position: fixed; inset: 0; background: rgba(0,0,0,0.4); display: flex; align-items: center; justify-content: center; z-index: 40; }
.dialog { background: #fff; border-radius: 8px; padding: 20px 24px; width: 420px; max-width: 92vw; }
.dialog h3 { margin: 0 0 8px; font-size: 16px; }
.dialog textarea { width: 100%; box-sizing: border-box; padding: 8px 10px; border: 1px solid #dcdfe6; border-radius: 4px; font-family: inherit; font-size: 13px; resize: vertical; margin-top: 8px; }
.dialog-actions { display: flex; justify-content: flex-end; gap: 10px; margin-top: 14px; }
</style>
