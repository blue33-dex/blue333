/**
 * 设计版本状态标签 · 通用组件（P2-T14）
 */
<script setup lang="ts">
import { computed } from 'vue'
import { VersionStatus } from '../../types/enums'

const props = defineProps<{ status: string }>()

const labelMap: Record<string, string> = {
  [VersionStatus.DRAFT]: '草稿',
  [VersionStatus.PENDING_INTERNAL_REVIEW]: '待内部评审',
  [VersionStatus.RELEASED]: '已放行',
  [VersionStatus.IN_CLIENT_REVIEW]: '客户评审中',
  [VersionStatus.CLIENT_CONFIRMED]: '客户已确认',
  [VersionStatus.FINAL]: '终稿',
}

const colorMap: Record<string, string> = {
  [VersionStatus.DRAFT]: '#909399',
  [VersionStatus.PENDING_INTERNAL_REVIEW]: '#409eff',
  [VersionStatus.RELEASED]: '#67c23a',
  [VersionStatus.IN_CLIENT_REVIEW]: '#e6a23c',
  [VersionStatus.CLIENT_CONFIRMED]: '#67c23a',
  [VersionStatus.FINAL]: '#303133',
}

const label = computed(() => labelMap[props.status] || props.status)
const color = computed(() => colorMap[props.status] || '#909399')
</script>

<template>
  <span class="version-status-tag" :style="{ background: color + '20', color }">{{ label }}</span>
</template>

<style scoped>
.version-status-tag {
  display: inline-block;
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 12px;
  font-weight: 500;
  white-space: nowrap;
}
</style>
