<script setup lang="ts">
import { computed } from 'vue'
import { RouterView } from 'vue-router'
import UserMenu from './components/common/UserMenu.vue'
import { useAuthStore } from './stores/auth'

const authStore = useAuthStore()
// 登录页 / 注册页不显示全局用户菜单
const showUserMenu = computed(() => authStore.isLoggedIn && !!authStore.primaryRole)
</script>

<template>
  <div id="app">
    <header v-if="showUserMenu" class="app-bar">
      <span class="brand">宣传物料订单管控系统</span>
      <UserMenu />
    </header>
    <RouterView />
  </div>
</template>

<style>
#app {
  min-height: 100vh;
  font-family: system-ui, -apple-system, 'Segoe UI', Roboto, sans-serif;
}
.app-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 24px;
  background: #fff;
  border-bottom: 1px solid #ebeef5;
}
.app-bar .brand { font-size: 14px; font-weight: 600; color: #303133; }
</style>
