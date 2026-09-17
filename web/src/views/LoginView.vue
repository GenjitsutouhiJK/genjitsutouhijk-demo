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
      username: username.value,
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
      </div>
    </section>
  </AppShell>
</template>

<style scoped>
/* ---------------- 结果提示（只在出错时出现） ---------------- */
.status {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 2px;
  padding-top: 14px;
  border-top: 1px solid var(--line-soft);
  font-size: 12px;
  letter-spacing: var(--ls-mid);
  color: var(--err);
}

/* 呼应参考稿里的小方块节点 */
.status::before {
  content: '';
  flex: none;
  width: 6px;
  height: 6px;
  background: currentColor;
}
</style>
