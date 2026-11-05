<script setup lang="ts">
/**
 * 注册页 · 三端分离账号注册
 *
 * POST /api/v1/auth/register：Mock 模式下持久化到 localStorage，真实后端由 AuthService.register 落库。
 */
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { RoleType } from '../types/enums'
import http from '../api/http'

const router = useRouter()

const name = ref('')
const username = ref('')
const password = ref('')
const confirmPassword = ref('')
const role = ref<RoleType | ''>('')
const loading = ref(false)
const error = ref('')
const fieldErrors = ref<{ name?: string; username?: string; password?: string; confirm?: string; role?: string }>({})

const roleOptions: { value: RoleType; label: string; desc: string }[] = [
  { value: RoleType.AM, label: '客户经理端', desc: '登记订单' },
  { value: RoleType.BO, label: '业务负责人端', desc: '审阅 / 派发' },
  { value: RoleType.DS, label: '设计师端', desc: '接收任务' },
]

function validateForm(): boolean {
  fieldErrors.value = {}
  let ok = true
  if (!role.value) {
    fieldErrors.value.role = '请选择要注册的身份端'
    ok = false
  }
  if (!name.value.trim()) {
    fieldErrors.value.name = '请输入姓名'
    ok = false
  }
  if (!username.value.trim()) {
    fieldErrors.value.username = '请输入用户名'
    ok = false
  } else if (!/^[a-zA-Z0-9_]{3,20}$/.test(username.value.trim())) {
    fieldErrors.value.username = '用户名需为 3~20 位字母、数字或下划线'
    ok = false
  }
  if (!password.value) {
    fieldErrors.value.password = '请输入密码'
    ok = false
  } else if (password.value.length < 4) {
    fieldErrors.value.password = '密码至少 4 位'
    ok = false
  }
  if (confirmPassword.value !== password.value) {
    fieldErrors.value.confirm = '两次输入的密码不一致'
    ok = false
  }
  return ok
}

async function handleRegister() {
  error.value = ''
  if (!validateForm()) return

  loading.value = true
  try {
    await http.post('/auth/register', {
      name: name.value.trim(),
      username: username.value.trim(),
      password: password.value,
      role: role.value,
    })
    // 注册成功 → 回登录页并预填用户名与身份端
    router.push({ path: '/login', query: { registered: '1', username: username.value.trim(), role: role.value } })
  } catch (e: unknown) {
    const anyErr = e as { response?: { status?: number; data?: { message?: string } }; message?: string }
    error.value = anyErr.response?.data?.message || anyErr.message || '注册失败，请稍后重试'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="login-wrapper">
    <div class="login-card">
      <h1>宣传物料订单管控系统</h1>
      <p class="subtitle">注册新账号</p>
      <form @submit.prevent="handleRegister" novalidate>
        <div class="role-picker" :class="{ 'has-error': fieldErrors.role }">
          <button
            v-for="opt in roleOptions"
            :key="opt.value"
            type="button"
            class="role-card"
            :class="{ active: role === opt.value }"
            @click="role = opt.value"
          >
            <span class="role-label">{{ opt.label }}</span>
            <span class="role-desc">{{ opt.desc }}</span>
          </button>
        </div>
        <em v-if="fieldErrors.role" class="inline-error">{{ fieldErrors.role }}</em>
        <label class="field">
          <span>姓名</span>
          <input v-model="name" type="text" placeholder="真实姓名，如：张晓明" :class="{ 'has-error': fieldErrors.name }" />
          <em v-if="fieldErrors.name" class="inline-error">{{ fieldErrors.name }}</em>
        </label>
        <label class="field">
          <span>用户名</span>
          <input v-model="username" type="text" placeholder="3~20 位字母、数字或下划线" autocomplete="username" :class="{ 'has-error': fieldErrors.username }" />
          <em v-if="fieldErrors.username" class="inline-error">{{ fieldErrors.username }}</em>
        </label>
        <label class="field">
          <span>密码</span>
          <input v-model="password" type="password" placeholder="至少 4 位" autocomplete="new-password" :class="{ 'has-error': fieldErrors.password }" />
          <em v-if="fieldErrors.password" class="inline-error">{{ fieldErrors.password }}</em>
        </label>
        <label class="field">
          <span>确认密码</span>
          <input v-model="confirmPassword" type="password" placeholder="再次输入密码" autocomplete="new-password" :class="{ 'has-error': fieldErrors.confirm }" />
          <em v-if="fieldErrors.confirm" class="inline-error">{{ fieldErrors.confirm }}</em>
        </label>
        <p v-if="error" class="error">{{ error }}</p>
        <button type="submit" :disabled="loading">
          {{ loading ? '注册中…' : '注册' }}
        </button>
      </form>
      <p class="register-link">已有账号？<router-link to="/login">返回登录</router-link></p>
    </div>
  </div>
</template>

<style scoped>
.login-wrapper {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 100vh;
  background: #f5f7fa;
}
.login-card {
  background: #fff;
  padding: 40px;
  border-radius: 8px;
  width: 380px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.08);
}
.login-card h1 { font-size: 18px; margin-bottom: 4px; }
.subtitle { color: #666; margin-bottom: 18px; }
.role-picker { display: grid; grid-template-columns: repeat(3, 1fr); gap: 8px; margin-bottom: 4px; }
.role-card {
  padding: 10px 4px;
  background: #fff;
  border: 1px solid #dcdfe6;
  border-radius: 6px;
  cursor: pointer;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
  font-size: 13px;
  color: #333;
}
.role-card .role-desc { font-size: 11px; color: #999; }
.role-card.active { border-color: var(--primary); background: var(--primary-soft); color: var(--primary); }
.role-card.active .role-desc { color: var(--primary); }
.role-picker.has-error .role-card { border-color: #f56c6c; }
.field { display: block; margin-bottom: 16px; font-size: 13px; color: #333; }
.field span { display: block; margin-bottom: 6px; }
input {
  width: 100%;
  padding: 8px 12px;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  box-sizing: border-box;
  font-size: 14px;
}
input.has-error { border-color: #f56c6c; }
.inline-error { color: #f56c6c; font-size: 12px; margin: 4px 0 0; font-style: normal; display: block; }
button[type='submit'] {
  width: 100%;
  padding: 10px;
  background: var(--primary);
  color: #fff;
  border: none;
  border-radius: 4px;
  font-size: 14px;
  cursor: pointer;
}
button[type='submit']:disabled { background: var(--primary-border); cursor: not-allowed; }
.error { color: #f56c6c; font-size: 13px; margin: 0 0 12px; padding: 6px 10px; background: #fef0f0; border-radius: 4px; }
.register-link { font-size: 13px; color: #606266; text-align: center; margin: 14px 0 0; }
.register-link a { color: var(--primary); text-decoration: none; }
</style>
