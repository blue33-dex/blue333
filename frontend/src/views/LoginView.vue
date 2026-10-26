<script setup lang="ts">
/**
 * P1-T25 · 登录页
 *
 * TODO(P1-T25) 后端就绪后：
 *   - 保持 http.post('/auth/login') 不变（USE_MOCK=false 时自动打真实后端）
 *   - 将 setAuth 的 mock token 解析改为直接用后端返回的 JWT
 */
import { ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import { RoleType } from '../types/enums'
import http from '../api/http'

const router = useRouter()
const route = useRoute()
const auth = useAuthStore()

const username = ref('')
const password = ref('')
const loading = ref(false)
const error = ref('')
const fieldErrors = ref<{ username?: string; password?: string }>({})

function validateForm(): boolean {
  fieldErrors.value = {}
  let ok = true
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
  if (!validateForm()) return

  loading.value = true
  try {
    // 通过 http.ts → mock-adapter / 真实后端
    const res = await http.post('/auth/login', {
      username: username.value.trim(),
      password: password.value,
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
      const msg = anyErr.response.data?.message || '用户名或密码错误'
      error.value = msg + '。演示账号：am_demo / bo_demo / ds_demo（密码均为 demo123）。'
    } else {
      error.value = anyErr.message || '登录请求失败，请稍后重试'
    }
  } finally {
    loading.value = false
  }
}

function defaultRedirect(role: RoleType | undefined) {
  return role === RoleType.AM
    ? '/am/orders/create'
    : role === RoleType.BO
    ? '/bo/review'
    : '/ds/tasks'
}
</script>

<template>
  <div class="login-wrapper">
    <div class="login-card">
      <h1>宣传物料订单管控系统</h1>
      <p class="subtitle">登录</p>
      <form @submit.prevent="handleLogin" novalidate>
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
        <p v-if="error" class="error">{{ error }}</p>
        <button type="submit" :disabled="loading">
          {{ loading ? '登录中…' : '登录' }}
        </button>
      </form>
      <div class="hint">
        <p>Phase 1 演示账号（密码均为 demo123）：</p>
        <ul>
          <li><code>am_demo</code>（AM · 登记订单）</li>
          <li><code>bo_demo</code>（BO · 审阅/退回/派发）</li>
          <li><code>ds_demo</code>（DS · 查看任务）</li>
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
.subtitle { color: #666; margin-bottom: 24px; }
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
  background: #409eff;
  color: #fff;
  border: none;
  border-radius: 4px;
  font-size: 14px;
  cursor: pointer;
}
button:disabled { background: #a0cfff; cursor: not-allowed; }
.error { color: #f56c6c; font-size: 13px; margin: 0 0 12px; padding: 6px 10px; background: #fef0f0; border-radius: 4px; }
.hint { color: #999; font-size: 12px; margin-top: 18px; }
.hint ul { margin: 6px 0 0 18px; padding: 0; }
.hint li { margin: 2px 0; }
.hint code { background: #f0f2f5; padding: 1px 4px; border-radius: 3px; font-size: 12px; }
</style>
