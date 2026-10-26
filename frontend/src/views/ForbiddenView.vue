<script setup lang="ts">
import { useRouter } from 'vue-router'
const router = useRouter()
</script>

<template>
  <div class="forbidden">
    <h1>403</h1>
    <p>你没有权限访问此页面</p>
    <button @click="router.push('/login')">返回登录</button>
  </div>
</template>

<style scoped>
.forbidden { text-align: center; padding: 120px 20px; }
.forbidden h1 { font-size: 72px; color: #f56c6c; margin-bottom: 12px; }
.forbidden p { color: #666; margin-bottom: 24px; }
button { padding: 10px 24px; background: #409eff; color: #fff; border: none; border-radius: 4px; cursor: pointer; }
</style>
