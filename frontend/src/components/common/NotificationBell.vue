<script setup lang="ts">
/**
 * P4-T16 · 通知中心（铃铛 + 下拉列表 + SSE 实时推送）
 *
 * EventSource 不能携带 Authorization 头，因此走 fetch ReadableStream（见 phase4.ts）。
 * 连接异常时 5 秒后自动重连；组件卸载时关闭连接与定时器。
 */
import { onMounted, onBeforeUnmount, ref } from 'vue'
import { notificationApi, subscribeNotifications, type NotificationItem } from '../../api/modules/phase4'
import { errorMessage } from '../../api/http'

const unread = ref(0)
const items = ref<NotificationItem[]>([])
const open = ref(false)
const loading = ref(false)
const error = ref('')
const connected = ref(false)

let stopStream: (() => void) | null = null
let retryTimer: ReturnType<typeof setTimeout> | null = null
let disposed = false

function resourceLink(n: NotificationItem) {
  // 仅给出定位信息，避免在无权限页面跳转；页面内可自行复制
  return `${n.resource_type}#${n.resource_id}`
}

async function refresh() {
  loading.value = true
  error.value = ''
  try {
    const [countRes, listRes] = await Promise.all([
      notificationApi.unreadCount(),
      notificationApi.list({ limit: 10 }),
    ])
    unread.value = countRes.data?.unread_count ?? 0
    items.value = listRes.data || []
  } catch (e) {
    error.value = errorMessage(e, '加载通知失败')
  } finally {
    loading.value = false
  }
}

function connect() {
  if (disposed) return
  stopStream = subscribeNotifications(
    (event, payload) => {
      if (event === 'connected') {
        connected.value = true
        return
      }
      if (event === 'notification') {
        const n = payload as NotificationItem
        items.value = [n, ...items.value].slice(0, 10)
        unread.value += 1
      }
    },
    (message) => {
      connected.value = false
      error.value = `实时通知已断开：${message}，5 秒后重试`
      stopStream?.()
      if (retryTimer) clearTimeout(retryTimer)
      retryTimer = setTimeout(connect, 5000)
    }
  )
}

async function toggle() {
  open.value = !open.value
  if (open.value) await refresh()
}

async function markRead(n: NotificationItem) {
  if (n.read) return
  try {
    const res = await notificationApi.markRead(n.id)
    const updated = res.data
    items.value = items.value.map((x) => (x.id === n.id && updated ? updated : x))
    unread.value = Math.max(0, unread.value - 1)
  } catch (e) {
    error.value = errorMessage(e, '标记已读失败')
  }
}

async function markAll() {
  try {
    await notificationApi.markAllRead()
    items.value = items.value.map((x) => ({ ...x, read: true }))
    unread.value = 0
  } catch (e) {
    error.value = errorMessage(e, '全部已读失败')
  }
}

onMounted(() => {
  void refresh()
  connect()
})

onBeforeUnmount(() => {
  disposed = true
  stopStream?.()
  if (retryTimer) clearTimeout(retryTimer)
})
</script>

<template>
  <div class="bell-wrap">
    <button class="bell" :class="{ open }" @click="toggle" title="通知中心">
      <span class="icon">🔔</span>
      <span v-if="unread > 0" class="badge">{{ unread > 99 ? '99+' : unread }}</span>
      <span class="dot" :class="{ online: connected }" :title="connected ? '实时通知已连接' : '实时通知未连接'" />
    </button>

    <div v-if="open" class="panel">
      <header class="panel-head">
        <strong>通知中心</strong>
        <div class="head-actions">
          <button class="link" :disabled="loading || unread === 0" @click="markAll">全部已读</button>
          <button class="link" @click="open = false">关闭</button>
        </div>
      </header>

      <p v-if="error" class="err">{{ error }}</p>
      <p v-if="loading" class="muted">加载中…</p>
      <p v-else-if="items.length === 0" class="muted">暂无通知</p>
      <ul v-else class="list">
        <li v-for="n in items" :key="n.id" class="item" :class="{ unread: !n.read }" @click="markRead(n)">
          <div class="row">
            <span class="title">{{ n.title }}</span>
            <span class="time">{{ n.created_at?.replace('T', ' ').slice(0, 16) }}</span>
          </div>
          <p class="content">{{ n.content }}</p>
          <div class="row meta">
            <span class="tag">{{ n.resource_type }}</span>
            <span class="muted tiny">{{ resourceLink(n) }}</span>
            <span v-if="!n.read" class="new-tag">未读</span>
          </div>
        </li>
      </ul>
    </div>
  </div>
</template>

<style scoped>
.bell-wrap { position: relative; }
.bell {
  position: relative; width: 36px; height: 36px; border-radius: 50%;
  border: 1px solid #dcdfe6; background: #fff; cursor: pointer;
  display: flex; align-items: center; justify-content: center;
}
.bell:hover, .bell.open { border-color: var(--primary); background: var(--primary-soft); }
.icon { font-size: 16px; }
.badge {
  position: absolute; top: -6px; right: -6px; min-width: 16px; height: 16px; line-height: 16px;
  padding: 0 4px; border-radius: 8px; background: #f56c6c; color: #fff; font-size: 11px; text-align: center;
}
.dot { position: absolute; bottom: 0; right: 0; width: 7px; height: 7px; border-radius: 50%; background: #c0c4cc; }
.dot.online { background: #67c23a; }

.panel {
  position: absolute; right: 0; top: 44px; z-index: 20; width: 360px; max-height: 460px;
  display: flex; flex-direction: column; background: #fff; border-radius: 8px;
  box-shadow: 0 4px 18px rgba(0, 0, 0, 0.12); overflow: hidden;
}
.panel-head {
  display: flex; justify-content: space-between; align-items: center;
  padding: 12px 14px; border-bottom: 1px solid #ebeef5; font-size: 14px;
}
.head-actions { display: flex; gap: 10px; }
.link { border: none; background: none; color: var(--primary); cursor: pointer; font-size: 13px; padding: 0; }
.link:disabled { color: #c0c4cc; cursor: not-allowed; }
.list { margin: 0; padding: 0; list-style: none; overflow-y: auto; }
.item { padding: 12px 14px; border-bottom: 1px solid #f2f3f5; cursor: pointer; }
.item:hover { background: #fafbfc; }
.item.unread { background: #f4f9ff; }
.row { display: flex; justify-content: space-between; align-items: center; gap: 8px; }
.title { font-size: 13px; color: #303133; font-weight: 500; }
.time { font-size: 12px; color: #909399; flex-shrink: 0; }
.content { margin: 6px 0; font-size: 12px; color: #606266; }
.meta { justify-content: flex-start; gap: 8px; }
.tag { font-size: 11px; color: var(--primary); background: var(--primary-soft); border-radius: 3px; padding: 1px 6px; }
.new-tag { margin-left: auto; font-size: 11px; color: #f56c6c; }
.tiny { font-size: 11px; }
.muted { color: #999; font-size: 13px; padding: 12px 14px; }
.err { color: #f56c6c; font-size: 12px; padding: 0 14px; }
</style>
