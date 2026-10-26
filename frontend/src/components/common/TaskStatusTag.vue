/**
 * 物料任务状态标签 · 通用组件
 */
<script setup lang="ts">
import { computed } from 'vue'
import { MaterialTaskStatus } from '../../types/enums'

const props = defineProps<{ status: string }>()

const labelMap: Record<string, string> = {
  [MaterialTaskStatus.PENDING_DESIGN]: '待设计',
  [MaterialTaskStatus.PENDING_APPROVAL]: '待评审',
  [MaterialTaskStatus.PENDING_REVISE]: '待修改',
  [MaterialTaskStatus.APPROVED]: '已通过',
  [MaterialTaskStatus.CANCELLED]: '已取消',
}

const colorMap: Record<string, string> = {
  [MaterialTaskStatus.PENDING_DESIGN]: '#909399',
  [MaterialTaskStatus.PENDING_APPROVAL]: '#409eff',
  [MaterialTaskStatus.PENDING_REVISE]: '#e6a23c',
  [MaterialTaskStatus.APPROVED]: '#67c23a',
  [MaterialTaskStatus.CANCELLED]: '#909399',
}

const label = computed(() => labelMap[props.status] || props.status)
const color = computed(() => colorMap[props.status] || '#909399')
</script>

<template>
  <span class="task-status-tag" :style="{ background: color + '20', color }">{{ label }}</span>
</template>

<style scoped>
.task-status-tag {
  display: inline-block;
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 12px;
  font-weight: 500;
}
</style>
