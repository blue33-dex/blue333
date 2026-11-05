<script setup lang="ts">
/**
 * P4-T15 · BO 异常管理（A-20 登记 / A-21 关闭 / P4-T05 逾期扫描）
 */
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  exceptionApi,
  type ExceptionRecordItem,
  type ExceptionScanResult,
} from '../../api/modules/phase4'
import { errorMessage } from '../../api/http'
import NotificationBell from '../../components/common/NotificationBell.vue'
import { ExceptionStatus, ExceptionType } from '../../types/enums'

const router = useRouter()

const rows = ref<ExceptionRecordItem[]>([])
const loading = ref(false)
const error = ref('')
const cursor = ref<string | undefined>(undefined)
const hasNext = ref(false)
const statusFilter = ref<string>('')

const typeOptions = [
  { value: ExceptionType.DELAY, label: '逾期' },
  { value: ExceptionType.OTHER, label: '其他' },
]
const statusOptions = [
  { value: ExceptionStatus.PENDING_CONFIRM, label: '待确认' },
  { value: ExceptionStatus.IN_PROGRESS, label: '处理中' },
  { value: ExceptionStatus.CLOSED, label: '已关闭' },
]

const typeLabel = (v: string) => typeOptions.find((o) => o.value === v)?.label || v
const statusLabel = (v: string) => statusOptions.find((o) => o.value === v)?.label || v

// --- 列表 ---
async function load(reset = false) {
  loading.value = true
  error.value = ''
  try {
    const res = await exceptionApi.list({
      status: (statusFilter.value || undefined) as ExceptionStatus | undefined,
      limit: 20,
      cursor: reset ? undefined : cursor.value,
    })
    const data = res.data || []
    rows.value = reset ? data : [...rows.value, ...data]
    cursor.value = res.pagination?.next_cursor
    hasNext.value = !!res.pagination?.has_next
  } catch (e) {
    error.value = errorMessage(e, '加载异常列表失败')
  } finally {
    loading.value = false
  }
}

// --- A-20 登记 ---
const createOpen = ref(false)
const createOrderId = ref('')
const createType = ref<string>(ExceptionType.OTHER)
const createDescription = ref('')
const createSubmitting = ref(false)
const createError = ref('')
const createDone = ref('')

function openCreate() {
  createOpen.value = true
  createOrderId.value = ''
  createType.value = ExceptionType.OTHER
  createDescription.value = ''
  createError.value = ''
  createDone.value = ''
}
async function submitCreate() {
  const orderId = Number(createOrderId.value)
  if (!orderId) {
    createError.value = '请填写有效的订单 ID'
    return
  }
  if (!createDescription.value.trim()) {
    createError.value = '异常说明必填'
    return
  }
  createSubmitting.value = true
  createError.value = ''
  try {
    await exceptionApi.create(orderId, {
      exception_type: createType.value as ExceptionType,
      description: createDescription.value.trim(),
    })
    createDone.value = `订单 ${orderId} 的异常已登记，并已通知 BO。`
    createOpen.value = false
    await load(true)
  } catch (e) {
    createError.value = errorMessage(e, '登记异常失败')
  } finally {
    createSubmitting.value = false
  }
}

// --- A-21 关闭 ---
const closingId = ref<number | null>(null)
const closeResolution = ref('')
const closeSubmitting = ref(false)
const closeError = ref('')

function openClose(row: ExceptionRecordItem) {
  closingId.value = row.id
  closeResolution.value = ''
  closeError.value = ''
}
async function submitClose() {
  if (closingId.value == null) return
  if (!closeResolution.value.trim()) {
    closeError.value = '请填写关闭说明（处理结果）'
    return
  }
  closeSubmitting.value = true
  closeError.value = ''
  try {
    await exceptionApi.close(closingId.value, closeResolution.value.trim())
    closingId.value = null
    await load(true)
  } catch (e) {
    closeError.value = errorMessage(e, '关闭异常失败')
  } finally {
    closeSubmitting.value = false
  }
}

// --- P4-T05 手动扫描 ---
const scanning = ref(false)
const scanResult = ref<ExceptionScanResult | null>(null)
async function runScan() {
  scanning.value = true
  error.value = ''
  try {
    const res = await exceptionApi.autoScan()
    scanResult.value = res.data || null
    await load(true)
  } catch (e) {
    error.value = errorMessage(e, '触发逾期扫描失败')
  } finally {
    scanning.value = false
  }
}

onMounted(() => load(true))
</script>

<template>
  <div class="page">
    <header class="page-header">
      <div class="crumb">
        <h1>异常管理</h1>
      </div>
      <div class="header-actions">
        <button class="btn ghost small" @click="router.push('/bo/kanban')">看板</button>
        <button class="btn ghost small" @click="router.push('/bo/review')">待审阅</button>
        <button class="btn ghost small" @click="router.push('/bo/archive')">归档</button>
        <NotificationBell />
      </div>
    </header>

    <section class="card toolbar">
      <label class="field">
        <span>状态</span>
        <select v-model="statusFilter" @change="load(true)">
          <option value="">全部</option>
          <option v-for="o in statusOptions" :key="o.value" :value="o.value">{{ o.label }}</option>
        </select>
      </label>
      <div class="btn-row">
        <button class="btn ghost" :disabled="scanning" @click="runScan">
          {{ scanning ? '扫描中…' : '触发逾期扫描' }}
        </button>
        <button class="btn primary" @click="openCreate">登记异常</button>
      </div>
    </section>

    <p v-if="scanResult" class="ok">
      扫描完成：{{ scanResult.scanned_at?.replace('T', ' ').slice(0, 16) }}，新建 {{ scanResult.created_count }} 条异常。
    </p>
    <p v-if="createDone" class="ok">{{ createDone }}</p>
    <p v-if="error" class="global-err">{{ error }}</p>

    <!-- 登记表单 -->
    <section v-if="createOpen" class="card form-card">
      <h2>登记异常（A-20）</h2>
      <div class="row">
        <label class="field">
          <span>订单 ID <em>*</em></span>
          <input v-model="createOrderId" type="number" min="1" placeholder="例如 1024" />
        </label>
        <label class="field">
          <span>异常类型 <em>*</em></span>
          <select v-model="createType">
            <option v-for="o in typeOptions" :key="o.value" :value="o.value">{{ o.label }}</option>
          </select>
        </label>
      </div>
      <label class="field">
        <span>异常说明 <em>*</em></span>
        <textarea v-model="createDescription" rows="3" placeholder="描述问题与影响范围（≤5000 字）" />
      </label>
      <p v-if="createError" class="err">{{ createError }}</p>
      <div class="btn-row">
        <button class="btn ghost" @click="createOpen = false">取消</button>
        <button class="btn primary" :disabled="createSubmitting" @click="submitCreate">
          {{ createSubmitting ? '提交中…' : '确认登记' }}
        </button>
      </div>
      <p class="muted tiny">已取消的订单不可登记异常；登记后会向所有启用的 BO 推送站内通知。</p>
    </section>

    <p v-if="loading && rows.length === 0" class="muted">加载中…</p>
    <p v-else-if="rows.length === 0" class="muted">暂无异常记录</p>

    <section v-for="row in rows" :key="row.id" class="card exception-card" :class="row.status.toLowerCase()">
      <div class="row head">
        <div class="left">
          <span class="mono order-link" @click="router.push(`/bo/review/${row.order_id}`)">{{ row.order_no }}</span>
          <span class="tag type">{{ typeLabel(row.exception_type) }}</span>
          <span class="tag" :class="row.status.toLowerCase()">{{ statusLabel(row.status) }}</span>
          <span v-if="row.task_id" class="muted tiny">任务 #{{ row.task_id }}</span>
        </div>
        <div class="right">
          <span class="muted tiny">记录人：{{ row.recorder_name }}</span>
          <button v-if="row.status !== ExceptionStatus.CLOSED" class="btn ghost small" @click="openClose(row)">
            关闭异常
          </button>
        </div>
      </div>

      <p class="description">{{ row.description }}</p>
      <p v-if="row.resolution" class="resolution">处理结果：{{ row.resolution }}</p>

      <div v-if="closingId === row.id" class="close-box">
        <label class="field">
          <span>关闭说明（处理结果）<em>*</em></span>
          <textarea v-model="closeResolution" rows="2" placeholder="例如：客户已同意延后至 11-06 交付" />
        </label>
        <p v-if="closeError" class="err">{{ closeError }}</p>
        <div class="btn-row">
          <button class="btn ghost" @click="closingId = null">取消</button>
          <button class="btn primary" :disabled="closeSubmitting" @click="submitClose">
            {{ closeSubmitting ? '提交中…' : '确认关闭' }}
          </button>
        </div>
      </div>

      <p class="muted tiny time">{{ row.created_at?.replace('T', ' ').slice(0, 16) }} · 更新 {{ row.updated_at?.replace('T', ' ').slice(0, 16) }}</p>
    </section>

    <button v-if="hasNext" class="btn ghost more" :disabled="loading" @click="load(false)">
      {{ loading ? '加载中…' : '加载更多' }}
    </button>
  </div>
</template>

<style scoped>
.page { padding: 24px; max-width: 1080px; margin: 0 auto; }
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; gap: 12px; flex-wrap: wrap; }
.crumb h1 { margin: 0; font-size: 20px; }
.header-actions { display: flex; gap: 8px; align-items: center; }

.card { background: #fff; border-radius: 8px; box-shadow: 0 1px 4px rgba(0,0,0,0.05); padding: 18px 22px; margin-bottom: 14px; }
.card h2 { margin: 0 0 12px; font-size: 15px; padding-left: 8px; border-left: 3px solid var(--primary); }
.toolbar { display: flex; align-items: flex-end; gap: 16px; flex-wrap: wrap; }
.field { display: flex; flex-direction: column; gap: 6px; margin-bottom: 10px; }
.field span { font-size: 13px; color: #606266; }
.field em { color: #f56c6c; font-style: normal; }
.field input, .field select, .field textarea {
  padding: 8px 12px; border: 1px solid #dcdfe6; border-radius: 4px; font-size: 14px; box-sizing: border-box; font-family: inherit;
}
.field textarea { resize: vertical; }
.row { display: flex; gap: 16px; flex-wrap: wrap; }
.row .field { flex: 1; min-width: 160px; }
.btn-row { display: flex; justify-content: flex-end; gap: 10px; margin-left: auto; }
.btn { padding: 8px 16px; border-radius: 4px; border: 1px solid #dcdfe6; background: #fff; cursor: pointer; font-size: 14px; }
.btn.small { padding: 6px 12px; font-size: 13px; }
.btn.primary { background: var(--primary); border-color: var(--primary); color: #fff; }
.btn.primary:hover:not(:disabled) { background: var(--primary-hover); }
.btn.ghost:hover:not(:disabled) { color: var(--primary); border-color: var(--primary-border); background: var(--primary-soft); }
.btn:disabled { opacity: 0.6; cursor: not-allowed; }
.form-card { border-left: 3px solid #e6a23c; }

.exception-card.pending_confirm { border-left: 3px solid #e6a23c; }
.exception-card.in_progress { border-left: 3px solid var(--primary); }
.exception-card.closed { border-left: 3px solid #67c23a; }
.exception-card .head { justify-content: space-between; align-items: center; margin-bottom: 8px; }
.exception-card .left, .exception-card .right { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.mono { font-family: Menlo, Consolas, monospace; font-size: 13px; color: var(--primary); }
.order-link { cursor: pointer; }
.order-link:hover { text-decoration: underline; }
.tag { font-size: 11px; border-radius: 3px; padding: 1px 8px; background: #f4f4f5; color: #606266; }
.tag.type { background: #fdf6ec; color: #e6a23c; }
.tag.pending_confirm { background: #fdf6ec; color: #e6a23c; }
.tag.in_progress { background: var(--primary-soft); color: var(--primary); }
.tag.closed { background: #f0f9eb; color: #67c23a; }
.description { margin: 10px 0 6px; font-size: 14px; color: #303133; white-space: pre-wrap; }
.resolution { margin: 0 0 6px; font-size: 13px; color: #67c23a; white-space: pre-wrap; }
.close-box { margin-top: 10px; padding: 12px; background: #fafbfc; border-radius: 6px; }
.time { margin: 8px 0 0; }
.more { width: 100%; margin-top: 6px; }
.ok { color: #67c23a; font-size: 13px; margin: 0 0 10px; }
.err { color: #f56c6c; font-size: 13px; margin: 4px 0 0; }
.muted { color: #999; font-size: 13px; }
.tiny { font-size: 12px; }
.global-err { color: #f56c6c; background: #fef0f0; border-radius: 4px; padding: 10px; }
</style>
