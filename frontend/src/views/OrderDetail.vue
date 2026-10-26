<script setup lang="ts">
/**
 * P1-T29 · 通用订单详情页
 * 三角色共用（AM / BO / DS 路由均可到达），根据 primaryRole 渲染角色专属操作区
 *
 * TODO(后端就绪):
 *   - getOrderDetail / reviewOrder 自动切换到真实接口（USE_MOCK = false）
 *   - AM 取消按钮调 DELETE /orders/{id}
 *   - BO 退回/派发操作走真实 POST
 *   - 附件列表支持文件预览/下载
 *   - 关联物料任务完整列表（当前 mock 只在 assignTask 后有数据）
 */
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import { orderApi, type OrderDetail } from '../api/modules/order'
import OrderStatusTag from '../components/common/OrderStatusTag.vue'
import { RoleType, OrderStatus, RejectReasonType } from '../types/enums'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const orderId = computed(() => Number(route.params.orderId))
const detail = ref<OrderDetail | null>(null)
const loading = ref(true)
const error = ref('')

// BO 退回表单（仅 BO 角色可见）
const rejectOpen = ref(false)
const rejectReasonType = ref<string>(RejectReasonType.INFORMATION_INCOMPLETE)
const rejectRemark = ref('')
const rejectSubmitting = ref(false)
const rejectError = ref('')

// BO 派发表单（仅 BO 角色可见）
const assignOpen = ref(false)
const assignAssigneeName = ref('')
const assignDeadline = ref('')
const assignPriority = ref('MEDIUM')
const assignSubmitting = ref(false)
const assignError = ref('')

const reasonTypeOptions: { value: string; label: string }[] = [
  { value: RejectReasonType.INFORMATION_INCOMPLETE, label: '信息不完整' },
  { value: RejectReasonType.SPEC_UNCLEAR, label: '规格不明确' },
  { value: RejectReasonType.MATERIAL_MISSING, label: '物料缺失' },
  { value: RejectReasonType.OTHER, label: '其他' },
]

const priorityOptions: { value: string; label: string }[] = [
  { value: 'HIGH', label: '高' },
  { value: 'MEDIUM', label: '中' },
  { value: 'LOW', label: '低' },
]

const rejectReasonLabel = (v: string) =>
  reasonTypeOptions.find((o) => o.value === v)?.label || v

async function load() {
  loading.value = true
  error.value = ''
  try {
    let res
    if (auth.primaryRole === RoleType.BO) {
      // BO 走 review 端点（可带审阅上下文）
      res = await orderApi.reviewOrder(orderId.value)
    } else {
      res = await orderApi.getOrderDetail(orderId.value)
    }
    detail.value = (res.data as OrderDetail) || null
    if (!detail.value) error.value = '订单不存在'
  } catch (e: any) {
    error.value = e?.message || '加载订单详情失败'
  } finally {
    loading.value = false
  }
}

// --- BO 退回 ---
function openReject() {
  rejectOpen.value = true
  rejectReasonType.value = RejectReasonType.INFORMATION_INCOMPLETE
  rejectRemark.value = ''
  rejectError.value = ''
}
function cancelReject() {
  rejectOpen.value = false
  rejectRemark.value = ''
  rejectError.value = ''
}
async function handleReject() {
  if (!rejectRemark.value.trim()) {
    rejectError.value = '退回说明必填，请告诉 AM 需要补充什么'
    return
  }
  rejectSubmitting.value = true
  rejectError.value = ''
  try {
    await orderApi.rejectOrder(orderId.value, rejectReasonType.value, rejectRemark.value.trim())
    rejectOpen.value = false
    await load() // 重新拉取，刷新 reject_reason 区块
  } catch (e: any) {
    rejectError.value = e?.message || '退回失败'
  } finally {
    rejectSubmitting.value = false
  }
}

// --- BO 派发 ---
function openAssign() {
  assignOpen.value = true
  assignAssigneeName.value = ''
  assignDeadline.value = ''
  assignPriority.value = 'MEDIUM'
  assignError.value = ''
}
function cancelAssign() {
  assignOpen.value = false
  assignAssigneeName.value = ''
  assignError.value = ''
}
async function handleAssign() {
  if (!assignAssigneeName.value.trim()) {
    assignError.value = '请输入设计师姓名'
    return
  }
  if (!assignDeadline.value) {
    assignError.value = '请选择派发截止日期'
    return
  }
  assignSubmitting.value = true
  assignError.value = ''
  try {
    await orderApi.assignTask(orderId.value, {
      assignee_id: 3,
      assignee_name: assignAssigneeName.value.trim(),
      task_deadline: new Date(assignDeadline.value).toISOString(),
      priority: assignPriority.value,
    })
    assignOpen.value = false
    await load() // 刷新为 IN_PRODUCTION，物料任务也会同步创建
  } catch (e: any) {
    assignError.value = e?.message || '派发失败'
  } finally {
    assignSubmitting.value = false
  }
}

// --- AM 返回列表 ---
function backToList() {
  if (auth.primaryRole === RoleType.AM) router.push('/am/orders')
  else if (auth.primaryRole === RoleType.BO) router.push('/bo/review')
  else router.push('/ds/tasks')
}

onMounted(load)
</script>

<template>
  <div class="page">
    <header class="page-header">
      <div class="crumb">
        <button class="btn ghost small" @click="backToList">← 返回列表</button>
        <h1>订单详情</h1>
      </div>
      <div class="header-meta">
        <span v-if="detail" class="mono">{{ detail.order_no }}</span>
        <template v-if="detail">
          <OrderStatusTag :status="detail.status" />
        </template>
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

      <!-- 退回原因（仅存在时展示）-->
      <section v-if="detail.reject_reason" class="card reject-block">
        <h2>上次退回</h2>
        <dl class="kv-grid">
          <div class="kv">
            <dt>退回类型</dt>
            <dd>{{ rejectReasonLabel(detail.reject_reason.reject_reason_type) }}</dd>
          </div>
          <div class="kv"><dt>退回人</dt><dd>{{ detail.reject_reason.rejected_by }}</dd></div>
          <div class="kv full">
            <dt>退回说明</dt>
            <dd class="pre-wrap">{{ detail.reject_reason.remark }}</dd>
          </div>
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
        <p v-else class="muted">（无附件。附件上传功能 P1-T26 待后端就绪后接入）</p>
      </section>

      <!-- BO 操作区 -->
      <section v-if="auth.primaryRole === RoleType.BO && detail.status === OrderStatus.PENDING_CONFIRM" class="card action-card">
        <h2>BO 审阅操作</h2>

        <!-- 退回表单 -->
        <div v-if="rejectOpen" class="sub-card">
          <h3>退回订单</h3>
          <label class="field">
            <span>退回类型</span>
            <select v-model="rejectReasonType">
              <option v-for="o in reasonTypeOptions" :key="o.value" :value="o.value">{{ o.label }}</option>
            </select>
          </label>
          <label class="field">
            <span>退回说明 <em>*</em></span>
            <textarea v-model="rejectRemark" rows="3" placeholder="请明确告诉 AM 需要补充/修改什么" />
          </label>
          <p v-if="rejectError" class="err">{{ rejectError }}</p>
          <div class="btn-row">
            <button class="btn ghost" @click="cancelReject">取消</button>
            <button class="btn danger" :disabled="rejectSubmitting" @click="handleReject">
              {{ rejectSubmitting ? '退回中…' : '确认退回' }}
            </button>
          </div>
        </div>
        <button v-else class="btn danger ghost" @click="openReject">退回订单</button>

        <!-- 派发表单 -->
        <div v-if="assignOpen" class="sub-card">
          <h3>派发设计任务</h3>
          <div class="row">
            <label class="field">
              <span>设计师姓名 <em>*</em></span>
              <input v-model="assignAssigneeName" type="text" placeholder="如：李设计" />
            </label>
            <label class="field">
              <span>任务截止 <em>*</em></span>
              <input v-model="assignDeadline" type="date" />
            </label>
          </div>
          <label class="field">
            <span>优先级</span>
            <select v-model="assignPriority">
              <option v-for="o in priorityOptions" :key="o.value" :value="o.value">{{ o.label }}</option>
            </select>
          </label>
          <p v-if="assignError" class="err">{{ assignError }}</p>
          <div class="btn-row">
            <button class="btn ghost" @click="cancelAssign">取消</button>
            <button class="btn primary" :disabled="assignSubmitting" @click="handleAssign">
              {{ assignSubmitting ? '派发中…' : '确认派发' }}
            </button>
          </div>
          <p class="muted tiny">派发后订单状态转为"生产中"，每条物料规格会自动生成一条设计任务。</p>
        </div>
        <button v-else class="btn primary" @click="openAssign">派发设计任务</button>
      </section>

      <!-- 状态提示（非 BO 或已非 PENDING_CONFIRM）-->
      <section v-else-if="auth.primaryRole === RoleType.BO" class="card">
        <p class="muted">订单当前状态为"{{ detail.status }}"，无可执行的审阅操作。</p>
      </section>
    </template>
  </div>
</template>

<style scoped>
.page { padding: 24px; max-width: 1080px; margin: 0 auto; }
.page-header {
  display: flex; justify-content: space-between; align-items: center;
  margin-bottom: 16px; gap: 12px; flex-wrap: wrap;
}
.crumb { display: flex; align-items: center; gap: 12px; }
.crumb h1 { margin: 0; font-size: 20px; }
.header-meta { display: flex; align-items: center; gap: 12px; }
.mono { font-family: Menlo, Consolas, monospace; color: #409eff; font-size: 14px; }

.card {
  background: #fff; border-radius: 8px; box-shadow: 0 1px 4px rgba(0,0,0,0.05);
  padding: 20px 24px; margin-bottom: 16px;
}
.card h2 { margin: 0 0 14px; font-size: 15px; padding-left: 8px; border-left: 3px solid #409eff; }

.kv-grid {
  margin: 0; display: grid; grid-template-columns: repeat(2, 1fr);
  gap: 10px 24px;
}
.kv { display: flex; gap: 8px; }
.kv.full { grid-column: 1 / -1; }
.kv dt { color: #909399; font-size: 13px; min-width: 72px; flex-shrink: 0; }
.kv dd { margin: 0; font-size: 14px; color: #333; word-break: break-all; }
.pre-wrap { white-space: pre-wrap; }

.reject-block { border-left: 3px solid #f56c6c; }
.reject-block h2 { border-left: none; color: #f56c6c; padding-left: 0; }

.detail-table { width: 100%; border-collapse: collapse; font-size: 14px; }
.detail-table th, .detail-table td {
  text-align: left; padding: 10px 8px; border-bottom: 1px solid #ebeef5;
}
.detail-table thead th { font-weight: 500; color: #606266; background: #fafbfc; font-size: 13px; }
.idx { color: #999; text-align: center; }

.attach-list { margin: 0; padding: 0; list-style: none; }
.attach-list li {
  display: flex; justify-content: space-between; align-items: center;
  padding: 8px 12px; background: #fafbfc; border-radius: 4px; margin-bottom: 6px;
}
.fname { font-family: Menlo, Consolas, monospace; font-size: 13px; }

.action-card h2 { border-left-color: #e6a23c; }
.sub-card { margin-top: 14px; padding: 16px; background: #fdf6ec; border-radius: 6px; }
.sub-card h3 { margin: 0 0 12px; font-size: 14px; }
.field { display: block; margin-bottom: 12px; }
.field > span { display: block; font-size: 13px; color: #606266; margin-bottom: 6px; }
.field em { color: #f56c6c; font-style: normal; margin-left: 2px; }
.field input[type="text"], .field input[type="date"], .field select, .field textarea {
  width: 100%; padding: 8px 12px; border: 1px solid #dcdfe6; border-radius: 4px;
  box-sizing: border-box; font-size: 14px; font-family: inherit;
}
.field textarea { resize: vertical; }
.row { display: flex; gap: 16px; }
.row .field { flex: 1; }
.btn-row { display: flex; justify-content: flex-end; gap: 10px; margin-top: 6px; }

.btn {
  padding: 8px 16px; border-radius: 4px; border: 1px solid #dcdfe6; background: #fff; cursor: pointer; font-size: 14px;
}
.btn.small { padding: 6px 12px; font-size: 13px; }
.btn.primary { background: #409eff; border-color: #409eff; color: #fff; }
.btn.primary:hover:not(:disabled) { background: #66b1ff; border-color: #66b1ff; }
.btn.ghost { background: #fff; color: #606266; }
.btn.ghost:hover:not(:disabled) { color: #409eff; border-color: #c6e2ff; background: #ecf5ff; }
.btn.danger { background: #f56c6c; border-color: #f56c6c; color: #fff; }
.btn.danger.ghost { background: #fff; color: #f56c6c; border-color: #fbc4c4; }
.btn.danger.ghost:hover { background: #fef0f0; }
.btn:disabled { opacity: 0.6; cursor: not-allowed; }

.err { color: #f56c6c; font-size: 13px; margin: 6px 0 0; }
.muted { color: #999; font-size: 13px; }
.muted.tiny { font-size: 12px; margin-top: 8px; }
.global-err { color: #f56c6c; background: #fef0f0; border-radius: 4px; padding: 10px; }
</style>
