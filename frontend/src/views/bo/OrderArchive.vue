<script setup lang="ts">
/**
 * P3-T13 · BO 订单归档 + 时间线
 *
 * - 左栏列表：已交付（COMPLETED）订单及归档状态（GET /orders/completed）
 * - A-19 归档：生成 OpenPDF 归档报告（订单快照 + 版本 + 反馈 + 确认留痕 + 操作时间线）
 * - A-28 下载：后端 302 → 5 分钟预签名 GET，前端按 blob 下载
 *
 * 归档前提与后端一致：订单 COMPLETED，且全部设计任务已批准、存在客户确认版本。
 */
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { orderApi, type OrderBrief } from '../../api/modules/order'
import { archiveApi } from '../../api/modules/clientReview'
import { errorMessage } from '../../api/http'
import OrderStatusTag from '../../components/common/OrderStatusTag.vue'
import NotificationBell from '../../components/common/NotificationBell.vue'

const router = useRouter()

const orders = ref<OrderBrief[]>([])
const loading = ref(true)
const error = ref('')
const actionError = ref('')
const notice = ref('')
const actingId = ref<number | null>(null)

const archivedCount = computed(() => orders.value.filter((order) => order.archived).length)
const pendingCount = computed(() => orders.value.filter((order) => !order.archived).length)

async function load() {
  loading.value = true
  error.value = ''
  try {
    const res = await orderApi.listCompletedOrders()
    orders.value = (res.data || []) as OrderBrief[]
  } catch (e) {
    error.value = errorMessage(e, '加载已交付订单失败')
  } finally {
    loading.value = false
  }
}

async function archive(order: OrderBrief) {
  if (!window.confirm(`确认归档订单 ${order.order_no}？归档后将生成不可变的 PDF 报告。`)) return
  actingId.value = order.id
  actionError.value = ''
  notice.value = ''
  try {
    const res = await archiveApi.archiveOrder(order.id)
    const archiveInfo = res.data
    notice.value = `订单 ${order.order_no} 已归档（${archiveInfo?.archived_at || ''}）。`
    await load()
  } catch (e) {
    actionError.value = errorMessage(e, '归档失败')
  } finally {
    actingId.value = null
  }
}

async function download(order: OrderBrief) {
  actingId.value = order.id
  actionError.value = ''
  notice.value = ''
  try {
    await archiveApi.downloadReport(order.id, `archive-report-${order.order_no}.pdf`)
  } catch (e) {
    actionError.value = errorMessage(e, '下载归档报告失败')
  } finally {
    actingId.value = null
  }
}

onMounted(load)
</script>

<template>
  <div class="page">
    <header class="page-header">
      <div class="crumb">
        <button class="btn ghost small" @click="router.push('/bo/review')">← 返回待审阅订单</button>
        <h1>订单归档</h1>
      </div>
      <div class="header-actions">
        <button class="btn ghost" @click="router.push('/bo/kanban')">生产看板 →</button>
        <button class="btn ghost" @click="router.push('/bo/exceptions')">异常管理 →</button>
        <button class="btn ghost" @click="router.push('/bo/internal-review')">内部评审 →</button>
        <NotificationBell />
      </div>
    </header>

    <p class="desc">
      已交付 {{ orders.length }} 单 · 待归档 {{ pendingCount }} 单 · 已归档 {{ archivedCount }} 单
    </p>

    <p v-if="notice" class="ok">{{ notice }}</p>
    <p v-if="actionError" class="err">{{ actionError }}</p>

    <section class="card">
      <p v-if="loading" class="muted">加载中…</p>
      <p v-else-if="error" class="global-err">{{ error }}</p>
      <p v-else-if="!orders.length" class="empty">
        当前没有已交付订单。AM 在客户确认后标记“已交付”，订单才会出现在这里。
      </p>
      <table v-else class="list-table">
        <thead>
          <tr>
            <th style="width: 150px">订单号</th>
            <th>客户名称</th>
            <th style="width: 110px">状态</th>
            <th style="width: 110px">业务负责人</th>
            <th style="width: 140px">交付截止</th>
            <th style="width: 110px">归档状态</th>
            <th style="width: 170px">归档时间</th>
            <th style="width: 150px">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="order in orders" :key="order.id">
            <td class="mono">{{ order.order_no }}</td>
            <td>{{ order.customer_name }}</td>
            <td><OrderStatusTag :status="order.status" /></td>
            <td class="muted">{{ order.am_name }}</td>
            <td>{{ order.delivery_deadline }}</td>
            <td>
              <span v-if="order.archived" class="archived-flag">已归档</span>
              <span v-else class="pending-flag">待归档</span>
            </td>
            <td class="muted">{{ order.archived_at || '—' }}</td>
            <td class="ops">
              <button
                v-if="!order.archived"
                class="btn small primary"
                :disabled="actingId === order.id"
                @click="archive(order)"
              >{{ actingId === order.id ? '归档中…' : '归档' }}</button>
              <button
                v-else
                class="btn small"
                :disabled="actingId === order.id"
                @click="download(order)"
              >下载 PDF 报告</button>
            </td>
          </tr>
        </tbody>
      </table>
    </section>

    <p class="muted tiny">
      归档报告包含：订单需求快照、物料任务与设计版本、客户评审与反馈、客户确认留痕、操作时间线。
      归档后订单保持“已完成”终态，不可回退。
    </p>
  </div>
</template>

<style scoped>
.page { padding: 24px; max-width: 1140px; margin: 0 auto; }
.page-header {
  display: flex; justify-content: space-between; align-items: center;
  margin-bottom: 8px; gap: 12px; flex-wrap: wrap;
}
.crumb { display: flex; align-items: center; gap: 12px; }
.crumb h1 { margin: 0; font-size: 20px; }
.header-actions { display: flex; gap: 10px; }
.desc { margin: 0 0 16px; color: #666; font-size: 13px; }

.card {
  background: #fff; border-radius: 8px; box-shadow: 0 1px 4px rgba(0, 0, 0, 0.05);
  padding: 12px 24px;
}

.list-table { width: 100%; border-collapse: collapse; font-size: 14px; }
.list-table th, .list-table td {
  text-align: left; padding: 12px 10px; border-bottom: 1px solid #ebeef5;
}
.list-table thead th { font-weight: 500; color: #606266; background: #fafbfc; font-size: 13px; }
.mono { font-family: Menlo, Consolas, monospace; color: var(--primary); }
.ops { white-space: nowrap; }

.archived-flag {
  display: inline-block; padding: 1px 8px; font-size: 12px; border-radius: 3px;
  color: #67c23a; background: #f0f9eb; border: 1px solid #c2e7b0;
}
.pending-flag {
  display: inline-block; padding: 1px 8px; font-size: 12px; border-radius: 3px;
  color: #e6a23c; background: #fdf6ec; border: 1px solid #f5dab1;
}

.btn { padding: 8px 16px; border-radius: 4px; border: 1px solid #dcdfe6; background: #fff; cursor: pointer; font-size: 14px; }
.btn.small { padding: 5px 12px; font-size: 13px; }
.btn.primary { background: var(--primary); border-color: var(--primary); color: #fff; }
.btn.primary:hover:not(:disabled) { background: var(--primary-hover); border-color: var(--primary-hover); }
.btn.ghost { background: #fff; color: #606266; }
.btn.ghost:hover:not(:disabled) { color: var(--primary); border-color: var(--primary-border); background: var(--primary-soft); }
.btn:disabled { opacity: 0.6; cursor: not-allowed; }

.err { color: #f56c6c; background: #fef0f0; border-radius: 4px; padding: 8px 10px; font-size: 13px; }
.ok { color: #67c23a; background: #f0f9eb; border-radius: 4px; padding: 8px 10px; font-size: 13px; }
.muted { color: #999; font-size: 13px; }
.muted.tiny { font-size: 12px; margin-top: 12px; }
.empty, .global-err { padding: 20px 0; text-align: center; color: #999; }
.global-err { color: #f56c6c; background: #fef0f0; border-radius: 4px; padding: 10px; }
</style>
