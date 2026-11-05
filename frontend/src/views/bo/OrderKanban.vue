<script setup lang="ts">
/**
 * P4-T13 · A-25 BO 生产看板
 * 四状态列 + AM / 客户名 / 创建日期筛选，卡片展示任务数与未解决反馈数。
 */
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { kanbanApi, type KanbanCard, type KanbanResponse } from '../../api/modules/phase4'
import { errorMessage } from '../../api/http'
import OrderStatusTag from '../../components/common/OrderStatusTag.vue'
import NotificationBell from '../../components/common/NotificationBell.vue'
import { OrderStatus } from '../../types/enums'

const router = useRouter()
const board = ref<KanbanResponse | null>(null)
const loading = ref(false)
const error = ref('')

const filters = reactive({ amId: '', customer: '', from: '', to: '' })

const columnTitles: Record<string, string> = {
  [OrderStatus.PENDING_CONFIRM]: '待确认',
  [OrderStatus.IN_PRODUCTION]: '生产中',
  [OrderStatus.COMPLETED]: '已完成',
  [OrderStatus.CANCELLED]: '已取消',
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    const res = await kanbanApi.getKanban({
      am_id: filters.amId ? Number(filters.amId) : undefined,
      customer: filters.customer.trim() || undefined,
      from: filters.from || undefined,
      to: filters.to || undefined,
    })
    board.value = res.data || null
  } catch (e) {
    error.value = errorMessage(e, '加载看板失败')
  } finally {
    loading.value = false
  }
}

function reset() {
  filters.amId = ''
  filters.customer = ''
  filters.from = ''
  filters.to = ''
  void load()
}

function openOrder(card: KanbanCard) {
  router.push(`/bo/review/${card.id}`)
}

function orderNos(card: KanbanCard) {
  const parts = [card.customer_name, card.order_no, card.am_name ? `AM ${card.am_name}` : '']
  return parts.filter(Boolean).join(' · ')
}

onMounted(load)
</script>

<template>
  <div class="page">
    <header class="page-header">
      <div class="crumb">
        <h1>生产看板</h1>
        <span v-if="board" class="muted">生成于 {{ board.generated_at?.replace('T', ' ').slice(0, 16) }}</span>
      </div>
      <div class="header-actions">
        <button class="btn ghost small" @click="router.push('/bo/exceptions')">异常管理</button>
        <button class="btn ghost small" @click="router.push('/bo/review')">待审阅</button>
        <button class="btn ghost small" @click="router.push('/bo/archive')">归档</button>
        <NotificationBell />
      </div>
    </header>

    <section class="filters card">
      <label class="field">
        <span>AM ID</span>
        <input v-model="filters.amId" type="number" min="1" placeholder="全部" @keyup.enter="load" />
      </label>
      <label class="field wide">
        <span>客户名称</span>
        <input v-model="filters.customer" type="text" placeholder="模糊匹配" @keyup.enter="load" />
      </label>
      <label class="field">
        <span>创建日期（起）</span>
        <input v-model="filters.from" type="date" />
      </label>
      <label class="field">
        <span>创建日期（止）</span>
        <input v-model="filters.to" type="date" />
      </label>
      <div class="btn-row">
        <button class="btn ghost" @click="reset">重置</button>
        <button class="btn primary" :disabled="loading" @click="load">{{ loading ? '加载中…' : '查询' }}</button>
      </div>
    </section>

    <p v-if="error" class="global-err">{{ error }}</p>

    <div v-if="board" class="board">
      <section v-for="col in board.columns" :key="col.status" class="column" :class="col.status.toLowerCase()">
        <header class="col-head">
          <strong>{{ columnTitles[col.status] || col.status }}</strong>
          <span class="count">{{ col.total }}</span>
        </header>
        <div class="cards">
          <article v-for="card in col.orders" :key="card.id" class="kanban-card" @click="openOrder(card)">
            <div class="row">
              <span class="order-no mono">{{ card.order_no }}</span>
              <OrderStatusTag :status="card.status" />
            </div>
            <p class="customer">{{ card.customer_name }}</p>
            <p class="muted tiny">{{ orderNos(card) }}</p>
            <div class="row meta">
              <span class="pill">任务 {{ card.task_count }}</span>
              <span class="pill" :class="{ warn: card.open_feedback_count > 0 }">
                未解决反馈 {{ card.open_feedback_count }}
              </span>
              <span class="muted tiny deadline">交付 {{ card.delivery_deadline || '-' }}</span>
            </div>
          </article>
          <p v-if="col.orders.length === 0" class="muted empty">（本列暂无订单）</p>
          <p v-if="col.total > col.orders.length" class="muted tiny more">
            仅展示最近 {{ col.orders.length }} / {{ col.total }} 单
          </p>
        </div>
      </section>
    </div>
    <p v-else-if="loading" class="muted">加载中…</p>
  </div>
</template>

<style scoped>
.page { padding: 24px; max-width: 1400px; margin: 0 auto; }
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; gap: 12px; flex-wrap: wrap; }
.crumb { display: flex; align-items: baseline; gap: 12px; }
.crumb h1 { margin: 0; font-size: 20px; }
.header-actions { display: flex; gap: 8px; align-items: center; }

.card { background: #fff; border-radius: 8px; box-shadow: 0 1px 4px rgba(0,0,0,0.05); padding: 16px 20px; }
.filters { display: flex; gap: 16px; align-items: flex-end; flex-wrap: wrap; margin-bottom: 16px; }
.field { display: flex; flex-direction: column; gap: 6px; min-width: 130px; }
.field.wide { flex: 1; min-width: 200px; }
.field span { font-size: 13px; color: #606266; }
.field input {
  padding: 8px 12px; border: 1px solid #dcdfe6; border-radius: 4px; font-size: 14px; box-sizing: border-box;
}
.btn-row { display: flex; gap: 10px; margin-left: auto; }
.btn { padding: 8px 16px; border-radius: 4px; border: 1px solid #dcdfe6; background: #fff; cursor: pointer; font-size: 14px; }
.btn.small { padding: 6px 12px; font-size: 13px; }
.btn.primary { background: var(--primary); border-color: var(--primary); color: #fff; }
.btn.primary:hover:not(:disabled) { background: var(--primary-hover); }
.btn.ghost:hover:not(:disabled) { color: var(--primary); border-color: var(--primary-border); background: var(--primary-soft); }
.btn:disabled { opacity: 0.6; cursor: not-allowed; }

.board { display: grid; grid-template-columns: repeat(4, minmax(240px, 1fr)); gap: 14px; align-items: start; }
.column { background: #fafbfc; border-radius: 8px; padding: 12px; min-height: 200px; border-top: 3px solid #909399; }
.column.pending_confirm { border-top-color: #e6a23c; }
.column.in_production { border-top-color: var(--primary); }
.column.completed { border-top-color: #67c23a; }
.column.cancelled { border-top-color: #909399; }
.col-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 10px; }
.col-head strong { font-size: 14px; }
.count { background: #fff; border-radius: 10px; padding: 1px 8px; font-size: 12px; color: #606266; }
.cards { display: flex; flex-direction: column; gap: 10px; }
.kanban-card { background: #fff; border-radius: 6px; padding: 12px; cursor: pointer; box-shadow: 0 1px 3px rgba(0,0,0,0.05); }
.kanban-card:hover { box-shadow: 0 2px 10px rgba(64,158,255,0.18); transform: translateY(-1px); }
.row { display: flex; justify-content: space-between; align-items: center; gap: 8px; flex-wrap: wrap; }
.order-no { font-family: Menlo, Consolas, monospace; font-size: 12px; color: var(--primary); }
.customer { margin: 8px 0 2px; font-size: 14px; color: #303133; font-weight: 500; }
.meta { margin-top: 8px; justify-content: flex-start; gap: 6px; }
.pill { font-size: 11px; background: #f4f4f5; color: #606266; border-radius: 10px; padding: 2px 8px; }
.pill.warn { background: #fef0f0; color: #f56c6c; }
.deadline { margin-left: auto; }
.empty { padding: 12px 0; }
.more { padding-top: 6px; }
.muted { color: #999; font-size: 13px; }
.tiny { font-size: 12px; }
.global-err { color: #f56c6c; background: #fef0f0; border-radius: 4px; padding: 10px; }
</style>
