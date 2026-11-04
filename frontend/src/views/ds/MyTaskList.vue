<script setup lang="ts">
/**
 * P1-T32 + P2-T14 · DS 我的任务列表
 *
 * 点击行进入任务详情，可上传版本、提交内部评审（服务 7 流程）
 */
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { materialTaskApi, type TaskSummary } from '../../api/modules/materialTask'
import { errorMessage } from '../../api/http'
import TaskStatusTag from '../../components/common/TaskStatusTag.vue'

const router = useRouter()
const tasks = ref<TaskSummary[]>([])
const loading = ref(true)
const error = ref('')

async function load() {
  loading.value = true
  error.value = ''
  try {
    const res = await materialTaskApi.listMine()
    tasks.value = (res.data as TaskSummary[]) || []
  } catch (e) {
    error.value = errorMessage(e, '加载任务列表失败')
  } finally {
    loading.value = false
  }
}

function goDetail(id: number) {
  router.push(`/ds/tasks/${id}`)
}

onMounted(load)
</script>

<template>
  <div class="page">
    <header class="page-header">
      <div>
        <h1>我的任务</h1>
        <p class="desc">共 {{ tasks.length }} 条</p>
      </div>
    </header>

    <section class="card">
      <p v-if="loading" class="muted">加载中…</p>
      <p v-else-if="error" class="global-err">{{ error }}</p>
      <p v-else-if="!tasks.length" class="empty">当前没有分配给你的设计任务。</p>
      <table v-else class="list-table">
        <thead>
          <tr>
            <th style="width: 160px">订单号</th>
            <th>物料类型</th>
            <th>规格尺寸</th>
            <th style="width: 110px">状态</th>
            <th style="width: 180px">任务截止</th>
          </tr>
        </thead>
        <tbody>
          <tr
            v-for="t in tasks"
            :key="t.id"
            class="clickable-row"
            @click="goDetail(t.id)"
          >
            <td class="mono">{{ t.order_no }}</td>
            <td>{{ t.material_type }}</td>
            <td>{{ t.spec_size }}</td>
            <td><TaskStatusTag :status="t.status" /></td>
            <td class="muted">{{ t.task_deadline }}</td>
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
