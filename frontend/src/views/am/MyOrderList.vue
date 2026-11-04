<script setup lang="ts">
// P1-T28 · AM 我的订单列表（数据源 GET /api/v1/orders/mine）
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { orderApi, type OrderBrief } from '../../api/modules/order'
import { errorMessage } from '../../api/http'
import OrderStatusTag from '../../components/common/OrderStatusTag.vue'
import { ClientReviewStatus, OrderStatus } from '../../types/enums'

const router = useRouter()
const orders = ref<OrderBrief[]>([])
const loading = ref(true)
const error = ref('')

async function loadOrders() {
  loading.value = true
  error.value = ''
  try {
    const res = await orderApi.listMyOrders()
    orders.value = (res.data as OrderBrief[]) || []
  } catch (e) {
    error.value = errorMessage(e, '加载订单列表失败')
  } finally {
    loading.value = false
  }
}

function openDetail(id: number) {
  router.push(`/am/orders/${id}`)
}

/** P3-T10：进入客户评审页（发起评审 / 记录反馈 / 客户确认 / 交付）*/
function goClientReview(id: number) {
  router.push(`/am/orders/${id}/client-review`)
}

/** 评审状态展示文案（后端未返回该字段表示尚未发起评审）*/
function reviewStatusLabel(status?: string): string {
  if (status === ClientReviewStatus.IN_REVIEW) return '评审中'
  if (status === ClientReviewStatus.COMPLETED) return '客户已确认'
  return '—'
}

function goCreate() {
  router.push('/am/orders/create')
}

onMounted(loadOrders)
</script>

<template>
  <div class="page">
    <header class="page-header">
      <div>
        <h1>我的订单</h1>
        <p class="desc">共 {{ orders.length }} 条</p>
      </div>
      <button class="btn primary" @click="goCreate">+ 新建订单</button>
    </header>

    <section class="card">
      <p v-if="loading" class="muted">加载中…</p>
      <p v-else-if="error" class="global-err">{{ error }}</p>
      <p v-else-if="!orders.length" class="empty">暂无订单。点击右上角"新建订单"开始。</p>
      <table v-else class="list-table">
        <thead>
          <tr>
            <th style="width: 160px">订单号</th>
            <th>客户名称</th>
            <th style="width: 110px">状态</th>
            <th style="width: 110px">评审状态</th>
            <th style="width: 100px">未解决反馈</th>
            <th style="width: 140px">交付截止</th>
            <th style="width: 180px">创建时间</th>
            <th style="width: 110px">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr
            v-for="o in orders"
            :key="o.id"
            class="clickable-row"
            @click="openDetail(o.id)"
          >
            <td class="mono">{{ o.order_no }}</td>
            <td>{{ o.customer_name }}</td>
            <td>
              <OrderStatusTag :status="o.status" />
              <span
                v-if="o.status === OrderStatus.PENDING_CONFIRM && o.reject_reason"
                class="rejected-flag"
                :title="o.reject_reason.remark"
              >已退回待补充</span>
            </td>
            <td>
              <span
                v-if="o.client_review_status"
                class="review-flag"
                :class="o.client_review_status === ClientReviewStatus.COMPLETED ? 'done' : 'active'"
              >{{ reviewStatusLabel(o.client_review_status) }}</span>
              <span v-else class="muted">—</span>
            </td>
            <td>
              <span v-if="o.open_feedback_count" class="feedback-count">{{ o.open_feedback_count }}</span>
              <span v-else class="muted">0</span>
            </td>
            <td>{{ o.delivery_deadline }}</td>
            <td class="muted">{{ o.created_at }}</td>
            <td class="ops">
              <button
                v-if="o.status === OrderStatus.IN_PRODUCTION"
                class="link"
                @click.stop="goClientReview(o.id)"
              >客户评审</button>
              <span v-else class="muted">—</span>
            </td>
          </tr>
        </tbody>
      </table>
    </section>
  </div>
</template>

<style scoped>
.page { padding: 24px; max-width: 1100px; margin: 0 auto; }
.page-header {
  display: flex; justify-content: space-between; align-items: center;
  margin-bottom: 16px;
}
.page-header h1 { margin: 0 0 2px; font-size: 20px; }
.page-header .desc { margin: 0; color: #666; font-size: 13px; }

.btn {
  padding: 9px 18px; border-radius: 4px; border: 1px solid #dcdfe6; background: #fff; cursor: pointer; font-size: 14px;
}
.btn.primary { background: #409eff; border-color: #409eff; color: #fff; }
.btn.primary:hover { background: #66b1ff; border-color: #66b1ff; }

.card {
  background: #fff; border-radius: 8px; box-shadow: 0 1px 4px rgba(0, 0, 0, 0.05);
  padding: 12px 24px;
}

.list-table { width: 100%; border-collapse: collapse; font-size: 14px; }
.list-table th, .list-table td {
  text-align: left;
  padding: 12px 10px;
  border-bottom: 1px solid #ebeef5;
}
.list-table thead th {
  font-weight: 500; color: #606266; background: #fafbfc; font-size: 13px;
}
.list-table tbody tr.clickable-row {
  cursor: pointer;
  transition: background 0.15s;
}
.list-table tbody tr.clickable-row:hover { background: #f5f7fa; }
.mono { font-family: Menlo, Consolas, monospace; color: #409eff; }
.rejected-flag {
  display: inline-block; margin-left: 6px; padding: 1px 6px;
  font-size: 12px; color: #f56c6c; background: #fef0f0;
  border: 1px solid #fbc4c4; border-radius: 3px;
}
.review-flag {
  display: inline-block; padding: 1px 8px; font-size: 12px; border-radius: 3px;
  border: 1px solid transparent;
}
.review-flag.active { color: #e6a23c; background: #fdf6ec; border-color: #f5dab1; }
.review-flag.done { color: #67c23a; background: #f0f9eb; border-color: #c2e7b0; }
.feedback-count {
  display: inline-block; min-width: 22px; text-align: center; padding: 1px 6px;
  font-size: 12px; color: #f56c6c; background: #fef0f0;
  border: 1px solid #fbc4c4; border-radius: 10px;
}
.ops { white-space: nowrap; }
.link {
  border: none; background: none; color: #409eff; cursor: pointer;
  padding: 0; font-size: 13px; text-decoration: underline;
}
.muted { color: #999; }
.empty, .global-err { padding: 20px 0; text-align: center; color: #999; }
.global-err { color: #f56c6c; background: #fef0f0; border-radius: 4px; padding: 10px; }
</style>
