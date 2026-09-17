<script setup>
/**
 * 主页：登录成功之后看到的第一页。
 *
 * 现在只做两件事：
 *   1. 欢迎语
 *   2. 把登录接口返回的会话信息原样列出来 —— 让你直观看到"登录到底拿到了什么"
 *
 * 以后要加功能，就从这个面板下面往上接。
 */
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import AppShell from '../components/AppShell.vue'
import { session, clearSession } from '../utils/session'

const router = useRouter()

// ---------------- 从会话里取数据 ----------------
// 这些数据是登录成功时 LoginView 存进 utils/session.js 的，
// 也就是后端 /api/auth/login 返回体里的 data。
// computed 的意思是"跟着源数据自动算出来"，session 一变，这里就会重新算。

const username = computed(() => session.value?.username ?? '—')
const tokenType = computed(() => session.value?.tokenType ?? '—')

// 令牌只显示前 12 位，完整 token 太长会把版面撑坏。
// （真实项目里 token 不应该渲染到页面上，这里只是为了让你看清流程。）
const tokenPreview = computed(() => {
  const token = session.value?.accessToken
  return token ? `${token.slice(0, 12)}…` : '—'
})

// 秒数 -> "1 小时" / "30 分" / "45 秒"
const expiresText = computed(() => {
  const seconds = session.value?.expiresIn
  if (!seconds) return '—'
  const hours = Math.floor(seconds / 3600)
  const minutes = Math.floor((seconds % 3600) / 60)
  if (hours) return `${hours} 小时`
  if (minutes) return `${minutes} 分`
  return `${seconds} 秒`
})

// 时间戳 -> "2026-09-16 11:45"
const loggedInAtText = computed(() => {
  const timestamp = session.value?.loggedInAt
  if (!timestamp) return '—'
  const d = new Date(timestamp)
  const pad = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
})

// ---------------- 退出登录 ----------------
function handleLogout() {
  clearSession() // 1. 清掉会话
  router.push({ name: 'login' }) // 2. 回登录页
}
</script>

<template>
  <AppShell status="Session / Active">
    <!-- 顶栏右侧：当前登录的用户 -->
    <template #aside>
      <span class="who">
        <span class="who-dot"></span>
        {{ username }}
      </span>
    </template>

    <div class="stage-head">
      <span class="field-code">01 /</span>
      <span class="stage-title">Home</span>
      <span class="field-line"></span>
    </div>

    <section class="panel">
      <div class="panel-bar">Session</div>

      <div class="panel-body">
        <div class="intro">
          <p class="welcome">欢迎回来，{{ username }}</p>
          <p class="hint">下面这些信息，全部来自登录接口的返回值。</p>
        </div>

        <dl class="info">
          <div class="info-row">
            <dt>登录账号</dt>
            <dd>{{ username }}</dd>
          </div>
          <div class="info-row">
            <dt>登录时间</dt>
            <dd>{{ loggedInAtText }}</dd>
          </div>
          <div class="info-row">
            <dt>令牌类型</dt>
            <dd>{{ tokenType }}</dd>
          </div>
          <div class="info-row">
            <dt>有效期</dt>
            <dd>{{ expiresText }}</dd>
          </div>
          <div class="info-row">
            <dt>访问令牌</dt>
            <dd class="mono">{{ tokenPreview }}</dd>
          </div>
        </dl>

        <button class="btn btn--ghost" @click="handleLogout">退出登录</button>
      </div>
    </section>
  </AppShell>
</template>

<style scoped>
/* ---------------- 欢迎语 ---------------- */
.intro {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.welcome {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 15px;
  letter-spacing: var(--ls-mid);
  color: var(--text-title);
}

/* 标题前的小方块节点，跟登录页结果提示的小方块同一个语汇 */
.welcome::before {
  content: '';
  flex: none;
  width: 6px;
  height: 6px;
  background: var(--ink);
}

.hint {
  font-size: 12px;
  line-height: 1.7;
  letter-spacing: var(--ls-mid);
  color: var(--muted);
}

/* ---------------- 信息清单 ---------------- */
/* dl = 描述列表，dt 是"名称"、dd 是"内容"，正好适合这种键值对照 */
.info {
  display: flex;
  flex-direction: column;
}

.info-row {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 16px;
  padding: 10px 0;
  border-bottom: 1px solid var(--line-soft);
}

.info-row:first-child {
  padding-top: 0;
}

/* 最后一行不需要下边线，否则会和按钮上方的留白叠在一起显得脏 */
.info-row:last-child {
  padding-bottom: 0;
  border-bottom: none;
}

.info-row dt {
  flex: none;
  font-size: 12px;
  letter-spacing: var(--ls-mid);
  color: var(--muted);
}

.info-row dd {
  font-size: 13px;
  letter-spacing: var(--ls-mid);
  color: var(--text-title);
  text-align: right;
  word-break: break-all;
}

.mono {
  font-family: var(--font-mono);
  font-size: 12px;
}

/* ---------------- 顶栏右侧的用户名 ---------------- */
.who {
  display: flex;
  align-items: center;
  gap: 7px;
  font-size: 12px;
  letter-spacing: var(--ls-wide);
  text-transform: uppercase;
  color: var(--muted);
}

/* 小绿点 = 会话在线，呼应登录页底部的状态条语汇 */
.who-dot {
  flex: none;
  width: 6px;
  height: 6px;
  background: var(--ok);
}
</style>
