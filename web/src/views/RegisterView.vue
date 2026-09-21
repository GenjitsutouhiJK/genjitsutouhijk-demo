<script setup>
/**
 * 注册页
 *
 * 结构上跟登录页是"孪生兄弟"：同样用 AppShell 套壳、同样一个 .panel、
 * 同样把结果提示放在按钮下面。差别只在字段多了两个、以及多了一段本地校验。
 *
 * 为什么不把登录和注册合成一个页面（加个 Tab 切换）？
 *   因为两者的"提交后去哪"完全不同：登录是"回来"，注册是"新建一个身份"。
 *   分成两个页面，各自的逻辑一眼能看完；等以后要加"找回密码"，
 *   再把三者抽成一个共用表单组件也不迟 —— 现在抽，反而要多传一堆参数。
 */
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import AppShell from '../components/AppShell.vue'
import PasswordInput from '../components/PasswordInput.vue'
import { register } from '../api/auth'
import { setSession } from '../utils/session'

const router = useRouter()

const username = ref('')
const password = ref('')
const confirmPassword = ref('') // 只在前端用，不会发给后端
const errorMessage = ref('') // 错误提示，空字符串表示没有错误
const isLoading = ref(false) // 是否正在提交

/** 跟后端 RegisterRequest 上那两行注解保持一致的规则 */
const USERNAME_MIN = 3
const USERNAME_MAX = 20
const PASSWORD_MIN = 6
const PASSWORD_MAX = 32

/**
 * 提交前的本地校验
 *
 * 返回空字符串表示"没问题，可以发请求"，否则返回要显示给用户的提示。
 *
 * 为什么要先在本地校验一遍？后端明明已经校验了。
 *   因为后端校验的代价是"一个来回的网络请求"。用户把密码少打一位，
 *   要等几百毫秒才被告知，体验很差。本地先看一眼，能挡下绝大多数手误。
 *
 * 但本地校验**永远不能替代后端校验** —— 请求可以绕过前端直接发给后端
 * （用 curl、Postman 都行），所以后端那份才是真正的防线。
 * 两边的规则要一起改，改一处忘一处是最常见的 bug 来源。
 *
 * @returns {string} 错误提示，空串表示通过
 */
function validate() {
  // 用户名前后可能有空格（复制粘贴时很常见），存进库之前先去掉。
  // 注意：只对用户名这样做。密码里的空格是有效字符，绝不能 trim，
  // 否则用户设的 "abc 123" 会变成 "abc 123"->"abc 123" 校验通过但登录时对不上。
  const name = username.value.trim()

  if (!name) return '用户名不能为空'
  if (name.length < USERNAME_MIN || name.length > USERNAME_MAX) {
    return `用户名长度需要在 ${USERNAME_MIN} 到 ${USERNAME_MAX} 个字符之间`
  }

  if (!password.value) return '密码不能为空'
  if (password.value.length < PASSWORD_MIN || password.value.length > PASSWORD_MAX) {
    return `密码长度需要在 ${PASSWORD_MIN} 到 ${PASSWORD_MAX} 个字符之间`
  }

  // "确认密码"是纯前端的概念：后端只需要一个密码字段，
  // 它根本不知道用户输了两次。这条规则只有这里能查。
  if (password.value !== confirmPassword.value) {
    return '两次输入的密码不一致'
  }

  return ''
}

async function handleRegister() {
  if (isLoading.value) return // 正在提交时忽略重复点击

  const localError = validate()
  if (localError) {
    errorMessage.value = localError
    return // 本地就没过，不发请求，省一个来回
  }

  isLoading.value = true // 开始提交，按钮进入禁用态
  errorMessage.value = ''

  try {
    const json = await register({
      username: username.value.trim(),
      password: password.value,
    })

    if (json.code === 0) {
      // 后端的注册接口是"注册即登录"，返回的 data 和登录一模一样，
      // 所以这里能直接复用登录那套存会话的代码，不需要再调一次登录接口。
      setSession(json.data)
      router.push({ name: 'home' })
    } else {
      // 后端能给出的错误，主要是 1004 用户名已被占用。
      // 这类"要看数据库才知道"的判断前端做不了，只能等后端回话。
      errorMessage.value = json.message
    }
  } catch {
    errorMessage.value = '网络错误，请稍后重试'
  } finally {
    isLoading.value = false // 无论成功失败，结束提交态
  }
}
</script>

<template>
  <!-- AppShell 负责页面外壳（背景、水印、准星、顶栏、底栏），
       它标签之间的内容会填进中间的主区域 -->
  <AppShell>
    <div class="stage-head">
      <span class="field-code">00 /</span>
      <span class="stage-title">Create Account</span>
      <span class="field-line"></span>
    </div>

    <section class="panel">
      <div class="panel-bar">Registration</div>

      <div class="panel-body">
        <div class="field">
          <div class="field-head">
            <span class="field-code">01 /</span>
            <span class="field-label">用户名</span>
            <span class="field-line"></span>
          </div>
          <input
            v-model="username"
            :placeholder="`${USERNAME_MIN} - ${USERNAME_MAX} 个字符`"
          />
        </div>

        <PasswordInput
          v-model="password"
          code="02"
          label="密码"
          :placeholder="`${PASSWORD_MIN} - ${PASSWORD_MAX} 个字符`"
        />

        <!-- 确认密码：防止用户把密码敲错却浑然不觉（密码框看不见内容，这类手误特别常见） -->
        <PasswordInput
          v-model="confirmPassword"
          code="03"
          label="确认密码"
          placeholder="请再输入一次密码"
          @keyup.enter="handleRegister"
        />

        <button class="btn btn--primary" :disabled="isLoading" @click="handleRegister">
          {{ isLoading ? '注册中…' : '注 册' }}
        </button>

        <p v-if="errorMessage" class="status">{{ errorMessage }}</p>

        <p class="form-switch">
          <span>已经有账号了？</span>
          <!-- RouterLink 会渲染成一个 <a>，但点击时走前端路由、不刷新整页。
               路由名 'login' 对应 router/index.js 里那条 /login。 -->
          <RouterLink class="form-switch-link" :to="{ name: 'login' }">
            返回登录
          </RouterLink>
        </p>
      </div>
    </section>
  </AppShell>
</template>
