/**
 * 订单状态标签 · 通用组件
 */
<script setup lang="ts">
import { computed } from 'vue'
import { OrderStatus } from '../../types/enums'

const props = defineProps<{ status: string }>()

const labelMap: Record<string, string> = {
  [OrderStatus.PENDING_CONFIRM]: '待审阅',
  [OrderStatus.IN_PRODUCTION]: '生产中',
  [OrderStatus.COMPLETED]: '已完成',
  [OrderStatus.CANCELLED]: '已取消',
}

const colorMap: Record<string, string> = {
  [OrderStatus.PENDING_CONFIRM]: '#e6a23c',
  [OrderStatus.IN_PRODUCTION]: '#409eff',
  [OrderStatus.COMPLETED]: '#67c23a',
  [OrderStatus.CANCELLED]: '#909399',
}

const label = computed(() => labelMap[props.status] || props.status)
const color = computed(() => colorMap[props.status] || '#909399')
</script>

<template>
  <span class="order-status-tag" :style="{ background: color + '20', color }">{{ label }}</span>
</template>

<style scoped>
.order-status-tag {
  display: inline-block;
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 12px;
  font-weight: 500;
}
</style>
