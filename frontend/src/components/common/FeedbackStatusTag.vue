/**
 * 客户反馈状态标签 · 通用组件（P3-T12）
 * EXPIRED 仅由系统在“同订单更高版本成为终稿”时置位，不可手动解决。
 */
<script setup lang="ts">
import { computed } from 'vue'
import { FeedbackStatus } from '../../types/enums'

const props = defineProps<{ status: string }>()

const labelMap: Record<string, string> = {
  [FeedbackStatus.UNRESOLVED]: '未解决',
  [FeedbackStatus.RESOLVED]: '已解决',
  [FeedbackStatus.EXPIRED]: '已过期',
}

const colorMap: Record<string, string> = {
  [FeedbackStatus.UNRESOLVED]: '#e6a23c',
  [FeedbackStatus.RESOLVED]: '#67c23a',
  [FeedbackStatus.EXPIRED]: '#909399',
}

const label = computed(() => labelMap[props.status] || props.status)
const color = computed(() => colorMap[props.status] || '#909399')
</script>

<template>
  <span class="feedback-status-tag" :style="{ background: color + '20', color }">{{ label }}</span>
</template>

<style scoped>
.feedback-status-tag {
  display: inline-block;
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 12px;
  font-weight: 500;
  white-space: nowrap;
}
</style>
