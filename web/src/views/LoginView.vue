<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import AppShell from '../components/AppShell.vue'
import PasswordInput from '../components/PasswordInput.vue'
import { login } from '../api/auth'
import { setSession } from '../utils/session'

const router = useRouter()

const username = ref('')
const password = ref('')
const errorMessage = ref('') // 错误提示，空字符串表示没有错误
const isLoading = ref(false) // 是否正在登录

async function handleLogin() {
  if (isLoading.value) return // 正在登录时忽略重复点击

  isLoading.value = true // 开始登录，按钮进入禁用态
  errorMessage.value = ''

  try {
    const json = await login({
      // 用户名去掉首尾空格后再发。复制粘贴用户名时很容易带进空格，
      // 而在注册页那边已经统一做了 trim —— 两处行为必须一致，
      // 否则会出现"注册时能用、登录时说账号不存在"这种很难查的问题。
      // 密码不能 trim：空格是密码的合法字符。
      username: username.value.trim(),
      password: password.value,
    })

    if (json.code === 0) {
      // 成功：先把后端返回的会话信息存起来（主页要用），再跳到主页。
      // 注意这里不需要再把"登录成功"显示出来，因为页面马上就切走了。
      setSession(json.data)
      router.push({ name: 'home' })
    } else {
      errorMessage.value = json.message
    }
  } catch (error) {
    errorMessage.value = '网络错误，请稍后重试'
  } finally {
    isLoading.value = false // 无论成功失败，结束登录态
  }
}
</script>

<template>
  <!-- AppShell 负责页面外壳（背景、水印、准星、顶栏、底栏），
       它标签之间的内容会填进中间的主区域 -->
  <AppShell>
    <div class="stage-head">
      <span class="field-code">00 /</span>
      <span class="stage-title">Access Terminal</span>
      <span class="field-line"></span>
    </div>

    <section class="panel">
      <div class="panel-bar">Authentication</div>

      <div class="panel-body">
        <div class="field">
          <div class="field-head">
            <span class="field-code">01 /</span>
            <span class="field-label">用户名</span>
            <span class="field-line"></span>
          </div>
          <input v-model="username" placeholder="请输入用户名" />
        </div>

        <PasswordInput
          v-model="password"
          code="02"
          placeholder="请输入密码"
          @keyup.enter="handleLogin"
        />

        <button class="btn btn--primary" :disabled="isLoading" @click="handleLogin">
          {{ isLoading ? '登录中…' : '登 录' }}
        </button>

        <p v-if="errorMessage" class="status">{{ errorMessage }}</p>

        <p class="form-switch">
          <span>还没有账号？</span>
          <!-- RouterLink 会渲染成一个 <a>，但点击时走前端路由、不刷新整页 -->
          <RouterLink class="form-switch-link" :to="{ name: 'register' }">
            创建账号
          </RouterLink>
        </p>
      </div>
    </section>
  </AppShell>
</template>
