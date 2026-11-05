<script setup lang="ts">
/** 全局用户菜单：展示当前账号与身份端，提供退出登录。 */
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import http from '../../api/http'
import { useAuthStore } from '../../stores/auth'

const ROLE_LABELS: Record<string, string> = {
  AM: '客户经理端',
  BO: '业务负责人端',
  DS: '设计师端',
}

const authStore = useAuthStore()
const router = useRouter()
const loggingOut = ref(false)

async function handleLogout() {
  if (loggingOut.value) return
  loggingOut.value = true
  try {
    // 后端会递增 token_version，使当前 refresh token 失效
    await http.post('/auth/logout')
  } catch {
    // 令牌可能已过期或网络异常，本地会话仍需清理
  } finally {
    authStore.clearAuth()
    loggingOut.value = false
    router.push('/login')
  }
}
</script>

<template>
  <div class="user-menu">
    <span class="user-name">{{ authStore.currentUser?.name || authStore.currentUser?.username }}</span>
    <span v-if="authStore.primaryRole" class="role-tag">
      {{ ROLE_LABELS[authStore.primaryRole] }}
    </span>
    <button class="logout-btn" type="button" :disabled="loggingOut" @click="handleLogout">
      {{ loggingOut ? '退出中…' : '退出登录' }}
    </button>
  </div>
</template>

<style scoped>
.user-menu { display: flex; align-items: center; gap: 10px; font-size: 13px; }
.user-name { color: #303133; }
.role-tag {
  padding: 2px 8px;
  border-radius: 10px;
  background: #ecf5ff;
  color: #409eff;
  font-size: 12px;
}
.logout-btn {
  padding: 5px 12px;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  background: #fff;
  color: #606266;
  font-size: 13px;
  cursor: pointer;
}
.logout-btn:hover { border-color: #409eff; color: #409eff; }
.logout-btn:disabled { color: #c0c4cc; cursor: not-allowed; }
</style>
