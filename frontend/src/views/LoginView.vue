<script setup lang="ts">
/**
 * P1-T25 · 登录页
 *
 * POST /api/v1/auth/login 走真实后端（USE_MOCK=false 默认）；成功后保存 JWT 与用户角色。
 */
import { ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import { RoleType } from '../types/enums'
import http from '../api/http'

const router = useRouter()
const route = useRoute()
const auth = useAuthStore()

const username = ref((route.query.username as string) || '')
const password = ref('')
const role = ref<RoleType | ''>((route.query.role as RoleType) || '')
const loading = ref(false)
const error = ref('')
const registered = ref(route.query.registered === '1')
const fieldErrors = ref<{ username?: string; password?: string; role?: string }>({})

/** 三端身份选项（登录前先选择身份端）*/
const roleOptions: { value: RoleType; label: string; desc: string }[] = [
  { value: RoleType.AM, label: '客户经理端', desc: '登记订单' },
  { value: RoleType.BO, label: '业务负责人端', desc: '审阅 / 派发' },
  { value: RoleType.DS, label: '设计师端', desc: '接收任务' },
]

function validateForm(): boolean {
  fieldErrors.value = {}
  let ok = true
  if (!role.value) {
    fieldErrors.value.role = '请先选择身份端'
    ok = false
  }
  if (!username.value.trim()) {
    fieldErrors.value.username = '请输入用户名'
    ok = false
  }
  if (!password.value) {
    fieldErrors.value.password = '请输入密码'
    ok = false
  } else if (password.value.length < 4) {
    fieldErrors.value.password = '密码至少 4 位'
    ok = false
  }
  return ok
}

async function handleLogin() {
  error.value = ''
  registered.value = false
  if (!validateForm()) return

  loading.value = true
  try {
    // 通过 http.ts → mock-adapter / 真实后端
    const res = await http.post('/auth/login', {
      username: username.value.trim(),
      password: password.value,
      role: role.value,
    })
    const body = res.data as {
      code?: string
      message?: string
      data?: {
        access_token: string
        refresh_token: string
        user: { id: number; username: string; name: string; roles: RoleType[] }
      }
    }
    if (!body.data?.access_token) {
      error.value = body.message || '登录失败：服务未返回 token'
      return
    }
    const { access_token, refresh_token, user } = body.data
    auth.setAuth(access_token, refresh_token, user)
    const redirect = (route.query.redirect as string) || defaultRedirect(user.roles[0])
    router.push(redirect)
  } catch (e: unknown) {
    // mock-adapter 返回 401，或真实后端返回 401/403
    const anyErr = e as { response?: { status?: number; data?: { message?: string } }; message?: string }
    if (anyErr.response?.status === 401) {
      error.value = anyErr.response.data?.message || '用户名或密码错误'
    } else {
      error.value = anyErr.message || '登录请求失败，请稍后重试'
    }
  } finally {
    loading.value = false
  }
}

function defaultRedirect(role: RoleType | undefined) {
  return role === RoleType.AM
    ? '/am/orders'
    : role === RoleType.BO
    ? '/bo/review'
    : '/ds/tasks'
}
</script>

<template>
  <div class="login-wrapper">
    <div class="login-card">
      <h1>宣传物料订单管控系统</h1>
      <p class="subtitle">请选择身份端后登录</p>
      <form @submit.prevent="handleLogin" novalidate>
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
          <span>用户名</span>
          <input
            v-model="username"
            type="text"
            placeholder="am_demo / bo_demo / ds_demo"
            autocomplete="username"
            :class="{ 'has-error': fieldErrors.username }"
          />
          <em v-if="fieldErrors.username" class="inline-error">{{ fieldErrors.username }}</em>
        </label>
        <label class="field">
          <span>密码</span>
          <input
            v-model="password"
            type="password"
            placeholder="demo123"
            autocomplete="current-password"
            :class="{ 'has-error': fieldErrors.password }"
          />
          <em v-if="fieldErrors.password" class="inline-error">{{ fieldErrors.password }}</em>
        </label>
        <p v-if="registered" class="ok">注册成功，请使用新账号登录。</p>
        <p v-if="error" class="error">{{ error }}</p>
        <button type="submit" :disabled="loading">
          {{ loading ? '登录中…' : '登录' }}
        </button>
      </form>
      <p class="register-link">还没有账号？<router-link to="/register">注册新账号</router-link></p>
      <div class="hint">
        <p>演示账号（密码均为 demo123，需选择对应身份端）：</p>
        <ul>
          <li>客户经理端 <code>am_demo</code></li>
          <li>业务负责人端 <code>bo_demo</code></li>
          <li>设计师端 <code>ds_demo</code></li>
        </ul>
      </div>
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
button {
  width: 100%;
  padding: 10px;
  background: var(--primary);
  color: #fff;
  border: none;
  border-radius: 4px;
  font-size: 14px;
  cursor: pointer;
}
button:disabled { background: var(--primary-border); cursor: not-allowed; }
.error { color: #f56c6c; font-size: 13px; margin: 0 0 12px; padding: 6px 10px; background: #fef0f0; border-radius: 4px; }
.ok { color: #67c23a; font-size: 13px; margin: 0 0 12px; padding: 6px 10px; background: #f0f9eb; border-radius: 4px; }
.register-link { font-size: 13px; color: #606266; text-align: center; margin: 14px 0 0; }
.register-link a { color: var(--primary); text-decoration: none; }
.hint { color: #999; font-size: 12px; margin-top: 18px; }
.hint ul { margin: 6px 0 0 18px; padding: 0; }
.hint li { margin: 2px 0; }
.hint code { background: #f0f2f5; padding: 1px 4px; border-radius: 3px; font-size: 12px; }
</style>
