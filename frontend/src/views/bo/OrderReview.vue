<script setup lang="ts">
/**
 * P1-T31 · BO 审阅 + 退回 + 派发 三合一页面
 *
 * TODO(后端就绪):
 *   - orderApi.rejectOrder / assignTask 自动走真实 POST
 *   - 派发时 assignee 改为从设计师 API 下拉选择，而非自由输入
 *   - 退回后写审计日志（后端强制）
 */
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { orderApi, type OrderDetail } from '../../api/modules/order'
import OrderStatusTag from '../../components/common/OrderStatusTag.vue'
import { OrderStatus, RejectReasonType, Priority } from '../../types/enums'

const route = useRoute()
const router = useRouter()

const orderId = computed(() => Number(route.params.orderId))
const detail = ref<OrderDetail | null>(null)
const loading = ref(true)
const error = ref('')

// 退回
const rejectReasonType = ref<string>(RejectReasonType.INFORMATION_INCOMPLETE)
const rejectRemark = ref('')
const rejectSubmitting = ref(false)
const rejectError = ref('')

// 派发
const assignAssigneeName = ref('')
const assignDeadline = ref('')
const assignPriority = ref<string>(Priority.MEDIUM)
const assignSubmitting = ref(false)
const assignError = ref('')

const reasonTypeOptions: { value: string; label: string }[] = [
  { value: RejectReasonType.INFORMATION_INCOMPLETE, label: '信息不完整' },
  { value: RejectReasonType.SPEC_UNCLEAR, label: '规格不明确' },
  { value: RejectReasonType.MATERIAL_MISSING, label: '物料缺失' },
  { value: RejectReasonType.OTHER, label: '其他' },
]
const priorityOptions: { value: string; label: string }[] = [
  { value: Priority.HIGH, label: '高' },
  { value: Priority.MEDIUM, label: '中' },
  { value: Priority.LOW, label: '低' },
]

const rejectReasonLabel = (v: string) =>
  reasonTypeOptions.find((o) => o.value === v)?.label || v

async function load() {
  loading.value = true
  error.value = ''
  try {
    const res = await orderApi.reviewOrder(orderId.value)
    detail.value = (res.data as OrderDetail) || null
    if (!detail.value) error.value = '订单不存在'
  } catch (e: any) {
    error.value = e?.message || '加载订单详情失败'
  } finally {
    loading.value = false
  }
}

async function handleReject() {
  rejectError.value = ''
  if (!rejectRemark.value.trim()) {
    rejectError.value = '请填写退回说明，告知 AM 需要补充什么'
    return
  }
  rejectSubmitting.value = true
  try {
    await orderApi.rejectOrder(orderId.value, rejectReasonType.value, rejectRemark.value.trim())
    // 退回成功 → 返回待审阅列表（原订单会因状态变化从队列消失或重新排队）
    router.push('/bo/review')
  } catch (e: any) {
    rejectError.value = e?.message || '退回失败'
  } finally {
    rejectSubmitting.value = false
  }
}

async function handleAssign() {
  assignError.value = ''
  if (!assignAssigneeName.value.trim()) {
    assignError.value = '请输入设计师姓名'
    return
  }
  if (!assignDeadline.value) {
    assignError.value = '请选择任务截止日期'
    return
  }
  assignSubmitting.value = true
  try {
    await orderApi.assignTask(orderId.value, {
      assignee_id: 3,
      assignee_name: assignAssigneeName.value.trim(),
      task_deadline: new Date(assignDeadline.value).toISOString(),
      priority: assignPriority.value,
    })
    // 派发成功 → 订单进入生产中，返回列表
    router.push('/bo/review')
  } catch (e: any) {
    assignError.value = e?.message || '派发失败'
  } finally {
    assignSubmitting.value = false
  }
}

const canReject = computed(() => detail.value?.status === OrderStatus.PENDING_CONFIRM)
const canAssign = computed(() => detail.value?.status === OrderStatus.PENDING_CONFIRM)

onMounted(load)
</script>

<template>
  <div class="page">
    <header class="page-header">
      <div class="crumb">
        <button class="btn ghost small" @click="router.push('/bo/review')">← 待审阅列表</button>
        <h1>订单审阅</h1>
      </div>
      <div class="header-meta">
        <span v-if="detail" class="mono">{{ detail.order_no }}</span>
        <template v-if="detail"><OrderStatusTag :status="detail.status" /></template>
      </div>
    </header>

    <p v-if="loading" class="muted">加载中…</p>
    <p v-else-if="error" class="global-err">{{ error }}</p>

    <template v-else-if="detail">
      <!-- 需求基线 -->
      <section class="card">
        <h2>需求基线</h2>
        <dl class="kv-grid">
          <div class="kv"><dt>客户名称</dt><dd>{{ detail.customer_name }}</dd></div>
          <div class="kv"><dt>AM</dt><dd>{{ detail.am_name }}</dd></div>
          <div class="kv"><dt>使用场景</dt><dd>{{ detail.use_scene || '-' }}</dd></div>
          <div class="kv"><dt>交付截止</dt><dd>{{ detail.delivery_deadline }}</dd></div>
          <div class="kv"><dt>创建时间</dt><dd>{{ detail.created_at }}</dd></div>
          <div class="kv"><dt>备注</dt><dd>{{ detail.remark || '-' }}</dd></div>
        </dl>
      </section>

      <!-- 退回历史 -->
      <section v-if="detail.reject_reason" class="card reject-block">
        <h2>上次退回</h2>
        <dl class="kv-grid">
          <div class="kv"><dt>类型</dt><dd>{{ rejectReasonLabel(detail.reject_reason.reject_reason_type) }}</dd></div>
          <div class="kv"><dt>退回人</dt><dd>{{ detail.reject_reason.rejected_by }}</dd></div>
          <div class="kv full"><dt>说明</dt><dd class="pre-wrap">{{ detail.reject_reason.remark }}</dd></div>
        </dl>
      </section>

      <!-- 物料规格 -->
      <section class="card">
        <h2>物料规格（{{ detail.material_specs?.length || 0 }} 行）</h2>
        <table v-if="detail.material_specs?.length" class="detail-table">
          <thead>
            <tr><th style="width:40px">#</th><th>物料类型</th><th>规格尺寸</th></tr>
          </thead>
          <tbody>
            <tr v-for="(s, i) in detail.material_specs" :key="s.id">
              <td class="idx">{{ i + 1 }}</td>
              <td>{{ s.material_type }}</td>
              <td>{{ s.spec_size }}</td>
            </tr>
          </tbody>
        </table>
        <p v-else class="muted">（无物料规格）</p>
      </section>

      <!-- 附件 -->
      <section class="card">
        <h2>附件（{{ detail.attachments?.length || 0 }} 个）</h2>
        <ul v-if="detail.attachments?.length" class="attach-list">
          <li v-for="a in detail.attachments" :key="a.id">
            <span class="fname">{{ a.file_name }}</span>
            <span class="muted">{{ (a.file_size / 1024 / 1024).toFixed(1) }} MB</span>
          </li>
        </ul>
        <p v-else class="muted">（无附件）</p>
      </section>

      <!-- BO 操作面板 -->
      <section v-if="canReject || canAssign" class="action-grid">
        <!-- 退回 -->
        <div class="card action-card reject-card">
          <h2>退回订单</h2>
          <p class="hint">退回后 AM 可补充信息再次提交。订单状态保持"待审阅"。</p>
          <label class="field">
            <span>退回类型</span>
            <select v-model="rejectReasonType">
              <option v-for="o in reasonTypeOptions" :key="o.value" :value="o.value">{{ o.label }}</option>
            </select>
          </label>
          <label class="field">
            <span>退回说明 <em>*</em></span>
            <textarea v-model="rejectRemark" rows="4" placeholder="明确告诉 AM 需要补充/修改什么" />
          </label>
          <p v-if="rejectError" class="err">{{ rejectError }}</p>
          <div class="btn-row">
            <button
              class="btn danger"
              :disabled="rejectSubmitting"
              @click="handleReject"
            >{{ rejectSubmitting ? '退回中…' : '确认退回' }}</button>
          </div>
        </div>

        <!-- 派发 -->
        <div class="card action-card assign-card">
          <h2>派发设计任务</h2>
          <p class="hint">派发后订单进入"生产中"，每条物料规格自动生成一条设计任务。</p>
          <label class="field">
            <span>设计师姓名 <em>*</em></span>
            <input v-model="assignAssigneeName" type="text" placeholder="如：李设计" />
          </label>
          <label class="field">
            <span>任务截止日期 <em>*</em></span>
            <input v-model="assignDeadline" type="date" />
          </label>
          <label class="field">
            <span>优先级</span>
            <select v-model="assignPriority">
              <option v-for="o in priorityOptions" :key="o.value" :value="o.value">{{ o.label }}</option>
            </select>
          </label>
          <p v-if="assignError" class="err">{{ assignError }}</p>
          <div class="btn-row">
            <button
              class="btn primary"
              :disabled="assignSubmitting"
              @click="handleAssign"
            >{{ assignSubmitting ? '派发中…' : '确认派发' }}</button>
          </div>
        </div>
      </section>

      <section v-else class="card">
        <p class="muted">订单当前状态为"{{ detail.status }}"，无可执行的审阅操作。</p>
      </section>
    </template>
  </div>
</template>

<style scoped>
.page { padding: 24px; max-width: 1100px; margin: 0 auto; }
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; gap: 12px; }
.crumb { display: flex; align-items: center; gap: 12px; }
.crumb h1 { margin: 0; font-size: 20px; }
.header-meta { display: flex; align-items: center; gap: 12px; }
.mono { font-family: Menlo, Consolas, monospace; color: #409eff; font-size: 14px; }

.card {
  background: #fff; border-radius: 8px; box-shadow: 0 1px 4px rgba(0,0,0,0.05);
  padding: 20px 24px; margin-bottom: 16px;
}
.card h2 { margin: 0 0 14px; font-size: 15px; padding-left: 8px; border-left: 3px solid #409eff; }

.kv-grid { margin: 0; display: grid; grid-template-columns: repeat(2, 1fr); gap: 10px 24px; }
.kv { display: flex; gap: 8px; }
.kv.full { grid-column: 1 / -1; }
.kv dt { color: #909399; font-size: 13px; min-width: 72px; flex-shrink: 0; }
.kv dd { margin: 0; font-size: 14px; color: #333; word-break: break-all; }
.pre-wrap { white-space: pre-wrap; }

.reject-block { border-left: 3px solid #f56c6c; }
.reject-block h2 { border-left: none; color: #f56c6c; padding-left: 0; }

.detail-table { width: 100%; border-collapse: collapse; font-size: 14px; }
.detail-table th, .detail-table td { text-align: left; padding: 10px 8px; border-bottom: 1px solid #ebeef5; }
.detail-table thead th { font-weight: 500; color: #606266; background: #fafbfc; font-size: 13px; }
.idx { color: #999; text-align: center; }

.attach-list { margin: 0; padding: 0; list-style: none; }
.attach-list li {
  display: flex; justify-content: space-between; align-items: center;
  padding: 8px 12px; background: #fafbfc; border-radius: 4px; margin-bottom: 6px;
}
.fname { font-family: Menlo, Consolas, monospace; font-size: 13px; }

.action-grid { display: grid; grid-template-columns: repeat(2, 1fr); gap: 16px; margin-bottom: 16px; }
.action-grid .card { margin-bottom: 0; }
.reject-card h2 { border-left-color: #f56c6c; }
.assign-card h2 { border-left-color: #67c23a; }

.hint { margin: 0 0 14px; font-size: 12px; color: #909399; }
.field { display: block; margin-bottom: 12px; }
.field > span { display: block; font-size: 13px; color: #606266; margin-bottom: 6px; }
.field em { color: #f56c6c; font-style: normal; margin-left: 2px; }
.field input[type="text"], .field input[type="date"], .field select, .field textarea {
  width: 100%; padding: 8px 12px; border: 1px solid #dcdfe6; border-radius: 4px;
  box-sizing: border-box; font-size: 14px; font-family: inherit;
}
.field textarea { resize: vertical; }

.btn-row { display: flex; justify-content: flex-end; gap: 10px; margin-top: 8px; }
.btn { padding: 8px 16px; border-radius: 4px; border: 1px solid #dcdfe6; background: #fff; cursor: pointer; font-size: 14px; }
.btn.small { padding: 6px 12px; font-size: 13px; }
.btn.primary { background: #409eff; border-color: #409eff; color: #fff; }
.btn.primary:hover:not(:disabled) { background: #66b1ff; border-color: #66b1ff; }
.btn.ghost { background: #fff; color: #606266; }
.btn.ghost:hover:not(:disabled) { color: #409eff; border-color: #c6e2ff; background: #ecf5ff; }
.btn.danger { background: #f56c6c; border-color: #f56c6c; color: #fff; }
.btn.danger:hover:not(:disabled) { background: #f78989; border-color: #f78989; }
.btn:disabled { opacity: 0.6; cursor: not-allowed; }

.err { color: #f56c6c; font-size: 13px; margin: 6px 0 0; }
.muted { color: #999; font-size: 13px; }
.global-err { color: #f56c6c; background: #fef0f0; border-radius: 4px; padding: 10px; }
</style>
