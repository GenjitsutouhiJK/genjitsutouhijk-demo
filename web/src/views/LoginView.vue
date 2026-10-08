<script setup lang="ts">
/**
 * 登录页。
 *
 * ★ 这一页的模板根是 <AppShell>，而它外面**必须是单根** ——
 *   所以说明写在这里，不写在 <template> 的顶层。
 *
 *   原因：App.vue 用 <Transition mode="out-in"> 包住路由页面，而 <Transition>
 *   只能作用在**单个根节点**上。dev 模式下 Vue 会保留模板里的注释，
 *   一旦 <template> 顶层出现注释，编译结果就变成 [注释, AppShell] 这种多根，
 *   根会退化成 Fragment，过渡钩子挂不到真正的 DOM 上。
 *
 *   后果不是"动画不好看"，而是：**离开这一页时整页永久空白，控制台一个字都不报**，
 *   刷新一下又好了（因为整页加载走的是另一条路）。2026-09-28 踩过一次。
 *
 * AppShell 负责页面外壳（背景、水印、准星、顶栏、底栏），
 * 它标签之间的内容会填进中间的主区域。
 */
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import AppShell from '../components/AppShell.vue'
import PasswordInput from '../components/PasswordInput.vue'
import { login } from '../api/auth'
import { setSession, takeNotice } from '../utils/session'

const router = useRouter()

/**
 * 上一次会话为什么会结束（比如"登录已过期"）。
 *
 * takeNotice() 是"读出来就顺手清掉"，所以这句话只会显示一次：
 * 刷新页面、或者下次自己点进登录页，它就没了。
 * （为什么需要它：会话在原页面失效时，用户只看到自己突然回到了登录页，
 *   没有这句话就完全不知道发生了什么。）
 */
const notice = ref(takeNotice())

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

    // ⚠️ 必须同时判 data 是不是 null —— 只判 code 是不够的。
    //   类型上 `code === 0` 并不会让 data 变成"一定有值"：ApiResponse 的失败分支里
    //   code 的类型是 number，而 number 包含 0，所以按 code 收窄时两个分支都满足，
    //   data 仍然是 `LoginResponse | null`。（完整说明见 types/api.ts。）
    if (json.code === 0 && json.data !== null) {
      // 成功：先把后端返回的会话信息存起来（主页要用），再跳到主页。
      // 注意这里不需要再把"登录成功"显示出来，因为页面马上就切走了。
      setSession(json.data)
      router.push({ name: 'home' })
    } else {
      // code 非 0 → 直接用后端给的那句话（后端保证它能给人看）。
      // code 是 0 却没带 data → 按契约不该发生；这时 message 通常是空串，
      // 所以补一句兜底，否则用户会看到"按钮恢复了，但什么都没发生"。
      errorMessage.value = json.message || '服务端返回的数据不完整，请稍后重试'
    }
  } catch (error) {
    errorMessage.value = '网络错误，请稍后重试'
  } finally {
    isLoading.value = false // 无论成功失败，结束登录态
  }
}
</script>

<template>
  <AppShell :identifier="username" :content-width="400">
    <div class="stage-head">
      <span class="field-code">00 /</span>
      <span class="stage-title">Access Terminal</span>
      <span class="field-line"></span>
    </div>

    <section class="panel">
      <div class="panel-bar">Authentication</div>

      <div class="panel-body">
        <!-- 为什么会被带到这里（凭证过期等）。放在表单最上面，
             一眼就能看到"我不是走错页面了，是得重新登录"。 -->
        <p v-if="notice" class="status notice">{{ notice }}</p>

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

<style scoped>
/*
 * "上一次会话为什么结束"那句话。
 *
 * 复用了全局的 .status（红字 + 前面一个小方块，和表单报错同款），
 * 但它在面板**最上面**，不需要 .status 那条"和上面的内容隔开"的上边线，
 * 也不需要上内边距 —— 面板自己的 gap 已经把它和用户名那栏拉开了。
 *
 * 写在 scoped 里而不是改全局：全局那套描边对"表单下方的报错"是对的，
 * 这里只是位置不同带来的一个例外。
 */
.notice {
  padding-top: 0;
  border-top: none;
}
</style>
