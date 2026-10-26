<script setup lang="ts">
/**
 * P1-T30 · BO 待审阅列表
 *
 * TODO(后端就绪):
 *   - orderApi.listMyOrders 自动走真实 GET /api/v1/orders/mine
 *   - 分页、筛选（状态/日期/关键字）按 PRD 补充
 */
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { orderApi, type OrderBrief } from '../../api/modules/order'
import OrderStatusTag from '../../components/common/OrderStatusTag.vue'
import { OrderStatus } from '../../types/enums'

const router = useRouter()
const allOrders = ref<OrderBrief[]>([])
const loading = ref(true)
const error = ref('')

/** BO 审阅队列 = 所有 PENDING_CONFIRM 的订单，按 updated_at 倒序 */
const pendingOrders = computed(() =>
  allOrders.value
    .filter((o) => o.status === OrderStatus.PENDING_CONFIRM)
    .sort((a, b) => (b.created_at || '').localeCompare(a.created_at || ''))
)

async function load() {
  loading.value = true
  error.value = ''
  try {
    const res = await orderApi.listMyOrders()
    allOrders.value = (res.data as OrderBrief[]) || []
  } catch (e: any) {
    error.value = e?.message || '加载待审阅列表失败'
  } finally {
    loading.value = false
  }
}

function goReview(id: number) {
  router.push(`/bo/review/${id}`)
}

onMounted(load)
</script>

<template>
  <div class="page">
    <header class="page-header">
      <div>
        <h1>待审阅订单</h1>
        <p class="desc">共 {{ pendingOrders.length }} 条待处理（Phase 1 mock 数据）</p>
      </div>
    </header>

    <section class="card">
      <p v-if="loading" class="muted">加载中…</p>
      <p v-else-if="error" class="global-err">{{ error }}</p>
      <p v-else-if="!pendingOrders.length" class="empty">当前没有待审阅的订单。</p>
      <table v-else class="list-table">
        <thead>
          <tr>
            <th style="width: 160px">订单号</th>
            <th>客户名称</th>
            <th style="width: 110px">状态</th>
            <th style="width: 140px">交付截止</th>
            <th style="width: 180px">创建时间</th>
          </tr>
        </thead>
        <tbody>
          <tr
            v-for="o in pendingOrders"
            :key="o.id"
            class="clickable-row"
            @click="goReview(o.id)"
          >
            <td class="mono">{{ o.order_no }}</td>
            <td>{{ o.customer_name }}</td>
            <td><OrderStatusTag :status="o.status" /></td>
            <td>{{ o.delivery_deadline }}</td>
            <td class="muted">{{ o.created_at }}</td>
          </tr>
        </tbody>
      </table>
    </section>
  </div>
</template>

<style scoped>
.page { padding: 24px; max-width: 1100px; margin: 0 auto; }
.page-header { margin-bottom: 16px; }
.page-header h1 { margin: 0 0 2px; font-size: 20px; }
.page-header .desc { margin: 0; color: #666; font-size: 13px; }

.card {
  background: #fff; border-radius: 8px; box-shadow: 0 1px 4px rgba(0, 0, 0, 0.05);
  padding: 12px 24px;
}

.list-table { width: 100%; border-collapse: collapse; font-size: 14px; }
.list-table th, .list-table td {
  text-align: left; padding: 12px 10px; border-bottom: 1px solid #ebeef5;
}
.list-table thead th { font-weight: 500; color: #606266; background: #fafbfc; font-size: 13px; }
.list-table tbody tr.clickable-row { cursor: pointer; transition: background 0.15s; }
.list-table tbody tr.clickable-row:hover { background: #f5f7fa; }

.mono { font-family: Menlo, Consolas, monospace; color: #409eff; }
.muted { color: #999; }
.empty, .global-err { padding: 20px 0; text-align: center; color: #999; }
.global-err { color: #f56c6c; background: #fef0f0; border-radius: 4px; padding: 10px; }
</style>
