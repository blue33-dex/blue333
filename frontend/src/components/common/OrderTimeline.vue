<script setup lang="ts">
/**
 * P4-T14 · A-29 订单时间线（AM / BO / DS 可见，BR-13 由后端校验）
 * 游标升序加载：首屏最新 20 条之前的更早记录通过"加载更多"追加到顶部。
 */
import { onMounted, ref } from 'vue'
import { timelineApi, type TimelineEntry } from '../../api/modules/phase4'
import { errorMessage } from '../../api/http'

const props = defineProps<{ orderId: number }>()

const entries = ref<TimelineEntry[]>([])
const loading = ref(false)
const error = ref('')
const cursor = ref<string | undefined>(undefined)
const hasNext = ref(false)

async function load(reset = false) {
  loading.value = true
  error.value = ''
  try {
    const res = await timelineApi.list(props.orderId, {
      limit: 20,
      cursor: reset ? undefined : cursor.value,
    })
    const rows = res.data || []
    entries.value = reset ? rows : [...rows, ...entries.value]
    cursor.value = res.pagination?.next_cursor
    hasNext.value = !!res.pagination?.has_next
  } catch (e) {
    error.value = errorMessage(e, '加载时间线失败')
  } finally {
    loading.value = false
  }
}

function fmt(t: string) {
  return t?.replace('T', ' ').slice(0, 16) || '-'
}

function exportTimeline() {
  const lines = entries.value.map(
    (e) =>
      `${fmt(e.operated_at)} | ${e.action_label}(${e.action_type}) | ${e.operator_name}` +
      `${e.before_state || e.after_state ? ` | ${e.before_state || '-'} → ${e.after_state || '-'}` : ''}` +
      `${e.remark ? ` | ${e.remark}` : ''}`
  )
  const content = [`订单 ${props.orderId} 操作时间线`, ...lines].join('\r\n')
  const blob = new Blob([content], { type: 'text/plain;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `order-${props.orderId}-timeline.txt`
  a.click()
  URL.revokeObjectURL(url)
}

onMounted(() => load(true))
</script>

<template>
  <section class="card">
    <div class="head">
      <h2>操作时间线</h2>
      <div class="actions">
        <button v-if="entries.length" class="btn ghost small" @click="exportTimeline">导出</button>
        <button class="btn ghost small" :disabled="loading" @click="load(true)">刷新</button>
      </div>
    </div>

    <p v-if="error" class="err">{{ error }}</p>
    <p v-if="loading && entries.length === 0" class="muted">加载中…</p>
    <p v-else-if="entries.length === 0" class="muted">（暂无操作记录）</p>

    <template v-else>
      <button v-if="hasNext" class="btn ghost small more" :disabled="loading" @click="load(false)">
        {{ loading ? '加载中…' : '加载更早的记录' }}
      </button>
      <ul class="timeline">
        <li v-for="e in entries" :key="e.id" class="node">
          <span class="dot" />
          <div class="body">
            <div class="row">
              <strong>{{ e.action_label }}</strong>
              <span class="muted tiny">{{ fmt(e.operated_at) }}</span>
            </div>
            <div class="row meta">
              <span class="tag">{{ e.target_type }}</span>
              <span class="muted tiny">操作人：{{ e.operator_name }}</span>
              <span v-if="e.before_state || e.after_state" class="state">
                {{ e.before_state || '-' }} → {{ e.after_state || '-' }}
              </span>
            </div>
            <p v-if="e.remark" class="remark">{{ e.remark }}</p>
          </div>
        </li>
      </ul>
    </template>
  </section>
</template>

<style scoped>
.card { background: #fff; border-radius: 8px; box-shadow: 0 1px 4px rgba(0,0,0,0.05); padding: 20px 24px; margin-bottom: 16px; }
.head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; }
.card h2 { margin: 0; font-size: 15px; padding-left: 8px; border-left: 3px solid #409eff; }
.actions { display: flex; gap: 8px; }
.more { margin-bottom: 12px; }
.timeline { margin: 0; padding: 0 0 0 6px; list-style: none; position: relative; }
.node { position: relative; padding: 0 0 16px 18px; border-left: 2px solid #ebeef5; }
.node:last-child { border-left-color: transparent; padding-bottom: 0; }
.dot { position: absolute; left: -6px; top: 4px; width: 10px; height: 10px; border-radius: 50%; background: #409eff; }
.body .row { display: flex; gap: 10px; align-items: center; flex-wrap: wrap; }
.body strong { font-size: 14px; color: #303133; font-weight: 500; }
.meta { margin-top: 4px; }
.tag { font-size: 11px; color: #409eff; background: #ecf5ff; border-radius: 3px; padding: 1px 6px; }
.state { font-size: 12px; color: #606266; background: #f4f4f5; border-radius: 3px; padding: 1px 6px; font-family: Menlo, Consolas, monospace; }
.remark { margin: 6px 0 0; font-size: 13px; color: #606266; white-space: pre-wrap; }
.btn { padding: 6px 12px; border-radius: 4px; border: 1px solid #dcdfe6; background: #fff; cursor: pointer; font-size: 13px; }
.btn:hover:not(:disabled) { color: #409eff; border-color: #c6e2ff; background: #ecf5ff; }
.btn:disabled { opacity: 0.6; cursor: not-allowed; }
.muted { color: #999; font-size: 13px; }
.tiny { font-size: 12px; }
.err { color: #f56c6c; font-size: 13px; }
</style>
