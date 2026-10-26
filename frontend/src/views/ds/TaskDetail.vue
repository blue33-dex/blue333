<script setup lang="ts">
/**
 * P1-T32 · DS 任务详情（Phase 1 只读）
 *
 * TODO(后端就绪):
 *   - materialTaskApi.getById 自动走真实 GET /api/v1/material-tasks/{id}
 *   - Phase 2 版本上传入口（multipart → 对象存储 MinIO）
 *   - Phase 2 渲染版本列表（含状态、上传人、时间）
 */
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { materialTaskApi, type TaskDetail } from '../../api/modules/materialTask'
import TaskStatusTag from '../../components/common/TaskStatusTag.vue'

const route = useRoute()
const router = useRouter()

const taskId = computed(() => Number(route.params.taskId))
const task = ref<TaskDetail | null>(null)
const loading = ref(true)
const error = ref('')

/** 截止时间倒计时（只读信息展示，不做自动刷新）*/
const countdown = computed(() => {
  if (!task.value?.task_deadline) return ''
  const deadline = new Date(task.value.task_deadline).getTime()
  const now = Date.now()
  const diff = deadline - now
  if (diff <= 0) return '已过期'
  const days = Math.floor(diff / (1000 * 60 * 60 * 24))
  const hours = Math.floor((diff % (1000 * 60 * 60 * 24)) / (1000 * 60 * 60))
  if (days > 0) return `剩 ${days} 天 ${hours} 小时`
  const mins = Math.floor((diff % (1000 * 60 * 60)) / (1000 * 60))
  return `剩 ${hours} 小时 ${mins} 分钟`
})

async function load() {
  loading.value = true
  error.value = ''
  try {
    const res = await materialTaskApi.getById(taskId.value)
    task.value = (res.data as TaskDetail) || null
    if (!task.value) error.value = '任务不存在'
  } catch (e: any) {
    error.value = e?.message || '加载任务详情失败'
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="page">
    <header class="page-header">
      <div class="crumb">
        <button class="btn ghost small" @click="router.push('/ds/tasks')">← 返回任务列表</button>
        <h1>任务详情</h1>
      </div>
      <div class="header-meta">
        <span v-if="task" class="mono">{{ task.order_no }}</span>
        <template v-if="task"><TaskStatusTag :status="task.status" /></template>
      </div>
    </header>

    <p v-if="loading" class="muted">加载中…</p>
    <p v-else-if="error" class="global-err">{{ error }}</p>

    <template v-else-if="task">
      <section class="card">
        <h2>任务信息</h2>
        <dl class="kv-grid">
          <div class="kv"><dt>订单号</dt><dd class="mono">{{ task.order_no }}</dd></div>
          <div class="kv"><dt>物料类型</dt><dd>{{ task.material_type }}</dd></div>
          <div class="kv"><dt>规格尺寸</dt><dd>{{ task.spec_size }}</dd></div>
          <div class="kv"><dt>状态</dt><dd><TaskStatusTag :status="task.status" /></dd></div>
          <div class="kv"><dt>优先级</dt><dd>{{ task.priority }}</dd></div>
          <div class="kv"><dt>指派设计师</dt><dd>{{ task.assignee_name || '（未指派）' }}</dd></div>
          <div class="kv"><dt>任务截止</dt><dd>{{ task.task_deadline }}</dd></div>
          <div class="kv"><dt>剩余时间</dt>
            <dd :class="{ 'urgent': countdown.startsWith('剩') && countdown.includes('天') === false }">
              {{ countdown }}
            </dd>
          </div>
          <div class="kv"><dt>创建时间</dt><dd>{{ task.created_at }}</dd></div>
          <div class="kv"><dt>最后更新</dt><dd>{{ task.updated_at }}</dd></div>
        </dl>
      </section>

      <section class="card">
        <h2>版本交付 <span class="tag-todo">P2-TXX 待实现</span></h2>
        <div class="attach-placeholder">
          <p>Phase 1 只读，设计师无法在此页面上传设计版本。</p>
          <p class="muted">Phase 2 接入后端后：此处展示版本列表（含上传人、时间、状态、下载链接），并提供"上传新版本"入口。</p>
        </div>
      </section>
    </template>
  </div>
</template>

<style scoped>
.page { padding: 24px; max-width: 960px; margin: 0 auto; }
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
.kv dt { color: #909399; font-size: 13px; min-width: 84px; flex-shrink: 0; }
.kv dd { margin: 0; font-size: 14px; color: #333; }
.urgent { color: #f56c6c; font-weight: 500; }

.btn { padding: 8px 16px; border-radius: 4px; border: 1px solid #dcdfe6; background: #fff; cursor: pointer; font-size: 14px; }
.btn.small { padding: 6px 12px; font-size: 13px; }
.btn.ghost { background: #fff; color: #606266; }
.btn.ghost:hover:not(:disabled) { color: #409eff; border-color: #c6e2ff; background: #ecf5ff; }

.tag-todo {
  font-size: 11px; padding: 1px 6px; background: #fdf6ec; color: #e6a23c;
  border-radius: 3px; margin-left: 6px; border: 1px solid #faecd8;
}
.attach-placeholder {
  background: #f7f9fc; border: 1px dashed #dcdfe6; border-radius: 6px;
  padding: 16px; color: #606266; font-size: 13px;
}
.attach-placeholder .muted { color: #999; margin: 4px 0 0; }

.muted { color: #999; font-size: 13px; }
.global-err { color: #f56c6c; background: #fef0f0; border-radius: 4px; padding: 10px; }
</style>
