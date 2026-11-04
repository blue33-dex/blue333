<script setup lang="ts">
/**
 * P3-T12 · DS 客户反馈视图
 *
 * - 按版本聚合展示客户反馈（A-27），反馈条目绑定具体版本编号（BR-09）
 * - 未解决反馈可标记已解决（A-15）
 * - EXPIRED 反馈只读：同订单更高版本成为终稿后由系统自动置位，设计师不能再解决
 */
import { computed, ref, watch } from 'vue'
import { clientReviewApi, type FeedbackResponse } from '../../api/modules/clientReview'
import { errorMessage } from '../../api/http'
import type { VersionItem } from '../../api/modules/designVersion'
import FeedbackStatusTag from '../common/FeedbackStatusTag.vue'
import { FeedbackSource, FeedbackStatus, FeedbackType, VersionStatus } from '../../types/enums'

const props = defineProps<{ taskId: number; versions: VersionItem[] }>()
const emit = defineEmits<{ (event: 'resolved'): void }>()

const feedbacks = ref<Record<number, FeedbackResponse[]>>({})
const loading = ref(false)
const error = ref('')
const actingId = ref<number | null>(null)

/** 只有进入/结束客户评审的版本才可能存在反馈 */
const reviewedVersions = computed(() =>
  props.versions
    .filter((version) => version.task_id === props.taskId)
    .filter((version) =>
      [
        VersionStatus.IN_CLIENT_REVIEW,
        VersionStatus.CLIENT_CONFIRMED,
        VersionStatus.FINAL,
      ].includes(version.status as VersionStatus)
    )
    .sort((a, b) => b.id - a.id)
)

const versionKey = computed(() =>
  reviewedVersions.value.map((version) => `${version.id}:${version.status}`).join('|')
)

const totalCount = computed(() =>
  reviewedVersions.value.reduce((sum, version) => sum + (feedbacks.value[version.id]?.length || 0), 0)
)

const openCount = computed(() =>
  reviewedVersions.value.reduce(
    (sum, version) =>
      sum + (feedbacks.value[version.id] || []).filter((item) => item.status === FeedbackStatus.UNRESOLVED).length,
    0
  )
)

function feedbackFor(version: VersionItem): FeedbackResponse[] {
  return feedbacks.value[version.id] || []
}

const typeLabels: Record<string, string> = {
  [FeedbackType.MODIFICATION_SUGGESTION]: '修改建议',
  [FeedbackType.REJECTION]: '退回',
  [FeedbackType.APPROVAL]: '认可',
}

const sourceLabels: Record<string, string> = {
  [FeedbackSource.CLIENT]: '客户反馈',
  [FeedbackSource.BUSINESS_OWNER]: '业务负责人',
}

function typeLabel(value: string): string {
  return typeLabels[value] || value
}

function sourceLabel(value: string): string {
  return sourceLabels[value] || value
}

async function load() {
  if (!reviewedVersions.value.length) {
    feedbacks.value = {}
    return
  }
  loading.value = true
  error.value = ''
  try {
    const results = await Promise.all(
      reviewedVersions.value.map(async (version) => {
        const res = await clientReviewApi.listFeedbacks(version.id)
        return { versionId: version.id, items: (res.data || []) as FeedbackResponse[] }
      })
    )
    const map: Record<number, FeedbackResponse[]> = {}
    for (const result of results) map[result.versionId] = result.items
    feedbacks.value = map
  } catch (e) {
    error.value = errorMessage(e, '加载客户反馈失败')
  } finally {
    loading.value = false
  }
}

async function resolve(item: FeedbackResponse) {
  if (!window.confirm(`确认反馈 ${item.feedback_no} 已处理完毕？标记后不可撤销。`)) return
  actingId.value = item.id
  error.value = ''
  try {
    await clientReviewApi.resolveFeedback(item.id)
    await load()
    emit('resolved')
  } catch (e) {
    error.value = errorMessage(e, '标记已解决失败')
  } finally {
    actingId.value = null
  }
}

watch(versionKey, load, { immediate: true })
</script>

<template>
  <div class="feedback-panel">
    <div class="panel-head">
      <h2>客户反馈</h2>
      <span class="summary">
        共 {{ totalCount }} 条
        <span v-if="openCount" class="open-flag">未解决 {{ openCount }}</span>
      </span>
    </div>

    <p v-if="error" class="err">{{ error }}</p>
    <p v-if="loading" class="muted">反馈加载中…</p>
    <p v-else-if="!totalCount" class="muted">
      暂无客户反馈。版本进入客户评审后，业务负责人记录的反馈会显示在这里。
    </p>

    <template v-else>
      <section v-for="version in reviewedVersions" :key="version.id" class="version-block">
        <div v-if="feedbackFor(version).length" class="version-head">
          <span class="version-no">{{ version.version_no }}</span>
          <span class="muted">{{ feedbackFor(version).length }} 条</span>
        </div>
        <table v-if="feedbackFor(version).length" class="feedback-table">
          <thead>
            <tr>
              <th style="width: 90px">编号</th>
              <th style="width: 100px">类型</th>
              <th style="width: 100px">来源</th>
              <th>内容</th>
              <th style="width: 80px">状态</th>
              <th style="width: 150px">记录</th>
              <th style="width: 110px">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="item in feedbackFor(version)" :key="item.id">
              <td class="mono">{{ item.feedback_no }}</td>
              <td>{{ typeLabel(item.feedback_type) }}</td>
              <td>{{ sourceLabel(item.source) }}</td>
              <td class="content">{{ item.content }}</td>
              <td><FeedbackStatusTag :status="item.status" /></td>
              <td class="muted">{{ item.created_by_name }}<br />{{ item.created_at }}</td>
              <td class="ops">
                <button
                  v-if="item.status === FeedbackStatus.UNRESOLVED"
                  class="btn small primary"
                  :disabled="actingId === item.id"
                  @click="resolve(item)"
                >{{ actingId === item.id ? '处理中…' : '标记已解决' }}</button>
                <span v-else-if="item.status === FeedbackStatus.EXPIRED" class="muted" title="更高版本已成为终稿，该反馈自动过期">
                  已过期·只读
                </span>
                <span v-else class="muted">已处理</span>
              </td>
            </tr>
          </tbody>
        </table>
      </section>
    </template>
  </div>
</template>

<style scoped>
.feedback-panel { margin-top: 4px; }
.panel-head { display: flex; justify-content: space-between; align-items: baseline; margin-bottom: 10px; }
.panel-head h2 { margin: 0; font-size: 15px; padding-left: 8px; border-left: 3px solid #e6a23c; }
.summary { font-size: 13px; color: #909399; }
.open-flag {
  margin-left: 8px; padding: 1px 6px; font-size: 12px; color: #e6a23c;
  background: #fdf6ec; border: 1px solid #f5dab1; border-radius: 3px;
}
.version-block { margin-bottom: 12px; }
.version-head { display: flex; align-items: baseline; gap: 8px; margin-bottom: 4px; }
.version-no { font-family: Menlo, Consolas, monospace; font-size: 13px; color: #409eff; }
.feedback-table { width: 100%; border-collapse: collapse; font-size: 13px; }
.feedback-table th, .feedback-table td {
  text-align: left; padding: 8px 10px; border-bottom: 1px solid #ebeef5; vertical-align: top;
}
.feedback-table th { color: #909399; font-weight: 500; background: #fafafa; }
.content { max-width: 300px; word-break: break-word; color: #303133; }
.mono { font-family: Menlo, Consolas, monospace; color: #409eff; }
.ops { white-space: nowrap; }
.btn { padding: 8px 16px; border-radius: 4px; border: 1px solid #dcdfe6; background: #fff; cursor: pointer; font-size: 14px; }
.btn.small { padding: 5px 10px; font-size: 12px; }
.btn.primary { background: #409eff; border-color: #409eff; color: #fff; }
.btn:disabled { opacity: 0.6; cursor: not-allowed; }
.err { color: #f56c6c; background: #fef0f0; border-radius: 4px; padding: 8px 10px; font-size: 13px; }
.muted { color: #999; font-size: 13px; }
</style>
