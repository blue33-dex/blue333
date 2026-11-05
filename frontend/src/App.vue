<script setup lang="ts">
import { computed } from 'vue'
import { RouterView } from 'vue-router'
import UserMenu from './components/common/UserMenu.vue'
import { useAuthStore } from './stores/auth'
import { RoleType } from './types/enums'

const authStore = useAuthStore()

// 登录页 / 注册页不显示全局用户菜单
const showUserMenu = computed(() => authStore.isLoggedIn && !!authStore.primaryRole)

/** 三端主题：AM 暖橙、BO 沉稳蓝、DS 创意紫 */
const themeClass = computed(() => {
  switch (authStore.primaryRole) {
    case RoleType.AM:
      return 'theme-am'
    case RoleType.BO:
      return 'theme-bo'
    case RoleType.DS:
      return 'theme-ds'
    default:
      return ''
  }
})

const endLabel = computed(() => {
  switch (authStore.primaryRole) {
    case RoleType.AM:
      return '客户经理端'
    case RoleType.BO:
      return '业务负责人端'
    case RoleType.DS:
      return '设计师端'
    default:
      return ''
  }
})
</script>

<template>
  <div id="app" :class="themeClass">
    <header v-if="showUserMenu" class="app-bar">
      <div class="brand-group">
        <span class="brand-mark" />
        <span class="brand">宣传物料订单管控系统</span>
        <span class="end-badge">{{ endLabel }}</span>
      </div>
      <UserMenu />
    </header>
    <RouterView />
  </div>
</template>

<style>
#app {
  min-height: 100vh;
  font-family: system-ui, -apple-system, 'Segoe UI', Roboto, 'PingFang SC', 'Microsoft YaHei', sans-serif;
  background: var(--bg);
  color: var(--text);
  transition: background 0.25s ease;
}
.app-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 10px 24px;
  background: var(--surface);
  border-bottom: 2px solid var(--primary);
}
.brand-group { display: flex; align-items: center; gap: 10px; }
.brand-mark { width: 4px; height: 18px; border-radius: 2px; background: var(--primary); }
.brand { font-size: 14px; font-weight: 600; color: var(--text); }
.end-badge {
  padding: 2px 10px;
  border-radius: 999px;
  border: 1px solid var(--primary-border);
  background: var(--primary-soft);
  color: var(--primary);
  font-size: 12px;
}
</style>
